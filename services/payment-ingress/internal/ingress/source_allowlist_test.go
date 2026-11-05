package ingress

import (
	"net"
	"testing"
)

func TestDarajaAllowlistFailsClosed(t *testing.T) {
	if ipAllowed(net.ParseIP("203.0.113.12"), nil) {
		t.Fatal("empty allowlist must reject all Daraja sources")
	}
	_, trusted, err := net.ParseCIDR("192.0.2.0/24")
	if err != nil {
		t.Fatal(err)
	}
	if ipAllowed(net.ParseIP("203.0.113.12"), []*net.IPNet{trusted}) {
		t.Fatal("unlisted source IP must be rejected")
	}
	if !ipAllowed(net.ParseIP("192.0.2.12"), []*net.IPNet{trusted}) {
		t.Fatal("listed source IP should be accepted")
	}
}

func TestDarajaAllowlistRejectsNilRemoteAddress(t *testing.T) {
	_, trusted, err := net.ParseCIDR("192.0.2.0/24")
	if err != nil {
		t.Fatal(err)
	}
	if ipAllowed(nil, []*net.IPNet{trusted}) {
		t.Fatal("missing peer IP must be rejected")
	}
}
