package bridge

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"

	"wechat-observatory/internal/config"
)

type Service struct {
	cfg         Config
	hub         *Hub
	persistence Persistence
	outbox      Outbox
	adminReader AdminReader
	mediaDir    string

	mu               sync.RWMutex
	nextChatRecordID int64

	outboxNotifyMu sync.Mutex
	outboxNotify   map[string]map[chan struct{}]struct{}
}

const maxOutboxPollBatch = 20

type Config struct {
	DefaultDevice string
	MediaDir      string
	Devices       map[string]config.Device
	APIKeys       map[string]config.APIKey
}

func NewService(cfg Config, opts ...Option) *Service {
	service := &Service{
		cfg:              cfg,
		hub:              NewHub(500),
		outbox:           NewMemoryOutbox(),
		outboxNotify:     map[string]map[chan struct{}]struct{}{},
		nextChatRecordID: time.Now().Unix() * 1000,
		mediaDir:         strings.TrimSpace(cfg.MediaDir),
	}
	for _, opt := range opts {
		opt(service)
	}
	return service
}

func (s *Service) Hub() *Hub {
	return s.hub
}

func (s *Service) DefaultDevice() string {
	return s.cfg.DefaultDevice
}

func (s *Service) Device(name string) (config.Device, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	device, ok := s.cfg.Devices[name]
	return device, ok
}

func (s *Service) Devices() []config.Device {
	s.mu.RLock()
	defer s.mu.RUnlock()
	out := make([]config.Device, 0, len(s.cfg.Devices))
	for _, device := range s.cfg.Devices {
		out = append(out, device)
	}
	return out
}

func (s *Service) APIKeys() []config.APIKey {
	s.mu.RLock()
	defer s.mu.RUnlock()
	out := make([]config.APIKey, 0, len(s.cfg.APIKeys))
	for _, key := range s.cfg.APIKeys {
		out = append(out, key)
	}
	return out
}

func (s *Service) AdminReader() AdminReader {
	return s.adminReader
}

func (s *Service) AdminWriter() AdminWriter {
	if writer, ok := s.persistence.(AdminWriter); ok {
		return writer
	}
	return nil
}

func (s *Service) UpsertAPIKey(ctx context.Context, req APIKeyUpsertRequest) (APIKeyView, error) {
	key := config.APIKey{
		Code:     firstNonEmpty(req.APIKey, req.Code),
		Device:   strings.TrimSpace(req.Device),
		Nickname: strings.TrimSpace(req.Nickname),
	}

	s.mu.Lock()
	defer s.mu.Unlock()
	if s.cfg.APIKeys == nil {
		s.cfg.APIKeys = map[string]config.APIKey{}
	}
	if key.Code == "" {
		for i := 0; i < 5; i++ {
			key.Code = generateAPIKey()
			if _, exists := s.cfg.APIKeys[key.Code]; !exists {
				break
			}
		}
		if _, exists := s.cfg.APIKeys[key.Code]; exists {
			return APIKeyView{}, fmt.Errorf("failed to generate unique api key")
		}
	}
	if key.Device == "" {
		key.Device = apiKeyDeviceName(key)
	}
	if writer := s.AdminWriter(); writer != nil {
		if err := writer.UpsertAPIKey(ctx, key); err != nil {
			return APIKeyView{}, err
		}
	}
	s.cfg.APIKeys[key.Code] = key
	return apiKeyView(key), nil
}

func (s *Service) DeleteAPIKey(ctx context.Context, apiKey string) error {
	apiKey = strings.TrimSpace(apiKey)
	if apiKey == "" {
		return fmt.Errorf("api key is required")
	}

	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.cfg.APIKeys[apiKey]; !ok {
		return fmt.Errorf("api key %q not found", apiKey)
	}
	if writer := s.AdminWriter(); writer != nil {
		if err := writer.DeleteAPIKey(ctx, apiKey); err != nil {
			return err
		}
	}
	delete(s.cfg.APIKeys, apiKey)
	return nil
}

func (s *Service) SetAPIKeyEnabled(ctx context.Context, apiKey string, enabled bool) (APIKeyView, error) {
	apiKey = strings.TrimSpace(apiKey)
	if apiKey == "" {
		return APIKeyView{}, fmt.Errorf("api key is required")
	}

	s.mu.Lock()
	defer s.mu.Unlock()
	key, ok := s.cfg.APIKeys[apiKey]
	if !ok {
		return APIKeyView{}, fmt.Errorf("api key %q not found", apiKey)
	}
	key.Disabled = !enabled
	if writer := s.AdminWriter(); writer != nil {
		if err := writer.SetAPIKeyEnabled(ctx, apiKey, enabled); err != nil {
			return APIKeyView{}, err
		}
	}
	s.cfg.APIKeys[apiKey] = key
	return apiKeyView(key), nil
}

func (s *Service) UpsertDevice(ctx context.Context, req DeviceUpsertRequest) (ModuleStatusView, error) {
	name := strings.TrimSpace(req.Name)
	if name == "" {
		return ModuleStatusView{}, fmt.Errorf("device name is required")
	}
	nickname := strings.TrimSpace(req.Nickname)

	s.mu.Lock()
	defer s.mu.Unlock()
	device, ok := s.cfg.Devices[name]
	if !ok {
		return ModuleStatusView{}, fmt.Errorf("unknown device %q", name)
	}
	device.Nickname = firstNonEmpty(nickname, device.Nickname, device.Name)
	if writer := s.AdminWriter(); writer != nil {
		if err := writer.UpsertDevice(ctx, device); err != nil {
			return ModuleStatusView{}, err
		}
	}
	s.cfg.Devices[name] = device
	status := ModuleStatusView{
		Device:         device.Name,
		DeviceWxID:     device.WxID,
		DeviceNickname: device.Nickname,
		Enabled:        true,
	}
	if strings.TrimSpace(device.WxID) != "" {
		status.Registered = true
	}
	status.NormalizeRuntimeStatus()
	return status, nil
}

func (s *Service) RegisterModule(ctx context.Context, req ModuleRegistrationRequest) (*ModuleRegistrationResult, error) {
	req, err := req.Validate(s.cfg.DefaultDevice)
	if err != nil {
		return nil, err
	}

	s.mu.Lock()
	defer s.mu.Unlock()
	key, ok := s.cfg.APIKeys[req.APIKey]
	if !ok {
		return nil, fmt.Errorf("invalid api key")
	}
	if key.Disabled {
		return nil, fmt.Errorf("api key disabled")
	}
	deviceName := apiKeyDeviceName(key)
	req.Device = deviceName
	if strings.TrimSpace(req.Device) == "" {
		return nil, fmt.Errorf("device is required")
	}
	if key.Device != req.Device {
		key.Device = req.Device
		if writer := s.AdminWriter(); writer != nil {
			if err := writer.UpsertAPIKey(ctx, key); err != nil {
				return nil, err
			}
		}
		s.cfg.APIKeys[key.Code] = key
	}
	device := s.cfg.Devices[req.Device]
	if strings.TrimSpace(device.Name) == "" {
		device.Name = req.Device
		device.Timeout = 5 * time.Second
	}
	device.WxID = req.WxID
	device.Nickname = firstNonEmpty(device.Nickname, key.Nickname, req.Nickname, device.Name)
	if s.persistence != nil {
		if err := s.persistence.UpdateDeviceIdentity(ctx, req.Device, device.WxID, device.Nickname); err != nil {
			return nil, err
		}
	}
	s.recordModuleActivity(ctx, ModuleActivity{
		Device: req.Device,
		WxID:   req.WxID,
		APIKey: req.APIKey,
		Kind:   "register",
	})
	s.cfg.Devices[req.Device] = device
	return &ModuleRegistrationResult{
		Device: moduleDeviceView(device),
	}, nil
}

func (s *Service) Ingest(ctx context.Context, event MessageEvent) (*IngestResult, error) {
	auth, err := s.authorizeModuleAPIKey(event.APIKey)
	if err != nil {
		return nil, err
	}
	if event.Device == "" {
		event.Device = auth.Device
	}
	if event.Direction == "" {
		event.Direction = DirectionRecv
	}
	if event.MessageType == 0 {
		event.MessageType = 1
	}
	if event.CreateTime == 0 {
		event.CreateTime = time.Now().Unix()
	}
	event = event.Normalize()
	mediaError := ""
	if stored, err := s.StoreMediaAttachment(event); err != nil {
		event.MediaBase64 = ""
		mediaError = err.Error()
	} else {
		event = stored
	}
	event.APIKey = ""
	if err := event.Validate(); err != nil {
		return nil, err
	}
	event.Device = auth.Device
	if ownerWxID := s.deviceWxID(event.Device); ownerWxID != "" {
		event.OwnerWxID = ownerWxID
	}

	alreadyStored, lookupErr := s.messageEventAlreadyStored(ctx, event)
	mediaEnriched, mediaLookupErr := s.messageEventMediaEnriched(ctx, event, alreadyStored)
	result := &IngestResult{}
	if mediaError != "" {
		result.PersistenceError = "media: " + mediaError
	}
	if lookupErr != nil {
		result.PersistenceError = appendPersistenceError(result.PersistenceError, lookupErr.Error())
	}
	if mediaLookupErr != nil {
		result.PersistenceError = appendPersistenceError(result.PersistenceError, mediaLookupErr.Error())
	}
	recorded := true
	if s.persistence != nil {
		if err := s.persistence.RecordInboundEvent(ctx, event); err != nil {
			recorded = false
			result.PersistenceError = appendPersistenceError(result.PersistenceError, err.Error())
		}
	}
	if !alreadyStored || mediaEnriched || !recorded {
		s.hub.Publish(event)
		result.Published = true
	}
	return result, nil
}

func (s *Service) messageEventAlreadyStored(ctx context.Context, event MessageEvent) (bool, error) {
	if s.persistence == nil || event.ChatRecordID <= 0 || messageEventAlwaysPublishes(event) {
		return false, nil
	}
	lookup, ok := s.persistence.(MessageEventLookup)
	if !ok {
		return false, nil
	}
	return lookup.HasMessageEvent(ctx, event)
}

func (s *Service) messageEventMediaEnriched(ctx context.Context, event MessageEvent, alreadyStored bool) (bool, error) {
	if !alreadyStored || strings.TrimSpace(event.MediaURL) == "" {
		return false, nil
	}
	lookup, ok := s.persistence.(MessageEventMediaLookup)
	if !ok {
		return false, nil
	}
	hasMedia, err := lookup.HasMessageEventMedia(ctx, event)
	if err != nil {
		return false, err
	}
	return !hasMedia, nil
}

func messageEventAlwaysPublishes(event MessageEvent) bool {
	return strings.TrimSpace(event.MessageKind) == MessageKindSystem &&
		strings.TrimSpace(event.AppMsgSubtype) == SystemSubtypeRevoke
}

func appendPersistenceError(current string, next string) string {
	next = strings.TrimSpace(next)
	if next == "" {
		return current
	}
	if current == "" {
		return next
	}
	return current + "; " + next
}

func (s *Service) SendText(ctx context.Context, req SendTextRequest) (int64, error) {
	req, err := req.Validate(s.cfg.DefaultDevice)
	if err != nil {
		return 0, err
	}
	return s.SendAction(ctx, SendActionRequest{
		Device:    req.Device,
		OwnerWxID: req.OwnerWxID,
		WxIDs:     req.WxIDs,
		Kind:      OutboxKindText,
		Text:      req.Text,
	})
}

func (s *Service) SendAction(ctx context.Context, req SendActionRequest) (int64, error) {
	items, err := s.SendActions(ctx, req)
	if err != nil {
		return 0, err
	}
	if len(items) == 0 {
		return 0, nil
	}
	return items[0].ID, nil
}

func (s *Service) SendActions(ctx context.Context, req SendActionRequest) ([]ModuleOutboxItem, error) {
	req, err := req.Validate(s.cfg.DefaultDevice)
	if err != nil {
		return nil, err
	}
	if _, ok := s.Device(req.Device); !ok {
		return nil, fmt.Errorf("unknown device %q", req.Device)
	}
	ownerWxID := s.deviceWxID(req.Device)
	if req.OwnerWxID != "" && req.OwnerWxID != ownerWxID {
		return nil, fmt.Errorf("send owner wxid %q is not current device wxid", req.OwnerWxID)
	}
	if isMediaOutboxKind(req.Kind) {
		req, err = s.prepareMediaAction(req, ownerWxID)
		if err != nil {
			return nil, err
		}
	}
	payloadJSON, err := actionPayloadJSON(req)
	if err != nil {
		return nil, err
	}
	items := make([]ModuleOutboxItem, 0, len(req.WxIDs))
	for _, wxid := range req.WxIDs {
		item, err := s.outbox.EnqueueReply(ctx, ReplyAction{
			Device:      req.Device,
			OwnerWxID:   ownerWxID,
			WxID:        wxid,
			Kind:        req.Kind,
			Text:        req.Text,
			PayloadJSON: payloadJSON,
			MediaKind:   req.MediaKind,
			MediaMime:   req.MediaMime,
			MediaName:   req.MediaName,
			MediaURL:    req.MediaURL,
			MediaSize:   req.MediaSize,
		})
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	s.notifyOutbox(req.Device)
	return items, nil
}

func (s *Service) OutboxItem(ctx context.Context, id int64) (ModuleOutboxItem, error) {
	if id <= 0 {
		return ModuleOutboxItem{}, ErrOutboxItemNotFound
	}
	return s.outbox.GetReplyAction(ctx, id)
}

func (s *Service) prepareMediaAction(req SendActionRequest, ownerWxID string) (SendActionRequest, error) {
	if req.Text == "" {
		req.Text = defaultOutboxMediaText(req.Kind)
	}
	req.MediaKind = req.Kind
	if req.MediaBase64 != "" {
		stored, err := s.StoreMediaAttachment(MessageEvent{
			Device:      req.Device,
			OwnerWxID:   ownerWxID,
			Text:        req.Text,
			MessageType: outboundMessageType(ModuleOutboxItem{Kind: req.Kind, MediaKind: req.MediaKind}),
			MediaKind:   req.MediaKind,
			MediaMime:   req.MediaMime,
			MediaName:   req.MediaName,
			MediaBase64: req.MediaBase64,
			CreateTime:  time.Now().Unix(),
			Direction:   DirectionSent,
		})
		if err != nil {
			return req, err
		}
		req.MediaMime = stored.MediaMime
		req.MediaName = stored.MediaName
		req.MediaURL = stored.MediaURL
		req.MediaSize = stored.MediaSize
		req.MediaBase64 = ""
	}
	if req.MediaURL == "" {
		return req, fmt.Errorf("media_url is required")
	}
	fullPath, err := s.MediaFilePath(req.MediaURL)
	if err != nil {
		return req, err
	}
	info, err := os.Stat(fullPath)
	if err != nil {
		return req, fmt.Errorf("media file not found")
	}
	if info.Size() <= 0 {
		return req, fmt.Errorf("media file is empty")
	}
	if req.MediaSize <= 0 {
		req.MediaSize = info.Size()
	}
	return req, nil
}

func actionPayloadJSON(req SendActionRequest) (json.RawMessage, error) {
	payload := map[string]any{
		"kind": req.Kind,
	}
	if req.Text != "" {
		payload["text"] = req.Text
	}
	if req.MediaKind != "" {
		payload["media_kind"] = req.MediaKind
	}
	if req.MediaMime != "" {
		payload["media_mime"] = req.MediaMime
	}
	if req.MediaName != "" {
		payload["media_name"] = req.MediaName
	}
	if req.MediaURL != "" {
		payload["media_url"] = req.MediaURL
	}
	if req.MediaSize > 0 {
		payload["media_size"] = req.MediaSize
	}
	if req.MediaDurationMS > 0 {
		payload["media_duration_ms"] = req.MediaDurationMS
		payload["duration_ms"] = req.MediaDurationMS
	}
	if req.LocationLatitude != nil {
		payload["location_latitude"] = *req.LocationLatitude
	}
	if req.LocationLongitude != nil {
		payload["location_longitude"] = *req.LocationLongitude
	}
	if req.LocationScale > 0 {
		payload["location_scale"] = req.LocationScale
	}
	if req.LocationLabel != "" {
		payload["location_label"] = req.LocationLabel
	}
	if req.LocationPoiName != "" {
		payload["location_poiname"] = req.LocationPoiName
	}
	if req.LocationInfoURL != "" {
		payload["location_info_url"] = req.LocationInfoURL
	}
	if req.LocationPoiID != "" {
		payload["location_poi_id"] = req.LocationPoiID
	}
	if req.LocationFromPoiList {
		payload["location_from_poi_list"] = req.LocationFromPoiList
	}
	if req.LocationPoiTips != "" {
		payload["location_poi_category_tips"] = req.LocationPoiTips
	}
	if req.QuoteMsgID > 0 {
		payload["quote_msg_id"] = req.QuoteMsgID
		payload["quote_chat_record_id"] = req.QuoteChatRecordID
	}
	if req.QuoteTalker != "" {
		payload["quote_talker"] = req.QuoteTalker
	}
	if req.QuoteSenderWxID != "" {
		payload["quote_sender_wxid"] = req.QuoteSenderWxID
	}
	if req.AppMsgTitle != "" {
		payload["appmsg_title"] = req.AppMsgTitle
	}
	if req.AppMsgDescription != "" {
		payload["appmsg_description"] = req.AppMsgDescription
	}
	if req.AppMsgURL != "" {
		payload["appmsg_url"] = req.AppMsgURL
	}
	if req.AppMsgAppName != "" {
		payload["appmsg_app_name"] = req.AppMsgAppName
	}
	if req.AppMsgThumbURL != "" {
		payload["appmsg_thumb_url"] = req.AppMsgThumbURL
	}
	if req.MiniProgramUsername != "" {
		payload["mini_program_username"] = req.MiniProgramUsername
	}
	if req.MiniProgramPagePath != "" {
		payload["mini_program_page_path"] = req.MiniProgramPagePath
	}
	if req.MiniProgramAppID != "" {
		payload["mini_program_appid"] = req.MiniProgramAppID
	}
	if req.MiniProgramIconURL != "" {
		payload["mini_program_icon_url"] = req.MiniProgramIconURL
	}
	if req.MiniProgramVersion > 0 {
		payload["mini_program_version"] = req.MiniProgramVersion
	}
	if req.MiniProgramType > 0 {
		payload["mini_program_type"] = req.MiniProgramType
	}
	if req.EmojiMD5 != "" {
		payload["emoji_md5"] = req.EmojiMD5
	}
	if req.EmojiProductID != "" {
		payload["emoji_product_id"] = req.EmojiProductID
	}
	if req.RecordTitle != "" {
		payload["record_title"] = req.RecordTitle
	}
	if req.RecordDescription != "" {
		payload["record_description"] = req.RecordDescription
	}
	if req.RecordItemXML != "" {
		payload["recorditem_xml"] = req.RecordItemXML
	}
	if req.ForwardOriginal {
		payload["forward_original"] = req.ForwardOriginal
	}
	if req.SourceChatRecordID > 0 {
		payload["source_chat_record_id"] = req.SourceChatRecordID
	}
	if len(req.SourceChatRecordIDs) > 0 {
		payload["source_chat_record_ids"] = req.SourceChatRecordIDs
	}
	if req.TargetChatRecordID > 0 {
		payload["target_chat_record_id"] = req.TargetChatRecordID
	}
	if req.TargetMsgSvrID > 0 {
		payload["target_msg_svr_id"] = req.TargetMsgSvrID
	}
	if req.RevokeTicket != "" {
		payload["revoke_ticket"] = req.RevokeTicket
	}
	raw, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}
	return raw, nil
}

type chatHistoryOutboxPayload struct {
	RecordTitle       string `json:"record_title"`
	RecordDescription string `json:"record_description"`
	RecordItemXML     string `json:"recorditem_xml"`
}

type appMsgOutboxPayload struct {
	AppMsgTitle         string `json:"appmsg_title"`
	AppMsgDescription   string `json:"appmsg_description"`
	AppMsgURL           string `json:"appmsg_url"`
	AppMsgAppName       string `json:"appmsg_app_name"`
	MiniProgramUsername string `json:"mini_program_username"`
	MiniProgramPagePath string `json:"mini_program_page_path"`
	MiniProgramAppID    string `json:"mini_program_appid"`
}

type locationOutboxPayload struct {
	LocationLabel   string `json:"location_label"`
	LocationPoiName string `json:"location_poiname"`
}

type revokeOutboxPayload struct {
	TargetChatRecordID int64  `json:"target_chat_record_id"`
	TargetMsgSvrID     int64  `json:"target_msg_svr_id"`
	RevokeTicket       string `json:"revoke_ticket"`
}

func appMsgPayloadFromRaw(raw json.RawMessage) appMsgOutboxPayload {
	if len(raw) == 0 {
		return appMsgOutboxPayload{}
	}
	var payload appMsgOutboxPayload
	if err := json.Unmarshal(raw, &payload); err != nil {
		return appMsgOutboxPayload{}
	}
	payload.AppMsgTitle = strings.TrimSpace(payload.AppMsgTitle)
	payload.AppMsgDescription = strings.TrimSpace(payload.AppMsgDescription)
	payload.AppMsgURL = strings.TrimSpace(payload.AppMsgURL)
	payload.AppMsgAppName = strings.TrimSpace(payload.AppMsgAppName)
	payload.MiniProgramUsername = strings.TrimSpace(payload.MiniProgramUsername)
	payload.MiniProgramPagePath = strings.TrimSpace(payload.MiniProgramPagePath)
	payload.MiniProgramAppID = strings.TrimSpace(payload.MiniProgramAppID)
	return payload
}

func chatHistoryPayloadFromRaw(raw json.RawMessage) chatHistoryOutboxPayload {
	if len(raw) == 0 {
		return chatHistoryOutboxPayload{}
	}
	var payload chatHistoryOutboxPayload
	if err := json.Unmarshal(raw, &payload); err != nil {
		return chatHistoryOutboxPayload{}
	}
	payload.RecordTitle = strings.TrimSpace(payload.RecordTitle)
	payload.RecordDescription = strings.TrimSpace(payload.RecordDescription)
	payload.RecordItemXML = strings.TrimSpace(payload.RecordItemXML)
	return payload
}

func locationPayloadFromRaw(raw json.RawMessage) locationOutboxPayload {
	if len(raw) == 0 {
		return locationOutboxPayload{}
	}
	var payload locationOutboxPayload
	if err := json.Unmarshal(raw, &payload); err != nil {
		return locationOutboxPayload{}
	}
	payload.LocationLabel = strings.TrimSpace(payload.LocationLabel)
	payload.LocationPoiName = strings.TrimSpace(payload.LocationPoiName)
	return payload
}

func revokePayloadFromRaw(raw json.RawMessage) revokeOutboxPayload {
	if len(raw) == 0 {
		return revokeOutboxPayload{}
	}
	var payload revokeOutboxPayload
	if err := json.Unmarshal(raw, &payload); err != nil {
		return revokeOutboxPayload{}
	}
	payload.RevokeTicket = strings.TrimSpace(payload.RevokeTicket)
	return payload
}

func (s *Service) PollOutbox(ctx context.Context, req ModulePollRequest) ([]ModuleOutboxItem, error) {
	req, err := req.Validate(s.cfg.DefaultDevice)
	if err != nil {
		return nil, err
	}
	auth, err := s.authorizeModuleAPIKey(req.APIKey)
	if err != nil {
		return nil, err
	}
	req.Device = auth.Device
	currentWxID := s.deviceWxID(req.Device)
	if req.WxID == "" {
		req.WxID = currentWxID
	}
	if currentWxID != "" && req.WxID != "" && currentWxID != req.WxID {
		return nil, fmt.Errorf("module wxid %q is not current device wxid", req.WxID)
	}
	if req.Limit > maxOutboxPollBatch {
		req.Limit = maxOutboxPollBatch
	}
	items, err := s.outbox.PollReplyActions(ctx, req)
	if err != nil {
		return nil, err
	}
	s.recordModuleActivity(ctx, ModuleActivity{
		Device:        req.Device,
		WxID:          req.WxID,
		APIKey:        req.APIKey,
		Kind:          "poll",
		PollLimit:     req.Limit,
		PollItemCount: len(items),
	})
	return items, nil
}

func (s *Service) AckOutbox(ctx context.Context, req ModuleAckRequest) ([]ModuleOutboxItem, error) {
	req, err := req.Validate(s.cfg.DefaultDevice)
	if err != nil {
		return nil, err
	}
	auth, err := s.authorizeModuleAPIKey(req.APIKey)
	if err != nil {
		return nil, err
	}
	req.Device = auth.Device
	currentWxID := s.deviceWxID(req.Device)
	if req.WxID == "" {
		req.WxID = currentWxID
	}
	if currentWxID != "" && req.WxID != "" && currentWxID != req.WxID {
		return nil, fmt.Errorf("module wxid %q is not current device wxid", req.WxID)
	}
	items, err := s.outbox.AckReplyActions(ctx, req)
	if err != nil {
		return nil, err
	}
	s.recordModuleActivity(ctx, ackActivity(req))
	acks := map[int64]ModuleAckItem{}
	for _, ack := range req.Items {
		acks[ack.ID] = ack
	}
	for _, item := range items {
		ack := acks[item.ID]
		if ack.Status != "sent" {
			continue
		}
		recordID := ack.ChatRecordID
		if isRevokeOutboxItem(item) || recordID <= 0 {
			recordID = s.nextRecordID()
		}
		event := MessageEvent{
			ID:                strconv.FormatInt(recordID, 10),
			EventID:           recordID,
			ChatRecordID:      recordID,
			Device:            item.Device,
			OwnerWxID:         firstNonEmpty(item.OwnerWxID, s.deviceWxID(item.Device)),
			From:              s.deviceWxID(item.Device),
			To:                item.WxID,
			Text:              outboundText(item),
			MessageType:       outboundMessageType(item),
			MediaKind:         item.MediaKind,
			MediaMime:         item.MediaMime,
			MediaName:         item.MediaName,
			MediaURL:          item.MediaURL,
			MediaSize:         item.MediaSize,
			AppMsgType:        outboundAppMsgType(item),
			AppMsgSubtype:     outboundAppMsgSubtype(item),
			AppMsgTitle:       outboundAppMsgTitle(item),
			AppMsgDescription: outboundAppMsgDescription(item),
			AppMsgURL:         outboundAppMsgURL(item),
			AppMsgFileName:    outboundAppMsgFileName(item),
			AppMsgAppName:     outboundAppMsgAppName(item),
			Direction:         DirectionSent,
			CreateTime:        time.Now().Unix(),
			RawProvider:       RawProviderModuleAck,
		}
		if isRevokeOutboxItem(item) {
			event.MessageKind = MessageKindSystem
			event.AppMsgSubtype = SystemSubtypeRevoke
			event.MessageType = 10000
			event.RawXML = outboundRevokeRawXML(item)
			event.Evidence = appendUnique(event.Evidence, "message.type=10000", "outbox.kind=revoke")
			event = event.Normalize()
		} else if isChatHistoryOutboxItem(item) {
			event.MessageKind = MessageKindChatHistory
			event.Evidence = appendUnique(event.Evidence, "message.type=49", "outbox.kind=chat_history")
			if event.Text == "" {
				event.Text = appMsgDisplayText(event)
			}
		} else if isLinkOutboxItem(item) || isMiniProgramOutboxItem(item) {
			event.MessageKind = MessageKindAppMsg
			event.Evidence = appendUnique(event.Evidence, "message.type=49", "outbox.kind="+item.Kind)
			if event.Text == "" {
				event.Text = appMsgDisplayText(event)
			}
		} else {
			event = event.Normalize()
		}
		alreadyStored, lookupErr := s.messageEventAlreadyStored(ctx, event)
		recorded := true
		if s.persistence != nil {
			if err := s.persistence.RecordOutboundEvent(ctx, event); err != nil {
				recorded = false
			}
		}
		if lookupErr != nil || !alreadyStored || !recorded {
			s.hub.Publish(event)
		}
	}
	return items, nil
}

func outboundText(item ModuleOutboxItem) string {
	if strings.TrimSpace(item.Text) != "" {
		return item.Text
	}
	if isVideoOutboxItem(item) {
		return defaultOutboxMediaText(OutboxKindVideo)
	}
	if isFileOutboxItem(item) {
		return defaultOutboxMediaText(OutboxKindFile)
	}
	if isVoiceOutboxItem(item) {
		return defaultOutboxMediaText(OutboxKindVoice)
	}
	if isImageOutboxItem(item) {
		return defaultOutboxMediaText(OutboxKindImage)
	}
	if isEmojiOutboxItem(item) {
		return "[表情]"
	}
	if isLocationOutboxItem(item) {
		payload := locationPayloadFromRaw(item.PayloadJSON)
		return firstNonEmpty(payload.LocationLabel, payload.LocationPoiName, "[位置]")
	}
	if isRevokeOutboxItem(item) {
		return "[撤回]"
	}
	return item.Text
}

func outboundMessageType(item ModuleOutboxItem) int32 {
	if isRevokeOutboxItem(item) {
		return 10000
	}
	if isVideoOutboxItem(item) {
		return 43
	}
	if isFileOutboxItem(item) {
		return MessageTypeFileTransfer
	}
	if isVoiceOutboxItem(item) {
		return 34
	}
	if isImageOutboxItem(item) {
		return 3
	}
	if isEmojiOutboxItem(item) {
		return 47
	}
	if isLocationOutboxItem(item) {
		return 48
	}
	if isQuoteOutboxItem(item) {
		return MessageTypeQuote
	}
	if isChatHistoryOutboxItem(item) || isLinkOutboxItem(item) || isMiniProgramOutboxItem(item) {
		return MessageTypeAppMsg
	}
	return 1
}

func outboundAppMsgType(item ModuleOutboxItem) int32 {
	if isChatHistoryOutboxItem(item) {
		return 19
	}
	if isQuoteOutboxItem(item) {
		return 57
	}
	if isLinkOutboxItem(item) {
		return 5
	}
	if isMiniProgramOutboxItem(item) {
		return 33
	}
	return 0
}

func outboundAppMsgSubtype(item ModuleOutboxItem) string {
	if isFileOutboxItem(item) {
		return "file"
	}
	if isQuoteOutboxItem(item) {
		return "quote"
	}
	if isChatHistoryOutboxItem(item) {
		return "chat_history"
	}
	if isLinkOutboxItem(item) {
		return "link"
	}
	if isMiniProgramOutboxItem(item) {
		return "mini_program"
	}
	if isRevokeOutboxItem(item) {
		return SystemSubtypeRevoke
	}
	return ""
}

func outboundAppMsgTitle(item ModuleOutboxItem) string {
	if isChatHistoryOutboxItem(item) {
		payload := chatHistoryPayloadFromRaw(item.PayloadJSON)
		return firstNonEmpty(payload.RecordTitle, item.Text, "聊天记录")
	}
	if isLinkOutboxItem(item) || isMiniProgramOutboxItem(item) {
		return firstNonEmpty(appMsgPayloadFromRaw(item.PayloadJSON).AppMsgTitle, item.Text)
	}
	return ""
}

func outboundAppMsgDescription(item ModuleOutboxItem) string {
	if isChatHistoryOutboxItem(item) {
		return chatHistoryPayloadFromRaw(item.PayloadJSON).RecordDescription
	}
	if isLinkOutboxItem(item) || isMiniProgramOutboxItem(item) {
		return appMsgPayloadFromRaw(item.PayloadJSON).AppMsgDescription
	}
	return ""
}

func outboundAppMsgURL(item ModuleOutboxItem) string {
	if isLinkOutboxItem(item) || isMiniProgramOutboxItem(item) {
		return appMsgPayloadFromRaw(item.PayloadJSON).AppMsgURL
	}
	return ""
}

func outboundAppMsgAppName(item ModuleOutboxItem) string {
	if isLinkOutboxItem(item) || isMiniProgramOutboxItem(item) {
		payload := appMsgPayloadFromRaw(item.PayloadJSON)
		return firstNonEmpty(payload.AppMsgAppName, payload.MiniProgramAppID, payload.MiniProgramUsername)
	}
	return ""
}

func outboundAppMsgFileName(item ModuleOutboxItem) string {
	if isFileOutboxItem(item) {
		return item.MediaName
	}
	if isChatHistoryOutboxItem(item) {
		return outboundAppMsgTitle(item)
	}
	return ""
}

func isMediaOutboxKind(kind string) bool {
	return kind == OutboxKindImage || kind == OutboxKindVideo || kind == OutboxKindVoice || kind == OutboxKindFile
}

func isImageOutboxItem(item ModuleOutboxItem) bool {
	return item.Kind == OutboxKindImage || item.MediaKind == OutboxKindImage
}

func isVideoOutboxItem(item ModuleOutboxItem) bool {
	return item.Kind == OutboxKindVideo || item.MediaKind == OutboxKindVideo
}

func isFileOutboxItem(item ModuleOutboxItem) bool {
	return item.Kind == OutboxKindFile || item.MediaKind == OutboxKindFile
}

func isVoiceOutboxItem(item ModuleOutboxItem) bool {
	return item.Kind == OutboxKindVoice || item.MediaKind == OutboxKindVoice
}

func isEmojiOutboxItem(item ModuleOutboxItem) bool {
	return item.Kind == OutboxKindEmoji || item.MediaKind == OutboxKindEmoji
}

func isLocationOutboxItem(item ModuleOutboxItem) bool {
	return item.Kind == OutboxKindLocation
}

func isQuoteOutboxItem(item ModuleOutboxItem) bool {
	return item.Kind == OutboxKindQuote
}

func isLinkOutboxItem(item ModuleOutboxItem) bool {
	return item.Kind == OutboxKindLink
}

func isMiniProgramOutboxItem(item ModuleOutboxItem) bool {
	return item.Kind == OutboxKindMiniProgram
}

func isChatHistoryOutboxItem(item ModuleOutboxItem) bool {
	return item.Kind == OutboxKindChatHistory
}

func isRevokeOutboxItem(item ModuleOutboxItem) bool {
	return item.Kind == OutboxKindRevoke
}

func outboundRevokeRawXML(item ModuleOutboxItem) string {
	payload := revokePayloadFromRaw(item.PayloadJSON)
	targetID := firstPositiveLong(payload.TargetChatRecordID, item.ChatRecordID)
	targetSvrID := payload.TargetMsgSvrID
	replacemsg := firstNonEmpty(item.Text, "撤回了一条消息")
	return "<sysmsg type=\"revokemsg\"><revokemsg>" +
		"<session>" + xmlEscapeText(item.WxID) + "</session>" +
		"<oldmsgid>" + strconv.FormatInt(targetID, 10) + "</oldmsgid>" +
		"<msgid>" + strconv.FormatInt(targetID, 10) + "</msgid>" +
		"<newmsgid>" + strconv.FormatInt(targetSvrID, 10) + "</newmsgid>" +
		"<replacemsg>" + xmlEscapeText(replacemsg) + "</replacemsg>" +
		"</revokemsg></sysmsg>"
}

func xmlEscapeText(value string) string {
	return strings.NewReplacer(
		"&", "&amp;",
		"<", "&lt;",
		">", "&gt;",
		`"`, "&quot;",
		"'", "&#39;",
	).Replace(value)
}

func defaultOutboxMediaText(kind string) string {
	if kind == OutboxKindVideo {
		return "[视频]"
	}
	if kind == OutboxKindFile {
		return "[文件]"
	}
	if kind == OutboxKindVoice {
		return "[语音]"
	}
	return "[图片]"
}

func (s *Service) RecordModuleContacts(ctx context.Context, req ModuleContactSnapshotRequest) (int, error) {
	req, err := req.Validate(s.cfg.DefaultDevice)
	if err != nil {
		return 0, err
	}
	auth, err := s.authorizeModuleAPIKey(req.APIKey)
	if err != nil {
		return 0, err
	}
	req.Device = auth.Device
	if req.WxID == "" {
		req.WxID = s.deviceWxID(req.Device)
	}
	if s.persistence == nil {
		return len(req.Contacts), nil
	}
	store, ok := s.persistence.(ModuleContactStore)
	if !ok {
		return len(req.Contacts), nil
	}
	if err := store.RecordModuleContacts(ctx, req); err != nil {
		return 0, err
	}
	return len(req.Contacts), nil
}

func (s *Service) recordModuleActivity(ctx context.Context, activity ModuleActivity) {
	if s.persistence == nil {
		return
	}
	recorder, ok := s.persistence.(ModuleActivityRecorder)
	if !ok {
		return
	}
	_ = recorder.RecordModuleActivity(ctx, activity)
}

func (s *Service) subscribeOutbox(device string) (<-chan struct{}, func()) {
	device = strings.TrimSpace(device)
	ch := make(chan struct{}, 1)
	s.outboxNotifyMu.Lock()
	if s.outboxNotify[device] == nil {
		s.outboxNotify[device] = map[chan struct{}]struct{}{}
	}
	s.outboxNotify[device][ch] = struct{}{}
	s.outboxNotifyMu.Unlock()
	unsubscribe := func() {
		s.outboxNotifyMu.Lock()
		if subscribers := s.outboxNotify[device]; subscribers != nil {
			delete(subscribers, ch)
			if len(subscribers) == 0 {
				delete(s.outboxNotify, device)
			}
		}
		s.outboxNotifyMu.Unlock()
	}
	return ch, unsubscribe
}

func (s *Service) notifyOutbox(device string) {
	device = strings.TrimSpace(device)
	s.outboxNotifyMu.Lock()
	defer s.outboxNotifyMu.Unlock()
	for ch := range s.outboxNotify[device] {
		select {
		case ch <- struct{}{}:
		default:
		}
	}
}

func ackActivity(req ModuleAckRequest) ModuleActivity {
	activity := ModuleActivity{
		Device: req.Device,
		Kind:   "ack",
	}
	for _, item := range req.Items {
		switch item.Status {
		case "failed":
			activity.AckFailedCount++
			if activity.LastError == "" {
				activity.LastError = item.Error
			}
		case "sent":
			activity.AckSentCount++
		}
	}
	return activity
}

func (s *Service) deviceWxID(deviceName string) string {
	if device, ok := s.Device(deviceName); ok {
		return device.WxID
	}
	return ""
}

type moduleAPIKeyAuth struct {
	Key    config.APIKey
	Device string
}

func (s *Service) authorizeModuleAPIKey(apiKey string) (moduleAPIKeyAuth, error) {
	apiKey = strings.TrimSpace(apiKey)
	if apiKey == "" {
		return moduleAPIKeyAuth{}, fmt.Errorf("api_key is required")
	}
	s.mu.RLock()
	defer s.mu.RUnlock()
	key, ok := s.cfg.APIKeys[apiKey]
	if !ok {
		return moduleAPIKeyAuth{}, fmt.Errorf("invalid api key")
	}
	if key.Disabled {
		return moduleAPIKeyAuth{}, fmt.Errorf("api key disabled")
	}
	device := apiKeyDeviceName(key)
	if strings.TrimSpace(device) == "" {
		return moduleAPIKeyAuth{}, fmt.Errorf("device is required")
	}
	if _, ok := s.cfg.Devices[device]; !ok {
		return moduleAPIKeyAuth{}, fmt.Errorf("unknown device %q", device)
	}
	return moduleAPIKeyAuth{Key: key, Device: device}, nil
}

func (s *Service) nextRecordID() int64 {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.nextChatRecordID++
	return s.nextChatRecordID
}

type IngestResult struct {
	Published        bool   `json:"published"`
	PersistenceError string `json:"persistence_error,omitempty"`
}

type ReplyAction struct {
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
	Error        string          `json:"error,omitempty"`
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value = strings.TrimSpace(value); value != "" {
			return value
		}
	}
	return ""
}

func apiKeyView(key config.APIKey) APIKeyView {
	return APIKeyView{
		Code:     key.Code,
		APIKey:   key.Code,
		Device:   key.Device,
		Nickname: key.Nickname,
		Enabled:  !key.Disabled,
	}
}

func generateAPIKey() string {
	random := make([]byte, 16)
	if _, err := rand.Read(random); err != nil {
		return fmt.Sprintf("wg_%d", time.Now().UnixNano())
	}
	return "wg_" + hex.EncodeToString(random)
}

func safeCodePart(value string) string {
	value = strings.ToLower(strings.TrimSpace(value))
	var builder strings.Builder
	for _, r := range value {
		switch {
		case r >= 'a' && r <= 'z':
			builder.WriteRune(r)
		case r >= '0' && r <= '9':
			builder.WriteRune(r)
		case r == '-' || r == '_':
			builder.WriteRune(r)
		}
	}
	if builder.Len() == 0 {
		return "code"
	}
	return builder.String()
}

type ModuleRegistrationResult struct {
	Device ModuleDeviceView `json:"device"`
}

type ModuleDeviceView struct {
	Name     string `json:"name"`
	WxID     string `json:"wxid,omitempty"`
	Nickname string `json:"nickname,omitempty"`
}

func apiKeyDeviceName(key config.APIKey) string {
	if value := strings.TrimSpace(key.Device); value != "" {
		return value
	}
	return "device-" + safeCodePart(key.Code)
}

func moduleDeviceView(device config.Device) ModuleDeviceView {
	return ModuleDeviceView{
		Name:     device.Name,
		WxID:     device.WxID,
		Nickname: device.Nickname,
	}
}
