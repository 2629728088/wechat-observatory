#!/usr/bin/env python3
"""Report Android build toolchain readiness for the LSPosed module.

This script is intentionally read-only. It does not download Gradle, install
SDK packages, build APKs, or touch connected devices.
"""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
ANDROID_MODULE = ROOT / "android-module"
APP_BUILD = ANDROID_MODULE / "app" / "build.gradle"
APK_OUTPUT_DIR = ANDROID_MODULE / "app" / "build" / "outputs" / "apk" / "debug"


def command_path(name: str) -> str:
    return shutil.which(name) or ""


def file_count(paths: list[Path]) -> int:
    return sum(1 for path in paths if path.is_file())


def read_compile_sdk(app_build: Path = APP_BUILD) -> int:
    if not app_build.is_file():
        return 0
    match = re.search(r"\bcompileSdk\s+(\d+)", app_build.read_text(encoding="utf-8", errors="ignore"))
    return int(match.group(1)) if match else 0


def default_android_roots() -> list[Path]:
    roots: list[Path] = []
    for name in ["WECHAT_ANDROID_SDK_ROOT", "ANDROID_HOME", "ANDROID_SDK_ROOT"]:
        value = os.environ.get(name, "")
        if value:
            roots.append(Path(value))
    roots.extend(
        [
            Path("/usr/lib/android-sdk"),
            Path("/opt/android-sdk"),
            Path("/root/android-sdk"),
            Path("/root/Android/Sdk"),
            Path("/tmp/wechat-android-sdk"),
        ]
    )
    return unique_paths(roots)


def unique_paths(paths: list[Path]) -> list[Path]:
    seen: set[str] = set()
    result: list[Path] = []
    for path in paths:
        key = path.expanduser().as_posix()
        if key in seen:
            continue
        seen.add(key)
        result.append(path)
    return result


def find_android_jars(roots: list[Path] | None = None) -> list[str]:
    jars: list[str] = []
    for root in roots if roots is not None else default_android_roots():
        if root.exists():
            jars.extend(str(path) for path in root.rglob("android.jar"))
    return sorted(set(jars))


def default_gradle_candidates() -> list[Path]:
    candidates: list[Path] = []
    value = os.environ.get("GRADLE_BIN", "")
    if value:
        candidates.append(Path(value))
    for root in [Path("/tmp/wechat-gradle"), Path("/tmp")]:
        if root.exists():
            candidates.extend(root.glob("gradle-*/bin/gradle"))
    return unique_paths(candidates)


def find_gradle_command(command_lookup=command_path, candidates: list[Path] | None = None) -> str:
    gradle = command_lookup("gradle")
    if gradle:
        return gradle
    search_paths = candidates if candidates is not None else default_gradle_candidates()
    for path in search_paths:
        if path.is_file():
            return str(path)
    return ""


def gradle_wrapper_files(android_module: Path = ANDROID_MODULE) -> dict[str, bool]:
    return {
        "gradlew": (android_module / "gradlew").is_file(),
        "gradlew_bat": (android_module / "gradlew.bat").is_file(),
        "wrapper_jar": (android_module / "gradle" / "wrapper" / "gradle-wrapper.jar").is_file(),
        "wrapper_properties": (android_module / "gradle" / "wrapper" / "gradle-wrapper.properties").is_file(),
    }


def gradle_wrapper_complete(wrapper: dict[str, bool]) -> bool:
    return all(wrapper.values())


def gradle_wrapper_present(wrapper: dict[str, bool]) -> bool:
    return any(wrapper.values())


def apk_outputs(apk_output_dir: Path = APK_OUTPUT_DIR, root: Path = ROOT, command_lookup=command_path) -> list[dict[str, Any]]:
    outputs: list[dict[str, Any]] = []
    if not apk_output_dir.exists():
        return outputs
    for path in sorted(apk_output_dir.glob("*.apk")):
        item: dict[str, Any] = {
            "path": str(path.relative_to(root)),
            "size": path.stat().st_size,
        }
        badging = apk_badging(path, command_lookup("aapt"))
        if badging:
            item.update(badging)
        outputs.append(item)
    return outputs


def apk_badging(path: Path, aapt: str = "") -> dict[str, Any]:
    aapt = aapt or command_path("aapt")
    if not aapt:
        return {}
    try:
        proc = subprocess.run(
            [aapt, "dump", "badging", str(path)],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            timeout=20,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return {}
    if proc.returncode != 0:
        return {}
    text = proc.stdout
    result: dict[str, Any] = {}
    package = re.search(r"package: name='([^']+)'.*versionCode='([^']+)'.*versionName='([^']+)'", text)
    if package:
        result["package"] = package.group(1)
        result["version_code"] = package.group(2)
        result["version_name"] = package.group(3)
    sdk = re.search(r"sdkVersion:'([^']+)'", text)
    target = re.search(r"targetSdkVersion:'([^']+)'", text)
    if sdk:
        result["min_sdk"] = sdk.group(1)
    if target:
        result["target_sdk"] = target.group(1)
    result["debuggable"] = "application-debuggable" in text
    return result


def has_compile_sdk_jar(android_jars: list[str], compile_sdk: int) -> bool:
    if not compile_sdk:
        return False
    suffix = f"/android-{compile_sdk}/android.jar"
    return any(Path(path).as_posix().endswith(suffix) for path in android_jars)


def collect_report(
    root: Path = ROOT,
    android_module: Path = ANDROID_MODULE,
    app_build: Path = APP_BUILD,
    apk_output_dir: Path = APK_OUTPUT_DIR,
    android_roots: list[Path] | None = None,
    gradle_candidates: list[Path] | None = None,
    command_lookup=command_path,
) -> dict[str, Any]:
    commands = {name: command_lookup(name) for name in ["java", "javac", "gradle", "adb", "aapt", "aapt2"]}
    commands["gradle"] = find_gradle_command(command_lookup, gradle_candidates)
    wrapper = gradle_wrapper_files(android_module)
    roots = android_roots if android_roots is not None else default_android_roots()
    android_jars = find_android_jars(roots)
    compile_sdk = read_compile_sdk(app_build)
    compile_sdk_jar_present = has_compile_sdk_jar(android_jars, compile_sdk)
    wrapper_complete = gradle_wrapper_complete(wrapper)

    blockers: list[str] = []
    if gradle_wrapper_present(wrapper) and not wrapper_complete:
        blockers.append("incomplete_gradle_wrapper")
    if not commands["gradle"] and not wrapper_complete:
        blockers.append("missing_gradle_or_project_wrapper")
    if compile_sdk and not compile_sdk_jar_present:
        blockers.append(f"missing_android_platform_android_jar_for_compileSdk_{compile_sdk}")
    if not commands["java"]:
        blockers.append("missing_java")
    if not commands["aapt2"]:
        blockers.append("missing_aapt2")

    apks = apk_outputs(apk_output_dir, root, command_lookup)
    return {
        "ok": True,
        "build_ready": not blockers,
        "blockers": blockers,
        "compile_sdk": compile_sdk,
        "commands": {name: bool(path) for name, path in commands.items()},
        "command_paths": commands,
        "android_roots": [str(path) for path in roots],
        "gradle_candidate_paths": [str(path) for path in (gradle_candidates if gradle_candidates is not None else default_gradle_candidates())],
        "gradle_wrapper": wrapper,
        "gradle_wrapper_complete": wrapper_complete,
        "android_jar_count": len(android_jars),
        "compile_sdk_jar_present": compile_sdk_jar_present,
        "apk_output_count": len(apks),
        "apk_outputs": apks,
        "note": "Existing APK outputs are historical artifacts; build_ready requires Gradle and compileSdk android.jar.",
    }


def main() -> int:
    result = collect_report()
    print(json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
