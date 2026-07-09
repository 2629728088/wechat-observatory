package bridge

import (
	"bufio"
	"bytes"
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"strconv"
	"strings"
	"testing"
	"time"

	"wechat-observatory/internal/config"
)

const testAPIKey = "wechat-a-key"

func TestIngestPublishesAndPersistsWithoutBusinessReply(t *testing.T) {
	outbox := &fakeOutbox{}
	persistence := &fakePersistence{}
	service := newTestService("", WithOutbox(outbox), WithPersistence(persistence))

	result, err := service.Ingest(t.Context(), MessageEvent{
		APIKey:    testAPIKey,
		ID:        "101",
		Device:    "phone-a",
		From:      "wxid_friend",
		To:        "wxid_self",
		Text:      "ping",
		Direction: DirectionRecv,
	})
	if err != nil {
		t.Fatal(err)
	}
	if result == nil || !result.Published || result.PersistenceError != "" {
		t.Fatalf("unexpected ingest result: %+v", result)
	}
	if len(persistence.inboundEvents) != 1 {
		t.Fatalf("expected one persisted inbound event, got %+v", persistence.inboundEvents)
	}
	if len(outbox.items) != 0 {
		t.Fatalf("webhook ingest must not enqueue automatic replies: %+v", outbox.items)
	}
	if got := service.Hub().Recent(1); len(got) != 1 || got[0].Text != "ping" || got[0].ChatID() != "wxid_friend" {
		t.Fatalf("unexpected hub event: %+v", got)
	}
}

func TestIngestStoresMediaAttachment(t *testing.T) {
	persistence := &fakePersistence{}
	service := newTestService("", WithPersistence(persistence), WithMediaDir(t.TempDir()))
	raw := []byte{0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1, 2, 3}

	result, err := service.Ingest(t.Context(), MessageEvent{
		APIKey:      testAPIKey,
		ID:          "media-101",
		Device:      "phone-a",
		From:        "wxid_friend",
		To:          "wxid_self",
		Text:        "[鍥剧墖]",
		MessageType: 3,
		Direction:   DirectionRecv,
		MediaKind:   "image",
		MediaMime:   "image/png",
		MediaName:   "photo.png",
		MediaBase64: base64.StdEncoding.EncodeToString(raw),
	})
	if err != nil {
		t.Fatal(err)
	}
	if result == nil || !result.Published || result.PersistenceError != "" {
		t.Fatalf("unexpected ingest result: %+v", result)
	}
	if len(persistence.inboundEvents) != 1 {
		t.Fatalf("expected one inbound event, got %+v", persistence.inboundEvents)
	}
	event := persistence.inboundEvents[0]
	if event.MediaBase64 != "" || event.MediaURL == "" || event.MediaKind != "image" || event.MediaMime != "image/png" {
		t.Fatalf("unexpected persisted media fields: %+v", event)
	}
	if event.MediaSize != int64(len(raw)) {
		t.Fatalf("unexpected media size=%d", event.MediaSize)
	}
	fullPath, err := service.MediaFilePath(strings.TrimPrefix(event.MediaURL, "/api/media/"))
	if err != nil {
		t.Fatal(err)
	}
	stored, err := os.ReadFile(fullPath)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(stored, raw) {
		t.Fatalf("stored media mismatch: %x", stored)
	}
}

func TestIngestParsesAppMsgRawXML(t *testing.T) {
	persistence := &fakePersistence{}
	service := newTestService("", WithPersistence(persistence))

	result, err := service.Ingest(t.Context(), MessageEvent{
		APIKey:      testAPIKey,
		ID:          "appmsg-101",
		Device:      "phone-a",
		From:        "wxid_friend",
		To:          "wxid_self",
		MessageType: 49,
		Direction:   DirectionRecv,
		RawXML:      `<msg><appmsg><title>Spec Document</title><des>Design notes</des><type>6</type><appattach><filename>spec.pdf</filename><totallen>456</totallen></appattach></appmsg></msg>`,
	})
	if err != nil {
		t.Fatal(err)
	}
	if result == nil || !result.Published || result.PersistenceError != "" {
		t.Fatalf("unexpected ingest result: %+v", result)
	}
	if len(persistence.inboundEvents) != 1 {
		t.Fatalf("expected one inbound event, got %+v", persistence.inboundEvents)
	}
	event := persistence.inboundEvents[0]
	if event.MessageKind != MessageKindFile || event.AppMsgSubtype != "file" || event.AppMsgFileName != "spec.pdf" {
		t.Fatalf("unexpected parsed appmsg fields: %+v", event)
	}
	if event.RawXML != "" {
		t.Fatalf("raw xml should not be persisted: %+v", event)
	}
	if event.Text != "[文件] Spec Document" {
		t.Fatalf("unexpected display text: %q", event.Text)
	}
}

func TestIngestPreservesUnknownMessageWithNoText(t *testing.T) {
	persistence := &fakePersistence{}
	service := newTestService("", WithPersistence(persistence))

	result, err := service.Ingest(t.Context(), MessageEvent{
		APIKey:      testAPIKey,
		ID:          "unknown-101",
		Device:      "phone-a",
		From:        "wxid_friend",
		To:          "wxid_self",
		MessageType: 900000001,
		Direction:   DirectionRecv,
	})
	if err != nil {
		t.Fatal(err)
	}
	if result == nil || !result.Published || result.PersistenceError != "" {
		t.Fatalf("unexpected ingest result: %+v", result)
	}
	if len(persistence.inboundEvents) != 1 {
		t.Fatalf("expected one inbound event, got %+v", persistence.inboundEvents)
	}
	event := persistence.inboundEvents[0]
	if event.MessageKind != MessageKindUnknown || event.Text != "[未支持] 类型 900000001" {
		t.Fatalf("unknown message should remain visible: %+v", event)
	}
	if !containsString(event.Unsupported, "message_type:900000001") ||
		!containsString(event.Evidence, "message.type=900000001") {
		t.Fatalf("unknown message evidence missing: %+v", event)
	}
}

func TestIngestParsesLocationRawXML(t *testing.T) {
	persistence := &fakePersistence{}
	service := newTestService("", WithPersistence(persistence))

	result, err := service.Ingest(t.Context(), MessageEvent{
		APIKey:      testAPIKey,
		ID:          "location-101",
		Device:      "phone-a",
		From:        "wxid_friend",
		To:          "wxid_self",
		MessageType: 48,
		Direction:   DirectionRecv,
		RawXML:      `<msg><location x="31.2304" y="121.4737" scale="16" label="People Square" poiname="Metro Station" /></msg>`,
	})
	if err != nil {
		t.Fatal(err)
	}
	if result == nil || !result.Published || result.PersistenceError != "" {
		t.Fatalf("unexpected ingest result: %+v", result)
	}
	if len(persistence.inboundEvents) != 1 {
		t.Fatalf("expected one inbound event, got %+v", persistence.inboundEvents)
	}
	event := persistence.inboundEvents[0]
	if event.MessageKind != MessageKindLocation || event.LocationLatitude == nil || *event.LocationLatitude != 31.2304 || event.LocationLongitude == nil || *event.LocationLongitude != 121.4737 {
		t.Fatalf("unexpected location event: %+v", event)
	}
	if event.Text != "People Square" || event.RawXML != "" || !containsString(event.Evidence, "raw_xml.location.poiname") {
		t.Fatalf("location event should be normalized and redacted: %+v", event)
	}
}

func TestIngestStoresAppMsgThumbnailAsImage(t *testing.T) {
	persistence := &fakePersistence{}
	service := newTestService("", WithPersistence(persistence), WithMediaDir(t.TempDir()))
	raw := []byte{0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 4, 5, 6}

	result, err := service.Ingest(t.Context(), MessageEvent{
		APIKey:      testAPIKey,
		ID:          "appmsg-link-101",
		Device:      "phone-a",
		From:        "wxid_friend",
		To:          "wxid_self",
		MessageType: 49,
		Direction:   DirectionRecv,
		MediaKind:   MessageKindFile,
		MediaMime:   "image/png",
		MediaBase64: base64.StdEncoding.EncodeToString(raw),
		RawXML:      `<msg><appmsg><title>Article</title><des>Summary</des><type>5</type><url>https://example.test/article</url></appmsg></msg>`,
	})
	if err != nil {
		t.Fatal(err)
	}
	if result == nil || !result.Published || result.PersistenceError != "" {
		t.Fatalf("unexpected ingest result: %+v", result)
	}
	if len(persistence.inboundEvents) != 1 {
		t.Fatalf("expected one inbound event, got %+v", persistence.inboundEvents)
	}
	event := persistence.inboundEvents[0]
	if event.MessageKind != MessageKindAppMsg || event.AppMsgSubtype != "link" {
		t.Fatalf("unexpected appmsg fields: %+v", event)
	}
	if event.MediaKind != MessageKindImage || event.MediaURL == "" || event.MediaBase64 != "" {
		t.Fatalf("unexpected thumbnail media fields: %+v", event)
	}
}

func TestLsposedWebhookStoresInboundMessageOnly(t *testing.T) {
	outbox := &fakeOutbox{}
	service := newTestService("", WithOutbox(outbox))
	server := NewHTTPServer(service, "admin").Handler()

	body, err := json.Marshal(MessageEvent{
		APIKey:    testAPIKey,
		Device:    "phone-a",
		Direction: DirectionRecv,
		From:      "wxid_friend",
		To:        "wxid_self",
		Text:      "ping",
	})
	if err != nil {
		t.Fatal(err)
	}

	req := httptest.NewRequest(http.MethodPost, "/webhook/lsposed/message", bytes.NewReader(body))
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}

	var payload struct {
		OK     bool         `json:"ok"`
		Result IngestResult `json:"result"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &payload); err != nil {
		t.Fatal(err)
	}
	if !payload.OK || !payload.Result.Published || payload.Result.PersistenceError != "" {
		t.Fatalf("unexpected webhook response: %s", rec.Body.String())
	}
	if strings.Contains(rec.Body.String(), "business") || strings.Contains(rec.Body.String(), "command") || strings.Contains(rec.Body.String(), "scene") {
		t.Fatalf("webhook response still exposes business fields: %s", rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 0 {
		t.Fatalf("inbound webhook should not enqueue outbox replies: %+v", items)
	}
}

func TestLiveEventsStreamsPublishedMessages(t *testing.T) {
	service := newTestService("")
	server := httptest.NewServer(NewHTTPServer(service, "admin").Handler())
	defer server.Close()

	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, server.URL+"/api/live/events?password=admin", nil)
	if err != nil {
		t.Fatal(err)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("unexpected status %d", resp.StatusCode)
	}

	got := make(chan string, 1)
	go func() {
		buf := make([]byte, 4096)
		var out strings.Builder
		deadline := time.After(2 * time.Second)
		for {
			select {
			case <-deadline:
				got <- out.String()
				return
			default:
			}
			n, err := resp.Body.Read(buf)
			if n > 0 {
				out.Write(buf[:n])
				if strings.Contains(out.String(), "live ping") {
					got <- out.String()
					return
				}
			}
			if err != nil {
				got <- out.String()
				return
			}
		}
	}()

	body, err := json.Marshal(MessageEvent{
		APIKey:    testAPIKey,
		Device:    "phone-a",
		Direction: DirectionRecv,
		From:      "wxid_friend",
		To:        "wxid_self",
		Text:      "live ping",
	})
	if err != nil {
		t.Fatal(err)
	}
	postReq, err := http.NewRequestWithContext(t.Context(), http.MethodPost, server.URL+"/webhook/lsposed/message", bytes.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	postResp, err := http.DefaultClient.Do(postReq)
	if err != nil {
		t.Fatal(err)
	}
	_ = postResp.Body.Close()
	if postResp.StatusCode != http.StatusOK {
		t.Fatalf("unexpected post status %d", postResp.StatusCode)
	}

	select {
	case stream := <-got:
		if !strings.Contains(stream, "event: message") || !strings.Contains(stream, "live ping") {
			t.Fatalf("stream did not contain message event: %s", stream)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("timed out waiting for live event")
	}
}

func TestSentMessagesAreObservedWithoutBusinessRouting(t *testing.T) {
	outbox := &fakeOutbox{}
	service := newTestService("", WithOutbox(outbox))
	result, err := service.Ingest(t.Context(), MessageEvent{
		APIKey:    testAPIKey,
		ID:        "sent-101",
		Device:    "phone-a",
		From:      "wxid_self",
		To:        "wxid_friend",
		Text:      "寮€澶氬彿",
		Direction: DirectionSent,
	})
	if err != nil {
		t.Fatal(err)
	}
	if result == nil || !result.Published {
		t.Fatalf("unexpected sent event result: %+v", result)
	}
	if len(outbox.items) != 0 {
		t.Fatalf("sent observation must not re-enter business or enqueue replies: %+v", outbox.items)
	}
}

func TestAdminSendImageActionStoresMediaAndQueuesAction(t *testing.T) {
	mediaDir := t.TempDir()
	service := newTestService("", WithMediaDir(mediaDir))
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"image",
		"text":"photo",
		"media_mime":"image/png",
		"media_name":"photo.png",
		"media_base64":"iVBORw0KGgo="
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 {
		t.Fatalf("expected one outbox item, got %+v", items)
	}
	item := items[0]
	if item.Kind != OutboxKindImage || item.MediaKind != OutboxKindImage || item.MediaURL == "" || item.MediaSize == 0 {
		t.Fatalf("unexpected image action: %+v", item)
	}
	if item.PayloadJSON == nil || !bytes.Contains(item.PayloadJSON, []byte(`"media_url"`)) {
		t.Fatalf("payload_json should include media_url, got %s", string(item.PayloadJSON))
	}
	if item.Text != "photo" || item.WxID != "wxid_friend" || item.OwnerWxID != "wxid_self" {
		t.Fatalf("unexpected queued target/text: %+v", item)
	}
}

func TestAdminSendVideoActionStoresMediaAndQueuesAction(t *testing.T) {
	mediaDir := t.TempDir()
	service := newTestService("", WithMediaDir(mediaDir))
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"video",
		"media_mime":"video/mp4",
		"media_name":"clip.mp4",
		"media_base64":"AAAAHGZ0eXBtcDQyAAAAAG1wNDFtcDQyaXNvbQ=="
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 {
		t.Fatalf("expected one outbox item, got %+v", items)
	}
	item := items[0]
	if item.Kind != OutboxKindVideo || item.MediaKind != OutboxKindVideo || item.MediaURL == "" || item.MediaSize == 0 {
		t.Fatalf("unexpected video action: %+v", item)
	}
	if item.Text != "[视频]" || item.WxID != "wxid_friend" || item.OwnerWxID != "wxid_self" {
		t.Fatalf("unexpected queued target/text: %+v", item)
	}

	acked, err := service.AckOutbox(t.Context(), ModuleAckRequest{
		APIKey: testAPIKey,
		Device: "phone-a",
		Items: []ModuleAckItem{{
			ID:           item.ID,
			Status:       "sent",
			ChatRecordID: 4321,
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(acked) != 1 {
		t.Fatalf("expected one acked item, got %+v", acked)
	}
	events := service.Hub().Recent(1)
	if len(events) != 1 || events[0].MessageType != 43 || events[0].MediaKind != OutboxKindVideo {
		t.Fatalf("unexpected outbound video event: %+v", events)
	}
}

func TestAckOutboxSuppressesLiveDuplicateWhenObservedMessageAlreadyStored(t *testing.T) {
	persistence := &fakePersistence{}
	service := newTestService("", WithPersistence(persistence))

	outboxID, err := service.SendAction(t.Context(), SendActionRequest{
		Device:    "phone-a",
		OwnerWxID: "wxid_self",
		WxIDs:     []string{"wxid_friend"},
		Kind:      OutboxKindText,
		Text:      "already observed before ack",
	})
	if err != nil {
		t.Fatal(err)
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 || items[0].ID != outboxID {
		t.Fatalf("expected one outbox item, got %+v", items)
	}
	persistence.existingEventKeys = map[string]bool{
		fakeMessageEventKey(MessageEvent{
			Device:       "phone-a",
			OwnerWxID:    "wxid_self",
			Direction:    DirectionSent,
			ChatRecordID: 4321,
		}): true,
	}

	acked, err := service.AckOutbox(t.Context(), ModuleAckRequest{
		APIKey: testAPIKey,
		Device: "phone-a",
		Items: []ModuleAckItem{{
			ID:           items[0].ID,
			Status:       "sent",
			ChatRecordID: 4321,
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(acked) != 1 {
		t.Fatalf("expected one acked item, got %+v", acked)
	}
	if len(persistence.outboundEvents) != 1 {
		t.Fatalf("ack should still update persistence, got %+v", persistence.outboundEvents)
	}
	if got := service.Hub().Recent(1); len(got) != 0 {
		t.Fatalf("duplicate ack event should not hit live hub: %+v", got)
	}
}

func TestAdminSendVoiceActionStoresMediaAndQueuesAction(t *testing.T) {
	mediaDir := t.TempDir()
	service := newTestService("", WithMediaDir(mediaDir))
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"voice",
		"media_mime":"audio/amr",
		"media_name":"voice.amr",
		"media_duration_ms":12000,
		"media_base64":"IyFBTVIKAA=="
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 {
		t.Fatalf("expected one outbox item, got %+v", items)
	}
	item := items[0]
	if item.Kind != OutboxKindVoice || item.MediaKind != OutboxKindVoice || item.MediaURL == "" || item.MediaSize == 0 {
		t.Fatalf("unexpected voice action: %+v", item)
	}
	if item.Text != "[语音]" || item.MediaName != "voice.amr" || item.MediaMime != "audio/amr" {
		t.Fatalf("unexpected queued voice metadata: %+v", item)
	}
	var payload map[string]any
	if err := json.Unmarshal(item.PayloadJSON, &payload); err != nil {
		t.Fatalf("payload_json should be valid json: %v", err)
	}
	if payload["media_duration_ms"] != float64(12000) || payload["duration_ms"] != float64(12000) {
		t.Fatalf("payload_json should include voice duration, got %+v", payload)
	}

	acked, err := service.AckOutbox(t.Context(), ModuleAckRequest{
		APIKey: testAPIKey,
		Device: "phone-a",
		Items: []ModuleAckItem{{
			ID:           item.ID,
			Status:       "sent",
			ChatRecordID: 8765,
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(acked) != 1 {
		t.Fatalf("expected one acked item, got %+v", acked)
	}
	events := service.Hub().Recent(1)
	if len(events) != 1 || events[0].MessageType != 34 || events[0].MediaKind != OutboxKindVoice || events[0].MediaName != "voice.amr" {
		t.Fatalf("unexpected outbound voice event: %+v", events)
	}
}

func TestAdminSendFileActionStoresMediaAndQueuesAction(t *testing.T) {
	mediaDir := t.TempDir()
	service := newTestService("", WithMediaDir(mediaDir))
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"file",
		"media_mime":"text/plain",
		"media_name":"note.txt",
		"media_base64":"aGVsbG8="
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 {
		t.Fatalf("expected one outbox item, got %+v", items)
	}
	item := items[0]
	if item.Kind != OutboxKindFile || item.MediaKind != OutboxKindFile || item.MediaURL == "" || item.MediaSize == 0 {
		t.Fatalf("unexpected file action: %+v", item)
	}
	if item.Text != "[文件]" || item.MediaName != "note.txt" || item.MediaMime != "text/plain" {
		t.Fatalf("unexpected queued file metadata: %+v", item)
	}
	if item.PayloadJSON == nil || !bytes.Contains(item.PayloadJSON, []byte(`"media_url"`)) {
		t.Fatalf("payload_json should include media_url, got %s", string(item.PayloadJSON))
	}

	acked, err := service.AckOutbox(t.Context(), ModuleAckRequest{
		APIKey: testAPIKey,
		Device: "phone-a",
		Items: []ModuleAckItem{{
			ID:           item.ID,
			Status:       "sent",
			ChatRecordID: 5432,
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(acked) != 1 {
		t.Fatalf("expected one acked item, got %+v", acked)
	}
	events := service.Hub().Recent(1)
	if len(events) != 1 || events[0].MessageType != MessageTypeFileTransfer || events[0].MediaKind != OutboxKindFile || events[0].MediaName != "note.txt" {
		t.Fatalf("unexpected outbound file event: %+v", events)
	}
}

func TestAdminSendLocationActionQueuesAction(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"location",
		"location_latitude":31.2304,
		"location_longitude":121.4737,
		"location_scale":15,
		"location_label":"人民广场",
		"location_poiname":"人民广场站"
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 {
		t.Fatalf("expected one outbox item, got %+v", items)
	}
	item := items[0]
	if item.Kind != OutboxKindLocation || item.Text != "人民广场" || item.WxID != "wxid_friend" {
		t.Fatalf("unexpected location action: %+v", item)
	}
	var payload struct {
		Latitude  float64 `json:"location_latitude"`
		Longitude float64 `json:"location_longitude"`
		Scale     int     `json:"location_scale"`
		Label     string  `json:"location_label"`
		PoiName   string  `json:"location_poiname"`
	}
	if err := json.Unmarshal(item.PayloadJSON, &payload); err != nil {
		t.Fatalf("payload_json should be valid json: %v", err)
	}
	if payload.Latitude != 31.2304 || payload.Longitude != 121.4737 ||
		payload.Scale != 15 || payload.Label != "人民广场" || payload.PoiName != "人民广场站" {
		t.Fatalf("unexpected location payload: %+v", payload)
	}

	acked, err := service.AckOutbox(t.Context(), ModuleAckRequest{
		APIKey: testAPIKey,
		Device: "phone-a",
		Items: []ModuleAckItem{{
			ID:           item.ID,
			Status:       "sent",
			ChatRecordID: 67893,
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(acked) != 1 {
		t.Fatalf("expected one acked item, got %+v", acked)
	}
	events := service.Hub().Recent(1)
	if len(events) != 1 ||
		events[0].MessageType != 48 ||
		events[0].MessageKind != MessageKindLocation ||
		events[0].Text != "人民广场" {
		t.Fatalf("unexpected outbound location event: %+v", events)
	}
}

func TestAdminSendLocationActionRequiresCoordinates(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"location",
		"location_label":"missing coordinates"
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("missing location coordinates should be rejected, got status %d body=%s", rec.Code, rec.Body.String())
	}
}

func TestAdminSendEmojiActionQueuesAction(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"emoji",
		"source_chat_record_id":8301,
		"emoji_md5":"0123456789abcdef0123456789abcdef",
		"emoji_product_id":"prod-1"
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 {
		t.Fatalf("expected one outbox item, got %+v", items)
	}
	item := items[0]
	if item.Kind != OutboxKindEmoji || item.MediaKind != OutboxKindEmoji || item.Text != "[表情]" || item.WxID != "wxid_friend" {
		t.Fatalf("unexpected emoji action: %+v", item)
	}
	var payload struct {
		EmojiMD5           string `json:"emoji_md5"`
		EmojiProductID     string `json:"emoji_product_id"`
		SourceChatRecordID int64  `json:"source_chat_record_id"`
	}
	if err := json.Unmarshal(item.PayloadJSON, &payload); err != nil {
		t.Fatalf("payload_json should be valid json: %v", err)
	}
	if payload.EmojiMD5 != "0123456789abcdef0123456789abcdef" ||
		payload.EmojiProductID != "prod-1" ||
		payload.SourceChatRecordID != 8301 {
		t.Fatalf("unexpected emoji payload: %+v", payload)
	}

	acked, err := service.AckOutbox(t.Context(), ModuleAckRequest{
		APIKey: testAPIKey,
		Device: "phone-a",
		Items: []ModuleAckItem{{
			ID:           item.ID,
			Status:       "sent",
			ChatRecordID: 67894,
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(acked) != 1 {
		t.Fatalf("expected one acked item, got %+v", acked)
	}
	events := service.Hub().Recent(1)
	if len(events) != 1 ||
		events[0].MessageType != 47 ||
		events[0].MessageKind != MessageKindEmoji ||
		events[0].MediaKind != MessageKindEmoji ||
		events[0].AppMsgSubtype != "emoji" {
		t.Fatalf("unexpected outbound emoji event: %+v", events)
	}
}

func TestAdminSendEmojiActionRequiresSourceOrMD5(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"emoji"
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("missing emoji source should be rejected, got status %d body=%s", rec.Code, rec.Body.String())
	}
}

func TestAdminSendQuoteActionQueuesAction(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"quote",
		"text":"quoted reply",
		"quote_chat_record_id":12345,
		"quote_talker":"room@chatroom",
		"quote_sender_wxid":"wxid_member"
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 {
		t.Fatalf("expected one outbox item, got %+v", items)
	}
	item := items[0]
	if item.Kind != OutboxKindQuote || item.Text != "quoted reply" || item.WxID != "wxid_friend" {
		t.Fatalf("unexpected quote action: %+v", item)
	}
	if item.PayloadJSON == nil ||
		!bytes.Contains(item.PayloadJSON, []byte(`"quote_msg_id":12345`)) ||
		!bytes.Contains(item.PayloadJSON, []byte(`"quote_chat_record_id":12345`)) ||
		!bytes.Contains(item.PayloadJSON, []byte(`"quote_talker":"room@chatroom"`)) ||
		!bytes.Contains(item.PayloadJSON, []byte(`"quote_sender_wxid":"wxid_member"`)) {
		t.Fatalf("payload_json should include quote metadata, got %s", string(item.PayloadJSON))
	}

	acked, err := service.AckOutbox(t.Context(), ModuleAckRequest{
		APIKey: testAPIKey,
		Device: "phone-a",
		Items: []ModuleAckItem{{
			ID:           item.ID,
			Status:       "sent",
			ChatRecordID: 67890,
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(acked) != 1 {
		t.Fatalf("expected one acked item, got %+v", acked)
	}
	events := service.Hub().Recent(1)
	if len(events) != 1 ||
		events[0].MessageType != MessageTypeQuote ||
		events[0].MessageKind != MessageKindAppMsg ||
		events[0].AppMsgSubtype != "quote" {
		t.Fatalf("unexpected outbound quote event: %+v", events)
	}
}

func TestAdminSendQuoteActionRequiresQuoteMsgID(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"quote",
		"text":"quoted reply"
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("missing quote msg id should be rejected, got status %d body=%s", rec.Code, rec.Body.String())
	}
}

func TestAdminSendLinkActionQueuesAppMsg(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"link",
		"appmsg_title":"Article",
		"appmsg_description":"Summary",
		"appmsg_url":"https://example.test/article",
		"appmsg_app_name":"Docs"
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 {
		t.Fatalf("expected one outbox item, got %+v", items)
	}
	item := items[0]
	if item.Kind != OutboxKindLink || item.Text != "Article" || item.WxID != "wxid_friend" {
		t.Fatalf("unexpected link action: %+v", item)
	}
	var payload struct {
		Title       string `json:"appmsg_title"`
		Description string `json:"appmsg_description"`
		URL         string `json:"appmsg_url"`
		AppName     string `json:"appmsg_app_name"`
	}
	if err := json.Unmarshal(item.PayloadJSON, &payload); err != nil {
		t.Fatalf("payload_json should be valid json: %v", err)
	}
	if payload.Title != "Article" || payload.Description != "Summary" ||
		payload.URL != "https://example.test/article" || payload.AppName != "Docs" {
		t.Fatalf("unexpected link payload: %+v", payload)
	}

	acked, err := service.AckOutbox(t.Context(), ModuleAckRequest{
		APIKey: testAPIKey,
		Device: "phone-a",
		Items: []ModuleAckItem{{
			ID:           item.ID,
			Status:       "sent",
			ChatRecordID: 67892,
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(acked) != 1 {
		t.Fatalf("expected one acked item, got %+v", acked)
	}
	events := service.Hub().Recent(1)
	if len(events) != 1 ||
		events[0].MessageType != MessageTypeAppMsg ||
		events[0].MessageKind != MessageKindAppMsg ||
		events[0].AppMsgType != 5 ||
		events[0].AppMsgSubtype != "link" ||
		events[0].AppMsgTitle != "Article" ||
		events[0].AppMsgURL != "https://example.test/article" {
		t.Fatalf("unexpected outbound link event: %+v", events)
	}
	if containsString(events[0].Unsupported, "appmsg_xml_missing") {
		t.Fatalf("outbound link ack should not require raw xml: %+v", events[0])
	}
}

func TestAdminSendLinkActionRequiresURL(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"link",
		"appmsg_title":"Article"
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("missing link url should be rejected, got status %d body=%s", rec.Code, rec.Body.String())
	}
}

func TestAdminSendMiniProgramActionQueuesSourceForward(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"mini_program",
		"source_chat_record_id":8204
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 {
		t.Fatalf("expected one outbox item, got %+v", items)
	}
	item := items[0]
	var payload struct {
		SourceChatRecordID int64 `json:"source_chat_record_id"`
	}
	if err := json.Unmarshal(item.PayloadJSON, &payload); err != nil {
		t.Fatalf("payload_json should be valid json: %v", err)
	}
	if item.Kind != OutboxKindMiniProgram || item.Text != "[小程序]" || payload.SourceChatRecordID != 8204 {
		t.Fatalf("unexpected mini program action: item=%+v payload=%+v", item, payload)
	}
}

func TestAdminSendChatHistoryActionQueuesAction(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"chat_history",
		"text":"聊天记录",
		"record_title":"聊天记录",
		"record_description":"共2条：图片 / 图片",
		"recorditem_xml":"<recordinfo><datalist count=\"2\"><dataitem datatype=\"2\"/><dataitem datatype=\"2\"/></datalist></recordinfo>"
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 {
		t.Fatalf("expected one outbox item, got %+v", items)
	}
	item := items[0]
	if item.Kind != OutboxKindChatHistory || item.Text != "聊天记录" || item.WxID != "wxid_friend" {
		t.Fatalf("unexpected chat history action: %+v", item)
	}
	var payload map[string]string
	if err := json.Unmarshal(item.PayloadJSON, &payload); err != nil {
		t.Fatalf("payload_json should be valid json: %v", err)
	}
	if payload["record_title"] != "聊天记录" ||
		payload["record_description"] != "共2条：图片 / 图片" ||
		!strings.HasPrefix(payload["recorditem_xml"], "<recordinfo>") {
		t.Fatalf("payload_json should include chat history metadata, got %s", string(item.PayloadJSON))
	}

	acked, err := service.AckOutbox(t.Context(), ModuleAckRequest{
		APIKey: testAPIKey,
		Device: "phone-a",
		Items: []ModuleAckItem{{
			ID:           item.ID,
			Status:       "sent",
			ChatRecordID: 67891,
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(acked) != 1 {
		t.Fatalf("expected one acked item, got %+v", acked)
	}
	events := service.Hub().Recent(1)
	if len(events) != 1 ||
		events[0].MessageType != MessageTypeAppMsg ||
		events[0].MessageKind != MessageKindChatHistory ||
		events[0].AppMsgType != 19 ||
		events[0].AppMsgSubtype != "chat_history" {
		t.Fatalf("unexpected outbound chat history event: %+v", events)
	}
	if containsString(events[0].Unsupported, "appmsg_xml_missing") {
		t.Fatalf("outbound chat history ack should not require raw xml: %+v", events[0])
	}
}

func TestAdminSendChatHistoryActionRequiresRecordItemXML(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"chat_history",
		"text":"聊天记录"
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("missing recorditem_xml should be rejected, got status %d body=%s", rec.Code, rec.Body.String())
	}
}

func TestAdminSendChatHistoryActionQueuesSourceChatRecordIDs(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"chat_history",
		"record_title":"自动聊天记录",
		"source_chat_record_ids":[1001,1002]
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 {
		t.Fatalf("expected one outbox item, got %+v", items)
	}
	item := items[0]
	var payload struct {
		RecordTitle         string  `json:"record_title"`
		RecordItemXML       string  `json:"recorditem_xml"`
		SourceChatRecordIDs []int64 `json:"source_chat_record_ids"`
	}
	if err := json.Unmarshal(item.PayloadJSON, &payload); err != nil {
		t.Fatalf("payload_json should be valid json: %v", err)
	}
	if item.Kind != OutboxKindChatHistory || item.Text != "自动聊天记录" || payload.RecordTitle != "自动聊天记录" {
		t.Fatalf("unexpected chat history action: item=%+v payload=%+v", item, payload)
	}
	if payload.RecordItemXML != "" || len(payload.SourceChatRecordIDs) != 2 ||
		payload.SourceChatRecordIDs[0] != 1001 || payload.SourceChatRecordIDs[1] != 1002 {
		t.Fatalf("payload_json should carry source ids only, got %+v", payload)
	}
}

func TestAdminSendChatHistoryActionQueuesOriginalForwardSource(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"chat_history",
		"forward_original":true,
		"source_chat_record_id":8207
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 {
		t.Fatalf("expected one outbox item, got %+v", items)
	}
	item := items[0]
	var payload struct {
		ForwardOriginal    bool    `json:"forward_original"`
		RecordTitle        string  `json:"record_title"`
		SourceChatRecordID int64   `json:"source_chat_record_id"`
		SourceIDs          []int64 `json:"source_chat_record_ids"`
	}
	if err := json.Unmarshal(item.PayloadJSON, &payload); err != nil {
		t.Fatalf("payload_json should be valid json: %v", err)
	}
	if item.Kind != OutboxKindChatHistory ||
		item.Text != "聊天记录" ||
		!payload.ForwardOriginal ||
		payload.RecordTitle != "" ||
		payload.SourceChatRecordID != 8207 ||
		len(payload.SourceIDs) != 1 ||
		payload.SourceIDs[0] != 8207 {
		t.Fatalf("unexpected original forward payload: item=%+v payload=%+v", item, payload)
	}
}

func TestAdminSendRevokeActionQueuesTargetAndPublishesSystemAck(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{
		"device":"phone-a",
		"owner_wxid":"wxid_self",
		"wx_ids":["wxid_friend"],
		"kind":"revoke",
		"target_chat_record_id":8208,
		"target_msg_svr_id":9100001
	}`)
	req := httptest.NewRequest(http.MethodPost, "/api/send/action", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 {
		t.Fatalf("expected one outbox item, got %+v", items)
	}
	item := items[0]
	var payload struct {
		TargetChatRecordID int64 `json:"target_chat_record_id"`
		TargetMsgSvrID     int64 `json:"target_msg_svr_id"`
	}
	if err := json.Unmarshal(item.PayloadJSON, &payload); err != nil {
		t.Fatalf("payload_json should be valid json: %v", err)
	}
	if item.Kind != OutboxKindRevoke || item.Text != "[撤回]" || payload.TargetChatRecordID != 8208 || payload.TargetMsgSvrID != 9100001 {
		t.Fatalf("unexpected revoke action: item=%+v payload=%+v", item, payload)
	}

	acked, err := service.AckOutbox(t.Context(), ModuleAckRequest{
		APIKey: testAPIKey,
		Device: "phone-a",
		Items: []ModuleAckItem{{
			ID:           item.ID,
			Status:       "sent",
			ChatRecordID: 8208,
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(acked) != 1 {
		t.Fatalf("expected one acked item, got %+v", acked)
	}
	events := service.Hub().Recent(1)
	if len(events) != 1 {
		t.Fatalf("expected one outbound revoke event, got %+v", events)
	}
	event := events[0]
	if event.MessageType != 10000 || event.MessageKind != MessageKindSystem || event.AppMsgSubtype != SystemSubtypeRevoke {
		t.Fatalf("unexpected outbound revoke event: %+v", event)
	}
	if event.ChatRecordID == 8208 || event.AppMsgFileName != "8208" || event.AppMsgAppName != "9100001" || event.AppMsgURL != "wxid_friend" {
		t.Fatalf("revoke ack should preserve target identifiers without reusing them as event id: %+v", event)
	}
}

func TestModuleMediaFileRequiresAPIKeyAndDeviceScopedPath(t *testing.T) {
	mediaDir := t.TempDir()
	service := newTestService("", WithMediaDir(mediaDir))
	if _, err := service.SendAction(t.Context(), SendActionRequest{
		Device:      "phone-a",
		OwnerWxID:   "wxid_self",
		WxIDs:       []string{"wxid_friend"},
		Kind:        OutboxKindImage,
		MediaMime:   "image/png",
		MediaName:   "photo.png",
		MediaBase64: "iVBORw0KGgo=",
	}); err != nil {
		t.Fatal(err)
	}
	items := pollOutbox(t, service, "phone-a", 10)
	if len(items) != 1 || items[0].MediaURL == "" {
		t.Fatalf("expected image media url, got %+v", items)
	}
	modulePath := strings.Replace(items[0].MediaURL, "/api/media/", "/module/media/", 1)
	server := NewHTTPServer(service, "admin").Handler()

	req := httptest.NewRequest(http.MethodGet, modulePath+"?api_key="+testAPIKey, nil)
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK || rec.Body.Len() == 0 {
		t.Fatalf("module media download failed status=%d body=%q", rec.Code, rec.Body.String())
	}

	publicReq := httptest.NewRequest(http.MethodGet, items[0].MediaURL, nil)
	publicReq.Header.Set("X-Bridge-API-Key", testAPIKey)
	publicRec := httptest.NewRecorder()
	server.ServeHTTP(publicRec, publicReq)
	if publicRec.Code != http.StatusOK || publicRec.Body.Len() == 0 {
		t.Fatalf("public media download failed status=%d body=%q", publicRec.Code, publicRec.Body.String())
	}

	publicForbiddenReq := httptest.NewRequest(http.MethodGet, items[0].MediaURL, nil)
	publicForbiddenReq.Header.Set("X-Bridge-API-Key", "wechat-phone-b-key")
	publicForbiddenRec := httptest.NewRecorder()
	server.ServeHTTP(publicForbiddenRec, publicForbiddenReq)
	if publicForbiddenRec.Code != http.StatusForbidden {
		t.Fatalf("public media from another device should be forbidden, got status=%d body=%s", publicForbiddenRec.Code, publicForbiddenRec.Body.String())
	}

	forbiddenReq := httptest.NewRequest(http.MethodGet, "/module/media/other-device/20260101/file.png?api_key="+testAPIKey, nil)
	forbiddenRec := httptest.NewRecorder()
	server.ServeHTTP(forbiddenRec, forbiddenReq)
	if forbiddenRec.Code != http.StatusForbidden {
		t.Fatalf("expected forbidden for other device media, got status=%d body=%s", forbiddenRec.Code, forbiddenRec.Body.String())
	}
}

func TestRegisterModuleKeepsIdentityStableAcrossWeChatSwitch(t *testing.T) {
	service := newTestService("http://127.0.0.1:1")

	first, err := service.RegisterModule(t.Context(), ModuleRegistrationRequest{
		APIKey:   "wechat-a-key",
		Device:   "phone-a",
		WxID:     "wxid_wechat_a1",
		Nickname: "WeChat A1",
	})
	if err != nil {
		t.Fatal(err)
	}
	moved, err := service.RegisterModule(t.Context(), ModuleRegistrationRequest{
		APIKey:   "wechat-a-key",
		Device:   "phone-a",
		WxID:     "wxid_wechat_a2",
		Nickname: "WeChat A2",
	})
	if err != nil {
		t.Fatal(err)
	}
	if first.Device.Name != "phone-a" || first.Device.WxID != "wxid_wechat_a1" {
		t.Fatalf("unexpected first registration: %+v", first)
	}
	if moved.Device.Name != "phone-a" || moved.Device.WxID != "wxid_wechat_a2" {
		t.Fatalf("unexpected moved registration: %+v", moved)
	}
	if device, ok := service.Device("phone-a"); !ok || device.WxID != "wxid_wechat_a2" {
		t.Fatalf("device wxid should follow the latest registration: ok=%v device=%+v", ok, device)
	}
	other, err := service.RegisterModule(t.Context(), ModuleRegistrationRequest{
		APIKey:   "wechat-b-key",
		Device:   "phone-a",
		WxID:     "wxid_wechat_b",
		Nickname: "WeChat B",
	})
	if err != nil {
		t.Fatal(err)
	}
	if other.Device.Name != "device-wechat-b-key" || other.Device.WxID != "wxid_wechat_b" {
		t.Fatalf("unexpected separate API key registration: %+v", other)
	}
}

func TestRegisterModuleKeepsAPIKeyDeviceWhenWxIDWasSeenOnAnotherDevice(t *testing.T) {
	persistence := &fakePersistence{
		deviceByWxID: map[string]config.Device{
			"wxid_self": {
				Name:     "phone-a",
				WxID:     "wxid_self",
				Nickname: "WeChat Phone",
			},
		},
	}
	service := newTestService("http://127.0.0.1:1", WithPersistence(persistence))

	result, err := service.RegisterModule(t.Context(), ModuleRegistrationRequest{
		APIKey:   "wechat-b-key",
		WxID:     "wxid_self",
		Nickname: "Same WeChat",
	})
	if err != nil {
		t.Fatal(err)
	}
	if result.Device.Name != "device-wechat-b-key" {
		t.Fatalf("api key should keep its own device, got %+v", result.Device)
	}
	keys := service.APIKeys()
	var bound config.APIKey
	for _, key := range keys {
		if key.Code == "wechat-b-key" {
			bound = key
			break
		}
	}
	if bound.Device != "device-wechat-b-key" {
		t.Fatalf("api key should not be rebound by wxid lookup, got %+v", bound)
	}
}

func TestModuleOutboxIgnoresStaleOwnerWxIDAfterSwitch(t *testing.T) {
	outbox := &fakeOutbox{}
	service := newTestService("http://127.0.0.1:1", WithOutbox(outbox))
	if _, err := service.SendText(t.Context(), SendTextRequest{
		Device:    "phone-a",
		OwnerWxID: "wxid_self",
		WxIDs:     []string{"wxid_friend"},
		Text:      "old owner queued",
	}); err != nil {
		t.Fatal(err)
	}
	if _, err := service.RegisterModule(t.Context(), ModuleRegistrationRequest{
		APIKey:   "wechat-a-key",
		Device:   "phone-a",
		WxID:     "wxid_self_new",
		Nickname: "WeChat New",
	}); err != nil {
		t.Fatal(err)
	}
	if _, err := service.PollOutbox(t.Context(), ModulePollRequest{
		APIKey: testAPIKey,
		Device: "phone-a",
		WxID:   "wxid_self",
		Limit:  1,
	}); err == nil || !strings.Contains(err.Error(), "not current device wxid") {
		t.Fatalf("expected stale owner poll rejection, got %v", err)
	}
	items, err := service.PollOutbox(t.Context(), ModulePollRequest{
		APIKey: testAPIKey,
		Device: "phone-a",
		WxID:   "wxid_self_new",
		Limit:  1,
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 0 {
		t.Fatalf("old-owner outbox item should not be leased by new login: %+v", items)
	}
	if _, err := service.SendText(t.Context(), SendTextRequest{
		Device:    "phone-a",
		OwnerWxID: "wxid_self",
		WxIDs:     []string{"wxid_friend"},
		Text:      "stale admin send",
	}); err == nil || !strings.Contains(err.Error(), "not current device wxid") {
		t.Fatalf("expected stale owner send rejection, got %v", err)
	}
}

func TestRegisterModulePersistsStableIdentity(t *testing.T) {
	persistence := &fakePersistence{}
	service := newTestService("http://127.0.0.1:1", WithPersistence(persistence))

	result, err := service.RegisterModule(t.Context(), ModuleRegistrationRequest{
		APIKey:   "wechat-a-key",
		Device:   "phone-a",
		WxID:     "wxid_new_self",
		Nickname: "New WeChat",
	})
	if err != nil {
		t.Fatal(err)
	}
	if result.Device.Name != "phone-a" || result.Device.WxID != "wxid_new_self" {
		t.Fatalf("unexpected registration device: %+v", result.Device)
	}
	if persistence.deviceName != "phone-a" || persistence.deviceWxID != "wxid_new_self" || persistence.deviceNickname != "WeChat Phone" {
		t.Fatalf("device identity was not persisted: name=%q wxid=%q nickname=%q", persistence.deviceName, persistence.deviceWxID, persistence.deviceNickname)
	}
	if len(persistence.moduleActivities) != 1 || persistence.moduleActivities[0].Kind != "register" || persistence.moduleActivities[0].APIKey != "wechat-a-key" {
		t.Fatalf("module register activity was not recorded: %+v", persistence.moduleActivities)
	}
}

func TestIngestRecordsPersistenceChain(t *testing.T) {
	persistence := &fakePersistence{}
	outbox := &fakeOutbox{}
	service := newTestService("", WithPersistence(persistence), WithOutbox(outbox))

	result, err := service.Ingest(t.Context(), MessageEvent{
		APIKey:    testAPIKey,
		ID:        "persist-101",
		Device:    "phone-a",
		From:      "wxid_friend",
		To:        "wxid_self",
		Text:      "ping",
		Direction: DirectionRecv,
	})
	if err != nil {
		t.Fatal(err)
	}
	if result == nil || !result.Published || result.PersistenceError != "" {
		t.Fatalf("unexpected ingest result: %+v", result)
	}
	if strings.Join(persistence.calls, ",") != "inbound" {
		t.Fatalf("unexpected persistence calls: %+v", persistence.calls)
	}
	if len(outbox.items) != 0 {
		t.Fatalf("pure gateway ingest should not enqueue replies: %+v", outbox.items)
	}
}

func TestIngestPublishesMediaEnrichmentForExistingMessage(t *testing.T) {
	persistence := &fakePersistence{
		existingEventKeys: map[string]bool{
			fakeMessageEventKey(MessageEvent{
				Device:       "phone-a",
				OwnerWxID:    "wxid_self",
				Direction:    DirectionRecv,
				ChatRecordID: 5678,
			}): true,
		},
	}
	service := newTestService("", WithPersistence(persistence))

	result, err := service.Ingest(t.Context(), MessageEvent{
		APIKey:       testAPIKey,
		ID:           "5678",
		Device:       "phone-a",
		From:         "wxid_friend",
		To:           "wxid_self",
		Text:         "[图片]",
		Direction:    DirectionRecv,
		ChatRecordID: 5678,
		MessageType:  3,
		MediaKind:    "image",
		MediaMime:    "image/jpeg",
		MediaName:    "late.jpg",
		MediaURL:     "/api/media/phone-a/20260706/late.jpg",
		MediaSize:    3778,
	})
	if err != nil {
		t.Fatal(err)
	}
	if result == nil || !result.Published || result.PersistenceError != "" {
		t.Fatalf("media enrichment should publish once: %+v", result)
	}
	got := service.Hub().Recent(1)
	if len(got) != 1 || got[0].MediaURL == "" || got[0].ChatRecordID != 5678 {
		t.Fatalf("media enrichment should hit live hub: %+v", got)
	}
}

func TestIngestSuppressesMediaDuplicateWhenExistingMessageAlreadyHasMedia(t *testing.T) {
	existing := MessageEvent{
		Device:       "phone-a",
		OwnerWxID:    "wxid_self",
		Direction:    DirectionRecv,
		ChatRecordID: 6789,
	}
	persistence := &fakePersistence{
		existingEventKeys: map[string]bool{fakeMessageEventKey(existing): true},
		existingMediaKeys: map[string]bool{fakeMessageEventKey(existing): true},
	}
	service := newTestService("", WithPersistence(persistence))

	result, err := service.Ingest(t.Context(), MessageEvent{
		APIKey:       testAPIKey,
		ID:           "6789",
		Device:       "phone-a",
		From:         "wxid_friend",
		To:           "wxid_self",
		Text:         "[图片]",
		Direction:    DirectionRecv,
		ChatRecordID: 6789,
		MessageType:  3,
		MediaKind:    "image",
		MediaURL:     "/api/media/phone-a/20260706/already.jpg",
	})
	if err != nil {
		t.Fatal(err)
	}
	if result == nil || result.Published || result.PersistenceError != "" {
		t.Fatalf("existing media duplicate should stay suppressed: %+v", result)
	}
	if got := service.Hub().Recent(1); len(got) != 0 {
		t.Fatalf("existing media duplicate should not hit live hub: %+v", got)
	}
}

func TestIngestSuppressesLiveDuplicateWhenPersistenceAlreadyHasMessage(t *testing.T) {
	persistence := &fakePersistence{
		existingEventKeys: map[string]bool{
			fakeMessageEventKey(MessageEvent{
				Device:       "phone-a",
				OwnerWxID:    "wxid_self",
				Direction:    DirectionSent,
				ChatRecordID: 4321,
			}): true,
		},
	}
	service := newTestService("", WithPersistence(persistence))

	result, err := service.Ingest(t.Context(), MessageEvent{
		APIKey:       testAPIKey,
		ID:           "4321",
		Device:       "phone-a",
		From:         "wxid_self",
		To:           "wxid_friend",
		Text:         "outbound observed after ack",
		Direction:    DirectionSent,
		ChatRecordID: 4321,
	})
	if err != nil {
		t.Fatal(err)
	}
	if result == nil || result.Published || result.PersistenceError != "" {
		t.Fatalf("duplicate ingest should be persisted but not republished: %+v", result)
	}
	if len(persistence.inboundEvents) != 1 {
		t.Fatalf("duplicate event should still update persistence, got %+v", persistence.inboundEvents)
	}
	if got := service.Hub().Recent(1); len(got) != 0 {
		t.Fatalf("duplicate event should not hit live hub: %+v", got)
	}
}

func TestModuleRegisterEndpointUsesAPIKey(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{"api_key":"wechat-a-key","device":"phone-a","wxid":"wxid_module","nickname":"Module WeChat"}`)
	req := httptest.NewRequest(http.MethodPost, "/module/register", bytes.NewReader(body))
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
	var payload struct {
		OK     bool `json:"ok"`
		Result struct {
			Device struct {
				Name     string `json:"name"`
				WxID     string `json:"wxid"`
				Nickname string `json:"nickname"`
			} `json:"device"`
		} `json:"result"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &payload); err != nil {
		t.Fatal(err)
	}
	if !payload.OK || payload.Result.Device.Name != "phone-a" || payload.Result.Device.WxID != "wxid_module" {
		t.Fatalf("unexpected register payload: %s", rec.Body.String())
	}
	if strings.Contains(rec.Body.String(), `"Name"`) || strings.Contains(rec.Body.String(), `"WxID"`) || strings.Contains(rec.Body.String(), `"Timeout"`) {
		t.Fatalf("register response leaked Go device field names: %s", rec.Body.String())
	}
	if device, ok := service.Device("phone-a"); !ok || device.WxID != "wxid_module" {
		t.Fatalf("device wxid was not updated: ok=%v device=%+v", ok, device)
	}
}

func TestModuleRegisterEndpointRejectsBadCode(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	req := httptest.NewRequest(http.MethodPost, "/module/register", bytes.NewReader([]byte(`{"api_key":"bad","device":"phone-a","wxid":"wxid_module"}`)))
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
}

func TestAPIKeyDisableStopsAndEnableRestoresModuleAuth(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	disableReq := httptest.NewRequest(http.MethodPost, "/api/api-keys/wechat-a-key/disable", nil)
	disableReq.Header.Set("X-Bridge-Password", "admin")
	disableRec := httptest.NewRecorder()
	server.ServeHTTP(disableRec, disableReq)
	if disableRec.Code != http.StatusOK || !strings.Contains(disableRec.Body.String(), `"enabled":false`) {
		t.Fatalf("unexpected disable response status=%d body=%s", disableRec.Code, disableRec.Body.String())
	}

	_, err := service.RegisterModule(t.Context(), ModuleRegistrationRequest{
		APIKey: "wechat-a-key",
		WxID:   "wxid_module",
	})
	if err == nil || !strings.Contains(err.Error(), "disabled") {
		t.Fatalf("disabled api key should reject module auth, got %v", err)
	}

	enableReq := httptest.NewRequest(http.MethodPost, "/api/api-keys/wechat-a-key/enable", nil)
	enableReq.Header.Set("X-Bridge-Password", "admin")
	enableRec := httptest.NewRecorder()
	server.ServeHTTP(enableRec, enableReq)
	if enableRec.Code != http.StatusOK || !strings.Contains(enableRec.Body.String(), `"enabled":true`) {
		t.Fatalf("unexpected enable response status=%d body=%s", enableRec.Code, enableRec.Body.String())
	}
	if _, err := service.RegisterModule(t.Context(), ModuleRegistrationRequest{
		APIKey: "wechat-a-key",
		WxID:   "wxid_module",
	}); err != nil {
		t.Fatalf("enabled api key should register again: %v", err)
	}
}

func TestAdminCanGenerateAPIKeyAndRenameDevice(t *testing.T) {
	service := newTestService("http://127.0.0.1:1")
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{"api_key":"wg_web_key","device":"phone-web","nickname":"Web WeChat"}`)
	req := httptest.NewRequest(http.MethodPost, "/api/api-keys", bytes.NewReader(body))
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("unexpected api key status %d body=%s", rec.Code, rec.Body.String())
	}
	if !strings.Contains(rec.Body.String(), `"api_key"`) || !strings.Contains(rec.Body.String(), `"phone-web"`) {
		t.Fatalf("unexpected api key response: %s", rec.Body.String())
	}

	deviceBody := []byte(`{"name":"phone-a","nickname":"Web Phone A"}`)
	deviceReq := httptest.NewRequest(http.MethodPost, "/api/devices", bytes.NewReader(deviceBody))
	deviceReq.Header.Set("X-Bridge-Password", "admin")
	deviceRec := httptest.NewRecorder()
	server.ServeHTTP(deviceRec, deviceReq)
	if deviceRec.Code != http.StatusOK {
		t.Fatalf("unexpected device status %d body=%s", deviceRec.Code, deviceRec.Body.String())
	}
	if !strings.Contains(deviceRec.Body.String(), `"device_nickname":"Web Phone A"`) {
		t.Fatalf("unexpected device response: %s", deviceRec.Body.String())
	}

	deleteReq := httptest.NewRequest(http.MethodDelete, "/api/api-keys/wg_web_key", nil)
	deleteReq.Header.Set("X-Bridge-Password", "admin")
	deleteRec := httptest.NewRecorder()
	server.ServeHTTP(deleteRec, deleteReq)
	if deleteRec.Code != http.StatusOK {
		t.Fatalf("unexpected delete api key status %d body=%s", deleteRec.Code, deleteRec.Body.String())
	}
	for _, code := range service.APIKeys() {
		if code.Code == "wg_web_key" {
			t.Fatalf("api key was not removed")
		}
	}
}

func TestLegacyBusinessAdminEndpointsAreGone(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()
	for _, path := range []string{"/api/commands", "/api/replies"} {
		req := httptest.NewRequest(http.MethodGet, path, nil)
		req.Header.Set("X-Bridge-Password", "admin")
		rec := httptest.NewRecorder()
		server.ServeHTTP(rec, req)
		if rec.Code != http.StatusNotFound {
			t.Fatalf("%s should be removed, got status=%d body=%s", path, rec.Code, rec.Body.String())
		}
	}
}

func TestModuleContactsSnapshotEndpointPersistsContacts(t *testing.T) {
	persistence := &fakePersistence{}
	service := newTestService("http://127.0.0.1:1", WithPersistence(persistence))
	server := NewHTTPServer(service, "admin").Handler()

	body := []byte(`{"api_key":"wechat-a-key","device":"phone-a","wxid":"wxid_self","complete":true,"contacts":[{"wxid":"wxid_friend","nickname":"Friend"},{"wxid":"","nickname":"ignored"}]}`)
	req := httptest.NewRequest(http.MethodPost, "/module/contacts/snapshot", bytes.NewReader(body))
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK || !strings.Contains(rec.Body.String(), `"count":1`) {
		t.Fatalf("unexpected contacts response status=%d body=%s", rec.Code, rec.Body.String())
	}
	if len(persistence.contactSnapshots) != 1 || len(persistence.contactSnapshots[0].Contacts) != 1 || persistence.contactSnapshots[0].Contacts[0].WxID != "wxid_friend" {
		t.Fatalf("unexpected persisted contact snapshot: %+v", persistence.contactSnapshots)
	}
}

func TestModuleStatusEndpointFallsBackToRuntimeSnapshot(t *testing.T) {
	service := newTestService("")
	server := NewHTTPServer(service, "admin").Handler()

	req := httptest.NewRequest(http.MethodGet, "/api/modules/status", nil)
	req.Header.Set("X-Bridge-Password", "admin")
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK || !strings.Contains(rec.Body.String(), `"device":"phone-a"`) || !strings.Contains(rec.Body.String(), `"runtime_status":"ready"`) {
		t.Fatalf("unexpected status response %d body=%s", rec.Code, rec.Body.String())
	}
}

func TestAdminReadEndpointsRequireAdminPassword(t *testing.T) {
	service := newTestService("http://127.0.0.1:1")
	server := NewHTTPServer(service, "admin").Handler()

	req := httptest.NewRequest(http.MethodGet, "/api/modules/status", nil)
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
}

func TestAdminReadEndpointsRejectBridgeTokenHeader(t *testing.T) {
	service := newTestService("http://127.0.0.1:1")
	server := NewHTTPServer(service, "admin").Handler()

	req := httptest.NewRequest(http.MethodGet, "/api/modules/status", nil)
	rec := httptest.NewRecorder()
	server.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("unexpected status %d body=%s", rec.Code, rec.Body.String())
	}
}

func TestModuleOutboxPollAndAckEndpoints(t *testing.T) {
	outbox := &fakeOutbox{}
	persistence := &fakePersistence{}
	service := newTestService("http://127.0.0.1:1", WithOutbox(outbox), WithPersistence(persistence))
	server := NewHTTPServer(service, "admin").Handler()

	if _, err := service.SendText(t.Context(), SendTextRequest{
		Device: "phone-a",
		WxIDs:  []string{"wxid_friend"},
		Text:   "queued reply",
	}); err != nil {
		t.Fatal(err)
	}

	pollBody := []byte(`{"api_key":"wechat-a-key","device":"phone-a","limit":10}`)
	pollReq := httptest.NewRequest(http.MethodPost, "/module/outbox/poll", bytes.NewReader(pollBody))
	pollRec := httptest.NewRecorder()
	server.ServeHTTP(pollRec, pollReq)
	if pollRec.Code != http.StatusOK {
		t.Fatalf("unexpected poll status %d body=%s", pollRec.Code, pollRec.Body.String())
	}
	var pollPayload struct {
		OK    bool               `json:"ok"`
		Items []ModuleOutboxItem `json:"items"`
	}
	if err := json.Unmarshal(pollRec.Body.Bytes(), &pollPayload); err != nil {
		t.Fatal(err)
	}
	if !pollPayload.OK || len(pollPayload.Items) != 1 || pollPayload.Items[0].Text != "queued reply" || pollPayload.Items[0].Status != "leased" {
		t.Fatalf("unexpected poll payload: %s", pollRec.Body.String())
	}

	ackBody := []byte(`{"api_key":"wechat-a-key","device":"phone-a","items":[{"id":1,"status":"sent","chat_record_id":9001}]}`)
	ackReq := httptest.NewRequest(http.MethodPost, "/module/outbox/ack", bytes.NewReader(ackBody))
	ackRec := httptest.NewRecorder()
	server.ServeHTTP(ackRec, ackReq)
	if ackRec.Code != http.StatusOK {
		t.Fatalf("unexpected ack status %d body=%s", ackRec.Code, ackRec.Body.String())
	}
	if len(persistence.outboundEvents) != 1 ||
		persistence.outboundEvents[0].ChatRecordID != 9001 ||
		persistence.outboundEvents[0].RawProvider != RawProviderModuleAck ||
		persistence.outboundEvents[0].OwnerWxID != "wxid_self" {
		t.Fatalf("ack did not record outbound event: %+v", persistence.outboundEvents)
	}
	if len(persistence.moduleActivities) != 2 ||
		persistence.moduleActivities[0].Kind != "poll" || persistence.moduleActivities[0].PollItemCount != 1 ||
		persistence.moduleActivities[1].Kind != "ack" || persistence.moduleActivities[1].AckSentCount != 1 {
		t.Fatalf("module poll/ack activity was not recorded: %+v", persistence.moduleActivities)
	}
}

func TestModuleOutboxWebSocketPushAndAck(t *testing.T) {
	outbox := &fakeOutbox{}
	persistence := &fakePersistence{}
	service := newTestService("http://127.0.0.1:1", WithOutbox(outbox), WithPersistence(persistence))
	server := httptest.NewServer(NewHTTPServer(service, "admin").Handler())
	defer server.Close()

	conn := dialTestWebSocket(t, server.URL, "/module/outbox/ws?api_key=wechat-a-key&device=phone-a&wxid=wxid_self")
	defer conn.close()

	ready := readTestWSMessage(t, conn)
	if ready.Type != "ready" || !ready.OK {
		t.Fatalf("unexpected ready message: %+v", ready)
	}
	if _, err := service.SendText(t.Context(), SendTextRequest{
		Device: "phone-a",
		WxIDs:  []string{"wxid_friend"},
		Text:   "queued through ws",
	}); err != nil {
		t.Fatal(err)
	}
	outboxMsg := readTestWSMessage(t, conn)
	if outboxMsg.Type != "outbox" || len(outboxMsg.Items) != 1 || outboxMsg.Items[0].Text != "queued through ws" || outboxMsg.Items[0].Status != "leased" {
		t.Fatalf("unexpected outbox message: %+v", outboxMsg)
	}
	ack := ModuleAckRequest{
		Items: []ModuleAckItem{{
			ID:           outboxMsg.Items[0].ID,
			Status:       "sent",
			ChatRecordID: 9101,
		}},
	}
	if !conn.writeJSON(outboxWSMessage{Type: "ack", Ack: &ack}) {
		t.Fatal("failed to write websocket ack")
	}
	ackMsg := readTestWSMessageOfType(t, conn, "ack")
	if ackMsg.Type != "ack" || !ackMsg.OK || len(ackMsg.Items) != 1 || ackMsg.Items[0].Status != "sent" {
		t.Fatalf("unexpected ack message: %+v", ackMsg)
	}
	if len(persistence.outboundEvents) != 1 ||
		persistence.outboundEvents[0].ChatRecordID != 9101 ||
		persistence.outboundEvents[0].RawProvider != RawProviderModuleAck ||
		persistence.outboundEvents[0].OwnerWxID != "wxid_self" {
		t.Fatalf("ack did not record outbound event: %+v", persistence.outboundEvents)
	}
	hasAckActivity := false
	for _, activity := range persistence.moduleActivities {
		if activity.Kind == "ack" && activity.AckSentCount == 1 {
			hasAckActivity = true
		}
	}
	if !hasAckActivity {
		t.Fatalf("module websocket activity was not recorded: %+v", persistence.moduleActivities)
	}
}

func TestModuleOutboxPollBatchesPendingItems(t *testing.T) {
	service := newTestService("")
	for _, text := range []string{"first queued reply", "second queued reply"} {
		if _, err := service.SendText(t.Context(), SendTextRequest{
			Device: "phone-a",
			WxIDs:  []string{"wxid_friend"},
			Text:   text,
		}); err != nil {
			t.Fatal(err)
		}
	}

	items, err := service.PollOutbox(t.Context(), ModulePollRequest{
		APIKey: testAPIKey,
		Device: "phone-a",
		Limit:  10,
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 2 || items[0].Text != "first queued reply" || items[1].Text != "second queued reply" {
		t.Fatalf("unexpected batched poll items: %+v", items)
	}
}

func TestModuleRegisterUsesWebBoundDevice(t *testing.T) {
	service := newTestService("http://127.0.0.1:1")
	result, err := service.RegisterModule(t.Context(), ModuleRegistrationRequest{
		APIKey: "wechat-phone-b-key",
		Device: "phone-a",
		WxID:   "wxid_self_wrong_device",
	})
	if err != nil {
		t.Fatal(err)
	}
	if result.Device.Name != "phone-b" || result.Device.WxID != "wxid_self_wrong_device" {
		t.Fatalf("web-bound device should win over module payload: %+v", result)
	}
}

func pollOutbox(t *testing.T, service *Service, device string, limit int) []ModuleOutboxItem {
	t.Helper()
	items, err := service.PollOutbox(t.Context(), ModulePollRequest{
		APIKey: testAPIKey,
		Device: device,
		Limit:  limit,
	})
	if err != nil {
		t.Fatal(err)
	}
	return items
}

func newTestService(legacyEndpoint string, opts ...Option) *Service {
	return NewService(Config{
		DefaultDevice: "phone-a",
		Devices: map[string]config.Device{
			"phone-a": {
				Name:     "phone-a",
				WxID:     "wxid_self",
				Nickname: "WeChat Phone",
				Timeout:  time.Second,
			},
			"phone-b": {
				Name:     "phone-b",
				WxID:     "wxid_unbound_phone_b",
				Nickname: "WeChat Phone B",
				Timeout:  time.Second,
			},
		},
		APIKeys: map[string]config.APIKey{
			"wechat-a-key": {
				Code:   "wechat-a-key",
				Device: "phone-a",
			},
			"wechat-b-key": {
				Code: "wechat-b-key",
			},
			"wechat-phone-b-key": {
				Code:   "wechat-phone-b-key",
				Device: "phone-b",
			},
		},
	}, opts...)
}

type fakePersistence struct {
	deviceName        string
	deviceWxID        string
	deviceNickname    string
	deviceByWxID      map[string]config.Device
	existingEventKeys map[string]bool
	existingMediaKeys map[string]bool
	inboundEvents     []MessageEvent
	outboundEvents    []MessageEvent
	moduleActivities  []ModuleActivity
	contactSnapshots  []ModuleContactSnapshotRequest
	calls             []string
}

func (p *fakePersistence) UpdateDeviceIdentity(_ context.Context, deviceName string, wxid string, nickname string) error {
	p.deviceName = deviceName
	p.deviceWxID = wxid
	p.deviceNickname = nickname
	return nil
}

func (p *fakePersistence) LookupDeviceByWxID(_ context.Context, wxid string) (config.Device, bool, error) {
	if p.deviceByWxID == nil {
		return config.Device{}, false, nil
	}
	device, ok := p.deviceByWxID[wxid]
	return device, ok, nil
}

func (p *fakePersistence) UpsertAPIKey(_ context.Context, key config.APIKey) error {
	p.calls = append(p.calls, "upsert-key:"+key.Code)
	return nil
}

func (p *fakePersistence) DeleteAPIKey(_ context.Context, code string) error {
	p.calls = append(p.calls, "delete-key:"+code)
	return nil
}

func (p *fakePersistence) SetAPIKeyEnabled(_ context.Context, code string, enabled bool) error {
	p.calls = append(p.calls, fmt.Sprintf("key-enabled:%s:%t", code, enabled))
	return nil
}

func (p *fakePersistence) RecordInboundEvent(_ context.Context, event MessageEvent) error {
	p.calls = append(p.calls, "inbound")
	p.inboundEvents = append(p.inboundEvents, event)
	return nil
}

func (p *fakePersistence) RecordOutboundEvent(_ context.Context, event MessageEvent) error {
	p.calls = append(p.calls, "outbound")
	p.outboundEvents = append(p.outboundEvents, event)
	return nil
}

func (p *fakePersistence) HasMessageEvent(_ context.Context, event MessageEvent) (bool, error) {
	if p.existingEventKeys == nil {
		return false, nil
	}
	return p.existingEventKeys[fakeMessageEventKey(event)], nil
}

func (p *fakePersistence) HasMessageEventMedia(_ context.Context, event MessageEvent) (bool, error) {
	if p.existingMediaKeys == nil {
		return false, nil
	}
	return p.existingMediaKeys[fakeMessageEventKey(event)], nil
}

func fakeMessageEventKey(event MessageEvent) string {
	return strings.Join([]string{
		strings.TrimSpace(event.Device),
		strings.TrimSpace(event.OwnerWxID),
		string(event.Direction),
		strconv.FormatInt(event.ChatRecordID, 10),
	}, "\x00")
}

func (p *fakePersistence) RecordModuleActivity(_ context.Context, activity ModuleActivity) error {
	p.calls = append(p.calls, "module:"+activity.Kind)
	p.moduleActivities = append(p.moduleActivities, activity)
	return nil
}

func (p *fakePersistence) RecordModuleContacts(_ context.Context, snapshot ModuleContactSnapshotRequest) error {
	p.calls = append(p.calls, "contacts")
	p.contactSnapshots = append(p.contactSnapshots, snapshot)
	return nil
}

type fakeAdminReader struct {
	keys     []APIKeyView
	events   []StoredEventView
	messages []StoredEventView
	modules  []ModuleStatusView
	contacts []ModuleContactView
	calls    []string
}

func (r *fakeAdminReader) ListAPIKeys(_ context.Context, limit int) ([]APIKeyView, error) {
	r.calls = append(r.calls, "keys:"+strconv.Itoa(limit))
	return r.keys, nil
}

func (r *fakeAdminReader) ListStoredEvents(_ context.Context, limit int) ([]StoredEventView, error) {
	r.calls = append(r.calls, "events:"+strconv.Itoa(limit))
	return r.events, nil
}

func (r *fakeAdminReader) ListMessages(_ context.Context, filter MessageFilter) ([]StoredEventView, error) {
	r.calls = append(r.calls, "messages:"+filter.Device+":"+filter.WxID+":"+strconv.Itoa(filter.Limit))
	out := make([]StoredEventView, 0, len(r.messages))
	for _, message := range r.messages {
		if filter.Device != "" && message.Device != filter.Device {
			continue
		}
		if filter.OwnerWxID != "" && message.OwnerWxID != filter.OwnerWxID {
			continue
		}
		if filter.WxID != "" && message.FromWxID != filter.WxID && message.ToWxID != filter.WxID && message.RoomID != filter.WxID && message.ChatID != filter.WxID {
			continue
		}
		if filter.ChatID != "" && message.ChatID != filter.ChatID && message.RoomID != filter.ChatID && message.FromWxID != filter.ChatID && message.ToWxID != filter.ChatID {
			continue
		}
		if filter.ChatKind != "" && message.ChatKind != filter.ChatKind {
			continue
		}
		if filter.AfterIDSet && message.ID <= filter.AfterID {
			continue
		}
		if filter.BeforeID > 0 && message.ID >= filter.BeforeID {
			continue
		}
		out = append(out, message)
	}
	if !filter.AfterIDSet {
		for i, j := 0, len(out)-1; i < j; i, j = i+1, j-1 {
			out[i], out[j] = out[j], out[i]
		}
	}
	if filter.Limit > 0 && len(out) > filter.Limit {
		out = out[:filter.Limit]
	}
	return out, nil
}

func (r *fakeAdminReader) ListModuleStatuses(_ context.Context) ([]ModuleStatusView, error) {
	r.calls = append(r.calls, "modules")
	return r.modules, nil
}

func (r *fakeAdminReader) ListModuleContacts(_ context.Context, filter ModuleContactFilter) ([]ModuleContactView, error) {
	r.calls = append(r.calls, "contacts:"+filter.Device+":"+filter.Query+":"+strconv.Itoa(filter.Limit))
	return r.contacts, nil
}

type fakeOutbox struct {
	nextID int64
	items  []ModuleOutboxItem
}

func (o *fakeOutbox) EnqueueReply(_ context.Context, action ReplyAction) (ModuleOutboxItem, error) {
	o.nextID++
	item := ModuleOutboxItem{
		ID:          o.nextID,
		Device:      action.Device,
		OwnerWxID:   action.OwnerWxID,
		WxID:        action.WxID,
		Kind:        firstNonEmpty(action.Kind, OutboxKindText),
		Text:        action.Text,
		PayloadJSON: append([]byte(nil), action.PayloadJSON...),
		MediaKind:   action.MediaKind,
		MediaMime:   action.MediaMime,
		MediaName:   action.MediaName,
		MediaURL:    action.MediaURL,
		MediaSize:   action.MediaSize,
		Status:      "pending",
	}
	o.items = append(o.items, item)
	return item, nil
}

func (o *fakeOutbox) PollReplyActions(_ context.Context, req ModulePollRequest) ([]ModuleOutboxItem, error) {
	limit := req.Limit
	if limit <= 0 || limit > len(o.items) {
		limit = len(o.items)
	}
	out := []ModuleOutboxItem{}
	for i := range o.items {
		if len(out) >= limit {
			break
		}
		if o.items[i].Device != req.Device {
			continue
		}
		if req.WxID != "" && o.items[i].OwnerWxID != "" && o.items[i].OwnerWxID != req.WxID {
			continue
		}
		if o.items[i].Status != "pending" {
			continue
		}
		o.items[i].Status = "leased"
		o.items[i].AttemptCount++
		out = append(out, o.items[i])
	}
	return out, nil
}

func (o *fakeOutbox) AckReplyActions(_ context.Context, req ModuleAckRequest) ([]ModuleOutboxItem, error) {
	byID := map[int64]ModuleAckItem{}
	for _, ack := range req.Items {
		byID[ack.ID] = ack
	}
	out := []ModuleOutboxItem{}
	for i := range o.items {
		ack, ok := byID[o.items[i].ID]
		if !ok || o.items[i].Device != req.Device {
			continue
		}
		if req.WxID != "" && o.items[i].OwnerWxID != "" && o.items[i].OwnerWxID != req.WxID {
			continue
		}
		o.items[i].Status = ack.Status
		o.items[i].LastError = ack.Error
		o.items[i].ChatRecordID = ack.ChatRecordID
		out = append(out, o.items[i])
	}
	return out, nil
}

func (o *fakeOutbox) GetReplyAction(_ context.Context, id int64) (ModuleOutboxItem, error) {
	for _, item := range o.items {
		if item.ID == id {
			return item, nil
		}
	}
	return ModuleOutboxItem{}, ErrOutboxItemNotFound
}

func dialTestWebSocket(t *testing.T, serverURL string, path string) *wsConn {
	t.Helper()
	parsed, err := url.Parse(serverURL)
	if err != nil {
		t.Fatal(err)
	}
	conn, err := net.Dial("tcp", parsed.Host)
	if err != nil {
		t.Fatal(err)
	}
	keyBytes := make([]byte, 16)
	if _, err := rand.Read(keyBytes); err != nil {
		t.Fatal(err)
	}
	key := base64.StdEncoding.EncodeToString(keyBytes)
	request := fmt.Sprintf("GET %s HTTP/1.1\r\nHost: %s\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: %s\r\n\r\n", path, parsed.Host, key)
	if _, err := conn.Write([]byte(request)); err != nil {
		_ = conn.Close()
		t.Fatal(err)
	}
	reader := bufio.NewReader(conn)
	status, err := reader.ReadString('\n')
	if err != nil {
		_ = conn.Close()
		t.Fatal(err)
	}
	if !strings.Contains(status, "101") {
		_ = conn.Close()
		t.Fatalf("unexpected websocket status: %s", status)
	}
	accept := ""
	for {
		line, err := reader.ReadString('\n')
		if err != nil {
			_ = conn.Close()
			t.Fatal(err)
		}
		line = strings.TrimRight(line, "\r\n")
		if line == "" {
			break
		}
		name, value, ok := strings.Cut(line, ":")
		if ok && strings.EqualFold(strings.TrimSpace(name), "Sec-WebSocket-Accept") {
			accept = strings.TrimSpace(value)
		}
	}
	if accept != websocketAccept(key) {
		_ = conn.Close()
		t.Fatalf("unexpected websocket accept %q", accept)
	}
	return &wsConn{
		conn: conn,
		rw:   bufio.NewReadWriter(reader, bufio.NewWriter(conn)),
	}
}

func readTestPublicWSMessage(t *testing.T, conn *wsConn) publicWSMessage {
	t.Helper()
	for {
		payload, op, err := conn.readFrame()
		if err != nil {
			t.Fatal(err)
		}
		if op != wsOpText {
			continue
		}
		var msg publicWSMessage
		if err := json.Unmarshal(payload, &msg); err != nil {
			t.Fatalf("invalid public ws json %q: %v", string(payload), err)
		}
		return msg
	}
}

func readTestWSMessage(t *testing.T, conn *wsConn) outboxWSMessage {
	t.Helper()
	if err := conn.conn.SetReadDeadline(time.Now().Add(3 * time.Second)); err != nil {
		t.Fatal(err)
	}
	for {
		payload, op, err := conn.readFrame()
		if err != nil {
			t.Fatal(err)
		}
		switch op {
		case wsOpText:
			var msg outboxWSMessage
			if err := json.Unmarshal(payload, &msg); err != nil {
				t.Fatal(err)
			}
			return msg
		case wsOpPing:
			if !conn.writeControl(wsOpPong, payload) {
				t.Fatal("failed to write pong")
			}
		}
	}
}

func readTestWSMessageOfType(t *testing.T, conn *wsConn, typ string) outboxWSMessage {
	t.Helper()
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		msg := readTestWSMessage(t, conn)
		if msg.Type == typ {
			return msg
		}
	}
	t.Fatalf("websocket message type %q was not received", typ)
	return outboxWSMessage{}
}
