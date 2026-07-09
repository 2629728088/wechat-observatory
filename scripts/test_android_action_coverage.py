#!/usr/bin/env python3
"""Unit tests for Android action coverage validation."""

from __future__ import annotations

import unittest

import validate_android_action_coverage as coverage


def events_source(*kinds: str) -> str:
    lines = []
    for index, kind in enumerate(kinds):
        name = "".join(part.title() for part in kind.split("_"))
        lines.append(f'const OutboxKind{name} = "{kind}"')
        if index == 0:
            lines.append(f'const OutboxKind{name}Duplicate = "{kind}"')
    return "\n".join(lines)


def hook_source(
    *kinds: str,
    include_unknown_failed_ack: bool = True,
    include_terminal_ack: bool = True,
    omit_media_requirements: tuple[str, ...] = (),
    include_parallel_dispatch: bool = True,
    include_send_lock: bool = True,
    include_inbound_image_handling: bool = True,
) -> str:
    dispatch_lines = ['kind = isBlank(item.optString("media_kind", "")) ? "text" : kind;']
    handler_definitions = []
    for kind in kinds:
        method = coverage.ACTION_METHODS[kind]
        dispatch_lines.append(f'if ("{kind}".equals(kind)) {{ result = {method}item); }}')
        if kind != "text":
            handler_definitions.append(handler_definition(kind, method[:-1], kind not in omit_media_requirements))
    if include_unknown_failed_ack:
        dispatch_lines.append('result = SendResult.failed("unsupported outbox kind: " + kind);')
    ack_lines = []
    if include_terminal_ack:
        ack_lines.extend(['ack.put("status", result.ok ? "sent" : "failed");', 'ack.put("error", result.error);'])
    parallel_lines = (
        [
            "handleOutboxWorkItemsParallel(workItems, classLoader, config, 4);",
            "outboxLaneKey(item);",
            "normalizedOutboxParallelism(4, 2);",
            "Executors.newFixedThreadPool(2);",
        ]
        if include_parallel_dispatch
        else []
    )
    send_lock_lines = (
        [
            "private static final Object WECHAT_SEND_LOCK = new Object();",
            "private static void callOnMainThread() { synchronized (WECHAT_SEND_LOCK) {} }",
        ]
        if include_send_lock
        else []
    )
    inbound_image_lines = (
        [
            "MediaFiles.isImageMessageType(type);",
            "MediaAttachmentProcessorFactory.create(type, xml);",
            "MediaFiles.shouldLogMissingMedia(type);",
            "WechatImageInfoResolver imageInfoResolver;",
            "WechatImageMediaResolver imageMediaResolver;",
            "log(\"image named lookup missed; skip ambiguous recent fallback\");",
            "WechatImageSearchPlan.addCandidateVariants(imageNames, candidate);",
            "hasImageHeader(file);",
            "findClass(classLoader, \"m11.t0\");",
            "new WechatImageDownloadCallbackFactory();",
            "log(\"image NetSceneGetMsgImg enqueued\");",
            "new ImageDownloadRequestTracker(1024);",
            "ImageDownloadCoordinator coordinator;",
        ]
        if include_inbound_image_handling
        else []
    )
    return "\n".join(
        [
            *send_lock_lines,
            *inbound_image_lines,
            "private static void handleOutboxItems(JSONArray items) {",
            *parallel_lines,
            "}",
            "private static SendResult executeOutboxItem(JSONObject item) {",
            *dispatch_lines,
            "}",
            "private static void outboxAck(SendResult result) {",
            *ack_lines,
            "}",
            *handler_definitions,
        ]
    )


def handler_definition(kind: str, method_name: str, include_media_requirements: bool) -> str:
    if kind not in coverage.MEDIA_ACTION_REQUIREMENTS or not include_media_requirements:
        return f"private static SendResult {method_name}(JSONObject item) {{ return SendResult.ok(); }}"
    send_method = {
        "image": "sendImage(",
        "video": "sendVideo(",
        "voice": "sendVoice(",
        "file": "sendFile(",
    }[kind]
    voice_check = 'if (!MediaFiles.isSupportedVoiceMediaFile(mediaFile, "")) { return SendResult.failed("voice media must be AMR or SILK"); }' if kind == "voice" else ""
    return "\n".join(
        [
            f"private static SendResult {method_name}(JSONObject item) {{",
            "String mediaUrl = \"\";",
            "OutboxMediaFilePreparer.fromRuntime(config, media, logger);",
            f'if (isBlank(mediaUrl)) {{ return SendResult.failed("{kind} media_url is required"); }}',
            "File mediaFile = downloadPreparedMedia(config, mediaUrl, \"\");",
            f'if (mediaFile == null || !mediaFile.isFile()) {{ return SendResult.failed("{kind} media download produced empty file"); }}',
            voice_check,
            f"{send_method}config, classLoader, wxid, mediaFile);",
            "media.cleanup(kind);",
            "file.delete();",
            "return SendResult.ok();",
            "}",
        ]
    )


class AndroidActionCoverageTests(unittest.TestCase):
    def test_complete_action_dispatch_passes(self) -> None:
        kinds = tuple(coverage.ACTION_METHODS)

        result = coverage.validate_action_coverage(events_source(*kinds), hook_source(*kinds))

        self.assertTrue(result["ok"], result)
        self.assertEqual(result["kind_count"], len(kinds))
        self.assertEqual(result["kinds"], list(kinds))
        self.assertEqual(result["errors"], [])

    def test_missing_dispatch_reports_specific_kind(self) -> None:
        result = coverage.validate_action_coverage(
            events_source("text", "image"),
            hook_source("text"),
        )

        self.assertFalse(result["ok"], result)
        self.assertIn({"type": "missing_android_dispatch", "kinds": ["image"]}, result["errors"])
        self.assertIn({"type": "missing_android_handler_definition", "kinds": ["image"]}, result["errors"])

    def test_unknown_outbox_kind_must_ack_failed(self) -> None:
        result = coverage.validate_action_coverage(
            events_source("text"),
            hook_source("text", include_unknown_failed_ack=False),
        )

        self.assertFalse(result["ok"], result)
        self.assertIn({"type": "missing_unknown_kind_failed_ack"}, result["errors"])

    def test_terminal_ack_must_include_status_and_error(self) -> None:
        result = coverage.validate_action_coverage(
            events_source("text"),
            hook_source("text", include_terminal_ack=False),
        )

        self.assertFalse(result["ok"], result)
        self.assertIn({"type": "missing_terminal_ack_status_or_error"}, result["errors"])

    def test_unknown_gateway_kind_needs_validator_mapping(self) -> None:
        result = coverage.validate_action_coverage(
            events_source("text", "future_kind"),
            hook_source("text"),
        )

        self.assertFalse(result["ok"], result)
        self.assertIn({"type": "missing_validator_mapping", "kinds": ["future_kind"]}, result["errors"])


    def test_outbox_dispatch_must_keep_parallel_lane_executor(self) -> None:
        result = coverage.validate_action_coverage(
            events_source("text"),
            hook_source("text", include_parallel_dispatch=False),
        )

        self.assertFalse(result["ok"], result)
        self.assertIn({"type": "missing_parallel_outbox_lane_dispatch"}, result["errors"])

    def test_parallel_outbox_must_keep_wechat_send_lock(self) -> None:
        result = coverage.validate_action_coverage(
            events_source("text"),
            hook_source("text", include_send_lock=False),
        )

        self.assertFalse(result["ok"], result)
        self.assertIn({"type": "missing_wechat_send_lock"}, result["errors"])

    def test_inbound_images_must_preserve_raw_xml_and_missing_media_logs(self) -> None:
        result = coverage.validate_action_coverage(
            events_source("text"),
            hook_source("text", include_inbound_image_handling=False),
        )

        self.assertFalse(result["ok"], result)
        self.assertIn(
            {
                "type": "missing_inbound_image_requirements",
                "items": [
                    "image_raw_xml_type",
                    "image_embedded_xml_normalization",
                    "image_missing_media_log",
                    "image_info_table_lookup",
                    "image_priority_search",
                    "image_ambiguous_recent_skip",
                    "image_candidate_variants",
                    "image_header_validation",
                    "image_net_scene_download",
                    "image_download_callback",
                    "image_download_enqueue_log",
                    "image_download_dedup",
                    "image_download_coordinator",
                ],
            },
            result["errors"],
        )

    def test_media_actions_must_download_validate_send_and_cleanup_files(self) -> None:
        result = coverage.validate_action_coverage(
            events_source("text", "image"),
            hook_source("text", "image", omit_media_requirements=("image",)),
        )

        self.assertFalse(result["ok"], result)
        media_errors = [item for item in result["errors"] if item.get("type") == "missing_media_action_requirements"]
        self.assertEqual(len(media_errors), 1)
        self.assertEqual(media_errors[0]["items"][0]["kind"], "image")
        self.assertIn("OutboxMediaFilePreparer.fromRuntime(", media_errors[0]["items"][0]["missing"])


if __name__ == "__main__":
    unittest.main()
