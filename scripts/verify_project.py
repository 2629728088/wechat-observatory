#!/usr/bin/env python3
"""Run the standard wechat-observatory verification suite.

The default suite is local and non-destructive: Go tests, public protocol
validators, Android module/action coverage, frontend tests/build, Python
syntax checks, and whitespace checks. Live HTTP/WebSocket checks and Android
APK builds are opt-in because they need environment-specific credentials or
tooling.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Sequence

ROOT = Path(__file__).resolve().parents[1]
WEB_ADMIN = ROOT / "web" / "admin"
ANDROID_MODULE = ROOT / "android-module"


@dataclass
class StepResult:
    name: str
    status: str
    seconds: float
    command: str
    detail: str = ""
    returncode: int = 0


def display_command(command: Sequence[str]) -> str:
    return " ".join(command)


def npm_command(*args: str) -> list[str]:
    executable = "npm.cmd" if os.name == "nt" else "npm"
    return [executable, *args]


def bridge_build_command(output_dir: Path) -> list[str]:
    suffix = ".exe" if os.name == "nt" else ""
    return ["go", "build", "-o", str(output_dir / ("wechat-observatory-bridge" + suffix)), "./cmd/bridge"]


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


def run_step(name: str, command: Sequence[str], cwd: Path, env: dict[str, str] | None = None) -> StepResult:
    print(f"\n== {name} ==", flush=True)
    print(f"$ {display_command(command)}", flush=True)
    start = time.monotonic()
    proc = subprocess.run(list(command), cwd=str(cwd), env=env)
    seconds = round(time.monotonic() - start, 3)
    status = "passed" if proc.returncode == 0 else "failed"
    return StepResult(
        name=name,
        status=status,
        seconds=seconds,
        command=display_command(command),
        returncode=proc.returncode,
    )


def skip_step(name: str, detail: str) -> StepResult:
    print(f"\n== {name} ==", flush=True)
    print(f"skip: {detail}", flush=True)
    return StepResult(name=name, status="skipped", seconds=0.0, command="", detail=detail)


def android_build_command() -> tuple[list[str], Path] | None:
    if not ANDROID_MODULE.is_dir():
        return None
    gradle_bin = os.environ.get("GRADLE_BIN", "")
    if gradle_bin and Path(gradle_bin).is_file():
        return [gradle_bin, ":app:assembleDebug"], ANDROID_MODULE

    gradle = shutil.which("gradle")
    if gradle:
        return [gradle, ":app:assembleDebug"], ANDROID_MODULE
    for path in gradle_candidate_paths():
        if path.is_file():
            return [str(path), ":app:assembleDebug"], ANDROID_MODULE

    if os.name == "nt":
        wrapper = ANDROID_MODULE / "gradlew.bat"
        if wrapper.is_file():
            return [str(wrapper), ":app:assembleDebug"], ANDROID_MODULE
    else:
        wrapper = ANDROID_MODULE / "gradlew"
        if wrapper.is_file():
            return [str(wrapper), ":app:assembleDebug"], ANDROID_MODULE
    return None


def gradle_candidate_paths() -> list[Path]:
    candidates: list[Path] = []
    for root in [Path("/tmp/wechat-gradle"), Path("/tmp")]:
        if root.exists():
            candidates.extend(root.glob("gradle-*/bin/gradle"))
    return unique_paths(candidates)


def android_sdk_candidate_roots() -> list[Path]:
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


def first_android_sdk_root() -> str:
    for root in android_sdk_candidate_roots():
        if root.is_dir() and (root / "platforms").is_dir():
            return str(root)
    return ""


def android_build_env() -> dict[str, str]:
    env = os.environ.copy()
    sdk_root = env.get("ANDROID_HOME") or env.get("ANDROID_SDK_ROOT") or first_android_sdk_root()
    if sdk_root:
        env.setdefault("ANDROID_HOME", sdk_root)
        env.setdefault("ANDROID_SDK_ROOT", sdk_root)
    return env


def dotenv_value(name: str) -> str:
    env_path = ROOT / ".env"
    if not env_path.is_file():
        return ""
    prefix = name + "="
    for line in env_path.read_text(encoding="utf-8", errors="ignore").splitlines():
        if line.startswith(prefix):
            return line.split("=", 1)[1].strip().strip('"').strip("'")
    return ""


def absolute_url(base_url: str, path: str) -> str:
    return base_url.rstrip("/") + "/" + path.lstrip("/")


def request_admin_json(base_url: str, password: str, path: str) -> dict:
    req = urllib.request.Request(
        absolute_url(base_url, path),
        headers={"Accept": "application/json", "X-Bridge-Password": password},
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8") or "{}")
    except (urllib.error.URLError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"admin API request failed for {path}") from exc
    if not isinstance(data, dict):
        raise RuntimeError(f"admin API response is not an object for {path}")
    return data


def enabled_api_key_value(item: dict) -> str:
    if item.get("enabled") is False:
        return ""
    return str(item.get("api_key") or item.get("code") or "")


def api_key_device(item: dict) -> str:
    return str(item.get("device") or item.get("device_id") or item.get("bound_device") or "")


def resolve_live_api_key(args: argparse.Namespace) -> str:
    if not args.live_api_key_from_admin:
        return os.environ.get("WECHAT_OBSERVATORY_API_KEY", "")

    password = os.environ.get("WECHAT_OBSERVATORY_ADMIN_PASSWORD", "") or dotenv_value("BRIDGE_ADMIN_PASSWORD")
    if not password:
        raise RuntimeError("WECHAT_OBSERVATORY_ADMIN_PASSWORD or BRIDGE_ADMIN_PASSWORD in .env is required")

    ready_devices: set[str] = set()
    if args.require_live_ready or args.live_device:
        modules = request_admin_json(args.base_url, password, "/api/modules/status").get("modules") or []
        if isinstance(modules, list):
            ready_devices = {
                str(item.get("device") or "")
                for item in modules
                if isinstance(item, dict) and item.get("runtime_status") == "ready" and item.get("device")
            }

    keys = request_admin_json(args.base_url, password, "/api/api-keys?limit=100").get("api_keys") or []
    if not isinstance(keys, list):
        raise RuntimeError("admin API keys response is not a list")

    for item in keys:
        if not isinstance(item, dict):
            continue
        value = enabled_api_key_value(item)
        if not value:
            continue
        device = api_key_device(item)
        if args.live_device and device != args.live_device:
            continue
        if args.require_live_ready and device not in ready_devices:
            continue
        return value

    if args.require_live_ready:
        raise RuntimeError("no enabled API key is bound to a ready module")
    if args.live_device:
        raise RuntimeError("no enabled API key is bound to --live-device")
    raise RuntimeError("no enabled API key found")


def add_result(results: list[StepResult], result: StepResult) -> bool:
    results.append(result)
    return result.status == "failed"


def live_contract_command(args: argparse.Namespace) -> list[str]:
    command = [
        sys.executable,
        "scripts/public_api_contract_check.py",
        "--base-url",
        args.base_url,
        "--ws-timeout",
        str(args.ws_timeout),
        "--message-limit",
        str(args.live_message_limit),
        "--message-pages",
        str(args.live_message_pages),
    ]
    if args.require_live_ready:
        command.append("--require-ready-module")
    optional_args = [
        ("--require-message-kind", args.live_require_message_kind),
        ("--require-media-kind", args.live_require_media_kind),
        ("--require-appmsg-subtype", args.live_require_appmsg_subtype),
        ("--require-fixture", args.live_require_fixture),
    ]
    for flag, value in optional_args:
        if value:
            command.extend([flag, value])
    return command


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the standard project verification suite.")
    parser.add_argument("--skip-go", action="store_true", help="Skip go test ./...")
    parser.add_argument("--skip-web-tests", action="store_true", help="Skip Vitest.")
    parser.add_argument("--skip-web-build", action="store_true", help="Skip frontend production build.")
    parser.add_argument("--skip-bridge-build", action="store_true", help="Skip rebuilding the bridge binary after frontend build.")
    parser.add_argument("--skip-docs", action="store_true", help="Skip public API doc and fixture validators.")
    parser.add_argument("--skip-static-checks", action="store_true", help="Skip Android static checks, contract tool unit tests, and Python syntax checks.")
    parser.add_argument("--skip-source-hygiene", action="store_true", help="Skip source hygiene checks for tracked and untracked source-like files.")
    parser.add_argument("--skip-diff-check", action="store_true", help="Skip git diff --check.")
    parser.add_argument("--android", action="store_true", help="Require an Android debug APK build.")
    parser.add_argument(
        "--live-readonly",
        action="store_true",
        help="Run the read-only public API contract check. Requires WECHAT_OBSERVATORY_API_KEY.",
    )
    parser.add_argument(
        "--live-api-key-from-admin",
        action="store_true",
        help="For --live-readonly, read admin API metadata and select an enabled API key without printing it.",
    )
    parser.add_argument("--live-device", default="", help="With --live-api-key-from-admin, select the API key bound to this device.")
    parser.add_argument(
        "--require-live-ready",
        action="store_true",
        help="With --live-readonly, fail unless at least one module reports runtime_status=ready.",
    )
    parser.add_argument("--base-url", default="http://127.0.0.1:8088", help="Base URL for --live-readonly.")
    parser.add_argument("--ws-timeout", default="8", help="WebSocket timeout seconds for --live-readonly.")
    parser.add_argument("--live-message-limit", default="50", help="With --live-readonly, recent message window size for coverage checks.")
    parser.add_argument("--live-message-pages", default="1", help="With --live-readonly, message pages to scan for coverage checks.")
    parser.add_argument("--live-require-message-kind", default="", help="With --live-readonly, require recent messages to include these envelope kinds.")
    parser.add_argument("--live-require-media-kind", default="", help="With --live-readonly, require recent messages to include these media kinds.")
    parser.add_argument("--live-require-appmsg-subtype", default="", help="With --live-readonly, require recent messages to include these appmsg subtypes.")
    parser.add_argument("--live-require-fixture", default="", help="With --live-readonly, require recent messages to cover these public fixture ids or short names.")
    parser.add_argument("--json", action="store_true", help="Print a JSON summary at the end.")
    args = parser.parse_args()

    results: list[StepResult] = []

    if not args.skip_go and add_result(results, run_step("Go tests", ["go", "test", "./..."], ROOT)):
        return finish(results, args.json)

    if not args.skip_docs:
        doc_steps = [
            ("Public API docs", [sys.executable, "scripts/validate_public_api_docs.py"]),
            ("Public API docs unit tests", [sys.executable, "scripts/test_public_api_docs.py"]),
            ("Public API fixtures", [sys.executable, "scripts/validate_public_api_fixtures.py"]),
            ("Public API fixture unit tests", [sys.executable, "scripts/test_public_api_fixtures.py"]),
        ]
        for name, command in doc_steps:
            if add_result(results, run_step(name, command, ROOT)):
                return finish(results, args.json)

    if not args.skip_static_checks:
        static_steps = [
            ("Android module structure", [sys.executable, "scripts/validate_android_module.py"]),
            ("Android module structure unit tests", [sys.executable, "scripts/test_android_module.py"]),
            ("Android toolchain report", [sys.executable, "scripts/validate_android_toolchain.py"]),
            ("Android action coverage", [sys.executable, "scripts/validate_android_action_coverage.py"]),
            ("Android toolchain unit tests", [sys.executable, "scripts/test_android_toolchain.py"]),
            ("Android action coverage unit tests", [sys.executable, "scripts/test_android_action_coverage.py"]),
            ("Public API contract unit tests", [sys.executable, "scripts/test_public_api_contract_check.py"]),
            ("Verify project unit tests", [sys.executable, "scripts/test_verify_project.py"]),
            (
                "Python syntax",
                [
                    sys.executable,
                    "-m",
                    "py_compile",
                    "scripts/validate_android_module.py",
                    "scripts/test_android_module.py",
                    "scripts/validate_android_toolchain.py",
                    "scripts/test_android_toolchain.py",
                    "scripts/validate_android_action_coverage.py",
                    "scripts/test_android_action_coverage.py",
                    "scripts/test_public_api_contract_check.py",
                    "scripts/public_api_contract_check.py",
                    "scripts/validate_public_api_docs.py",
                    "scripts/test_public_api_docs.py",
                    "scripts/validate_public_api_fixtures.py",
                    "scripts/test_public_api_fixtures.py",
                    "scripts/validate_source_hygiene.py",
                    "scripts/test_source_hygiene.py",
                    "scripts/verify_project.py",
                    "scripts/test_verify_project.py",
                ],
            ),
        ]
        for name, command in static_steps:
            if add_result(results, run_step(name, command, ROOT)):
                return finish(results, args.json)

    if not args.skip_source_hygiene:
        hygiene_steps = [
            ("Source hygiene", [sys.executable, "scripts/validate_source_hygiene.py"]),
            ("Source hygiene unit tests", [sys.executable, "scripts/test_source_hygiene.py"]),
        ]
        for name, command in hygiene_steps:
            if add_result(results, run_step(name, command, ROOT)):
                return finish(results, args.json)

    if not args.skip_web_tests and add_result(results, run_step("Web tests", npm_command("test"), WEB_ADMIN)):
        return finish(results, args.json)
    if not args.skip_web_build and add_result(results, run_step("Web build", npm_command("run", "build"), WEB_ADMIN)):
        return finish(results, args.json)
    if not args.skip_bridge_build:
        with tempfile.TemporaryDirectory(prefix="wechat-observatory-bridge-") as tmp:
            if add_result(results, run_step("Bridge binary build", bridge_build_command(Path(tmp)), ROOT)):
                return finish(results, args.json)

    if args.android:
        build = android_build_command()
        if build is None:
            add_result(results, StepResult("Android build", "failed", 0.0, "", "missing Gradle wrapper or system gradle", 1))
            return finish(results, args.json)
        command, cwd = build
        if add_result(results, run_step("Android build", command, cwd, env=android_build_env())):
            return finish(results, args.json)
    else:
        build = android_build_command()
        detail = "available; pass --android to build" if build else "missing Gradle wrapper or system gradle"
        results.append(skip_step("Android build", detail))

    if args.live_readonly:
        try:
            api_key = resolve_live_api_key(args)
        except RuntimeError as exc:
            add_result(results, StepResult("Live public API readonly contract", "failed", 0.0, "", str(exc), 1))
            return finish(results, args.json)
        if not api_key:
            add_result(
                results,
                StepResult(
                    "Live public API readonly contract",
                    "failed",
                    0.0,
                    "",
                    "WECHAT_OBSERVATORY_API_KEY is required unless --live-api-key-from-admin is used",
                    1,
                ),
            )
            return finish(results, args.json)
        env = os.environ.copy()
        env["WECHAT_OBSERVATORY_API_KEY"] = api_key
        command = live_contract_command(args)
        if add_result(results, run_step("Live public API readonly contract", command, ROOT, env=env)):
            return finish(results, args.json)
    else:
        results.append(skip_step("Live public API readonly contract", "pass --live-readonly with WECHAT_OBSERVATORY_API_KEY"))

    if not args.skip_diff_check and add_result(results, run_step("Whitespace diff check", ["git", "diff", "--check"], ROOT)):
        return finish(results, args.json)

    return finish(results, args.json)


def finish(results: list[StepResult], emit_json: bool) -> int:
    failed = [result for result in results if result.status == "failed"]
    summary = {
        "ok": not failed,
        "passed": sum(1 for result in results if result.status == "passed"),
        "failed": len(failed),
        "skipped": sum(1 for result in results if result.status == "skipped"),
        "steps": [asdict(result) for result in results],
    }
    print("\n== Summary ==", flush=True)
    print(json.dumps(summary, ensure_ascii=False, sort_keys=True, indent=2 if emit_json else None), flush=True)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
