package bridge

import (
	"strconv"
	"testing"
)

func TestNormalizeAppMsgLink(t *testing.T) {
	event := MessageEvent{
		MessageType: 49,
		RawXML:      `<msg><appmsg appid="wx-app"><title>Release Notes</title><des>Read this</des><type>5</type><url>https://example.test/post</url><appname>Docs</appname></appmsg></msg>`,
	}.Normalize()

	if event.MessageKind != MessageKindAppMsg || event.AppMsgSubtype != "link" || event.AppMsgType != 5 {
		t.Fatalf("unexpected appmsg classification: %+v", event)
	}
	if event.AppMsgTitle != "Release Notes" || event.AppMsgDescription != "Read this" || event.AppMsgURL != "https://example.test/post" {
		t.Fatalf("unexpected appmsg fields: %+v", event)
	}
	if !containsString(event.Evidence, "raw_xml.appmsg.url") ||
		!containsString(event.Evidence, "raw_xml.appmsg.title") ||
		!containsString(event.Evidence, "raw_xml.appmsg.des") {
		t.Fatalf("expected structural evidence: %+v", event.Evidence)
	}
	if event.Text != "[链接] Release Notes" {
		t.Fatalf("unexpected display text: %q", event.Text)
	}
	if event.RawXML != "" {
		t.Fatalf("raw xml should be cleared after parsing")
	}
}

func TestNormalizeImageMessageExtractsCDNURLFallback(t *testing.T) {
	event := MessageEvent{
		MessageType: 3,
		RawXML:      `<msg><img aeskey="" cdnmidimgurl="https://gchat.qpic.cn/gchatpic_new/0/0-0-ABC/0" cdnthumburl="https://gchat.qpic.cn/thumb/0" length="19418" md5="abcdef0123456789" /></msg>`,
	}.Normalize()

	if event.MessageKind != MessageKindImage || event.MediaKind != MessageKindImage {
		t.Fatalf("unexpected image classification: %+v", event)
	}
	if event.MediaURL != "https://gchat.qpic.cn/gchatpic_new/0/0-0-ABC/0" || event.MediaSize != 19418 {
		t.Fatalf("unexpected image media fallback: %+v", event)
	}
	if event.MediaName != "abcdef0123456789.jpg" || event.MediaMime != "image/jpeg" {
		t.Fatalf("unexpected image media metadata: %+v", event)
	}
	if event.Text != "[图片]" || event.RawXML != "" {
		t.Fatalf("unexpected image text/raw xml: %+v", event)
	}
	if !containsString(event.Evidence, "raw_xml.img.cdnmidimgurl") || !containsString(event.Evidence, "raw_xml.img.length") {
		t.Fatalf("image evidence missing: %+v", event.Evidence)
	}
}

func TestNormalizeImageMessageKeepsExistingMediaURL(t *testing.T) {
	event := MessageEvent{
		MessageType: 3,
		MediaURL:    "/api/media/device/day/local.jpg",
		RawXML:      `<msg><img cdnmidimgurl="https://example.test/remote.jpg" length="123" /></msg>`,
	}.Normalize()

	if event.MediaURL != "/api/media/device/day/local.jpg" {
		t.Fatalf("existing local media URL should be kept: %+v", event)
	}
	if event.MediaSize != 123 {
		t.Fatalf("image length should still enrich metadata: %+v", event)
	}
}

func TestNormalizeImageMessageExtractsFallbackFromMalformedXML(t *testing.T) {
	event := MessageEvent{
		MessageType: 3,
		RawXML:      `<msg><img cdnmidimgurl='https://example.test/fallback.jpg' length='77'></msg>`,
	}.Normalize()

	if event.MediaURL != "https://example.test/fallback.jpg" || event.MediaSize != 77 {
		t.Fatalf("malformed image xml fallback failed: %+v", event)
	}
}

func TestNormalizeImageMessageDoesNotSynthesizeCDNURLFromMD5(t *testing.T) {
	event := MessageEvent{
		MessageType: 3,
		RawXML:      `<msg><img cdnmidimgurl="3071020100044f304d" cdnthumburl="3057020100044f304d" length="102217" md5="713679214c7286f239323c0d5949eccc" /></msg>`,
	}.Normalize()

	if event.MediaURL != "" {
		t.Fatalf("non-http WeChat CDN fields must not become downloadable URLs: %+v", event)
	}
	if event.MediaName != "713679214c7286f239323c0d5949eccc.jpg" || event.MediaSize != 102217 || event.MediaMime != "" {
		t.Fatalf("unexpected image metadata from non-http CDN fields: %+v", event)
	}
}

func containsString(values []string, want string) bool {
	for _, value := range values {
		if value == want {
			return true
		}
	}
	return false
}

func TestNormalizeEmojiMessage(t *testing.T) {
	event := MessageEvent{
		MessageType: 47,
		RawXML:      `<msg><emoji type="2" md5="0123456789abcdef0123456789abcdef" len="1234" productid="prod-1" cdnurl="https://example.test/emoji.gif" /></msg>`,
	}.Normalize()

	if event.MessageKind != MessageKindEmoji || event.MediaKind != MessageKindEmoji || event.AppMsgSubtype != "emoji" {
		t.Fatalf("unexpected emoji classification: %+v", event)
	}
	if event.AppMsgTitle != "0123456789abcdef0123456789abcdef" ||
		event.AppMsgAppName != "prod-1" ||
		event.AppMsgURL != "https://example.test/emoji.gif" ||
		event.AppMsgDescription != "type=2 len=1234" {
		t.Fatalf("unexpected emoji fields: %+v", event)
	}
	if event.Text != "[表情]" || event.RawXML != "" {
		t.Fatalf("unexpected emoji text/raw xml: %+v", event)
	}
	if !containsString(event.Evidence, "raw_xml.emoji.md5") ||
		!containsString(event.Evidence, "raw_xml.emoji.productid") ||
		!containsString(event.Evidence, "raw_xml.emoji.cdnurl") {
		t.Fatalf("emoji structural evidence missing: %+v", event.Evidence)
	}
}

func TestNormalizeEmojiMessageNormalizesWeChatEscapedURL(t *testing.T) {
	event := MessageEvent{
		MessageType: 47,
		RawXML:      `<msg><emoji type="1" md5="0123456789abcdef0123456789abcdef" cdnurl="http*#*//example.test/emoji.gif" /></msg>`,
	}.Normalize()

	if event.AppMsgURL != "http://example.test/emoji.gif" {
		t.Fatalf("unexpected emoji url: %q", event.AppMsgURL)
	}
}

func TestNormalizeAppMsgLinkUsesThumbnailMediaKind(t *testing.T) {
	event := MessageEvent{
		MessageType: 49,
		MediaKind:   MessageKindFile,
		MediaMime:   "image/jpeg",
		MediaBase64: "encoded-thumbnail",
		RawXML:      `<msg><appmsg><title>Article</title><type>5</type><url>https://example.test/article</url></appmsg></msg>`,
	}.Normalize()

	if event.MessageKind != MessageKindAppMsg || event.AppMsgSubtype != "link" {
		t.Fatalf("unexpected link appmsg: %+v", event)
	}
	if event.MediaKind != MessageKindImage {
		t.Fatalf("link thumbnail should be image media, got %+v", event)
	}
}

func TestNormalizeAppMsgFile(t *testing.T) {
	event := MessageEvent{
		MessageType: 49,
		MediaKind:   MessageKindFile,
		Text:        "[链接/文件]",
		RawXML:      `<msg><appmsg><title>Quarterly PDF</title><des></des><type>6</type><appattach><filename>report.pdf</filename><fileext>pdf</fileext><totallen>12345</totallen></appattach></appmsg></msg>`,
	}.Normalize()

	if event.MessageKind != MessageKindFile || event.AppMsgSubtype != "file" || event.AppMsgFileName != "report.pdf" {
		t.Fatalf("unexpected file appmsg: %+v", event)
	}
	if event.Text != "[文件] Quarterly PDF" {
		t.Fatalf("unexpected display text: %q", event.Text)
	}
	if event.MediaKind != MessageKindFile {
		t.Fatalf("file appmsg should keep file media kind: %+v", event)
	}
}

func TestNormalizeFileTransferMessageType(t *testing.T) {
	event := MessageEvent{
		MessageType: MessageTypeFileTransfer,
		Text:        "contract-check.txt",
	}.Normalize()

	if event.MessageKind != MessageKindFile || event.MediaKind != MessageKindFile || event.AppMsgSubtype != "file" {
		t.Fatalf("unexpected file transfer classification: %+v", event)
	}
	if containsString(event.Unsupported, "message_type:1090519089") {
		t.Fatalf("file transfer type should be supported, got unsupported=%+v", event.Unsupported)
	}
	if !containsString(event.Evidence, "message.type=1090519089") {
		t.Fatalf("expected file transfer evidence: %+v", event.Evidence)
	}
}

func TestNormalizeAppMsgMiniProgram(t *testing.T) {
	event := MessageEvent{
		MessageType: 49,
		MediaKind:   MessageKindFile,
		RawXML:      `<msg><appmsg><title>Mini Tool</title><type>33</type><weappinfo><username>gh_xxx@app</username><pagepath>pages/index</pagepath><appid>wx123</appid></weappinfo></appmsg></msg>`,
	}.Normalize()

	if event.MessageKind != MessageKindAppMsg || event.AppMsgSubtype != "mini_program" || event.AppMsgTitle != "Mini Tool" {
		t.Fatalf("unexpected mini program appmsg: %+v", event)
	}
	if event.Text != "[小程序] Mini Tool" {
		t.Fatalf("unexpected display text: %q", event.Text)
	}
	if !containsString(event.Evidence, "raw_xml.appmsg.weappinfo") ||
		!containsString(event.Evidence, "raw_xml.appmsg.weappinfo.username") ||
		!containsString(event.Evidence, "raw_xml.appmsg.weappinfo.pagepath") {
		t.Fatalf("mini program structural evidence missing: %+v", event.Evidence)
	}
	if event.MediaKind != "" {
		t.Fatalf("mini program card without payload should not look like a file attachment: %+v", event)
	}
}

func TestNormalizeAppMsgChatHistory(t *testing.T) {
	event := MessageEvent{
		MessageType: 49,
		MediaKind:   MessageKindFile,
		RawXML:      `<msg><appmsg><title>Chat History</title><type>19</type><recorditem><![CDATA[<recordinfo></recordinfo>]]></recorditem></appmsg></msg>`,
	}.Normalize()

	if event.MessageKind != MessageKindChatHistory || event.AppMsgSubtype != "chat_history" || event.AppMsgType != 19 {
		t.Fatalf("unexpected chat history appmsg: %+v", event)
	}
	if event.Text != "[聊天记录] Chat History" {
		t.Fatalf("unexpected display text: %q", event.Text)
	}
	if event.MediaKind != "" {
		t.Fatalf("chat history card should not look like a file attachment: %+v", event)
	}
}

func TestNormalizeAppMsgChatHistoryRecordSummary(t *testing.T) {
	event := MessageEvent{
		MessageType: 49,
		MediaKind:   MessageKindFile,
		RawXML: `<msg><appmsg><title>Team Notes</title><type>19</type><recorditem><![CDATA[
<recordinfo><datalist count="3">
  <recorditem><datatype>1</datatype><datadesc>Hello team</datadesc></recorditem>
  <recorditem><datatype>5</datatype><title>Launch note</title><desc>Spec</desc></recorditem>
  <recorditem><datatype>8</datatype><filename>brief.pdf</filename></recorditem>
</datalist></recordinfo>
]]></recorditem></appmsg></msg>`,
	}.Normalize()

	if event.MessageKind != MessageKindChatHistory || event.AppMsgSubtype != "chat_history" {
		t.Fatalf("unexpected chat history appmsg: %+v", event)
	}
	want := "共3条：文本 Hello team / 链接 Launch note / 文件 brief.pdf"
	if event.AppMsgDescription != want {
		t.Fatalf("unexpected record summary: %q", event.AppMsgDescription)
	}
	if !containsString(event.Evidence, "raw_xml.appmsg.recorditem") ||
		!containsString(event.Evidence, "raw_xml.appmsg.recorditem.count=3") {
		t.Fatalf("recorditem evidence missing: %+v", event.Evidence)
	}
	if !containsString(event.Evidence, "raw_xml.appmsg.recorditem.datatype.1=1") ||
		!containsString(event.Evidence, "raw_xml.appmsg.recorditem.datatype.5=1") ||
		!containsString(event.Evidence, "raw_xml.appmsg.recorditem.datatype.8=1") {
		t.Fatalf("recorditem type evidence missing: %+v", event.Evidence)
	}
	if len(event.Unsupported) != 0 {
		t.Fatalf("record summary should be supported: %+v", event.Unsupported)
	}
	if event.MediaKind != "" {
		t.Fatalf("chat history card should not look like a file attachment: %+v", event)
	}
}

func TestNormalizeAppMsgChatHistorySummarizesDataItemChildren(t *testing.T) {
	event := MessageEvent{
		MessageType: 49,
		RawXML: `<msg><appmsg><title>Real Shape</title><type>19</type><recorditem><![CDATA[
<recordinfo><datalist count="3">
  <dataitem><datatype>1</datatype><datadesc>Hello team</datadesc></dataitem>
  <dataitem><datatype>5</datatype><title>Launch note</title></dataitem>
  <dataitem><datatype>42</datatype><datadesc>Future message</datadesc></dataitem>
</datalist></recordinfo>
]]></recorditem></appmsg></msg>`,
	}.Normalize()

	if event.MessageKind != MessageKindChatHistory || event.AppMsgSubtype != "chat_history" {
		t.Fatalf("unexpected chat history appmsg: %+v", event)
	}
	want := "共3条：文本 Hello team / 链接 Launch note / 类型42 Future message"
	if event.AppMsgDescription != want {
		t.Fatalf("unexpected record summary: %q", event.AppMsgDescription)
	}
	if !containsString(event.Evidence, "raw_xml.appmsg.recorditem.count=3") ||
		!containsString(event.Evidence, "raw_xml.appmsg.recorditem.datatype.1=1") ||
		!containsString(event.Evidence, "raw_xml.appmsg.recorditem.datatype.5=1") ||
		!containsString(event.Evidence, "raw_xml.appmsg.recorditem.datatype.42=1") {
		t.Fatalf("recorditem type evidence missing: %+v", event.Evidence)
	}
	if !containsString(event.Unsupported, "appmsg_recorditem_datatype:42") {
		t.Fatalf("unknown datatype should be explicit: %+v", event.Unsupported)
	}
}

func TestNormalizeAppMsgChatHistorySummarizesDataItemAttributes(t *testing.T) {
	event := MessageEvent{
		MessageType: 49,
		RawXML: `<msg><appmsg><title>Attribute Shape</title><type>19</type><recorditem><![CDATA[
<recordinfo><datalist count="3">
  <dataitem datatype="1"><datadesc>Hello attr</datadesc></dataitem>
  <dataitem datatype="5"><title>Attr Link</title></dataitem>
  <dataitem datatype="8"><filename>attr.pdf</filename></dataitem>
</datalist></recordinfo>
]]></recorditem></appmsg></msg>`,
	}.Normalize()

	if event.MessageKind != MessageKindChatHistory || event.AppMsgSubtype != "chat_history" {
		t.Fatalf("unexpected chat history appmsg: %+v", event)
	}
	want := "共3条：文本 Hello attr / 链接 Attr Link / 文件 attr.pdf"
	if event.AppMsgDescription != want {
		t.Fatalf("unexpected record summary: %q", event.AppMsgDescription)
	}
	if !containsString(event.Evidence, "raw_xml.appmsg.recorditem.datatype.1=1") ||
		!containsString(event.Evidence, "raw_xml.appmsg.recorditem.datatype.5=1") ||
		!containsString(event.Evidence, "raw_xml.appmsg.recorditem.datatype.8=1") {
		t.Fatalf("recorditem attribute evidence missing: %+v", event.Evidence)
	}
	if len(event.Unsupported) != 0 {
		t.Fatalf("attribute record summary should be supported: %+v", event.Unsupported)
	}
}

func TestNormalizeAppMsgChatHistoryBrokenRecordItemIsExplicit(t *testing.T) {
	event := MessageEvent{
		MessageType: 49,
		RawXML:      `<msg><appmsg><title>Broken</title><type>19</type><recorditem><![CDATA[<recordinfo><datalist>]]></recorditem></appmsg></msg>`,
	}.Normalize()

	if event.MessageKind != MessageKindChatHistory || event.AppMsgSubtype != "chat_history" {
		t.Fatalf("unexpected chat history appmsg: %+v", event)
	}
	if !containsString(event.Unsupported, "appmsg_recorditem_parse_failed") {
		t.Fatalf("broken recorditem should be explicit: %+v", event.Unsupported)
	}
	if !containsString(event.Evidence, "raw_xml.appmsg.recorditem") {
		t.Fatalf("recorditem evidence missing: %+v", event.Evidence)
	}
}

func TestNormalizeAppMsgChatHistoryUnknownRecordItemTypeIsExplicit(t *testing.T) {
	event := MessageEvent{
		MessageType: 49,
		RawXML: `<msg><appmsg><title>Mixed History</title><type>19</type><recorditem><![CDATA[
<recordinfo><datalist count="2">
  <recorditem><datatype>1</datatype><datadesc>Hello team</datadesc></recorditem>
  <recorditem><datatype>42</datatype><datadesc>Future message</datadesc></recorditem>
</datalist></recordinfo>
]]></recorditem></appmsg></msg>`,
	}.Normalize()

	if event.MessageKind != MessageKindChatHistory || event.AppMsgSubtype != "chat_history" {
		t.Fatalf("unexpected chat history appmsg: %+v", event)
	}
	if !containsString(event.Evidence, "raw_xml.appmsg.recorditem.datatype.1=1") ||
		!containsString(event.Evidence, "raw_xml.appmsg.recorditem.datatype.42=1") {
		t.Fatalf("recorditem type evidence missing: %+v", event.Evidence)
	}
	if !containsString(event.Unsupported, "appmsg_recorditem_datatype:42") {
		t.Fatalf("unknown recorditem datatype should be explicit: %+v", event.Unsupported)
	}
}

func TestNormalizePaymentMessageTypesAreParseOnly(t *testing.T) {
	cases := []struct {
		name        string
		messageType int32
		wantSubtype string
		wantText    string
	}{
		{name: "transfer", messageType: MessageTypeTransfer, wantSubtype: "transfer", wantText: "[转账]"},
		{name: "red_packet", messageType: MessageTypeRedPacket, wantSubtype: "red_packet", wantText: "[红包]"},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			event := MessageEvent{
				MessageType:       tc.messageType,
				Text:              "sensitive payment preview should not persist",
				RawXML:            `<msg><appmsg><title>sensitive</title></appmsg></msg>`,
				AppMsgTitle:       "sensitive title",
				AppMsgDescription: "sensitive description",
				AppMsgURL:         "weixin://sensitive-payment-url",
			}.Normalize()

			if event.MessageKind != MessageKindPayment || event.AppMsgSubtype != tc.wantSubtype {
				t.Fatalf("unexpected payment message type classification: %+v", event)
			}
			if event.Text != tc.wantText || event.RawXML != "" || event.AppMsgTitle != "" || event.AppMsgDescription != "" || event.AppMsgURL != "" {
				t.Fatalf("direct payment type should be redacted: %+v", event)
			}
			if !containsString(event.Unsupported, "payment_outbound_unsupported") || !containsString(event.Unsupported, "payment_sensitive_fields_redacted") {
				t.Fatalf("direct payment unsupported markers missing: %+v", event.Unsupported)
			}
			if !containsString(event.Evidence, "message.type="+strconv.FormatInt(int64(tc.messageType), 10)) || !containsString(event.Evidence, "payment.message_type."+tc.wantSubtype) {
				t.Fatalf("direct payment evidence missing: %+v", event.Evidence)
			}
		})
	}
}

func TestNormalizeAppMsgPaymentRedactsSensitiveFields(t *testing.T) {
	cases := []struct {
		name          string
		appMsgType    int32
		wantSubtype   string
		wantText      string
		paymentRawXML string
	}{
		{
			name:          "transfer",
			appMsgType:    AppMsgTypeTransfer,
			wantSubtype:   "transfer",
			wantText:      "[转账]",
			paymentRawXML: `<msg><appmsg><title>amount and user should not persist</title><des>payer/payee details</des><type>2000</type><url>weixin://sensitive-transfer</url><appname>WeChat Pay</appname></appmsg></msg>`,
		},
		{
			name:          "red_packet",
			appMsgType:    AppMsgTypeRedPacket,
			wantSubtype:   "red_packet",
			wantText:      "[红包]",
			paymentRawXML: `<msg><appmsg><title>red packet title</title><des>sender details</des><type>2001</type><url>weixin://sensitive-red-packet</url><appname>WeChat Pay</appname></appmsg></msg>`,
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			event := MessageEvent{MessageType: 49, RawXML: tc.paymentRawXML, MediaKind: MessageKindFile}.Normalize()

			if event.MessageKind != MessageKindPayment || event.AppMsgType != tc.appMsgType || event.AppMsgSubtype != tc.wantSubtype {
				t.Fatalf("unexpected payment classification: %+v", event)
			}
			if event.Text != tc.wantText || event.RawXML != "" || event.MediaKind != "" {
				t.Fatalf("payment message should be safe display only: %+v", event)
			}
			if event.AppMsgTitle != "" || event.AppMsgDescription != "" || event.AppMsgURL != "" || event.AppMsgFileName != "" || event.AppMsgAppName != "" {
				t.Fatalf("payment fields should be redacted: %+v", event)
			}
			if !containsString(event.Unsupported, "payment_outbound_unsupported") || !containsString(event.Unsupported, "payment_sensitive_fields_redacted") {
				t.Fatalf("payment unsupported markers missing: %+v", event.Unsupported)
			}
			if !containsString(event.Evidence, "payment.parse_only") || !containsString(event.Evidence, "raw_xml.appmsg.type="+strconv.FormatInt(int64(tc.appMsgType), 10)) {
				t.Fatalf("payment evidence missing: %+v", event.Evidence)
			}
		})
	}
}

func TestNormalizeAppMsgUnknownAndParseFailureAreExplicit(t *testing.T) {
	unknown := MessageEvent{
		MessageType: 49,
		RawXML:      `<msg><appmsg><title>Mystery</title><type>4242</type></appmsg></msg>`,
	}.Normalize()
	if unknown.AppMsgSubtype != "unknown" || len(unknown.Unsupported) == 0 || unknown.Unsupported[0] != "appmsg_type:4242" {
		t.Fatalf("unknown appmsg should be explicit: %+v", unknown)
	}

	broken := MessageEvent{
		MessageType: 49,
		RawXML:      `<msg><appmsg><title>broken`,
	}.Normalize()
	if broken.AppMsgSubtype != "" || len(broken.Unsupported) == 0 || broken.Unsupported[0] != "appmsg_xml_parse_failed" {
		t.Fatalf("broken appmsg should be explicit: %+v", broken)
	}

	missingXML := MessageEvent{
		MessageType: 49,
	}.Normalize()
	if missingXML.MessageKind != MessageKindAppMsg ||
		!containsString(missingXML.Unsupported, "appmsg_xml_missing") ||
		!containsString(missingXML.Evidence, "message.type=49") ||
		missingXML.Text == "" {
		t.Fatalf("missing appmsg xml should remain visible and explicit: %+v", missingXML)
	}
}

func TestNormalizeLocationMessageXML(t *testing.T) {
	event := MessageEvent{
		MessageType: 48,
		RawXML:      `<msg><location x="31.2304" y="121.4737" scale="16" label="People Square" poiname="Metro Station" infourl="https://example.test/map" poiid="poi-1" frompoilist="1" poicategorytips="Transit" /></msg>`,
	}.Normalize()

	if event.MessageKind != MessageKindLocation {
		t.Fatalf("unexpected location kind: %+v", event)
	}
	if event.LocationLatitude == nil || *event.LocationLatitude != 31.2304 || event.LocationLongitude == nil || *event.LocationLongitude != 121.4737 {
		t.Fatalf("unexpected location coordinates: %+v", event)
	}
	if event.LocationScale != 16 || event.LocationLabel != "People Square" || event.LocationPoiName != "Metro Station" || event.LocationInfoURL != "https://example.test/map" || event.LocationPoiID != "poi-1" || !event.LocationFromPoiList || event.LocationPoiTips != "Transit" {
		t.Fatalf("unexpected location fields: %+v", event)
	}
	if event.Text != "People Square" || event.RawXML != "" {
		t.Fatalf("unexpected location text/raw xml: %+v", event)
	}
	if !containsString(event.Evidence, "message.type=48") || !containsString(event.Evidence, "raw_xml.location.x") || !containsString(event.Evidence, "raw_xml.location.y") || !containsString(event.Evidence, "raw_xml.location.label") {
		t.Fatalf("location evidence missing: %+v", event.Evidence)
	}
}

func TestNormalizeLocationMessageParseFailureIsExplicit(t *testing.T) {
	event := MessageEvent{
		MessageType: 48,
		RawXML:      `<msg><location x="bad" y="121.4737" /></msg>`,
	}.Normalize()

	if event.MessageKind != MessageKindLocation || event.LocationLongitude == nil || *event.LocationLongitude != 121.4737 {
		t.Fatalf("unexpected partial location parse: %+v", event)
	}
	if !containsString(event.Unsupported, "location_x_parse_failed") {
		t.Fatalf("bad coordinate should be explicit: %+v", event.Unsupported)
	}
}

func TestNormalizeQuoteMessageType(t *testing.T) {
	event := MessageEvent{
		MessageType: MessageTypeQuote,
		Text:        "[引用] reply preview",
	}.Normalize()

	if event.MessageKind != MessageKindAppMsg || event.AppMsgType != 57 || event.AppMsgSubtype != "quote" {
		t.Fatalf("unexpected quote message classification: %+v", event)
	}
	if len(event.Unsupported) != 0 {
		t.Fatalf("quote message should not be unsupported: %+v", event.Unsupported)
	}
	if !containsString(event.Evidence, "message.type=822083633") {
		t.Fatalf("quote message evidence missing: %+v", event.Evidence)
	}
}

func TestNormalizeUnknownMessageTypeIsExplicit(t *testing.T) {
	event := MessageEvent{
		MessageType: 900000001,
	}.Normalize()

	if event.MessageKind != MessageKindUnknown {
		t.Fatalf("unexpected unknown message kind: %+v", event)
	}
	if len(event.Unsupported) == 0 || event.Unsupported[0] != "message_type:900000001" {
		t.Fatalf("unknown message type should be explicit: %+v", event)
	}
	if len(event.Evidence) == 0 || event.Evidence[0] != "message.type=900000001" {
		t.Fatalf("unknown message evidence missing: %+v", event)
	}
	if event.Text != "[未支持] 类型 900000001" {
		t.Fatalf("unknown message should get a safe display text, got %q", event.Text)
	}
}

func TestNormalizeSystemMessageTypeIsExplicit(t *testing.T) {
	event := MessageEvent{
		MessageType: 10000,
	}.Normalize()

	if event.MessageKind != MessageKindSystem {
		t.Fatalf("unexpected system message kind: %+v", event)
	}
	if !containsString(event.Evidence, "message.type=10000") {
		t.Fatalf("system message evidence missing: %+v", event.Evidence)
	}
	if event.Text != "[系统消息]" {
		t.Fatalf("system message should get a safe display text, got %q", event.Text)
	}
}

func TestNormalizeSystemRevokeMessage(t *testing.T) {
	event := MessageEvent{
		MessageType: 10000,
		RawXML:      `<sysmsg type="revokemsg"><revokemsg><session>room-a@chatroom</session><oldmsgid>12345</oldmsgid><newmsgid>67890</newmsgid><replacemsg><![CDATA["Alice" 撤回了一条消息]]></replacemsg></revokemsg></sysmsg>`,
	}.Normalize()

	if event.MessageKind != MessageKindSystem || event.AppMsgSubtype != SystemSubtypeRevoke {
		t.Fatalf("unexpected revoke system message: %+v", event)
	}
	if event.AppMsgURL != "room-a@chatroom" || event.AppMsgFileName != "12345" || event.AppMsgAppName != "67890" {
		t.Fatalf("revoke identifiers not parsed: %+v", event)
	}
	if event.Text != `"Alice" 撤回了一条消息` || event.RawXML != "" {
		t.Fatalf("revoke display/raw xml mismatch: text=%q raw=%q", event.Text, event.RawXML)
	}
	if !containsString(event.Evidence, "raw_xml.sysmsg.revokemsg") || !containsString(event.Evidence, "raw_xml.sysmsg.revokemsg.msgid") {
		t.Fatalf("revoke evidence missing: %+v", event.Evidence)
	}
}
