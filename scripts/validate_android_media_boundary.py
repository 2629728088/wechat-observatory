#!/usr/bin/env python3
"""Validate Android media parsing boundaries."""

from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ANDROID_MAIN_JAVA_DIR = ROOT / "android-module/app/src/main/java"
HOOK_ENTRY = ROOT / "android-module/app/src/main/java/cc/wechat/observatory/HookEntry.java"
HOOK_MEDIA_SERVICES = ROOT / "android-module/app/src/main/java/cc/wechat/observatory/HookMediaServices.java"
HOOK_MEDIA_RETRY_SCHEDULER = ROOT / "android-module/app/src/main/java/cc/wechat/observatory/HookMediaRetryScheduler.java"
HOOK_WECHAT_MEDIA_RUNTIME_PROVIDER = ROOT / "android-module/app/src/main/java/cc/wechat/observatory/HookWechatMediaRuntimeProvider.java"
PRIMARY_MEDIA_BOUNDARY_FILES = {
    "MediaFiles.java": ROOT / "android-module/app/src/main/java/cc/wechat/observatory/media/MediaFiles.java",
    "MediaSearchPlan.java": ROOT / "android-module/app/src/main/java/cc/wechat/observatory/media/MediaSearchPlan.java",
    "MediaResolver.java": ROOT / "android-module/app/src/main/java/cc/wechat/observatory/media/MediaResolver.java",
}
MEDIA_PACKAGE_DIR = ROOT / "android-module/app/src/main/java/cc/wechat/observatory/media"

ALLOWED_MEDIA_IMPORTS = set()

FORBIDDEN_MEDIA_IMPORT_PREFIXES = (
    "cc.wechat.observatory.media.ImageDownload",
    "cc.wechat.observatory.media.EmojiMediaParser",
    "cc.wechat.observatory.media.EmojiInfoDiagnostics",
    "cc.wechat.observatory.media.MediaDigests",
    "cc.wechat.observatory.media.MediaAttachment",
    "cc.wechat.observatory.media.MediaDirect",
    "cc.wechat.observatory.media.MediaFile",
    "cc.wechat.observatory.media.MediaFiles",
    "cc.wechat.observatory.media.MediaHint",
    "cc.wechat.observatory.media.MediaPayload",
    "cc.wechat.observatory.media.MediaResolver",
    "cc.wechat.observatory.media.MediaRetryRuntime",
    "cc.wechat.observatory.media.MediaSearch",
    "cc.wechat.observatory.media.NamedMedia",
    "cc.wechat.observatory.media.NonImage",
    "cc.wechat.observatory.media.OutboundMediaNames",
    "cc.wechat.observatory.media.Wechat",
)

FORBIDDEN_HOOK_TOKENS = {
    "direct_wechat_media_runtime_provider": "HookWechatMediaRuntimeProvider",
    "old_resolve_media_file_method": "resolveMediaFile(",
    "old_resolve_image_info_method": "resolveImageMediaFromInfo(",
    "old_request_image_download_method": "requestImageDownloadAndResolve(",
    "old_resolve_image_candidate_method": "resolveImageCandidateFile(",
    "old_find_named_image_method": "findNamedImageFile(",
    "wechat_image2_literal": '"image2"',
    "wechat_ref_dir_literal": '".ref"',
    "wechat_ref_suffix_symbol": "\\u2316",
    "wechat_thumbnail_prefix_literal": '"th_"',
    "wechat_net_scene_get_msg_img": "NetSceneGetMsgImg",
    "wechat_image_download_queue_symbol": "gm0.j1.d",
    "direct_media_attachment_bridge_factory": "HookMediaServices.attachmentBridge(",
    "direct_media_retry_runtime": "new MediaRetryRuntime(",
    "direct_media_retry_runtime_type": "MediaRetryRuntime.",
    "direct_media_upload_tracker": "new MediaUploadTracker(",
    "direct_emoji_info_diagnostics": "EmojiInfoDiagnostics",
    "direct_media_digests": "MediaDigests.",
    "direct_image_download_request_tracker": "new ImageDownloadRequestTracker(",
    "direct_emoji_media_parser": "EmojiMediaParser.",
    "direct_outbound_media_names": "OutboundMediaNames.",
}

REQUIRED_BRIDGE_TOKENS = {
    "hook_media_services": "HookMediaServices",
    "media_attachment_controller": "HookMediaAttachmentController",
    "media_retry_bridge": "HookMediaRetryMessageBridge",
    "media_retry_scheduler": "HookMediaRetryScheduler",
    "media_services_attachment_controller": "HookMediaServices.attachmentController(",
    "media_services_emoji_parser": "HookMediaServices.emojiMd5FromWechatContent(",
    "media_services_retry_scheduler": "HookMediaServices.mediaRetryScheduler(",
    "media_services_video_base_name": "HookMediaServices.videoBaseName(",
    "media_services_voice_base_name": "HookMediaServices.voiceBaseName(",
}

HOOK_MEDIA_SERVICES_REQUIREMENTS = {
    "media_services_owns_image_download_tracker": "new ImageDownloadRequestTracker(1024)",
    "media_services_passes_image_download_tracker": "IMAGE_DOWNLOAD_REQUEST_TRACKER,",
    "media_services_builds_attachment_bridge": "new HookWechatMediaRuntimeProvider(",
    "media_services_builds_attachment_controller": "new HookMediaAttachmentController(",
    "media_services_exposes_attachment_controller": "static HookMediaAttachmentController attachmentController(",
    "media_services_owns_emoji_parser": "EmojiMediaParser.md5FromWechatContent(",
    "media_services_exposes_emoji_parser": "static String emojiMd5FromWechatContent(",
    "media_services_owns_video_base_name": "OutboundMediaNames.videoBaseName(",
    "media_services_owns_voice_base_name": "OutboundMediaNames.voiceBaseName(",
    "media_services_exposes_video_base_name": "static String videoBaseName(",
    "media_services_exposes_voice_base_name": "static String voiceBaseName(",
    "media_services_builds_retry_scheduler": "new HookMediaRetryScheduler(",
    "media_services_exposes_retry_scheduler": "static HookMediaRetryScheduler mediaRetryScheduler(",
}

HOOK_MEDIA_RETRY_SCHEDULER_REQUIREMENTS = {
    "retry_scheduler_owns_media_retry_request": "new MediaRetryRuntime.Request(",
    "retry_scheduler_delegates_schedule": "runtime.scheduleIfNeeded(",
    "retry_scheduler_delegates_remember": "runtime.rememberUploaded(payload)",
}

HOOK_WECHAT_MEDIA_RUNTIME_PROVIDER_REQUIREMENTS = {
    "wechat_media_runtime_provider_creates_runtime": "new WechatMediaRuntime(",
    "wechat_media_runtime_provider_uses_environment_factory": (
        "HookWechatMediaRuntimeEnvironmentFactory.create("
    ),
}

FORBIDDEN_WECHAT_MEDIA_RUNTIME_PROVIDER_TOKENS = {
    "wechat_media_runtime_provider_builds_environment_directly": "new WechatMediaRuntimeEnvironment(",
}

PRIMARY_MEDIA_FORBIDDEN_IMPORT_PREFIXES = (
    "android.",
    "de.robv.android.xposed",
    "cc.wechat.observatory.Hook",
    "cc.wechat.observatory.config",
    "cc.wechat.observatory.gateway",
    "cc.wechat.observatory.model",
    "cc.wechat.observatory.outbox",
    "cc.wechat.observatory.wechat",
)

SENSITIVE_LOG_TOKENS = (
    "apikey",
    "api_key",
    "auth",
    "base64",
    "content",
    "cookie",
    "credential",
    "media_base64",
    "mediabase64",
    "password",
    "room",
    "secret",
    "sender",
    "session",
    "talker",
    "token",
    "wxid",
)

LOG_CONTEXT_TOKENS = (
    "logger.log",
    "log(logger",
    "bridgelogger.log",
    "log(",
    "string message",
    "string logmessage",
)

RAW_SENSITIVE_LOG_EXPRESSIONS = {
    "raw_wxid": "+wxid",
    "raw_target_wxid": "+targetwxid",
    "raw_config_self_wxid": "+config.selfwxid",
    "raw_payload_chat_id": "+payload.chatid",
    "raw_payload_from": "+payload.from",
    "raw_payload_sender": "+payload.sender",
    "raw_payload_to": "+payload.to",
    "raw_talker": "+talker",
    "raw_request_talker": "+request.talker",
    "raw_source_talker": "+source.talker",
    "raw_content": "+content",
    "raw_request_content": "+request.content",
    "raw_source_content": "+source.content",
    "raw_config_api_key": "+config.apikey",
    "raw_api_key": "+apikey",
}


def extract_imports(source: str) -> list[str]:
    return re.findall(r"^\s*import\s+([^;]+);", source, flags=re.MULTILINE)


def forbidden_media_imports(imports: list[str]) -> list[str]:
    forbidden = []
    for imported in imports:
        if imported in ALLOWED_MEDIA_IMPORTS:
            continue
        if any(imported.startswith(prefix) for prefix in FORBIDDEN_MEDIA_IMPORT_PREFIXES):
            forbidden.append(imported)
    return sorted(forbidden)


def forbidden_primary_media_imports(imports: list[str]) -> list[str]:
    return sorted(
        imported for imported in imports
        if any(imported.startswith(prefix) for prefix in PRIMARY_MEDIA_FORBIDDEN_IMPORT_PREFIXES)
    )


def validate_primary_media_sources(primary_media_sources: dict[str, str]) -> list[dict[str, object]]:
    errors: list[dict[str, object]] = []
    for name, source in sorted(primary_media_sources.items()):
        bad_imports = forbidden_primary_media_imports(extract_imports(source))
        if bad_imports:
            errors.append({
                "type": "forbidden_primary_media_boundary_imports",
                "file": name,
                "imports": bad_imports,
            })
    return errors


def collect_java_statements(source: str) -> list[tuple[int, str]]:
    statements: list[tuple[int, str]] = []
    start_line = 1
    current: list[str] = []
    for line_number, line in enumerate(source.splitlines(), start=1):
        stripped = line.strip()
        if not current and not stripped:
            continue
        if not current:
            start_line = line_number
        current.append(line)
        if ";" in stripped:
            statements.append((start_line, "\n".join(current)))
            current = []
    if current:
        statements.append((start_line, "\n".join(current)))
    return statements


def is_media_log_context(statement: str) -> bool:
    normalized = statement.lower().replace(" ", "")
    spaced = statement.lower()
    return any(token.replace(" ", "") in normalized for token in LOG_CONTEXT_TOKENS) or (
        "string message" in spaced
        or "string logmessage" in spaced
    )


def sensitive_log_tokens(statement: str) -> list[str]:
    normalized = statement.lower().replace(" ", "")
    return sorted(token for token in SENSITIVE_LOG_TOKENS if token in normalized)


def sensitive_log_line_number(start_line: int, statement: str) -> int:
    fallback = start_line
    for offset, line in enumerate(statement.splitlines()):
        line_number = start_line + offset
        if sensitive_log_tokens(line):
            fallback = line_number
        if is_media_log_context(line) and sensitive_log_tokens(line):
            return line_number
    return fallback


def validate_media_log_safety(media_sources: dict[str, str]) -> list[dict[str, object]]:
    errors: list[dict[str, object]] = []
    for name, source in sorted(media_sources.items()):
        for line_number, statement in collect_java_statements(source):
            if not is_media_log_context(statement):
                continue
            tokens = sensitive_log_tokens(statement)
            if tokens:
                errors.append({
                    "type": "sensitive_media_log_context",
                    "file": name,
                    "line": sensitive_log_line_number(line_number, statement),
                    "tokens": tokens,
                })
    return errors


def validate_sensitive_log_expressions(sources: dict[str, str]) -> list[dict[str, object]]:
    errors: list[dict[str, object]] = []
    for name, source in sorted(sources.items()):
        for line_number, statement in collect_java_statements(source):
            if not is_media_log_context(statement):
                continue
            normalized = statement.lower().replace(" ", "").replace("\n", "")
            items = [
                item for item, token in RAW_SENSITIVE_LOG_EXPRESSIONS.items()
                if token in normalized
            ]
            if items:
                errors.append({
                    "type": "raw_sensitive_log_expression",
                    "file": name,
                    "line": sensitive_log_line_number(line_number, statement),
                    "items": sorted(items),
                })
    return errors


def read_java_sources(root: Path) -> dict[str, str]:
    return {
        path.relative_to(root).as_posix(): path.read_text(encoding="utf-8")
        for path in sorted(root.rglob("*.java"))
    }


def validate_media_boundary(
        hook_source: str,
        primary_media_sources: dict[str, str] | None = None,
        media_sources: dict[str, str] | None = None,
        log_safety_sources: dict[str, str] | None = None,
        hook_media_services_source: str | None = None,
        hook_media_retry_scheduler_source: str | None = None,
        hook_wechat_media_runtime_provider_source: str | None = None) -> dict[str, object]:
    imports = extract_imports(hook_source)
    bad_imports = forbidden_media_imports(imports)
    bad_tokens = [
        name for name, token in FORBIDDEN_HOOK_TOKENS.items()
        if token in hook_source
    ]
    missing_bridge_tokens = [
        name for name, token in REQUIRED_BRIDGE_TOKENS.items()
        if token not in hook_source
    ]

    errors = []
    if bad_imports:
        errors.append({"type": "forbidden_hook_media_imports", "imports": bad_imports})
    if bad_tokens:
        errors.append({"type": "forbidden_hook_media_tokens", "items": bad_tokens})
    if missing_bridge_tokens:
        errors.append({"type": "missing_hook_media_bridge_tokens", "items": missing_bridge_tokens})
    if primary_media_sources is not None:
        errors.extend(validate_primary_media_sources(primary_media_sources))
    if media_sources is not None:
        errors.extend(validate_media_log_safety(media_sources))
    if log_safety_sources is not None:
        errors.extend(validate_sensitive_log_expressions(log_safety_sources))
    missing_hook_media_services_requirements = []
    if hook_media_services_source is not None:
        missing_hook_media_services_requirements = [
            name for name, token in HOOK_MEDIA_SERVICES_REQUIREMENTS.items()
            if token not in hook_media_services_source
        ]
        if missing_hook_media_services_requirements:
            errors.append({
                "type": "missing_hook_media_services_requirements",
                "items": missing_hook_media_services_requirements,
            })
    missing_hook_media_retry_scheduler_requirements = []
    if hook_media_retry_scheduler_source is not None:
        missing_hook_media_retry_scheduler_requirements = [
            name for name, token in HOOK_MEDIA_RETRY_SCHEDULER_REQUIREMENTS.items()
            if token not in hook_media_retry_scheduler_source
        ]
        if missing_hook_media_retry_scheduler_requirements:
            errors.append({
                "type": "missing_hook_media_retry_scheduler_requirements",
                "items": missing_hook_media_retry_scheduler_requirements,
            })
    missing_wechat_media_runtime_provider_requirements = []
    forbidden_wechat_media_runtime_provider_tokens = []
    if hook_wechat_media_runtime_provider_source is not None:
        missing_wechat_media_runtime_provider_requirements = [
            name for name, token in HOOK_WECHAT_MEDIA_RUNTIME_PROVIDER_REQUIREMENTS.items()
            if token not in hook_wechat_media_runtime_provider_source
        ]
        forbidden_wechat_media_runtime_provider_tokens = [
            name for name, token in FORBIDDEN_WECHAT_MEDIA_RUNTIME_PROVIDER_TOKENS.items()
            if token in hook_wechat_media_runtime_provider_source
        ]
        if missing_wechat_media_runtime_provider_requirements:
            errors.append({
                "type": "missing_hook_wechat_media_runtime_provider_requirements",
                "items": missing_wechat_media_runtime_provider_requirements,
            })
        if forbidden_wechat_media_runtime_provider_tokens:
            errors.append({
                "type": "forbidden_hook_wechat_media_runtime_provider_tokens",
                "items": forbidden_wechat_media_runtime_provider_tokens,
            })

    return {
        "ok": not errors,
        "allowed_media_imports": sorted(imported for imported in imports if imported in ALLOWED_MEDIA_IMPORTS),
        "forbidden_media_imports": bad_imports,
        "forbidden_tokens": bad_tokens,
        "missing_bridge_tokens": missing_bridge_tokens,
        "primary_media_files": sorted(primary_media_sources or {}),
        "media_log_files": sorted(media_sources or {}),
        "log_safety_files": sorted(log_safety_sources or {}),
        "missing_hook_media_services_requirements": missing_hook_media_services_requirements,
        "missing_hook_media_retry_scheduler_requirements": (
            missing_hook_media_retry_scheduler_requirements
        ),
        "missing_hook_wechat_media_runtime_provider_requirements": (
            missing_wechat_media_runtime_provider_requirements
        ),
        "forbidden_hook_wechat_media_runtime_provider_tokens": (
            forbidden_wechat_media_runtime_provider_tokens
        ),
        "errors": errors,
    }


def main() -> int:
    hook_source = HOOK_ENTRY.read_text(encoding="utf-8")
    primary_media_sources = {
        name: path.read_text(encoding="utf-8")
        for name, path in PRIMARY_MEDIA_BOUNDARY_FILES.items()
    }
    media_sources = {
        path.name: path.read_text(encoding="utf-8")
        for path in sorted(MEDIA_PACKAGE_DIR.glob("*.java"))
    }
    log_safety_sources = read_java_sources(ANDROID_MAIN_JAVA_DIR)
    hook_media_services_source = HOOK_MEDIA_SERVICES.read_text(encoding="utf-8")
    hook_media_retry_scheduler_source = HOOK_MEDIA_RETRY_SCHEDULER.read_text(encoding="utf-8")
    hook_wechat_media_runtime_provider_source = (
        HOOK_WECHAT_MEDIA_RUNTIME_PROVIDER.read_text(encoding="utf-8")
    )
    summary = validate_media_boundary(
        hook_source,
        primary_media_sources,
        media_sources,
        log_safety_sources,
        hook_media_services_source,
        hook_media_retry_scheduler_source,
        hook_wechat_media_runtime_provider_source)
    print(json.dumps(summary, ensure_ascii=False, sort_keys=True, indent=2))
    return 0 if summary["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
