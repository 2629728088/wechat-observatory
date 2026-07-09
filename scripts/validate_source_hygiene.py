#!/usr/bin/env python3
"""Check source-like workspace files for simple hygiene issues."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path, PurePosixPath
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]
INCLUDE_EXTENSIONS = {
    ".css",
    ".go",
    ".gradle",
    ".html",
    ".js",
    ".json",
    ".jsx",
    ".md",
    ".mod",
    ".py",
    ".sh",
    ".sql",
    ".sum",
    ".toml",
    ".ts",
    ".tsx",
    ".xml",
    ".yaml",
    ".yml",
}
INCLUDE_NAMES = {".gitattributes", ".gitignore", "Dockerfile", "Makefile", "gradlew", "gradlew.bat"}
SKIP_PREFIXES = (
    "backups/",
    "internal/bridge/admin_dist/",
    "web/admin/node_modules/",
)
SKIP_PARTS = {"__pycache__", ".git"}
CONFLICT_MARKERS = ("<<<<<<< ", "=======", ">>>>>>> ")
STRAY_TEMPLATE_MARKERS = (
    ".Destination" + "}}{{end}}",
    r".Destination" + r"\}\}\{\{end\}\}",
)


def git_files(root: Path) -> list[str]:
    tracked = subprocess.check_output(["git", "ls-files"], cwd=str(root), text=True).splitlines()
    untracked = subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], cwd=str(root), text=True).splitlines()
    return sorted(set(tracked + untracked))


def should_check_path(path: str) -> bool:
    if path.startswith(SKIP_PREFIXES):
        return False
    pure = PurePosixPath(path)
    if any(part in SKIP_PARTS for part in pure.parts):
        return False
    return pure.suffix in INCLUDE_EXTENSIONS or pure.name in INCLUDE_NAMES


def check_text(path: str, text: str) -> list[dict[str, object]]:
    findings: list[dict[str, object]] = []
    for line_number, raw_line in enumerate(text.splitlines(), 1):
        line = raw_line.rstrip("\r\n")
        if line.rstrip(" \t") != line:
            findings.append({"path": path, "line": line_number, "code": "trailing_whitespace"})
        if line.startswith(CONFLICT_MARKERS):
            findings.append({"path": path, "line": line_number, "code": "conflict_marker"})
        if any(marker in line for marker in STRAY_TEMPLATE_MARKERS):
            findings.append({"path": path, "line": line_number, "code": "stray_template_marker"})
    return findings


def validate_paths(root: Path, paths: Iterable[str]) -> dict[str, object]:
    checked = 0
    skipped = 0
    findings: list[dict[str, object]] = []
    for rel in sorted(set(paths)):
        if not should_check_path(rel):
            skipped += 1
            continue
        path = root / rel
        if not path.is_file():
            skipped += 1
            continue
        data = path.read_bytes()
        if b"\0" in data:
            skipped += 1
            continue
        checked += 1
        findings.extend(check_text(rel, data.decode("utf-8", errors="replace")))
    return {"ok": not findings, "checked": checked, "skipped": skipped, "finding_count": len(findings), "findings": findings}


def main() -> int:
    result = validate_paths(ROOT, git_files(ROOT))
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0 if result["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
