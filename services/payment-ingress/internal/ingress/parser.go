package ingress

import (
	"encoding/json"
	"errors"
	"fmt"
	"regexp"
	"strconv"
	"strings"
	"time"
)

type Payment struct {
	Provider       string
	TransactionRef string
	AmountMinor    int64
	ShortCode      string
	PayerPhone     *string
	OccurredAt     time.Time
	RawMSISDN      *string
}

type c2bPayload struct {
	TransID           string `json:"TransID"`
	TransTime         string `json:"TransTime"`
	TransAmount       string `json:"TransAmount"`
	BusinessShortCode string `json:"BusinessShortCode"`
	MSISDN            string `json:"MSISDN"`
}

var (
	receiptPattern = regexp.MustCompile(`^[A-Z0-9]{8,16}$`)
	shortCodePattern = regexp.MustCompile(`^[0-9]{4,20}$`)
	phonePattern = regexp.MustCompile(`^(?:2547|2541|07|01)[0-9]{8}$`)
	amountPattern = regexp.MustCompile(`^[0-9]{1,9}(?:\.[0-9]{1,2})?$`)
	// Supports common Safaricom formats: "Ksh1,000.00 received from ..." and
	// "You have received KES 1,000.00 from ...". The SMS remains unverified evidence.
	smsPattern = regexp.MustCompile(`(?i)^([A-Z0-9]{8,16})\s+confirmed\.?.*?\b(?:KES|KSH)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s+(?:received\s+from|from)\s+(.+?)\s+(?:on|at)\s+([0-9]{1,2}/[0-9]{1,2}/[0-9]{2,4})\s+(?:at\s+)?([0-9]{1,2}:[0-9]{2}\s*(?:AM|PM)?)\b.*$`)
)

func ParseDarajaC2B(body []byte) (Payment,error) {
	var p c2bPayload
	if err:=json.Unmarshal(body,&p); err!=nil { return Payment{},errors.New("invalid JSON callback") }
	p.TransID=strings.TrimSpace(strings.ToUpper(p.TransID)); p.BusinessShortCode=strings.TrimSpace(p.BusinessShortCode)
	if !receiptPattern.MatchString(p.TransID) { return Payment{},errors.New("invalid TransID") }
	if !shortCodePattern.MatchString(p.BusinessShortCode) { return Payment{},errors.New("invalid BusinessShortCode") }
	amount,err:=parseMinorUnits(p.TransAmount); if err!=nil || amount<=0 { return Payment{},errors.New("invalid TransAmount") }
	t,err:=time.ParseInLocation("20060102150405",strings.TrimSpace(p.TransTime),time.FixedZone("EAT",3*60*60))
	if err!=nil { return Payment{},fmt.Errorf("invalid TransTime: %w",err) }
	out:=Payment{Provider:"safaricom_c2b",TransactionRef:p.TransID,AmountMinor:amount,ShortCode:p.BusinessShortCode,OccurredAt:t.UTC()}
	msisdn:=strings.TrimSpace(p.MSISDN)
	if msisdn!="" { out.RawMSISDN=&msisdn; if phonePattern.MatchString(msisdn) { n:=normalizePhone(msisdn); out.PayerPhone=&n } }
	return out,nil
}
func ParseMPesaSMS(body []byte,shortcode string) (Payment,error) {
	m:=smsPattern.FindStringSubmatch(strings.TrimSpace(string(body)))
	if m==nil { return Payment{},errors.New("SMS format not recognized") }
	ref:=strings.ToUpper(m[1]); if !receiptPattern.MatchString(ref) { return Payment{},errors.New("invalid SMS receipt") }
	amount,err:=parseMinorUnits(strings.ReplaceAll(m[2],",","")); if err!=nil || amount<=0 { return Payment{},errors.New("invalid SMS amount") }
	phoneMatch:=regexp.MustCompile(`(?:\+?254|0)?[17][0-9]{8}`).FindString(m[3])
	var phone *string
	if phoneMatch!="" && phonePattern.MatchString(phoneMatch) { n:=normalizePhone(phoneMatch); phone=&n }
	dateTime:=strings.TrimSpace(m[4]+" "+m[5])
	layouts:=[]string{"2/1/2006 3:04 PM","2/1/06 3:04 PM","2/1/2006 15:04","2/1/06 15:04"}
	var occurred time.Time
	for _,layout:=range layouts { occurred,err=time.ParseInLocation(layout,strings.ToUpper(dateTime),time.FixedZone("EAT",3*60*60)); if err==nil { break } }
	if err!=nil { return Payment{},errors.New("invalid SMS timestamp") }
	if !shortCodePattern.MatchString(shortcode) { return Payment{},errors.New("SMS source shortcode is not configured") }
	return Payment{Provider:"mpesa_sms_candidate",TransactionRef:ref,AmountMinor:amount,ShortCode:shortcode,PayerPhone:phone,OccurredAt:occurred.UTC()},nil
}
func parseMinorUnits(s string) (int64,error) {
	s=strings.TrimSpace(strings.ReplaceAll(s,",","")); if !amountPattern.MatchString(s) { return 0,errors.New("invalid amount") }
	parts:=strings.SplitN(s,".",2); whole,err:=strconv.ParseInt(parts[0],10,64); if err!=nil { return 0,err }
	fraction:="00"; if len(parts)==2 { fraction=parts[1]+strings.Repeat("0",2-len(parts[1])) }
	cents,err:=strconv.ParseInt(fraction,10,64); if err!=nil { return 0,err }
	return whole*100+cents,nil
}
func normalizePhone(s string) string { s=strings.TrimPrefix(s,"+"); if strings.HasPrefix(s,"0") { return "254"+s[1:] }; return s }
