package bridge

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"
)

func (s *HTTPServer) sendV1Message(expectedKind string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		r.Body = http.MaxBytesReader(w, r.Body, 16<<20)
		var req SendActionRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid_json", err.Error())
			return
		}
		auth, _ := publicAPIAuthFromContext(r.Context())
		if auth.Device != "" {
			if requestedDevice := strings.TrimSpace(req.Device); requestedDevice != "" && requestedDevice != auth.Device {
				writeError(w, http.StatusForbidden, "device_forbidden", "API key cannot send from this device")
				return
			}
			currentOwnerWxID := strings.TrimSpace(s.service.deviceWxID(auth.Device))
			if currentOwnerWxID == "" {
				writeError(w, http.StatusConflict, "owner_wxid_unbound", "device current owner_wxid is not registered; wait for module registration")
				return
			}
			if requestedOwnerWxID := strings.TrimSpace(req.OwnerWxID); requestedOwnerWxID != "" && requestedOwnerWxID != currentOwnerWxID {
				writeError(w, http.StatusForbidden, "owner_wxid_forbidden", "API key cannot send from this owner_wxid")
				return
			}
			req.Device = auth.Device
			req.OwnerWxID = currentOwnerWxID
		}
		if auth.Device == "" && strings.TrimSpace(req.OwnerWxID) == "" {
			writeError(w, http.StatusBadRequest, "owner_wxid_required", "owner_wxid is required for public sends")
			return
		}
		if expectedKind != "" {
			gotKind := strings.ToLower(strings.TrimSpace(req.Kind))
			if gotKind != "" && gotKind != expectedKind {
				writeError(w, http.StatusBadRequest, "kind_mismatch", "kind must match endpoint type")
				return
			}
			req.Kind = expectedKind
		}
		items, err := s.service.SendActions(r.Context(), req)
		if err != nil {
			writeError(w, http.StatusBadRequest, "send_failed", err.Error())
			return
		}
		writeJSON(w, http.StatusOK, publicSendResponse(firstNonEmpty(req.Kind, OutboxKindText), items, publicSendWarnings(req)))
	}
}

func (s *HTTPServer) getV1OutboxItem(w http.ResponseWriter, r *http.Request) {
	rawID := strings.Trim(strings.TrimPrefix(r.URL.Path, "/api/v1/outbox/"), "/")
	if rawID == "" || strings.Contains(rawID, "/") {
		writeError(w, http.StatusBadRequest, "invalid_outbox_id", "outbox id is required")
		return
	}
	id, err := strconv.ParseInt(rawID, 10, 64)
	if err != nil || id <= 0 {
		writeError(w, http.StatusBadRequest, "invalid_outbox_id", "outbox id must be a positive integer")
		return
	}
	item, err := s.service.OutboxItem(r.Context(), id)
	if errors.Is(err, ErrOutboxItemNotFound) {
		writeError(w, http.StatusNotFound, "outbox_not_found", "outbox item not found")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, "outbox_read_failed", err.Error())
		return
	}
	if !publicAuthCanAccessDevice(r.Context(), item.Device) {
		writeError(w, http.StatusNotFound, "outbox_not_found", "outbox item not found")
		return
	}
	writeJSON(w, http.StatusOK, PublicOutboxResponse{
		OK:              true,
		ProtocolVersion: "v1",
		Outbox:          publicOutboxEnvelope(item),
	})
}

type PublicSendResponse struct {
	OK              bool                   `json:"ok"`
	ProtocolVersion string                 `json:"protocol_version"`
	Kind            string                 `json:"kind"`
	OutboxID        int64                  `json:"outbox_id"`
	OutboxIDs       []int64                `json:"outbox_ids,omitempty"`
	ChatRecordID    int64                  `json:"chat_record_id,omitempty"`
	StatusURL       string                 `json:"status_url"`
	Outbox          PublicOutboxEnvelope   `json:"outbox"`
	Outboxes        []PublicOutboxEnvelope `json:"outboxes,omitempty"`
	Warnings        []string               `json:"warnings,omitempty"`
}

type PublicOutboxResponse struct {
	OK              bool                 `json:"ok"`
	ProtocolVersion string               `json:"protocol_version"`
	Outbox          PublicOutboxEnvelope `json:"outbox"`
}

type PublicOutboxEnvelope struct {
	ID           int64                `json:"id"`
	Device       string               `json:"device"`
	OwnerWxID    string               `json:"owner_wxid,omitempty"`
	TargetWxID   string               `json:"target_wxid"`
	Kind         string               `json:"kind"`
	Text         string               `json:"text,omitempty"`
	Status       string               `json:"status"`
	StatusURL    string               `json:"status_url"`
	ChatRecordID int64                `json:"chat_record_id,omitempty"`
	Media        []PublicMessageMedia `json:"media,omitempty"`
	AttemptCount int                  `json:"attempt_count,omitempty"`
	LastError    string               `json:"last_error,omitempty"`
	CreatedAt    string               `json:"created_at,omitempty"`
	UpdatedAt    string               `json:"updated_at,omitempty"`
}

type PublicCapabilitiesResponse struct {
	OK              bool                        `json:"ok"`
	Protocol        string                      `json:"protocol"`
	ProtocolVersion string                      `json:"protocol_version"`
	Envelope        PublicEnvelopeContract      `json:"envelope"`
	Capabilities    []PublicMessageCapability   `json:"capabilities"`
	Transports      []PublicTransportCapability `json:"transports"`
	Limits          PublicProtocolLimits        `json:"limits"`
}

type PublicEnvelopeContract struct {
	Name   string                `json:"name"`
	Fields []PublicEnvelopeField `json:"fields"`
}

type PublicEnvelopeField struct {
	Name        string `json:"name"`
	Type        string `json:"type"`
	Required    bool   `json:"required"`
	Description string `json:"description"`
}

type PublicMessageCapability struct {
	Kind           string   `json:"kind"`
	Subtype        string   `json:"subtype,omitempty"`
	SendKind       string   `json:"send_kind,omitempty"`
	Title          string   `json:"title"`
	InboundStatus  string   `json:"inbound_status"`
	OutboundStatus string   `json:"outbound_status"`
	Verification   string   `json:"verification"`
	MessageTypes   []int32  `json:"message_types,omitempty"`
	AppMsgTypes    []int32  `json:"appmsg_types,omitempty"`
	SendEndpoint   string   `json:"send_endpoint,omitempty"`
	RequiredFields []string `json:"required_fields,omitempty"`
	OptionalFields []string `json:"optional_fields,omitempty"`
	Unsupported    []string `json:"unsupported,omitempty"`
	Notes          []string `json:"notes,omitempty"`
}

type PublicTransportCapability struct {
	Name      string `json:"name"`
	Direction string `json:"direction"`
	Endpoint  string `json:"endpoint"`
	Auth      string `json:"auth"`
	Notes     string `json:"notes,omitempty"`
}

type PublicProtocolLimits struct {
	MaxRequestBytes       int64 `json:"max_request_bytes"`
	MaxRecordItemXMLBytes int64 `json:"max_recorditem_xml_bytes"`
	MaxChatHistoryItems   int   `json:"max_chat_history_items"`
	MaxWebSocketReplay    int   `json:"max_websocket_replay"`
}

func (s *HTTPServer) publicCapabilities(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, publicCapabilitiesResponse())
}

func publicCapabilitiesResponse() PublicCapabilitiesResponse {
	return PublicCapabilitiesResponse{
		OK:              true,
		Protocol:        "wechat-observatory",
		ProtocolVersion: "v1",
		Envelope: PublicEnvelopeContract{
			Name: "MessageEnvelope v1",
			Fields: []PublicEnvelopeField{
				{Name: "id", Type: "string", Description: "公开消息 ID，用于 after_id/before_id 游标。"},
				{Name: "device", Type: "string", Required: true, Description: "手机模块设备名。"},
				{Name: "owner_wxid", Type: "string", Description: "当前登录微信 wxid。"},
				{Name: "direction", Type: "string", Required: true, Description: "recv 或 sent。"},
				{Name: "kind", Type: "string", Required: true, Description: "稳定消息类型，不能识别时为 unknown。"},
				{Name: "subtype", Type: "string", Description: "appmsg、引用、支付等业务子类型。"},
				{Name: "message_type", Type: "int32", Required: true, Description: "微信原始 message.type。"},
				{Name: "appmsg_type", Type: "int32", Description: "微信 appmsg type，仅 appmsg 类消息存在。"},
				{Name: "chat_id", Type: "string", Required: true, Description: "稳定会话 ID；发送目标必须使用 wxid 或 room id，不使用显示名。"},
				{Name: "chat_kind", Type: "string", Required: true, Description: "direct、room 或 unknown。"},
				{Name: "from_wxid", Type: "string", Description: "消息来源 wxid。"},
				{Name: "to_wxid", Type: "string", Description: "消息接收 wxid。"},
				{Name: "room_id", Type: "string", Description: "群聊 room id。"},
				{Name: "sender_wxid", Type: "string", Description: "群聊中的实际发送者 wxid；私聊可为空。"},
				{Name: "text", Type: "string", Description: "展示文本、标题摘要或降级说明。"},
				{Name: "media[]", Type: "array<PublicMessageMedia>", Description: "媒体资源列表；不包含 media_base64。"},
				{Name: "media[].kind", Type: "string", Description: "image、voice、video、file、emoji 等媒体类型。"},
				{Name: "media[].mime", Type: "string", Description: "服务端识别或上报的媒体 MIME。"},
				{Name: "media[].name", Type: "string", Description: "文件名或派生媒体名。"},
				{Name: "media[].url", Type: "string", Description: "服务端保存后的 /api/media/... 相对 URL。"},
				{Name: "media[].size", Type: "int64", Description: "媒体字节数。"},
				{Name: "media[].opaque", Type: "boolean", Description: "true 表示微信本地 opaque 原始附件，不保证可直接预览；表情优先看 appmsg。"},
				{Name: "appmsg", Type: "PublicAppMsgEnvelope", Description: "链接、小程序、文件、引用、聊天记录等卡片结构。"},
				{Name: "appmsg.type", Type: "int32", Description: "微信 appmsg type。"},
				{Name: "appmsg.subtype", Type: "string", Description: "link、mini_program、quote、chat_history、file、emoji 等业务子类型。"},
				{Name: "appmsg.title", Type: "string", Description: "卡片标题；表情场景通常为 MD5。"},
				{Name: "appmsg.description", Type: "string", Description: "卡片描述或结构摘要。"},
				{Name: "appmsg.url", Type: "string", Description: "链接、小程序或表情 CDN URL。"},
				{Name: "appmsg.file_name", Type: "string", Description: "文件名或表情派生文件名。"},
				{Name: "appmsg.app_name", Type: "string", Description: "卡片来源应用名。"},
				{Name: "location", Type: "PublicLocationEnvelope", Description: "位置消息结构。"},
				{Name: "location.latitude", Type: "number", Description: "位置纬度。"},
				{Name: "location.longitude", Type: "number", Description: "位置经度。"},
				{Name: "location.scale", Type: "integer", Description: "地图缩放。"},
				{Name: "location.label", Type: "string", Description: "位置展示文本。"},
				{Name: "location.poiname", Type: "string", Description: "POI 名称。"},
				{Name: "unsupported", Type: "string[]", Description: "明确未支持或被降级的字段/行为。"},
				{Name: "evidence", Type: "string[]", Description: "字段来源证据，例如 message.type、raw_xml 节点或本地 DB 记录。"},
				{Name: "created_at", Type: "string", Description: "服务端记录时间。"},
				{Name: "chat_display_name", Type: "string", Description: "仅用于展示的联系人或群名，不能作为发送目标。"},
			},
		},
		Capabilities: []PublicMessageCapability{
			{Kind: MessageKindText, SendKind: OutboxKindText, Title: "文本", InboundStatus: "stable", OutboundStatus: "stable", Verification: "user_confirmed", MessageTypes: []int32{1}, SendEndpoint: "/api/v1/messages/text", RequiredFields: []string{"wx_ids", "text"}, OptionalFields: []string{"device", "owner_wxid"}},
			{Kind: MessageKindImage, SendKind: OutboxKindImage, Title: "图片", InboundStatus: "stable", OutboundStatus: "stable", Verification: "user_confirmed", MessageTypes: []int32{3}, SendEndpoint: "/api/v1/messages/image", RequiredFields: []string{"wx_ids", "media_url|media_base64"}, OptionalFields: []string{"media_name", "media_mime", "text"}},
			{Kind: MessageKindVideo, SendKind: OutboxKindVideo, Title: "视频", InboundStatus: "stable", OutboundStatus: "stable", Verification: "user_confirmed", MessageTypes: []int32{43, 62}, SendEndpoint: "/api/v1/messages/video", RequiredFields: []string{"wx_ids", "media_url|media_base64"}, OptionalFields: []string{"media_name", "media_mime", "text"}},
			{Kind: MessageKindVoice, SendKind: OutboxKindVoice, Title: "语音", InboundStatus: "stable", OutboundStatus: "stable", Verification: "user_confirmed", MessageTypes: []int32{34}, SendEndpoint: "/api/v1/messages/voice", RequiredFields: []string{"wx_ids", "media_url|media_base64"}, Notes: []string{"已验证可听音频；继续积累 AMR/SILK 时长和群聊样本。"}},
			{Kind: MessageKindFile, SendKind: OutboxKindFile, Title: "文件", InboundStatus: "stable", OutboundStatus: "stable", Verification: "user_confirmed", MessageTypes: []int32{MessageTypeAppMsg, MessageTypeFileTransfer}, AppMsgTypes: []int32{6}, SendEndpoint: "/api/v1/messages/file", RequiredFields: []string{"wx_ids", "media_url|media_base64", "media_name"}},
			{Kind: MessageKindEmoji, SendKind: OutboxKindEmoji, Title: "表情", InboundStatus: "structured", OutboundStatus: "stable", Verification: "db_verified", MessageTypes: []int32{47}, SendEndpoint: "/api/v1/messages/emoji", RequiredFields: []string{"wx_ids", "source_chat_record_id|emoji_md5"}, Notes: []string{"入站稳定字段是 appmsg.title/MD5 与 appmsg.url/CDN；本地 media[] 可能带 opaque=true。"}},
			{Kind: MessageKindLocation, SendKind: OutboxKindLocation, Title: "位置", InboundStatus: "structured", OutboundStatus: "stable", Verification: "user_confirmed", MessageTypes: []int32{48}, SendEndpoint: "/api/v1/messages/location", RequiredFields: []string{"wx_ids", "location_latitude", "location_longitude"}, OptionalFields: []string{"location_label", "location_poiname", "location_scale"}},
			{Kind: MessageKindAppMsg, Subtype: "quote", SendKind: OutboxKindQuote, Title: "引用", InboundStatus: "structured", OutboundStatus: "sample_only", Verification: "sample_only", MessageTypes: []int32{MessageTypeQuote}, AppMsgTypes: []int32{57}, SendEndpoint: "/api/v1/messages/quote", RequiredFields: []string{"wx_ids", "text", "quote_msg_id"}, Notes: []string{"群聊引用还需要更多样本。"}},
			{Kind: MessageKindAppMsg, Subtype: "link", SendKind: OutboxKindLink, Title: "链接", InboundStatus: "structured", OutboundStatus: "stable", Verification: "db_verified", MessageTypes: []int32{MessageTypeAppMsg}, AppMsgTypes: []int32{5}, SendEndpoint: "/api/v1/messages/link", RequiredFields: []string{"wx_ids", "source_chat_record_id|appmsg_title+appmsg_url"}},
			{Kind: MessageKindAppMsg, Subtype: "mini_program", SendKind: OutboxKindMiniProgram, Title: "小程序", InboundStatus: "structured", OutboundStatus: "source_forward_stable", Verification: "db_verified", MessageTypes: []int32{MessageTypeAppMsg}, AppMsgTypes: []int32{33, 36}, SendEndpoint: "/api/v1/messages/mini-program", RequiredFields: []string{"wx_ids", "source_chat_record_id|appmsg_title+mini_program_username+mini_program_page_path"}, Notes: []string{"源消息转发已通过真实设备验证；直接构造仍需更多真实样本。"}},
			{Kind: MessageKindChatHistory, SendKind: OutboxKindChatHistory, Title: "聊天记录", InboundStatus: "basic", OutboundStatus: "source_forward_only", Verification: "sample_only", MessageTypes: []int32{MessageTypeAppMsg}, AppMsgTypes: []int32{19}, SendEndpoint: "/api/v1/messages/chat-history", RequiredFields: []string{"wx_ids", "recorditem_xml|source_chat_record_ids|forward_original+source_chat_record_id"}, Unsupported: []string{"arbitrary_raw_xml_send"}, Notes: []string{"仅稳定支持已有来源消息转发；嵌套 recorditem 摘要仍在收口。"}},
			{Kind: MessageKindPayment, Title: "支付", InboundStatus: "parse_only", OutboundStatus: "unsupported", Verification: "sample_only", MessageTypes: []int32{MessageTypeTransfer, MessageTypeRedPacket, MessageTypeAppMsg}, AppMsgTypes: []int32{AppMsgTypeTransfer, AppMsgTypeRedPacket}, Unsupported: []string{"payment_outbound_unsupported", "payment_sensitive_fields_redacted"}, Notes: []string{"红包、转账只做安全识别和脱敏，不提供发送自动化。"}},
			{Kind: MessageKindSystem, Subtype: SystemSubtypeRevoke, SendKind: OutboxKindRevoke, Title: "撤回", InboundStatus: "structured", OutboundStatus: "private_verified", Verification: "user_confirmed", MessageTypes: []int32{10000}, SendEndpoint: "/api/v1/messages/revoke", RequiredFields: []string{"wx_ids", "target_chat_record_id"}, Notes: []string{"私聊实机已验证；群聊跟随撤回仍需继续积累样本。", "只支持撤回当前账号已发送且本地 message 表仍可查到的消息。"}},
			{Kind: MessageKindSystem, Title: "系统消息", InboundStatus: "parse_only", OutboundStatus: "non_goal", Verification: "parse_only", MessageTypes: []int32{10000}},
			{Kind: MessageKindUnknown, Subtype: "unknown-business", Title: "未知高位业务类型", InboundStatus: "preserved", OutboundStatus: "unsupported", Verification: "sample_only", Unsupported: []string{"message_type:*"}, Notes: []string{"保留 message_type、unsupported 和 evidence，确认前不猜测业务含义。"}},
		},
		Transports: []PublicTransportCapability{
			{Name: "http", Direction: "outbound", Endpoint: "/api/v1/messages/*", Auth: "api_key_or_admin_password"},
			{Name: "http", Direction: "query", Endpoint: "/api/v1/messages", Auth: "api_key_or_admin_password"},
			{Name: "websocket", Direction: "inbound", Endpoint: "/api/v1/ws", Auth: "api_key_or_admin_password", Notes: "推荐给外部机器人框架订阅实时消息。"},
			{Name: "sse", Direction: "inbound", Endpoint: "/api/live/events", Auth: "admin_password", Notes: "保留给现有 Web 管理台。"},
		},
		Limits: PublicProtocolLimits{
			MaxRequestBytes:       16 << 20,
			MaxRecordItemXMLBytes: 1024 * 1024,
			MaxChatHistoryItems:   50,
			MaxWebSocketReplay:    200,
		},
	}
}

func publicSendResponse(kind string, items []ModuleOutboxItem, warnings []string) PublicSendResponse {
	outboxes := make([]PublicOutboxEnvelope, 0, len(items))
	outboxIDs := make([]int64, 0, len(items))
	for _, item := range items {
		outboxes = append(outboxes, publicOutboxEnvelope(item))
		outboxIDs = append(outboxIDs, item.ID)
	}
	if len(items) == 0 {
		return PublicSendResponse{OK: true, ProtocolVersion: "v1", Kind: firstNonEmpty(kind, OutboxKindText)}
	}
	first := items[0]
	return PublicSendResponse{
		OK:              true,
		ProtocolVersion: "v1",
		Kind:            firstNonEmpty(kind, first.Kind, OutboxKindText),
		OutboxID:        first.ID,
		OutboxIDs:       outboxIDs,
		ChatRecordID:    first.ChatRecordID,
		StatusURL:       publicOutboxStatusURL(first.ID),
		Outbox:          outboxes[0],
		Outboxes:        outboxes,
		Warnings:        warnings,
	}
}

func publicOutboxEnvelope(item ModuleOutboxItem) PublicOutboxEnvelope {
	return PublicOutboxEnvelope{
		ID:           item.ID,
		Device:       item.Device,
		OwnerWxID:    item.OwnerWxID,
		TargetWxID:   item.WxID,
		Kind:         firstNonEmpty(item.Kind, OutboxKindText),
		Text:         item.Text,
		Status:       item.Status,
		StatusURL:    publicOutboxStatusURL(item.ID),
		ChatRecordID: item.ChatRecordID,
		Media:        publicMessageMedia(item.MediaKind, item.MediaMime, item.MediaName, item.MediaURL, item.MediaSize),
		AttemptCount: item.AttemptCount,
		LastError:    item.LastError,
		CreatedAt:    item.CreatedAt,
		UpdatedAt:    item.UpdatedAt,
	}
}

func publicOutboxStatusURL(id int64) string {
	if id <= 0 {
		return ""
	}
	return "/api/v1/outbox/" + strconv.FormatInt(id, 10)
}

func publicSendWarnings(req SendActionRequest) []string {
	if len(req.WxIDs) <= 1 {
		return nil
	}
	return []string{"multiple_targets_outboxes_returned", "singular_outbox_fields_reference_first_target"}
}

type PublicMessagesResponse struct {
	OK              bool                    `json:"ok"`
	ProtocolVersion string                  `json:"protocol_version"`
	Messages        []PublicMessageEnvelope `json:"messages"`
	NextCursor      int64                   `json:"next_cursor,omitempty"`
	NextCursorParam string                  `json:"next_cursor_param,omitempty"`
	CursorField     string                  `json:"cursor_field,omitempty"`
	HasMore         bool                    `json:"has_more"`
}

type PublicMessageEnvelope struct {
	ID              string                  `json:"id,omitempty"`
	EventID         int64                   `json:"event_id,omitempty"`
	ChatRecordID    int64                   `json:"chat_record_id,omitempty"`
	Device          string                  `json:"device"`
	OwnerWxID       string                  `json:"owner_wxid,omitempty"`
	Direction       string                  `json:"direction"`
	Kind            string                  `json:"kind"`
	Subtype         string                  `json:"subtype,omitempty"`
	MessageType     int32                   `json:"message_type"`
	AppMsgType      int32                   `json:"appmsg_type,omitempty"`
	ChatID          string                  `json:"chat_id"`
	ChatKind        string                  `json:"chat_kind"`
	FromWxID        string                  `json:"from_wxid,omitempty"`
	ToWxID          string                  `json:"to_wxid,omitempty"`
	RoomID          string                  `json:"room_id,omitempty"`
	SenderWxID      string                  `json:"sender_wxid,omitempty"`
	Text            string                  `json:"text,omitempty"`
	Media           []PublicMessageMedia    `json:"media,omitempty"`
	AppMsg          *PublicAppMsgEnvelope   `json:"appmsg,omitempty"`
	Location        *PublicLocationEnvelope `json:"location,omitempty"`
	System          *PublicSystemEnvelope   `json:"system,omitempty"`
	Unsupported     []string                `json:"unsupported,omitempty"`
	Evidence        []string                `json:"evidence,omitempty"`
	CreateTime      int64                   `json:"create_time,omitempty"`
	CreatedAt       string                  `json:"created_at,omitempty"`
	ChatDisplayName string                  `json:"chat_display_name,omitempty"`
}

type PublicMessageMedia struct {
	Kind   string `json:"kind,omitempty"`
	Mime   string `json:"mime,omitempty"`
	Name   string `json:"name,omitempty"`
	URL    string `json:"url,omitempty"`
	Size   int64  `json:"size,omitempty"`
	Opaque bool   `json:"opaque,omitempty"`
}

type PublicAppMsgEnvelope struct {
	Type        int32  `json:"type,omitempty"`
	Subtype     string `json:"subtype,omitempty"`
	Title       string `json:"title,omitempty"`
	Description string `json:"description,omitempty"`
	URL         string `json:"url,omitempty"`
	FileName    string `json:"file_name,omitempty"`
	AppName     string `json:"app_name,omitempty"`
}

type PublicLocationEnvelope struct {
	Latitude        *float64 `json:"latitude,omitempty"`
	Longitude       *float64 `json:"longitude,omitempty"`
	Scale           int      `json:"scale,omitempty"`
	Label           string   `json:"label,omitempty"`
	PoiName         string   `json:"poiname,omitempty"`
	InfoURL         string   `json:"info_url,omitempty"`
	PoiID           string   `json:"poi_id,omitempty"`
	FromPoiList     bool     `json:"from_poi_list,omitempty"`
	PoiCategoryTips string   `json:"poi_category_tips,omitempty"`
}

type PublicSystemEnvelope struct {
	Subtype     string `json:"subtype,omitempty"`
	Session     string `json:"session,omitempty"`
	MsgID       string `json:"msg_id,omitempty"`
	NewMsgID    string `json:"new_msg_id,omitempty"`
	Replacement string `json:"replacement,omitempty"`
}

func (s *HTTPServer) getV1Messages(w http.ResponseWriter, r *http.Request) {
	reader := s.service.AdminReader()
	if reader == nil {
		writeJSON(w, http.StatusOK, PublicMessagesResponse{OK: true, ProtocolVersion: "v1", Messages: []PublicMessageEnvelope{}, CursorField: "id"})
		return
	}
	clientLimit := queryLimit(r, 100)
	afterIDSet := strings.TrimSpace(r.URL.Query().Get("after_id")) != ""
	afterID, ok := parsePositiveInt64Query(w, r, "after_id")
	if !ok {
		return
	}
	beforeIDSet := strings.TrimSpace(r.URL.Query().Get("before_id")) != ""
	beforeID, ok := parsePositiveInt64Query(w, r, "before_id")
	if !ok {
		return
	}
	if afterIDSet && beforeIDSet {
		writeError(w, http.StatusBadRequest, "cursor_conflict", "after_id and before_id cannot be used together")
		return
	}
	filter := MessageFilter{
		Device:     strings.TrimSpace(r.URL.Query().Get("device")),
		WxID:       strings.TrimSpace(r.URL.Query().Get("wxid")),
		OwnerWxID:  strings.TrimSpace(r.URL.Query().Get("owner_wxid")),
		ChatID:     strings.TrimSpace(r.URL.Query().Get("chat_id")),
		ChatKind:   strings.TrimSpace(r.URL.Query().Get("chat_kind")),
		AfterID:    afterID,
		AfterIDSet: afterIDSet,
		BeforeID:   beforeID,
		Limit:      clientLimit + 1,
	}
	if filter.OwnerWxID == "" && filter.Device != "" {
		filter.OwnerWxID = s.service.deviceWxID(filter.Device)
	}
	var filterOK bool
	filter.Device, filter.OwnerWxID, filterOK = s.bindPublicDeviceFilter(w, r, filter.Device, filter.OwnerWxID)
	if !filterOK {
		return
	}
	stored, err := reader.ListMessages(r.Context(), filter)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "admin_read_failed", err.Error())
		return
	}
	messages, nextCursor, hasMore := publicMessagePage(stored, clientLimit)
	writeJSON(w, http.StatusOK, PublicMessagesResponse{
		OK:              true,
		ProtocolVersion: "v1",
		Messages:        messages,
		NextCursor:      nextCursor,
		NextCursorParam: publicNextCursorParam(afterIDSet),
		CursorField:     "id",
		HasMore:         hasMore,
	})
}

func parsePositiveInt64Query(w http.ResponseWriter, r *http.Request, name string) (int64, bool) {
	raw := strings.TrimSpace(r.URL.Query().Get(name))
	if raw == "" {
		return 0, true
	}
	value, err := strconv.ParseInt(raw, 10, 64)
	if err != nil || value < 0 {
		writeError(w, http.StatusBadRequest, "invalid_cursor", name+" must be a non-negative integer")
		return 0, false
	}
	return value, true
}

func publicMessagePage(stored []StoredEventView, limit int) ([]PublicMessageEnvelope, int64, bool) {
	if limit <= 0 {
		limit = 100
	}
	hasMore := len(stored) > limit
	if hasMore {
		stored = stored[:limit]
	}
	messages := make([]PublicMessageEnvelope, 0, len(stored))
	for _, item := range stored {
		messages = append(messages, publicStoredMessageEnvelope(item))
	}
	if len(stored) == 0 {
		return messages, 0, false
	}
	return messages, stored[len(stored)-1].ID, hasMore
}

func publicNextCursorParam(afterIDSet bool) string {
	if afterIDSet {
		return "after_id"
	}
	return "before_id"
}

func publicStoredMessageEnvelope(item StoredEventView) PublicMessageEnvelope {
	chatID := strings.TrimSpace(item.ChatID)
	chatKind := strings.TrimSpace(item.ChatKind)
	if chatID == "" || chatKind == "" {
		event := MessageEvent{
			Direction: Direction(item.Direction),
			From:      item.FromWxID,
			To:        item.ToWxID,
			RoomID:    item.RoomID,
			Sender:    item.SenderWxID,
		}.Normalize()
		chatID = firstNonEmpty(chatID, event.ChatID())
		chatKind = firstNonEmpty(chatKind, string(event.Kind()))
	}
	kind := publicStoredMessageKind(item)
	envelope := PublicMessageEnvelope{
		ID:              publicStoredEnvelopeID(item),
		EventID:         item.EventID,
		ChatRecordID:    item.ChatRecordID,
		Device:          item.Device,
		OwnerWxID:       item.OwnerWxID,
		Direction:       item.Direction,
		Kind:            kind,
		Subtype:         publicStoredMessageSubtype(item),
		MessageType:     item.MessageType,
		AppMsgType:      item.AppMsgType,
		ChatID:          chatID,
		ChatKind:        firstNonEmpty(chatKind, string(ChatKindUnknown)),
		FromWxID:        item.FromWxID,
		ToWxID:          item.ToWxID,
		RoomID:          item.RoomID,
		SenderWxID:      item.SenderWxID,
		Text:            item.Text,
		Unsupported:     item.Unsupported,
		Evidence:        item.Evidence,
		CreateTime:      item.CreateTime,
		CreatedAt:       item.CreatedAt,
		ChatDisplayName: item.ChatDisplayName,
	}
	envelope.Media = publicMessageMedia(item.MediaKind, item.MediaMime, item.MediaName, item.MediaURL, item.MediaSize)
	if kind == MessageKindSystem {
		envelope.System = publicSystemEnvelope(item.AppMsgSubtype, item.AppMsgURL, item.AppMsgFileName, item.AppMsgAppName, item.AppMsgTitle)
	} else {
		envelope.AppMsg = publicAppMsgEnvelope(item.AppMsgType, item.AppMsgSubtype, item.AppMsgTitle, item.AppMsgDescription, item.AppMsgURL, item.AppMsgFileName, item.AppMsgAppName)
	}
	envelope.Location = publicLocationEnvelope(item.LocationLatitude, item.LocationLongitude, item.LocationScale, item.LocationLabel, item.LocationPoiName, item.LocationInfoURL, item.LocationPoiID, item.LocationFromPoiList, item.LocationPoiTips)
	return envelope
}

func publicStoredMessageKind(item StoredEventView) string {
	kind := strings.TrimSpace(item.MessageKind)
	if item.MessageType == MessageTypeAppMsg && item.AppMsgType != 0 {
		appKind := messageKindForAppMsg(item.AppMsgType)
		if appKind != MessageKindAppMsg && (kind == "" || kind == MessageKindUnknown || kind == MessageKindAppMsg) {
			return appKind
		}
	}
	typeKind := messageKindForType(item.MessageType)
	if typeKind != MessageKindUnknown && (kind == "" || kind == MessageKindUnknown) {
		return typeKind
	}
	if kind != "" {
		return kind
	}
	return typeKind
}

func publicStoredMessageSubtype(item StoredEventView) string {
	if subtype := strings.TrimSpace(item.AppMsgSubtype); subtype != "" {
		return subtype
	}
	if paymentSubtype := paymentSubtypeForMessageType(item.MessageType); paymentSubtype != "" {
		return paymentSubtype
	}
	if item.MessageType == MessageTypeAppMsg && item.AppMsgType != 0 {
		return appMsgSubtype(item.AppMsgType)
	}
	return ""
}

func publicEventMessageEnvelope(event MessageEvent) PublicMessageEnvelope {
	event.APIKey = ""
	event.MediaBase64 = ""
	event.RawXML = ""
	event = event.Normalize()
	kind := firstNonEmpty(event.MessageKind, messageKindForType(event.MessageType))
	envelope := PublicMessageEnvelope{
		ID:           publicEventEnvelopeID(event),
		EventID:      event.EventID,
		ChatRecordID: event.ChatRecordID,
		Device:       event.Device,
		OwnerWxID:    event.OwnerWxID,
		Direction:    string(event.Direction),
		Kind:         kind,
		Subtype:      event.AppMsgSubtype,
		MessageType:  event.MessageType,
		AppMsgType:   event.AppMsgType,
		ChatID:       event.ChatID(),
		ChatKind:     string(event.Kind()),
		FromWxID:     event.From,
		ToWxID:       event.To,
		RoomID:       event.RoomID,
		SenderWxID:   event.Sender,
		Text:         event.Text,
		Unsupported:  event.Unsupported,
		Evidence:     event.Evidence,
		CreateTime:   event.Timestamp(),
	}
	envelope.Media = publicMessageMedia(event.MediaKind, event.MediaMime, event.MediaName, event.MediaURL, event.MediaSize)
	if kind == MessageKindSystem {
		envelope.System = publicSystemEnvelope(event.AppMsgSubtype, event.AppMsgURL, event.AppMsgFileName, event.AppMsgAppName, event.AppMsgTitle)
	} else {
		envelope.AppMsg = publicAppMsgEnvelope(event.AppMsgType, event.AppMsgSubtype, event.AppMsgTitle, event.AppMsgDescription, event.AppMsgURL, event.AppMsgFileName, event.AppMsgAppName)
	}
	envelope.Location = publicLocationEnvelope(event.LocationLatitude, event.LocationLongitude, event.LocationScale, event.LocationLabel, event.LocationPoiName, event.LocationInfoURL, event.LocationPoiID, event.LocationFromPoiList, event.LocationPoiTips)
	return envelope
}

func publicStoredEnvelopeID(item StoredEventView) string {
	if item.ID > 0 {
		return strconv.FormatInt(item.ID, 10)
	}
	return strings.TrimSpace(item.SourceID)
}

func publicEventEnvelopeID(event MessageEvent) string {
	if strings.TrimSpace(event.ID) != "" {
		return strings.TrimSpace(event.ID)
	}
	if event.EventID > 0 {
		return "event:" + strconv.FormatInt(event.EventID, 10)
	}
	if event.ChatRecordID > 0 {
		return "chat_record:" + strconv.FormatInt(event.ChatRecordID, 10)
	}
	return ""
}

func publicMessageMedia(kind string, mime string, name string, url string, size int64) []PublicMessageMedia {
	if strings.TrimSpace(kind) == "" && strings.TrimSpace(mime) == "" && strings.TrimSpace(name) == "" && strings.TrimSpace(url) == "" && size <= 0 {
		return nil
	}
	kind = strings.TrimSpace(kind)
	mime = strings.TrimSpace(mime)
	return []PublicMessageMedia{{
		Kind: kind,
		Mime: mime,
		Name: strings.TrimSpace(name),
		URL:  strings.TrimSpace(url),
		Size: size,
		Opaque: kind == MessageKindEmoji &&
			(strings.EqualFold(mime, "application/octet-stream") || strings.TrimSpace(mime) == ""),
	}}
}

func publicAppMsgEnvelope(appMsgType int32, subtype string, title string, description string, url string, fileName string, appName string) *PublicAppMsgEnvelope {
	if appMsgType == 0 && strings.TrimSpace(subtype) == "" && strings.TrimSpace(title) == "" && strings.TrimSpace(description) == "" && strings.TrimSpace(url) == "" && strings.TrimSpace(fileName) == "" && strings.TrimSpace(appName) == "" {
		return nil
	}
	return &PublicAppMsgEnvelope{
		Type:        appMsgType,
		Subtype:     strings.TrimSpace(subtype),
		Title:       strings.TrimSpace(title),
		Description: strings.TrimSpace(description),
		URL:         strings.TrimSpace(url),
		FileName:    strings.TrimSpace(fileName),
		AppName:     strings.TrimSpace(appName),
	}
}

func publicSystemEnvelope(subtype string, session string, msgID string, newMsgID string, replacement string) *PublicSystemEnvelope {
	if strings.TrimSpace(subtype) == "" &&
		strings.TrimSpace(session) == "" &&
		strings.TrimSpace(msgID) == "" &&
		strings.TrimSpace(newMsgID) == "" &&
		strings.TrimSpace(replacement) == "" {
		return nil
	}
	return &PublicSystemEnvelope{
		Subtype:     strings.TrimSpace(subtype),
		Session:     strings.TrimSpace(session),
		MsgID:       strings.TrimSpace(msgID),
		NewMsgID:    strings.TrimSpace(newMsgID),
		Replacement: strings.TrimSpace(replacement),
	}
}

func publicLocationEnvelope(latitude *float64, longitude *float64, scale int, label string, poiName string, infoURL string, poiID string, fromPoiList bool, poiTips string) *PublicLocationEnvelope {
	if latitude == nil && longitude == nil && scale == 0 && strings.TrimSpace(label) == "" && strings.TrimSpace(poiName) == "" && strings.TrimSpace(infoURL) == "" && strings.TrimSpace(poiID) == "" && !fromPoiList && strings.TrimSpace(poiTips) == "" {
		return nil
	}
	return &PublicLocationEnvelope{
		Latitude:        latitude,
		Longitude:       longitude,
		Scale:           scale,
		Label:           strings.TrimSpace(label),
		PoiName:         strings.TrimSpace(poiName),
		InfoURL:         strings.TrimSpace(infoURL),
		PoiID:           strings.TrimSpace(poiID),
		FromPoiList:     fromPoiList,
		PoiCategoryTips: strings.TrimSpace(poiTips),
	}
}
