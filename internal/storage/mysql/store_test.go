package mysql

import (
	"strings"
	"testing"

	"wechat-observatory/internal/bridge"
)

func TestStoreImplementsBridgePersistence(t *testing.T) {
	var _ bridge.Persistence = (*Store)(nil)
	var _ bridge.Outbox = (*Store)(nil)
	var _ bridge.AdminReader = (*Store)(nil)
}

func TestMigrationsCoverCoreTables(t *testing.T) {
	joined := strings.Join(Migrations(), "\n")
	for _, table := range []string{
		"bridge_api_keys",
		"bridge_devices",
		"bridge_message_events",
		"bridge_module_outbox",
		"bridge_module_runtime",
		"bridge_module_contacts",
	} {
		if !strings.Contains(joined, table) {
			t.Fatalf("migration does not include %s", table)
		}
	}
	if !strings.Contains(joined, "enabled BOOLEAN NOT NULL DEFAULT TRUE") {
		t.Fatalf("bridge_api_keys migration should include enabled state: %s", joined)
	}
	if !strings.Contains(joined, "location_latitude DOUBLE NULL") || !strings.Contains(joined, "location_poi_category_tips TEXT NULL") {
		t.Fatalf("bridge_message_events migration should include location columns: %s", joined)
	}
}

func TestListMessagesQueryExcludesModuleAckEvents(t *testing.T) {
	query, args := listMessagesQuery(bridge.MessageFilter{
		Device: "phone-a",
		WxID:   "wxid_friend",
		Limit:  25,
	})
	if !strings.Contains(query, "ev.raw_provider IS NULL OR ev.raw_provider <> ?") {
		t.Fatalf("message query does not exclude module ack events: %s", query)
	}
	if !strings.Contains(query, "LEFT JOIN bridge_module_contacts c") || !strings.Contains(query, "c.wxid = CASE") {
		t.Fatalf("message query should include chat contact metadata join: %s", query)
	}
	if len(args) != 7 {
		t.Fatalf("unexpected args: %#v", args)
	}
	if args[0] != bridge.RawProviderModuleAck {
		t.Fatalf("first arg should exclude module ack provider, got %#v", args[0])
	}
	if args[1] != "phone-a" {
		t.Fatalf("device arg mismatch: %#v", args)
	}
	for i := 2; i <= 5; i++ {
		if args[i] != "wxid_friend" {
			t.Fatalf("wxid arg %d mismatch: %#v", i, args)
		}
	}
	if args[6] != 25 {
		t.Fatalf("limit arg mismatch: %#v", args)
	}
}

func TestListMessagesQueryFiltersByOwnerWxID(t *testing.T) {
	query, args := listMessagesQuery(bridge.MessageFilter{
		Device:    "phone-a",
		OwnerWxID: "wxid_current",
		Limit:     25,
	})
	if !strings.Contains(query, "owner_wxid = ?") {
		t.Fatalf("message query does not filter by owner_wxid: %s", query)
	}
	if strings.Contains(query, "from_wxid = ? OR to_wxid = ? OR sender_wxid = ?") {
		t.Fatalf("owner filter should not fall back to participant matching: %s", query)
	}
	if len(args) != 4 {
		t.Fatalf("unexpected args: %#v", args)
	}
	if args[0] != bridge.RawProviderModuleAck || args[1] != "phone-a" || args[2] != "wxid_current" || args[3] != 25 {
		t.Fatalf("owner filter args mismatch: %#v", args)
	}
}

func TestListMessagesQuerySupportsAfterIDForwardSync(t *testing.T) {
	query, args := listMessagesQuery(bridge.MessageFilter{
		Device:     "phone-a",
		AfterID:    100,
		AfterIDSet: true,
		Limit:      26,
	})
	if !strings.Contains(query, "ev.id > ?") || !strings.Contains(query, "ORDER BY ev.id ASC") {
		t.Fatalf("after_id query should scan forward: %s", query)
	}
	if len(args) != 4 || args[0] != bridge.RawProviderModuleAck || args[1] != "phone-a" || args[2] != int64(100) || args[3] != 26 {
		t.Fatalf("after_id query args mismatch: %#v", args)
	}
}

func TestListMessagesQuerySupportsAfterIDZeroForwardSync(t *testing.T) {
	query, args := listMessagesQuery(bridge.MessageFilter{
		Device:     "phone-a",
		AfterID:    0,
		AfterIDSet: true,
		Limit:      26,
	})
	if !strings.Contains(query, "ev.id > ?") || !strings.Contains(query, "ORDER BY ev.id ASC") {
		t.Fatalf("after_id=0 query should scan forward: %s", query)
	}
	if len(args) != 4 || args[0] != bridge.RawProviderModuleAck || args[1] != "phone-a" || args[2] != int64(0) || args[3] != 26 {
		t.Fatalf("after_id=0 query args mismatch: %#v", args)
	}
}

func TestListMessagesQuerySupportsBeforeIDHistoryPage(t *testing.T) {
	query, args := listMessagesQuery(bridge.MessageFilter{
		Device:   "phone-a",
		BeforeID: 100,
		Limit:    26,
	})
	if !strings.Contains(query, "ev.id < ?") || !strings.Contains(query, "ORDER BY ev.id DESC") {
		t.Fatalf("before_id query should scan backward: %s", query)
	}
	if len(args) != 4 || args[0] != bridge.RawProviderModuleAck || args[1] != "phone-a" || args[2] != int64(100) || args[3] != 26 {
		t.Fatalf("before_id query args mismatch: %#v", args)
	}
}

func TestMessageEventOwnerBackfillUsesCurrentDeviceWxID(t *testing.T) {
	query := strings.Join(strings.Fields(messageEventOwnerBackfillStatement), " ")
	for _, want := range []string{
		"UPDATE bridge_message_events e",
		"JOIN bridge_devices d ON d.name = e.device",
		"SET e.owner_wxid = d.wxid",
		"e.owner_wxid IS NULL",
		"e.from_wxid = d.wxid OR e.to_wxid = d.wxid OR e.sender_wxid = d.wxid",
	} {
		if !strings.Contains(query, want) {
			t.Fatalf("owner backfill query missing %q: %s", want, query)
		}
	}
}

func TestMessageEventDedupUpdateMatchesStableMessageIdentity(t *testing.T) {
	query := strings.Join(strings.Fields(messageEventDedupUpdateStatement), " ")
	for _, want := range []string{
		"UPDATE bridge_message_events",
		"WHERE device = ?",
		"owner_wxid <=> ?",
		"direction = ?",
		"chat_record_id = ?",
		"LIMIT 1",
	} {
		if !strings.Contains(query, want) {
			t.Fatalf("dedup update query missing %q: %s", want, query)
		}
	}
	if strings.Contains(query, "text = ?") {
		t.Fatalf("dedup update should not overwrite existing text with empty input: %s", query)
	}
}

func TestMessageEventDedupUpdatePromotesObservedEventOverAckPlaceholder(t *testing.T) {
	query := strings.Join(strings.Fields(messageEventDedupUpdateStatement), " ")
	for _, want := range []string{
		"raw_provider = CASE",
		"WHEN ? THEN NULL",
		"ELSE raw_provider",
		"location_latitude = COALESCE(?, location_latitude)",
		"location_scale = CASE WHEN ? > 0 THEN ? ELSE location_scale END",
		"media_url = COALESCE(?, media_url)",
		"media_size = CASE WHEN ? > 0 THEN ? ELSE media_size END",
	} {
		if !strings.Contains(query, want) {
			t.Fatalf("dedup update query missing %q: %s", want, query)
		}
	}
	if strings.Contains(query, "WHEN raw_provider IS NULL OR raw_provider = '' THEN ?") {
		t.Fatalf("module_ack update must not overwrite an already observed message provider: %s", query)
	}
}

func TestSystemRevokeMessageEventBypassesChatRecordDedup(t *testing.T) {
	if messageEventRequiresAppend(bridge.MessageEvent{
		MessageKind:   bridge.MessageKindText,
		AppMsgSubtype: bridge.SystemSubtypeRevoke,
	}) {
		t.Fatalf("non-system event should keep chat_record_id dedup")
	}
	if messageEventRequiresAppend(bridge.MessageEvent{
		MessageKind:   bridge.MessageKindSystem,
		AppMsgSubtype: "notice",
	}) {
		t.Fatalf("non-revoke system event should keep chat_record_id dedup")
	}
	if !messageEventRequiresAppend(bridge.MessageEvent{
		MessageKind:   bridge.MessageKindSystem,
		AppMsgSubtype: bridge.SystemSubtypeRevoke,
	}) {
		t.Fatalf("system revoke event should be appended as a distinct event")
	}
}

func TestListMessagesQuerySupportsChatRooms(t *testing.T) {
	query, args := listMessagesQuery(bridge.MessageFilter{
		Device:   "phone-a",
		ChatID:   "wxid_room@chatroom",
		ChatKind: string(bridge.ChatKindRoom),
		Limit:    25,
	})
	if !strings.Contains(query, "ev.room_id = ? OR ev.from_wxid = ? OR ev.to_wxid = ?") {
		t.Fatalf("message query does not target chat rooms: %s", query)
	}
	if len(args) != 6 {
		t.Fatalf("unexpected args: %#v", args)
	}
	if args[1] != "phone-a" || args[2] != "wxid_room@chatroom" || args[3] != "wxid_room@chatroom" || args[4] != "wxid_room@chatroom" {
		t.Fatalf("chat room args mismatch: %#v", args)
	}
	if args[5] != 25 {
		t.Fatalf("limit arg mismatch: %#v", args)
	}
}

func TestQuoteIdentifierEscapesBackticks(t *testing.T) {
	if got := quoteIdentifier("wechat`gateway"); got != "`wechat``gateway`" {
		t.Fatalf("unexpected quoted identifier: %s", got)
	}
}
