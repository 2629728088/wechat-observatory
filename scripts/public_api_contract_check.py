#!/usr/bin/env python3
"""Public API v1 contract check for wechat-observatory.

Default mode is read-only. It validates the public protocol surface without
printing API keys, raw XML, media base64, sessions, cookies, tokens, auth
values, or passwords. Real sends only run with --confirm-send, an explicit
target id or resolved contact, and --target-name-exact.
"""

from __future__ import annotations

import argparse
import base64
import json
import mimetypes
import os
import re
import socket
import ssl
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zlib
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

FINAL_OUTBOX_STATUSES = {"sent", "failed"}
DEFAULT_SEND_KINDS = ["text"]
SAFE_BASIC_SEND_KINDS = ["text", "image", "file", "link", "location"]
SUPPORTED_SEND_KINDS = {
    "text",
    "image",
    "video",
    "voice",
    "file",
    "emoji",
    "location",
    "quote",
    "link",
    "mini-program",
    "chat-history",
}
SEND_PROFILES = {
    "custom": [],
    "text": ["text"],
    "safe-basic": SAFE_BASIC_SEND_KINDS,
    "all-basic": SAFE_BASIC_SEND_KINDS,
    "all": sorted(SUPPORTED_SEND_KINDS),
}
REQUIRED_CAPABILITY_ENDPOINTS = {
    "text": "/api/v1/messages/text",
    "image": "/api/v1/messages/image",
    "video": "/api/v1/messages/video",
    "voice": "/api/v1/messages/voice",
    "file": "/api/v1/messages/file",
    "emoji": "/api/v1/messages/emoji",
    "location": "/api/v1/messages/location",
    "quote": "/api/v1/messages/quote",
    "link": "/api/v1/messages/link",
    "mini-program": "/api/v1/messages/mini-program",
    "chat-history": "/api/v1/messages/chat-history",
}
REQUIRED_OPENAPI_PATHS = [
    "/api/v1/capabilities",
    "/api/v1/messages",
    *REQUIRED_CAPABILITY_ENDPOINTS.values(),
    "/api/v1/outbox/{id}",
    "/api/v1/ws",
    "/api/media/{media_path}",
]
REQUIRED_ERROR_CODES = ["owner_wxid_unbound", "media_forbidden", "cursor_conflict"]
REQUIRED_DOC_LINKS = [
    "/docs/openapi.json",
    "adapter-quickstart-v1.md",
    "capability-evidence-v1.md",
    "public-api-message-samples-v1.md",
    "public-api-python-client-v1.md",
]
ALLOWED_INBOUND_STATUSES = {"stable", "basic", "structured", "parse_only", "preserved"}
ALLOWED_OUTBOUND_STATUSES = {"stable", "implemented", "sample_only", "source_forward_stable", "source_forward_only", "private_verified", "unsupported", "non_goal"}
ALLOWED_VERIFICATION_LEVELS = {"user_confirmed", "db_verified", "sample_only", "parse_only"}
NON_SENDABLE_OUTBOUND_STATUSES = {"unsupported", "non_goal"}
MINIMAL_AMR_BASE64 = base64.b64encode(b"#!AMR\n" + bytes([0x7C]) * 50).decode("ascii")
DEFAULT_MESSAGE_LIMIT = 50
MAX_MESSAGE_LIMIT = 500
DEFAULT_MESSAGE_PAGES = 1
MAX_MESSAGE_PAGES = 20
PUBLIC_FIXTURE_COVERAGE_REQUIREMENTS = {
    "public-api-v1.text": ("kind", "text"),
    "public-api-v1.image": ("media_kind", "image"),
    "public-api-v1.video": ("media_kind", "video"),
    "public-api-v1.voice": ("media_kind", "voice"),
    "public-api-v1.file": ("media_kind", "file"),
    "public-api-v1.emoji": ("media_kind", "emoji"),
    "public-api-v1.location": ("location", "present"),
    "public-api-v1.quote": ("appmsg_subtype", "quote"),
    "public-api-v1.link": ("appmsg_subtype", "link"),
    "public-api-v1.mini-program": ("appmsg_subtype", "mini_program"),
    "public-api-v1.chat-history": ("appmsg_subtype", "chat_history"),
    "public-api-v1.payment": ("kind", "payment"),
    "public-api-v1.system": ("kind", "system"),
    "public-api-v1.unknown": ("kind", "unknown"),
}


def png_chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack("!I", len(data)) + kind + data + struct.pack("!I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def build_contract_check_png_base64() -> str:
    width, height = 320, 180
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            if x < 14 or y < 14 or x >= width - 14 or y >= height - 14:
                rgb = (22, 122, 100)
            elif 118 <= x <= 202 and 68 <= y <= 112:
                rgb = (49, 91, 130)
            elif ((x // 32) + (y // 32)) % 2 == 0:
                rgb = (230, 243, 237)
            else:
                rgb = (231, 238, 246)
            raw.extend(rgb)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", struct.pack("!IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + png_chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + png_chunk(b"IEND", b"")
    )
    return base64.b64encode(png).decode("ascii")


CONTRACT_CHECK_PNG_BASE64 = build_contract_check_png_base64()
PUBLIC_SAFE_LIVE_FIXTURE_IDS = set(PUBLIC_FIXTURE_COVERAGE_REQUIREMENTS) - {"public-api-v1.payment"}
PAYMENT_MESSAGE_TYPE_LABELS = {
    "419430449": "message_type.transfer",
    "436207665": "message_type.red_packet",
}
PAYMENT_APPMSG_TYPE_LABELS = {
    "2000": "appmsg_type.transfer",
    "2001": "appmsg_type.red_packet",
}


class ContractError(RuntimeError):
    def __init__(self, message: str, details: Optional[Dict[str, Any]] = None) -> None:
        super().__init__(message)
        self.details = details or {}


class APIError(RuntimeError):
    def __init__(self, status: int, body: str) -> None:
        super().__init__(f"HTTP {status}: {redact(body)[:300]}")
        self.status = status
        self.body = body


def redact(value: Any) -> str:
    if not isinstance(value, str):
        return ""
    out = value
    out = re.sub(r"api_key=[^&\s\"]+", "api_key=<redacted>", out, flags=re.IGNORECASE)
    out = re.sub(r"(X-Bridge-API-Key:?)\s*[^\s\"]+", r"\1 <redacted>", out, flags=re.IGNORECASE)
    out = re.sub(r"(Authorization:?)\s*[^\n\"]+", r"\1 <redacted>", out, flags=re.IGNORECASE)
    out = re.sub(r"(api[_-]?key|password|token|secret|cookie|credential|session|auth)=([^&\s\"]+)", r"\1=<redacted>", out, flags=re.IGNORECASE)
    out = re.sub(r'"(api[_-]?key|password|token|secret|cookie|credential|session|auth)"\s*:\s*"[^"]+"', r'"\1":"<redacted>"', out, flags=re.IGNORECASE)
    return out


def request_json(
    base_url: str,
    api_key: str,
    method: str,
    path: str,
    payload: Optional[Dict[str, Any]] = None,
    timeout: int = 20,
    allow_statuses: Sequence[int] = (200,),
) -> Dict[str, Any]:
    status, raw = request_bytes(base_url, api_key, method, path, payload, timeout, allow_statuses)
    try:
        parsed = json.loads(raw.decode("utf-8") or "{}")
    except json.JSONDecodeError as exc:
        raise ContractError(f"response is not valid JSON for {method} {path}: HTTP {status}") from exc
    if not isinstance(parsed, dict):
        raise ContractError(f"response is not a JSON object for {method} {path}")
    return parsed


def request_bytes(
    base_url: str,
    api_key: str,
    method: str,
    path: str,
    payload: Optional[Dict[str, Any]] = None,
    timeout: int = 20,
    allow_statuses: Sequence[int] = (200,),
) -> Tuple[int, bytes]:
    url = absolute_url(base_url, path)
    data = None
    headers = {"Accept": "application/json"}
    if api_key:
        headers["X-Bridge-API-Key"] = api_key
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method.upper())
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            status = resp.status
            raw = resp.read()
    except urllib.error.HTTPError as exc:
        status = exc.code
        raw = exc.read()
    if status not in set(allow_statuses):
        raise APIError(status, raw.decode("utf-8", errors="replace"))
    return status, raw


def absolute_url(base_url: str, path: str) -> str:
    if path.startswith("http://") or path.startswith("https://"):
        return path
    return base_url.rstrip("/") + "/" + path.lstrip("/")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ContractError(message)


def normalize_message_limit(value: int) -> int:
    require(1 <= value <= MAX_MESSAGE_LIMIT, f"message limit must be between 1 and {MAX_MESSAGE_LIMIT}")
    return value


def normalize_message_pages(value: int) -> int:
    require(1 <= value <= MAX_MESSAGE_PAGES, f"message pages must be between 1 and {MAX_MESSAGE_PAGES}")
    return value


def public_summary_message(message: Dict[str, Any]) -> Dict[str, Any]:
    media = message.get("media") or []
    appmsg = message.get("appmsg") if isinstance(message.get("appmsg"), dict) else {}
    return {
        "id_present": bool(message.get("id") or message.get("event_id")),
        "kind": message.get("kind"),
        "direction": message.get("direction"),
        "chat_kind": message.get("chat_kind"),
        "message_type": message.get("message_type"),
        "appmsg_type": appmsg.get("type"),
        "text_len": len(message.get("text") or "") if isinstance(message.get("text"), str) else 0,
        "media_count": len(media) if isinstance(media, list) else 0,
        "appmsg_present": bool(appmsg),
        "location_present": isinstance(message.get("location"), dict),
        "unsupported_count": len(message.get("unsupported") or []) if isinstance(message.get("unsupported"), list) else 0,
    }


def increment_count(counts: Dict[str, int], value: Any) -> None:
    key = str(value or "").strip()
    if key:
        counts[key] = counts.get(key, 0) + 1


def sorted_counts(counts: Dict[str, int]) -> Dict[str, int]:
    return {key: counts[key] for key in sorted(counts)}


def increment_payment_candidate_count(counts: Dict[str, int], labels: Dict[str, str], value: Any) -> None:
    label = labels.get(str(value or "").strip())
    if label:
        counts[label] = counts.get(label, 0) + 1


def parse_required_coverage_values(raw: str) -> set[str]:
    return {item.strip().lower().replace("-", "_") for item in raw.split(",") if item.strip()}


def normalize_fixture_id(value: str) -> str:
    text = value.strip().lower().replace("_", "-")
    if not text:
        return ""
    if not text.startswith("public-api-v1."):
        text = "public-api-v1." + text
    if text not in PUBLIC_FIXTURE_COVERAGE_REQUIREMENTS:
        raise ContractError("unknown required fixture id: " + text)
    return text


def parse_required_fixture_ids(raw: str) -> set[str]:
    fixture_ids: set[str] = set()
    for item in raw.split(","):
        text = item.strip().lower()
        if not text:
            continue
        alias = text.replace("_", "-")
        if text == "all":
            fixture_ids.update(PUBLIC_FIXTURE_COVERAGE_REQUIREMENTS)
            continue
        if alias in {"all-safe-live", "all-safe"}:
            fixture_ids.update(PUBLIC_SAFE_LIVE_FIXTURE_IDS)
            continue
        fixture_ids.add(normalize_fixture_id(item))
    return fixture_ids


def summarize_fixture_coverage(
    kind_counts: Dict[str, int],
    media_kind_counts: Dict[str, int],
    appmsg_subtype_counts: Dict[str, int],
    location_message_count: int,
) -> Dict[str, Any]:
    covered: list[str] = []
    missing: list[str] = []
    for fixture_id, (source, value) in sorted(PUBLIC_FIXTURE_COVERAGE_REQUIREMENTS.items()):
        present = False
        if source == "kind":
            present = kind_counts.get(value, 0) > 0
        elif source == "media_kind":
            present = media_kind_counts.get(value, 0) > 0
        elif source == "appmsg_subtype":
            present = appmsg_subtype_counts.get(value, 0) > 0
        elif source == "location":
            present = location_message_count > 0
        if present:
            covered.append(fixture_id)
        else:
            missing.append(fixture_id)
    return {
        "covered": covered,
        "missing": missing,
        "covered_count": len(covered),
        "missing_count": len(missing),
        "fixture_count": len(PUBLIC_FIXTURE_COVERAGE_REQUIREMENTS),
    }


def require_fixture_coverage(fixture_coverage: Dict[str, Any], required_fixture_ids: set[str]) -> None:
    covered = set(fixture_coverage.get("covered") or [])
    missing = sorted(required_fixture_ids - covered)
    if missing:
        diagnostics = []
        if "public-api-v1.payment" in missing and fixture_coverage.get("payment_candidate_total", 0) > 0:
            diagnostics.append("payment candidates found by message_type or appmsg.type, but no public kind=payment was returned")
        raise ContractError(
            "missing required fixture coverage: " + ", ".join(missing),
            {
                "fixture_coverage": fixture_coverage,
                "missing_required_fixtures": missing,
                "required_fixture_ids": sorted(required_fixture_ids),
                "diagnostics": diagnostics,
            },
        )


def summarize_message_coverage(
    messages: Sequence[Any],
    required_kinds: set[str] | None = None,
    required_media_kinds: set[str] | None = None,
    required_appmsg_subtypes: set[str] | None = None,
    required_fixture_ids: set[str] | None = None,
) -> Dict[str, Any]:
    required_fields = ["kind", "direction", "chat_id", "chat_kind"]
    kind_counts: Dict[str, int] = {}
    direction_counts: Dict[str, int] = {}
    chat_kind_counts: Dict[str, int] = {}
    media_kind_counts: Dict[str, int] = {}
    appmsg_subtype_counts: Dict[str, int] = {}
    message_type_counts: Dict[str, int] = {}
    payment_candidate_counts: Dict[str, int] = {}
    media_message_count = 0
    appmsg_message_count = 0
    location_message_count = 0
    unsupported_total = 0
    evidence_total = 0

    for index, message in enumerate(messages):
        require(isinstance(message, dict), f"message[{index}] must be an object")
        for field in required_fields:
            require(field in message, f"message[{index}] missing {field}")
        increment_count(kind_counts, message.get("kind"))
        increment_count(direction_counts, message.get("direction"))
        increment_count(chat_kind_counts, message.get("chat_kind"))
        increment_count(message_type_counts, message.get("message_type"))
        increment_payment_candidate_count(payment_candidate_counts, PAYMENT_MESSAGE_TYPE_LABELS, message.get("message_type"))
        media = message.get("media") or []
        require(isinstance(media, list), f"message[{index}].media must be a list when present")
        if media:
            media_message_count += 1
        for media_item in media:
            require(isinstance(media_item, dict), f"message[{index}].media item must be an object")
            increment_count(media_kind_counts, media_item.get("kind") or media_item.get("media_kind"))
        appmsg = message.get("appmsg") if isinstance(message.get("appmsg"), dict) else {}
        if appmsg:
            appmsg_message_count += 1
            increment_count(appmsg_subtype_counts, appmsg.get("subtype"))
            increment_payment_candidate_count(payment_candidate_counts, PAYMENT_APPMSG_TYPE_LABELS, appmsg.get("type"))
        if isinstance(message.get("location"), dict):
            location_message_count += 1
        if isinstance(message.get("unsupported"), list):
            unsupported_total += len(message["unsupported"])
        if isinstance(message.get("evidence"), list):
            evidence_total += len(message["evidence"])

    required = required_kinds or set()
    missing_required = sorted(required - set(kind_counts))
    require(not missing_required, "missing required message kind(s): " + ", ".join(missing_required))
    required_media = required_media_kinds or set()
    missing_media = sorted(required_media - set(media_kind_counts))
    require(not missing_media, "missing required media kind(s): " + ", ".join(missing_media))
    required_appmsg = required_appmsg_subtypes or set()
    missing_appmsg = sorted(required_appmsg - set(appmsg_subtype_counts))
    require(not missing_appmsg, "missing required appmsg subtype(s): " + ", ".join(missing_appmsg))
    fixture_coverage = summarize_fixture_coverage(
        kind_counts,
        media_kind_counts,
        appmsg_subtype_counts,
        location_message_count,
    )
    fixture_coverage["payment_candidate_counts"] = sorted_counts(payment_candidate_counts)
    fixture_coverage["payment_candidate_total"] = sum(payment_candidate_counts.values())
    required_fixtures = required_fixture_ids or set()
    require_fixture_coverage(fixture_coverage, required_fixtures)
    return {
        "kind_counts": sorted_counts(kind_counts),
        "direction_counts": sorted_counts(direction_counts),
        "chat_kind_counts": sorted_counts(chat_kind_counts),
        "message_type_counts": sorted_counts(message_type_counts),
        "payment_candidate_counts": sorted_counts(payment_candidate_counts),
        "media_kind_counts": sorted_counts(media_kind_counts),
        "appmsg_subtype_counts": sorted_counts(appmsg_subtype_counts),
        "media_message_count": media_message_count,
        "appmsg_message_count": appmsg_message_count,
        "location_message_count": location_message_count,
        "unsupported_total": unsupported_total,
        "evidence_total": evidence_total,
        "fixture_coverage": fixture_coverage,
        "required_fixtures_present": sorted(required_fixtures),
        "required_kinds_present": sorted(required),
        "required_media_kinds_present": sorted(required_media),
        "required_appmsg_subtypes_present": sorted(required_appmsg),
    }


def contact_display_name(item: Dict[str, Any]) -> str:
    return (item.get("remark") or item.get("nickname") or item.get("display_name") or "").strip()


def contact_is_room(item: Dict[str, Any]) -> bool:
    wxid = item.get("wxid") or ""
    return bool(item.get("chatroom")) or wxid.endswith("@chatroom")


def contacts(data: Dict[str, Any]) -> Iterable[Dict[str, Any]]:
    value = data.get("contacts") or []
    return (item for item in value if isinstance(item, dict)) if isinstance(value, list) else []


def ensure_target_kind(wxid: str, target_kind: str) -> None:
    is_room = wxid.endswith("@chatroom")
    if target_kind == "room" and not is_room:
        raise ContractError("--target-kind room requires a room id ending with @chatroom")
    if target_kind == "direct" and is_room:
        raise ContractError("--target-kind direct cannot use a room id ending with @chatroom")


def find_target(base_url: str, api_key: str, query: str, exact_name: str, target_kind: str) -> Dict[str, Any]:
    path = "/api/v1/contacts?limit=50&q=" + urllib.parse.quote(query)
    data = request_json(base_url, api_key, "GET", path)
    matches = []
    for item in contacts(data):
        name = contact_display_name(item)
        is_room = contact_is_room(item)
        if item.get("deleted") or name != exact_name:
            continue
        if target_kind == "room" and not is_room:
            continue
        if target_kind == "direct" and is_room:
            continue
        matches.append(item)
    if len(matches) != 1:
        raise ContractError(f"expected exactly one {target_kind} target named {exact_name!r}; found {len(matches)}")
    return matches[0]


def find_target_by_wxid(base_url: str, api_key: str, wxid: str, target_kind: str) -> Optional[Dict[str, Any]]:
    path = "/api/v1/contacts?limit=20&q=" + urllib.parse.quote(wxid)
    data = request_json(base_url, api_key, "GET", path)
    matches = []
    for item in contacts(data):
        if item.get("deleted") or item.get("wxid") != wxid:
            continue
        is_room = contact_is_room(item)
        if target_kind == "room" and not is_room:
            continue
        if target_kind == "direct" and is_room:
            continue
        matches.append(item)
    if len(matches) == 1:
        matches[0]["_target_source"] = "contacts_by_wxid"
        return matches[0]
    return None


def resolve_target(args: argparse.Namespace) -> Dict[str, Any]:
    target_wxid = args.target_wxid.strip()
    if target_wxid:
        ensure_target_kind(target_wxid, args.target_kind)
        contact = find_target_by_wxid(args.base_url, args.api_key, target_wxid, args.target_kind)
        if contact is not None:
            return contact
        return {
            "wxid": target_wxid,
            "nickname": args.target_name.strip(),
            "chatroom": target_wxid.endswith("@chatroom"),
            "_target_source": "wxid",
        }
    if not args.target_query:
        raise ContractError("target requires --target-wxid or --target-query")
    target = find_target(args.base_url, args.api_key, args.target_query, args.target_name, args.target_kind)
    target["_target_source"] = "contacts"
    return target


def require_target_safety(args: argparse.Namespace, target: Dict[str, Any]) -> None:
    if args.require_target_contact and target.get("_target_source") == "wxid":
        raise ContractError("target wxid was not found in contacts; remove --require-target-contact or sync contacts first")
    exact = args.target_name_exact.strip()
    if exact:
        display_name = contact_display_name(target)
        if display_name != exact:
            raise ContractError(f"target display name must equal {exact!r}; got {display_name!r}")
    expected = args.target_name_contains.strip()
    if expected:
        display_name = contact_display_name(target)
        if expected not in display_name:
            raise ContractError(f"target display name must contain {expected!r}; got {display_name!r}")


def poll_outbox(base_url: str, api_key: str, outbox_id: int, timeout: int) -> Dict[str, Any]:
    deadline = time.monotonic() + timeout
    last: Dict[str, Any] = {}
    while True:
        data = request_json(base_url, api_key, "GET", f"/api/v1/outbox/{outbox_id}")
        outbox = data.get("outbox") or {}
        if isinstance(outbox, dict):
            last = outbox
            if outbox.get("status") in FINAL_OUTBOX_STATUSES:
                return outbox
        if time.monotonic() >= deadline:
            return last
        time.sleep(2)


def check_capabilities(base_url: str, api_key: str) -> Dict[str, Any]:
    capabilities = request_json(base_url, api_key, "GET", "/api/v1/capabilities")
    require(capabilities.get("ok") is True, "capabilities.ok must be true")
    require(capabilities.get("protocol_version") == "v1", "protocol_version must be v1")
    caps = capabilities.get("capabilities") or []
    require(isinstance(caps, list) and len(caps) >= 10, "capabilities list is unexpectedly small")
    by_endpoint = {item.get("send_endpoint"): item for item in caps if isinstance(item, dict) and item.get("send_endpoint")}
    missing = [endpoint for endpoint in REQUIRED_CAPABILITY_ENDPOINTS.values() if endpoint not in by_endpoint]
    require(not missing, "capabilities missing send endpoints: " + ", ".join(missing))
    non_sendable_count = 0
    for item in caps:
        require(isinstance(item, dict), "capability item must be an object")
        title = str(item.get("title") or item.get("kind") or "<unknown>")
        inbound_status = item.get("inbound_status")
        outbound_status = item.get("outbound_status")
        verification = item.get("verification")
        require(inbound_status in ALLOWED_INBOUND_STATUSES, f"capability {title} has invalid inbound_status {inbound_status!r}")
        require(outbound_status in ALLOWED_OUTBOUND_STATUSES, f"capability {title} has invalid outbound_status {outbound_status!r}")
        require(verification in ALLOWED_VERIFICATION_LEVELS, f"capability {title} has invalid verification {verification!r}")
        endpoint = item.get("send_endpoint") or ""
        required_fields = item.get("required_fields") or []
        require(isinstance(required_fields, list), f"capability {title} required_fields must be a list")
        if endpoint:
            require(endpoint.startswith("/api/v1/messages/"), f"capability {title} send_endpoint must be under /api/v1/messages")
            require("wx_ids" in required_fields, f"capability {title} send endpoint must require wx_ids")
            require(item.get("send_kind"), f"capability {title} with send_endpoint must include send_kind")
            require(outbound_status not in NON_SENDABLE_OUTBOUND_STATUSES, f"capability {title} cannot expose send_endpoint while outbound_status is {outbound_status}")
        else:
            non_sendable_count += 1
            require(outbound_status in NON_SENDABLE_OUTBOUND_STATUSES, f"capability {title} without send_endpoint must be unsupported or non_goal")
            unsupported = item.get("unsupported") or []
            notes = item.get("notes") or []
            require(unsupported or notes or outbound_status == "non_goal", f"capability {title} without send_endpoint must explain unsupported status")
    transports = capabilities.get("transports") or []
    require(isinstance(transports, list) and any((item or {}).get("endpoint") == "/api/v1/ws" for item in transports if isinstance(item, dict)), "capabilities must advertise /api/v1/ws")
    return {
        "ok": True,
        "capability_count": len(caps),
        "required_send_endpoints_present": True,
        "capability_statuses_checked": True,
        "non_sendable_count": non_sendable_count,
        "websocket_advertised": True,
    }


def check_messages(
    base_url: str,
    api_key: str,
    limit: int = DEFAULT_MESSAGE_LIMIT,
    pages: int = DEFAULT_MESSAGE_PAGES,
    required_kinds: set[str] | None = None,
    required_media_kinds: set[str] | None = None,
    required_appmsg_subtypes: set[str] | None = None,
    required_fixture_ids: set[str] | None = None,
) -> Dict[str, Any]:
    limit = normalize_message_limit(limit)
    pages = normalize_message_pages(pages)
    msg_list, page_summaries, has_more = collect_message_pages(base_url, api_key, limit, pages)
    try:
        coverage = summarize_message_coverage(
            msg_list,
            required_kinds,
            required_media_kinds,
            required_appmsg_subtypes,
            required_fixture_ids,
        )
    except ContractError as exc:
        if exc.details:
            exc.details.setdefault("message_count", len(msg_list))
            exc.details.setdefault("message_limit", limit)
            exc.details.setdefault("message_pages_requested", pages)
            exc.details.setdefault("message_pages_scanned", len(page_summaries))
            exc.details.setdefault("message_has_more", has_more)
            exc.details.setdefault("message_page_summaries", page_summaries)
        raise
    conflict = request_json(base_url, api_key, "GET", "/api/v1/messages?after_id=1&before_id=2", allow_statuses=(400,))
    require(conflict.get("code") == "cursor_conflict", "after_id+before_id must return cursor_conflict")
    return {
        "ok": True,
        "message_count": len(msg_list),
        "message_limit": limit,
        "message_pages_requested": pages,
        "message_pages_scanned": len(page_summaries),
        "message_has_more": has_more,
        "message_page_summaries": page_summaries,
        "sample_message": public_summary_message(msg_list[0]) if msg_list else None,
        "coverage": coverage,
        "cursor_conflict_checked": True,
    }


def collect_message_pages(base_url: str, api_key: str, limit: int, pages: int) -> Tuple[list[Any], list[Dict[str, Any]], bool]:
    messages: list[Any] = []
    summaries: list[Dict[str, Any]] = []
    path = "/api/v1/messages?" + urllib.parse.urlencode({"limit": str(limit)})
    has_more = False
    for page_index in range(pages):
        page = request_json(base_url, api_key, "GET", path)
        require(page.get("ok") is True, "messages.ok must be true")
        require(page.get("protocol_version") == "v1", "messages protocol_version must be v1")
        require(page.get("cursor_field") == "id", "messages cursor_field must be id")
        page_messages = page.get("messages") or []
        require(isinstance(page_messages, list), "messages must be a list")
        messages.extend(page_messages)
        has_more = bool(page.get("has_more"))
        cursor_param = str(page.get("next_cursor_param") or "")
        next_cursor = page.get("next_cursor")
        summaries.append(
            {
                "page": page_index + 1,
                "message_count": len(page_messages),
                "has_more": has_more,
                "next_cursor_param": cursor_param if has_more else "",
                "next_cursor": next_cursor if has_more else 0,
            }
        )
        if not has_more or page_index + 1 >= pages:
            break
        require(cursor_param in {"before_id", "after_id"}, "messages next_cursor_param must be before_id or after_id")
        require(isinstance(next_cursor, int) and next_cursor > 0, "messages next_cursor must be a positive integer")
        path = "/api/v1/messages?" + urllib.parse.urlencode({"limit": str(limit), cursor_param: str(next_cursor)})
    return messages, summaries, has_more


def check_modules(base_url: str, api_key: str) -> Dict[str, Any]:
    modules = request_json(base_url, api_key, "GET", "/api/v1/modules/status")
    module_list = modules.get("modules") or []
    require(isinstance(module_list, list), "modules must be a list")
    return {
        "ok": True,
        "module_count": len(module_list),
        "module_ready_count": sum(1 for item in module_list if isinstance(item, dict) and item.get("runtime_status") == "ready"),
    }


def check_docs(base_url: str) -> Dict[str, Any]:
    _, raw = request_bytes(base_url, "", "GET", "/docs/openapi.json")
    spec = json.loads(raw.decode("utf-8"))
    paths = spec.get("paths") or {}
    missing = [path for path in REQUIRED_OPENAPI_PATHS if path not in paths]
    require(not missing, "OpenAPI missing paths: " + ", ".join(missing))
    spec_text = json.dumps(spec, ensure_ascii=False)
    missing_codes = [code for code in REQUIRED_ERROR_CODES if code not in spec_text]
    require(not missing_codes, "OpenAPI missing error codes: " + ", ".join(missing_codes))
    _, html = request_bytes(base_url, "", "GET", "/docs")
    html_text = html.decode("utf-8", errors="replace")
    missing_links = [link for link in REQUIRED_DOC_LINKS if link not in html_text]
    require(not missing_links, "/docs missing required link(s): " + ", ".join(missing_links))
    return {"ok": True, "openapi_path_count": len(paths), "required_errors_present": True}


def find_first_media_url(base_url: str, api_key: str) -> Optional[str]:
    page = request_json(base_url, api_key, "GET", "/api/v1/messages?limit=100")
    for message in page.get("messages") or []:
        if not isinstance(message, dict):
            continue
        for media in message.get("media") or []:
            if isinstance(media, dict) and isinstance(media.get("url"), str) and media["url"]:
                return media["url"]
    return None


def check_media(base_url: str, api_key: str, require_device_scope: bool) -> Dict[str, Any]:
    summary: Dict[str, Any] = {"ok": True, "sample_media_found": False, "sample_media_read": "not_checked"}
    media_url = find_first_media_url(base_url, api_key)
    if media_url:
        summary["sample_media_found"] = True
        try:
            status, blob = request_bytes(base_url, api_key, "GET", media_url, allow_statuses=(200, 404))
            summary["sample_media_read"] = "ok" if status == 200 and len(blob) > 0 else "not_found"
        except APIError as exc:
            summary["sample_media_read"] = f"http_{exc.status}"
    forbidden = request_json(base_url, api_key, "GET", "/api/media/__contract_probe__/not-owned.bin", allow_statuses=(403, 404))
    code = forbidden.get("code")
    summary["forbidden_probe_code"] = code
    if require_device_scope:
        require(code == "media_forbidden", "device-scoped API key must reject media outside its device path")
    return summary


def ws_path_with_query(path: str, query: Dict[str, str]) -> str:
    encoded = urllib.parse.urlencode({k: v for k, v in query.items() if v != ""})
    return path + (("?" + encoded) if encoded else "")


def websocket_contract_check(base_url: str, api_key: str, timeout: int) -> Dict[str, Any]:
    sock, pending = ws_connect(base_url, "/api/v1/ws?replay=1", api_key, timeout)
    try:
        hello = ws_recv_json(sock, timeout, "hello", pending)
        require(hello.get("type") == "hello" and hello.get("ok") is True, "WebSocket must send hello")
        replay = ws_recv_until(sock, timeout, {"replay"}, "initial replay", pending)
        replay_events = replay.get("events") or []
        require(replay.get("type") == "replay" and isinstance(replay_events, list), "WebSocket replay must include events list or omit it when empty")
        ws_send_json(sock, {"type": "ping"})
        pong = ws_recv_until(sock, timeout, {"pong"}, "pong", pending)
        require(pong.get("type") == "pong" and pong.get("ok") is True, "WebSocket ping must return pong")
        ws_send_json(sock, {"type": "replay", "limit": 1})
        replay2 = ws_recv_until(sock, timeout, {"replay"}, "command replay", pending)
        replay2_events = replay2.get("events") or []
        require(isinstance(replay2_events, list), "WebSocket command replay must include events list or omit it when empty")
        return {
            "ok": True,
            "hello": True,
            "initial_replay_count": len(replay_events),
            "ping_pong": True,
            "command_replay_count": len(replay2_events),
        }
    finally:
        try:
            ws_send_close(sock)
        finally:
            sock.close()


def ws_connect(base_url: str, path: str, api_key: str, timeout: int) -> Tuple[socket.socket, bytearray]:
    parsed = urllib.parse.urlparse(base_url)
    scheme = parsed.scheme or "http"
    host = parsed.hostname or "127.0.0.1"
    port = parsed.port or (443 if scheme == "https" else 80)
    raw_sock = socket.create_connection((host, port), timeout=timeout)
    sock: socket.socket
    if scheme == "https":
        sock = ssl.create_default_context().wrap_socket(raw_sock, server_hostname=host)
    else:
        sock = raw_sock
    sock.settimeout(timeout)
    nonce = base64.b64encode(os.urandom(16)).decode("ascii")
    host_header = host if parsed.port is None else f"{host}:{port}"
    request = (
        f"GET {path} HTTP/1.1\r\n"
        f"Host: {host_header}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {nonce}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        f"X-Bridge-API-Key: {api_key}\r\n"
        "\r\n"
    ).encode("utf-8")
    sock.sendall(request)
    response = b""
    while b"\r\n\r\n" not in response:
        chunk = sock.recv(4096)
        if not chunk:
            break
        response += chunk
        if len(response) > 16384:
            break
    first_line = response.split(b"\r\n", 1)[0].decode("iso-8859-1", errors="replace")
    if " 101 " not in first_line:
        sock.close()
        raise ContractError("WebSocket upgrade failed: " + redact(first_line))
    _, _, pending = response.partition(b"\r\n\r\n")
    return sock, bytearray(pending)


def ws_send_json(sock: socket.socket, payload: Dict[str, Any]) -> None:
    ws_send_frame(sock, 0x1, json.dumps(payload, ensure_ascii=False).encode("utf-8"))


def ws_send_close(sock: socket.socket) -> None:
    ws_send_frame(sock, 0x8, b"")


def ws_send_pong(sock: socket.socket, payload: bytes) -> None:
    ws_send_frame(sock, 0xA, payload)


def ws_send_frame(sock: socket.socket, opcode: int, payload: bytes) -> None:
    first = 0x80 | opcode
    mask = os.urandom(4)
    length = len(payload)
    header = bytearray([first])
    if length < 126:
        header.append(0x80 | length)
    elif length < (1 << 16):
        header.append(0x80 | 126)
        header.extend(struct.pack("!H", length))
    else:
        header.append(0x80 | 127)
        header.extend(struct.pack("!Q", length))
    masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    sock.sendall(bytes(header) + mask + masked)


def ws_recv_json(sock: socket.socket, timeout: int, stage: str, pending: bytearray) -> Dict[str, Any]:
    deadline = time.monotonic() + timeout
    while True:
        remaining = max(0.1, deadline - time.monotonic())
        sock.settimeout(remaining)
        opcode, payload = ws_recv_frame(sock, stage, pending)
        if opcode == 0x1:
            parsed = json.loads(payload.decode("utf-8"))
            require(isinstance(parsed, dict), "WebSocket text frame must be JSON object")
            return parsed
        if opcode == 0x8:
            raise ContractError(f"WebSocket closed before expected {stage}")
        if opcode == 0x9:
            ws_send_pong(sock, payload)
        if time.monotonic() >= deadline:
            raise ContractError(f"WebSocket timed out waiting for {stage}")


def ws_recv_until(sock: socket.socket, timeout: int, wanted_types: set[str], stage: str, pending: bytearray) -> Dict[str, Any]:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        message = ws_recv_json(sock, max(1, int(deadline - time.monotonic())), stage, pending)
        if str(message.get("type")) in wanted_types:
            return message
    raise ContractError(f"WebSocket did not receive {stage} message type: " + ",".join(sorted(wanted_types)))


def ws_recv_frame(sock: socket.socket, stage: str, pending: bytearray) -> Tuple[int, bytes]:
    header = recv_exact(sock, 2, stage, pending)
    first, second = header[0], header[1]
    opcode = first & 0x0F
    masked = bool(second & 0x80)
    length = second & 0x7F
    if length == 126:
        length = struct.unpack("!H", recv_exact(sock, 2, stage, pending))[0]
    elif length == 127:
        length = struct.unpack("!Q", recv_exact(sock, 8, stage, pending))[0]
    mask = recv_exact(sock, 4, stage, pending) if masked else b""
    payload = recv_exact(sock, length, stage, pending) if length else b""
    if masked:
        payload = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    return opcode, payload


def recv_exact(sock: socket.socket, size: int, stage: str, pending: bytearray) -> bytes:
    chunks = bytearray()
    if pending:
        take = min(size, len(pending))
        chunks.extend(pending[:take])
        del pending[:take]
    while len(chunks) < size:
        try:
            chunk = sock.recv(size - len(chunks))
        except socket.timeout as exc:
            raise ContractError(f"WebSocket timed out while reading {stage} frame") from exc
        if not chunk:
            raise ContractError(f"WebSocket connection closed while reading {stage} frame")
        chunks.extend(chunk)
    return bytes(chunks)


def check_readonly(args: argparse.Namespace) -> Dict[str, Any]:
    summary: Dict[str, Any] = {"readonly_ok": False}
    summary["capabilities"] = check_capabilities(args.base_url, args.api_key)
    summary["messages"] = check_messages(
        args.base_url,
        args.api_key,
        args.message_limit,
        args.message_pages,
        parse_required_coverage_values(args.require_message_kind),
        parse_required_coverage_values(args.require_media_kind),
        parse_required_coverage_values(args.require_appmsg_subtype),
        parse_required_fixture_ids(args.require_fixture),
    )
    summary["modules"] = check_modules(args.base_url, args.api_key)
    if not args.skip_docs:
        summary["docs"] = check_docs(args.base_url)
    if not args.skip_media:
        summary["media"] = check_media(args.base_url, args.api_key, args.require_device_scoped_media)
    if not args.skip_ws:
        summary["websocket"] = websocket_contract_check(args.base_url, args.api_key, args.ws_timeout)
    summary["readonly_ok"] = True
    return summary


def check_required_live_state(args: argparse.Namespace, summary: Dict[str, Any]) -> Dict[str, Any]:
    required: Dict[str, Any] = {}
    if args.require_ready_module:
        ready_count = int((summary.get("modules") or {}).get("module_ready_count") or 0)
        require(ready_count > 0, "--require-ready-module needs at least one module with runtime_status=ready")
        required["ready_module"] = True
    return required


def summarize_target(target: Dict[str, Any], target_name: str) -> Dict[str, Any]:
    display_name = contact_display_name(target) or target_name.strip()
    return {
        "target_found": True,
        "target_source": target.get("_target_source") or "contacts",
        "target_name": display_name,
        "target_kind": "room" if contact_is_room(target) else "direct",
        "target_wxid_present": bool(target.get("wxid")),
        "target_device_present": bool(target.get("device")),
        "target_owner_present": bool(target.get("owner_wxid")),
    }


def check_target(args: argparse.Namespace) -> Dict[str, Any]:
    target = resolve_target(args)
    require_target_safety(args, target)
    return summarize_target(target, args.target_name)


def normalize_send_selector(value: str) -> str:
    return value.strip().lower().replace("_", "-")


def parse_send_kinds(raw: str) -> List[str]:
    if not raw.strip():
        return list(DEFAULT_SEND_KINDS)
    items = [normalize_send_selector(item) for item in raw.split(",") if item.strip()]
    expanded: List[str] = []
    for item in items:
        if item in SEND_PROFILES and item != "custom":
            expanded.extend(SEND_PROFILES[item])
        else:
            expanded.append(item)
    invalid = [item for item in expanded if item not in SUPPORTED_SEND_KINDS]
    if invalid:
        raise ContractError("unsupported send kind(s): " + ", ".join(invalid))
    result: List[str] = []
    for item in expanded:
        if item not in result:
            result.append(item)
    return result or list(DEFAULT_SEND_KINDS)


def selected_send_kinds(args: argparse.Namespace) -> List[str]:
    profile = normalize_send_selector(getattr(args, "send_profile", "custom") or "custom")
    raw_kinds = getattr(args, "send_kinds", "") or ""
    default_raw = ",".join(DEFAULT_SEND_KINDS)
    explicit_kinds = normalize_send_selector(raw_kinds) != normalize_send_selector(default_raw)
    if profile in {"", "custom"}:
        return parse_send_kinds(raw_kinds)
    if profile not in SEND_PROFILES:
        raise ContractError("unsupported send profile: " + profile)
    if explicit_kinds:
        raise ContractError("--send-profile cannot be combined with explicit --send-kinds")
    return list(SEND_PROFILES[profile])


def send_checks(args: argparse.Namespace) -> Dict[str, Any]:
    target = resolve_target(args)
    require(bool(args.target_name_exact.strip()), "confirm-send requires --target-name-exact for target safety")
    require(target.get("_target_source") != "wxid", "confirm-send requires the target wxid to be found in contacts; sync contacts or use --target-query for manual resolution")
    require_target_safety(args, target)
    target_id = target.get("wxid") or ""
    require(bool(target_id), "target wxid is empty")
    marker_prefix = "[协议自测] public-api v1 " + time.strftime("%Y-%m-%d %H:%M:%S")
    results = []
    for kind in selected_send_kinds(args):
        if kind == "voice" and not args.voice_file and not args.voice_media_url:
            raise ContractError("voice confirm-send requires --voice-file or --voice-media-url with a real AMR/SILK sample; the built-in fallback is only for dry-run payload shape checks")
        built = build_send_payload(kind, target_id, marker_prefix, args)
        if built.get("skipped"):
            results.append(built)
            continue
        endpoint = built.pop("endpoint")
        match_text = built.pop("match_text", "")
        response = request_json(args.base_url, args.api_key, "POST", endpoint, built)
        outbox_id = response.get("outbox_id")
        require(response.get("ok") is True and isinstance(outbox_id, int) and outbox_id > 0, f"{kind} send response did not return a valid outbox_id")
        outbox = poll_outbox(args.base_url, args.api_key, outbox_id, args.timeout)
        status = outbox.get("status")
        chat_record_id = outbox.get("chat_record_id")
        found = False
        if status == "sent":
            found = outbound_message_found(
                args.base_url,
                args.api_key,
                target_id,
                kind,
                match_text,
                chat_record_id if isinstance(chat_record_id, int) else 0,
                wait_seconds=min(20, max(5, args.timeout // 3)),
            )
        results.append({
            "kind": kind,
            "endpoint": endpoint,
            "queued": True,
            "outbox_id": outbox_id,
            "outbox_chat_record_id": chat_record_id,
            "outbox_final_status": status,
            "outbox_last_error_present": bool(outbox.get("last_error")),
            "outbound_message_found": found,
        })
    return {
        "send_checked": True,
        "target_source": target.get("_target_source") or "contacts",
        "target_name": contact_display_name(target) or args.target_name,
        "target_name_exact_checked": True,
        "target_kind": "room" if contact_is_room(target) else "direct",
        "target_wxid_present": bool(target_id),
        "results": results,
    }


def dry_run_send_checks(args: argparse.Namespace) -> Dict[str, Any]:
    target = resolve_target(args)
    require_target_safety(args, target)
    target_id = target.get("wxid") or ""
    require(bool(target_id), "target wxid is empty")
    marker_prefix = "[协议自测] public-api v1 dry-run"
    payloads = []
    for kind in selected_send_kinds(args):
        built = build_send_payload(kind, target_id, marker_prefix, args)
        payloads.append(summarize_send_payload(kind, built))
    return {
        "dry_run": True,
        "target_source": target.get("_target_source") or "contacts",
        "target_name": contact_display_name(target) or args.target_name,
        "target_name_exact_checked": bool(args.target_name_exact.strip()),
        "target_kind": "room" if contact_is_room(target) else "direct",
        "target_wxid_present": bool(target_id),
        "payloads": payloads,
    }


def summarize_send_payload(kind: str, payload: Dict[str, Any]) -> Dict[str, Any]:
    if payload.get("skipped"):
        return {
            "kind": kind,
            "skipped": True,
            "reason": payload.get("reason") or "skipped",
        }
    public_keys = sorted(key for key in payload if key not in {"endpoint", "match_text", "media_base64", "wx_ids"})
    return {
        "kind": kind,
        "endpoint": payload.get("endpoint"),
        "target_count": len(payload.get("wx_ids") or []),
        "fields": public_keys,
        "has_media_base64": bool(payload.get("media_base64")),
        "has_media_url": bool(payload.get("media_url")),
    }


def check_required_send_success(summary: Dict[str, Any]) -> Dict[str, Any]:
    failures = []
    for item in summary.get("results") or []:
        if not isinstance(item, dict):
            failures.append({"reason": "send result is not an object"})
            continue
        kind = item.get("kind") or "<unknown>"
        if item.get("skipped"):
            failures.append({"kind": kind, "reason": item.get("reason") or "skipped"})
            continue
        if item.get("outbox_final_status") != "sent":
            failures.append({"kind": kind, "reason": "outbox_not_sent", "status": item.get("outbox_final_status")})
            continue
        if item.get("outbound_message_found") is not True:
            failures.append({"kind": kind, "reason": "outbound_message_not_found"})
    require(not failures, "send checks did not all succeed: " + json.dumps(failures, ensure_ascii=False, sort_keys=True))
    return {"send_success": True}


def parse_int_csv(raw: str) -> List[int]:
    result: List[int] = []
    for item in raw.split(","):
        item = item.strip()
        if not item:
            continue
        try:
            value = int(item)
        except ValueError as exc:
            raise ContractError("id list must contain integers") from exc
        if value <= 0:
            raise ContractError("id list cannot contain non-positive values")
        result.append(value)
    return result


def build_send_payload(kind: str, target_id: str, marker_prefix: str, args: argparse.Namespace) -> Dict[str, Any]:
    base = {"wx_ids": [target_id]}
    if kind == "text":
        text = marker_prefix + " text"
        return {**base, "endpoint": "/api/v1/messages/text", "text": text, "match_text": text}
    if kind == "link":
        title = marker_prefix + " link"
        return {
            **base,
            "endpoint": "/api/v1/messages/link",
            "appmsg_title": title,
            "appmsg_description": "wechat-observatory public API contract check",
            "appmsg_url": args.link_url,
            "match_text": title,
        }
    if kind == "location":
        return {
            **base,
            "endpoint": "/api/v1/messages/location",
            "location_latitude": args.location_lat,
            "location_longitude": args.location_lon,
            "location_scale": 16,
            "location_label": args.location_label,
            "match_text": args.location_label,
        }
    if kind == "emoji":
        payload = {**base, "endpoint": "/api/v1/messages/emoji", "text": "[表情]", "match_text": "[表情]"}
        if args.emoji_source_chat_record_id > 0:
            payload["source_chat_record_id"] = args.emoji_source_chat_record_id
            return payload
        if args.emoji_md5:
            payload["emoji_md5"] = args.emoji_md5
            if args.emoji_product_id:
                payload["emoji_product_id"] = args.emoji_product_id
            return payload
        return {"kind": kind, "skipped": True, "reason": "emoji requires --emoji-source-chat-record-id or --emoji-md5"}
    if kind == "quote":
        if args.quote_msg_id <= 0:
            return {"kind": kind, "skipped": True, "reason": "quote requires --quote-msg-id"}
        payload = {**base, "endpoint": "/api/v1/messages/quote", "text": marker_prefix + " quote", "quote_msg_id": args.quote_msg_id}
        if args.quote_chat_record_id > 0:
            payload["quote_chat_record_id"] = args.quote_chat_record_id
        if args.quote_talker:
            payload["quote_talker"] = args.quote_talker
        if args.quote_sender_wxid:
            payload["quote_sender_wxid"] = args.quote_sender_wxid
        return payload
    if kind == "image":
        payload = {**base, "endpoint": "/api/v1/messages/image", "text": marker_prefix + " image"}
        attach_media(payload, args.image_file, args.image_media_url, "contract-check.png", "image/png", CONTRACT_CHECK_PNG_BASE64)
        return payload
    if kind == "file":
        payload = {**base, "endpoint": "/api/v1/messages/file", "text": marker_prefix + " file"}
        fallback = base64.b64encode((marker_prefix + " file\n").encode("utf-8")).decode("ascii")
        attach_media(payload, args.file_path, args.file_media_url, "contract-check.txt", "text/plain", fallback)
        return payload
    if kind == "video":
        payload = {**base, "endpoint": "/api/v1/messages/video", "text": marker_prefix + " video"}
        if not args.video_file and not args.video_media_url:
            return {"kind": kind, "skipped": True, "reason": "video requires --video-file or --video-media-url"}
        attach_media(payload, args.video_file, args.video_media_url, "contract-check.mp4", "video/mp4", "")
        return payload
    if kind == "voice":
        payload = {**base, "endpoint": "/api/v1/messages/voice", "text": marker_prefix + " voice", "match_text": "[语音]"}
        attach_media(payload, args.voice_file, args.voice_media_url, "contract-check.amr", "audio/amr", MINIMAL_AMR_BASE64)
        payload["media_duration_ms"] = args.voice_duration_ms
        return payload
    if kind == "mini-program":
        payload = {**base, "endpoint": "/api/v1/messages/mini-program", "text": marker_prefix + " mini_program", "match_text": marker_prefix + " mini_program"}
        if args.mini_program_source_chat_record_id > 0:
            payload["source_chat_record_id"] = args.mini_program_source_chat_record_id
            return payload
        if args.mini_program_username and args.mini_program_page_path:
            payload["appmsg_title"] = args.mini_program_title or marker_prefix + " mini_program"
            payload["appmsg_description"] = args.mini_program_description
            payload["mini_program_username"] = args.mini_program_username
            payload["mini_program_page_path"] = args.mini_program_page_path
            if args.mini_program_appid:
                payload["mini_program_appid"] = args.mini_program_appid
            return payload
        return {"kind": kind, "skipped": True, "reason": "mini-program requires --mini-program-source-chat-record-id or --mini-program-username with --mini-program-page-path"}
    if kind == "chat-history":
        payload = {**base, "endpoint": "/api/v1/messages/chat-history", "text": marker_prefix + " chat_history", "record_title": marker_prefix + " chat_history"}
        if args.chat_history_recorditem_xml:
            payload["recorditem_xml"] = args.chat_history_recorditem_xml
            return payload
        ids = parse_int_csv(args.chat_history_source_chat_record_ids) if args.chat_history_source_chat_record_ids else []
        if ids:
            payload["source_chat_record_ids"] = ids
            return payload
        if args.chat_history_source_chat_record_id > 0:
            payload["source_chat_record_id"] = args.chat_history_source_chat_record_id
            payload["source_chat_record_ids"] = [args.chat_history_source_chat_record_id]
            if args.chat_history_forward_original:
                payload["forward_original"] = True
            return payload
        return {"kind": kind, "skipped": True, "reason": "chat-history requires --chat-history-recorditem-xml or source chat record id(s)"}
    raise ContractError("unsupported send kind: " + kind)


def attach_media(payload: Dict[str, Any], file_path: str, media_url: str, default_name: str, default_mime: str, fallback_base64: str) -> None:
    if media_url:
        payload["media_url"] = media_url
        payload["media_name"] = default_name
        payload["media_mime"] = default_mime
        return
    if file_path:
        path = Path(file_path)
        data = path.read_bytes()
        payload["media_base64"] = base64.b64encode(data).decode("ascii")
        payload["media_name"] = path.name or default_name
        payload["media_mime"] = mimetypes.guess_type(path.name)[0] or default_mime
        return
    if fallback_base64:
        payload["media_base64"] = fallback_base64
        payload["media_name"] = default_name
        payload["media_mime"] = default_mime
        return
    raise ContractError("media file or media url is required")


def outbound_message_found(
    base_url: str,
    api_key: str,
    target_id: str,
    kind: str,
    match_text: str,
    chat_record_id: int,
    wait_seconds: int = 10,
) -> bool:
    deadline = time.monotonic() + max(1, wait_seconds)
    path = "/api/v1/messages?limit=80&wxid=" + urllib.parse.quote(target_id)
    while True:
        history = request_json(base_url, api_key, "GET", path)
        for item in history.get("messages") or []:
            if not isinstance(item, dict) or item.get("direction") != "sent":
                continue
            if chat_record_id > 0 and item.get("chat_record_id") == chat_record_id:
                return True
            if match_text and item.get("text") == match_text:
                return True
            expected_kind = kind.replace("-", "_")
            if kind != "text" and item.get("kind") == expected_kind:
                return True
            appmsg = item.get("appmsg") if isinstance(item.get("appmsg"), dict) else {}
            if kind == "mini-program" and (item.get("appmsg_subtype") == "mini_program" or appmsg.get("subtype") == "mini_program"):
                return True
        if time.monotonic() >= deadline:
            return False
        time.sleep(2)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Check wechat-observatory public API v1 contract.")
    parser.add_argument("--base-url", default=os.environ.get("WECHAT_OBSERVATORY_BASE_URL", "http://127.0.0.1:8088"))
    parser.add_argument("--api-key", default=os.environ.get("WECHAT_OBSERVATORY_API_KEY", ""))
    parser.add_argument("--target-wxid", "--target-id", default=os.environ.get("WECHAT_OBSERVATORY_TARGET_WXID", ""), help="Stable friend wxid or room id. Preferred for sends; contact search is only a fallback.")
    parser.add_argument("--target-query", default="", help="Contact search query used only when --target-wxid is absent.")
    parser.add_argument("--target-name", default="test", help="Exact target display name when resolving through contacts.")
    parser.add_argument("--target-kind", choices=["room", "direct", "any"], default="room", help="Validate the target as a room, direct chat, or either.")
    parser.add_argument("--target-name-contains", default="", help="Fail target checks unless the resolved contact display name contains this text.")
    parser.add_argument("--target-name-exact", default="", help="Fail target checks unless the resolved contact display name exactly matches this text. Required for --confirm-send.")
    parser.add_argument("--require-target-contact", action="store_true", help="With --target-wxid, fail unless the target is found in contacts.")
    parser.add_argument("--dry-run-send", action="store_true", help="Resolve target and summarize send payloads without enqueuing outbox items.")
    parser.add_argument("--confirm-send", action="store_true", help="Actually send messages to --target-wxid or the resolved contact.")
    parser.add_argument("--send-profile", choices=sorted(SEND_PROFILES), default="custom", help="Named send check profile. safe-basic covers text,image,file,link,location.")
    parser.add_argument("--send-kinds", default=",".join(DEFAULT_SEND_KINDS), help="Comma-separated kinds: text,image,video,voice,file,emoji,location,quote,link,mini-program,chat-history,safe-basic,all-basic,all.")
    parser.add_argument("--timeout", type=int, default=60, help="Outbox poll timeout when --confirm-send is used.")
    parser.add_argument("--ws-timeout", type=int, default=10, help="WebSocket contract check timeout.")
    parser.add_argument("--message-limit", type=int, default=DEFAULT_MESSAGE_LIMIT, help=f"Recent messages to scan for readonly coverage, 1..{MAX_MESSAGE_LIMIT}.")
    parser.add_argument("--message-pages", type=int, default=DEFAULT_MESSAGE_PAGES, help=f"Message pages to scan for readonly coverage, 1..{MAX_MESSAGE_PAGES}.")
    parser.add_argument("--skip-ws", action="store_true", help="Skip WebSocket hello/replay/ping checks.")
    parser.add_argument("--skip-media", action="store_true", help="Skip media URL and forbidden path checks.")
    parser.add_argument("--skip-docs", action="store_true", help="Skip /docs and OpenAPI checks.")
    parser.add_argument("--require-device-scoped-media", action="store_true", help="Fail unless a foreign media path returns media_forbidden.")
    parser.add_argument("--require-ready-module", action="store_true", help="Fail unless at least one module reports runtime_status=ready.")
    parser.add_argument("--require-message-kind", default="", help="Comma-separated envelope kinds that must appear in recent /api/v1/messages, for example text,image,appmsg,chat-history.")
    parser.add_argument("--require-media-kind", default="", help="Comma-separated media kinds that must appear in recent /api/v1/messages media entries, for example image,voice,emoji.")
    parser.add_argument("--require-appmsg-subtype", default="", help="Comma-separated appmsg subtypes that must appear in recent /api/v1/messages, for example link,mini-program,quote.")
    parser.add_argument("--require-fixture", default="", help="Comma-separated public fixtures that must be covered by recent messages. Aliases: all, all-safe-live. Example: image,voice,public-api-v1.chat-history.")
    parser.add_argument("--require-send-success", action="store_true", help="With --confirm-send, fail unless every requested send is sent and visible in message history.")
    parser.add_argument("--image-file", default="", help="Local image file for image send checks. Defaults to a tiny generated PNG.")
    parser.add_argument("--image-media-url", default="", help="Existing /api/media/... URL for image send checks.")
    parser.add_argument("--video-file", default="", help="Local video file for video send checks.")
    parser.add_argument("--video-media-url", default="", help="Existing /api/media/... URL for video send checks.")
    parser.add_argument("--voice-file", default="", help="Local AMR/SILK file for voice send checks. Required for --confirm-send voice checks unless --voice-media-url is provided.")
    parser.add_argument("--voice-media-url", default="", help="Existing /api/media/... URL for voice send checks.")
    parser.add_argument("--voice-duration-ms", type=int, default=1000, help="Voice duration passed to Android for voice send checks.")
    parser.add_argument("--file-path", default="", help="Local file for file send checks. Defaults to a small generated text file.")
    parser.add_argument("--file-media-url", default="", help="Existing /api/media/... URL for file send checks.")
    parser.add_argument("--link-url", default="https://example.com/wechat-observatory-contract-check")
    parser.add_argument("--emoji-source-chat-record-id", type=int, default=0, help="Existing emoji chat_record_id for source-forward send checks.")
    parser.add_argument("--emoji-md5", default="", help="Emoji md5 for direct emoji send checks.")
    parser.add_argument("--emoji-product-id", default="", help="Optional emoji product id for direct emoji send checks.")
    parser.add_argument("--quote-msg-id", type=int, default=0, help="Existing WeChat quote msg id for quote send checks.")
    parser.add_argument("--quote-chat-record-id", type=int, default=0, help="Optional source chat_record_id for quote send checks.")
    parser.add_argument("--quote-talker", default="", help="Optional quote talker wxid or room id.")
    parser.add_argument("--quote-sender-wxid", default="", help="Optional quote sender wxid.")
    parser.add_argument("--mini-program-source-chat-record-id", type=int, default=0, help="Existing mini-program chat_record_id for source-forward send checks.")
    parser.add_argument("--mini-program-title", default="", help="Direct-built mini-program title when no source record is used.")
    parser.add_argument("--mini-program-description", default="wechat-observatory public API contract check")
    parser.add_argument("--mini-program-username", default="", help="Direct-built mini-program username when no source record is used.")
    parser.add_argument("--mini-program-page-path", default="", help="Direct-built mini-program page path when no source record is used.")
    parser.add_argument("--mini-program-appid", default="", help="Optional direct-built mini-program appid.")
    parser.add_argument("--chat-history-source-chat-record-id", type=int, default=0, help="Existing chat record id for chat-history send checks.")
    parser.add_argument("--chat-history-source-chat-record-ids", default="", help="Comma-separated existing chat record ids for chat-history send checks.")
    parser.add_argument("--chat-history-recorditem-xml", default="", help="Existing sanitized recorditem XML for chat-history send checks.")
    parser.add_argument("--chat-history-forward-original", action="store_true", help="Forward the original source record for chat-history checks.")
    parser.add_argument("--location-lat", type=float, default=39.9042)
    parser.add_argument("--location-lon", type=float, default=116.4074)
    parser.add_argument("--location-label", default="Public API contract check")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    if not args.api_key:
        raise SystemExit("Set WECHAT_OBSERVATORY_API_KEY or pass --api-key.")
    if args.confirm_send and not (args.target_wxid or args.target_query):
        raise SystemExit("--confirm-send requires --target-wxid or --target-query.")
    if args.confirm_send and not args.target_name_exact.strip():
        raise SystemExit("--confirm-send requires --target-name-exact for target safety.")
    if args.dry_run_send and not (args.target_wxid or args.target_query):
        raise SystemExit("--dry-run-send requires --target-wxid or --target-query.")
    if args.dry_run_send and args.confirm_send:
        raise SystemExit("--dry-run-send cannot be combined with --confirm-send.")
    if args.require_send_success and not args.confirm_send:
        raise SystemExit("--require-send-success requires --confirm-send.")

    result: Dict[str, Any] = {
        "ok": False,
        "base_url": args.base_url.rstrip("/"),
        "send_enabled": bool(args.confirm_send),
        "send_profile": args.send_profile,
        "send_kinds": [],
    }
    try:
        if args.confirm_send or args.dry_run_send:
            result["send_kinds"] = selected_send_kinds(args)
        result.update(check_readonly(args))
        required_live = check_required_live_state(args, result)
        if required_live:
            result["required_live_checks"] = required_live
        if args.target_wxid or args.target_query:
            result["target"] = check_target(args)
        if args.dry_run_send:
            result["send_dry_run"] = dry_run_send_checks(args)
        if args.confirm_send:
            send_summary = send_checks(args)
            if args.require_send_success:
                send_summary["required"] = check_required_send_success(send_summary)
            result["send"] = send_summary
        result["ok"] = True
    except (APIError, ContractError, OSError, json.JSONDecodeError) as exc:
        result["error"] = redact(str(exc))
        details = getattr(exc, "details", {})
        if isinstance(details, dict) and details:
            result["error_details"] = details
        print(json.dumps(result, ensure_ascii=False, sort_keys=True), file=sys.stderr)
        raise SystemExit(1) from exc

    print(json.dumps(result, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
