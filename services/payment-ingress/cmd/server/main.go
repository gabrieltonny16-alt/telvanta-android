package main

import (
	"context"
	"log"
	"net"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
	"github.com/gabrieltonny16-alt/telvanta-android/services/payment-ingress/internal/ingress"
)

func main() {
	ctx := context.Background()
	dbURL := required("DATABASE_URL")
	redisURL := required("REDIS_URL")
	pool, err := pgxpool.New(ctx, dbURL)
	if err != nil { log.Fatal(err) }
	defer pool.Close()
	if err := pool.Ping(ctx); err != nil { log.Fatal(err) }

	opts, err := redis.ParseURL(redisURL)
	if err != nil { log.Fatal(err) }
	rdb := redis.NewClient(opts)
	defer rdb.Close()
	if err := rdb.Ping(ctx).Err(); err != nil { log.Fatal(err) }

	var cidrs []*net.IPNet
	for _, raw := range strings.Split(os.Getenv("DARAJA_ALLOWED_CIDRS"), ",") {
		raw = strings.TrimSpace(raw); if raw=="" { continue }
		_, block, err := net.ParseCIDR(raw); if err != nil { log.Fatalf("invalid DARAJA_ALLOWED_CIDRS entry: %v",err) }
		cidrs=append(cidrs,block)
	}
	h := &ingress.Handler{DB:pool,Redis:rdb,SMSSecret:[]byte(os.Getenv("SMS_INGRESS_HMAC_SECRET")),SMSShortcode:os.Getenv("SMS_RECEIVING_SHORTCODE"),AllowedDarajaCIDRs:cidrs}
	mux := http.NewServeMux()
	mux.HandleFunc("/api/v1/payments/callback",h.Callback)
	mux.HandleFunc("/healthz",func(w http.ResponseWriter,r *http.Request){ w.WriteHeader(http.StatusNoContent) })
	addr := os.Getenv("HTTP_ADDR"); if addr=="" { addr=":8080" }
	server := &http.Server{Addr:addr,Handler:securityHeaders(mux),ReadHeaderTimeout:5*time.Second,ReadTimeout:10*time.Second,WriteTimeout:10*time.Second,IdleTimeout:60*time.Second}
	log.Printf("BingwaPulse payment ingress listening on %s",addr)
	log.Fatal(server.ListenAndServe())
}
func required(k string) string { v:=strings.TrimSpace(os.Getenv(k)); if v=="" { log.Fatalf("missing required environment variable %s",k) }; return v }
func securityHeaders(next http.Handler) http.Handler { return http.HandlerFunc(func(w http.ResponseWriter,r *http.Request){ w.Header().Set("X-Content-Type-Options","nosniff"); w.Header().Set("Cache-Control","no-store"); next.ServeHTTP(w,r) }) }
