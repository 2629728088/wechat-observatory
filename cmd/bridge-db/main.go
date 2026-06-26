package main

import (
	"context"
	"encoding/json"
	"flag"
	"log"
	"os"
	"time"

	"wechat-observatory/internal/config"
	mysqlstore "wechat-observatory/internal/storage/mysql"
)

type summary struct {
	Migrated      bool   `json:"migrated,omitempty"`
	Seeded        bool   `json:"seeded,omitempty"`
	DefaultDevice string `json:"default_device,omitempty"`
	Devices       int    `json:"devices"`
	APIKeys       int    `json:"api_keys"`
	Ready         bool   `json:"ready"`
}

func main() {
	createDatabase := flag.Bool("create-database", false, "create the database named in BRIDGE_MYSQL_DSN before connecting to it")
	migrate := flag.Bool("migrate", false, "apply gateway MySQL schema")
	seed := flag.Bool("seed", false, "seed rows from BRIDGE_* env config")
	check := flag.Bool("check", false, "load snapshot and verify runtime readiness")
	flag.Parse()

	if !*createDatabase && !*migrate && !*seed && !*check {
		flag.Usage()
		os.Exit(2)
	}

	cfg, err := config.LoadFromEnv()
	if err != nil {
		log.Fatalf("load config: %v", err)
	}
	if !cfg.MySQL.Enabled() {
		log.Fatal("BRIDGE_MYSQL_DSN is required")
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if *createDatabase {
		if err := mysqlstore.EnsureDatabase(ctx, cfg.MySQL.DSN); err != nil {
			log.Fatalf("create database: %v", err)
		}
	}

	store, err := mysqlstore.Open(ctx, cfg.MySQL.DSN)
	if err != nil {
		log.Fatalf("open mysql: %v", err)
	}
	defer func() {
		if err := store.Close(); err != nil {
			log.Printf("close mysql: %v", err)
		}
	}()

	out := summary{}
	if *migrate {
		if err := store.ApplyMigrations(ctx); err != nil {
			log.Fatalf("migrate: %v", err)
		}
		out.Migrated = true
	}
	if *seed {
		if err := store.SeedFromConfig(ctx, cfg); err != nil {
			log.Fatalf("seed: %v", err)
		}
		out.Seeded = true
	}
	if *check {
		snapshot, err := store.LoadSnapshot(ctx)
		if err != nil {
			log.Fatalf("load snapshot: %v", err)
		}
		cfg.Devices = snapshot.Devices
		cfg.APIKeys = snapshot.APIKeys
		if err := cfg.EnsureRuntimeReady(); err != nil {
			log.Fatalf("runtime check: %v", err)
		}
		out.DefaultDevice = cfg.DefaultDevice
		out.Devices = len(snapshot.Devices)
		out.APIKeys = len(snapshot.APIKeys)
		out.Ready = true
	}
	if err := json.NewEncoder(os.Stdout).Encode(out); err != nil {
		log.Fatalf("write summary: %v", err)
	}
}
