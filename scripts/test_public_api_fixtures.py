#!/usr/bin/env python3
"""Unit tests for public API fixture validation."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from typing import Any

import validate_public_api_fixtures as fixtures


MINIMAL_REQUIRED_FIXTURE_IDS = {"public-api-v1.text"}


def minimal_fixture(fixture_id: str = "public-api-v1.text", *, outbound_supported: bool = True) -> dict[str, Any]:
    data: dict[str, Any] = {
        "fixture_id": fixture_id,
        "protocol_version": "v1",
        "kind": "text",
        "outbound_supported": outbound_supported,
        "inbound_envelope": {
            "id": 1,
            "device": "device-a",
            "direction": "recv",
            "kind": "text",
            "message_type": 1,
            "chat_id": "target-a",
            "chat_kind": "direct",
        },
    }
    if outbound_supported:
        data.update(
            {
                "outbound_request": {
                    "endpoint": "/api/v1/messages/text",
                    "json": {"wx_ids": ["target-a"], "text": "hello"},
                },
                "send_response": {"protocol_version": "v1", "outbox_id": 10},
                "outbox_terminal": {"outbox": {"status": "sent", "chat_record_id": 90001}},
            }
        )
    return data


def write_json(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def write_index(root: Path, names: list[str]) -> None:
    write_json(
        root / "index.json",
        {
            "fixture_set": "public-api-v1",
            "protocol_version": "v1",
            "fixtures": names,
        },
    )


class PublicAPIFixtureValidatorTests(unittest.TestCase):
    def test_valid_fixture_set_returns_fixture_ids(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_index(root, ["public-api-v1.text.json"])
            write_json(root / "public-api-v1.text.json", minimal_fixture())

            self.assertEqual(fixtures.validate_fixture_set(root, MINIMAL_REQUIRED_FIXTURE_IDS), ["public-api-v1.text"])

    def test_index_must_match_fixture_files_exactly(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_index(root, ["public-api-v1.text.json", "public-api-v1.image.json"])
            write_json(root / "public-api-v1.text.json", minimal_fixture())

            with self.assertRaisesRegex(SystemExit, "do not exist"):
                fixtures.validate_fixture_set(root, MINIMAL_REQUIRED_FIXTURE_IDS)

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_index(root, ["public-api-v1.text.json"])
            write_json(root / "public-api-v1.text.json", minimal_fixture())
            write_json(root / "public-api-v1.image.json", minimal_fixture("public-api-v1.image"))

            with self.assertRaisesRegex(SystemExit, "missing from index"):
                fixtures.validate_fixture_set(root, MINIMAL_REQUIRED_FIXTURE_IDS)

    def test_fixture_id_must_match_file_name(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_index(root, ["public-api-v1.text.json"])
            write_json(root / "public-api-v1.text.json", minimal_fixture("public-api-v1.wrong"))

            with self.assertRaisesRegex(SystemExit, "fixture_id must match file name stem"):
                fixtures.validate_fixture_set(root, MINIMAL_REQUIRED_FIXTURE_IDS)

    def test_sensitive_fields_and_real_identifiers_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_index(root, ["public-api-v1.text.json"])
            data = minimal_fixture()
            data["media_base64"] = "AA=="
            write_json(root / "public-api-v1.text.json", data)

            with self.assertRaisesRegex(SystemExit, "forbidden key"):
                fixtures.validate_fixture_set(root, MINIMAL_REQUIRED_FIXTURE_IDS)

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_index(root, ["public-api-v1.text.json"])
            data = minimal_fixture()
            data["inbound_envelope"]["auth_key"] = "redacted-but-forbidden-field"
            data["outbound_request"]["json"]["session"] = "redacted-but-forbidden-field"
            write_json(root / "public-api-v1.text.json", data)

            with self.assertRaisesRegex(SystemExit, "forbidden key"):
                fixtures.validate_fixture_set(root, MINIMAL_REQUIRED_FIXTURE_IDS)

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_index(root, ["public-api-v1.text.json"])
            data = minimal_fixture()
            data["inbound_envelope"]["chat_id"] = "12345@chatroom"
            write_json(root / "public-api-v1.text.json", data)

            with self.assertRaisesRegex(SystemExit, "possible real identifier"):
                fixtures.validate_fixture_set(root, MINIMAL_REQUIRED_FIXTURE_IDS)

    def test_outbound_supported_fixtures_require_terminal_status(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_index(root, ["public-api-v1.text.json"])
            data = minimal_fixture()
            del data["outbox_terminal"]
            write_json(root / "public-api-v1.text.json", data)

            with self.assertRaisesRegex(SystemExit, "outbox_terminal"):
                fixtures.validate_fixture_set(root, MINIMAL_REQUIRED_FIXTURE_IDS)

    def test_queued_send_response_must_not_claim_chat_record_id(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_index(root, ["public-api-v1.text.json"])
            data = minimal_fixture()
            data["send_response"]["chat_record_id"] = data["send_response"]["outbox_id"]
            write_json(root / "public-api-v1.text.json", data)

            with self.assertRaisesRegex(SystemExit, "must not mirror outbox_id"):
                fixtures.validate_fixture_set(root, MINIMAL_REQUIRED_FIXTURE_IDS)

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_index(root, ["public-api-v1.text.json"])
            data = minimal_fixture()
            data["send_response"]["chat_record_id"] = 99
            data["send_response"]["outbox"] = {"id": 10, "status": "pending"}
            write_json(root / "public-api-v1.text.json", data)

            with self.assertRaisesRegex(SystemExit, "must be omitted until module ACK"):
                fixtures.validate_fixture_set(root, MINIMAL_REQUIRED_FIXTURE_IDS)

    def test_inbound_envelope_rejects_legacy_flat_fields(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_index(root, ["public-api-v1.text.json"])
            data = minimal_fixture()
            data["inbound_envelope"]["media_url"] = "/api/media/device-a/date/file.jpg"
            write_json(root / "public-api-v1.text.json", data)

            with self.assertRaisesRegex(SystemExit, "nested v1 fields"):
                fixtures.validate_fixture_set(root, MINIMAL_REQUIRED_FIXTURE_IDS)

    def test_default_validation_requires_all_public_fixture_ids(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_index(root, ["public-api-v1.text.json"])
            write_json(root / "public-api-v1.text.json", minimal_fixture())

            with self.assertRaisesRegex(SystemExit, "missing required fixture id"):
                fixtures.validate_fixture_set(root)


if __name__ == "__main__":
    unittest.main()
