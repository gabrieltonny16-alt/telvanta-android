package ingress

import (
	"testing"
	"time"
)

func TestParseDarajaC2BDoesNotInventMaskedPayerPhone(t *testing.T) {
	body:=[]byte(`{"TransID":"QWERTY12345","TransTime":"20261104123045","TransAmount":"19.50","BusinessShortCode":"123456","MSISDN":"2547*****123"}`)
	got,err:=ParseDarajaC2B(body)
	if err!=nil { t.Fatal(err) }
	if got.AmountMinor!=1950 { t.Fatalf("amount minor = %d, want 1950",got.AmountMinor) }
	if got.PayerPhone!=nil { t.Fatalf("masked MSISDN must not be exposed as phone: %v",*got.PayerPhone) }
	if got.RawMSISDN==nil { t.Fatal("expected original masked value to be retained") }
	if got.OccurredAt.Location()!=time.UTC { t.Fatal("timestamp must be normalized to UTC") }
}

func TestParseDarajaC2BValidPhone(t *testing.T) {
	body:=[]byte(`{"TransID":"QWERTY12346","TransTime":"20261104123045","TransAmount":"20","BusinessShortCode":"123456","MSISDN":"254712345678"}`)
	got,err:=ParseDarajaC2B(body)
	if err!=nil { t.Fatal(err) }
	if got.PayerPhone==nil || *got.PayerPhone!="254712345678" { t.Fatalf("unexpected payer phone: %v",got.PayerPhone) }
}

func TestParseMinorUnits(t *testing.T) {
	for input,want:=range map[string]int64{"1":100,"19.5":1950,"1000.05":100005} {
		got,err:=parseMinorUnits(input)
		if err!=nil || got!=want { t.Fatalf("parseMinorUnits(%q) = %d, %v; want %d",input,got,err,want) }
	}
}
