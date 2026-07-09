#!/usr/bin/env python3
"""Validate sanitized public API v1 fixtures."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

FORBIDDEN_KEYS = {
    "api_key",
    "raw_xml",
    "media_base64",
    "raw_provider",
    "payload_json",
    "cookie",
    "token",
    "secret",
    "password",
    "credential",
    "session",
    "session_key",
    "auth",
    "auth_key",
    "authorization",
}
REAL_ID_PATTERNS = [
    re.compile(r"wxid_[A-Za-z0-9_-]+"),
    re.compile(r"[A-Za-z0-9_-]+@chatroom"),
]
FIXTURE_SET = "public-api-v1"
PROTOCOL_VERSION = "v1"
REQUIRED_FIXTURE_IDS = {
    "public-api-v1.text",
    "public-api-v1.image",
    "public-api-v1.video",
    "public-api-v1.voice",
    "public-api-v1.file",
    "public-api-v1.emoji",
    "public-api-v1.location",
    "public-api-v1.quote",
    "public-api-v1.link",
    "public-api-v1.mini-program",
    "public-api-v1.chat-history",
    "public-api-v1.payment",
    "public-api-v1.system",
    "public-api-v1.unknown",
}
LEGACY_INBOUND_ENVELOPE_FIELDS = {
    "appmsg_subtype",
    "appmsg_title",
    "appmsg_description",
    "appmsg_url",
    "appmsg_file_name",
    "appmsg_app_name",
    "media_kind",
    "media_mime",
    "media_name",
    "media_url",
    "media_size",
    "location_latitude",
    "location_longitude",
    "location_scale",
    "location_label",
    "location_poiname",
    "location_info_url",
    "location_poi_id",
}
PRE_ACK_OUTBOX_STATUSES = {"pending", "leased", "queued"}


def fail(message: str) -> None:
    raise SystemExit(message)


def walk(value: Any, path: str = "$") -> None:
    if isinstance(value, dict):
        for key, item in value.items():
            if key.lower() in FORBIDDEN_KEYS:
                fail(f"forbidden key {path}.{key}")
            walk(item, f"{path}.{key}")
    elif isinstance(value, list):
        for idx, item in enumerate(value):
            walk(item, f"{path}[{idx}]")
    elif isinstance(value, str):
        for pattern in REAL_ID_PATTERNS:
            if pattern.search(value):
                fail(f"possible real identifier at {path}: {value[:60]}")
        if len(value) > 256 and re.fullmatch(r"[A-Za-z0-9+/=_-]+", value):
            fail(f"possible encoded secret or media payload at {path}")


def validate_fixture(path: Path) -> str:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{path}: root must be object")
    walk(data)
    fixture_id = data.get("fixture_id")
    if not isinstance(fixture_id, str) or not fixture_id.startswith(FIXTURE_SET + "."):
        fail(f"{path}: fixture_id must start with {FIXTURE_SET}.")
    if fixture_id != path.stem:
        fail(f"{path}: fixture_id must match file name stem")
    if data.get("protocol_version") != PROTOCOL_VERSION:
        fail(f"{path}: protocol_version must be {PROTOCOL_VERSION}")
    kind = data.get("kind")
    if not isinstance(kind, str) or not kind:
        fail(f"{path}: kind is required")
    inbound = data.get("inbound_envelope")
    if not isinstance(inbound, dict):
        fail(f"{path}: inbound_envelope is required")
    for field in ["id", "device", "direction", "kind", "message_type", "chat_id", "chat_kind"]:
        if field not in inbound:
            fail(f"{path}: inbound_envelope.{field} is required")
    legacy_fields = sorted(set(inbound) & LEGACY_INBOUND_ENVELOPE_FIELDS)
    if legacy_fields:
        fail(f"{path}: inbound_envelope must use nested v1 fields, found legacy field(s): {', '.join(legacy_fields)}")
    outbound_supported = bool(data.get("outbound_supported"))
    if outbound_supported:
        request = data.get("outbound_request")
        if not isinstance(request, dict) or not str(request.get("endpoint", "")).startswith("/api/v1/messages/"):
            fail(f"{path}: outbound_request.endpoint must be /api/v1/messages/...")
        if not isinstance(request.get("json"), dict) or "wx_ids" not in request["json"]:
            fail(f"{path}: outbound_request.json.wx_ids is required")
        response = data.get("send_response")
        if not isinstance(response, dict) or response.get("protocol_version") != "v1" or "outbox_id" not in response:
            fail(f"{path}: send_response must include protocol_version and outbox_id")
        response_outbox = response.get("outbox")
        if response.get("chat_record_id") == response.get("outbox_id"):
            fail(f"{path}: send_response.chat_record_id must not mirror outbox_id")
        if isinstance(response_outbox, dict):
            response_status = response_outbox.get("status")
            if response_outbox.get("chat_record_id") == response.get("outbox_id"):
                fail(f"{path}: send_response.outbox.chat_record_id must not mirror outbox_id")
            if response_status in PRE_ACK_OUTBOX_STATUSES:
                if "chat_record_id" in response:
                    fail(f"{path}: send_response.chat_record_id must be omitted until module ACK")
                if "chat_record_id" in response_outbox:
                    fail(f"{path}: send_response.outbox.chat_record_id must be omitted until module ACK")
        terminal = data.get("outbox_terminal")
        if not isinstance(terminal, dict) or (terminal.get("outbox") or {}).get("status") not in {"sent", "failed"}:
            fail(f"{path}: outbox_terminal.outbox.status must be sent or failed")
        terminal_outbox = terminal.get("outbox") or {}
        if terminal_outbox.get("status") == "sent" and not isinstance(terminal_outbox.get("chat_record_id"), int):
            fail(f"{path}: sent outbox_terminal.outbox.chat_record_id is required")
    return fixture_id


def validate_index(root: Path, fixture_files: list[Path]) -> None:
    index_path = root / "index.json"
    if not index_path.is_file():
        fail(f"{root}: index.json is required")
    data = json.loads(index_path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{index_path}: root must be object")
    walk(data)
    if data.get("fixture_set") != FIXTURE_SET:
        fail(f"{index_path}: fixture_set must be {FIXTURE_SET}")
    if data.get("protocol_version") != PROTOCOL_VERSION:
        fail(f"{index_path}: protocol_version must be {PROTOCOL_VERSION}")
    listed = data.get("fixtures")
    if not isinstance(listed, list) or not all(isinstance(item, str) for item in listed):
        fail(f"{index_path}: fixtures must be a string list")
    if len(set(listed)) != len(listed):
        fail(f"{index_path}: fixtures must not contain duplicates")

    actual = sorted(path.name for path in fixture_files)
    missing_files = sorted(set(listed) - set(actual))
    unlisted_files = sorted(set(actual) - set(listed))
    if missing_files:
        fail(f"{index_path}: listed fixture file(s) do not exist: {', '.join(missing_files)}")
    if unlisted_files:
        fail(f"{index_path}: fixture file(s) missing from index: {', '.join(unlisted_files)}")


def validate_fixture_set(root: Path, required_fixture_ids: set[str] | None = None) -> list[str]:
    if not root.is_dir():
        fail(f"fixture dir does not exist: {root}")
    fixture_files = sorted(path for path in root.glob("*.json") if path.name != "index.json")
    validate_index(root, fixture_files)
    if not fixture_files:
        fail("no fixtures found")

    ids: list[str] = []
    seen: set[str] = set()
    for path in fixture_files:
        fixture_id = validate_fixture(path)
        if fixture_id in seen:
            fail(f"{path}: duplicate fixture_id {fixture_id}")
        seen.add(fixture_id)
        ids.append(fixture_id)
    required = REQUIRED_FIXTURE_IDS if required_fixture_ids is None else required_fixture_ids
    missing_ids = sorted(required - seen)
    if missing_ids:
        fail(f"{root}: missing required fixture id(s): {', '.join(missing_ids)}")
    return ids


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate public API v1 message fixtures.")
    parser.add_argument("fixture_dir", nargs="?", default="docs/message-fixtures/public-api-v1")
    args = parser.parse_args()
    root = Path(args.fixture_dir)
    ids = validate_fixture_set(root)
    print(json.dumps({"ok": True, "fixture_count": len(ids), "fixtures": ids}, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
