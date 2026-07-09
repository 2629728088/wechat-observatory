package bridge

import (
	"encoding/json"
	"errors"
	"strings"
	"time"
)

type Direction string

type ChatKind string

const (
	DirectionRecv Direction = "recv"
	DirectionSent Direction = "sent"

	ChatKindDirect  ChatKind = "direct"
	ChatKindRoom    ChatKind = "room"
	ChatKindUnknown ChatKind = "unknown"

	RawProviderModuleAck = "module_ack"

	OutboxKindText     = "text"
	OutboxKindImage    = "image"
	OutboxKindVideo    = "video"
	OutboxKindVoice    = "voice"
	OutboxKindFile     = "file"
	OutboxKindEmoji    = "emoji"
	OutboxKindLocation = "location"
	OutboxKindQuote    = "quote"
	OutboxKindLink     = "link"
	OutboxKindRevoke   = "revoke"

	OutboxKindChatHistory = "chat_history"
	OutboxKindMiniProgram = "mini_program"
)

type MessageEvent struct {
	APIKey              string    `json:"api_key,omitempty"`
	ID                  string    `json:"id"`
	EventID             int64     `json:"event_id"`
	ChatRecordID        int64     `json:"chat_record_id"`
	Device              string    `json:"device"`
	OwnerWxID           string    `json:"owner_wxid,omitempty"`
	MessageKind         string    `json:"kind,omitempty"`
	From                string    `json:"from"`
	To                  string    `json:"to"`
	RoomID              string    `json:"room_id"`
	Sender              string    `json:"sender"`
	Text                string    `json:"text"`
	MessageType         int32     `json:"message_type"`
	RawXML              string    `json:"raw_xml,omitempty"`
	AppMsgType          int32     `json:"appmsg_type,omitempty"`
	AppMsgSubtype       string    `json:"appmsg_subtype,omitempty"`
	AppMsgTitle         string    `json:"appmsg_title,omitempty"`
	AppMsgDescription   string    `json:"appmsg_description,omitempty"`
	AppMsgURL           string    `json:"appmsg_url,omitempty"`
	AppMsgFileName      string    `json:"appmsg_file_name,omitempty"`
	AppMsgAppName       string    `json:"appmsg_app_name,omitempty"`
	Unsupported         []string  `json:"unsupported,omitempty"`
	Evidence            []string  `json:"evidence,omitempty"`
	LocationLatitude    *float64  `json:"location_latitude,omitempty"`
	LocationLongitude   *float64  `json:"location_longitude,omitempty"`
	LocationScale       int       `json:"location_scale,omitempty"`
	LocationLabel       string    `json:"location_label,omitempty"`
	LocationPoiName     string    `json:"location_poiname,omitempty"`
	LocationInfoURL     string    `json:"location_info_url,omitempty"`
	LocationPoiID       string    `json:"location_poi_id,omitempty"`
	LocationFromPoiList bool      `json:"location_from_poi_list,omitempty"`
	LocationPoiTips     string    `json:"location_poi_category_tips,omitempty"`
	MediaKind           string    `json:"media_kind,omitempty"`
	MediaMime           string    `json:"media_mime,omitempty"`
	MediaName           string    `json:"media_name,omitempty"`
	MediaURL            string    `json:"media_url,omitempty"`
	MediaSize           int64     `json:"media_size,omitempty"`
	MediaBase64         string    `json:"media_base64,omitempty"`
	CreateTime          int64     `json:"create_time"`
	Direction           Direction `json:"direction"`
	RawProvider         string    `json:"raw_provider"`
	ChatKind            ChatKind  `json:"chat_kind,omitempty"`
	Conversation        string    `json:"chat_id,omitempty"`
}

func (e MessageEvent) Validate() error {
	if strings.TrimSpace(e.Device) == "" {
		return errors.New("device is required")
	}
	if strings.TrimSpace(e.Text) == "" && strings.TrimSpace(e.MediaURL) == "" {
		return errors.New("text or media_url is required")
	}
	if e.Direction != DirectionRecv && e.Direction != DirectionSent {
		return errors.New("direction must be recv or sent")
	}
	if e.ChatID() == "" {
		return errors.New("one of from, to, room_id, or chat_id is required")
	}
	return nil
}

func (e MessageEvent) ChatID() string {
	if strings.TrimSpace(e.Conversation) != "" {
		return strings.TrimSpace(e.Conversation)
	}
	if strings.TrimSpace(e.RoomID) != "" {
		return strings.TrimSpace(e.RoomID)
	}
	if e.Direction == DirectionSent && strings.TrimSpace(e.To) != "" {
		return strings.TrimSpace(e.To)
	}
	if strings.TrimSpace(e.From) != "" {
		return strings.TrimSpace(e.From)
	}
	return strings.TrimSpace(e.To)
}

func (e MessageEvent) Kind() ChatKind {
	if strings.TrimSpace(string(e.ChatKind)) != "" {
		return e.ChatKind
	}
	if strings.TrimSpace(e.RoomID) != "" || strings.Contains(strings.ToLower(strings.TrimSpace(e.ChatID())), "@chatroom") {
		return ChatKindRoom
	}
	return ChatKindDirect
}

func (e MessageEvent) Normalize() MessageEvent {
	if strings.TrimSpace(e.Conversation) == "" {
		e.Conversation = e.ChatID()
	}
	if strings.TrimSpace(string(e.ChatKind)) == "" {
		e.ChatKind = e.Kind()
	}
	if e.ChatKind == ChatKindRoom && strings.TrimSpace(e.RoomID) == "" {
		e.RoomID = e.ChatID()
	}
	e = normalizeMessageEnvelope(e)
	return e
}

func (e MessageEvent) Timestamp() int64 {
	if e.CreateTime > 0 {
		return e.CreateTime
	}
	return time.Now().Unix()
}

type SendTextRequest struct {
	Device    string   `json:"device"`
	OwnerWxID string   `json:"owner_wxid,omitempty"`
	WxIDs     []string `json:"wx_ids"`
	Text      string   `json:"text"`
}

func (req SendTextRequest) Validate(defaultDevice string) (SendTextRequest, error) {
	if strings.TrimSpace(req.Device) == "" {
		req.Device = defaultDevice
	}
	if strings.TrimSpace(req.Device) == "" {
		return req, errors.New("device is required")
	}
	if len(req.WxIDs) == 0 {
		return req, errors.New("wx_ids is required")
	}
	for i := range req.WxIDs {
		req.WxIDs[i] = strings.TrimSpace(req.WxIDs[i])
		if req.WxIDs[i] == "" {
			return req, errors.New("wx_ids cannot contain empty values")
		}
	}
	req.OwnerWxID = strings.TrimSpace(req.OwnerWxID)
	if strings.TrimSpace(req.Text) == "" {
		return req, errors.New("text is required")
	}
	return req, nil
}

type SendActionRequest struct {
	Device              string   `json:"device"`
	OwnerWxID           string   `json:"owner_wxid,omitempty"`
	WxIDs               []string `json:"wx_ids"`
	Kind                string   `json:"kind"`
	Text                string   `json:"text,omitempty"`
	MediaKind           string   `json:"media_kind,omitempty"`
	MediaMime           string   `json:"media_mime,omitempty"`
	MediaName           string   `json:"media_name,omitempty"`
	MediaURL            string   `json:"media_url,omitempty"`
	MediaSize           int64    `json:"media_size,omitempty"`
	MediaDurationMS     int      `json:"media_duration_ms,omitempty"`
	DurationMS          int      `json:"duration_ms,omitempty"`
	MediaBase64         string   `json:"media_base64,omitempty"`
	QuoteMsgID          int64    `json:"quote_msg_id,omitempty"`
	QuoteChatRecordID   int64    `json:"quote_chat_record_id,omitempty"`
	QuoteTalker         string   `json:"quote_talker,omitempty"`
	QuoteSenderWxID     string   `json:"quote_sender_wxid,omitempty"`
	AppMsgTitle         string   `json:"appmsg_title,omitempty"`
	AppMsgDescription   string   `json:"appmsg_description,omitempty"`
	AppMsgURL           string   `json:"appmsg_url,omitempty"`
	AppMsgAppName       string   `json:"appmsg_app_name,omitempty"`
	AppMsgThumbURL      string   `json:"appmsg_thumb_url,omitempty"`
	MiniProgramUsername string   `json:"mini_program_username,omitempty"`
	MiniProgramPagePath string   `json:"mini_program_page_path,omitempty"`
	MiniProgramAppID    string   `json:"mini_program_appid,omitempty"`
	MiniProgramIconURL  string   `json:"mini_program_icon_url,omitempty"`
	MiniProgramVersion  int      `json:"mini_program_version,omitempty"`
	MiniProgramType     int      `json:"mini_program_type,omitempty"`
	EmojiMD5            string   `json:"emoji_md5,omitempty"`
	EmojiProductID      string   `json:"emoji_product_id,omitempty"`
	RecordTitle         string   `json:"record_title,omitempty"`
	RecordDescription   string   `json:"record_description,omitempty"`
	RecordItemXML       string   `json:"recorditem_xml,omitempty"`
	ForwardOriginal     bool     `json:"forward_original,omitempty"`
	SourceChatRecordID  int64    `json:"source_chat_record_id,omitempty"`
	SourceChatRecordIDs []int64  `json:"source_chat_record_ids,omitempty"`
	TargetChatRecordID  int64    `json:"target_chat_record_id,omitempty"`
	TargetMsgSvrID      int64    `json:"target_msg_svr_id,omitempty"`
	RevokeTicket        string   `json:"revoke_ticket,omitempty"`
	LocationLatitude    *float64 `json:"location_latitude,omitempty"`
	LocationLongitude   *float64 `json:"location_longitude,omitempty"`
	LocationScale       int      `json:"location_scale,omitempty"`
	LocationLabel       string   `json:"location_label,omitempty"`
	LocationPoiName     string   `json:"location_poiname,omitempty"`
	LocationInfoURL     string   `json:"location_info_url,omitempty"`
	LocationPoiID       string   `json:"location_poi_id,omitempty"`
	LocationFromPoiList bool     `json:"location_from_poi_list,omitempty"`
	LocationPoiTips     string   `json:"location_poi_category_tips,omitempty"`
}

func (req SendActionRequest) Validate(defaultDevice string) (SendActionRequest, error) {
	if strings.TrimSpace(req.Device) == "" {
		req.Device = defaultDevice
	}
	req.Device = strings.TrimSpace(req.Device)
	req.OwnerWxID = strings.TrimSpace(req.OwnerWxID)
	req.Kind = strings.ToLower(strings.TrimSpace(req.Kind))
	if req.Kind == "" {
		req.Kind = OutboxKindText
	}
	if req.Device == "" {
		return req, errors.New("device is required")
	}
	if len(req.WxIDs) == 0 {
		return req, errors.New("wx_ids is required")
	}
	for i := range req.WxIDs {
		req.WxIDs[i] = strings.TrimSpace(req.WxIDs[i])
		if req.WxIDs[i] == "" {
			return req, errors.New("wx_ids cannot contain empty values")
		}
	}
	req.Text = strings.TrimSpace(req.Text)
	req.MediaKind = strings.ToLower(strings.TrimSpace(req.MediaKind))
	req.MediaMime = strings.TrimSpace(req.MediaMime)
	req.MediaName = strings.TrimSpace(req.MediaName)
	req.MediaURL = strings.TrimSpace(req.MediaURL)
	req.MediaBase64 = strings.TrimSpace(req.MediaBase64)
	req.QuoteTalker = strings.TrimSpace(req.QuoteTalker)
	req.QuoteSenderWxID = strings.TrimSpace(req.QuoteSenderWxID)
	req.AppMsgTitle = strings.TrimSpace(req.AppMsgTitle)
	req.AppMsgDescription = strings.TrimSpace(req.AppMsgDescription)
	req.AppMsgURL = strings.TrimSpace(req.AppMsgURL)
	req.AppMsgAppName = strings.TrimSpace(req.AppMsgAppName)
	req.AppMsgThumbURL = strings.TrimSpace(req.AppMsgThumbURL)
	req.MiniProgramUsername = strings.TrimSpace(req.MiniProgramUsername)
	req.MiniProgramPagePath = strings.TrimSpace(req.MiniProgramPagePath)
	req.MiniProgramAppID = strings.TrimSpace(req.MiniProgramAppID)
	req.MiniProgramIconURL = strings.TrimSpace(req.MiniProgramIconURL)
	req.EmojiMD5 = strings.TrimSpace(req.EmojiMD5)
	req.EmojiProductID = strings.TrimSpace(req.EmojiProductID)
	req.RecordTitle = strings.TrimSpace(req.RecordTitle)
	req.RecordDescription = strings.TrimSpace(req.RecordDescription)
	req.RecordItemXML = strings.TrimSpace(req.RecordItemXML)
	req.RevokeTicket = strings.TrimSpace(req.RevokeTicket)
	req.LocationLabel = strings.TrimSpace(req.LocationLabel)
	req.LocationPoiName = strings.TrimSpace(req.LocationPoiName)
	req.LocationInfoURL = strings.TrimSpace(req.LocationInfoURL)
	req.LocationPoiID = strings.TrimSpace(req.LocationPoiID)
	req.LocationPoiTips = strings.TrimSpace(req.LocationPoiTips)
	if req.SourceChatRecordID <= 0 && len(req.SourceChatRecordIDs) == 1 {
		req.SourceChatRecordID = req.SourceChatRecordIDs[0]
	}
	if req.TargetChatRecordID <= 0 {
		req.TargetChatRecordID = firstPositiveLong(req.SourceChatRecordID, req.QuoteChatRecordID, req.QuoteMsgID)
	}
	if req.QuoteMsgID <= 0 {
		req.QuoteMsgID = req.QuoteChatRecordID
	}
	if req.QuoteChatRecordID <= 0 {
		req.QuoteChatRecordID = req.QuoteMsgID
	}
	if req.MediaSize < 0 {
		return req, errors.New("media_size cannot be negative")
	}
	if req.MediaDurationMS <= 0 && req.DurationMS > 0 {
		req.MediaDurationMS = req.DurationMS
	}
	if req.MediaDurationMS < 0 {
		return req, errors.New("media_duration_ms cannot be negative")
	}
	switch req.Kind {
	case OutboxKindText:
		if req.Text == "" {
			return req, errors.New("text is required")
		}
	case OutboxKindImage, OutboxKindVideo, OutboxKindVoice, OutboxKindFile:
		req.MediaKind = req.Kind
		if req.MediaURL == "" && req.MediaBase64 == "" {
			return req, errors.New("media_url or media_base64 is required")
		}
		if req.MediaURL != "" && !strings.HasPrefix(req.MediaURL, "/api/media/") {
			return req, errors.New("media_url must start with /api/media/")
		}
	case OutboxKindEmoji:
		req.MediaKind = OutboxKindEmoji
		if req.SourceChatRecordID <= 0 && req.EmojiMD5 == "" {
			return req, errors.New("source_chat_record_id or emoji_md5 is required")
		}
		if req.Text == "" {
			req.Text = "[表情]"
		}
	case OutboxKindLocation:
		if req.LocationLatitude == nil {
			return req, errors.New("location_latitude is required")
		}
		if req.LocationLongitude == nil {
			return req, errors.New("location_longitude is required")
		}
		if *req.LocationLatitude < -90 || *req.LocationLatitude > 90 {
			return req, errors.New("location_latitude must be between -90 and 90")
		}
		if *req.LocationLongitude < -180 || *req.LocationLongitude > 180 {
			return req, errors.New("location_longitude must be between -180 and 180")
		}
		if req.LocationScale < 0 {
			return req, errors.New("location_scale cannot be negative")
		}
		if req.LocationScale == 0 {
			req.LocationScale = 16
		}
		if req.LocationLabel == "" {
			req.LocationLabel = firstNonEmpty(req.Text, "[位置]")
		}
		if req.LocationPoiName == "" {
			req.LocationPoiName = req.LocationLabel
		}
		if req.Text == "" {
			req.Text = req.LocationLabel
		}
	case OutboxKindQuote:
		if req.Text == "" {
			return req, errors.New("text is required")
		}
		if req.QuoteMsgID <= 0 {
			return req, errors.New("quote_msg_id is required")
		}
	case OutboxKindLink:
		if req.SourceChatRecordID <= 0 {
			if req.AppMsgTitle == "" {
				req.AppMsgTitle = req.Text
			}
			if req.AppMsgTitle == "" {
				return req, errors.New("appmsg_title is required")
			}
			if req.AppMsgURL == "" {
				return req, errors.New("appmsg_url is required")
			}
			if !strings.HasPrefix(strings.ToLower(req.AppMsgURL), "http://") && !strings.HasPrefix(strings.ToLower(req.AppMsgURL), "https://") {
				return req, errors.New("appmsg_url must start with http:// or https://")
			}
		}
		if req.Text == "" {
			req.Text = firstNonEmpty(req.AppMsgTitle, "[链接]")
		}
	case OutboxKindMiniProgram:
		if req.SourceChatRecordID <= 0 {
			if req.AppMsgTitle == "" {
				req.AppMsgTitle = req.Text
			}
			if req.AppMsgTitle == "" {
				return req, errors.New("appmsg_title is required")
			}
			if req.MiniProgramUsername == "" {
				return req, errors.New("mini_program_username is required")
			}
			if req.MiniProgramPagePath == "" {
				return req, errors.New("mini_program_page_path is required")
			}
		}
		if req.Text == "" {
			req.Text = firstNonEmpty(req.AppMsgTitle, "[小程序]")
		}
	case OutboxKindChatHistory:
		if req.ForwardOriginal {
			if req.SourceChatRecordID <= 0 {
				return req, errors.New("source_chat_record_id is required when forward_original is true")
			}
			if len(req.SourceChatRecordIDs) == 0 {
				req.SourceChatRecordIDs = []int64{req.SourceChatRecordID}
			}
		}
		if req.RecordItemXML == "" && len(req.SourceChatRecordIDs) == 0 {
			return req, errors.New("recorditem_xml or source_chat_record_ids is required")
		}
		if req.RecordItemXML != "" {
			if !looksLikeRecordItemXML(req.RecordItemXML) {
				return req, errors.New("recorditem_xml must be recordinfo or recorditem XML")
			}
			if len([]byte(req.RecordItemXML)) > 1024*1024 {
				return req, errors.New("recorditem_xml exceeds 1MB")
			}
		}
		if len(req.SourceChatRecordIDs) > 50 {
			return req, errors.New("source_chat_record_ids cannot exceed 50 items")
		}
		for _, id := range req.SourceChatRecordIDs {
			if id <= 0 {
				return req, errors.New("source_chat_record_ids cannot contain non-positive values")
			}
		}
		if req.ForwardOriginal {
			if req.Text == "" {
				req.Text = firstNonEmpty(req.RecordTitle, "聊天记录")
			}
		} else {
			if req.RecordTitle == "" {
				req.RecordTitle = firstNonEmpty(req.Text, "聊天记录")
			}
			if req.Text == "" {
				req.Text = req.RecordTitle
			}
		}
	case OutboxKindRevoke:
		if req.TargetChatRecordID <= 0 && req.TargetMsgSvrID <= 0 {
			return req, errors.New("target_chat_record_id is required")
		}
		if req.Text == "" {
			req.Text = "[撤回]"
		}
	default:
		return req, errors.New("kind must be text, image, video, voice, file, emoji, location, quote, link, mini_program, chat_history, or revoke")
	}
	return req, nil
}

func looksLikeRecordItemXML(value string) bool {
	value = strings.ToLower(strings.TrimSpace(value))
	return strings.HasPrefix(value, "<recordinfo") ||
		strings.HasPrefix(value, "<recorditem") ||
		strings.HasPrefix(value, "<![cdata[<recordinfo")
}

func firstPositiveLong(values ...int64) int64 {
	for _, value := range values {
		if value > 0 {
			return value
		}
	}
	return 0
}

type ModuleRegistrationRequest struct {
	APIKey   string `json:"api_key"`
	Device   string `json:"device"`
	WxID     string `json:"wxid"`
	Nickname string `json:"nickname"`
}

func (req ModuleRegistrationRequest) Validate(defaultDevice string) (ModuleRegistrationRequest, error) {
	req.APIKey = strings.TrimSpace(req.APIKey)
	req.Device = strings.TrimSpace(req.Device)
	req.WxID = strings.TrimSpace(req.WxID)
	req.Nickname = strings.TrimSpace(req.Nickname)
	if req.APIKey == "" {
		return req, errors.New("api_key is required")
	}
	if req.WxID == "" {
		return req, errors.New("wxid is required")
	}
	return req, nil
}

type ModulePollRequest struct {
	APIKey string `json:"api_key"`
	Device string `json:"device"`
	WxID   string `json:"wxid"`
	Limit  int    `json:"limit"`
}

func (req ModulePollRequest) Validate(defaultDevice string) (ModulePollRequest, error) {
	req.APIKey = strings.TrimSpace(req.APIKey)
	req.Device = strings.TrimSpace(req.Device)
	req.WxID = strings.TrimSpace(req.WxID)
	if req.APIKey == "" {
		return req, errors.New("api_key is required")
	}
	if req.Device == "" {
		req.Device = defaultDevice
	}
	if req.Device == "" {
		return req, errors.New("device is required")
	}
	if req.Limit <= 0 {
		req.Limit = 20
	}
	if req.Limit > 100 {
		req.Limit = 100
	}
	return req, nil
}

type ModuleAckRequest struct {
	APIKey string          `json:"api_key"`
	Device string          `json:"device"`
	WxID   string          `json:"wxid,omitempty"`
	Items  []ModuleAckItem `json:"items"`
	IDs    []int64         `json:"ids,omitempty"`
	Error  string          `json:"error,omitempty"`
}

type ModuleAckItem struct {
	ID           int64  `json:"id"`
	Status       string `json:"status"`
	Error        string `json:"error,omitempty"`
	ChatRecordID int64  `json:"chat_record_id,omitempty"`
}

func (req ModuleAckRequest) Validate(defaultDevice string) (ModuleAckRequest, error) {
	req.APIKey = strings.TrimSpace(req.APIKey)
	req.Device = strings.TrimSpace(req.Device)
	req.WxID = strings.TrimSpace(req.WxID)
	if req.APIKey == "" {
		return req, errors.New("api_key is required")
	}
	if req.Device == "" {
		req.Device = defaultDevice
	}
	if req.Device == "" {
		return req, errors.New("device is required")
	}
	for _, id := range req.IDs {
		if id > 0 {
			req.Items = append(req.Items, ModuleAckItem{ID: id, Status: "sent", Error: req.Error})
		}
	}
	for i := range req.Items {
		req.Items[i].Status = strings.ToLower(strings.TrimSpace(req.Items[i].Status))
		if req.Items[i].Status == "" {
			req.Items[i].Status = "sent"
		}
		if req.Items[i].Status != "sent" && req.Items[i].Status != "failed" {
			return req, errors.New("ack item status must be sent or failed")
		}
		req.Items[i].Error = strings.TrimSpace(req.Items[i].Error)
	}
	if len(req.Items) == 0 {
		return req, errors.New("items or ids is required")
	}
	return req, nil
}

type ModuleOutboxItem struct {
	ID           int64           `json:"id"`
	Device       string          `json:"device"`
	OwnerWxID    string          `json:"owner_wxid,omitempty"`
	WxID         string          `json:"wxid"`
	Kind         string          `json:"kind"`
	Text         string          `json:"text"`
	PayloadJSON  json.RawMessage `json:"payload_json,omitempty"`
	MediaKind    string          `json:"media_kind,omitempty"`
	MediaMime    string          `json:"media_mime,omitempty"`
	MediaName    string          `json:"media_name,omitempty"`
	MediaURL     string          `json:"media_url,omitempty"`
	MediaSize    int64           `json:"media_size,omitempty"`
	ChatRecordID int64           `json:"chat_record_id,omitempty"`
	Status       string          `json:"status"`
	AttemptCount int             `json:"attempt_count"`
	LastError    string          `json:"last_error,omitempty"`
	CreatedAt    string          `json:"created_at,omitempty"`
	UpdatedAt    string          `json:"updated_at,omitempty"`
}

type ModuleContactSnapshotRequest struct {
	APIKey   string          `json:"api_key"`
	Device   string          `json:"device"`
	WxID     string          `json:"wxid"`
	Complete bool            `json:"complete"`
	Contacts []ModuleContact `json:"contacts"`
}

type ModuleContact struct {
	WxID       string `json:"wxid"`
	Nickname   string `json:"nickname,omitempty"`
	Remark     string `json:"remark,omitempty"`
	Alias      string `json:"alias,omitempty"`
	Type       int    `json:"type,omitempty"`
	VerifyFlag int    `json:"verify_flag,omitempty"`
	Chatroom   bool   `json:"chatroom,omitempty"`
	Deleted    bool   `json:"deleted,omitempty"`
}

func (req ModuleContactSnapshotRequest) Validate(defaultDevice string) (ModuleContactSnapshotRequest, error) {
	req.APIKey = strings.TrimSpace(req.APIKey)
	req.Device = strings.TrimSpace(req.Device)
	req.WxID = strings.TrimSpace(req.WxID)
	if req.APIKey == "" {
		return req, errors.New("api_key is required")
	}
	if req.Device == "" {
		req.Device = defaultDevice
	}
	if req.Device == "" {
		return req, errors.New("device is required")
	}
	if len(req.Contacts) > 10000 {
		return req, errors.New("contacts cannot exceed 10000 items")
	}
	out := make([]ModuleContact, 0, len(req.Contacts))
	for _, contact := range req.Contacts {
		contact.WxID = strings.TrimSpace(contact.WxID)
		contact.Nickname = strings.TrimSpace(contact.Nickname)
		contact.Remark = strings.TrimSpace(contact.Remark)
		contact.Alias = strings.TrimSpace(contact.Alias)
		if contact.WxID == "" {
			continue
		}
		out = append(out, contact)
	}
	req.Contacts = out
	return req, nil
}
