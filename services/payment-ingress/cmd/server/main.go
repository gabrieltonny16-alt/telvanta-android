package main

import (
	"context"
	"crypto/tls"
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
	ctx:=context.Background()
	pool,err:=pgxpool.New(ctx,required("DATABASE_URL")); if err!=nil { log.Fatal(err) }
	defer pool.Close()
	if err=pool.Ping(ctx); err!=nil { log.Fatal(err) }

	addrs:=splitNonEmpty(required("REDIS_CLUSTER_ADDRS"))
	if len(addrs)==0 { log.Fatal("REDIS_CLUSTER_ADDRS must contain at least one host:port") }
	clusterOpts:=&redis.ClusterOptions{Addrs:addrs,Username:os.Getenv("REDIS_USERNAME"),Password:required("REDIS_PASSWORD")}
	if strings.EqualFold(os.Getenv("REDIS_TLS"),"true") { clusterOpts.TLSConfig=&tls.Config{MinVersion:tls.VersionTLS12} }
	rdb:=redis.NewClusterClient(clusterOpts); defer rdb.Close()
	if err=rdb.Ping(ctx).Err(); err!=nil { log.Fatal(err) }

	var cidrs []*net.IPNet
	for _,raw:=range strings.Split(os.Getenv("DARAJA_ALLOWED_CIDRS"),",") {
		raw=strings.TrimSpace(raw); if raw=="" { continue }
		_,block,e:=net.ParseCIDR(raw); if e!=nil { log.Fatalf("invalid DARAJA_ALLOWED_CIDRS entry: %v",e) }
		cidrs=append(cidrs,block)
	}
	if len(cidrs)==0 {
		log.Fatal("DARAJA_ALLOWED_CIDRS must contain Safaricom-confirmed source CIDRs; refusing to accept unsigned C2B callbacks")
	}
	h:=&ingress.Handler{DB:pool,Redis:rdb,SMSSecret:[]byte(os.Getenv("SMS_INGRESS_HMAC_SECRET")),AllowedDarajaCIDRs:cidrs}
	mux:=http.NewServeMux()
	mux.HandleFunc("/api/v1/payments/callback",h.Callback)
	mux.HandleFunc("/healthz",func(w http.ResponseWriter,r *http.Request){ w.WriteHeader(http.StatusNoContent) })
	addr:=os.Getenv("HTTP_ADDR"); if addr=="" { addr=":8080" }
	server:=&http.Server{Addr:addr,Handler:securityHeaders(mux),ReadHeaderTimeout:5*time.Second,ReadTimeout:10*time.Second,WriteTimeout:10*time.Second,IdleTimeout:60*time.Second}
	log.Printf("BingwaPulse payment ingress listening on %s",addr)
	log.Fatal(server.ListenAndServe())
}
func required(k string) string { v:=strings.TrimSpace(os.Getenv(k)); if v=="" { log.Fatalf("missing required environment variable %s",k) }; return v }
func splitNonEmpty(s string) []string { out:=[]string{}; for _,v:=range strings.Split(s,",") { if v=strings.TrimSpace(v); v!="" { out=append(out,v) } }; return out }
func securityHeaders(next http.Handler) http.Handler { return http.HandlerFunc(func(w http.ResponseWriter,r *http.Request){ w.Header().Set("X-Content-Type-Options","nosniff"); w.Header().Set("Cache-Control","no-store"); next.ServeHTTP(w,r) }) }
