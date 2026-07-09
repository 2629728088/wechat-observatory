package bridge

import (
	"context"
	"strings"

	"wechat-observatory/internal/config"
)

type Persistence interface {
	UpdateDeviceIdentity(ctx context.Context, deviceName string, wxid string, nickname string) error
	RecordInboundEvent(ctx context.Context, event MessageEvent) error
	RecordOutboundEvent(ctx context.Context, event MessageEvent) error
}

type MessageEventLookup interface {
	HasMessageEvent(ctx context.Context, event MessageEvent) (bool, error)
}

type MessageEventMediaLookup interface {
	HasMessageEventMedia(ctx context.Context, event MessageEvent) (bool, error)
}

type DeviceLocator interface {
	LookupDeviceByWxID(ctx context.Context, wxid string) (config.Device, bool, error)
}

type Outbox interface {
	EnqueueReply(ctx context.Context, action ReplyAction) (ModuleOutboxItem, error)
	PollReplyActions(ctx context.Context, req ModulePollRequest) ([]ModuleOutboxItem, error)
	AckReplyActions(ctx context.Context, req ModuleAckRequest) ([]ModuleOutboxItem, error)
	GetReplyAction(ctx context.Context, id int64) (ModuleOutboxItem, error)
}

type ModuleActivityRecorder interface {
	RecordModuleActivity(ctx context.Context, activity ModuleActivity) error
}

type ModuleContactStore interface {
	RecordModuleContacts(ctx context.Context, snapshot ModuleContactSnapshotRequest) error
}

type AdminWriter interface {
	UpsertAPIKey(ctx context.Context, key config.APIKey) error
	DeleteAPIKey(ctx context.Context, code string) error
	SetAPIKeyEnabled(ctx context.Context, code string, enabled bool) error
	UpsertDevice(ctx context.Context, device config.Device) error
}

type AdminReader interface {
	ListAPIKeys(ctx context.Context, limit int) ([]APIKeyView, error)
	ListStoredEvents(ctx context.Context, limit int) ([]StoredEventView, error)
	ListMessages(ctx context.Context, filter MessageFilter) ([]StoredEventView, error)
	ListModuleStatuses(ctx context.Context) ([]ModuleStatusView, error)
	ListModuleContacts(ctx context.Context, filter ModuleContactFilter) ([]ModuleContactView, error)
}

type APIKeyUpsertRequest struct {
	Code     string `json:"code,omitempty"`
	APIKey   string `json:"api_key,omitempty"`
	Device   string `json:"device,omitempty"`
	Nickname string `json:"nickname,omitempty"`
}

type DeviceUpsertRequest struct {
	Name     string `json:"name"`
	Nickname string `json:"nickname,omitempty"`
}

type APIKeyView struct {
	Code      string `json:"code"`
	APIKey    string `json:"api_key"`
	Device    string `json:"device,omitempty"`
	Nickname  string `json:"nickname,omitempty"`
	Enabled   bool   `json:"enabled"`
	CreatedAt string `json:"created_at,omitempty"`
	UpdatedAt string `json:"updated_at,omitempty"`
}

type StoredEventView struct {
	ID                  int64    `json:"id"`
	SourceID            string   `json:"source_id,omitempty"`
	EventID             int64    `json:"event_id,omitempty"`
	ChatRecordID        int64    `json:"chat_record_id,omitempty"`
	Device              string   `json:"device"`
	OwnerWxID           string   `json:"owner_wxid,omitempty"`
	Direction           string   `json:"direction"`
	FromWxID            string   `json:"from_wxid,omitempty"`
	ToWxID              string   `json:"to_wxid,omitempty"`
	RoomID              string   `json:"room_id,omitempty"`
	SenderWxID          string   `json:"sender_wxid,omitempty"`
	Text                string   `json:"text"`
	MessageType         int32    `json:"message_type"`
	MessageKind         string   `json:"kind,omitempty"`
	AppMsgType          int32    `json:"appmsg_type,omitempty"`
	AppMsgSubtype       string   `json:"appmsg_subtype,omitempty"`
	AppMsgTitle         string   `json:"appmsg_title,omitempty"`
	AppMsgDescription   string   `json:"appmsg_description,omitempty"`
	AppMsgURL           string   `json:"appmsg_url,omitempty"`
	AppMsgFileName      string   `json:"appmsg_file_name,omitempty"`
	AppMsgAppName       string   `json:"appmsg_app_name,omitempty"`
	Unsupported         []string `json:"unsupported,omitempty"`
	Evidence            []string `json:"evidence,omitempty"`
	LocationLatitude    *float64 `json:"location_latitude,omitempty"`
	LocationLongitude   *float64 `json:"location_longitude,omitempty"`
	LocationScale       int      `json:"location_scale,omitempty"`
	LocationLabel       string   `json:"location_label,omitempty"`
	LocationPoiName     string   `json:"location_poiname,omitempty"`
	LocationInfoURL     string   `json:"location_info_url,omitempty"`
	LocationPoiID       string   `json:"location_poi_id,omitempty"`
	LocationFromPoiList bool     `json:"location_from_poi_list,omitempty"`
	LocationPoiTips     string   `json:"location_poi_category_tips,omitempty"`
	MediaKind           string   `json:"media_kind,omitempty"`
	MediaMime           string   `json:"media_mime,omitempty"`
	MediaName           string   `json:"media_name,omitempty"`
	MediaURL            string   `json:"media_url,omitempty"`
	MediaSize           int64    `json:"media_size,omitempty"`
	RawProvider         string   `json:"raw_provider,omitempty"`
	ChatID              string   `json:"chat_id,omitempty"`
	ChatKind            string   `json:"chat_kind,omitempty"`
	ChatName            string   `json:"chat_name,omitempty"`
	ChatRemark          string   `json:"chat_remark,omitempty"`
	ChatAlias           string   `json:"chat_alias,omitempty"`
	ChatDisplayName     string   `json:"chat_display_name,omitempty"`
	ChatDeleted         bool     `json:"chat_deleted,omitempty"`
	CreateTime          int64    `json:"create_time"`
	CreatedAt           string   `json:"created_at,omitempty"`
}

type MessageFilter struct {
	Device     string
	WxID       string
	OwnerWxID  string
	ChatID     string
	ChatKind   string
	AfterID    int64
	AfterIDSet bool
	BeforeID   int64
	Limit      int
}

type ModuleActivity struct {
	Device         string
	WxID           string
	APIKey         string
	Kind           string
	PollLimit      int
	PollItemCount  int
	AckSentCount   int
	AckFailedCount int
	LastError      string
}

type ModuleContactFilter struct {
	Device         string
	OwnerWxID      string
	Query          string
	IncludeDeleted bool
	Limit          int
}

type ModuleContactView struct {
	ID         int64  `json:"id"`
	Device     string `json:"device"`
	OwnerWxID  string `json:"owner_wxid,omitempty"`
	WxID       string `json:"wxid"`
	Nickname   string `json:"nickname,omitempty"`
	Remark     string `json:"remark,omitempty"`
	Alias      string `json:"alias,omitempty"`
	Type       int    `json:"type,omitempty"`
	VerifyFlag int    `json:"verify_flag,omitempty"`
	Chatroom   bool   `json:"chatroom"`
	Deleted    bool   `json:"deleted"`
	LastSeenAt string `json:"last_seen_at,omitempty"`
	UpdatedAt  string `json:"updated_at,omitempty"`
}

type ModuleStatusView struct {
	Device             string `json:"device"`
	DeviceWxID         string `json:"device_wxid,omitempty"`
	DeviceNickname     string `json:"device_nickname,omitempty"`
	Enabled            bool   `json:"enabled"`
	Registered         bool   `json:"-"`
	RuntimeStatus      string `json:"runtime_status"`
	PendingOutbox      int64  `json:"pending_outbox"`
	LeasedOutbox       int64  `json:"leased_outbox"`
	SentOutbox         int64  `json:"sent_outbox"`
	FailedOutbox       int64  `json:"failed_outbox"`
	LastOutboxID       int64  `json:"last_outbox_id,omitempty"`
	LastOutboxStatus   string `json:"last_outbox_status,omitempty"`
	LastOutboxError    string `json:"last_outbox_error,omitempty"`
	LastOutboxUpdated  string `json:"last_outbox_updated_at,omitempty"`
	LastEventAt        string `json:"last_event_at,omitempty"`
	LastInboundAt      string `json:"last_inbound_at,omitempty"`
	LastOutboundAckAt  string `json:"last_outbound_ack_at,omitempty"`
	LastRegisterAt     string `json:"last_register_at,omitempty"`
	LastPollAt         string `json:"last_poll_at,omitempty"`
	LastAckAt          string `json:"last_ack_at,omitempty"`
	LastPollLimit      int    `json:"last_poll_limit,omitempty"`
	LastPollItemCount  int    `json:"last_poll_item_count,omitempty"`
	LastAckSentCount   int    `json:"last_ack_sent_count,omitempty"`
	LastAckFailedCount int    `json:"last_ack_failed_count,omitempty"`
	RuntimeUpdatedAt   string `json:"runtime_updated_at,omitempty"`
	DeviceUpdatedAt    string `json:"device_updated_at,omitempty"`
}

func (v *ModuleStatusView) NormalizeRuntimeStatus() {
	switch {
	case !v.Enabled:
		v.RuntimeStatus = "disabled"
	case !v.Registered:
		v.RuntimeStatus = "unregistered"
	case v.LeasedOutbox > 0:
		v.RuntimeStatus = "sending"
	case v.PendingOutbox > 0:
		v.RuntimeStatus = "pending"
	default:
		v.RuntimeStatus = "ready"
	}
}

type Option func(*Service)

func WithPersistence(persistence Persistence) Option {
	return func(s *Service) {
		s.persistence = persistence
	}
}

func WithOutbox(outbox Outbox) Option {
	return func(s *Service) {
		if outbox != nil {
			s.outbox = outbox
		}
	}
}

func WithAdminReader(reader AdminReader) Option {
	return func(s *Service) {
		s.adminReader = reader
	}
}

func WithMediaDir(mediaDir string) Option {
	return func(s *Service) {
		s.mediaDir = strings.TrimSpace(mediaDir)
	}
}
