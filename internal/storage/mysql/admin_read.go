package mysql

import (
	"context"
	"database/sql"
	"encoding/json"
	"strings"
	"time"

	"wechat-observatory/internal/bridge"
)

func (s *Store) ListAPIKeys(ctx context.Context, limit int) ([]bridge.APIKeyView, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT code, device, nickname, enabled, created_at, updated_at
		FROM bridge_api_keys
		ORDER BY updated_at DESC, code ASC
		LIMIT ?`, normalizeLimit(limit))
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []bridge.APIKeyView
	for rows.Next() {
		var item bridge.APIKeyView
		var device, nickname sql.NullString
		var enabled bool
		var createdAt, updatedAt time.Time
		if err := rows.Scan(
			&item.Code,
			&device,
			&nickname,
			&enabled,
			&createdAt,
			&updatedAt,
		); err != nil {
			return nil, err
		}
		item.Device = device.String
		item.Nickname = nickname.String
		item.APIKey = item.Code
		item.Enabled = enabled
		item.CreatedAt = formatTime(createdAt)
		item.UpdatedAt = formatTime(updatedAt)
		out = append(out, item)
	}
	return out, rows.Err()
}

func (s *Store) ListStoredEvents(ctx context.Context, limit int) ([]bridge.StoredEventView, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT ev.id, ev.source_id, ev.event_id, ev.chat_record_id, ev.device, ev.owner_wxid,
			ev.direction, ev.from_wxid, ev.to_wxid, ev.room_id, ev.sender_wxid, ev.text,
			ev.message_type, ev.message_kind, ev.appmsg_type, ev.appmsg_subtype,
			ev.appmsg_title, ev.appmsg_description, ev.appmsg_url, ev.appmsg_file_name,
			ev.appmsg_app_name, ev.unsupported, ev.evidence,
			ev.location_latitude, ev.location_longitude, ev.location_scale, ev.location_label,
			ev.location_poiname, ev.location_info_url, ev.location_poi_id,
			ev.location_from_poi_list, ev.location_poi_category_tips,
			ev.media_kind, ev.media_mime, ev.media_name, ev.media_url, ev.media_size,
			ev.raw_provider, ev.create_time,
			ev.created_at, c.nickname, c.remark, c.contact_alias, c.is_deleted
		FROM bridge_message_events ev
		LEFT JOIN bridge_module_contacts c
			ON c.device = ev.device
			AND c.owner_wxid <=> ev.owner_wxid
			AND c.wxid = CASE
				WHEN ev.room_id IS NOT NULL AND ev.room_id <> '' THEN ev.room_id
				WHEN ev.direction = 'sent' THEN COALESCE(NULLIF(ev.to_wxid, ''), NULLIF(ev.from_wxid, ''), ev.sender_wxid)
				ELSE COALESCE(NULLIF(ev.from_wxid, ''), NULLIF(ev.sender_wxid, ''), ev.to_wxid)
			END
		ORDER BY ev.id DESC
		LIMIT ?`, normalizeLimit(limit))
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	return scanStoredEventViews(rows)
}

func (s *Store) ListMessages(ctx context.Context, filter bridge.MessageFilter) ([]bridge.StoredEventView, error) {
	query, args := listMessagesQuery(filter)
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	return scanStoredEventViews(rows)
}

func listMessagesQuery(filter bridge.MessageFilter) (string, []any) {
	conditions := []string{"(ev.raw_provider IS NULL OR ev.raw_provider <> ?)"}
	args := []any{bridge.RawProviderModuleAck}
	if device := strings.TrimSpace(filter.Device); device != "" {
		conditions = append(conditions, "ev.device = ?")
		args = append(args, device)
	}
	if ownerWxID := strings.TrimSpace(filter.OwnerWxID); ownerWxID != "" {
		conditions = append(conditions, "ev.owner_wxid = ?")
		args = append(args, ownerWxID)
	}
	chatID := strings.TrimSpace(filter.ChatID)
	if chatID == "" {
		chatID = strings.TrimSpace(filter.WxID)
	}
	if chatID != "" {
		chatKind := strings.ToLower(strings.TrimSpace(filter.ChatKind))
		switch {
		case chatKind == string(bridge.ChatKindRoom) || strings.Contains(strings.ToLower(chatID), "@chatroom"):
			conditions = append(conditions, "(ev.room_id = ? OR ev.from_wxid = ? OR ev.to_wxid = ?)")
			args = append(args, chatID, chatID, chatID)
		case chatKind == string(bridge.ChatKindDirect):
			conditions = append(conditions, "((ev.room_id IS NULL OR ev.room_id = '') AND (ev.from_wxid = ? OR ev.to_wxid = ? OR ev.sender_wxid = ?))")
			args = append(args, chatID, chatID, chatID)
		default:
			conditions = append(conditions, "(ev.from_wxid = ? OR ev.to_wxid = ? OR ev.room_id = ? OR ev.sender_wxid = ?)")
			args = append(args, chatID, chatID, chatID, chatID)
		}
	} else if wxid := strings.TrimSpace(filter.WxID); wxid != "" {
		conditions = append(conditions, "(ev.from_wxid = ? OR ev.to_wxid = ? OR ev.room_id = ? OR ev.sender_wxid = ?)")
		args = append(args, wxid, wxid, wxid, wxid)
	}
	orderBy := "ev.id DESC"
	if filter.AfterIDSet {
		conditions = append(conditions, "ev.id > ?")
		args = append(args, filter.AfterID)
		orderBy = "ev.id ASC"
	}
	if filter.BeforeID > 0 {
		conditions = append(conditions, "ev.id < ?")
		args = append(args, filter.BeforeID)
	}
	args = append(args, normalizeMessageQueryLimit(filter.Limit))
	return `
		SELECT ev.id, ev.source_id, ev.event_id, ev.chat_record_id, ev.device, ev.owner_wxid,
			ev.direction, ev.from_wxid, ev.to_wxid, ev.room_id, ev.sender_wxid, ev.text,
			ev.message_type, ev.message_kind, ev.appmsg_type, ev.appmsg_subtype,
			ev.appmsg_title, ev.appmsg_description, ev.appmsg_url, ev.appmsg_file_name,
			ev.appmsg_app_name, ev.unsupported, ev.evidence,
			ev.location_latitude, ev.location_longitude, ev.location_scale, ev.location_label,
			ev.location_poiname, ev.location_info_url, ev.location_poi_id,
			ev.location_from_poi_list, ev.location_poi_category_tips,
			ev.media_kind, ev.media_mime, ev.media_name, ev.media_url, ev.media_size,
			ev.raw_provider, ev.create_time,
			ev.created_at, c.nickname, c.remark, c.contact_alias, c.is_deleted
		FROM bridge_message_events ev
		LEFT JOIN bridge_module_contacts c
			ON c.device = ev.device
			AND c.owner_wxid <=> ev.owner_wxid
			AND c.wxid = CASE
				WHEN ev.room_id IS NOT NULL AND ev.room_id <> '' THEN ev.room_id
				WHEN ev.direction = 'sent' THEN COALESCE(NULLIF(ev.to_wxid, ''), NULLIF(ev.from_wxid, ''), ev.sender_wxid)
				ELSE COALESCE(NULLIF(ev.from_wxid, ''), NULLIF(ev.sender_wxid, ''), ev.to_wxid)
			END
		WHERE ` + strings.Join(conditions, " AND ") + `
		ORDER BY ` + orderBy + `
		LIMIT ?`, args
}

func normalizeMessageQueryLimit(limit int) int {
	if limit <= 0 {
		return 50
	}
	if limit > 501 {
		return 501
	}
	return limit
}

func scanStoredEventViews(rows *sql.Rows) ([]bridge.StoredEventView, error) {
	var out []bridge.StoredEventView
	for rows.Next() {
		var item bridge.StoredEventView
		var sourceID, ownerWxID, fromWxID, toWxID, roomID, senderWxID, rawProvider sql.NullString
		var messageKind, appMsgSubtype, appMsgTitle, appMsgDescription, appMsgURL, appMsgFileName, appMsgAppName sql.NullString
		var unsupportedJSON, evidenceJSON sql.NullString
		var locationLabel, locationPoiName, locationInfoURL, locationPoiID, locationPoiTips sql.NullString
		var locationLatitude, locationLongitude sql.NullFloat64
		var locationScale sql.NullInt64
		var locationFromPoiList sql.NullBool
		var mediaKind, mediaMime, mediaName, mediaURL sql.NullString
		var chatName, chatRemark, chatAlias sql.NullString
		var eventID, chatRecordID, appMsgType, mediaSize sql.NullInt64
		var chatDeleted sql.NullBool
		var createdAt time.Time
		if err := rows.Scan(
			&item.ID,
			&sourceID,
			&eventID,
			&chatRecordID,
			&item.Device,
			&ownerWxID,
			&item.Direction,
			&fromWxID,
			&toWxID,
			&roomID,
			&senderWxID,
			&item.Text,
			&item.MessageType,
			&messageKind,
			&appMsgType,
			&appMsgSubtype,
			&appMsgTitle,
			&appMsgDescription,
			&appMsgURL,
			&appMsgFileName,
			&appMsgAppName,
			&unsupportedJSON,
			&evidenceJSON,
			&locationLatitude,
			&locationLongitude,
			&locationScale,
			&locationLabel,
			&locationPoiName,
			&locationInfoURL,
			&locationPoiID,
			&locationFromPoiList,
			&locationPoiTips,
			&mediaKind,
			&mediaMime,
			&mediaName,
			&mediaURL,
			&mediaSize,
			&rawProvider,
			&item.CreateTime,
			&createdAt,
			&chatName,
			&chatRemark,
			&chatAlias,
			&chatDeleted,
		); err != nil {
			return nil, err
		}
		item.SourceID = sourceID.String
		item.EventID = eventID.Int64
		item.ChatRecordID = chatRecordID.Int64
		item.OwnerWxID = ownerWxID.String
		item.FromWxID = fromWxID.String
		item.ToWxID = toWxID.String
		item.RoomID = roomID.String
		item.SenderWxID = senderWxID.String
		item.MessageKind = messageKind.String
		item.AppMsgType = int32(appMsgType.Int64)
		item.AppMsgSubtype = appMsgSubtype.String
		item.AppMsgTitle = appMsgTitle.String
		item.AppMsgDescription = appMsgDescription.String
		item.AppMsgURL = appMsgURL.String
		item.AppMsgFileName = appMsgFileName.String
		item.AppMsgAppName = appMsgAppName.String
		item.Unsupported = decodeStringSliceJSON(unsupportedJSON.String)
		item.Evidence = decodeStringSliceJSON(evidenceJSON.String)
		if locationLatitude.Valid {
			value := locationLatitude.Float64
			item.LocationLatitude = &value
		}
		if locationLongitude.Valid {
			value := locationLongitude.Float64
			item.LocationLongitude = &value
		}
		item.LocationScale = int(locationScale.Int64)
		item.LocationLabel = locationLabel.String
		item.LocationPoiName = locationPoiName.String
		item.LocationInfoURL = locationInfoURL.String
		item.LocationPoiID = locationPoiID.String
		item.LocationFromPoiList = locationFromPoiList.Bool
		item.LocationPoiTips = locationPoiTips.String
		item.MediaKind = mediaKind.String
		item.MediaMime = mediaMime.String
		item.MediaName = mediaName.String
		item.MediaURL = mediaURL.String
		item.MediaSize = mediaSize.Int64
		item.RawProvider = rawProvider.String
		event := bridge.MessageEvent{
			Direction: bridge.Direction(item.Direction),
			From:      item.FromWxID,
			To:        item.ToWxID,
			RoomID:    item.RoomID,
			Sender:    item.SenderWxID,
		}.Normalize()
		item.ChatID = event.ChatID()
		item.ChatKind = string(event.Kind())
		item.ChatName = chatName.String
		item.ChatRemark = chatRemark.String
		item.ChatAlias = chatAlias.String
		item.ChatDisplayName = firstNonEmptyContactLabel(chatRemark.String, chatName.String, chatAlias.String)
		item.ChatDeleted = chatDeleted.Bool
		item.CreatedAt = formatTime(createdAt)
		out = append(out, item)
	}
	return out, rows.Err()
}

func (s *Store) ListModuleStatuses(ctx context.Context) ([]bridge.ModuleStatusView, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT ak.device, d.wxid, COALESCE(NULLIF(d.nickname, ''), ak.nickname, ak.device), ak.enabled, d.updated_at,
			rt.last_register_at,
			rt.last_poll_at,
			rt.last_ack_at,
			rt.last_poll_limit,
			rt.last_poll_item_count,
			rt.last_ack_sent_count,
			rt.last_ack_failed_count,
			rt.last_error,
			rt.updated_at,
			rt.api_key,
			ak.active_code,
			COALESCE(obs.pending_count, 0),
			COALESCE(obs.leased_count, 0),
			COALESCE(obs.sent_count, 0),
			COALESCE(obs.failed_count, 0),
			obs.last_outbox_id,
			last_ob.status,
			last_ob.last_error,
			last_ob.updated_at,
			ev.last_event_at,
			ev.last_inbound_at,
			ev.last_outbound_ack_at
		FROM (
			SELECT raw.device,
				MAX(CASE WHEN raw.enabled THEN 1 ELSE 0 END) AS enabled,
				NULLIF(MAX(CASE WHEN raw.nickname IS NOT NULL AND raw.nickname <> '' THEN raw.nickname ELSE '' END), '') AS nickname,
				NULLIF(MAX(CASE WHEN raw.enabled AND raw.code = rt.api_key THEN raw.code ELSE '' END), '') AS active_code
			FROM bridge_api_keys raw
			LEFT JOIN bridge_module_runtime rt
				ON rt.device = raw.device
			WHERE raw.device IS NOT NULL AND raw.device <> ''
			GROUP BY raw.device
		) ak
		LEFT JOIN bridge_devices d
			ON d.name = ak.device
		LEFT JOIN bridge_module_runtime rt
			ON rt.device = ak.device
		LEFT JOIN (
			SELECT device, owner_wxid,
				SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) AS pending_count,
				SUM(CASE WHEN status = 'leased' THEN 1 ELSE 0 END) AS leased_count,
				SUM(CASE WHEN status = 'sent' THEN 1 ELSE 0 END) AS sent_count,
				SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) AS failed_count,
				MAX(id) AS last_outbox_id
			FROM bridge_module_outbox
			WHERE owner_wxid IS NOT NULL AND owner_wxid <> ''
			GROUP BY device, owner_wxid
		) obs ON obs.device = ak.device AND obs.owner_wxid = d.wxid
		LEFT JOIN bridge_module_outbox last_ob
			ON last_ob.id = obs.last_outbox_id
		LEFT JOIN (
			SELECT device,
				MAX(created_at) AS last_event_at,
				MAX(CASE WHEN direction = 'recv' THEN created_at ELSE NULL END) AS last_inbound_at,
				MAX(CASE WHEN direction = 'sent' AND raw_provider = ? THEN created_at ELSE NULL END) AS last_outbound_ack_at
			FROM bridge_message_events
			GROUP BY device
		) ev ON ev.device = ak.device
		ORDER BY ak.device ASC`, bridge.RawProviderModuleAck)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []bridge.ModuleStatusView{}
	for rows.Next() {
		var item bridge.ModuleStatusView
		var deviceWxID, deviceNickname sql.NullString
		var enabled bool
		var runtimeError, runtimeAPIKey, activeAPIKey sql.NullString
		var deviceUpdatedAt sql.NullTime
		var lastRegisterAt, lastPollAt, lastAckAt, runtimeUpdatedAt sql.NullTime
		var lastOutboxUpdated, lastEventAt, lastInboundAt, lastOutboundAckAt sql.NullTime
		var lastPollLimit, lastPollItemCount, lastAckSentCount, lastAckFailedCount sql.NullInt64
		var pending, leased, sent, failed int64
		var lastOutboxID sql.NullInt64
		var lastOutboxStatus, lastOutboxError sql.NullString
		if err := rows.Scan(
			&item.Device,
			&deviceWxID,
			&deviceNickname,
			&enabled,
			&deviceUpdatedAt,
			&lastRegisterAt,
			&lastPollAt,
			&lastAckAt,
			&lastPollLimit,
			&lastPollItemCount,
			&lastAckSentCount,
			&lastAckFailedCount,
			&runtimeError,
			&runtimeUpdatedAt,
			&runtimeAPIKey,
			&activeAPIKey,
			&pending,
			&leased,
			&sent,
			&failed,
			&lastOutboxID,
			&lastOutboxStatus,
			&lastOutboxError,
			&lastOutboxUpdated,
			&lastEventAt,
			&lastInboundAt,
			&lastOutboundAckAt,
		); err != nil {
			return nil, err
		}
		item.DeviceWxID = deviceWxID.String
		item.DeviceNickname = deviceNickname.String
		item.Enabled = enabled
		item.Registered = item.Enabled &&
			strings.TrimSpace(item.DeviceWxID) != "" &&
			activeAPIKey.Valid &&
			runtimeAPIKey.Valid &&
			strings.TrimSpace(runtimeAPIKey.String) == strings.TrimSpace(activeAPIKey.String)
		item.LastRegisterAt = formatNullTime(lastRegisterAt)
		item.LastPollAt = formatNullTime(lastPollAt)
		item.LastAckAt = formatNullTime(lastAckAt)
		item.LastPollLimit = int(lastPollLimit.Int64)
		item.LastPollItemCount = int(lastPollItemCount.Int64)
		item.LastAckSentCount = int(lastAckSentCount.Int64)
		item.LastAckFailedCount = int(lastAckFailedCount.Int64)
		item.PendingOutbox = pending
		item.LeasedOutbox = leased
		item.SentOutbox = sent
		item.FailedOutbox = failed
		item.LastOutboxID = lastOutboxID.Int64
		item.LastOutboxStatus = lastOutboxStatus.String
		item.LastOutboxError = lastOutboxError.String
		item.LastOutboxUpdated = formatNullTime(lastOutboxUpdated)
		item.LastEventAt = formatNullTime(lastEventAt)
		item.LastInboundAt = formatNullTime(lastInboundAt)
		item.LastOutboundAckAt = formatNullTime(lastOutboundAckAt)
		item.RuntimeUpdatedAt = formatNullTime(runtimeUpdatedAt)
		item.DeviceUpdatedAt = formatNullTime(deviceUpdatedAt)
		if item.LastOutboxError == "" {
			item.LastOutboxError = runtimeError.String
		}
		item.NormalizeRuntimeStatus()
		out = append(out, item)
	}
	return out, rows.Err()
}

func (s *Store) ListModuleContacts(ctx context.Context, filter bridge.ModuleContactFilter) ([]bridge.ModuleContactView, error) {
	limit := normalizeLimit(filter.Limit)
	conditions := []string{"1=1"}
	args := []any{}
	if device := strings.TrimSpace(filter.Device); device != "" {
		conditions = append(conditions, "device = ?")
		args = append(args, device)
	}
	if ownerWxID := strings.TrimSpace(filter.OwnerWxID); ownerWxID != "" {
		conditions = append(conditions, "owner_wxid = ?")
		args = append(args, ownerWxID)
	}
	if !filter.IncludeDeleted {
		conditions = append(conditions, "is_deleted = FALSE")
	}
	if query := strings.TrimSpace(filter.Query); query != "" {
		like := "%" + query + "%"
		conditions = append(conditions, "(wxid LIKE ? OR nickname LIKE ? OR remark LIKE ? OR contact_alias LIKE ?)")
		args = append(args, like, like, like, like)
	}
	args = append(args, limit)
	rows, err := s.db.QueryContext(ctx, `
		SELECT id, device, owner_wxid, wxid, nickname, remark, contact_alias,
			contact_type, verify_flag, is_chatroom, is_deleted, last_seen_at, updated_at
		FROM bridge_module_contacts
		WHERE `+strings.Join(conditions, " AND ")+`
		ORDER BY is_deleted ASC,
			COALESCE(NULLIF(remark, ''), NULLIF(nickname, ''), wxid) ASC,
			id ASC
		LIMIT ?`, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []bridge.ModuleContactView
	for rows.Next() {
		var item bridge.ModuleContactView
		var ownerWxID, nickname, remark, alias sql.NullString
		var lastSeenAt sql.NullTime
		var updatedAt time.Time
		if err := rows.Scan(
			&item.ID,
			&item.Device,
			&ownerWxID,
			&item.WxID,
			&nickname,
			&remark,
			&alias,
			&item.Type,
			&item.VerifyFlag,
			&item.Chatroom,
			&item.Deleted,
			&lastSeenAt,
			&updatedAt,
		); err != nil {
			return nil, err
		}
		item.OwnerWxID = ownerWxID.String
		item.Nickname = nickname.String
		item.Remark = remark.String
		item.Alias = alias.String
		item.LastSeenAt = formatNullTime(lastSeenAt)
		item.UpdatedAt = formatTime(updatedAt)
		out = append(out, item)
	}
	return out, rows.Err()
}

func normalizeLimit(limit int) int {
	if limit <= 0 {
		return 50
	}
	if limit > 500 {
		return 500
	}
	return limit
}

func formatTime(value time.Time) string {
	if value.IsZero() {
		return ""
	}
	return value.UTC().Format(time.RFC3339)
}

func formatNullTime(value sql.NullTime) string {
	if !value.Valid {
		return ""
	}
	return formatTime(value.Time)
}

func decodeStringSliceJSON(value string) []string {
	value = strings.TrimSpace(value)
	if value == "" {
		return nil
	}
	var out []string
	if err := json.Unmarshal([]byte(value), &out); err != nil {
		return nil
	}
	cleaned := make([]string, 0, len(out))
	for _, item := range out {
		if item = strings.TrimSpace(item); item != "" {
			cleaned = append(cleaned, item)
		}
	}
	if len(cleaned) == 0 {
		return nil
	}
	return cleaned
}

func firstNonEmptyContactLabel(values ...string) string {
	for _, value := range values {
		if value = strings.TrimSpace(value); value != "" {
			return value
		}
	}
	return ""
}
