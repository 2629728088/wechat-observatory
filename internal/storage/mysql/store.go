package mysql

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	_ "github.com/go-sql-driver/mysql"

	"wechat-observatory/internal/bridge"
	"wechat-observatory/internal/config"
)

const driverName = "mysql"

type Store struct {
	db *sql.DB
}

type Snapshot struct {
	Devices map[string]config.Device
	APIKeys map[string]config.APIKey
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value = strings.TrimSpace(value); value != "" {
			return value
		}
	}
	return ""
}

func Open(ctx context.Context, dsn string) (*Store, error) {
	db, err := sql.Open(driverName, strings.TrimSpace(dsn))
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(10)
	db.SetMaxIdleConns(5)
	db.SetConnMaxLifetime(30 * time.Minute)
	if err := db.PingContext(ctx); err != nil {
		_ = db.Close()
		return nil, err
	}
	return &Store{db: db}, nil
}

func New(db *sql.DB) *Store {
	return &Store{db: db}
}

func (s *Store) Close() error {
	if s == nil || s.db == nil {
		return nil
	}
	return s.db.Close()
}

func (s *Store) ApplyMigrations(ctx context.Context) error {
	for _, statement := range Migrations() {
		if _, err := s.db.ExecContext(ctx, statement); err != nil {
			return err
		}
	}
	if err := s.ensureMessageEventMediaColumns(ctx); err != nil {
		return err
	}
	if err := s.ensureMessageEventAppMsgColumns(ctx); err != nil {
		return err
	}
	if err := s.ensureMessageEventLocationColumns(ctx); err != nil {
		return err
	}
	if err := s.ensureMessageEventOwnerColumns(ctx); err != nil {
		return err
	}
	if err := s.backfillMessageEventOwnerWxID(ctx); err != nil {
		return err
	}
	if err := s.ensureOutboxOwnerColumns(ctx); err != nil {
		return err
	}
	if err := s.ensureOutboxActionColumns(ctx); err != nil {
		return err
	}
	if err := s.ensureModuleContactOwnerKey(ctx); err != nil {
		return err
	}
	if err := s.ensureAPIKeyEnabledColumn(ctx); err != nil {
		return err
	}
	return nil
}

const messageEventOwnerBackfillStatement = `
	UPDATE bridge_message_events e
	JOIN bridge_devices d ON d.name = e.device
	SET e.owner_wxid = d.wxid
	WHERE (e.owner_wxid IS NULL OR e.owner_wxid = '')
		AND d.wxid IS NOT NULL
		AND d.wxid <> ''
		AND (e.from_wxid = d.wxid OR e.to_wxid = d.wxid OR e.sender_wxid = d.wxid)`

func (s *Store) ensureMessageEventMediaColumns(ctx context.Context) error {
	statements := []string{
		`ALTER TABLE bridge_message_events ADD COLUMN media_kind VARCHAR(32) NULL AFTER message_type`,
		`ALTER TABLE bridge_message_events ADD COLUMN media_mime VARCHAR(128) NULL AFTER media_kind`,
		`ALTER TABLE bridge_message_events ADD COLUMN media_name VARCHAR(255) NULL AFTER media_mime`,
		`ALTER TABLE bridge_message_events ADD COLUMN media_url TEXT NULL AFTER media_name`,
		`ALTER TABLE bridge_message_events ADD COLUMN media_size BIGINT NULL AFTER media_url`,
	}
	for _, statement := range statements {
		if _, err := s.db.ExecContext(ctx, statement); err != nil {
			if strings.Contains(strings.ToLower(err.Error()), "duplicate column") {
				continue
			}
			return err
		}
	}
	return nil
}

func (s *Store) ensureMessageEventAppMsgColumns(ctx context.Context) error {
	statements := []string{
		`ALTER TABLE bridge_message_events ADD COLUMN message_kind VARCHAR(32) NULL AFTER message_type`,
		`ALTER TABLE bridge_message_events ADD COLUMN appmsg_type INT NULL AFTER message_kind`,
		`ALTER TABLE bridge_message_events ADD COLUMN appmsg_subtype VARCHAR(64) NULL AFTER appmsg_type`,
		`ALTER TABLE bridge_message_events ADD COLUMN appmsg_title TEXT NULL AFTER appmsg_subtype`,
		`ALTER TABLE bridge_message_events ADD COLUMN appmsg_description TEXT NULL AFTER appmsg_title`,
		`ALTER TABLE bridge_message_events ADD COLUMN appmsg_url TEXT NULL AFTER appmsg_description`,
		`ALTER TABLE bridge_message_events ADD COLUMN appmsg_file_name VARCHAR(255) NULL AFTER appmsg_url`,
		`ALTER TABLE bridge_message_events ADD COLUMN appmsg_app_name VARCHAR(255) NULL AFTER appmsg_file_name`,
		`ALTER TABLE bridge_message_events ADD COLUMN unsupported JSON NULL AFTER appmsg_app_name`,
		`ALTER TABLE bridge_message_events ADD COLUMN evidence JSON NULL AFTER unsupported`,
	}
	for _, statement := range statements {
		if _, err := s.db.ExecContext(ctx, statement); err != nil {
			if strings.Contains(strings.ToLower(err.Error()), "duplicate column") {
				continue
			}
			return err
		}
	}
	return nil
}

func (s *Store) ensureMessageEventLocationColumns(ctx context.Context) error {
	statements := []string{
		`ALTER TABLE bridge_message_events ADD COLUMN location_latitude DOUBLE NULL AFTER evidence`,
		`ALTER TABLE bridge_message_events ADD COLUMN location_longitude DOUBLE NULL AFTER location_latitude`,
		`ALTER TABLE bridge_message_events ADD COLUMN location_scale INT NULL AFTER location_longitude`,
		`ALTER TABLE bridge_message_events ADD COLUMN location_label TEXT NULL AFTER location_scale`,
		`ALTER TABLE bridge_message_events ADD COLUMN location_poiname TEXT NULL AFTER location_label`,
		`ALTER TABLE bridge_message_events ADD COLUMN location_info_url TEXT NULL AFTER location_poiname`,
		`ALTER TABLE bridge_message_events ADD COLUMN location_poi_id VARCHAR(255) NULL AFTER location_info_url`,
		`ALTER TABLE bridge_message_events ADD COLUMN location_from_poi_list BOOLEAN NULL AFTER location_poi_id`,
		`ALTER TABLE bridge_message_events ADD COLUMN location_poi_category_tips TEXT NULL AFTER location_from_poi_list`,
	}
	for _, statement := range statements {
		if _, err := s.db.ExecContext(ctx, statement); err != nil {
			if strings.Contains(strings.ToLower(err.Error()), "duplicate column") {
				continue
			}
			return err
		}
	}
	return nil
}

func (s *Store) ensureMessageEventOwnerColumns(ctx context.Context) error {
	statements := []string{
		`ALTER TABLE bridge_message_events ADD COLUMN owner_wxid VARCHAR(191) NULL AFTER device`,
		`CREATE INDEX idx_bridge_message_events_owner_time ON bridge_message_events (device, owner_wxid, id)`,
	}
	for _, statement := range statements {
		if _, err := s.db.ExecContext(ctx, statement); err != nil {
			lower := strings.ToLower(err.Error())
			if strings.Contains(lower, "duplicate column") || strings.Contains(lower, "duplicate key name") {
				continue
			}
			return err
		}
	}
	return nil
}

func (s *Store) backfillMessageEventOwnerWxID(ctx context.Context) error {
	_, err := s.db.ExecContext(ctx, messageEventOwnerBackfillStatement)
	return err
}

func (s *Store) ensureOutboxOwnerColumns(ctx context.Context) error {
	statements := []string{
		`ALTER TABLE bridge_module_outbox ADD COLUMN owner_wxid VARCHAR(191) NULL AFTER device`,
		`CREATE INDEX idx_bridge_module_outbox_owner_status ON bridge_module_outbox (device, owner_wxid, status, id)`,
	}
	for _, statement := range statements {
		if _, err := s.db.ExecContext(ctx, statement); err != nil {
			lower := strings.ToLower(err.Error())
			if strings.Contains(lower, "duplicate column") || strings.Contains(lower, "duplicate key name") {
				continue
			}
			return err
		}
	}
	return nil
}

func (s *Store) ensureOutboxActionColumns(ctx context.Context) error {
	statements := []string{
		`ALTER TABLE bridge_module_outbox ADD COLUMN kind VARCHAR(32) NOT NULL DEFAULT 'text' AFTER text`,
		`ALTER TABLE bridge_module_outbox ADD COLUMN payload_json JSON NULL AFTER kind`,
		`ALTER TABLE bridge_module_outbox ADD COLUMN media_kind VARCHAR(32) NULL AFTER payload_json`,
		`ALTER TABLE bridge_module_outbox ADD COLUMN media_mime VARCHAR(128) NULL AFTER media_kind`,
		`ALTER TABLE bridge_module_outbox ADD COLUMN media_name VARCHAR(255) NULL AFTER media_mime`,
		`ALTER TABLE bridge_module_outbox ADD COLUMN media_url TEXT NULL AFTER media_name`,
		`ALTER TABLE bridge_module_outbox ADD COLUMN media_size BIGINT NULL AFTER media_url`,
	}
	for _, statement := range statements {
		if _, err := s.db.ExecContext(ctx, statement); err != nil {
			if strings.Contains(strings.ToLower(err.Error()), "duplicate column") {
				continue
			}
			return err
		}
	}
	_, err := s.db.ExecContext(ctx, `UPDATE bridge_module_outbox SET kind = 'text' WHERE kind IS NULL OR kind = ''`)
	return err
}

func (s *Store) ensureModuleContactOwnerKey(ctx context.Context) error {
	statements := []string{
		`DELETE c1 FROM bridge_module_contacts c1
			JOIN bridge_module_contacts c2
				ON c1.device = c2.device
				AND COALESCE(c1.owner_wxid, '') = COALESCE(c2.owner_wxid, '')
				AND c1.wxid = c2.wxid
				AND c1.id < c2.id`,
		`ALTER TABLE bridge_module_contacts DROP INDEX uniq_bridge_module_contacts_device_wxid`,
		`ALTER TABLE bridge_module_contacts ADD UNIQUE KEY uniq_bridge_module_contacts_owner_wxid (device, owner_wxid, wxid)`,
		`CREATE INDEX idx_bridge_module_contacts_owner_deleted ON bridge_module_contacts (device, owner_wxid, is_deleted, updated_at)`,
	}
	for _, statement := range statements {
		if _, err := s.db.ExecContext(ctx, statement); err != nil {
			lower := strings.ToLower(err.Error())
			if strings.Contains(lower, "can't drop") ||
				strings.Contains(lower, "check that column/key exists") ||
				strings.Contains(lower, "duplicate key name") {
				continue
			}
			return err
		}
	}
	return nil
}

func (s *Store) ensureAPIKeyEnabledColumn(ctx context.Context) error {
	_, err := s.db.ExecContext(ctx, `ALTER TABLE bridge_api_keys ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER nickname`)
	if err == nil {
		return nil
	}
	if strings.Contains(strings.ToLower(err.Error()), "duplicate column") {
		return nil
	}
	return err
}

func Migrations() []string {
	return []string{
		`CREATE TABLE IF NOT EXISTS bridge_api_keys (
			code VARCHAR(128) NOT NULL PRIMARY KEY,
			device VARCHAR(128) NULL,
			nickname VARCHAR(255) NULL,
			enabled BOOLEAN NOT NULL DEFAULT TRUE,
			created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
		) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
		`CREATE TABLE IF NOT EXISTS bridge_devices (
			name VARCHAR(128) NOT NULL PRIMARY KEY,
			wxid VARCHAR(191) NOT NULL,
			nickname VARCHAR(255) NOT NULL,
			timeout_ms BIGINT NOT NULL DEFAULT 5000,
			created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
			KEY idx_bridge_devices_wxid (wxid)
		) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
		`CREATE TABLE IF NOT EXISTS bridge_message_events (
			id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
			source_id VARCHAR(191) NULL,
			event_id BIGINT NULL,
			chat_record_id BIGINT NULL,
			device VARCHAR(128) NOT NULL,
			owner_wxid VARCHAR(191) NULL,
			direction VARCHAR(16) NOT NULL,
			from_wxid VARCHAR(191) NULL,
			to_wxid VARCHAR(191) NULL,
			room_id VARCHAR(191) NULL,
				sender_wxid VARCHAR(191) NULL,
				text TEXT NOT NULL,
				message_type INT NOT NULL,
				message_kind VARCHAR(32) NULL,
				appmsg_type INT NULL,
				appmsg_subtype VARCHAR(64) NULL,
				appmsg_title TEXT NULL,
				appmsg_description TEXT NULL,
				appmsg_url TEXT NULL,
				appmsg_file_name VARCHAR(255) NULL,
				appmsg_app_name VARCHAR(255) NULL,
				unsupported JSON NULL,
				evidence JSON NULL,
				location_latitude DOUBLE NULL,
				location_longitude DOUBLE NULL,
				location_scale INT NULL,
				location_label TEXT NULL,
				location_poiname TEXT NULL,
				location_info_url TEXT NULL,
				location_poi_id VARCHAR(255) NULL,
				location_from_poi_list BOOLEAN NULL,
				location_poi_category_tips TEXT NULL,
				media_kind VARCHAR(32) NULL,
				media_mime VARCHAR(128) NULL,
			media_name VARCHAR(255) NULL,
			media_url TEXT NULL,
			media_size BIGINT NULL,
			raw_provider VARCHAR(64) NULL,
			create_time BIGINT NOT NULL,
			created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
			KEY idx_bridge_message_events_device_time (device, create_time),
			KEY idx_bridge_message_events_owner_time (device, owner_wxid, id),
			KEY idx_bridge_message_events_chat_record (chat_record_id),
			KEY idx_bridge_message_events_direction (direction)
		) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
		`CREATE TABLE IF NOT EXISTS bridge_module_outbox (
			id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
			device VARCHAR(128) NOT NULL,
			owner_wxid VARCHAR(191) NULL,
			wxid VARCHAR(191) NOT NULL,
			text TEXT NOT NULL,
			kind VARCHAR(32) NOT NULL DEFAULT 'text',
			payload_json JSON NULL,
			media_kind VARCHAR(32) NULL,
			media_mime VARCHAR(128) NULL,
			media_name VARCHAR(255) NULL,
			media_url TEXT NULL,
			media_size BIGINT NULL,
			chat_record_id BIGINT NULL,
			status VARCHAR(32) NOT NULL DEFAULT 'pending',
			attempt_count INT NOT NULL DEFAULT 0,
			last_error TEXT NULL,
			lease_until TIMESTAMP NULL,
			created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
			KEY idx_bridge_module_outbox_device_status (device, status, id),
			KEY idx_bridge_module_outbox_owner_status (device, owner_wxid, status, id),
			KEY idx_bridge_module_outbox_lease (lease_until)
		) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
		`CREATE TABLE IF NOT EXISTS bridge_module_runtime (
			device VARCHAR(128) NOT NULL PRIMARY KEY,
			wxid VARCHAR(191) NULL,
			api_key VARCHAR(128) NULL,
			last_register_at TIMESTAMP NULL,
			last_poll_at TIMESTAMP NULL,
			last_poll_limit INT NOT NULL DEFAULT 0,
			last_poll_item_count INT NOT NULL DEFAULT 0,
			last_ack_at TIMESTAMP NULL,
			last_ack_sent_count INT NOT NULL DEFAULT 0,
			last_ack_failed_count INT NOT NULL DEFAULT 0,
			last_error TEXT NULL,
			created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
			KEY idx_bridge_module_runtime_wxid (wxid),
			KEY idx_bridge_module_runtime_api_key (api_key)
		) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
		`CREATE TABLE IF NOT EXISTS bridge_module_contacts (
			id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
			device VARCHAR(128) NOT NULL,
			owner_wxid VARCHAR(191) NULL,
			wxid VARCHAR(191) NOT NULL,
			nickname VARCHAR(255) NULL,
			remark VARCHAR(255) NULL,
			contact_alias VARCHAR(255) NULL,
			contact_type INT NOT NULL DEFAULT 0,
			verify_flag INT NOT NULL DEFAULT 0,
			is_chatroom BOOLEAN NOT NULL DEFAULT FALSE,
			is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
			last_seen_at TIMESTAMP NULL,
			created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
			UNIQUE KEY uniq_bridge_module_contacts_owner_wxid (device, owner_wxid, wxid),
			KEY idx_bridge_module_contacts_device_deleted (device, is_deleted, updated_at),
			KEY idx_bridge_module_contacts_owner_deleted (device, owner_wxid, is_deleted, updated_at),
			KEY idx_bridge_module_contacts_wxid (wxid)
		) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`,
	}
}

func (s *Store) SeedFromConfig(ctx context.Context, cfg config.Config) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer func() {
		_ = tx.Rollback()
	}()
	for _, key := range cfg.APIKeys {
		if err := upsertAPIKey(ctx, tx, key); err != nil {
			return err
		}
	}
	for _, device := range cfg.Devices {
		if err := upsertDevice(ctx, tx, device); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (s *Store) LoadSnapshot(ctx context.Context) (Snapshot, error) {
	snapshot := Snapshot{
		Devices: map[string]config.Device{},
		APIKeys: map[string]config.APIKey{},
	}
	if err := s.loadAPIKeys(ctx, snapshot.APIKeys); err != nil {
		return Snapshot{}, err
	}
	if err := s.loadDevices(ctx, snapshot.Devices); err != nil {
		return Snapshot{}, err
	}
	return snapshot, nil
}

func (s *Store) UpsertAPIKey(ctx context.Context, key config.APIKey) error {
	return upsertAPIKey(ctx, s.db, key)
}

func (s *Store) UpsertDevice(ctx context.Context, device config.Device) error {
	return upsertDevice(ctx, s.db, device)
}

func (s *Store) UpdateDeviceIdentity(ctx context.Context, deviceName string, wxid string, nickname string) error {
	deviceName = strings.TrimSpace(deviceName)
	nickname = firstNonEmpty(strings.TrimSpace(nickname), deviceName)
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO bridge_devices (name, wxid, nickname, timeout_ms)
		VALUES (?, ?, ?, 5000)
		ON DUPLICATE KEY UPDATE
			wxid = VALUES(wxid)`,
		deviceName,
		strings.TrimSpace(wxid),
		nickname,
	)
	return err
}

func (s *Store) LookupDeviceByWxID(ctx context.Context, wxid string) (config.Device, bool, error) {
	wxid = strings.TrimSpace(wxid)
	if wxid == "" {
		return config.Device{}, false, nil
	}
	row := s.db.QueryRowContext(ctx, `
		SELECT d.name, d.wxid, d.nickname, d.timeout_ms
		FROM bridge_devices d
		WHERE d.wxid = ?
		ORDER BY
			EXISTS(
				SELECT 1
				FROM bridge_module_contacts c
				WHERE c.device = d.name
					AND c.owner_wxid = d.wxid
					AND c.is_deleted = 0
			) DESC,
			EXISTS(
				SELECT 1
				FROM bridge_message_events m
				WHERE m.device = d.name
					AND m.owner_wxid = d.wxid
			) DESC,
			d.updated_at DESC,
			d.name ASC
		LIMIT 1`,
		wxid)
	var device config.Device
	var timeoutMS int64
	if err := row.Scan(&device.Name, &device.WxID, &device.Nickname, &timeoutMS); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return config.Device{}, false, nil
		}
		return config.Device{}, false, err
	}
	device.Timeout = time.Duration(timeoutMS) * time.Millisecond
	return device, true, nil
}

func (s *Store) RecordInboundEvent(ctx context.Context, event bridge.MessageEvent) error {
	return s.recordMessageEvent(ctx, event)
}

func (s *Store) RecordOutboundEvent(ctx context.Context, event bridge.MessageEvent) error {
	return s.recordMessageEvent(ctx, event)
}

func (s *Store) HasMessageEvent(ctx context.Context, event bridge.MessageEvent) (bool, error) {
	return s.hasMessageEventMatching(ctx, event, messageEventDedupExistsStatement)
}

func (s *Store) HasMessageEventMedia(ctx context.Context, event bridge.MessageEvent) (bool, error) {
	return s.hasMessageEventMatching(ctx, event, messageEventDedupMediaExistsStatement)
}

func (s *Store) hasMessageEventMatching(ctx context.Context, event bridge.MessageEvent, statement string) (bool, error) {
	if event.ChatRecordID <= 0 {
		return false, nil
	}
	var id int64
	err := s.db.QueryRowContext(ctx, statement,
		strings.TrimSpace(event.Device),
		nullString(event.OwnerWxID),
		string(event.Direction),
		event.ChatRecordID,
	).Scan(&id)
	if errors.Is(err, sql.ErrNoRows) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return true, nil
}

const messageEventDedupUpdateStatement = `
	UPDATE bridge_message_events
	SET
		source_id = COALESCE(?, source_id),
		event_id = COALESCE(?, event_id),
		from_wxid = COALESCE(?, from_wxid),
		to_wxid = COALESCE(?, to_wxid),
		room_id = COALESCE(?, room_id),
		sender_wxid = COALESCE(?, sender_wxid),
			text = CASE WHEN ? <> '' THEN ? ELSE text END,
			message_type = CASE WHEN ? > 0 THEN ? ELSE message_type END,
			message_kind = COALESCE(?, message_kind),
			appmsg_type = CASE WHEN ? > 0 THEN ? ELSE appmsg_type END,
			appmsg_subtype = COALESCE(?, appmsg_subtype),
			appmsg_title = COALESCE(?, appmsg_title),
			appmsg_description = COALESCE(?, appmsg_description),
			appmsg_url = COALESCE(?, appmsg_url),
			appmsg_file_name = COALESCE(?, appmsg_file_name),
			appmsg_app_name = COALESCE(?, appmsg_app_name),
			unsupported = COALESCE(?, unsupported),
			evidence = COALESCE(?, evidence),
			location_latitude = COALESCE(?, location_latitude),
			location_longitude = COALESCE(?, location_longitude),
			location_scale = CASE WHEN ? > 0 THEN ? ELSE location_scale END,
			location_label = COALESCE(?, location_label),
			location_poiname = COALESCE(?, location_poiname),
			location_info_url = COALESCE(?, location_info_url),
			location_poi_id = COALESCE(?, location_poi_id),
			location_from_poi_list = COALESCE(?, location_from_poi_list),
			location_poi_category_tips = COALESCE(?, location_poi_category_tips),
			media_kind = COALESCE(?, media_kind),
			media_mime = COALESCE(?, media_mime),
		media_name = COALESCE(?, media_name),
		media_url = COALESCE(?, media_url),
		media_size = CASE WHEN ? > 0 THEN ? ELSE media_size END,
		raw_provider = CASE
			WHEN ? THEN NULL
			ELSE raw_provider
		END,
		create_time = CASE WHEN ? AND ? > 0 THEN ? ELSE create_time END
	WHERE device = ?
		AND owner_wxid <=> ?
		AND direction = ?
		AND chat_record_id = ?
	LIMIT 1`

const messageEventDedupExistsStatement = `
	SELECT id
	FROM bridge_message_events
	WHERE device = ?
		AND owner_wxid <=> ?
		AND direction = ?
		AND chat_record_id = ?
	LIMIT 1`

const messageEventDedupMediaExistsStatement = `
	SELECT id
	FROM bridge_message_events
	WHERE device = ?
		AND owner_wxid <=> ?
		AND direction = ?
		AND chat_record_id = ?
		AND media_url IS NOT NULL
		AND media_url <> ''
	LIMIT 1`

func (s *Store) RecordModuleActivity(ctx context.Context, activity bridge.ModuleActivity) error {
	switch strings.TrimSpace(activity.Kind) {
	case "register":
		_, err := s.db.ExecContext(ctx, `
			INSERT INTO bridge_module_runtime (
				device, wxid, api_key, last_register_at, last_error
			) VALUES (?, ?, ?, CURRENT_TIMESTAMP, NULL)
			ON DUPLICATE KEY UPDATE
				wxid = VALUES(wxid),
				api_key = VALUES(api_key),
				last_register_at = CURRENT_TIMESTAMP,
				last_error = NULL`,
			strings.TrimSpace(activity.Device),
			nullString(activity.WxID),
			nullString(activity.APIKey),
		)
		return err
	case "poll":
		_, err := s.db.ExecContext(ctx, `
			INSERT INTO bridge_module_runtime (
				device, wxid, last_poll_at, last_poll_limit, last_poll_item_count
			) VALUES (?, ?, CURRENT_TIMESTAMP, ?, ?)
			ON DUPLICATE KEY UPDATE
				wxid = COALESCE(VALUES(wxid), wxid),
				last_poll_at = CURRENT_TIMESTAMP,
				last_poll_limit = VALUES(last_poll_limit),
				last_poll_item_count = VALUES(last_poll_item_count)`,
			strings.TrimSpace(activity.Device),
			nullString(activity.WxID),
			activity.PollLimit,
			activity.PollItemCount,
		)
		return err
	case "ack":
		_, err := s.db.ExecContext(ctx, `
			INSERT INTO bridge_module_runtime (
				device, last_ack_at, last_ack_sent_count, last_ack_failed_count, last_error
			) VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?)
			ON DUPLICATE KEY UPDATE
				last_ack_at = CURRENT_TIMESTAMP,
				last_ack_sent_count = VALUES(last_ack_sent_count),
				last_ack_failed_count = VALUES(last_ack_failed_count),
				last_error = VALUES(last_error)`,
			strings.TrimSpace(activity.Device),
			activity.AckSentCount,
			activity.AckFailedCount,
			nullString(activity.LastError),
		)
		return err
	default:
		return nil
	}
}

func (s *Store) RecordModuleContacts(ctx context.Context, snapshot bridge.ModuleContactSnapshotRequest) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer func() {
		_ = tx.Rollback()
	}()
	device := strings.TrimSpace(snapshot.Device)
	if snapshot.Complete {
		query := `
			UPDATE bridge_module_contacts
			SET is_deleted = TRUE
			WHERE device = ?`
		args := []any{device}
		if ownerWxID := strings.TrimSpace(snapshot.WxID); ownerWxID != "" {
			query += ` AND owner_wxid = ?`
			args = append(args, ownerWxID)
		}
		if _, err := tx.ExecContext(ctx, query, args...); err != nil {
			return err
		}
	}
	for _, contact := range snapshot.Contacts {
		if strings.TrimSpace(contact.WxID) == "" {
			continue
		}
		_, err := tx.ExecContext(ctx, `
			INSERT INTO bridge_module_contacts (
				device, owner_wxid, wxid, nickname, remark, contact_alias, contact_type,
				verify_flag, is_chatroom, is_deleted, last_seen_at
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
			ON DUPLICATE KEY UPDATE
				owner_wxid = VALUES(owner_wxid),
				nickname = VALUES(nickname),
				remark = VALUES(remark),
				contact_alias = VALUES(contact_alias),
				contact_type = VALUES(contact_type),
				verify_flag = VALUES(verify_flag),
				is_chatroom = VALUES(is_chatroom),
				is_deleted = VALUES(is_deleted),
				last_seen_at = CURRENT_TIMESTAMP`,
			device,
			nullString(snapshot.WxID),
			strings.TrimSpace(contact.WxID),
			nullString(contact.Nickname),
			nullString(contact.Remark),
			nullString(contact.Alias),
			contact.Type,
			contact.VerifyFlag,
			contact.Chatroom,
			contact.Deleted,
		)
		if err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (s *Store) EnqueueReply(ctx context.Context, action bridge.ReplyAction) (bridge.ModuleOutboxItem, error) {
	result, err := s.db.ExecContext(ctx, `
		INSERT INTO bridge_module_outbox (
			device, owner_wxid, wxid, text, kind, payload_json,
			media_kind, media_mime, media_name, media_url, media_size,
			chat_record_id, status
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending')`,
		strings.TrimSpace(action.Device),
		nullString(action.OwnerWxID),
		strings.TrimSpace(action.WxID),
		action.Text,
		firstNonEmpty(action.Kind, bridge.OutboxKindText),
		nullString(string(action.PayloadJSON)),
		nullString(action.MediaKind),
		nullString(action.MediaMime),
		nullString(action.MediaName),
		nullString(action.MediaURL),
		nullInt64(action.MediaSize),
		nullInt64(action.ChatRecordID),
	)
	if err != nil {
		return bridge.ModuleOutboxItem{}, err
	}
	id, err := result.LastInsertId()
	if err != nil {
		return bridge.ModuleOutboxItem{}, err
	}
	return s.findOutboxItem(ctx, id)
}

func (s *Store) PollReplyActions(ctx context.Context, req bridge.ModulePollRequest) ([]bridge.ModuleOutboxItem, error) {
	limit := normalizeLimit(req.Limit)
	leaseUntil := time.Now().Add(60 * time.Second)
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer func() {
		_ = tx.Rollback()
	}()

	rows, err := tx.QueryContext(ctx, `
		SELECT id
		FROM bridge_module_outbox
		WHERE device = ?
			AND owner_wxid = ?
			AND (
				status = 'pending'
				OR (status = 'leased' AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP))
			)
		ORDER BY id ASC
		LIMIT ? FOR UPDATE`,
		strings.TrimSpace(req.Device),
		strings.TrimSpace(req.WxID),
		limit,
	)
	if err != nil {
		return nil, err
	}
	var ids []int64
	for rows.Next() {
		var id int64
		if err := rows.Scan(&id); err != nil {
			_ = rows.Close()
			return nil, err
		}
		ids = append(ids, id)
	}
	if err := rows.Close(); err != nil {
		return nil, err
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	for _, id := range ids {
		if _, err := tx.ExecContext(ctx, `
			UPDATE bridge_module_outbox
			SET status = 'leased', attempt_count = attempt_count + 1, lease_until = ?
			WHERE id = ?`,
			leaseUntil,
			id,
		); err != nil {
			return nil, err
		}
	}
	if err := tx.Commit(); err != nil {
		return nil, err
	}
	if len(ids) == 0 {
		return []bridge.ModuleOutboxItem{}, nil
	}
	return s.listOutboxItems(ctx, ids)
}

func (s *Store) AckReplyActions(ctx context.Context, req bridge.ModuleAckRequest) ([]bridge.ModuleOutboxItem, error) {
	for _, item := range req.Items {
		_, err := s.db.ExecContext(ctx, `
			UPDATE bridge_module_outbox
			SET status = ?, last_error = ?, chat_record_id = COALESCE(?, chat_record_id), lease_until = NULL
			WHERE id = ? AND device = ? AND (? = '' OR owner_wxid = ?)`,
			item.Status,
			nullString(item.Error),
			nullInt64(item.ChatRecordID),
			item.ID,
			strings.TrimSpace(req.Device),
			strings.TrimSpace(req.WxID),
			strings.TrimSpace(req.WxID),
		)
		if err != nil {
			return nil, err
		}
	}
	ids := make([]int64, 0, len(req.Items))
	for _, item := range req.Items {
		if item.ID > 0 {
			ids = append(ids, item.ID)
		}
	}
	if len(ids) == 0 {
		return []bridge.ModuleOutboxItem{}, nil
	}
	return s.listOutboxItemsForDevice(ctx, ids, req.Device)
}

func (s *Store) GetReplyAction(ctx context.Context, id int64) (bridge.ModuleOutboxItem, error) {
	item, err := s.findOutboxItem(ctx, id)
	if errors.Is(err, sql.ErrNoRows) {
		return bridge.ModuleOutboxItem{}, bridge.ErrOutboxItemNotFound
	}
	return item, err
}

func (s *Store) findOutboxItem(ctx context.Context, id int64) (bridge.ModuleOutboxItem, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT id, device, owner_wxid, wxid, kind, text, payload_json,
			media_kind, media_mime, media_name, media_url, media_size,
			chat_record_id, status, attempt_count, last_error, created_at, updated_at
		FROM bridge_module_outbox
		WHERE id = ?`,
		id,
	)
	if err != nil {
		return bridge.ModuleOutboxItem{}, err
	}
	defer rows.Close()
	if !rows.Next() {
		return bridge.ModuleOutboxItem{}, sql.ErrNoRows
	}
	item, err := scanOutboxItem(rows)
	if err != nil {
		return bridge.ModuleOutboxItem{}, err
	}
	return item, rows.Err()
}

func (s *Store) listOutboxItems(ctx context.Context, ids []int64) ([]bridge.ModuleOutboxItem, error) {
	return s.listOutboxItemsForDevice(ctx, ids, "")
}

func (s *Store) listOutboxItemsForDevice(ctx context.Context, ids []int64, device string) ([]bridge.ModuleOutboxItem, error) {
	ids = positiveIDs(ids)
	if len(ids) == 0 {
		return []bridge.ModuleOutboxItem{}, nil
	}
	placeholders := make([]string, 0, len(ids))
	args := make([]any, 0, len(ids))
	for _, id := range ids {
		placeholders = append(placeholders, "?")
		args = append(args, id)
	}
	deviceFilter := ""
	if device = strings.TrimSpace(device); device != "" {
		deviceFilter = " AND device = ?"
		args = append(args, device)
	}
	rows, err := s.db.QueryContext(ctx, fmt.Sprintf(`
		SELECT id, device, owner_wxid, wxid, kind, text, payload_json,
			media_kind, media_mime, media_name, media_url, media_size,
			chat_record_id, status, attempt_count, last_error, created_at, updated_at
		FROM bridge_module_outbox
		WHERE id IN (%s)
			%s
		ORDER BY id ASC`, strings.Join(placeholders, ","), deviceFilter), args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []bridge.ModuleOutboxItem
	for rows.Next() {
		item, err := scanOutboxItem(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, item)
	}
	return out, rows.Err()
}

func (s *Store) recordMessageEvent(ctx context.Context, event bridge.MessageEvent) error {
	if event.ChatRecordID > 0 && !messageEventRequiresAppend(event) {
		updated, err := s.updateMessageEventByChatRecordID(ctx, event)
		if err != nil {
			return err
		}
		if updated {
			return nil
		}
	}
	_, err := s.db.ExecContext(ctx, `
			INSERT INTO bridge_message_events (
				source_id, event_id, chat_record_id, device, owner_wxid, direction, from_wxid,
				to_wxid, room_id, sender_wxid, text, message_type, message_kind,
				appmsg_type, appmsg_subtype, appmsg_title, appmsg_description, appmsg_url,
				appmsg_file_name, appmsg_app_name, unsupported, evidence, location_latitude,
				location_longitude, location_scale, location_label, location_poiname,
				location_info_url, location_poi_id, location_from_poi_list,
				location_poi_category_tips, media_kind, media_mime, media_name,
				media_url, media_size, raw_provider, create_time
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		nullString(event.ID),
		nullInt64(event.EventID),
		nullInt64(event.ChatRecordID),
		strings.TrimSpace(event.Device),
		nullString(event.OwnerWxID),
		string(event.Direction),
		nullString(event.From),
		nullString(event.To),
		nullString(event.RoomID),
		nullString(event.Sender),
		event.Text,
		event.MessageType,
		nullString(event.MessageKind),
		nullInt64(int64(event.AppMsgType)),
		nullString(event.AppMsgSubtype),
		nullString(event.AppMsgTitle),
		nullString(event.AppMsgDescription),
		nullString(event.AppMsgURL),
		nullString(event.AppMsgFileName),
		nullString(event.AppMsgAppName),
		nullStringSliceJSON(event.Unsupported),
		nullStringSliceJSON(event.Evidence),
		nullFloat64Ptr(event.LocationLatitude),
		nullFloat64Ptr(event.LocationLongitude),
		nullInt64(int64(event.LocationScale)),
		nullString(event.LocationLabel),
		nullString(event.LocationPoiName),
		nullString(event.LocationInfoURL),
		nullString(event.LocationPoiID),
		nullBoolTrue(event.LocationFromPoiList),
		nullString(event.LocationPoiTips),
		nullString(event.MediaKind),
		nullString(event.MediaMime),
		nullString(event.MediaName),
		nullString(event.MediaURL),
		nullInt64(event.MediaSize),
		nullString(event.RawProvider),
		event.Timestamp(),
	)
	return err
}

func messageEventRequiresAppend(event bridge.MessageEvent) bool {
	return strings.TrimSpace(event.MessageKind) == bridge.MessageKindSystem &&
		strings.TrimSpace(event.AppMsgSubtype) == bridge.SystemSubtypeRevoke
}

func (s *Store) updateMessageEventByChatRecordID(ctx context.Context, event bridge.MessageEvent) (bool, error) {
	authoritative := strings.TrimSpace(event.RawProvider) != bridge.RawProviderModuleAck
	result, err := s.db.ExecContext(ctx, messageEventDedupUpdateStatement,
		nullString(event.ID),
		nullInt64(event.EventID),
		nullString(event.From),
		nullString(event.To),
		nullString(event.RoomID),
		nullString(event.Sender),
		strings.TrimSpace(event.Text), event.Text,
		event.MessageType, event.MessageType,
		nullString(event.MessageKind),
		event.AppMsgType, event.AppMsgType,
		nullString(event.AppMsgSubtype),
		nullString(event.AppMsgTitle),
		nullString(event.AppMsgDescription),
		nullString(event.AppMsgURL),
		nullString(event.AppMsgFileName),
		nullString(event.AppMsgAppName),
		nullStringSliceJSON(event.Unsupported),
		nullStringSliceJSON(event.Evidence),
		nullFloat64Ptr(event.LocationLatitude),
		nullFloat64Ptr(event.LocationLongitude),
		event.LocationScale, event.LocationScale,
		nullString(event.LocationLabel),
		nullString(event.LocationPoiName),
		nullString(event.LocationInfoURL),
		nullString(event.LocationPoiID),
		nullBoolTrue(event.LocationFromPoiList),
		nullString(event.LocationPoiTips),
		nullString(event.MediaKind),
		nullString(event.MediaMime),
		nullString(event.MediaName),
		nullString(event.MediaURL),
		event.MediaSize, event.MediaSize,
		authoritative,
		authoritative, event.Timestamp(), event.Timestamp(),
		strings.TrimSpace(event.Device),
		nullString(event.OwnerWxID),
		string(event.Direction),
		event.ChatRecordID,
	)
	if err != nil {
		return false, err
	}
	if affected, err := result.RowsAffected(); err == nil && affected > 0 {
		return true, nil
	}
	var id int64
	err = s.db.QueryRowContext(ctx, messageEventDedupExistsStatement,
		strings.TrimSpace(event.Device),
		nullString(event.OwnerWxID),
		string(event.Direction),
		event.ChatRecordID,
	).Scan(&id)
	if errors.Is(err, sql.ErrNoRows) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return true, nil
}

func upsertAPIKey(ctx context.Context, exec sqlExecutor, key config.APIKey) error {
	_, err := exec.ExecContext(ctx, `
		INSERT INTO bridge_api_keys (code, device, nickname, enabled)
		VALUES (?, ?, ?, ?)
		ON DUPLICATE KEY UPDATE
			device = VALUES(device),
			nickname = VALUES(nickname),
			enabled = VALUES(enabled)`,
		strings.TrimSpace(key.Code),
		nullString(key.Device),
		nullString(key.Nickname),
		!key.Disabled,
	)
	return err
}

func (s *Store) DeleteAPIKey(ctx context.Context, code string) error {
	code = strings.TrimSpace(code)
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `
		UPDATE bridge_module_runtime
		SET api_key = NULL,
			last_error = 'api key revoked',
			updated_at = CURRENT_TIMESTAMP
		WHERE api_key = ?`,
		code,
	); err != nil {
		_ = tx.Rollback()
		return err
	}
	if _, err := tx.ExecContext(ctx, `
		DELETE FROM bridge_api_keys
		WHERE code = ?`,
		code,
	); err != nil {
		_ = tx.Rollback()
		return err
	}
	return tx.Commit()
}

func (s *Store) SetAPIKeyEnabled(ctx context.Context, code string, enabled bool) error {
	code = strings.TrimSpace(code)
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	var exists int
	if err := tx.QueryRowContext(ctx, `
		SELECT COUNT(*)
		FROM bridge_api_keys
		WHERE code = ?`,
		code,
	).Scan(&exists); err != nil {
		_ = tx.Rollback()
		return err
	}
	if exists == 0 {
		_ = tx.Rollback()
		return fmt.Errorf("api key %q not found", code)
	}
	if _, err := tx.ExecContext(ctx, `
		UPDATE bridge_api_keys
		SET enabled = ?
		WHERE code = ?`,
		enabled,
		code,
	); err != nil {
		_ = tx.Rollback()
		return err
	}
	if !enabled {
		if _, err := tx.ExecContext(ctx, `
			UPDATE bridge_module_runtime
			SET api_key = NULL,
				last_error = 'api key disabled',
				updated_at = CURRENT_TIMESTAMP
			WHERE api_key = ?`,
			code,
		); err != nil {
			_ = tx.Rollback()
			return err
		}
	}
	return tx.Commit()
}

func upsertDevice(ctx context.Context, exec sqlExecutor, device config.Device) error {
	_, err := exec.ExecContext(ctx, `
		INSERT INTO bridge_devices (name, wxid, nickname, timeout_ms)
		VALUES (?, ?, ?, ?)
		ON DUPLICATE KEY UPDATE
			wxid = CASE
				WHEN VALUES(wxid) <> '' THEN VALUES(wxid)
				ELSE wxid
			END,
			nickname = VALUES(nickname),
			timeout_ms = VALUES(timeout_ms)`,
		strings.TrimSpace(device.Name),
		strings.TrimSpace(device.WxID),
		strings.TrimSpace(device.Nickname),
		device.Timeout.Milliseconds(),
	)
	return err
}

func (s *Store) loadAPIKeys(ctx context.Context, out map[string]config.APIKey) error {
	rows, err := s.db.QueryContext(ctx, `
		SELECT code, device, nickname, enabled
		FROM bridge_api_keys`)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var key config.APIKey
		var device, nickname sql.NullString
		var enabled bool
		if err := rows.Scan(&key.Code, &device, &nickname, &enabled); err != nil {
			return err
		}
		key.Device = device.String
		key.Nickname = nickname.String
		key.Disabled = !enabled
		out[key.Code] = key
	}
	return rows.Err()
}

func (s *Store) loadDevices(ctx context.Context, out map[string]config.Device) error {
	rows, err := s.db.QueryContext(ctx, `
		SELECT name, wxid, nickname, timeout_ms
		FROM bridge_devices`)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var device config.Device
		var timeoutMS int64
		if err := rows.Scan(&device.Name, &device.WxID, &device.Nickname, &timeoutMS); err != nil {
			return err
		}
		device.Timeout = time.Duration(timeoutMS) * time.Millisecond
		out[device.Name] = device
	}
	return rows.Err()
}

type sqlExecutor interface {
	ExecContext(ctx context.Context, query string, args ...any) (sql.Result, error)
	QueryRowContext(ctx context.Context, query string, args ...any) *sql.Row
}

type scanner interface {
	Scan(dest ...any) error
}

func scanOutboxItem(row scanner) (bridge.ModuleOutboxItem, error) {
	var item bridge.ModuleOutboxItem
	var ownerWxID, kind, payloadJSON, mediaKind, mediaMime, mediaName, mediaURL sql.NullString
	var mediaSize sql.NullInt64
	var chatRecordID sql.NullInt64
	var lastError sql.NullString
	var createdAt, updatedAt time.Time
	if err := row.Scan(
		&item.ID,
		&item.Device,
		&ownerWxID,
		&item.WxID,
		&kind,
		&item.Text,
		&payloadJSON,
		&mediaKind,
		&mediaMime,
		&mediaName,
		&mediaURL,
		&mediaSize,
		&chatRecordID,
		&item.Status,
		&item.AttemptCount,
		&lastError,
		&createdAt,
		&updatedAt,
	); err != nil {
		return bridge.ModuleOutboxItem{}, err
	}
	item.OwnerWxID = ownerWxID.String
	item.Kind = firstNonEmpty(kind.String, bridge.OutboxKindText)
	if payloadJSON.Valid && strings.TrimSpace(payloadJSON.String) != "" {
		item.PayloadJSON = []byte(payloadJSON.String)
	}
	item.MediaKind = mediaKind.String
	item.MediaMime = mediaMime.String
	item.MediaName = mediaName.String
	item.MediaURL = mediaURL.String
	item.MediaSize = mediaSize.Int64
	item.ChatRecordID = chatRecordID.Int64
	item.LastError = lastError.String
	item.CreatedAt = formatTime(createdAt)
	item.UpdatedAt = formatTime(updatedAt)
	return item, nil
}

func nullString(value string) sql.NullString {
	value = strings.TrimSpace(value)
	return sql.NullString{String: value, Valid: value != ""}
}

func nullStringSliceJSON(values []string) sql.NullString {
	if len(values) == 0 {
		return sql.NullString{}
	}
	cleaned := make([]string, 0, len(values))
	for _, value := range values {
		if value = strings.TrimSpace(value); value != "" {
			cleaned = append(cleaned, value)
		}
	}
	if len(cleaned) == 0 {
		return sql.NullString{}
	}
	data, err := json.Marshal(cleaned)
	if err != nil {
		return sql.NullString{}
	}
	return sql.NullString{String: string(data), Valid: true}
}

func nullFloat64Ptr(value *float64) sql.NullFloat64 {
	if value == nil {
		return sql.NullFloat64{}
	}
	return sql.NullFloat64{Float64: *value, Valid: true}
}

func nullBoolTrue(value bool) sql.NullBool {
	return sql.NullBool{Bool: value, Valid: value}
}

func nullInt64(value int64) sql.NullInt64 {
	return sql.NullInt64{Int64: value, Valid: value > 0}
}

func positiveIDs(ids []int64) []int64 {
	out := make([]int64, 0, len(ids))
	seen := map[int64]struct{}{}
	for _, id := range ids {
		if id <= 0 {
			continue
		}
		if _, ok := seen[id]; ok {
			continue
		}
		seen[id] = struct{}{}
		out = append(out, id)
	}
	return out
}
