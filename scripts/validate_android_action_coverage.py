#!/usr/bin/env python3
"""Validate Android Action Outbox coverage without building the APK.

This lightweight check keeps the gateway outbox protocol and the LSPosed action
dispatcher aligned even when a full Android build is intentionally skipped.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVENTS_GO = ROOT / "internal" / "bridge" / "events.go"
ANDROID_MAIN = ROOT / "android-module" / "app" / "src" / "main" / "java" / "cc" / "wechat" / "observatory"
HOOK_ENTRY = ANDROID_MAIN / "HookEntry.java"

ACTION_COVERAGE_FILES = [
    HOOK_ENTRY,
    ANDROID_MAIN / "HookMediaAttachmentBridge.java",
    ANDROID_MAIN / "HookMediaAttachmentController.java",
    ANDROID_MAIN / "HookMediaServices.java",
    ANDROID_MAIN / "HookOutboxMediaActionRunner.java",
    ANDROID_MAIN / "outbox" / "OutboxMediaDownloader.java",
    ANDROID_MAIN / "outbox" / "OutboxMediaFilePreparer.java",
    ANDROID_MAIN / "media" / "ImageDownloadCoordinator.java",
    ANDROID_MAIN / "media" / "ImageDownloadRequestTracker.java",
    ANDROID_MAIN / "media" / "MediaAttachmentProcessorFactory.java",
    ANDROID_MAIN / "media" / "MediaAttachmentRuntime.java",
    ANDROID_MAIN / "media" / "MediaFileSelector.java",
    ANDROID_MAIN / "media" / "MediaFiles.java",
    ANDROID_MAIN / "media" / "MediaHintResolver.java",
    ANDROID_MAIN / "media" / "MediaResolver.java",
    ANDROID_MAIN / "media" / "MediaSearchPlan.java",
    ANDROID_MAIN / "media" / "WechatImageDownloadResolver.java",
    ANDROID_MAIN / "media" / "WechatImageInfoCandidateNames.java",
    ANDROID_MAIN / "media" / "WechatImageInfoResolver.java",
    ANDROID_MAIN / "media" / "WechatImageInfoValueResolver.java",
    ANDROID_MAIN / "media" / "WechatImageMediaResolver.java",
    ANDROID_MAIN / "media" / "WechatImageSearchPlan.java",
    ANDROID_MAIN / "wechat" / "WechatImageDownloadCallbackFactory.java",
    ANDROID_MAIN / "wechat" / "WechatImageDownloadLogLine.java",
    ANDROID_MAIN / "wechat" / "WechatImageDownloadRuntime.java",
    ANDROID_MAIN / "wechat" / "WechatImageDownloadRuntimeClasses.java",
]

ACTION_METHODS = {
    "text": "sendText(",
    "image": "sendImageAction(",
    "video": "sendVideoAction(",
    "voice": "sendVoiceAction(",
    "file": "sendFileAction(",
    "emoji": "sendEmojiAction(",
    "location": "sendLocationAction(",
    "quote": "sendQuoteAction(",
    "link": "sendLinkAction(",
    "mini_program": "sendMiniProgramAction(",
    "chat_history": "sendChatHistoryAction(",
    "revoke": "sendRevokeAction(",
}

ACTION_HANDLER_DEFINITIONS = {
    kind: method[:-1] for kind, method in ACTION_METHODS.items() if kind != "text"
}

MEDIA_ACTION_REQUIREMENTS = {
    "image": [
        "media_url is required",
        "OutboxMediaFilePreparer.fromRuntime(",
        "media download produced empty file",
        "sendImage(",
        "media.cleanup(kind)",
        "file.delete()",
    ],
    "video": [
        "media_url is required",
        "OutboxMediaFilePreparer.fromRuntime(",
        "media download produced empty file",
        "sendVideo(",
        "media.cleanup(kind)",
        "file.delete()",
    ],
    "voice": [
        "media_url is required",
        "OutboxMediaFilePreparer.fromRuntime(",
        "media download produced empty file",
        "MediaFiles.isSupportedVoiceMediaFile(",
        "sendVoice(",
        "media.cleanup(kind)",
        "file.delete()",
    ],
    "file": [
        "media_url is required",
        "OutboxMediaFilePreparer.fromRuntime(",
        "media download produced empty file",
        "sendFile(",
        "media.cleanup(kind)",
        "file.delete()",
    ],
}

INBOUND_IMAGE_REQUIREMENTS = {
    "image_raw_xml_type": "MediaFiles.isImageMessageType(type)",
    "image_embedded_xml_normalization": "MediaAttachmentProcessorFactory.create(",
    "image_missing_media_log": "MediaFiles.shouldLogMissingMedia",
    "image_info_table_lookup": "WechatImageInfoResolver",
    "image_priority_search": "WechatImageMediaResolver",
    "image_ambiguous_recent_skip": "image named lookup missed; skip ambiguous recent fallback",
    "image_candidate_variants": "addCandidateVariants",
    "image_header_validation": "hasImageHeader",
    "image_net_scene_download": "m11.t0",
    "image_download_callback": "WechatImageDownloadCallbackFactory",
    "image_download_enqueue_log": "image NetSceneGetMsgImg enqueued",
    "image_download_dedup": "new ImageDownloadRequestTracker(1024)",
    "image_download_coordinator": "ImageDownloadCoordinator",
}


def extract_outbox_kinds(source: str) -> list[str]:
    kinds = re.findall(r"OutboxKind[A-Za-z]+\s*=\s*\"([^\"]+)\"", source)
    seen: set[str] = set()
    ordered: list[str] = []
    for kind in kinds:
        if kind not in seen:
            seen.add(kind)
            ordered.append(kind)
    return ordered


def extract_method_body(source: str, method_name: str) -> str:
    match = re.search(
        r"(?:private|public|protected)\s+static\s+[\w<>\[\]]+\s+"
        + re.escape(method_name)
        + r"\s*\(",
        source,
    )
    if match is None:
        return ""
    brace = source.find("{", match.start())
    if brace < 0:
        return ""
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[brace : index + 1]
    return ""


def validate_action_coverage(
    events_source: str,
    hook_source: str,
    architecture_source: str | None = None,
) -> dict[str, object]:
    architecture_source = hook_source if architecture_source is None else architecture_source
    handle_body = extract_method_body(hook_source, "handleOutboxItems")
    dispatch_body = extract_method_body(hook_source, "executeOutboxItem") or handle_body
    ack_body = extract_method_body(hook_source, "outboxAck")

    kinds = extract_outbox_kinds(events_source)
    missing_known_methods = [kind for kind in kinds if kind not in ACTION_METHODS]
    missing_dispatch = [
        kind
        for kind in kinds
        if f'"{kind}".equals(kind)' not in dispatch_body or ACTION_METHODS.get(kind, "") not in dispatch_body
    ]
    missing_handler_definitions = [
        kind
        for kind, method in ACTION_HANDLER_DEFINITIONS.items()
        if kind in kinds and f"SendResult {method}" not in hook_source
    ]
    missing_media_requirements = []
    for kind, requirements in MEDIA_ACTION_REQUIREMENTS.items():
        if kind not in kinds:
            continue
        missing = [requirement for requirement in requirements if requirement not in architecture_source]
        if missing:
            missing_media_requirements.append({"kind": kind, "missing": missing})

    unknown_failed_ack = "unsupported outbox kind" in dispatch_body and "SendResult.failed" in dispatch_body
    terminal_ack = (
        'ack.put("status", result.ok ? "sent" : "failed")' in ack_body
        and 'ack.put("error", result.error)' in ack_body
    )
    legacy_text_default = 'kind = isBlank(item.optString("media_kind", "")) ? "text"' in dispatch_body
    parallel_dispatch = all(
        token in hook_source
        for token in (
            "handleOutboxWorkItemsParallel(",
            "outboxLaneKey(",
            "normalizedOutboxParallelism(",
            "Executors.newFixedThreadPool",
        )
    )
    send_lock = all(
        token in hook_source
        for token in (
            "WECHAT_SEND_LOCK",
            "synchronized (WECHAT_SEND_LOCK)",
        )
    )
    missing_inbound_image_requirements = [
        name for name, token in INBOUND_IMAGE_REQUIREMENTS.items() if token not in architecture_source
    ]

    errors = []
    if missing_known_methods:
        errors.append({"type": "missing_validator_mapping", "kinds": missing_known_methods})
    if missing_dispatch:
        errors.append({"type": "missing_android_dispatch", "kinds": missing_dispatch})
    if missing_handler_definitions:
        errors.append({"type": "missing_android_handler_definition", "kinds": missing_handler_definitions})
    if missing_media_requirements:
        errors.append({"type": "missing_media_action_requirements", "items": missing_media_requirements})
    if not unknown_failed_ack:
        errors.append({"type": "missing_unknown_kind_failed_ack"})
    if not terminal_ack:
        errors.append({"type": "missing_terminal_ack_status_or_error"})
    if not legacy_text_default:
        errors.append({"type": "missing_legacy_text_default"})
    if not parallel_dispatch:
        errors.append({"type": "missing_parallel_outbox_lane_dispatch"})
    if not send_lock:
        errors.append({"type": "missing_wechat_send_lock"})
    if missing_inbound_image_requirements:
        errors.append({"type": "missing_inbound_image_requirements", "items": missing_inbound_image_requirements})

    summary = {
        "ok": not errors,
        "kind_count": len(kinds),
        "kinds": kinds,
        "unknown_failed_ack": unknown_failed_ack,
        "terminal_ack": terminal_ack,
        "legacy_text_default": legacy_text_default,
        "parallel_dispatch": parallel_dispatch,
        "send_lock": send_lock,
        "missing_inbound_image_requirements": missing_inbound_image_requirements,
        "errors": errors,
    }
    return summary


def read_action_architecture_source() -> str:
    sources = []
    for path in ACTION_COVERAGE_FILES:
        if path.exists():
            sources.append(path.read_text(encoding="utf-8"))
    return "\n".join(sources)


def main() -> int:
    events_source = EVENTS_GO.read_text(encoding="utf-8")
    hook_source = HOOK_ENTRY.read_text(encoding="utf-8")
    architecture_source = read_action_architecture_source()
    summary = validate_action_coverage(events_source, hook_source, architecture_source)
    print(json.dumps(summary, ensure_ascii=False, sort_keys=True, indent=2))
    return 0 if summary["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
