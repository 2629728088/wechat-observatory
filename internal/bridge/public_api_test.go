package bridge

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestAdminSendTextRequiresCurrentOwnerWxID(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{"device":"phone-a","owner_wxid":"wxid_self","wx_ids":["wxid_friend"],"text":"manual reply"}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/text", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	var sendResponse struct {
		OK           bool   `json:"ok"`
		OutboxID     int64  `json:"outbox_id"`
		Queued       bool   `json:"queued"`
		StatusURL    string `json:"status_url"`
		ChatRecordID *int64 `json:"chat_record_id"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &sendResponse); err != nil {
		t.Fatalf("send response should be json: %v", err)
	}
	if !sendResponse.OK || sendResponse.OutboxID <= 0 || !sendResponse.Queued || sendResponse.StatusURL != publicOutboxStatusURL(sendResponse.OutboxID) || sendResponse.ChatRecordID != nil {
		t.Fatalf("legacy text endpoint should return queued outbox metadata, got %+v", sendResponse)
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 || items[0].OwnerWxID != "wxid_self" || items[0].WxID != "wxid_friend" || items[0].Text != "manual reply" {
		t.Fatalf("unexpected outbox items: %+v", items)
	}
	if items[0].Kind != OutboxKindText {
		t.Fatalf("text sends must be queued as kind=text, got %+v", items[0])
	}

	staleBody := []byte(`{"device":"phone-a","owner_wxid":"wxid_stale","wx_ids":["wxid_friend"],"text":"stale reply"}`)
	staleReq := httptest.NewRequest(http.MethodPost, "/api/send/text", bytes.NewReader(staleBody))
	staleReq.Header.Set("X-Bridge-Password", "admin")
	staleRec := httptest.NewRecorder()
	server.ServeHTTP(staleRec, staleReq)
	if staleRec.Code != http.StatusBadRequest {
		t.Fatalf("stale owner should be rejected, got status %d body=%s", staleRec.Code, staleRec.Body.String())
	}

	missingOwnerBody := []byte(`{"device":"phone-a","wx_ids":["wxid_friend"],"text":"missing owner"}`)
	missingOwnerReq := httptest.NewRequest(http.MethodPost, "/api/send/text", bytes.NewReader(missingOwnerBody))
	missingOwnerReq.Header.Set("X-Bridge-Password", "admin")
	missingOwnerRec := httptest.NewRecorder()
	server.ServeHTTP(missingOwnerRec, missingOwnerReq)
	if missingOwnerRec.Code != http.StatusBadRequest {
		t.Fatalf("missing owner should be rejected, got status %d body=%s", missingOwnerRec.Code, missingOwnerRec.Body.String())
	}
}

func TestAdminSendActionReturnsQueuedOutboxID(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{"device":"phone-a","owner_wxid":"wxid_self","wx_ids":["wxid_friend"],"kind":"text","text":"manual action"}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	var sendResponse struct {
		OK           bool   `json:"ok"`
		OutboxID     int64  `json:"outbox_id"`
		Queued       bool   `json:"queued"`
		StatusURL    string `json:"status_url"`
		ChatRecordID *int64 `json:"chat_record_id"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &sendResponse); err != nil {
		t.Fatalf("send action response should be json: %v", err)
	}
	if !sendResponse.OK || sendResponse.OutboxID <= 0 || !sendResponse.Queued || sendResponse.StatusURL != publicOutboxStatusURL(sendResponse.OutboxID) || sendResponse.ChatRecordID != nil {
		t.Fatalf("legacy action endpoint should return queued outbox metadata, got %+v", sendResponse)
	}
}

func TestPublicV1CapabilitiesDescribeProtocolWithoutSecrets(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	req := httptest.NewRequest(http.MethodGet, "/api/v1/capabilities", nil)
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected capabilities status=%d body=%s", rec.Code, rec.Body.String())
	}
	if strings.Contains(rec.Body.String(), "example-real-password") || strings.Contains(rec.Body.String(), testAPIKey) {
		t.Fatalf("capabilities must not expose secrets: %s", rec.Body.String())
	}

	var got PublicCapabilitiesResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &got); err != nil {
		t.Fatalf("capabilities response must be valid json: %v", err)
	}
	if !got.OK || got.ProtocolVersion != "v1" || got.Envelope.Name != "MessageEnvelope v1" {
		t.Fatalf("unexpected protocol metadata: %+v", got)
	}
	fields := map[string]bool{}
	for _, field := range got.Envelope.Fields {
		fields[field.Name] = true
	}
	for _, field := range []string{"kind", "subtype", "message_type", "chat_id", "media[]", "media[].opaque", "appmsg", "appmsg.subtype", "location", "location.latitude", "unsupported", "evidence"} {
		if !fields[field] {
			t.Fatalf("envelope field %s missing: %+v", field, got.Envelope.Fields)
		}
	}
	for _, field := range []string{"media_url", "media_kind", "appmsg_subtype", "appmsg_title", "location_latitude", "location_longitude"} {
		if fields[field] {
			t.Fatalf("capabilities envelope contract must use nested v1 fields, found legacy field %s in %+v", field, got.Envelope.Fields)
		}
	}

	var payment *PublicMessageCapability
	var text *PublicMessageCapability
	var voice *PublicMessageCapability
	var emoji *PublicMessageCapability
	var chatHistory *PublicMessageCapability
	for i := range got.Capabilities {
		capability := &got.Capabilities[i]
		if capability.Kind == MessageKindPayment {
			payment = capability
		}
		if capability.Kind == MessageKindText {
			text = capability
		}
		if capability.Kind == MessageKindVoice {
			voice = capability
		}
		if capability.Kind == MessageKindEmoji {
			emoji = capability
		}
		if capability.Kind == MessageKindChatHistory {
			chatHistory = capability
		}
	}
	if text == nil || text.SendEndpoint != "/api/v1/messages/text" || text.OutboundStatus != "stable" {
		t.Fatalf("text capability missing or unstable: %+v", text)
	}
	if voice == nil || voice.InboundStatus != "stable" || voice.OutboundStatus != "stable" || voice.Verification != "user_confirmed" {
		t.Fatalf("voice capability should match current verified support: %+v", voice)
	}
	if emoji == nil || !containsString(emoji.Notes, "入站稳定字段是 appmsg.title/MD5 与 appmsg.url/CDN；本地 media[] 可能带 opaque=true。") {
		t.Fatalf("emoji capability should describe appmsg/opaque semantics: %+v", emoji)
	}
	if chatHistory == nil || chatHistory.OutboundStatus != "source_forward_only" || !containsString(chatHistory.Unsupported, "arbitrary_raw_xml_send") {
		t.Fatalf("chat history capability should remain forwarding-only: %+v", chatHistory)
	}
	if payment == nil || payment.InboundStatus != "parse_only" || payment.OutboundStatus != "unsupported" {
		t.Fatalf("payment capability should be parse-only inbound: %+v", payment)
	}
	if !containsString(payment.Unsupported, "payment_sensitive_fields_redacted") {
		t.Fatalf("payment redaction marker missing: %+v", payment.Unsupported)
	}
}

func TestPublicV1TypedMessageEndpointsUseActionOutbox(t *testing.T) {
	mediaDir := t.TempDir()
	service := newTestService("", WithMediaDir(mediaDir))
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"text":"v1 hello"
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/messages/text", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected v1 text status %d body=%s", rec.Code, rec.Body.String())
	}
	var textResponse PublicSendResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &textResponse); err != nil {
		t.Fatalf("text response should be json: %v", err)
	}
	if textResponse.OutboxID <= 0 || textResponse.ChatRecordID != 0 || textResponse.Outbox.ChatRecordID != 0 {
		t.Fatalf("queued response must expose only outbox id until ACK: %+v", textResponse)
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 || items[0].Kind != OutboxKindText || items[0].Text != "v1 hello" {
		t.Fatalf("unexpected v1 text outbox item: %+v", items)
	}

	imageBody := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"media_mime":"image/png",
		"media_name":"v1.png",
		"media_base64":"iVBORw0KGgo="
	}`)
	imageReq := httptest.NewRequest(http.MethodPost, "/api/v1/messages/image", bytes.NewReader(imageBody))
	imageReq.Header.Set("X-Bridge-Password", "admin")
	imageRec := httptest.NewRecorder()
	server.ServeHTTP(imageRec, imageReq)
	if imageRec.Code != http.StatusOK {
		t.Fatalf("unexpected v1 image status %d body=%s", imageRec.Code, imageRec.Body.String())
	}
	items = pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 || items[0].Kind != OutboxKindImage || items[0].MediaURL == "" || bytes.Contains(items[0].PayloadJSON, []byte("media_base64")) {
		t.Fatalf("unexpected v1 image outbox item: %+v", items)
	}

	mismatchBody := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"image",
		"text":"wrong"
	}`)
	mismatchReq := httptest.NewRequest(http.MethodPost, "/api/v1/messages/text", bytes.NewReader(mismatchBody))
	mismatchReq.Header.Set("X-Bridge-Password", "admin")
	mismatchRec := httptest.NewRecorder()
	server.ServeHTTP(mismatchRec, mismatchReq)
	if mismatchRec.Code != http.StatusBadRequest || !strings.Contains(mismatchRec.Body.String(), "kind_mismatch") {
		t.Fatalf("kind mismatch should be rejected, got status=%d body=%s", mismatchRec.Code, mismatchRec.Body.String())
	}
}

func TestPublicV1GenericActionEndpointReturnsAllOutboxesAndCompatibilityFields(t *testing.T) {
	service := newTestService("", WithMediaDir(t.TempDir()))
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend","wxid_second"],
		"kind":"text",
		"text":"fanout through v1 action"
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/messages/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected v1 action status=%d body=%s", rec.Code, rec.Body.String())
	}
	if strings.Contains(rec.Body.String(), "payload_json") || strings.Contains(rec.Body.String(), "media_base64") {
		t.Fatalf("public action response should not expose internal payloads: %s", rec.Body.String())
	}
	var sent PublicSendResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &sent); err != nil {
		t.Fatalf("action response should be json: %v", err)
	}
	if !sent.OK || sent.Kind != OutboxKindText || sent.OutboxID <= 0 || sent.Outbox.ID != sent.OutboxID {
		t.Fatalf("unexpected action response: %+v", sent)
	}
	if len(sent.OutboxIDs) != 2 || len(sent.Outboxes) != 2 || sent.OutboxIDs[0] != sent.OutboxID || sent.Outboxes[0].ID != sent.OutboxID {
		t.Fatalf("multi-target response should include every outbox while keeping compatibility fields: %+v", sent)
	}
	if sent.ChatRecordID != 0 || sent.Outbox.ChatRecordID != 0 {
		t.Fatalf("queued action response must not claim a real chat_record_id before ACK: %+v", sent)
	}
	if sent.Outbox.TargetWxID != "wxid_friend" || sent.Outbox.Text != "fanout through v1 action" {
		t.Fatalf("action response should expose the first queued outbox item: %+v", sent.Outbox)
	}
	if sent.Outboxes[1].TargetWxID != "wxid_second" || sent.Outboxes[1].Text != "fanout through v1 action" {
		t.Fatalf("multi-target response should expose the second queued outbox item: %+v", sent.Outboxes)
	}
	if !containsString(sent.Warnings, "multiple_targets_outboxes_returned") || !containsString(sent.Warnings, "singular_outbox_fields_reference_first_target") {
		t.Fatalf("multi-target action should describe compatibility fields: %+v", sent.Warnings)
	}

	targets := map[string]bool{}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 2 {
		t.Fatalf("module polling should expose queued targets as a batch: %+v", items)
	}
	for index, item := range items {
		if item.Kind != OutboxKindText || item.OwnerWxID != "wxid_self" || item.Text != "fanout through v1 action" {
			t.Fatalf("unexpected queued action item: %+v", item)
		}
		if item.ID != sent.OutboxIDs[index] || item.ID != sent.Outboxes[index].ID {
			t.Fatalf("public response should preserve queued outbox order, response=%+v item=%+v", sent, item)
		}
		targets[item.WxID] = true
		if _, err := service.AckOutbox(t.Context(), ModuleAckRequest{
			APIKey: testAPIKey,
			Device: "phone-a",
			WxID:   "wxid_self",
			Items:  []ModuleAckItem{{ID: item.ID, Status: "sent", ChatRecordID: 1000 + int64(index)}},
		}); err != nil {
			t.Fatal(err)
		}
	}
	if !targets["wxid_friend"] || !targets["wxid_second"] {
		t.Fatalf("queued action targets mismatch: %+v", targets)
	}
	if items := pollOutbox(t, service, "phone-a", 10); len(items) != 0 {
		t.Fatalf("all queued action targets should be acked: %+v", items)
	}
}

func TestPublicV1TypedMessageEndpointsCoverAllActionKinds(t *testing.T) {
	service := newTestService("", WithMediaDir(t.TempDir()))
	server := NewHTTPServer(service, "admin").Handler()

	mediaBase64 := base64.StdEncoding.EncodeToString([]byte{0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1, 2, 3})
	amrBase64 := base64.StdEncoding.EncodeToString([]byte("#!AMR\n" + strings.Repeat("\x7c", 50)))
	fileBase64 := base64.StdEncoding.EncodeToString([]byte("%PDF-1.4\n%test\n"))

	cases := []struct {
		name      string
		path      string
		wantKind  string
		fields    map[string]any
		checkItem func(t *testing.T, item ModuleOutboxItem, payload map[string]any)
	}{
		{
			name:     "text",
			path:     "/api/v1/messages/text",
			wantKind: OutboxKindText,
			fields: map[string]any{
				"text": "hello from v1",
			},
			checkItem: func(t *testing.T, item ModuleOutboxItem, payload map[string]any) {
				t.Helper()
				if item.Text != "hello from v1" || payload["text"] != "hello from v1" {
					t.Fatalf("text endpoint should preserve text, item=%+v payload=%+v", item, payload)
				}
			},
		},
		{
			name:     "image",
			path:     "/api/v1/messages/image",
			wantKind: OutboxKindImage,
			fields: map[string]any{
				"media_base64": mediaBase64,
				"media_mime":   "image/png",
				"media_name":   "sample.png",
			},
			checkItem: assertQueuedMedia(OutboxKindImage, "sample.png"),
		},
		{
			name:     "video",
			path:     "/api/v1/messages/video",
			wantKind: OutboxKindVideo,
			fields: map[string]any{
				"media_base64": mediaBase64,
				"media_mime":   "video/mp4",
				"media_name":   "sample.mp4",
			},
			checkItem: assertQueuedMedia(OutboxKindVideo, "sample.mp4"),
		},
		{
			name:     "voice",
			path:     "/api/v1/messages/voice",
			wantKind: OutboxKindVoice,
			fields: map[string]any{
				"media_base64": amrBase64,
				"media_mime":   "audio/amr",
				"media_name":   "sample.amr",
			},
			checkItem: assertQueuedMedia(OutboxKindVoice, "sample.amr"),
		},
		{
			name:     "file",
			path:     "/api/v1/messages/file",
			wantKind: OutboxKindFile,
			fields: map[string]any{
				"media_base64": fileBase64,
				"media_mime":   "application/pdf",
				"media_name":   "sample.pdf",
			},
			checkItem: assertQueuedMedia(OutboxKindFile, "sample.pdf"),
		},
		{
			name:     "emoji",
			path:     "/api/v1/messages/emoji",
			wantKind: OutboxKindEmoji,
			fields: map[string]any{
				"emoji_md5": "0123456789abcdef0123456789abcdef",
			},
			checkItem: func(t *testing.T, item ModuleOutboxItem, payload map[string]any) {
				t.Helper()
				if item.MediaKind != OutboxKindEmoji || payload["emoji_md5"] == "" {
					t.Fatalf("emoji endpoint should queue md5 payload, item=%+v payload=%+v", item, payload)
				}
			},
		},
		{
			name:     "location",
			path:     "/api/v1/messages/location",
			wantKind: OutboxKindLocation,
			fields: map[string]any{
				"location_latitude":  30.25,
				"location_longitude": 120.15,
				"location_label":     "Office",
			},
			checkItem: func(t *testing.T, _ ModuleOutboxItem, payload map[string]any) {
				t.Helper()
				if payload["location_label"] != "Office" || payload["location_scale"] != float64(16) {
					t.Fatalf("location endpoint should default scale and preserve label, payload=%+v", payload)
				}
			},
		},
		{
			name:     "quote",
			path:     "/api/v1/messages/quote",
			wantKind: OutboxKindQuote,
			fields: map[string]any{
				"text":         "quoted reply",
				"quote_msg_id": 12345,
			},
			checkItem: func(t *testing.T, _ ModuleOutboxItem, payload map[string]any) {
				t.Helper()
				if payload["quote_msg_id"] != float64(12345) || payload["quote_chat_record_id"] != float64(12345) {
					t.Fatalf("quote endpoint should mirror quote ids, payload=%+v", payload)
				}
			},
		},
		{
			name:     "link",
			path:     "/api/v1/messages/link",
			wantKind: OutboxKindLink,
			fields: map[string]any{
				"appmsg_title": "Example",
				"appmsg_url":   "https://example.test/page",
			},
			checkItem: func(t *testing.T, _ ModuleOutboxItem, payload map[string]any) {
				t.Helper()
				if payload["appmsg_title"] != "Example" || payload["appmsg_url"] != "https://example.test/page" {
					t.Fatalf("link endpoint should preserve appmsg title/url, payload=%+v", payload)
				}
			},
		},
		{
			name:     "mini-program",
			path:     "/api/v1/messages/mini-program",
			wantKind: OutboxKindMiniProgram,
			fields: map[string]any{
				"appmsg_title":           "Mini",
				"mini_program_username":  "gh_demo@app",
				"mini_program_page_path": "pages/index",
				"mini_program_appid":     "wx-demo",
				"mini_program_icon_url":  "https://example.test/icon.png",
				"mini_program_version":   1,
				"mini_program_type":      0,
				"appmsg_description":     "Mini page",
				"appmsg_thumb_url":       "https://example.test/thumb.png",
				"appmsg_app_name":        "Demo",
				"source_chat_record_id":  0,
				"source_chat_record_ids": []int64{},
				"location_from_poi_list": false,
				"forward_original":       false,
			},
			checkItem: func(t *testing.T, _ ModuleOutboxItem, payload map[string]any) {
				t.Helper()
				if payload["mini_program_username"] != "gh_demo@app" || payload["mini_program_page_path"] != "pages/index" {
					t.Fatalf("mini-program endpoint should preserve mini program fields, payload=%+v", payload)
				}
			},
		},
		{
			name:     "chat-history",
			path:     "/api/v1/messages/chat-history",
			wantKind: OutboxKindChatHistory,
			fields: map[string]any{
				"record_title":           "History",
				"record_description":     "Two messages",
				"source_chat_record_ids": []int64{101, 102},
			},
			checkItem: func(t *testing.T, _ ModuleOutboxItem, payload map[string]any) {
				t.Helper()
				ids, ok := payload["source_chat_record_ids"].([]any)
				if !ok || len(ids) != 2 || payload["record_title"] != "History" {
					t.Fatalf("chat-history endpoint should preserve source ids and title, payload=%+v", payload)
				}
			},
		},
		{
			name:     "revoke",
			path:     "/api/v1/messages/revoke",
			wantKind: OutboxKindRevoke,
			fields: map[string]any{
				"target_chat_record_id": 7654321,
			},
			checkItem: func(t *testing.T, item ModuleOutboxItem, payload map[string]any) {
				t.Helper()
				if item.Text != "[撤回]" || payload["target_chat_record_id"] != float64(7654321) {
					t.Fatalf("revoke endpoint should preserve target id, item=%+v payload=%+v", item, payload)
				}
			},
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			body := map[string]any{
				"device":     "phone-a",
				"owner_wxid": "wxid_self",
				"wx_ids":     []string{"wxid_friend"},
			}
			for key, value := range tc.fields {
				body[key] = value
			}
			raw, err := json.Marshal(body)
			if err != nil {
				t.Fatal(err)
			}
			req := httptest.NewRequest(http.MethodPost, tc.path, bytes.NewReader(raw))
			req.Header.Set("X-Bridge-Password", "admin")
			rec := httptest.NewRecorder()
			server.ServeHTTP(rec, req)
			if rec.Code != http.StatusOK {
				t.Fatalf("%s status=%d body=%s", tc.path, rec.Code, rec.Body.String())
			}
			if strings.Contains(rec.Body.String(), "payload_json") || strings.Contains(rec.Body.String(), "media_base64") {
				t.Fatalf("public send response should not expose internal payloads: %s", rec.Body.String())
			}
			var sent PublicSendResponse
			if err := json.Unmarshal(rec.Body.Bytes(), &sent); err != nil {
				t.Fatalf("send response should be json: %v", err)
			}
			if !sent.OK || sent.Kind != tc.wantKind || sent.Outbox.Kind != tc.wantKind || sent.OutboxID <= 0 {
				t.Fatalf("unexpected public send response: %+v", sent)
			}
			item, err := service.OutboxItem(t.Context(), sent.OutboxID)
			if err != nil {
				t.Fatal(err)
			}
			if item.Kind != tc.wantKind || item.WxID != "wxid_friend" || item.OwnerWxID != "wxid_self" {
				t.Fatalf("unexpected queued outbox item: %+v", item)
			}
			if bytes.Contains(item.PayloadJSON, []byte("media_base64")) {
				t.Fatalf("queued payload must not preserve media_base64: %s", item.PayloadJSON)
			}
			var payload map[string]any
			if err := json.Unmarshal(item.PayloadJSON, &payload); err != nil {
				t.Fatalf("queued payload should be json: %v", err)
			}
			if payload["kind"] != tc.wantKind {
				t.Fatalf("payload kind mismatch: %+v", payload)
			}
			tc.checkItem(t, item, payload)
		})
	}
}

func assertQueuedMedia(kind, name string) func(t *testing.T, item ModuleOutboxItem, payload map[string]any) {
	return func(t *testing.T, item ModuleOutboxItem, payload map[string]any) {
		t.Helper()
		if item.MediaKind != kind || item.MediaName != name || item.MediaURL == "" || item.MediaSize <= 0 {
			t.Fatalf("media endpoint should queue stored media, item=%+v", item)
		}
		if payload["media_kind"] != kind || payload["media_name"] != name || payload["media_url"] == "" || payload["media_size"] == nil {
			t.Fatalf("media endpoint should preserve media metadata in payload, payload=%+v", payload)
		}
	}
}

func TestPublicV1APIKeyBindsDeviceAndOwner(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"wx_ids":["wxid_friend"],
		"text":"key scoped hello"
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/messages/text", bytes.NewReader(body))
	req.Header.Set("X-Bridge-API-Key", testAPIKey)
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("API key send should not require explicit device or owner_wxid, got status=%d body=%s", rec.Code, rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 || items[0].Device != "phone-a" || items[0].OwnerWxID != "wxid_self" || items[0].Text != "key scoped hello" {
		t.Fatalf("unexpected API key scoped outbox item: %+v", items)
	}

	forbiddenBody := []byte(`{
		"device":"phone-b",
		"wx_ids":["wxid_friend"],
		"text":"wrong device"
	}`)
	forbiddenReq := httptest.NewRequest(http.MethodPost, "/api/v1/messages/text", bytes.NewReader(forbiddenBody))
	forbiddenReq.Header.Set("X-Bridge-API-Key", testAPIKey)
	forbiddenRec := httptest.NewRecorder()
	server.ServeHTTP(forbiddenRec, forbiddenReq)
	if forbiddenRec.Code != http.StatusForbidden || !strings.Contains(forbiddenRec.Body.String(), "device_forbidden") {
		t.Fatalf("cross-device API key send should be rejected, got status=%d body=%s", forbiddenRec.Code, forbiddenRec.Body.String())
	}
}

func TestPublicV1APIKeySendRequiresRegisteredOwnerWxID(t *testing.T) {
	service := newTestService("")
	service.mu.Lock()
	device := service.cfg.Devices["phone-a"]
	device.WxID = ""
	service.cfg.Devices["phone-a"] = device
	service.mu.Unlock()
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"wx_ids":["wxid_friend"],
		"text":"should wait for module register"
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/messages/text", bytes.NewReader(body))
	req.Header.Set("X-Bridge-API-Key", testAPIKey)
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)
	if rec.Code != http.StatusConflict || !strings.Contains(rec.Body.String(), "owner_wxid_unbound") {
		t.Fatalf("API key send must wait for registered owner_wxid, got status=%d body=%s", rec.Code, rec.Body.String())
	}
	if items := pollOutbox(t, service, "phone-a", 10); len(items) != 0 {
		t.Fatalf("unbound owner_wxid must not create outbox items: %+v", items)
	}
}

func TestPublicV1OutboxEndpointReturnsSendStatus(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"text":"status please"
	}`)
	sendReq := httptest.NewRequest(http.MethodPost, "/api/v1/messages/text", bytes.NewReader(body))
	sendReq.Header.Set("X-Bridge-Password", "admin")
	sendRec := httptest.NewRecorder()
	server.ServeHTTP(sendRec, sendReq)
	if sendRec.Code != http.StatusOK {
		t.Fatalf("unexpected send status %d body=%s", sendRec.Code, sendRec.Body.String())
	}
	var sent PublicSendResponse
	if err := json.Unmarshal(sendRec.Body.Bytes(), &sent); err != nil {
		t.Fatalf("send response should be json: %v", err)
	}
	if !sent.OK || sent.ProtocolVersion != "v1" || sent.OutboxID <= 0 || sent.StatusURL != fmt.Sprintf("/api/v1/outbox/%d", sent.OutboxID) {
		t.Fatalf("send response should include protocol outbox metadata: %+v", sent)
	}
	if sent.Outbox.ID != sent.OutboxID || sent.Outbox.Status != "pending" || sent.Outbox.TargetWxID != "wxid_friend" {
		t.Fatalf("send response outbox should be public envelope: %+v", sent.Outbox)
	}
	if strings.Contains(sendRec.Body.String(), "payload_json") || strings.Contains(sendRec.Body.String(), "media_base64") || strings.Contains(sendRec.Body.String(), "api_key") {
		t.Fatalf("send response should not expose internal fields: %s", sendRec.Body.String())
	}

	statusReq := httptest.NewRequest(http.MethodGet, fmt.Sprintf("/api/v1/outbox/%d", sent.OutboxID), nil)
	statusReq.Header.Set("X-Bridge-Password", "admin")
	statusRec := httptest.NewRecorder()
	server.ServeHTTP(statusRec, statusReq)
	if statusRec.Code != http.StatusOK {
		t.Fatalf("unexpected outbox status response code=%d body=%s", statusRec.Code, statusRec.Body.String())
	}
	var status PublicOutboxResponse
	if err := json.Unmarshal(statusRec.Body.Bytes(), &status); err != nil {
		t.Fatalf("outbox status response should be json: %v", err)
	}
	if !status.OK || status.ProtocolVersion != "v1" || status.Outbox.ID != sent.OutboxID || status.Outbox.Status != "pending" {
		t.Fatalf("unexpected public outbox status response: %+v", status)
	}
	if strings.Contains(statusRec.Body.String(), "payload_json") || strings.Contains(statusRec.Body.String(), "api_key") {
		t.Fatalf("outbox status should not expose internal fields: %s", statusRec.Body.String())
	}

	missingReq := httptest.NewRequest(http.MethodGet, "/api/v1/outbox/99999", nil)
	missingReq.Header.Set("X-Bridge-Password", "admin")
	missingRec := httptest.NewRecorder()
	server.ServeHTTP(missingRec, missingReq)
	if missingRec.Code != http.StatusNotFound {
		t.Fatalf("missing outbox should be 404, got status=%d body=%s", missingRec.Code, missingRec.Body.String())
	}
}

func TestPublicV1OutboxEndpointReflectsTerminalAck(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	sendBody := func(text string) []byte {
		t.Helper()
		raw, err := json.Marshal(map[string]any{
			"device":     "phone-a",
			"owner_wxid": "wxid_self",
			"wx_ids":     []string{"wxid_friend"},
			"text":       text,
		})
		if err != nil {
			t.Fatal(err)
		}
		return raw
	}
	send := func(text string) PublicSendResponse {
		t.Helper()
		req := httptest.NewRequest(http.MethodPost, "/api/v1/messages/text", bytes.NewReader(sendBody(text)))
		req.Header.Set("X-Bridge-Password", "admin")
		rec := httptest.NewRecorder()
		server.ServeHTTP(rec, req)
		if rec.Code != http.StatusOK {
			t.Fatalf("unexpected send status=%d body=%s", rec.Code, rec.Body.String())
		}
		var sent PublicSendResponse
		if err := json.Unmarshal(rec.Body.Bytes(), &sent); err != nil {
			t.Fatalf("send response should be json: %v", err)
		}
		return sent
	}

	failed := send("will fail")
	sent := send("will send")
	if _, err := service.AckOutbox(t.Context(), ModuleAckRequest{
		APIKey: testAPIKey,
		Device: "phone-a",
		WxID:   "wxid_self",
		Items: []ModuleAckItem{
			{ID: failed.OutboxID, Status: "failed", Error: "no image sender found"},
			{ID: sent.OutboxID, Status: "sent", ChatRecordID: 99001},
		},
	}); err != nil {
		t.Fatal(err)
	}

	cases := []struct {
		name             string
		id               int64
		wantStatus       string
		wantLastError    string
		wantChatRecordID int64
	}{
		{name: "failed", id: failed.OutboxID, wantStatus: "failed", wantLastError: "no image sender found"},
		{name: "sent", id: sent.OutboxID, wantStatus: "sent", wantChatRecordID: 99001},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			req := httptest.NewRequest(http.MethodGet, fmt.Sprintf("/api/v1/outbox/%d", tc.id), nil)
			req.Header.Set("X-Bridge-Password", "admin")
			rec := httptest.NewRecorder()
			server.ServeHTTP(rec, req)
			if rec.Code != http.StatusOK {
				t.Fatalf("unexpected outbox status code=%d body=%s", rec.Code, rec.Body.String())
			}
			body := rec.Body.String()
			if strings.Contains(body, "payload_json") || strings.Contains(body, "media_base64") || strings.Contains(body, "api_key") {
				t.Fatalf("terminal outbox status should not expose internal fields: %s", body)
			}
			var got PublicOutboxResponse
			if err := json.Unmarshal(rec.Body.Bytes(), &got); err != nil {
				t.Fatalf("outbox status response should be json: %v", err)
			}
			if !got.OK || got.ProtocolVersion != "v1" || got.Outbox.ID != tc.id || got.Outbox.Status != tc.wantStatus {
				t.Fatalf("unexpected terminal outbox response: %+v", got)
			}
			if got.Outbox.LastError != tc.wantLastError || got.Outbox.ChatRecordID != tc.wantChatRecordID {
				t.Fatalf("unexpected terminal ack fields: %+v", got.Outbox)
			}
		})
	}
}

func TestPublicV1OutboxEndpointIsScopedByAPIKeyDevice(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"wx_ids":["wxid_friend"],
		"text":"phone b status"
	}`)
	sendReq := httptest.NewRequest(http.MethodPost, "/api/v1/messages/text", bytes.NewReader(body))
	sendReq.Header.Set("X-Bridge-API-Key", "wechat-phone-b-key")
	sendRec := httptest.NewRecorder()
	server.ServeHTTP(sendRec, sendReq)
	if sendRec.Code != http.StatusOK {
		t.Fatalf("unexpected phone-b send status=%d body=%s", sendRec.Code, sendRec.Body.String())
	}
	var sent struct {
		OutboxID int64 `json:"outbox_id"`
	}
	if err := json.Unmarshal(sendRec.Body.Bytes(), &sent); err != nil {
		t.Fatal(err)
	}

	forbiddenReq := httptest.NewRequest(http.MethodGet, fmt.Sprintf("/api/v1/outbox/%d", sent.OutboxID), nil)
	forbiddenReq.Header.Set("X-Bridge-API-Key", testAPIKey)
	forbiddenRec := httptest.NewRecorder()
	server.ServeHTTP(forbiddenRec, forbiddenReq)
	if forbiddenRec.Code != http.StatusNotFound {
		t.Fatalf("outbox item from another device should be hidden, got status=%d body=%s", forbiddenRec.Code, forbiddenRec.Body.String())
	}

	allowedReq := httptest.NewRequest(http.MethodGet, fmt.Sprintf("/api/v1/outbox/%d", sent.OutboxID), nil)
	allowedReq.Header.Set("X-Bridge-API-Key", "wechat-phone-b-key")
	allowedRec := httptest.NewRecorder()
	server.ServeHTTP(allowedRec, allowedReq)
	if allowedRec.Code != http.StatusOK || !strings.Contains(allowedRec.Body.String(), `"device":"phone-b"`) {
		t.Fatalf("own device outbox item should be visible, got status=%d body=%s", allowedRec.Code, allowedRec.Body.String())
	}
}

func TestPublicV1MessagesSupportCursorSync(t *testing.T) {
	reader := &fakeAdminReader{
		messages: []StoredEventView{
			{ID: 10, Device: "phone-a", Direction: string(DirectionRecv), FromWxID: "wxid_a", ToWxID: "wxid_self", Text: "old", MessageType: 1, MessageKind: MessageKindText},
			{ID: 11, Device: "phone-a", Direction: string(DirectionRecv), FromWxID: "wxid_a", ToWxID: "wxid_self", Text: "first", MessageType: 1, MessageKind: MessageKindText},
			{ID: 12, Device: "phone-a", Direction: string(DirectionRecv), FromWxID: "wxid_a", ToWxID: "wxid_self", Text: "second", MessageType: 1, MessageKind: MessageKindText},
			{ID: 13, Device: "phone-a", Direction: string(DirectionRecv), FromWxID: "wxid_a", ToWxID: "wxid_self", Text: "third", MessageType: 1, MessageKind: MessageKindText},
		},
	}
	service := newTestService("http://127.0.0.1:1", WithAdminReader(reader))
	server := NewHTTPServer(service, "admin").Handler()

	req := httptest.NewRequest(http.MethodGet, "/api/v1/messages?after_id=10&limit=2", nil)
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected cursor sync status=%d body=%s", rec.Code, rec.Body.String())
	}
	var got PublicMessagesResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &got); err != nil {
		t.Fatalf("cursor response should be json: %v", err)
	}
	if !got.OK || got.ProtocolVersion != "v1" || !got.HasMore || got.NextCursor != 12 || got.NextCursorParam != "after_id" || got.CursorField != "id" {
		t.Fatalf("unexpected cursor metadata: %+v", got)
	}
	if len(got.Messages) != 2 || got.Messages[0].ID != "11" || got.Messages[1].ID != "12" {
		t.Fatalf("after_id sync should return ascending messages: %+v", got.Messages)
	}
	if got.Messages[0].Text != "first" || got.Messages[1].Text != "second" {
		t.Fatalf("unexpected cursor messages: %+v", got.Messages)
	}

	firstReq := httptest.NewRequest(http.MethodGet, "/api/v1/messages?after_id=0&limit=2", nil)
	firstReq.Header.Set("X-Bridge-Password", "admin")
	firstRec := httptest.NewRecorder()
	server.ServeHTTP(firstRec, firstReq)
	if firstRec.Code != http.StatusOK {
		t.Fatalf("unexpected first sync status=%d body=%s", firstRec.Code, firstRec.Body.String())
	}
	var first PublicMessagesResponse
	if err := json.Unmarshal(firstRec.Body.Bytes(), &first); err != nil {
		t.Fatalf("first sync response should be json: %v", err)
	}
	if first.NextCursorParam != "after_id" || len(first.Messages) != 2 || first.Messages[0].ID != "10" || first.Messages[1].ID != "11" {
		t.Fatalf("after_id=0 should start forward sync: %+v", first)
	}
}

func TestPublicV1MessagesAPIKeyIsScopedToDeviceAndOwner(t *testing.T) {
	reader := &fakeAdminReader{
		messages: []StoredEventView{
			{ID: 1, Device: "phone-a", OwnerWxID: "wxid_self", Direction: string(DirectionRecv), FromWxID: "wxid_friend", ToWxID: "wxid_self", Text: "visible", MessageType: 1, MessageKind: MessageKindText, ChatID: "wxid_friend", ChatKind: string(ChatKindDirect)},
			{ID: 2, Device: "phone-a", OwnerWxID: "wxid_old", Direction: string(DirectionRecv), FromWxID: "wxid_old_friend", ToWxID: "wxid_old", Text: "old owner hidden", MessageType: 1, MessageKind: MessageKindText, ChatID: "wxid_old_friend", ChatKind: string(ChatKindDirect)},
			{ID: 3, Device: "phone-b", OwnerWxID: "wxid_phone_b", Direction: string(DirectionRecv), FromWxID: "wxid_phone_b_friend", ToWxID: "wxid_phone_b", Text: "other device hidden", MessageType: 1, MessageKind: MessageKindText, ChatID: "wxid_phone_b_friend", ChatKind: string(ChatKindDirect)},
		},
	}
	service := newTestService("http://127.0.0.1:1", WithAdminReader(reader))
	server := NewHTTPServer(service, "admin").Handler()

	req := httptest.NewRequest(http.MethodGet, "/api/v1/messages?limit=10", nil)
	req.Header.Set("X-Bridge-API-Key", testAPIKey)
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected messages status=%d body=%s", rec.Code, rec.Body.String())
	}
	var got PublicMessagesResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &got); err != nil {
		t.Fatalf("messages response should be json: %v", err)
	}
	if len(got.Messages) != 1 || got.Messages[0].Text != "visible" || got.Messages[0].Device != "phone-a" || got.Messages[0].OwnerWxID != "wxid_self" {
		t.Fatalf("API key messages should only include bound device and current owner: %+v", got.Messages)
	}
	if strings.Contains(rec.Body.String(), "old owner hidden") || strings.Contains(rec.Body.String(), "other device hidden") {
		t.Fatalf("API key messages leaked another owner/device: %s", rec.Body.String())
	}

	forbiddenDeviceReq := httptest.NewRequest(http.MethodGet, "/api/v1/messages?device=phone-b", nil)
	forbiddenDeviceReq.Header.Set("X-Bridge-API-Key", testAPIKey)
	forbiddenDeviceRec := httptest.NewRecorder()
	server.ServeHTTP(forbiddenDeviceRec, forbiddenDeviceReq)
	if forbiddenDeviceRec.Code != http.StatusForbidden || !strings.Contains(forbiddenDeviceRec.Body.String(), "device_forbidden") {
		t.Fatalf("cross-device API key read should be rejected, got status=%d body=%s", forbiddenDeviceRec.Code, forbiddenDeviceRec.Body.String())
	}

	forbiddenOwnerReq := httptest.NewRequest(http.MethodGet, "/api/v1/messages?owner_wxid=wxid_old", nil)
	forbiddenOwnerReq.Header.Set("X-Bridge-API-Key", testAPIKey)
	forbiddenOwnerRec := httptest.NewRecorder()
	server.ServeHTTP(forbiddenOwnerRec, forbiddenOwnerReq)
	if forbiddenOwnerRec.Code != http.StatusForbidden || !strings.Contains(forbiddenOwnerRec.Body.String(), "owner_wxid_forbidden") {
		t.Fatalf("cross-owner API key read should be rejected, got status=%d body=%s", forbiddenOwnerRec.Code, forbiddenOwnerRec.Body.String())
	}
}

func TestPublicV1MessagesRejectConflictingCursors(t *testing.T) {
	service := newTestService("http://127.0.0.1:1", WithAdminReader(&fakeAdminReader{}))
	server := NewHTTPServer(service, "admin").Handler()

	req := httptest.NewRequest(http.MethodGet, "/api/v1/messages?after_id=10&before_id=20", nil)
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest || !strings.Contains(rec.Body.String(), "cursor_conflict") {
		t.Fatalf("conflicting cursors should be rejected, got status=%d body=%s", rec.Code, rec.Body.String())
	}
}

func TestPublicV1MessagesReturnProtocolEnvelopes(t *testing.T) {
	reader := &fakeAdminReader{
		messages: []StoredEventView{{
			ID:              9,
			SourceID:        "internal-source-id",
			ChatRecordID:    123,
			Device:          "phone-a",
			OwnerWxID:       "wxid_self",
			Direction:       string(DirectionRecv),
			FromWxID:        "wxid_friend",
			ToWxID:          "wxid_self",
			Text:            "photo",
			MessageType:     3,
			MessageKind:     MessageKindImage,
			MediaKind:       MessageKindImage,
			MediaMime:       "image/png",
			MediaName:       "photo.png",
			MediaURL:        "/api/media/phone-a/20260702/photo.png",
			MediaSize:       128,
			RawProvider:     RawProviderModuleAck,
			ChatID:          "wxid_friend",
			ChatKind:        string(ChatKindDirect),
			ChatDisplayName: "Friend",
			CreateTime:      1793500000,
		}},
	}
	service := newTestService("http://127.0.0.1:1", WithAdminReader(reader))
	server := NewHTTPServer(service, "admin").Handler()

	req := httptest.NewRequest(http.MethodGet, "/api/v1/messages?device=phone-a&limit=1", nil)
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected v1 messages status=%d body=%s", rec.Code, rec.Body.String())
	}
	if strings.Contains(rec.Body.String(), "raw_provider") || strings.Contains(rec.Body.String(), "media_base64") || strings.Contains(rec.Body.String(), "api_key") || strings.Contains(rec.Body.String(), "internal-source-id") {
		t.Fatalf("v1 messages should not expose internal fields: %s", rec.Body.String())
	}
	var got PublicMessagesResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &got); err != nil {
		t.Fatalf("v1 messages must be valid json: %v", err)
	}
	if !got.OK || got.ProtocolVersion != "v1" || len(got.Messages) != 1 {
		t.Fatalf("unexpected v1 messages response: %+v", got)
	}
	msg := got.Messages[0]
	if msg.ID != "9" || msg.Kind != MessageKindImage || msg.ChatID != "wxid_friend" || msg.ChatKind != string(ChatKindDirect) || msg.ChatDisplayName != "Friend" {
		t.Fatalf("unexpected public envelope identity: %+v", msg)
	}
	if len(msg.Media) != 1 || msg.Media[0].URL != "/api/media/phone-a/20260702/photo.png" || msg.Media[0].Kind != MessageKindImage {
		t.Fatalf("media should be structured: %+v", msg.Media)
	}
}

func TestPublicStoredMessageEnvelopeMarksOpaqueEmojiMedia(t *testing.T) {
	msg := publicStoredMessageEnvelope(StoredEventView{
		ID:                10,
		Direction:         string(DirectionRecv),
		FromWxID:          "wxid_friend",
		ToWxID:            "wxid_self",
		Text:              "[表情]",
		MessageType:       47,
		MessageKind:       MessageKindEmoji,
		AppMsgSubtype:     "emoji",
		AppMsgTitle:       "0123456789abcdef0123456789abcdef",
		AppMsgDescription: "type=1 len=1234",
		AppMsgURL:         "http://example.test/emoji.gif",
		AppMsgFileName:    "0123456789abcdef0123456789abcdef.gif",
		MediaKind:         MessageKindEmoji,
		MediaMime:         "application/octet-stream",
		MediaName:         "0123456789abcdef0123456789abcdef.bin",
		MediaURL:          "/api/media/phone-a/20260703/emoji.bin",
		MediaSize:         1234,
	})

	if msg.Kind != MessageKindEmoji || msg.AppMsg == nil || msg.AppMsg.URL != "http://example.test/emoji.gif" {
		t.Fatalf("emoji appmsg fields should be structured: %+v", msg)
	}
	if len(msg.Media) != 1 || !msg.Media[0].Opaque {
		t.Fatalf("emoji octet-stream media should be marked opaque: %+v", msg.Media)
	}
}

func TestPublicStoredMessageEnvelopeCorrectsLegacyPaymentKind(t *testing.T) {
	transfer := publicStoredMessageEnvelope(StoredEventView{
		ID:          1,
		Direction:   string(DirectionRecv),
		FromWxID:    "wxid_friend",
		ToWxID:      "wxid_self",
		MessageType: MessageTypeTransfer,
		MessageKind: MessageKindUnknown,
		Text:        "[未支持] 类型 419430449",
	})
	if transfer.Kind != MessageKindPayment || transfer.Subtype != "transfer" {
		t.Fatalf("legacy transfer should be exposed as payment: %+v", transfer)
	}

	redPacket := publicStoredMessageEnvelope(StoredEventView{
		ID:          2,
		Direction:   string(DirectionRecv),
		FromWxID:    "wxid_friend",
		ToWxID:      "wxid_self",
		MessageType: MessageTypeAppMsg,
		MessageKind: MessageKindAppMsg,
		AppMsgType:  AppMsgTypeRedPacket,
		Text:        "[红包]",
	})
	if redPacket.Kind != MessageKindPayment || redPacket.Subtype != "red_packet" {
		t.Fatalf("legacy appmsg red packet should be exposed as payment: %+v", redPacket)
	}
}

func TestPublicV1SystemRevokeEnvelope(t *testing.T) {
	event := MessageEvent{
		ID:             "evt-revoke",
		Device:         "phone-a",
		Direction:      DirectionRecv,
		MessageType:    10000,
		MessageKind:    MessageKindSystem,
		AppMsgSubtype:  SystemSubtypeRevoke,
		AppMsgURL:      "room-a@chatroom",
		AppMsgFileName: "12345",
		AppMsgAppName:  "67890",
		AppMsgTitle:    `"Alice" 撤回了一条消息`,
		RawXML:         "<sysmsg>sensitive</sysmsg>",
	}
	envelope := publicEventMessageEnvelope(event)

	if envelope.Kind != MessageKindSystem || envelope.Subtype != SystemSubtypeRevoke {
		t.Fatalf("unexpected system envelope: %+v", envelope)
	}
	if envelope.AppMsg != nil {
		t.Fatalf("system revoke should not be exposed as appmsg: %+v", envelope.AppMsg)
	}
	if envelope.System == nil ||
		envelope.System.Subtype != SystemSubtypeRevoke ||
		envelope.System.Session != "room-a@chatroom" ||
		envelope.System.MsgID != "12345" ||
		envelope.System.NewMsgID != "67890" {
		t.Fatalf("system revoke payload missing: %+v", envelope.System)
	}
}

func TestPublicV1WebSocketStreamsSanitizedEvents(t *testing.T) {
	service := newTestService("")
	service.Hub().Publish(MessageEvent{
		APIKey:      testAPIKey,
		ID:          "evt-1",
		Device:      "phone-a",
		From:        "wxid_friend",
		To:          "wxid_self",
		Text:        "hello ws",
		MediaBase64: "sensitive",
		RawXML:      "<msg>sensitive</msg>",
		Direction:   DirectionRecv,
		CreateTime:  time.Now().Unix(),
	})
	server := httptest.NewServer(NewHTTPServer(service, "admin").Handler())
	defer server.Close()

	conn := dialTestWebSocket(t, server.URL, "/api/v1/ws?password=admin&replay=1")
	defer conn.close()

	hello := readTestPublicWSMessage(t, conn)
	if hello.Type != "hello" || !hello.OK || hello.ProtocolVersion != "v1" {
		t.Fatalf("unexpected hello message: %+v", hello)
	}
	replay := readTestPublicWSMessage(t, conn)
	if replay.Type != "replay" || len(replay.Events) != 1 {
		t.Fatalf("unexpected replay message: %+v", replay)
	}
	if replay.Events[0].Device != "phone-a" || replay.Events[0].Text != "hello ws" || replay.Events[0].ChatID != "wxid_friend" {
		t.Fatalf("public websocket event should be a protocol envelope: %+v", replay.Events[0])
	}

	service.Hub().Publish(MessageEvent{
		ID:         "evt-2",
		Device:     "phone-a",
		From:       "wxid_friend",
		To:         "wxid_self",
		Text:       "live event",
		Direction:  DirectionRecv,
		CreateTime: time.Now().Unix(),
	})
	live := readTestPublicWSMessage(t, conn)
	if live.Type != "message" || live.Event == nil || live.Event.Text != "live event" {
		t.Fatalf("unexpected live ws message: %+v", live)
	}
}

func TestPublicV1WebSocketAPIKeyFiltersDeviceEvents(t *testing.T) {
	service := newTestService("")
	service.Hub().Publish(MessageEvent{
		ID:        "evt-phone-a",
		Device:    "phone-a",
		Text:      "phone a replay",
		Direction: DirectionRecv,
	})
	service.Hub().Publish(MessageEvent{
		ID:        "evt-phone-b",
		Device:    "phone-b",
		Text:      "phone b replay",
		Direction: DirectionRecv,
	})
	server := httptest.NewServer(NewHTTPServer(service, "admin").Handler())
	defer server.Close()

	conn := dialTestWebSocket(t, server.URL, "/api/v1/ws?api_key="+testAPIKey+"&replay=10")
	defer conn.close()

	hello := readTestPublicWSMessage(t, conn)
	if hello.Type != "hello" || !hello.OK {
		t.Fatalf("unexpected hello message: %+v", hello)
	}
	replay := readTestPublicWSMessage(t, conn)
	if replay.Type != "replay" || len(replay.Events) != 1 || replay.Events[0].Device != "phone-a" {
		t.Fatalf("API key replay should only include bound device events: %+v", replay)
	}

	service.Hub().Publish(MessageEvent{ID: "evt-live-b", Device: "phone-b", Text: "hidden live", Direction: DirectionRecv})
	service.Hub().Publish(MessageEvent{ID: "evt-live-a", Device: "phone-a", Text: "visible live", Direction: DirectionRecv})
	live := readTestPublicWSMessage(t, conn)
	if live.Type != "message" || live.Event == nil || live.Event.Device != "phone-a" || live.Event.Text != "visible live" {
		t.Fatalf("API key live stream should only include bound device events: %+v", live)
	}
}

func TestPublicV1WebSocketCommandResponses(t *testing.T) {
	service := newTestService("")
	service.Hub().Publish(MessageEvent{ID: "evt-replay-1", Device: "phone-a", Text: "first", Direction: DirectionRecv})
	service.Hub().Publish(MessageEvent{ID: "evt-replay-2", Device: "phone-a", Text: "second", Direction: DirectionRecv})
	server := httptest.NewServer(NewHTTPServer(service, "admin").Handler())
	defer server.Close()

	conn := dialTestWebSocket(t, server.URL, "/api/v1/ws?password=admin")
	defer conn.close()
	if err := conn.conn.SetReadDeadline(time.Now().Add(3 * time.Second)); err != nil {
		t.Fatal(err)
	}

	hello := readTestPublicWSMessage(t, conn)
	if hello.Type != "hello" || !hello.OK {
		t.Fatalf("unexpected hello message: %+v", hello)
	}

	if !conn.writeJSON(publicWSMessage{Type: " ping "}) {
		t.Fatal("failed to write websocket ping command")
	}
	pong := readTestPublicWSMessage(t, conn)
	if pong.Type != "pong" || !pong.OK {
		t.Fatalf("ping command should return pong, got %+v", pong)
	}

	if !conn.writeJSON(publicWSMessage{Type: "replay", Limit: 1}) {
		t.Fatal("failed to write websocket replay command")
	}
	replay := readTestPublicWSMessage(t, conn)
	if replay.Type != "replay" || !replay.OK || len(replay.Events) != 1 || replay.Events[0].Text != "second" {
		t.Fatalf("replay command should return latest event only, got %+v", replay)
	}

	if !conn.writeJSON(publicWSMessage{Type: "unknown"}) {
		t.Fatal("failed to write websocket unknown command")
	}
	unknown := readTestPublicWSMessage(t, conn)
	if unknown.Type != "error" || !strings.Contains(unknown.Error, "unknown message type") {
		t.Fatalf("unknown command should return explicit error, got %+v", unknown)
	}

	if !conn.writeFrame(wsOpText, []byte(`{`)) {
		t.Fatal("failed to write invalid websocket json")
	}
	invalid := readTestPublicWSMessage(t, conn)
	if invalid.Type != "error" || !strings.Contains(invalid.Error, "invalid json") {
		t.Fatalf("invalid JSON should return explicit error, got %+v", invalid)
	}
}

func TestPublicV1WebSocketReplayLimitBounds(t *testing.T) {
	cases := []struct {
		raw      string
		fallback int
		want     int
	}{
		{raw: "", fallback: 7, want: 7},
		{raw: " 3 ", fallback: 7, want: 3},
		{raw: "-1", fallback: 7, want: 7},
		{raw: "bad", fallback: 7, want: 7},
		{raw: "201", fallback: 7, want: 200},
	}
	for _, tc := range cases {
		if got := wsReplayLimit(tc.raw, tc.fallback); got != tc.want {
			t.Fatalf("wsReplayLimit(%q, %d) = %d, want %d", tc.raw, tc.fallback, got, tc.want)
		}
	}
}

func TestPublicV1ReadAliasesUseAdminReader(t *testing.T) {
	reader := &fakeAdminReader{
		messages: []StoredEventView{{ID: 9, Device: "phone-a", Text: "chat"}},
		modules:  []ModuleStatusView{{Device: "phone-a", RuntimeStatus: "ready"}},
		contacts: []ModuleContactView{{Device: "phone-a", WxID: "wxid_friend", Nickname: "Friend"}},
	}
	service := newTestService("http://127.0.0.1:1", WithAdminReader(reader))
	server := NewHTTPServer(service, "admin").Handler()

	cases := []struct {
		path string
		want string
	}{
		{path: "/api/v1/messages?device=phone-a&wxid=wxid_friend&limit=1", want: `"messages"`},
		{path: "/api/v1/contacts?device=phone-a&q=Friend&limit=1", want: `"contacts"`},
		{path: "/api/v1/modules/status", want: `"modules"`},
	}
	for _, tc := range cases {
		req := httptest.NewRequest(http.MethodGet, tc.path, nil)
		req.Header.Set("X-Bridge-Password", "admin")
		rec := httptest.NewRecorder()
		server.ServeHTTP(rec, req)
		if rec.Code != http.StatusOK || !strings.Contains(rec.Body.String(), tc.want) {
			t.Fatalf("%s unexpected status=%d body=%s", tc.path, rec.Code, rec.Body.String())
		}
	}
}

func TestPublicV1ReadAliasesAreScopedByAPIKeyDevice(t *testing.T) {
	reader := &fakeAdminReader{
		messages: []StoredEventView{{ID: 9, Device: "phone-a", Text: "chat"}},
		modules: []ModuleStatusView{
			{Device: "phone-a", RuntimeStatus: "ready"},
			{Device: "phone-b", RuntimeStatus: "ready"},
		},
		contacts: []ModuleContactView{{Device: "phone-a", WxID: "wxid_friend", Nickname: "Friend"}},
	}
	service := newTestService("http://127.0.0.1:1", WithAdminReader(reader))
	server := NewHTTPServer(service, "admin").Handler()

	msgReq := httptest.NewRequest(http.MethodGet, "/api/v1/messages?limit=1", nil)
	msgReq.Header.Set("X-Bridge-API-Key", testAPIKey)
	msgRec := httptest.NewRecorder()
	server.ServeHTTP(msgRec, msgReq)
	if msgRec.Code != http.StatusOK || !strings.Contains(strings.Join(reader.calls, ","), "messages:phone-a::2") {
		t.Fatalf("messages should be queried with bound device, status=%d body=%s calls=%v", msgRec.Code, msgRec.Body.String(), reader.calls)
	}

	contactReq := httptest.NewRequest(http.MethodGet, "/api/v1/contacts?q=Friend&limit=1", nil)
	contactReq.Header.Set("X-Bridge-API-Key", testAPIKey)
	contactRec := httptest.NewRecorder()
	server.ServeHTTP(contactRec, contactReq)
	if contactRec.Code != http.StatusOK || !strings.Contains(strings.Join(reader.calls, ","), "contacts:phone-a:Friend:1") {
		t.Fatalf("contacts should be queried with bound device, status=%d body=%s calls=%v", contactRec.Code, contactRec.Body.String(), reader.calls)
	}

	statusReq := httptest.NewRequest(http.MethodGet, "/api/v1/modules/status", nil)
	statusReq.Header.Set("X-Bridge-API-Key", testAPIKey)
	statusRec := httptest.NewRecorder()
	server.ServeHTTP(statusRec, statusReq)
	if statusRec.Code != http.StatusOK || strings.Contains(statusRec.Body.String(), `"device":"phone-b"`) {
		t.Fatalf("module status should be filtered to bound device, status=%d body=%s", statusRec.Code, statusRec.Body.String())
	}

	forbiddenReq := httptest.NewRequest(http.MethodGet, "/api/v1/messages?device=phone-b", nil)
	forbiddenReq.Header.Set("X-Bridge-API-Key", testAPIKey)
	forbiddenRec := httptest.NewRecorder()
	server.ServeHTTP(forbiddenRec, forbiddenReq)
	if forbiddenRec.Code != http.StatusForbidden || !strings.Contains(forbiddenRec.Body.String(), "device_forbidden") {
		t.Fatalf("cross-device read should be rejected, got status=%d body=%s", forbiddenRec.Code, forbiddenRec.Body.String())
	}
}

func TestOpenAPIDocsArePublicAndDescribeActionProtocol(t *testing.T) {
	service := newTestService("http://127.0.0.1:1")
	server := NewHTTPServer(service, "admin").Handler()

	pageReq := httptest.NewRequest(http.MethodGet, "/docs", nil)
	pageRec := httptest.NewRecorder()
	server.ServeHTTP(pageRec, pageReq)
	if pageRec.Code != http.StatusOK ||
		!strings.Contains(pageRec.Body.String(), "/docs/openapi.json") ||
		!strings.Contains(pageRec.Body.String(), "/docs/adapter-quickstart-v1.md") ||
		!strings.Contains(pageRec.Body.String(), "微信观测站 API 协议") ||
		!strings.Contains(pageRec.Body.String(), "外部适配快速路径") ||
		!strings.Contains(pageRec.Body.String(), "Public API Python Client") ||
		!strings.Contains(pageRec.Body.String(), "能力矩阵") {
		t.Fatalf("unexpected docs response status=%d body=%s", pageRec.Code, pageRec.Body.String())
	}

	docReq := httptest.NewRequest(http.MethodGet, "/docs/adapter-quickstart-v1.md", nil)
	docRec := httptest.NewRecorder()
	server.ServeHTTP(docRec, docReq)
	if docRec.Code != http.StatusOK || !strings.Contains(docRec.Body.String(), "Adapter Quickstart v1") {
		t.Fatalf("unexpected public doc response status=%d body=%s", docRec.Code, docRec.Body.String())
	}
	missingDocReq := httptest.NewRequest(http.MethodGet, "/docs/internal-notes.md", nil)
	missingDocRec := httptest.NewRecorder()
	server.ServeHTTP(missingDocRec, missingDocReq)
	if missingDocRec.Code != http.StatusNotFound {
		t.Fatalf("unexpected missing doc status=%d body=%s", missingDocRec.Code, missingDocRec.Body.String())
	}

	specReq := httptest.NewRequest(http.MethodGet, "/docs/openapi.json", nil)
	specRec := httptest.NewRecorder()
	server.ServeHTTP(specRec, specReq)
	if specRec.Code != http.StatusOK {
		t.Fatalf("unexpected openapi status=%d body=%s", specRec.Code, specRec.Body.String())
	}
	var spec map[string]any
	if err := json.Unmarshal(specRec.Body.Bytes(), &spec); err != nil {
		t.Fatalf("openapi json must be valid: %v", err)
	}
	info, ok := spec["info"].(map[string]any)
	if !ok || info["title"] != "微信观测站 API 协议" {
		t.Fatalf("openapi title should be localized, got %+v", spec["info"])
	}
	paths, ok := spec["paths"].(map[string]any)
	if !ok {
		t.Fatalf("openapi paths missing: %+v", spec)
	}
	for _, path := range []string{"/api/v1/capabilities", "/api/v1/messages/text", "/api/v1/messages/image", "/api/v1/messages/link", "/api/v1/messages/chat-history", "/api/v1/messages/revoke", "/api/v1/outbox/{id}", "/api/v1/ws", "/api/send/action", "/module/outbox/poll", "/module/outbox/ack", "/webhook/module/message"} {
		if _, ok := paths[path]; !ok {
			t.Fatalf("openapi path %s missing", path)
		}
	}
	components, ok := spec["components"].(map[string]any)
	if !ok {
		t.Fatalf("openapi components missing: %+v", spec)
	}
	schemas, ok := components["schemas"].(map[string]any)
	if !ok {
		t.Fatalf("openapi schemas missing: %+v", components)
	}
	for _, schema := range []string{"CapabilitiesResponse", "PublicSendResponse", "PublicOutboxEnvelope", "PublicMessageEnvelope", "PublicMessageMedia", "TextMessageRequest", "MediaMessageRequest", "LinkMessageRequest", "ChatHistoryMessageRequest"} {
		if _, ok := schemas[schema]; !ok {
			t.Fatalf("openapi schema %s missing", schema)
		}
	}
	for _, code := range []string{"owner_wxid_unbound", "media_forbidden", "cursor_conflict"} {
		if !strings.Contains(specRec.Body.String(), code) {
			t.Fatalf("openapi spec should describe error code %s", code)
		}
	}
	if strings.Contains(specRec.Body.String(), "example-real-password") {
		t.Fatalf("openapi spec must not contain secrets")
	}
}

func TestAdminReadEndpointsUsePersistentReader(t *testing.T) {
	reader := &fakeAdminReader{
		keys: []APIKeyView{
			{Code: "wechat-a-key", APIKey: "wechat-a-key"},
		},
		events: []StoredEventView{
			{ID: 7, Device: "phone-a", Text: "hello"},
		},
		messages: []StoredEventView{
			{ID: 9, Device: "phone-a", Text: "chat"},
		},
		modules: []ModuleStatusView{
			{Device: "phone-a", RuntimeStatus: "ready"},
		},
		contacts: []ModuleContactView{
			{Device: "phone-a", WxID: "wxid_friend", Nickname: "Friend"},
		},
	}
	service := newTestService("http://127.0.0.1:1", WithAdminReader(reader))
	server := NewHTTPServer(service, "admin").Handler()

	cases := []struct {
		path string
		want string
	}{
		{path: "/api/api-keys?limit=1", want: `"api_keys"`},
		{path: "/api/stored-events?limit=1", want: `"events"`},
		{path: "/api/messages?device=phone-a&wxid=wxid_friend&limit=1", want: `"messages"`},
		{path: "/api/modules/status", want: `"modules"`},
		{path: "/api/module-contacts?device=phone-a&q=Friend&limit=1", want: `"contacts"`},
	}
	for _, tc := range cases {
		req := httptest.NewRequest(http.MethodGet, tc.path, nil)
		req.Header.Set("X-Bridge-Password", "admin")
		rec := httptest.NewRecorder()
		server.ServeHTTP(rec, req)
		if rec.Code != http.StatusOK || !strings.Contains(rec.Body.String(), tc.want) {
			t.Fatalf("%s unexpected status=%d body=%s", tc.path, rec.Code, rec.Body.String())
		}
	}
	if got := strings.Join(reader.calls, ","); !strings.Contains(got, "keys:1") || !strings.Contains(got, "events:1") || !strings.Contains(got, "messages:phone-a:wxid_friend:1") || !strings.Contains(got, "modules") || !strings.Contains(got, "contacts:phone-a:Friend:1") {
		t.Fatalf("persistent reader was not used as expected: %+v", reader.calls)
	}
}
