package bridge

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
)

type HTTPServer struct {
	service   *Service
	adminPass string
}

func NewHTTPServer(service *Service, adminPassword string) *HTTPServer {
	return &HTTPServer{
		service:   service,
		adminPass: adminPassword,
	}
}

func (s *HTTPServer) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", s.health)
	mux.HandleFunc("GET /docs", s.openAPIDocs)
	mux.HandleFunc("GET /docs/", s.openAPIDocs)
	mux.HandleFunc("GET /docs/openapi.json", s.openAPIJSON)
	mux.HandleFunc("GET /openapi.json", s.openAPIJSON)
	for name := range publicDocFiles {
		mux.HandleFunc("GET /docs/"+name, s.openAPIDocFile)
	}
	mux.HandleFunc("GET /api/devices", s.requireAdmin(s.devices))
	mux.HandleFunc("POST /api/devices", s.requireAdmin(s.upsertDevice))
	mux.HandleFunc("GET /api/api-keys", s.requireAdmin(s.apiKeys))
	mux.HandleFunc("POST /api/api-keys", s.requireAdmin(s.upsertAPIKey))
	mux.HandleFunc("POST /api/api-keys/", s.requireAdmin(s.updateAPIKeyState))
	mux.HandleFunc("DELETE /api/api-keys/", s.requireAdmin(s.deleteAPIKey))
	mux.HandleFunc("GET /api/events", s.requireAdmin(s.events))
	mux.HandleFunc("GET /api/stored-events", s.requireAdmin(s.storedEvents))
	mux.HandleFunc("GET /api/messages", s.requireAdmin(s.messages))
	mux.HandleFunc("GET /api/live/events", s.requireAdmin(s.liveEvents))
	mux.HandleFunc("GET /api/modules/status", s.requireAdmin(s.moduleStatuses))
	mux.HandleFunc("GET /api/module-contacts", s.requireAdmin(s.moduleContacts))
	mux.HandleFunc("GET /api/media/", s.requirePublicAPI(s.mediaFile))
	mux.HandleFunc("POST /api/send/text", s.requireAdmin(s.sendText))
	mux.HandleFunc("POST /api/send/action", s.requireAdmin(s.sendAction))
	mux.HandleFunc("POST /api/v1/messages/action", s.requirePublicAPI(s.sendV1Message("")))
	mux.HandleFunc("POST /api/v1/messages/text", s.requirePublicAPI(s.sendV1Message(OutboxKindText)))
	mux.HandleFunc("POST /api/v1/messages/image", s.requirePublicAPI(s.sendV1Message(OutboxKindImage)))
	mux.HandleFunc("POST /api/v1/messages/video", s.requirePublicAPI(s.sendV1Message(OutboxKindVideo)))
	mux.HandleFunc("POST /api/v1/messages/voice", s.requirePublicAPI(s.sendV1Message(OutboxKindVoice)))
	mux.HandleFunc("POST /api/v1/messages/file", s.requirePublicAPI(s.sendV1Message(OutboxKindFile)))
	mux.HandleFunc("POST /api/v1/messages/emoji", s.requirePublicAPI(s.sendV1Message(OutboxKindEmoji)))
	mux.HandleFunc("POST /api/v1/messages/location", s.requirePublicAPI(s.sendV1Message(OutboxKindLocation)))
	mux.HandleFunc("POST /api/v1/messages/quote", s.requirePublicAPI(s.sendV1Message(OutboxKindQuote)))
	mux.HandleFunc("POST /api/v1/messages/link", s.requirePublicAPI(s.sendV1Message(OutboxKindLink)))
	mux.HandleFunc("POST /api/v1/messages/mini-program", s.requirePublicAPI(s.sendV1Message(OutboxKindMiniProgram)))
	mux.HandleFunc("POST /api/v1/messages/chat-history", s.requirePublicAPI(s.sendV1Message(OutboxKindChatHistory)))
	mux.HandleFunc("POST /api/v1/messages/revoke", s.requirePublicAPI(s.sendV1Message(OutboxKindRevoke)))
	mux.HandleFunc("GET /api/v1/capabilities", s.requirePublicAPI(s.publicCapabilities))
	mux.HandleFunc("GET /api/v1/messages", s.requirePublicAPI(s.getV1Messages))
	mux.HandleFunc("GET /api/v1/ws", s.requirePublicAPI(s.publicWebSocket))
	mux.HandleFunc("GET /api/v1/outbox/", s.requirePublicAPI(s.getV1OutboxItem))
	mux.HandleFunc("GET /api/v1/contacts", s.requirePublicAPI(s.moduleContacts))
	mux.HandleFunc("GET /api/v1/modules/status", s.requirePublicAPI(s.moduleStatuses))
	mux.HandleFunc("GET /admin", s.adminPage)
	mux.HandleFunc("GET /admin/", s.adminPage)
	mux.HandleFunc("POST /module/register", s.registerModule)
	mux.HandleFunc("POST /module/contacts/snapshot", s.recordContacts)
	mux.HandleFunc("GET /module/media/", s.moduleMediaFile)
	mux.HandleFunc("POST /module/outbox/poll", s.pollOutbox)
	mux.HandleFunc("POST /module/outbox/ack", s.ackOutbox)
	mux.HandleFunc("GET /module/outbox/ws", s.outboxWebSocket)
	mux.HandleFunc("POST /webhook/lsposed/message", s.ingestMessageFrom("lsposed"))
	mux.HandleFunc("POST /webhook/module/message", s.ingestMessageFrom("module"))
	return mux
}

func (s *HTTPServer) health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"ok": true,
	})
}

func (s *HTTPServer) devices(w http.ResponseWriter, r *http.Request) {
	type deviceResponse struct {
		Name     string `json:"name"`
		WxID     string `json:"wxid"`
		Nickname string `json:"nickname"`
	}
	out := []deviceResponse{}
	if reader := s.service.AdminReader(); reader != nil {
		statuses, err := reader.ListModuleStatuses(r.Context())
		if err != nil {
			writeError(w, http.StatusInternalServerError, "admin_read_failed", err.Error())
			return
		}
		for _, status := range statuses {
			out = append(out, deviceResponse{
				Name:     status.Device,
				WxID:     status.DeviceWxID,
				Nickname: status.DeviceNickname,
			})
		}
		writeJSON(w, http.StatusOK, map[string]any{
			"default_device": s.service.DefaultDevice(),
			"devices":        out,
		})
		return
	}
	for _, device := range s.service.Devices() {
		out = append(out, deviceResponse{
			Name:     device.Name,
			WxID:     device.WxID,
			Nickname: device.Nickname,
		})
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"default_device": s.service.DefaultDevice(),
		"devices":        out,
	})
}

func (s *HTTPServer) apiKeys(w http.ResponseWriter, r *http.Request) {
	limit := queryLimit(r, 200)
	if reader := s.service.AdminReader(); reader != nil {
		keys, err := reader.ListAPIKeys(r.Context(), limit)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "admin_read_failed", err.Error())
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"api_keys": keys})
		return
	}
	keys := s.apiKeyViews(limit)
	writeJSON(w, http.StatusOK, map[string]any{"api_keys": keys})
}

func (s *HTTPServer) apiKeyViews(limit int) []APIKeyView {
	keys := s.service.APIKeys()
	if len(keys) > limit {
		keys = keys[:limit]
	}
	out := make([]APIKeyView, 0, len(keys))
	for _, key := range keys {
		out = append(out, apiKeyView(key))
	}
	return out
}

func (s *HTTPServer) upsertAPIKey(w http.ResponseWriter, r *http.Request) {
	var req APIKeyUpsertRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_json", err.Error())
		return
	}
	key, err := s.service.UpsertAPIKey(r.Context(), req)
	if err != nil {
		writeError(w, http.StatusBadRequest, "api_key_failed", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "api_key": key})
}

func (s *HTTPServer) deleteAPIKey(w http.ResponseWriter, r *http.Request) {
	apiKey := strings.TrimPrefix(r.URL.Path, "/api/api-keys/")
	if apiKey == "" || apiKey == r.URL.Path {
		writeError(w, http.StatusBadRequest, "api_key_failed", "api key is required")
		return
	}
	if err := s.service.DeleteAPIKey(r.Context(), apiKey); err != nil {
		writeError(w, http.StatusBadRequest, "api_key_failed", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *HTTPServer) updateAPIKeyState(w http.ResponseWriter, r *http.Request) {
	apiKey, action, ok := parseAPIKeyActionPath(r.URL.Path)
	if !ok {
		writeError(w, http.StatusBadRequest, "api_key_failed", "api key action is required")
		return
	}
	var enabled bool
	switch action {
	case "enable":
		enabled = true
	case "disable":
		enabled = false
	default:
		writeError(w, http.StatusBadRequest, "api_key_failed", "unknown api key action")
		return
	}
	key, err := s.service.SetAPIKeyEnabled(r.Context(), apiKey, enabled)
	if err != nil {
		writeError(w, http.StatusBadRequest, "api_key_failed", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "api_key": key})
}

func parseAPIKeyActionPath(path string) (string, string, bool) {
	suffix := strings.Trim(strings.TrimPrefix(path, "/api/api-keys/"), "/")
	parts := strings.Split(suffix, "/")
	if len(parts) != 2 || parts[0] == "" || parts[1] == "" || suffix == path {
		return "", "", false
	}
	return parts[0], parts[1], true
}

func (s *HTTPServer) upsertDevice(w http.ResponseWriter, r *http.Request) {
	var req DeviceUpsertRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_json", err.Error())
		return
	}
	device, err := s.service.UpsertDevice(r.Context(), req)
	if err != nil {
		writeError(w, http.StatusBadRequest, "device_failed", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "device": device})
}

func (s *HTTPServer) events(w http.ResponseWriter, r *http.Request) {
	limit := queryLimit(r, 50)
	writeJSON(w, http.StatusOK, map[string]any{
		"events": s.service.Hub().Recent(limit),
	})
}

func (s *HTTPServer) storedEvents(w http.ResponseWriter, r *http.Request) {
	reader := s.service.AdminReader()
	if reader == nil {
		writeJSON(w, http.StatusOK, map[string]any{"events": []StoredEventView{}})
		return
	}
	events, err := reader.ListStoredEvents(r.Context(), queryLimit(r, 50))
	if err != nil {
		writeError(w, http.StatusInternalServerError, "admin_read_failed", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"events": events})
}

func (s *HTTPServer) messages(w http.ResponseWriter, r *http.Request) {
	reader := s.service.AdminReader()
	if reader == nil {
		writeJSON(w, http.StatusOK, map[string]any{"messages": []StoredEventView{}})
		return
	}
	filter := MessageFilter{
		Device:    strings.TrimSpace(r.URL.Query().Get("device")),
		WxID:      strings.TrimSpace(r.URL.Query().Get("wxid")),
		OwnerWxID: strings.TrimSpace(r.URL.Query().Get("owner_wxid")),
		ChatID:    strings.TrimSpace(r.URL.Query().Get("chat_id")),
		ChatKind:  strings.TrimSpace(r.URL.Query().Get("chat_kind")),
		Limit:     queryLimit(r, 100),
	}
	if filter.OwnerWxID == "" && filter.Device != "" {
		filter.OwnerWxID = s.service.deviceWxID(filter.Device)
	}
	var ok bool
	filter.Device, filter.OwnerWxID, ok = s.bindPublicDeviceFilter(w, r, filter.Device, filter.OwnerWxID)
	if !ok {
		return
	}
	messages, err := reader.ListMessages(r.Context(), filter)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "admin_read_failed", err.Error())
		return
	}
	if messages == nil {
		messages = []StoredEventView{}
	}
	writeJSON(w, http.StatusOK, map[string]any{"messages": messages})
}

func (s *HTTPServer) liveEvents(w http.ResponseWriter, r *http.Request) {
	flusher, ok := w.(http.Flusher)
	if !ok {
		writeError(w, http.StatusInternalServerError, "stream_unsupported", "streaming is not supported")
		return
	}
	w.Header().Set("Content-Type", "text/event-stream; charset=utf-8")
	w.Header().Set("Cache-Control", "no-cache, no-transform")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("X-Accel-Buffering", "no")

	writeSSE(w, "ready", map[string]any{"ok": true, "time": time.Now().Unix()})
	flusher.Flush()

	events := s.service.Hub().Subscribe(r.Context())
	ticker := time.NewTicker(20 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-r.Context().Done():
			return
		case event, ok := <-events:
			if !ok {
				return
			}
			writeSSE(w, "message", event)
			flusher.Flush()
		case <-ticker.C:
			writeSSE(w, "ping", map[string]any{"time": time.Now().Unix()})
			flusher.Flush()
		}
	}
}

func (s *HTTPServer) moduleStatuses(w http.ResponseWriter, r *http.Request) {
	reader := s.service.AdminReader()
	if reader != nil {
		statuses, err := reader.ListModuleStatuses(r.Context())
		if err != nil {
			writeError(w, http.StatusInternalServerError, "admin_read_failed", err.Error())
			return
		}
		statuses = filterPublicModuleStatuses(r, statuses)
		writeJSON(w, http.StatusOK, map[string]any{"modules": statuses})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"modules": filterPublicModuleStatuses(r, s.moduleStatusViews())})
}

func (s *HTTPServer) moduleContacts(w http.ResponseWriter, r *http.Request) {
	reader := s.service.AdminReader()
	if reader == nil {
		writeJSON(w, http.StatusOK, map[string]any{"contacts": []ModuleContactView{}})
		return
	}
	filter := ModuleContactFilter{
		Device:         strings.TrimSpace(r.URL.Query().Get("device")),
		OwnerWxID:      strings.TrimSpace(r.URL.Query().Get("owner_wxid")),
		Query:          strings.TrimSpace(r.URL.Query().Get("q")),
		IncludeDeleted: parseBoolQuery(r.URL.Query().Get("include_deleted")),
		Limit:          queryLimit(r, 500),
	}
	var ok bool
	filter.Device, filter.OwnerWxID, ok = s.bindPublicDeviceFilter(w, r, filter.Device, filter.OwnerWxID)
	if !ok {
		return
	}
	contacts, err := reader.ListModuleContacts(r.Context(), filter)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "admin_read_failed", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"contacts": contacts})
}

func (s *HTTPServer) moduleStatusViews() []ModuleStatusView {
	devices := s.service.Devices()
	sort.Slice(devices, func(i, j int) bool {
		return devices[i].Name < devices[j].Name
	})
	out := make([]ModuleStatusView, 0, len(devices))
	for _, device := range devices {
		item := ModuleStatusView{
			Device:         device.Name,
			DeviceWxID:     device.WxID,
			DeviceNickname: device.Nickname,
			Enabled:        true,
		}
		if strings.TrimSpace(device.WxID) != "" {
			item.Registered = true
		}
		item.NormalizeRuntimeStatus()
		out = append(out, item)
	}
	return out
}

func (s *HTTPServer) sendText(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 16<<20)
	var req SendTextRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_json", err.Error())
		return
	}
	if strings.TrimSpace(req.OwnerWxID) == "" {
		writeError(w, http.StatusBadRequest, "owner_wxid_required", "owner_wxid is required for admin sends")
		return
	}
	outboxID, err := s.service.SendText(r.Context(), req)
	if err != nil {
		writeError(w, http.StatusBadRequest, "send_failed", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, adminSendQueuedResponse(outboxID))
}

func (s *HTTPServer) sendAction(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 16<<20)
	var req SendActionRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_json", err.Error())
		return
	}
	if strings.TrimSpace(req.OwnerWxID) == "" {
		writeError(w, http.StatusBadRequest, "owner_wxid_required", "owner_wxid is required for admin sends")
		return
	}
	outboxID, err := s.service.SendAction(r.Context(), req)
	if err != nil {
		writeError(w, http.StatusBadRequest, "send_failed", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, adminSendQueuedResponse(outboxID))
}

func adminSendQueuedResponse(outboxID int64) map[string]any {
	return map[string]any{
		"ok":         true,
		"outbox_id":  outboxID,
		"queued":     true,
		"status_url": publicOutboxStatusURL(outboxID),
	}
}

func (s *HTTPServer) registerModule(w http.ResponseWriter, r *http.Request) {
	var req ModuleRegistrationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_json", err.Error())
		return
	}
	result, err := s.service.RegisterModule(r.Context(), req)
	if err != nil {
		writeError(w, http.StatusBadRequest, "register_failed", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "result": result})
}

func (s *HTTPServer) recordContacts(w http.ResponseWriter, r *http.Request) {
	var req ModuleContactSnapshotRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_json", err.Error())
		return
	}
	count, err := s.service.RecordModuleContacts(r.Context(), req)
	if err != nil {
		writeError(w, http.StatusBadRequest, "contacts_failed", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "count": count})
}

func (s *HTTPServer) pollOutbox(w http.ResponseWriter, r *http.Request) {
	var req ModulePollRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_json", err.Error())
		return
	}
	items, err := s.service.PollOutbox(r.Context(), req)
	if err != nil {
		writeError(w, http.StatusBadRequest, "poll_failed", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "items": items})
}

func (s *HTTPServer) ackOutbox(w http.ResponseWriter, r *http.Request) {
	var req ModuleAckRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_json", err.Error())
		return
	}
	items, err := s.service.AckOutbox(r.Context(), req)
	if err != nil {
		writeError(w, http.StatusBadRequest, "ack_failed", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "items": items})
}

func (s *HTTPServer) ingestMessageFrom(provider string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		r.Body = http.MaxBytesReader(w, r.Body, 16<<20)
		var event MessageEvent
		if err := json.NewDecoder(r.Body).Decode(&event); err != nil {
			writeError(w, http.StatusBadRequest, "invalid_json", err.Error())
			return
		}
		if event.Device == "" {
			event.Device = s.service.DefaultDevice()
		}
		event.RawProvider = provider
		result, err := s.service.Ingest(r.Context(), event)
		if err != nil {
			writeError(w, http.StatusBadRequest, "ingest_failed", err.Error())
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"ok": true, "result": result})
	}
}

func (s *HTTPServer) mediaFile(w http.ResponseWriter, r *http.Request) {
	rel := strings.TrimPrefix(r.URL.Path, "/api/media/")
	rel = strings.TrimPrefix(rel, "/")
	if rel == "" {
		writeError(w, http.StatusBadRequest, "invalid_media_path", "media path is required")
		return
	}
	if !publicMediaPathAllowed(r.Context(), rel) {
		writeError(w, http.StatusForbidden, "media_forbidden", "media path does not belong to this device")
		return
	}
	fullPath, err := s.service.MediaFilePath(rel)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid_media_path", err.Error())
		return
	}
	if _, err := os.Stat(fullPath); err != nil {
		writeError(w, http.StatusNotFound, "media_not_found", "media file not found")
		return
	}
	http.ServeFile(w, r, filepath.Clean(fullPath))
}

func publicMediaPathAllowed(ctx context.Context, rel string) bool {
	auth, ok := publicAPIAuthFromContext(ctx)
	if !ok || auth.Device == "" {
		return true
	}
	deviceRoot := safePathPart(auth.Device)
	return rel == deviceRoot || strings.HasPrefix(rel, deviceRoot+"/")
}

func (s *HTTPServer) moduleMediaFile(w http.ResponseWriter, r *http.Request) {
	apiKey := strings.TrimSpace(r.URL.Query().Get("api_key"))
	if apiKey == "" {
		apiKey = strings.TrimSpace(r.Header.Get("X-Bridge-API-Key"))
	}
	auth, err := s.service.authorizeModuleAPIKey(apiKey)
	if err != nil {
		writeError(w, http.StatusUnauthorized, "unauthorized", err.Error())
		return
	}
	rel := strings.TrimPrefix(r.URL.Path, "/module/media/")
	rel = strings.TrimPrefix(rel, "/")
	if rel == "" {
		writeError(w, http.StatusBadRequest, "invalid_media_path", "media path is required")
		return
	}
	deviceRoot := safePathPart(auth.Device)
	if rel != deviceRoot && !strings.HasPrefix(rel, deviceRoot+"/") {
		writeError(w, http.StatusForbidden, "media_forbidden", "media path does not belong to this device")
		return
	}
	fullPath, err := s.service.MediaFilePath(rel)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid_media_path", err.Error())
		return
	}
	if _, err := os.Stat(fullPath); err != nil {
		writeError(w, http.StatusNotFound, "media_not_found", "media file not found")
		return
	}
	http.ServeFile(w, r, filepath.Clean(fullPath))
}

func (s *HTTPServer) requireAdmin(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !s.adminPasswordOK(r) {
			writeError(w, http.StatusUnauthorized, "unauthorized", "invalid admin password")
			return
		}
		next(w, r)
	}
}

type publicAPIAuth struct {
	Admin  bool
	APIKey string
	Device string
}

type publicAPIAuthContextKey struct{}

func (s *HTTPServer) requirePublicAPI(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if s.adminPasswordOK(r) {
			ctx := context.WithValue(r.Context(), publicAPIAuthContextKey{}, publicAPIAuth{Admin: true})
			next(w, r.WithContext(ctx))
			return
		}

		apiKey := publicAPIKeyFromRequest(r)
		if apiKey == "" {
			writeError(w, http.StatusUnauthorized, "unauthorized", "admin password or API key is required")
			return
		}
		auth, err := s.service.authorizeModuleAPIKey(apiKey)
		if err != nil {
			writeError(w, http.StatusUnauthorized, "unauthorized", "invalid API key")
			return
		}
		ctx := context.WithValue(r.Context(), publicAPIAuthContextKey{}, publicAPIAuth{
			APIKey: firstNonEmpty(auth.Key.Code, apiKey),
			Device: auth.Device,
		})
		next(w, r.WithContext(ctx))
	}
}

func (s *HTTPServer) adminPasswordOK(r *http.Request) bool {
	got := strings.TrimSpace(r.Header.Get("X-Bridge-Password"))
	if got == "" {
		got = strings.TrimSpace(r.URL.Query().Get("password"))
	}
	return got == s.adminPass
}

func publicAPIAuthFromContext(ctx context.Context) (publicAPIAuth, bool) {
	auth, ok := ctx.Value(publicAPIAuthContextKey{}).(publicAPIAuth)
	return auth, ok
}

func publicAPIKeyFromRequest(r *http.Request) string {
	if apiKey := strings.TrimSpace(r.Header.Get("X-Bridge-API-Key")); apiKey != "" {
		return apiKey
	}
	if apiKey := strings.TrimSpace(r.URL.Query().Get("api_key")); apiKey != "" {
		return apiKey
	}
	auth := strings.TrimSpace(r.Header.Get("Authorization"))
	if strings.HasPrefix(strings.ToLower(auth), "bearer ") {
		return strings.TrimSpace(auth[len("bearer "):])
	}
	return ""
}

func (s *HTTPServer) bindPublicDeviceFilter(w http.ResponseWriter, r *http.Request, device string, ownerWxID string) (string, string, bool) {
	auth, ok := publicAPIAuthFromContext(r.Context())
	if !ok || auth.Device == "" {
		return device, ownerWxID, true
	}
	if device != "" && device != auth.Device {
		writeError(w, http.StatusForbidden, "device_forbidden", "API key cannot access this device")
		return "", "", false
	}
	currentOwnerWxID := s.service.deviceWxID(auth.Device)
	if ownerWxID != "" && currentOwnerWxID != "" && ownerWxID != currentOwnerWxID {
		writeError(w, http.StatusForbidden, "owner_wxid_forbidden", "API key cannot access this owner_wxid")
		return "", "", false
	}
	return auth.Device, firstNonEmpty(ownerWxID, currentOwnerWxID), true
}

func publicAuthCanAccessDevice(ctx context.Context, device string) bool {
	auth, ok := publicAPIAuthFromContext(ctx)
	return !ok || auth.Device == "" || auth.Device == device
}

func filterPublicModuleStatuses(r *http.Request, statuses []ModuleStatusView) []ModuleStatusView {
	auth, ok := publicAPIAuthFromContext(r.Context())
	if !ok || auth.Device == "" {
		return statuses
	}
	out := make([]ModuleStatusView, 0, len(statuses))
	for _, status := range statuses {
		if status.Device == auth.Device {
			out = append(out, status)
		}
	}
	return out
}

func queryLimit(r *http.Request, fallback int) int {
	limit := fallback
	if raw := r.URL.Query().Get("limit"); raw != "" {
		if parsed, err := strconv.Atoi(raw); err == nil && parsed > 0 && parsed <= 500 {
			limit = parsed
		}
	}
	return limit
}

func parseBoolQuery(raw string) bool {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "1", "true", "yes", "on":
		return true
	default:
		return false
	}
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}

func writeError(w http.ResponseWriter, status int, code string, message string) {
	writeJSON(w, status, map[string]any{
		"ok":      false,
		"code":    code,
		"message": message,
	})
}

func writeSSE(w http.ResponseWriter, event string, payload any) {
	data, err := json.Marshal(payload)
	if err != nil {
		data = []byte(`{"error":"marshal_failed"}`)
	}
	if event != "" {
		_, _ = fmt.Fprintf(w, "event: %s\n", event)
	}
	_, _ = fmt.Fprintf(w, "data: %s\n\n", data)
}
