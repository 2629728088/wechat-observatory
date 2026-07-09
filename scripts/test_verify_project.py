#!/usr/bin/env python3
"""Unit tests for the standard verification orchestrator."""

from __future__ import annotations

import argparse
import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import verify_project as verify


def args(**overrides) -> argparse.Namespace:
    values = {
        "live_api_key_from_admin": True,
        "require_live_ready": True,
        "live_device": "",
        "base_url": "http://127.0.0.1:8088",
        "ws_timeout": "8",
        "live_message_limit": "50",
        "live_message_pages": "1",
        "live_require_message_kind": "",
        "live_require_media_kind": "",
        "live_require_appmsg_subtype": "",
        "live_require_fixture": "",
    }
    values.update(overrides)
    return argparse.Namespace(**values)


class VerifyProjectTests(unittest.TestCase):
    def test_dotenv_value_reads_quoted_values_from_project_root(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / ".env").write_text('OTHER=value\nBRIDGE_ADMIN_PASSWORD="admin pass"\n', encoding="utf-8")

            with patch.object(verify, "ROOT", root):
                self.assertEqual(verify.dotenv_value("BRIDGE_ADMIN_PASSWORD"), "admin pass")
                self.assertEqual(verify.dotenv_value("MISSING"), "")

    def test_enabled_api_key_helpers_ignore_disabled_keys_and_legacy_device_fields(self) -> None:
        self.assertEqual(verify.enabled_api_key_value({"enabled": False, "api_key": "key-a"}), "")
        self.assertEqual(verify.enabled_api_key_value({"enabled": True, "api_key": "key-a"}), "key-a")
        self.assertEqual(verify.enabled_api_key_value({"code": "legacy-code"}), "legacy-code")
        self.assertEqual(verify.api_key_device({"device_id": "device-a"}), "device-a")
        self.assertEqual(verify.api_key_device({"bound_device": "device-b"}), "device-b")

    def test_resolve_live_api_key_can_use_env_without_admin_lookup(self) -> None:
        with patch.dict(verify.os.environ, {"WECHAT_OBSERVATORY_API_KEY": "public-key"}, clear=True):
            self.assertEqual(verify.resolve_live_api_key(args(live_api_key_from_admin=False)), "public-key")

    def test_resolve_live_api_key_selects_enabled_key_bound_to_ready_module(self) -> None:
        responses = {
            "/api/modules/status": {
                "modules": [
                    {"device": "phone-a", "runtime_status": "pending"},
                    {"device": "phone-b", "runtime_status": "ready"},
                ]
            },
            "/api/api-keys?limit=100": {
                "api_keys": [
                    {"api_key": "disabled-key", "enabled": False, "device": "phone-b"},
                    {"api_key": "wrong-device-key", "enabled": True, "device": "phone-a"},
                    {"api_key": "ready-key", "enabled": True, "device": "phone-b"},
                ]
            },
        }

        with patch.dict(verify.os.environ, {"WECHAT_OBSERVATORY_ADMIN_PASSWORD": "admin"}, clear=True):
            with patch.object(verify, "request_admin_json", side_effect=lambda _base, _password, path: responses[path]):
                self.assertEqual(verify.resolve_live_api_key(args()), "ready-key")

    def test_resolve_live_api_key_reports_no_ready_bound_key(self) -> None:
        responses = {
            "/api/modules/status": {"modules": [{"device": "phone-a", "runtime_status": "pending"}]},
            "/api/api-keys?limit=100": {"api_keys": [{"api_key": "key-a", "enabled": True, "device": "phone-a"}]},
        }

        with patch.dict(verify.os.environ, {"WECHAT_OBSERVATORY_ADMIN_PASSWORD": "admin"}, clear=True):
            with patch.object(verify, "request_admin_json", side_effect=lambda _base, _password, path: responses[path]):
                with self.assertRaisesRegex(RuntimeError, "ready module"):
                    verify.resolve_live_api_key(args())

    def test_android_build_command_uses_project_wrapper_when_local_gradle_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            android_module = Path(tmp) / "android-module"
            android_module.mkdir(parents=True)
            (android_module / "gradlew").write_text("#!/bin/sh\n", encoding="utf-8")

            with patch.object(verify, "ANDROID_MODULE", android_module):
                with patch.object(verify.os, "name", "posix"):
                    with patch.object(verify.shutil, "which", return_value=None):
                        with patch.object(verify, "gradle_candidate_paths", return_value=[]):
                            command = verify.android_build_command()
            self.assertIsNotNone(command)
            self.assertEqual(command[0][0], str(android_module / "gradlew"))
            self.assertEqual(command[0][1], ":app:assembleDebug")
            self.assertEqual(command[1], android_module)

        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(verify, "ANDROID_MODULE", Path(tmp) / "android-module"):
                with patch.object(verify.os, "name", "posix"):
                    with patch.object(verify.shutil, "which", return_value=None):
                        self.assertIsNone(verify.android_build_command())

    def test_android_build_command_prefers_local_gradle_over_wrapper(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            android_module = root / "android-module"
            android_module.mkdir(parents=True)
            (android_module / "gradlew").write_text("#!/bin/sh\n", encoding="utf-8")
            gradle = root / "gradle-8.10.2" / "bin" / "gradle"
            gradle.parent.mkdir(parents=True)
            gradle.write_text("#!/bin/sh\n", encoding="utf-8")

            with patch.object(verify, "ANDROID_MODULE", android_module):
                with patch.object(verify.os, "name", "posix"):
                    with patch.object(verify.shutil, "which", return_value=None):
                        with patch.object(verify, "gradle_candidate_paths", return_value=[gradle]):
                            command = verify.android_build_command()

        self.assertIsNotNone(command)
        self.assertEqual(command[0][0], str(gradle))

    def test_android_build_command_uses_env_gradle_and_sdk_candidates(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            android_module = root / "android-module"
            android_module.mkdir(parents=True)
            gradle = root / "gradle-8.10.2" / "bin" / "gradle"
            gradle.parent.mkdir(parents=True)
            gradle.write_text("#!/bin/sh\n", encoding="utf-8")
            sdk = root / "sdk"
            (sdk / "platforms" / "android-35").mkdir(parents=True)

            with patch.object(verify, "ANDROID_MODULE", android_module):
                with patch.object(verify.os, "name", "posix"):
                    with patch.object(verify.shutil, "which", return_value=None):
                        with patch.dict(verify.os.environ, {"GRADLE_BIN": str(gradle), "WECHAT_ANDROID_SDK_ROOT": str(sdk)}, clear=True):
                            command = verify.android_build_command()
                            env = verify.android_build_env()

        self.assertIsNotNone(command)
        self.assertEqual(command[0][0], str(gradle))
        self.assertEqual(command[0][1], ":app:assembleDebug")
        self.assertEqual(env["ANDROID_HOME"], str(sdk))
        self.assertEqual(env["ANDROID_SDK_ROOT"], str(sdk))

    def test_bridge_build_command_outputs_to_requested_temp_dir(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            output_dir = Path(tmp)

            with patch.object(verify.os, "name", "posix"):
                command = verify.bridge_build_command(output_dir)

        self.assertEqual(command[:3], ["go", "build", "-o"])
        self.assertEqual(command[3], str(output_dir / "wechat-observatory-bridge"))
        self.assertEqual(command[4], "./cmd/bridge")

    def test_main_runs_bridge_build_unless_skipped(self) -> None:
        def fake_run_step(name, command, cwd, env=None):
            called_steps.append(name)
            return verify.StepResult(name, "passed", 0.0, verify.display_command(command))

        base_argv = [
            "verify_project.py",
            "--skip-go",
            "--skip-web-tests",
            "--skip-web-build",
            "--skip-diff-check",
        ]

        called_steps: list[str] = []
        with patch.object(verify.sys, "argv", base_argv):
            with patch.object(verify, "run_step", side_effect=fake_run_step):
                with patch.object(verify, "android_build_command", return_value=None):
                    with contextlib.redirect_stdout(io.StringIO()):
                        self.assertEqual(verify.main(), 0)
        self.assertIn("Source hygiene", called_steps)
        self.assertIn("Source hygiene unit tests", called_steps)
        self.assertIn("Bridge binary build", called_steps)

        called_steps = []
        with patch.object(verify.sys, "argv", [*base_argv, "--skip-bridge-build"]):
            with patch.object(verify, "run_step", side_effect=fake_run_step):
                with patch.object(verify, "android_build_command", return_value=None):
                    with contextlib.redirect_stdout(io.StringIO()):
                        self.assertEqual(verify.main(), 0)
        self.assertNotIn("Bridge binary build", called_steps)

    def test_source_hygiene_is_independent_from_docs_skip(self) -> None:
        def fake_run_step(name, command, cwd, env=None):
            called_steps.append(name)
            return verify.StepResult(name, "passed", 0.0, verify.display_command(command))

        base_argv = [
            "verify_project.py",
            "--skip-go",
            "--skip-docs",
            "--skip-web-tests",
            "--skip-web-build",
            "--skip-bridge-build",
            "--skip-diff-check",
        ]

        called_steps: list[str] = []
        with patch.object(verify.sys, "argv", base_argv):
            with patch.object(verify, "run_step", side_effect=fake_run_step):
                with patch.object(verify, "android_build_command", return_value=None):
                    with contextlib.redirect_stdout(io.StringIO()):
                        self.assertEqual(verify.main(), 0)
        self.assertIn("Source hygiene", called_steps)
        self.assertNotIn("Public API docs", called_steps)
        self.assertIn("Android module structure", called_steps)

        called_steps = []
        with patch.object(verify.sys, "argv", [*base_argv, "--skip-source-hygiene"]):
            with patch.object(verify, "run_step", side_effect=fake_run_step):
                with patch.object(verify, "android_build_command", return_value=None):
                    with contextlib.redirect_stdout(io.StringIO()):
                        self.assertEqual(verify.main(), 0)
        self.assertNotIn("Source hygiene", called_steps)

    def test_static_checks_have_their_own_skip_flag(self) -> None:
        def fake_run_step(name, command, cwd, env=None):
            called_steps.append(name)
            return verify.StepResult(name, "passed", 0.0, verify.display_command(command))

        base_argv = [
            "verify_project.py",
            "--skip-go",
            "--skip-docs",
            "--skip-source-hygiene",
            "--skip-web-tests",
            "--skip-web-build",
            "--skip-bridge-build",
            "--skip-diff-check",
        ]

        called_steps: list[str] = []
        with patch.object(verify.sys, "argv", base_argv):
            with patch.object(verify, "run_step", side_effect=fake_run_step):
                with patch.object(verify, "android_build_command", return_value=None):
                    with contextlib.redirect_stdout(io.StringIO()):
                        self.assertEqual(verify.main(), 0)
        self.assertIn("Android module structure", called_steps)
        self.assertIn("Python syntax", called_steps)
        self.assertNotIn("Public API docs", called_steps)

        called_steps = []
        with patch.object(verify.sys, "argv", [*base_argv, "--skip-static-checks"]):
            with patch.object(verify, "run_step", side_effect=fake_run_step):
                with patch.object(verify, "android_build_command", return_value=None):
                    with contextlib.redirect_stdout(io.StringIO()):
                        self.assertEqual(verify.main(), 0)
        self.assertNotIn("Android module structure", called_steps)
        self.assertNotIn("Python syntax", called_steps)

    def test_live_contract_command_forwards_required_coverage_flags(self) -> None:
        command = verify.live_contract_command(
            args(
                live_require_message_kind="text,image",
                live_require_media_kind="image,emoji",
                live_require_appmsg_subtype="mini-program",
                live_require_fixture="image,voice,chat-history",
                live_message_limit="200",
                live_message_pages="5",
            )
        )

        self.assertIn("--require-ready-module", command)
        self.assertIn("--message-limit", command)
        self.assertIn("200", command)
        self.assertIn("--message-pages", command)
        self.assertIn("5", command)
        self.assertIn("--require-message-kind", command)
        self.assertIn("text,image", command)
        self.assertIn("--require-media-kind", command)
        self.assertIn("image,emoji", command)
        self.assertIn("--require-appmsg-subtype", command)
        self.assertIn("mini-program", command)
        self.assertIn("--require-fixture", command)
        self.assertIn("image,voice,chat-history", command)

    def test_finish_returns_failure_when_any_step_failed(self) -> None:
        results = [
            verify.StepResult("ok", "passed", 0.1, "true"),
            verify.StepResult("bad", "failed", 0.1, "false", "boom", 1),
        ]

        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(verify.finish(results, emit_json=False), 1)


if __name__ == "__main__":
    unittest.main()
