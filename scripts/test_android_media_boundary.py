#!/usr/bin/env python3
"""Unit tests for Android media boundary validation."""

from __future__ import annotations

import unittest

import validate_android_media_boundary as boundary


def hook_source(extra: str = "") -> str:
    return "\n".join(
        [
            "final class HookEntry {",
            "private static HookMediaServices mediaServices() { return null; }",
            "private static HookMediaAttachmentController mediaAttachmentController() { return null; }",
            "private static HookMediaRetryMessageBridge retryBridge() { return null; }",
            "private static final HookMediaAttachmentController MEDIA_ATTACHMENT_CONTROLLER = HookMediaServices.attachmentController(null, null, null, null, null, null, null);",
            "private static final HookMediaRetryScheduler MEDIA_RETRY_SCHEDULER = HookMediaServices.mediaRetryScheduler(null);",
            "private static String emojiMd5() { return HookMediaServices.emojiMd5FromWechatContent(null, null); }",
            "private static String videoName() { return HookMediaServices.videoBaseName(null); }",
            "private static String voiceName() { return HookMediaServices.voiceBaseName(null); }",
            extra,
            "}",
        ]
    )


class AndroidMediaBoundaryTests(unittest.TestCase):
    def test_bridge_only_hook_entry_passes(self) -> None:
        result = boundary.validate_media_boundary(hook_source())

        self.assertTrue(result["ok"], result)
        self.assertEqual(result["errors"], [])

    def test_forbids_direct_media_resolver_imports(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source("import cc.wechat.observatory.media.WechatImageFileResolver;")
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "forbidden_hook_media_imports",
                "imports": ["cc.wechat.observatory.media.WechatImageFileResolver"],
            },
            result["errors"],
        )

    def test_forbids_direct_image_download_tracker_in_hook_entry(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(
                "\n".join(
                    [
                        "import cc.wechat.observatory.media.ImageDownloadRequestTracker;",
                        "Object tracker = new ImageDownloadRequestTracker(1024);",
                    ]
                )
            )
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "forbidden_hook_media_imports",
                "imports": ["cc.wechat.observatory.media.ImageDownloadRequestTracker"],
            },
            result["errors"],
        )
        self.assertIn(
            {
                "type": "forbidden_hook_media_tokens",
                "items": ["direct_image_download_request_tracker"],
            },
            result["errors"],
        )

    def test_forbids_direct_emoji_diagnostics_and_digests_in_hook_entry(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(
                "\n".join(
                    [
                        "import cc.wechat.observatory.media.EmojiInfoDiagnostics;",
                        "import cc.wechat.observatory.media.MediaDigests;",
                        "String md5 = MediaDigests.shortMd5(value);",
                        "String fields = EmojiInfoDiagnostics.fieldSummary(info);",
                    ]
                )
            )
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "forbidden_hook_media_imports",
                "imports": [
                    "cc.wechat.observatory.media.EmojiInfoDiagnostics",
                    "cc.wechat.observatory.media.MediaDigests",
                ],
            },
            result["errors"],
        )
        self.assertIn(
            {
                "type": "forbidden_hook_media_tokens",
                "items": [
                    "direct_emoji_info_diagnostics",
                    "direct_media_digests",
                ],
            },
            result["errors"],
        )

    def test_forbids_direct_emoji_media_parser_in_hook_entry(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(
                "\n".join(
                    [
                        "import cc.wechat.observatory.media.EmojiMediaParser;",
                        "String md5 = EmojiMediaParser.md5FromWechatContent(talker, content);",
                    ]
                )
            )
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "forbidden_hook_media_imports",
                "imports": ["cc.wechat.observatory.media.EmojiMediaParser"],
            },
            result["errors"],
        )
        self.assertIn(
            {
                "type": "forbidden_hook_media_tokens",
                "items": ["direct_emoji_media_parser"],
            },
            result["errors"],
        )

    def test_forbids_direct_outbound_media_names_in_hook_entry(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(
                "\n".join(
                    [
                        "import cc.wechat.observatory.media.OutboundMediaNames;",
                        "String video = OutboundMediaNames.videoBaseName(file);",
                        "String voice = OutboundMediaNames.voiceBaseName(file);",
                    ]
                )
            )
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "forbidden_hook_media_imports",
                "imports": ["cc.wechat.observatory.media.OutboundMediaNames"],
            },
            result["errors"],
        )
        self.assertIn(
            {
                "type": "forbidden_hook_media_tokens",
                "items": ["direct_outbound_media_names"],
            },
            result["errors"],
        )

    def test_forbids_image2_ref_and_net_scene_details_in_hook_entry(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source('String root = "image2"; String ref = ".ref"; String name = "th_"; NetSceneGetMsgImg();')
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "forbidden_hook_media_tokens",
                "items": [
                    "wechat_image2_literal",
                    "wechat_ref_dir_literal",
                    "wechat_thumbnail_prefix_literal",
                    "wechat_net_scene_get_msg_img",
                ],
            },
            result["errors"],
        )

    def test_forbids_old_media_resolver_methods_in_hook_entry(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(
                "\n".join(
                    [
                        "File a = resolveMediaFile(type, hint, createTime, emojiMd5);",
                        "File b = resolveImageMediaFromInfo(info);",
                        "File c = requestImageDownloadAndResolve(id, serverId);",
                        "File d = resolveImageCandidateFile(candidate);",
                        "File e = findNamedImageFile(root, names);",
                    ]
                )
            )
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "forbidden_hook_media_tokens",
                "items": [
                    "old_resolve_media_file_method",
                    "old_resolve_image_info_method",
                    "old_request_image_download_method",
                    "old_resolve_image_candidate_method",
                    "old_find_named_image_method",
                ],
            },
            result["errors"],
        )

    def test_forbids_direct_wechat_media_runtime_provider_in_hook_entry(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source("HookWechatMediaRuntimeProvider provider = null;")
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "forbidden_hook_media_tokens",
                "items": ["direct_wechat_media_runtime_provider"],
            },
            result["errors"],
        )

    def test_forbids_direct_attachment_bridge_factory_in_hook_entry(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source("HookMediaServices.attachmentBridge(null, null, null, null, null, null, null);")
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "forbidden_hook_media_tokens",
                "items": ["direct_media_attachment_bridge_factory"],
            },
            result["errors"],
        )

    def test_forbids_direct_media_retry_runtime_construction_in_hook_entry(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(
                "\n".join(
                    [
                        "import cc.wechat.observatory.media.MediaRetryRuntime;",
                        "MediaRetryRuntime retry = new MediaRetryRuntime(new MediaUploadTracker(1024), null, null);",
                    ]
                )
            )
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "forbidden_hook_media_imports",
                "imports": ["cc.wechat.observatory.media.MediaRetryRuntime"],
            },
            result["errors"],
        )
        self.assertIn(
            {
                "type": "forbidden_hook_media_tokens",
                "items": [
                    "direct_media_retry_runtime",
                    "direct_media_upload_tracker",
                ],
            },
            result["errors"],
        )

    def test_forbids_direct_media_retry_request_type_in_hook_entry(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(
                "\n".join(
                    [
                        "import cc.wechat.observatory.media.MediaRetryRuntime;",
                        "MediaRetryRuntime.Request request = null;",
                    ]
                )
            )
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "forbidden_hook_media_imports",
                "imports": ["cc.wechat.observatory.media.MediaRetryRuntime"],
            },
            result["errors"],
        )
        self.assertIn(
            {
                "type": "forbidden_hook_media_tokens",
                "items": ["direct_media_retry_runtime_type"],
            },
            result["errors"],
        )

    def test_requires_hook_to_stay_on_bridge_boundary(self) -> None:
        result = boundary.validate_media_boundary("final class HookEntry {}")

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "missing_hook_media_bridge_tokens",
                "items": [
                    "hook_media_services",
                    "media_attachment_controller",
                    "media_retry_bridge",
                    "media_retry_scheduler",
                    "media_services_attachment_controller",
                    "media_services_emoji_parser",
                    "media_services_retry_scheduler",
                    "media_services_video_base_name",
                    "media_services_voice_base_name",
                ],
            },
            result["errors"],
        )

    def test_requires_hook_media_services_to_own_image_download_tracker(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(),
            hook_media_services_source="final class HookMediaServices {}",
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "missing_hook_media_services_requirements",
                "items": [
                    "media_services_owns_image_download_tracker",
                    "media_services_passes_image_download_tracker",
                    "media_services_builds_attachment_bridge",
                    "media_services_builds_attachment_controller",
                    "media_services_exposes_attachment_controller",
                    "media_services_owns_emoji_parser",
                    "media_services_exposes_emoji_parser",
                    "media_services_owns_video_base_name",
                    "media_services_owns_voice_base_name",
                    "media_services_exposes_video_base_name",
                    "media_services_exposes_voice_base_name",
                    "media_services_builds_retry_scheduler",
                    "media_services_exposes_retry_scheduler",
                ],
            },
            result["errors"],
        )

    def test_hook_media_services_image_download_tracker_boundary_passes(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(),
            hook_media_services_source="\n".join(
                [
                    "final class HookMediaServices {",
                    "private static final Object IMAGE_DOWNLOAD_REQUEST_TRACKER = new ImageDownloadRequestTracker(1024);",
                    "Object bridge() {",
                    "return new HookWechatMediaRuntimeProvider(",
                    "IMAGE_DOWNLOAD_REQUEST_TRACKER,",
                    "null, null, null, null, null, null, null);",
                    "}",
                    "static HookMediaAttachmentController attachmentController() {",
                    "return new HookMediaAttachmentController(null);",
                    "}",
                    "static String emojiMd5FromWechatContent() {",
                    "return EmojiMediaParser.md5FromWechatContent(null, null);",
                    "}",
                    "static String videoBaseName() {",
                    "return OutboundMediaNames.videoBaseName(null);",
                    "}",
                    "static String voiceBaseName() {",
                    "return OutboundMediaNames.voiceBaseName(null);",
                    "}",
                    "static HookMediaRetryScheduler mediaRetryScheduler() {",
                    "return new HookMediaRetryScheduler(null);",
                    "}",
                    "}",
                ]
            ),
        )

        self.assertTrue(result["ok"], result)

    def test_hook_media_retry_scheduler_boundary_passes(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(),
            hook_media_retry_scheduler_source="\n".join(
                [
                    "final class HookMediaRetryScheduler {",
                    "  void rememberUploaded(MessagePayload payload) {",
                    "    runtime.rememberUploaded(payload);",
                    "  }",
                    "  void scheduleIfNeeded() {",
                    "    runtime.scheduleIfNeeded(",
                    "      new MediaRetryRuntime.Request(",
                    "        true, 1, null, null, null, null, null, 0, null), null);",
                    "  }",
                    "}",
                ]
            ),
        )

        self.assertTrue(result["ok"], result)

    def test_requires_hook_media_retry_scheduler_to_own_request_building(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(),
            hook_media_retry_scheduler_source="final class HookMediaRetryScheduler {}",
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "missing_hook_media_retry_scheduler_requirements",
                "items": [
                    "retry_scheduler_owns_media_retry_request",
                    "retry_scheduler_delegates_schedule",
                    "retry_scheduler_delegates_remember",
                ],
            },
            result["errors"],
        )

    def test_hook_wechat_media_runtime_provider_boundary_passes(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(),
            hook_wechat_media_runtime_provider_source="\n".join(
                [
                    "final class HookWechatMediaRuntimeProvider {",
                    "  Object runtime() {",
                    "    return new WechatMediaRuntime(null, environment(null));",
                    "  }",
                    "  Object environment(Object database) {",
                    "    return HookWechatMediaRuntimeEnvironmentFactory.create(",
                    "        database, null, null, null, null, null, null, null);",
                    "  }",
                    "}",
                ]
            ),
        )

        self.assertTrue(result["ok"], result)

    def test_requires_hook_wechat_media_runtime_provider_to_delegate_environment(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(),
            hook_wechat_media_runtime_provider_source="\n".join(
                [
                    "final class HookWechatMediaRuntimeProvider {",
                    "  Object environment(Object database) {",
                    "    return new WechatMediaRuntimeEnvironment(database);",
                    "  }",
                    "}",
                ]
            ),
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "missing_hook_wechat_media_runtime_provider_requirements",
                "items": [
                    "wechat_media_runtime_provider_creates_runtime",
                    "wechat_media_runtime_provider_uses_environment_factory",
                ],
            },
            result["errors"],
        )
        self.assertIn(
            {
                "type": "forbidden_hook_wechat_media_runtime_provider_tokens",
                "items": ["wechat_media_runtime_provider_builds_environment_directly"],
            },
            result["errors"],
        )

    def test_primary_media_boundary_files_reject_hook_and_outbox_imports(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(),
            {
                "MediaResolver.java": "\n".join(
                    [
                        "import cc.wechat.observatory.outbox.OutboxCommand;",
                        "import cc.wechat.observatory.HookEntry;",
                    ]
                ),
            },
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "forbidden_primary_media_boundary_imports",
                "file": "MediaResolver.java",
                "imports": [
                    "cc.wechat.observatory.HookEntry",
                    "cc.wechat.observatory.outbox.OutboxCommand",
                ],
            },
            result["errors"],
        )

    def test_primary_media_boundary_allows_java_and_util_imports(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(),
            {
                "MediaFiles.java": "\n".join(
                    [
                        "import java.io.File;",
                        "import static cc.wechat.observatory.util.Strings.isBlank;",
                    ]
                ),
                "MediaSearchPlan.java": "import java.util.List;",
                "MediaResolver.java": "import java.io.File;",
            },
        )

        self.assertTrue(result["ok"], result)

    def test_media_log_safety_rejects_sensitive_log_arguments(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(),
            media_sources={
                "MediaAttachmentProcessor.java": "\n".join(
                    [
                        "final class MediaAttachmentProcessor {",
                        "  void log(Logger logger, Request request) {",
                        "    log(logger, \"media content=\" + request.content());",
                        "  }",
                        "}",
                    ]
                ),
                "MediaRetryRuntime.java": "\n".join(
                    [
                        "final class MediaRetryRuntime {",
                        "  void log(Logger logger, Request request) {",
                        "    String message = \"retry talker=\" + request.talker();",
                        "    logger.log(message);",
                        "  }",
                        "}",
                    ]
                ),
            },
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "sensitive_media_log_context",
                "file": "MediaAttachmentProcessor.java",
                "line": 3,
                "tokens": ["content"],
            },
            result["errors"],
        )
        self.assertIn(
            {
                "type": "sensitive_media_log_context",
                "file": "MediaRetryRuntime.java",
                "line": 3,
                "tokens": ["talker"],
            },
            result["errors"],
        )

    def test_media_log_safety_allows_operational_log_arguments(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(),
            media_sources={
                "MediaPayloadWriter.java": "\n".join(
                    [
                        "final class MediaPayloadWriter {",
                        "  void log(Logger logger, int type, File file) {",
                        "    log(logger, \"skip media upload type=\" + type",
                        "        + \" size=\" + file.length()",
                        "        + \" file=\" + file.getName());",
                        "  }",
                        "}",
                    ]
                ),
                "MediaRetryLogLine.java": "\n".join(
                    [
                        "final class MediaRetryLogLine {",
                        "  String failed(int type, long chatRecordId, int attempt) {",
                        "    return \"media retry failed type=\" + type",
                        "        + \" msgId=\" + chatRecordId",
                        "        + \" attempt=\" + attempt;",
                        "  }",
                        "}",
                    ]
                ),
            },
        )

        self.assertTrue(result["ok"], result)

    def test_sensitive_log_expression_rejects_raw_ids(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(),
            log_safety_sources={
                "HookEntry.java": "\n".join(
                    [
                        "final class HookEntry {",
                        "  void report(String wxid, MessagePayload payload) {",
                        "    log(\"identity wxid=\" + wxid);",
                        "    log(\"reported revoke chat=\" + payload.chatId);",
                        "  }",
                        "}",
                    ]
                ),
            },
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "raw_sensitive_log_expression",
                "file": "HookEntry.java",
                "line": 3,
                "items": ["raw_wxid"],
            },
            result["errors"],
        )
        self.assertIn(
            {
                "type": "raw_sensitive_log_expression",
                "file": "HookEntry.java",
                "line": 4,
                "items": ["raw_payload_chat_id"],
            },
            result["errors"],
        )

    def test_sensitive_log_expression_allows_redacted_ids(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(),
            log_safety_sources={
                "HookEntry.java": "\n".join(
                    [
                        "final class HookEntry {",
                        "  void report(String wxid, MessagePayload payload) {",
                        "    log(\"identity wxid=\" + redactedId(wxid));",
                        "    log(\"reported revoke chat=\" + redactedId(payload.chatId));",
                        "  }",
                        "}",
                    ]
                ),
                "BridgeConfig.java": "\n".join(
                    [
                        "final class BridgeConfig {",
                        "  void report(BridgeConfig config) {",
                        "    BridgeLogger.log(\"selfWxid=\" + redactedId(config.selfWxid)",
                        "        + \" apiKey=\" + \"<set>\");",
                        "  }",
                        "}",
                    ]
                ),
            },
        )

        self.assertTrue(result["ok"], result)

    def test_sensitive_log_expression_applies_to_any_module_java_source(self) -> None:
        result = boundary.validate_media_boundary(
            hook_source(),
            log_safety_sources={
                "outbox/OutboxWorker.java": "\n".join(
                    [
                        "final class OutboxWorker {",
                        "  void report(BridgeConfig config) {",
                        "    BridgeLogger.log(\"api=\" + config.apiKey);",
                        "  }",
                        "}",
                    ]
                ),
            },
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "raw_sensitive_log_expression",
                "file": "outbox/OutboxWorker.java",
                "line": 3,
                "items": ["raw_config_api_key"],
            },
            result["errors"],
        )


if __name__ == "__main__":
    unittest.main()
