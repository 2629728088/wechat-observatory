package bridge

import (
	"encoding/xml"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

const (
	MessageTypeAppMsg       int32 = 49
	MessageTypeFileTransfer int32 = 1090519089
	MessageTypeQuote        int32 = 822083633

	AppMsgTypeTransfer  int32 = 2000
	AppMsgTypeRedPacket int32 = 2001

	MessageTypeTransfer  int32 = 419430449
	MessageTypeRedPacket int32 = 436207665

	MessageKindText        = "text"
	MessageKindImage       = "image"
	MessageKindVoice       = "voice"
	MessageKindVideo       = "video"
	MessageKindFile        = "file"
	MessageKindEmoji       = "emoji"
	MessageKindLocation    = "location"
	MessageKindPayment     = "payment"
	MessageKindAppMsg      = "appmsg"
	MessageKindChatHistory = "chat_history"
	MessageKindSystem      = "system"
	MessageKindUnknown     = "unknown"

	SystemSubtypeRevoke = "revoke"
)

type appMsgXMLDocument struct {
	AppMsg appMsgXML `xml:"appmsg"`
}

type appMsgXML struct {
	AppID             string          `xml:"appid,attr"`
	Title             string          `xml:"title"`
	Description       string          `xml:"des"`
	Type              int32           `xml:"type"`
	URL               string          `xml:"url"`
	AppName           string          `xml:"appname"`
	SourceDisplayName string          `xml:"sourcedisplayname"`
	AppAttach         appMsgAttachXML `xml:"appattach"`
	WeAppInfo         appMsgWeAppXML  `xml:"weappinfo"`
	RecordItem        string          `xml:"recorditem"`
}

type appMsgAttachXML struct {
	FileName string `xml:"filename"`
	FileExt  string `xml:"fileext"`
	TotalLen int64  `xml:"totallen"`
}

type appMsgWeAppXML struct {
	UserName string `xml:"username"`
	PagePath string `xml:"pagepath"`
	AppID    string `xml:"appid"`
}

type imageXMLDocument struct {
	Image imageXML `xml:"img"`
}

type imageXML struct {
	AESKey      string `xml:"aeskey,attr"`
	CDNBigURL   string `xml:"cdnbigimgurl,attr"`
	CDNMidURL   string `xml:"cdnmidimgurl,attr"`
	CDNThumbURL string `xml:"cdnthumburl,attr"`
	Length      string `xml:"length,attr"`
	MD5         string `xml:"md5,attr"`
}

type emojiXMLDocument struct {
	Emoji emojiXML `xml:"emoji"`
}

type emojiXML struct {
	Type       string `xml:"type,attr"`
	MD5        string `xml:"md5,attr"`
	AndroidMD5 string `xml:"androidmd5,attr"`
	ExternMD5  string `xml:"externmd5,attr"`
	Len        string `xml:"len,attr"`
	ProductID  string `xml:"productid,attr"`
	CDNURL     string `xml:"cdnurl,attr"`
	ThumbURL   string `xml:"thumburl,attr"`
	ExternURL  string `xml:"externurl,attr"`
}

type systemXMLDocument struct {
	Type      string          `xml:"type,attr"`
	RevokeMsg systemRevokeXML `xml:"revokemsg"`
}

type systemRevokeXML struct {
	Session    string `xml:"session"`
	OldMsgID   string `xml:"oldmsgid"`
	MsgID      string `xml:"msgid"`
	NewMsgID   string `xml:"newmsgid"`
	MsgSvrID   string `xml:"msgsvrid"`
	SvrID      string `xml:"svrid"`
	ReplaceMsg string `xml:"replacemsg"`
}

type locationXMLDocument struct {
	Location locationXML `xml:"location"`
}

type locationXML struct {
	X           string `xml:"x,attr"`
	Y           string `xml:"y,attr"`
	Scale       string `xml:"scale,attr"`
	Label       string `xml:"label,attr"`
	PoiName     string `xml:"poiname,attr"`
	InfoURL     string `xml:"infourl,attr"`
	PoiID       string `xml:"poiid,attr"`
	FromPoiList string `xml:"frompoilist,attr"`
	PoiTips     string `xml:"poicategorytips,attr"`
}

type appMsgRecordInfoXML struct {
	XMLName  xml.Name                `xml:"recordinfo"`
	DataList appMsgRecordDataListXML `xml:"datalist"`
	Items    []appMsgRecordItemXML   `xml:",any"`
}

type appMsgRecordDataListXML struct {
	XMLName xml.Name              `xml:"datalist"`
	Count   int                   `xml:"count,attr"`
	Items   []appMsgRecordItemXML `xml:",any"`
}

type appMsgRecordItemXML struct {
	XMLName         xml.Name
	DataType        int32  `xml:"datatype"`
	DataTypeAttr    int32  `xml:"datatype,attr"`
	Title           string `xml:"title"`
	DataTitle       string `xml:"datatitle"`
	Description     string `xml:"desc"`
	DataDescription string `xml:"datadesc"`
	FileName        string `xml:"filename"`
	SourceName      string `xml:"sourcename"`
}

type appMsgRecordSummary struct {
	Count            int
	Preview          string
	TypeCounts       []appMsgRecordTypeCount
	UnsupportedTypes []int32
}

type appMsgRecordTypeCount struct {
	DataType int32
	Count    int
}

var (
	recordItemDataTypeElementPattern   = regexp.MustCompile(`(?i)<datatype>\s*([0-9]+)\s*</datatype>`)
	recordItemDataTypeAttributePattern = regexp.MustCompile(`(?i)\bdatatype\s*=\s*["']?\s*([0-9]+)`)
)

func normalizeMessageEnvelope(event MessageEvent) MessageEvent {
	if strings.TrimSpace(event.MessageKind) == "" {
		event.MessageKind = messageKindForType(event.MessageType)
	}
	if paymentSubtype := paymentSubtypeForMessageType(event.MessageType); paymentSubtype != "" {
		event.AppMsgSubtype = paymentSubtype
		return normalizePaymentAppMsg(event)
	}
	if event.MessageType == MessageTypeQuote {
		return normalizeQuoteMessage(event)
	}
	if event.MessageType == 3 {
		return normalizeImageMessage(event)
	}
	if event.MessageType == 47 {
		return normalizeEmojiMessage(event)
	}
	if event.MessageType == 48 {
		return normalizeLocationMessage(event)
	}
	if event.MessageType == MessageTypeFileTransfer {
		return normalizeFileTransferMessage(event)
	}
	if event.MessageType != MessageTypeAppMsg {
		if event.MessageKind == MessageKindUnknown && event.MessageType > 0 {
			event.Unsupported = appendUnique(event.Unsupported, "message_type:"+strconv.FormatInt(int64(event.MessageType), 10))
			event.Evidence = appendUnique(event.Evidence, "message.type="+strconv.FormatInt(int64(event.MessageType), 10))
		}
		if event.MessageKind == MessageKindSystem && event.MessageType > 0 {
			return normalizeSystemMessage(event)
		}
		if strings.TrimSpace(event.Text) == "" {
			event.Text = defaultInboundDisplayText(event.MessageKind, event.MessageType)
		}
		return event
	}

	rawXML := strings.TrimSpace(firstNonEmpty(event.RawXML, xmlCandidateFromText(event.Text)))
	if rawXML == "" {
		event.MessageKind = MessageKindAppMsg
		event.Unsupported = appendUnique(event.Unsupported, "appmsg_xml_missing")
		event.Evidence = appendUnique(event.Evidence, "message.type=49")
		if strings.TrimSpace(event.Text) == "" {
			event.Text = appMsgDisplayText(event)
		}
		return normalizeAppMsgMediaFields(event)
	}
	parsed, ok := parseAppMsgXML(rawXML)
	event.RawXML = ""
	event.Evidence = appendUnique(event.Evidence, "message.type=49", "raw_xml.appmsg")
	if !ok {
		event.MessageKind = MessageKindAppMsg
		event.Unsupported = appendUnique(event.Unsupported, "appmsg_xml_parse_failed")
		if strings.TrimSpace(event.Text) == "" {
			event.Text = "[链接/文件]"
		}
		return normalizeAppMsgMediaFields(event)
	}

	event.AppMsgType = parsed.Type
	event.AppMsgSubtype = appMsgSubtype(parsed.Type)
	event.MessageKind = messageKindForAppMsg(parsed.Type)
	event.AppMsgTitle = compactSpace(parsed.Title)
	event.AppMsgDescription = compactSpace(parsed.Description)
	event.AppMsgURL = strings.TrimSpace(parsed.URL)
	event.AppMsgFileName = compactSpace(firstNonEmpty(parsed.AppAttach.FileName, parsed.Title))
	event.AppMsgAppName = compactSpace(firstNonEmpty(parsed.AppName, parsed.SourceDisplayName, parsed.WeAppInfo.UserName))
	event.Evidence = appendUnique(event.Evidence, "raw_xml.appmsg.type="+strconv.FormatInt(int64(parsed.Type), 10))
	event.Evidence = appendUnique(event.Evidence, appMsgStructuralEvidence(parsed)...)
	if event.MessageKind == MessageKindPayment {
		return normalizePaymentAppMsg(event)
	}
	if event.AppMsgSubtype == "chat_history" && strings.TrimSpace(parsed.RecordItem) != "" {
		summary, ok := summarizeAppMsgRecordItem(parsed.RecordItem)
		event.Evidence = appendUnique(event.Evidence, "raw_xml.appmsg.recorditem")
		if ok {
			if event.AppMsgDescription == "" && summary.Preview != "" {
				event.AppMsgDescription = summary.Preview
			}
			if summary.Count > 0 {
				event.Evidence = appendUnique(event.Evidence, "raw_xml.appmsg.recorditem.count="+strconv.Itoa(summary.Count))
			}
			for _, typeCount := range summary.TypeCounts {
				event.Evidence = appendUnique(event.Evidence,
					"raw_xml.appmsg.recorditem.datatype."+strconv.FormatInt(int64(typeCount.DataType), 10)+"="+strconv.Itoa(typeCount.Count))
			}
			for _, dataType := range summary.UnsupportedTypes {
				event.Unsupported = appendUnique(event.Unsupported,
					"appmsg_recorditem_datatype:"+strconv.FormatInt(int64(dataType), 10))
			}
		} else {
			event.Unsupported = appendUnique(event.Unsupported, "appmsg_recorditem_parse_failed")
		}
	}
	if event.AppMsgSubtype == "unknown" {
		event.Unsupported = appendUnique(event.Unsupported, "appmsg_type:"+strconv.FormatInt(int64(parsed.Type), 10))
	}
	if strings.TrimSpace(event.Text) == "" || strings.HasPrefix(strings.TrimSpace(event.Text), "[链接/文件]") {
		event.Text = appMsgDisplayText(event)
	}
	return normalizeAppMsgMediaFields(event)
}

func normalizeSystemMessage(event MessageEvent) MessageEvent {
	event.MessageKind = MessageKindSystem
	event.Evidence = appendUnique(event.Evidence, "message.type="+strconv.FormatInt(int64(event.MessageType), 10))
	rawXML := strings.TrimSpace(firstNonEmpty(event.RawXML, xmlCandidateFromText(event.Text)))
	if isRevokeSystemXML(rawXML) {
		revoke := parseSystemRevokeXML(rawXML)
		event.AppMsgSubtype = SystemSubtypeRevoke
		event.AppMsgTitle = compactDisplay(revoke.ReplaceMsg, 160)
		event.AppMsgURL = compactSpace(revoke.Session)
		event.AppMsgFileName = compactSpace(firstNonEmpty(revoke.OldMsgID, revoke.MsgID))
		event.AppMsgAppName = compactSpace(firstNonEmpty(revoke.NewMsgID, revoke.MsgSvrID, revoke.SvrID))
		event.Text = firstNonEmpty(event.AppMsgTitle, "[撤回消息]")
		event.RawXML = ""
		event.Evidence = appendUnique(event.Evidence, "raw_xml.sysmsg.revokemsg")
		if event.AppMsgFileName != "" {
			event.Evidence = appendUnique(event.Evidence, "raw_xml.sysmsg.revokemsg.msgid")
		}
		if event.AppMsgAppName != "" {
			event.Evidence = appendUnique(event.Evidence, "raw_xml.sysmsg.revokemsg.newmsgid")
		}
		return event
	}
	if strings.TrimSpace(event.Text) == "" {
		event.Text = defaultInboundDisplayText(event.MessageKind, event.MessageType)
	}
	event.RawXML = ""
	return event
}

func isRevokeSystemXML(rawXML string) bool {
	value := strings.ToLower(strings.TrimSpace(rawXML))
	return strings.Contains(value, "revokemsg") || strings.Contains(value, "msgsvrcancel")
}

func parseSystemRevokeXML(rawXML string) systemRevokeXML {
	var doc systemXMLDocument
	if err := xml.Unmarshal([]byte(rawXML), &doc); err == nil {
		revoke := doc.RevokeMsg
		revoke.Session = firstNonEmpty(revoke.Session, xmlTagValue(rawXML, "session"))
		revoke.OldMsgID = firstNonEmpty(revoke.OldMsgID, xmlTagValue(rawXML, "oldmsgid"))
		revoke.MsgID = firstNonEmpty(revoke.MsgID, xmlTagValue(rawXML, "msgid"))
		revoke.NewMsgID = firstNonEmpty(revoke.NewMsgID, xmlTagValue(rawXML, "newmsgid"))
		revoke.MsgSvrID = firstNonEmpty(revoke.MsgSvrID, xmlTagValue(rawXML, "msgsvrid"))
		revoke.SvrID = firstNonEmpty(revoke.SvrID, xmlTagValue(rawXML, "svrid"))
		revoke.ReplaceMsg = firstNonEmpty(revoke.ReplaceMsg, xmlTagValue(rawXML, "replacemsg"))
		return revoke
	}
	return systemRevokeXML{
		Session:    xmlTagValue(rawXML, "session"),
		OldMsgID:   xmlTagValue(rawXML, "oldmsgid"),
		MsgID:      xmlTagValue(rawXML, "msgid"),
		NewMsgID:   xmlTagValue(rawXML, "newmsgid"),
		MsgSvrID:   xmlTagValue(rawXML, "msgsvrid"),
		SvrID:      xmlTagValue(rawXML, "svrid"),
		ReplaceMsg: xmlTagValue(rawXML, "replacemsg"),
	}
}

func normalizeFileTransferMessage(event MessageEvent) MessageEvent {
	event.MessageKind = MessageKindFile
	event.MediaKind = firstNonEmpty(event.MediaKind, MessageKindFile)
	event.AppMsgSubtype = firstNonEmpty(event.AppMsgSubtype, "file")
	event.Evidence = appendUnique(event.Evidence, "message.type="+strconv.FormatInt(int64(MessageTypeFileTransfer), 10))
	if strings.TrimSpace(event.Text) == "" {
		event.Text = defaultInboundDisplayText(event.MessageKind, event.MessageType)
	}
	return event
}

func normalizeImageMessage(event MessageEvent) MessageEvent {
	event.MessageKind = MessageKindImage
	event.MediaKind = firstNonEmpty(event.MediaKind, MessageKindImage)
	event.Evidence = appendUnique(event.Evidence, "message.type=3")

	rawXML := strings.TrimSpace(firstNonEmpty(event.RawXML, xmlCandidateFromText(event.Text)))
	if rawXML != "" {
		parsed, ok := parseImageXML(rawXML)
		event.RawXML = ""
		if ok {
			event.Evidence = appendUnique(event.Evidence, "raw_xml.img")
			event.Evidence = appendUnique(event.Evidence, imageStructuralEvidence(parsed)...)
			if strings.TrimSpace(event.MediaURL) == "" {
				event.MediaURL = imageCDNURL(parsed)
			}
			if event.MediaSize <= 0 {
				event.MediaSize = parseInt64(parsed.Length)
			}
			if strings.TrimSpace(event.MediaName) == "" {
				event.MediaName = imageMediaName(parsed)
			}
			if strings.TrimSpace(event.MediaMime) == "" && strings.TrimSpace(event.MediaURL) != "" {
				event.MediaMime = "image/jpeg"
			}
		} else {
			event.Unsupported = appendUnique(event.Unsupported, "image_xml_parse_failed")
		}
	}
	if strings.TrimSpace(event.Text) == "" || looksLikeXML(event.Text) {
		event.Text = "[图片]"
	}
	return event
}

func parseImageXML(rawXML string) (imageXML, bool) {
	var doc imageXMLDocument
	if err := xml.Unmarshal([]byte(rawXML), &doc); err == nil {
		if strings.TrimSpace(firstNonEmpty(doc.Image.CDNBigURL, doc.Image.CDNMidURL, doc.Image.CDNThumbURL, doc.Image.MD5, doc.Image.Length)) != "" {
			return doc.Image, true
		}
	}
	parsed := imageXML{
		CDNBigURL:   xmlAttrValue(rawXML, "img", "cdnbigimgurl"),
		CDNMidURL:   xmlAttrValue(rawXML, "img", "cdnmidimgurl"),
		CDNThumbURL: xmlAttrValue(rawXML, "img", "cdnthumburl"),
		Length:      xmlAttrValue(rawXML, "img", "length"),
		MD5:         xmlAttrValue(rawXML, "img", "md5"),
	}
	return parsed, strings.TrimSpace(firstNonEmpty(parsed.CDNBigURL, parsed.CDNMidURL, parsed.CDNThumbURL, parsed.MD5, parsed.Length)) != ""
}

func imageCDNURL(parsed imageXML) string {
	for _, value := range []string{parsed.CDNBigURL, parsed.CDNMidURL, parsed.CDNThumbURL} {
		url := normalizeWeChatEscapedURL(value)
		lower := strings.ToLower(url)
		if strings.HasPrefix(lower, "http://") || strings.HasPrefix(lower, "https://") {
			return url
		}
	}
	return ""
}

func imageMediaName(parsed imageXML) string {
	md5 := compactSpace(parsed.MD5)
	if md5 == "" {
		return "wechat-image.jpg"
	}
	return md5 + ".jpg"
}

func imageStructuralEvidence(parsed imageXML) []string {
	evidence := []string{}
	if strings.TrimSpace(parsed.CDNBigURL) != "" {
		evidence = append(evidence, "raw_xml.img.cdnbigimgurl")
	}
	if strings.TrimSpace(parsed.CDNMidURL) != "" {
		evidence = append(evidence, "raw_xml.img.cdnmidimgurl")
	}
	if strings.TrimSpace(parsed.CDNThumbURL) != "" {
		evidence = append(evidence, "raw_xml.img.cdnthumburl")
	}
	if strings.TrimSpace(parsed.Length) != "" {
		evidence = append(evidence, "raw_xml.img.length")
	}
	if strings.TrimSpace(parsed.MD5) != "" {
		evidence = append(evidence, "raw_xml.img.md5")
	}
	return evidence
}

func normalizeEmojiMessage(event MessageEvent) MessageEvent {
	event.MessageKind = MessageKindEmoji
	event.MediaKind = firstNonEmpty(event.MediaKind, MessageKindEmoji)
	event.AppMsgSubtype = "emoji"
	event.Evidence = appendUnique(event.Evidence, "message.type=47")

	rawXML := strings.TrimSpace(firstNonEmpty(event.RawXML, xmlCandidateFromText(event.Text)))
	if rawXML == "" {
		if strings.TrimSpace(event.Text) == "" {
			event.Text = "[表情]"
		}
		return event
	}

	parsed, ok := parseEmojiXML(rawXML)
	event.RawXML = ""
	event.Evidence = appendUnique(event.Evidence, "raw_xml.emoji")
	if !ok {
		event.Unsupported = appendUnique(event.Unsupported, "emoji_xml_parse_failed")
		if strings.TrimSpace(event.Text) == "" || looksLikeXML(event.Text) {
			event.Text = "[表情]"
		}
		return event
	}

	md5 := compactSpace(firstNonEmpty(parsed.MD5, parsed.AndroidMD5, parsed.ExternMD5))
	event.AppMsgTitle = md5
	event.AppMsgDescription = emojiDescription(parsed)
	event.AppMsgURL = normalizeWeChatEscapedURL(firstNonEmpty(parsed.CDNURL, parsed.ExternURL, parsed.ThumbURL))
	event.AppMsgFileName = emojiFileName(md5, parsed.Type)
	event.AppMsgAppName = compactSpace(parsed.ProductID)
	event.Evidence = appendUnique(event.Evidence, emojiStructuralEvidence(parsed)...)
	if strings.TrimSpace(event.Text) == "" || looksLikeXML(event.Text) {
		event.Text = "[表情]"
	}
	return event
}

func normalizeWeChatEscapedURL(value string) string {
	text := strings.TrimSpace(value)
	if strings.HasPrefix(text, "http*#*//") {
		return "http://" + strings.TrimPrefix(text, "http*#*//")
	}
	if strings.HasPrefix(text, "https*#*//") {
		return "https://" + strings.TrimPrefix(text, "https*#*//")
	}
	return text
}

func normalizeLocationMessage(event MessageEvent) MessageEvent {
	event.MessageKind = MessageKindLocation
	event.MediaKind = ""
	event.Evidence = appendUnique(event.Evidence, "message.type=48")

	rawXML := strings.TrimSpace(firstNonEmpty(event.RawXML, xmlCandidateFromText(event.Text)))
	if rawXML == "" {
		if strings.TrimSpace(event.Text) == "" {
			event.Text = defaultInboundDisplayText(event.MessageKind, event.MessageType)
		}
		return event
	}

	parsed, ok := parseLocationXML(rawXML)
	event.RawXML = ""
	event.Evidence = appendUnique(event.Evidence, "raw_xml.location")
	if !ok {
		event.Unsupported = appendUnique(event.Unsupported, "location_xml_parse_failed")
		if strings.TrimSpace(event.Text) == "" || looksLikeXML(event.Text) {
			event.Text = defaultInboundDisplayText(event.MessageKind, event.MessageType)
		}
		return event
	}

	if value, ok := parseFloatField(parsed.X); ok {
		event.LocationLatitude = &value
		event.Evidence = appendUnique(event.Evidence, "raw_xml.location.x")
	} else if strings.TrimSpace(parsed.X) != "" {
		event.Unsupported = appendUnique(event.Unsupported, "location_x_parse_failed")
	}
	if value, ok := parseFloatField(parsed.Y); ok {
		event.LocationLongitude = &value
		event.Evidence = appendUnique(event.Evidence, "raw_xml.location.y")
	} else if strings.TrimSpace(parsed.Y) != "" {
		event.Unsupported = appendUnique(event.Unsupported, "location_y_parse_failed")
	}
	if value, ok := parseIntField(parsed.Scale); ok {
		event.LocationScale = value
		event.Evidence = appendUnique(event.Evidence, "raw_xml.location.scale")
	}
	event.LocationLabel = compactSpace(parsed.Label)
	event.LocationPoiName = compactSpace(parsed.PoiName)
	event.LocationInfoURL = strings.TrimSpace(parsed.InfoURL)
	event.LocationPoiID = compactSpace(parsed.PoiID)
	event.LocationFromPoiList = parseBoolField(parsed.FromPoiList)
	event.LocationPoiTips = compactSpace(parsed.PoiTips)
	event.Evidence = appendUnique(event.Evidence, locationStructuralEvidence(parsed)...)
	if strings.TrimSpace(event.Text) == "" || looksLikeXML(event.Text) {
		event.Text = firstNonEmpty(event.LocationLabel, event.LocationPoiName, defaultInboundDisplayText(event.MessageKind, event.MessageType))
	}
	return event
}

func normalizePaymentAppMsg(event MessageEvent) MessageEvent {
	event.MessageKind = MessageKindPayment
	event.MediaKind = ""
	event.RawXML = ""
	event.AppMsgTitle = ""
	event.AppMsgDescription = ""
	event.AppMsgURL = ""
	event.AppMsgFileName = ""
	event.AppMsgAppName = ""
	event.Unsupported = appendUnique(event.Unsupported, "payment_outbound_unsupported", "payment_sensitive_fields_redacted")
	event.Evidence = appendUnique(event.Evidence, "payment.parse_only")
	if event.MessageType > 0 {
		event.Evidence = appendUnique(event.Evidence, "message.type="+strconv.FormatInt(int64(event.MessageType), 10))
	}
	if event.MessageType != MessageTypeAppMsg {
		event.Evidence = appendUnique(event.Evidence, "payment.message_type."+event.AppMsgSubtype)
	}
	switch event.AppMsgSubtype {
	case "transfer":
		event.Text = "[转账]"
	case "red_packet":
		event.Text = "[红包]"
	default:
		event.Text = "[支付]"
	}
	return event
}

func paymentSubtypeForMessageType(messageType int32) string {
	switch messageType {
	case MessageTypeTransfer:
		return "transfer"
	case MessageTypeRedPacket:
		return "red_packet"
	default:
		return ""
	}
}

func normalizeQuoteMessage(event MessageEvent) MessageEvent {
	event.MessageKind = MessageKindAppMsg
	event.AppMsgType = 57
	event.AppMsgSubtype = "quote"
	event.Evidence = appendUnique(event.Evidence, "message.type="+strconv.FormatInt(int64(MessageTypeQuote), 10))
	if strings.TrimSpace(event.Text) == "" {
		event.Text = appMsgDisplayText(event)
	}
	return event
}

func appMsgStructuralEvidence(parsed appMsgXML) []string {
	evidence := []string{}
	if strings.TrimSpace(parsed.AppID) != "" {
		evidence = append(evidence, "raw_xml.appmsg.appid_attr")
	}
	if strings.TrimSpace(parsed.Title) != "" {
		evidence = append(evidence, "raw_xml.appmsg.title")
	}
	if strings.TrimSpace(parsed.Description) != "" {
		evidence = append(evidence, "raw_xml.appmsg.des")
	}
	if strings.TrimSpace(parsed.URL) != "" {
		evidence = append(evidence, "raw_xml.appmsg.url")
	}
	if strings.TrimSpace(parsed.AppName) != "" {
		evidence = append(evidence, "raw_xml.appmsg.appname")
	}
	if strings.TrimSpace(parsed.SourceDisplayName) != "" {
		evidence = append(evidence, "raw_xml.appmsg.sourcedisplayname")
	}
	if strings.TrimSpace(parsed.AppAttach.FileName) != "" || strings.TrimSpace(parsed.AppAttach.FileExt) != "" || parsed.AppAttach.TotalLen > 0 {
		evidence = append(evidence, "raw_xml.appmsg.appattach")
	}
	if strings.TrimSpace(parsed.AppAttach.FileName) != "" {
		evidence = append(evidence, "raw_xml.appmsg.appattach.filename")
	}
	if parsed.AppAttach.TotalLen > 0 {
		evidence = append(evidence, "raw_xml.appmsg.appattach.totallen")
	}
	if strings.TrimSpace(parsed.WeAppInfo.UserName) != "" || strings.TrimSpace(parsed.WeAppInfo.PagePath) != "" || strings.TrimSpace(parsed.WeAppInfo.AppID) != "" {
		evidence = append(evidence, "raw_xml.appmsg.weappinfo")
	}
	if strings.TrimSpace(parsed.WeAppInfo.UserName) != "" {
		evidence = append(evidence, "raw_xml.appmsg.weappinfo.username")
	}
	if strings.TrimSpace(parsed.WeAppInfo.PagePath) != "" {
		evidence = append(evidence, "raw_xml.appmsg.weappinfo.pagepath")
	}
	if strings.TrimSpace(parsed.WeAppInfo.AppID) != "" {
		evidence = append(evidence, "raw_xml.appmsg.weappinfo.appid")
	}
	if strings.TrimSpace(parsed.RecordItem) != "" {
		evidence = append(evidence, "raw_xml.appmsg.recorditem")
	}
	return evidence
}

func emojiStructuralEvidence(parsed emojiXML) []string {
	evidence := []string{}
	if strings.TrimSpace(parsed.MD5) != "" {
		evidence = append(evidence, "raw_xml.emoji.md5")
	}
	if strings.TrimSpace(parsed.AndroidMD5) != "" {
		evidence = append(evidence, "raw_xml.emoji.androidmd5")
	}
	if strings.TrimSpace(parsed.ExternMD5) != "" {
		evidence = append(evidence, "raw_xml.emoji.externmd5")
	}
	if strings.TrimSpace(parsed.Type) != "" {
		evidence = append(evidence, "raw_xml.emoji.type")
	}
	if strings.TrimSpace(parsed.Len) != "" {
		evidence = append(evidence, "raw_xml.emoji.len")
	}
	if strings.TrimSpace(parsed.ProductID) != "" {
		evidence = append(evidence, "raw_xml.emoji.productid")
	}
	if strings.TrimSpace(parsed.CDNURL) != "" {
		evidence = append(evidence, "raw_xml.emoji.cdnurl")
	}
	if strings.TrimSpace(parsed.ThumbURL) != "" {
		evidence = append(evidence, "raw_xml.emoji.thumburl")
	}
	if strings.TrimSpace(parsed.ExternURL) != "" {
		evidence = append(evidence, "raw_xml.emoji.externurl")
	}
	return evidence
}

func locationStructuralEvidence(parsed locationXML) []string {
	evidence := []string{}
	if strings.TrimSpace(parsed.Label) != "" {
		evidence = append(evidence, "raw_xml.location.label")
	}
	if strings.TrimSpace(parsed.PoiName) != "" {
		evidence = append(evidence, "raw_xml.location.poiname")
	}
	if strings.TrimSpace(parsed.InfoURL) != "" {
		evidence = append(evidence, "raw_xml.location.infourl")
	}
	if strings.TrimSpace(parsed.PoiID) != "" {
		evidence = append(evidence, "raw_xml.location.poiid")
	}
	if strings.TrimSpace(parsed.FromPoiList) != "" {
		evidence = append(evidence, "raw_xml.location.frompoilist")
	}
	if strings.TrimSpace(parsed.PoiTips) != "" {
		evidence = append(evidence, "raw_xml.location.poicategorytips")
	}
	return evidence
}

func parseAppMsgXML(rawXML string) (appMsgXML, bool) {
	rawXML = strings.TrimSpace(rawXML)
	if rawXML == "" {
		return appMsgXML{}, false
	}
	var wrapped appMsgXMLDocument
	if err := xml.Unmarshal([]byte(rawXML), &wrapped); err == nil && !isEmptyAppMsg(wrapped.AppMsg) {
		return wrapped.AppMsg, true
	}
	var direct appMsgXML
	if err := xml.Unmarshal([]byte(rawXML), &direct); err == nil && !isEmptyAppMsg(direct) {
		return direct, true
	}
	return appMsgXML{}, false
}

func parseEmojiXML(rawXML string) (emojiXML, bool) {
	rawXML = strings.TrimSpace(rawXML)
	if rawXML == "" {
		return emojiXML{}, false
	}
	var wrapped emojiXMLDocument
	if err := xml.Unmarshal([]byte(rawXML), &wrapped); err == nil && !isEmptyEmojiXML(wrapped.Emoji) {
		return wrapped.Emoji, true
	}
	var direct emojiXML
	if err := xml.Unmarshal([]byte(rawXML), &direct); err == nil && !isEmptyEmojiXML(direct) {
		return direct, true
	}
	return emojiXML{}, false
}

func parseLocationXML(rawXML string) (locationXML, bool) {
	rawXML = strings.TrimSpace(rawXML)
	if rawXML == "" {
		return locationXML{}, false
	}
	var wrapped locationXMLDocument
	if err := xml.Unmarshal([]byte(rawXML), &wrapped); err == nil && !isEmptyLocationXML(wrapped.Location) {
		return wrapped.Location, true
	}
	var direct locationXML
	if err := xml.Unmarshal([]byte(rawXML), &direct); err == nil && !isEmptyLocationXML(direct) {
		return direct, true
	}
	return locationXML{}, false
}

func summarizeAppMsgRecordItem(rawXML string) (appMsgRecordSummary, bool) {
	items, count, ok := parseAppMsgRecordItems(rawXML)
	if !ok {
		return appMsgRecordSummary{}, false
	}
	if count <= 0 {
		count = len(items)
	}
	typeCounts, unsupportedTypes := summarizeRecordItemTypes(items)
	if len(typeCounts) == 0 {
		typeCounts, unsupportedTypes = scanRecordItemTypes(rawXML)
	}
	if count <= 0 && len(typeCounts) > 0 {
		count = sumRecordItemTypeCounts(typeCounts)
	}
	if count <= 0 {
		return appMsgRecordSummary{TypeCounts: typeCounts, UnsupportedTypes: unsupportedTypes}, true
	}
	parts := make([]string, 0, 3)
	for _, item := range items {
		text := recordItemSummaryText(item)
		if text == "" {
			continue
		}
		parts = append(parts, text)
		if len(parts) >= 3 {
			break
		}
	}
	prefix := "共" + strconv.Itoa(count) + "条"
	if len(parts) == 0 {
		return appMsgRecordSummary{Count: count, Preview: prefix, TypeCounts: typeCounts, UnsupportedTypes: unsupportedTypes}, true
	}
	return appMsgRecordSummary{Count: count, Preview: prefix + "：" + strings.Join(parts, " / "), TypeCounts: typeCounts, UnsupportedTypes: unsupportedTypes}, true
}

func parseAppMsgRecordItems(rawXML string) ([]appMsgRecordItemXML, int, bool) {
	rawXML = strings.TrimSpace(rawXML)
	if rawXML == "" {
		return nil, 0, false
	}

	var info appMsgRecordInfoXML
	if err := xml.Unmarshal([]byte(rawXML), &info); err == nil && info.XMLName.Local != "" {
		items := filterAppMsgRecordItems(info.Items)
		items = append(items, filterAppMsgRecordItems(info.DataList.Items)...)
		return items, info.DataList.Count, true
	}

	var dataList appMsgRecordDataListXML
	if err := xml.Unmarshal([]byte(rawXML), &dataList); err == nil && dataList.XMLName.Local != "" {
		return filterAppMsgRecordItems(dataList.Items), dataList.Count, true
	}

	var item appMsgRecordItemXML
	if err := xml.Unmarshal([]byte(rawXML), &item); err == nil && isAppMsgRecordItemElement(item.XMLName.Local) {
		return []appMsgRecordItemXML{item}, 1, true
	}
	return nil, 0, false
}

func scanRecordItemTypes(rawXML string) ([]appMsgRecordTypeCount, []int32) {
	items := []appMsgRecordItemXML{}
	for _, pattern := range []*regexp.Regexp{recordItemDataTypeElementPattern, recordItemDataTypeAttributePattern} {
		for _, match := range pattern.FindAllStringSubmatch(rawXML, -1) {
			if len(match) < 2 {
				continue
			}
			value, err := strconv.Atoi(strings.TrimSpace(match[1]))
			if err != nil {
				continue
			}
			items = append(items, appMsgRecordItemXML{DataType: int32(value)})
		}
	}
	return summarizeRecordItemTypes(items)
}

func filterAppMsgRecordItems(items []appMsgRecordItemXML) []appMsgRecordItemXML {
	filtered := make([]appMsgRecordItemXML, 0, len(items))
	for _, item := range items {
		if isAppMsgRecordItemElement(item.XMLName.Local) {
			filtered = append(filtered, item)
		}
	}
	return filtered
}

func isAppMsgRecordItemElement(name string) bool {
	switch strings.ToLower(strings.TrimSpace(name)) {
	case "recorditem", "dataitem":
		return true
	default:
		return false
	}
}

func sumRecordItemTypeCounts(typeCounts []appMsgRecordTypeCount) int {
	total := 0
	for _, item := range typeCounts {
		total += item.Count
	}
	return total
}

func recordItemSummaryText(item appMsgRecordItemXML) string {
	label := recordItemTypeLabel(recordItemDataType(item))
	detail := compactDisplay(firstNonEmpty(
		item.Title,
		item.DataTitle,
		item.Description,
		item.DataDescription,
		item.FileName,
		item.SourceName,
	), 48)
	if detail == "" {
		return label
	}
	return label + " " + detail
}

func recordItemTypeLabel(dataType int32) string {
	switch dataType {
	case 1:
		return "文本"
	case 2:
		return "图片"
	case 3:
		return "语音"
	case 4, 15:
		return "视频"
	case 5:
		return "链接"
	case 6:
		return "位置"
	case 7:
		return "音乐"
	case 8:
		return "文件"
	default:
		if dataType > 0 {
			return "类型" + strconv.FormatInt(int64(dataType), 10)
		}
		return "项目"
	}
}

func summarizeRecordItemTypes(items []appMsgRecordItemXML) ([]appMsgRecordTypeCount, []int32) {
	counts := map[int32]int{}
	unsupported := map[int32]struct{}{}
	for _, item := range items {
		dataType := recordItemDataType(item)
		if dataType <= 0 {
			continue
		}
		counts[dataType]++
		if !isKnownRecordItemType(dataType) {
			unsupported[dataType] = struct{}{}
		}
	}
	keys := make([]int, 0, len(counts))
	for dataType := range counts {
		keys = append(keys, int(dataType))
	}
	sort.Ints(keys)
	typeCounts := make([]appMsgRecordTypeCount, 0, len(keys))
	for _, dataType := range keys {
		typeCounts = append(typeCounts, appMsgRecordTypeCount{DataType: int32(dataType), Count: counts[int32(dataType)]})
	}
	unsupportedKeys := make([]int, 0, len(unsupported))
	for dataType := range unsupported {
		unsupportedKeys = append(unsupportedKeys, int(dataType))
	}
	sort.Ints(unsupportedKeys)
	unsupportedTypes := make([]int32, 0, len(unsupportedKeys))
	for _, dataType := range unsupportedKeys {
		unsupportedTypes = append(unsupportedTypes, int32(dataType))
	}
	return typeCounts, unsupportedTypes
}

func recordItemDataType(item appMsgRecordItemXML) int32 {
	if item.DataType > 0 {
		return item.DataType
	}
	return item.DataTypeAttr
}

func isKnownRecordItemType(dataType int32) bool {
	switch dataType {
	case 1, 2, 3, 4, 5, 6, 7, 8, 15:
		return true
	default:
		return false
	}
}

func isEmptyAppMsg(value appMsgXML) bool {
	return value.Type == 0 &&
		strings.TrimSpace(value.Title) == "" &&
		strings.TrimSpace(value.Description) == "" &&
		strings.TrimSpace(value.URL) == "" &&
		strings.TrimSpace(value.AppAttach.FileName) == "" &&
		strings.TrimSpace(value.WeAppInfo.UserName) == ""
}

func isEmptyEmojiXML(value emojiXML) bool {
	return strings.TrimSpace(value.Type) == "" &&
		strings.TrimSpace(value.MD5) == "" &&
		strings.TrimSpace(value.AndroidMD5) == "" &&
		strings.TrimSpace(value.ExternMD5) == "" &&
		strings.TrimSpace(value.Len) == "" &&
		strings.TrimSpace(value.ProductID) == "" &&
		strings.TrimSpace(value.CDNURL) == "" &&
		strings.TrimSpace(value.ThumbURL) == "" &&
		strings.TrimSpace(value.ExternURL) == ""
}

func isEmptyLocationXML(value locationXML) bool {
	return strings.TrimSpace(value.X) == "" && strings.TrimSpace(value.Y) == "" && strings.TrimSpace(value.Scale) == "" && strings.TrimSpace(value.Label) == "" && strings.TrimSpace(value.PoiName) == "" && strings.TrimSpace(value.InfoURL) == "" && strings.TrimSpace(value.PoiID) == "" && strings.TrimSpace(value.FromPoiList) == "" && strings.TrimSpace(value.PoiTips) == ""
}

func parseFloatField(value string) (float64, bool) {
	value = strings.TrimSpace(value)
	if value == "" {
		return 0, false
	}
	parsed, err := strconv.ParseFloat(value, 64)
	return parsed, err == nil
}

func parseIntField(value string) (int, bool) {
	value = strings.TrimSpace(value)
	if value == "" {
		return 0, false
	}
	parsed, err := strconv.Atoi(value)
	return parsed, err == nil
}

func parseBoolField(value string) bool {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "1", "true", "yes", "on":
		return true
	default:
		return false
	}
}

func parseInt64(value string) int64 {
	parsed, ok := parseIntField(value)
	if !ok {
		return 0
	}
	return int64(parsed)
}

func messageKindForType(messageType int32) string {
	switch messageType {
	case 1:
		return MessageKindText
	case 3:
		return MessageKindImage
	case 34:
		return MessageKindVoice
	case 43, 62:
		return MessageKindVideo
	case 47:
		return MessageKindEmoji
	case 48:
		return MessageKindLocation
	case MessageTypeTransfer, MessageTypeRedPacket:
		return MessageKindPayment
	case MessageTypeAppMsg:
		return MessageKindAppMsg
	case MessageTypeFileTransfer:
		return MessageKindFile
	case MessageTypeQuote:
		return MessageKindAppMsg
	case 10000:
		return MessageKindSystem
	default:
		return MessageKindUnknown
	}
}

func messageKindForAppMsg(appMsgType int32) string {
	switch appMsgType {
	case 6:
		return MessageKindFile
	case 19:
		return MessageKindChatHistory
	case AppMsgTypeTransfer, AppMsgTypeRedPacket:
		return MessageKindPayment
	default:
		return MessageKindAppMsg
	}
}

func defaultInboundDisplayText(messageKind string, messageType int32) string {
	switch messageKind {
	case MessageKindImage:
		return "[图片]"
	case MessageKindVoice:
		return "[语音]"
	case MessageKindVideo:
		return "[视频]"
	case MessageKindFile:
		return "[文件]"
	case MessageKindEmoji:
		return "[表情]"
	case MessageKindLocation:
		return "[位置]"
	case MessageKindPayment:
		return "[支付]"
	case MessageKindSystem:
		return "[系统消息]"
	case MessageKindUnknown:
		if messageType > 0 {
			return "[未支持] 类型 " + strconv.FormatInt(int64(messageType), 10)
		}
		return "[未支持]"
	default:
		return ""
	}
}

func appMsgSubtype(appMsgType int32) string {
	switch appMsgType {
	case 3:
		return "music"
	case 4:
		return "video"
	case 5:
		return "link"
	case 6:
		return "file"
	case 8:
		return "emoji"
	case 19:
		return "chat_history"
	case 33, 36:
		return "mini_program"
	case 57:
		return "quote"
	case AppMsgTypeTransfer:
		return "transfer"
	case AppMsgTypeRedPacket:
		return "red_packet"
	default:
		return "unknown"
	}
}

func appMsgDisplayText(event MessageEvent) string {
	label := "[链接/文件]"
	switch event.AppMsgSubtype {
	case "link":
		label = "[链接]"
	case "file":
		label = "[文件]"
	case "mini_program":
		label = "[小程序]"
	case "chat_history":
		label = "[聊天记录]"
	case "quote":
		label = "[引用]"
	case "emoji":
		label = "[表情]"
	case "transfer":
		label = "[转账]"
	case "red_packet":
		label = "[红包]"
	}
	detail := firstNonEmpty(event.AppMsgTitle, event.AppMsgFileName, event.AppMsgDescription, event.AppMsgAppName)
	if detail == "" {
		return label
	}
	return label + " " + compactDisplay(detail, 96)
}

func normalizeAppMsgMediaFields(event MessageEvent) MessageEvent {
	if event.MessageType != 49 {
		return event
	}
	hasPayload := strings.TrimSpace(event.MediaBase64) != "" || strings.TrimSpace(event.MediaURL) != ""
	if hasPayload {
		if kind := mediaKindForMime(event.MediaMime); kind != "" {
			event.MediaKind = kind
			return event
		}
	}
	switch strings.TrimSpace(event.AppMsgSubtype) {
	case "file":
		if strings.TrimSpace(event.MediaKind) == "" {
			event.MediaKind = MessageKindFile
		}
	case "video":
		if hasPayload && (strings.TrimSpace(event.MediaKind) == "" || strings.TrimSpace(event.MediaKind) == MessageKindFile) {
			event.MediaKind = MessageKindVideo
		}
	case "emoji":
		if hasPayload && strings.TrimSpace(event.MediaKind) == "" {
			event.MediaKind = MessageKindEmoji
		}
	default:
		if strings.TrimSpace(event.MediaKind) == MessageKindFile {
			event.MediaKind = ""
		}
	}
	return event
}

func xmlCandidateFromText(text string) string {
	text = strings.TrimSpace(text)
	if looksLikeXML(text) {
		return text
	}
	return ""
}

func looksLikeXML(value string) bool {
	value = strings.ToLower(strings.TrimSpace(value))
	return strings.HasPrefix(value, "<msg") ||
		strings.HasPrefix(value, "<?xml") ||
		strings.HasPrefix(value, "<appmsg") ||
		strings.HasPrefix(value, "<emoji") ||
		strings.HasPrefix(value, "<sysmsg") ||
		strings.HasPrefix(value, "<template")
}

func emojiDescription(value emojiXML) string {
	parts := []string{}
	if text := strings.TrimSpace(value.Type); text != "" {
		parts = append(parts, "type="+text)
	}
	if text := strings.TrimSpace(value.Len); text != "" {
		parts = append(parts, "len="+text)
	}
	return strings.Join(parts, " ")
}

func emojiFileName(md5 string, emojiType string) string {
	md5 = strings.TrimSpace(md5)
	if md5 == "" {
		return ""
	}
	ext := ".emoji"
	switch strings.TrimSpace(emojiType) {
	case "1":
		ext = ".gif"
	case "2":
		ext = ".png"
	}
	return md5 + ext
}

func compactSpace(value string) string {
	return strings.Join(strings.Fields(strings.TrimSpace(value)), " ")
}

func compactDisplay(value string, limit int) string {
	value = compactSpace(value)
	if limit <= 0 || len([]rune(value)) <= limit {
		return value
	}
	runes := []rune(value)
	return string(runes[:limit]) + "..."
}

func xmlAttrValue(rawXML string, tag string, attr string) string {
	if strings.TrimSpace(rawXML) == "" || strings.TrimSpace(tag) == "" || strings.TrimSpace(attr) == "" {
		return ""
	}
	pattern := regexp.MustCompile(`(?is)<` + regexp.QuoteMeta(tag) + `\b[^>]*\s` + regexp.QuoteMeta(attr) + `\s*=\s*["']([^"']*)["']`)
	match := pattern.FindStringSubmatch(rawXML)
	if len(match) < 2 {
		return ""
	}
	return compactSpace(match[1])
}

func xmlTagValue(rawXML string, tag string) string {
	if strings.TrimSpace(rawXML) == "" || strings.TrimSpace(tag) == "" {
		return ""
	}
	pattern := regexp.MustCompile(`(?is)<` + regexp.QuoteMeta(tag) + `\b[^>]*>(.*?)</` + regexp.QuoteMeta(tag) + `>`)
	match := pattern.FindStringSubmatch(rawXML)
	if len(match) < 2 {
		return ""
	}
	value := strings.TrimSpace(match[1])
	value = strings.TrimPrefix(value, "<![CDATA[")
	value = strings.TrimSuffix(value, "]]>")
	return compactSpace(value)
}

func appendUnique(values []string, additions ...string) []string {
	seen := map[string]struct{}{}
	for _, value := range values {
		if value = strings.TrimSpace(value); value != "" {
			seen[value] = struct{}{}
		}
	}
	for _, value := range additions {
		value = strings.TrimSpace(value)
		if value == "" {
			continue
		}
		if _, ok := seen[value]; ok {
			continue
		}
		values = append(values, value)
		seen[value] = struct{}{}
	}
	return values
}
