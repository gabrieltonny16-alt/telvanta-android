package ingress

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
)

type Handler struct {
	DB *pgxpool.Pool
	Redis *redis.Client
	SMSSecret []byte
	SMSShortcode string
	AllowedDarajaCIDRs []*net.IPNet
}

type response struct {
	Result string `json:"result"`
	Reference string `json:"reference,omitempty"`
}

func (h *Handler) Callback(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost { http.Error(w, "method not allowed", http.StatusMethodNotAllowed); return }
	r.Body = http.MaxBytesReader(w, r.Body, 64*1024)
	body, err := io.ReadAll(r.Body)
	if err != nil { http.Error(w, "invalid request body", http.StatusBadRequest); return }
	source := strings.ToLower(strings.TrimSpace(r.Header.Get("X-Bingwa-Source")))
	var p Payment
	switch source {
	case "sms":
		if !verifyHMAC(body, r.Header.Get("X-Bingwa-SMS-Signature"), h.SMSSecret) { http.Error(w, "unauthorized SMS source", http.StatusUnauthorized); return }
		p, err = ParseMPesaSMS(body, h.SMSShortcode)
	case "", "daraja":
		if len(h.AllowedDarajaCIDRs) > 0 && !ipAllowed(remoteIP(r), h.AllowedDarajaCIDRs) { http.Error(w, "source IP not allowed", http.StatusForbidden); return }
		p, err = ParseDarajaC2B(body)
	default:
		http.Error(w, "unsupported ingress source", http.StatusBadRequest); return
	}
	if err != nil { http.Error(w, "invalid payment notification", http.StatusBadRequest); return }

	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()
	var businessID string
	err = h.DB.QueryRow(ctx, `select business_id::text from public.payment_ingress_channels where shortcode=$1 and is_active=true limit 1`, p.ShortCode).Scan(&businessID)
	if errors.Is(err, pgx.ErrNoRows) { http.Error(w, "unknown receiving shortcode", http.StatusUnprocessableEntity); return }
	if err != nil { http.Error(w, "ingress temporarily unavailable", http.StatusServiceUnavailable); return }

	lockKey := "bingwapulse:mpesa:" + businessID + ":" + p.TransactionRef
	lockToken := randomToken()
	locked, err := h.Redis.SetNX(ctx, lockKey, lockToken, 24*time.Hour).Result()
	if err != nil { http.Error(w, "ingress temporarily unavailable", http.StatusServiceUnavailable); return }
	if !locked { writeJSON(w, http.StatusOK, response{Result:"duplicate_ignored", Reference:p.TransactionRef}); return }

	tx, err := h.DB.BeginTx(ctx, pgx.TxOptions{IsoLevel: pgx.ReadCommitted})
	if err != nil { releaseLock(ctx, h.Redis, lockKey, lockToken); http.Error(w, "ingress temporarily unavailable", http.StatusServiceUnavailable); return }
	defer tx.Rollback(ctx)

	rawHash := sha256.Sum256(body)
	var payer any
	if p.PayerPhone != nil { payer = *p.PayerPhone }
	var insertedID string
	err = tx.QueryRow(ctx, `
		insert into public.payment_ingress_events
		(business_id, provider, transaction_ref, amount_minor, shortcode, payer_phone,
		 raw_msisdn, occurred_at, source_kind, payload_sha256, verification_status)
		values ($1::uuid,$2,$3,$4,$5,$6,$7,$8,$9,$10,'unverified')
		on conflict (business_id, provider, transaction_ref) do nothing
		returning id::text
	`, businessID, p.Provider, p.TransactionRef, p.AmountMinor, p.ShortCode, payer, nullableString(p.RawMSISDN), p.OccurredAt,
		map[bool]string{true:"sms_candidate",false:"daraja_callback"}[source=="sms"], hex.EncodeToString(rawHash[:])).Scan(&insertedID)
	if errors.Is(err, pgx.ErrNoRows) {
		writeJSON(w, http.StatusOK, response{Result:"duplicate_ignored", Reference:p.TransactionRef}); return
	}
	if err != nil {
		releaseLock(ctx, h.Redis, lockKey, lockToken)
		http.Error(w, "could not record payment notification", http.StatusServiceUnavailable); return
	}
	if err := tx.Commit(ctx); err != nil {
		releaseLock(ctx, h.Redis, lockKey, lockToken)
		http.Error(w, "could not commit payment notification", http.StatusServiceUnavailable); return
	}
	writeJSON(w, http.StatusAccepted, response{Result:"recorded_unverified", Reference:p.TransactionRef})
}

func verifyHMAC(body []byte, supplied string, secret []byte) bool {
	if len(secret)==0 || supplied=="" { return false }
	mac:=hmac.New(sha256.New,secret); mac.Write(body)
	expected:=hex.EncodeToString(mac.Sum(nil))
	return hmac.Equal([]byte(strings.ToLower(supplied)),[]byte(expected))
}
func remoteIP(r *http.Request) net.IP { host,_,err:=net.SplitHostPort(r.RemoteAddr); if err!=nil { return net.ParseIP(r.RemoteAddr) }; return net.ParseIP(host) }
func ipAllowed(ip net.IP, cidrs []*net.IPNet) bool { for _,n:=range cidrs { if ip!=nil && n.Contains(ip) { return true } }; return false }
func nullableString(v *string) any { if v==nil { return nil }; return *v }
func writeJSON(w http.ResponseWriter, status int, v any) { w.Header().Set("Content-Type","application/json"); w.WriteHeader(status); _=json.NewEncoder(w).Encode(v) }
func releaseLock(ctx context.Context, client *redis.Client, key, token string) {
	const script = `if redis.call("get", KEYS[1]) == ARGV[1] then return redis.call("del", KEYS[1]) else return 0 end`
	_ = client.Eval(ctx,script,[]string{key},token).Err()
}
var _ = bytes.MinRead
var _ = sql.ErrNoRows
var _ = os.Getenv
