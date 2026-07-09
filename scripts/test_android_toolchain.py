#!/usr/bin/env python3
"""Unit tests for the Android toolchain readiness reporter."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import validate_android_toolchain as toolchain


def make_project(root: Path, compile_sdk: int = 35) -> tuple[Path, Path, Path]:
    android_module = root / "android-module"
    app_build = android_module / "app" / "build.gradle"
    apk_dir = android_module / "app" / "build" / "outputs" / "apk" / "debug"
    app_build.parent.mkdir(parents=True)
    apk_dir.mkdir(parents=True)
    app_build.write_text(f'android {{\n    compileSdk {compile_sdk}\n}}\n', encoding="utf-8")
    return android_module, app_build, apk_dir


def command_lookup(paths: dict[str, str]):
    return lambda name: paths.get(name, "")


class AndroidToolchainReportTests(unittest.TestCase):
    def test_missing_gradle_and_android_platform_are_reported_as_blockers(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            android_module, app_build, apk_dir = make_project(root)
            report = toolchain.collect_report(
                root=root,
                android_module=android_module,
                app_build=app_build,
                apk_output_dir=apk_dir,
                android_roots=[],
                gradle_candidates=[],
                command_lookup=command_lookup({"java": "/bin/java", "javac": "/bin/javac", "aapt2": "/bin/aapt2"}),
            )

        self.assertFalse(report["build_ready"])
        self.assertIn("missing_gradle_or_project_wrapper", report["blockers"])
        self.assertIn("missing_android_platform_android_jar_for_compileSdk_35", report["blockers"])
        self.assertNotIn("missing_java", report["blockers"])
        self.assertNotIn("missing_aapt2", report["blockers"])

    def test_project_wrapper_and_matching_android_jar_make_toolchain_ready(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            android_module, app_build, apk_dir = make_project(root)
            wrapper_dir = android_module / "gradle" / "wrapper"
            wrapper_dir.mkdir(parents=True)
            (android_module / "gradlew").write_text("#!/bin/sh\n", encoding="utf-8")
            (android_module / "gradlew.bat").write_text("@echo off\n", encoding="utf-8")
            (wrapper_dir / "gradle-wrapper.jar").write_bytes(b"jar")
            (wrapper_dir / "gradle-wrapper.properties").write_text("distributionUrl=https://example.invalid/gradle.zip\n", encoding="utf-8")
            sdk = root / "sdk"
            android_jar = sdk / "platforms" / "android-35" / "android.jar"
            android_jar.parent.mkdir(parents=True)
            android_jar.write_bytes(b"android")

            report = toolchain.collect_report(
                root=root,
                android_module=android_module,
                app_build=app_build,
                apk_output_dir=apk_dir,
                android_roots=[sdk],
                command_lookup=command_lookup({"java": "/bin/java", "javac": "/bin/javac", "aapt2": "/bin/aapt2"}),
            )

        self.assertTrue(report["build_ready"])
        self.assertEqual(report["blockers"], [])
        self.assertTrue(report["gradle_wrapper"]["gradlew"])
        self.assertTrue(report["gradle_wrapper"]["gradlew_bat"])
        self.assertTrue(report["gradle_wrapper_complete"])
        self.assertTrue(report["compile_sdk_jar_present"])

    def test_partial_project_wrapper_is_reported_as_incomplete(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            android_module, app_build, apk_dir = make_project(root)
            (android_module / "gradlew").write_text("#!/bin/sh\n", encoding="utf-8")
            sdk = root / "sdk"
            android_jar = sdk / "platforms" / "android-35" / "android.jar"
            android_jar.parent.mkdir(parents=True)
            android_jar.write_bytes(b"android")

            report = toolchain.collect_report(
                root=root,
                android_module=android_module,
                app_build=app_build,
                apk_output_dir=apk_dir,
                android_roots=[sdk],
                gradle_candidates=[],
                command_lookup=command_lookup({"java": "/bin/java", "javac": "/bin/javac", "aapt2": "/bin/aapt2"}),
            )

        self.assertFalse(report["build_ready"])
        self.assertIn("incomplete_gradle_wrapper", report["blockers"])
        self.assertIn("missing_gradle_or_project_wrapper", report["blockers"])
        self.assertFalse(report["gradle_wrapper_complete"])

    def test_gradle_candidate_and_env_android_root_make_toolchain_ready(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            android_module, app_build, apk_dir = make_project(root)
            sdk = root / "sdk"
            android_jar = sdk / "platforms" / "android-35" / "android.jar"
            android_jar.parent.mkdir(parents=True)
            android_jar.write_bytes(b"android")
            gradle = root / "gradle-8.10.2" / "bin" / "gradle"
            gradle.parent.mkdir(parents=True)
            gradle.write_text("#!/bin/sh\n", encoding="utf-8")

            with patch.dict(toolchain.os.environ, {"ANDROID_HOME": str(sdk)}, clear=True):
                report = toolchain.collect_report(
                    root=root,
                    android_module=android_module,
                    app_build=app_build,
                    apk_output_dir=apk_dir,
                    android_roots=None,
                    gradle_candidates=[gradle],
                    command_lookup=command_lookup({"java": "/bin/java", "javac": "/bin/javac", "aapt2": "/bin/aapt2"}),
                )

        self.assertTrue(report["build_ready"])
        self.assertEqual(report["command_paths"]["gradle"], str(gradle))
        self.assertIn(str(sdk), report["android_roots"])
        self.assertTrue(report["compile_sdk_jar_present"])

    def test_apk_badging_is_summarized_without_building(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            android_module, app_build, apk_dir = make_project(root)
            apk = apk_dir / "app-debug.apk"
            apk.write_bytes(b"apk")
            stdout = "\n".join(
                [
                    "package: name='cc.wechat.observatory' versionCode='4' versionName='0.1.2-target-user'",
                    "sdkVersion:'23'",
                    "targetSdkVersion:'35'",
                    "application-debuggable",
                ]
            )
            with patch.object(toolchain.subprocess, "run", return_value=SimpleNamespace(returncode=0, stdout=stdout)):
                outputs = toolchain.apk_outputs(apk_dir, root, command_lookup({"aapt": "/bin/aapt"}))

        self.assertEqual(len(outputs), 1)
        self.assertEqual(outputs[0]["package"], "cc.wechat.observatory")
        self.assertEqual(outputs[0]["version_code"], "4")
        self.assertEqual(outputs[0]["min_sdk"], "23")
        self.assertEqual(outputs[0]["target_sdk"], "35")
        self.assertTrue(outputs[0]["debuggable"])


if __name__ == "__main__":
    unittest.main()
