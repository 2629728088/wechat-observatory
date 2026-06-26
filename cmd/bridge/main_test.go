package main

import (
	"context"
	"errors"
	"testing"

	"wechat-observatory/internal/config"
)

func TestPrepareMySQLStoreSkipsSeedWhenAutoMigrateDisabled(t *testing.T) {
	store := &fakeSetupStore{}
	cfg := config.Config{MySQL: config.MySQLConfig{AutoMigrate: false}}

	if err := prepareMySQLStore(context.Background(), store, cfg); err != nil {
		t.Fatalf("prepare mysql store: %v", err)
	}
	if store.migrated || store.seeded {
		t.Fatalf("expected no migration or seed, got migrated=%v seeded=%v", store.migrated, store.seeded)
	}
}

func TestPrepareMySQLStoreSeedsOnlyAfterMigration(t *testing.T) {
	store := &fakeSetupStore{}
	cfg := config.Config{MySQL: config.MySQLConfig{AutoMigrate: true}}

	if err := prepareMySQLStore(context.Background(), store, cfg); err != nil {
		t.Fatalf("prepare mysql store: %v", err)
	}
	if !store.migrated || !store.seeded {
		t.Fatalf("expected migration and seed, got migrated=%v seeded=%v", store.migrated, store.seeded)
	}
}

func TestPrepareMySQLStoreStopsWhenMigrationFails(t *testing.T) {
	store := &fakeSetupStore{migrateErr: errors.New("migration failed")}
	cfg := config.Config{MySQL: config.MySQLConfig{AutoMigrate: true}}

	if err := prepareMySQLStore(context.Background(), store, cfg); err == nil {
		t.Fatal("expected migration error")
	}
	if store.seeded {
		t.Fatal("seed should not run after migration failure")
	}
}

type fakeSetupStore struct {
	migrated   bool
	seeded     bool
	migrateErr error
	seedErr    error
}

func (s *fakeSetupStore) ApplyMigrations(context.Context) error {
	s.migrated = true
	return s.migrateErr
}

func (s *fakeSetupStore) SeedFromConfig(context.Context, config.Config) error {
	s.seeded = true
	return s.seedErr
}
