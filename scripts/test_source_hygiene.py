#!/usr/bin/env python3
"""Unit tests for source hygiene validation."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import validate_source_hygiene as hygiene


class SourceHygieneTests(unittest.TestCase):
    def test_should_check_source_like_paths_and_skip_generated_files(self) -> None:
        self.assertTrue(hygiene.should_check_path("scripts/verify_project.py"))
        self.assertTrue(hygiene.should_check_path(".gitignore"))
        self.assertTrue(hygiene.should_check_path("android-module/gradlew"))
        self.assertTrue(hygiene.should_check_path("android-module/gradlew.bat"))
        self.assertTrue(hygiene.should_check_path("Dockerfile"))
        self.assertTrue(hygiene.should_check_path("docs/message-fixtures/public-api-v1.text.json"))
        self.assertFalse(hygiene.should_check_path("backups/old.go"))
        self.assertFalse(hygiene.should_check_path("scripts/__pycache__/tool.cpython-312.pyc"))
        self.assertFalse(hygiene.should_check_path("internal/bridge/admin_dist/assets/index.js"))
        self.assertFalse(hygiene.should_check_path("docs/example.png"))

    def test_check_text_reports_trailing_whitespace_and_conflict_markers_without_content(self) -> None:
        findings = hygiene.check_text("scripts/example.py", "ok\nbad \n<<<<<<< HEAD\n")

        self.assertEqual(
            findings,
            [
                {"path": "scripts/example.py", "line": 2, "code": "trailing_whitespace"},
                {"path": "scripts/example.py", "line": 3, "code": "conflict_marker"},
            ],
        )

    def test_check_text_reports_stray_template_markers(self) -> None:
        findings = hygiene.check_text(".gitignore", "ok\n .Destination\\}\\}\\{\\{end\\}\\}\n")

        self.assertEqual(
            findings,
            [
                {"path": ".gitignore", "line": 2, "code": "stray_template_marker"},
            ],
        )

    def test_validate_paths_skips_binary_files_and_reports_summary(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "scripts").mkdir()
            (root / "scripts" / "ok.py").write_text("print('ok')\n", encoding="utf-8")
            (root / "scripts" / "bad.py").write_text("print('bad') \n", encoding="utf-8")
            (root / "scripts" / "binary.py").write_bytes(b"\0binary")

            result = hygiene.validate_paths(root, ["scripts/ok.py", "scripts/bad.py", "scripts/binary.py", "docs/image.png"])

        self.assertFalse(result["ok"])
        self.assertEqual(result["checked"], 2)
        self.assertEqual(result["skipped"], 2)
        self.assertEqual(result["finding_count"], 1)
        self.assertEqual(result["findings"][0]["path"], "scripts/bad.py")


if __name__ == "__main__":
    unittest.main()
