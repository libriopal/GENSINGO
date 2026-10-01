#!/usr/bin/env python3
"""
Tests for tools/verify_handoff.py: a well-formed handoff passes, and each B12 rule, broken on
its own, fails it (the negative controls). Run: python3 -m unittest tools/test_verify_handoff.py
"""
import contextlib
import io
import json
import os
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, os.path.dirname(__file__))
import verify_handoff  # noqa: E402

HANDOFF = {
    "handoff_version": 1, "created": "2026-10-01",
    "singnav": {"base44_app_name": "SingNav", "export_method": "zip-download", "exported_at": "2026-10-01"},
    "gensingo": {"base_commit": "abc123", "branch": "base44/singnav-integration", "head_commit": None,
                 "contracts": ["WP-1"], "tests_added": ["app/src/test/java/X.kt"], "compiled": False},
    "blueprint": "docs/blueprints/singnav-integration.md",
    "audit": {"done": True, "by": "fresh Superagent conversation"},
    "web_polish": {"done": ["phone first"], "open": []},
    "open": [], "attest_no_data_no_secrets": True,
}

GOOD = {
    "gensingo/settings.gradle.kts": "include(\":app\")",
    "gensingo/gradlew": "#!/bin/sh",
    "gensingo/app/build.gradle.kts": "plugins {}",
    "gensingo/app/src/main/AndroidManifest.xml": "<manifest/>",
    "gensingo/ARCHITECT.md": "# ARCHITECT",
    "gensingo/base44.md": "# base44",
    "gensingo/docs/blueprints/singnav-integration.md": "# Blueprint",
    "singnav-web/package.json": json.dumps({"name": "singnav", "dependencies": {}}),
    "singnav-web/src/App.jsx": "export default function App() { return null }",
}


def make(files, handoff=HANDOFF, root=""):
    fd, path = tempfile.mkstemp(suffix=".zip")
    os.close(fd)
    with zipfile.ZipFile(path, "w") as z:
        if handoff is not None:
            z.writestr(root + "HANDOFF.json", json.dumps(handoff) if isinstance(handoff, dict) else handoff)
        for name, body in files.items():
            z.writestr(root + name, body)
    return path


def verdict(path):
    out = io.StringIO()
    with contextlib.redirect_stdout(out):
        ok = verify_handoff.check(path)
    os.remove(path)
    return ok, out.getvalue()


class VerifyHandoffTest(unittest.TestCase):

    def test_a_well_formed_handoff_passes(self):
        ok, out = verdict(make(GOOD))
        self.assertTrue(ok, out)

    def test_one_wrapping_folder_is_accepted(self):
        ok, out = verdict(make(GOOD, root="gensingo-singnav-handoff-20261001/"))
        self.assertTrue(ok, out)

    def test_rule1_an_extra_top_level_item_fails(self):
        ok, out = verdict(make({**GOOD, "notes.txt": "x"}))
        self.assertFalse(ok)
        self.assertIn("FAIL 1", out)

    def test_rule2_a_missing_field_or_wrong_version_fails(self):
        for broken in ({k: v for k, v in HANDOFF.items() if k != "audit"},
                       {**HANDOFF, "handoff_version": 2},
                       {**HANDOFF, "attest_no_data_no_secrets": False},
                       {**HANDOFF, "gensingo": {**HANDOFF["gensingo"], "compiled": "no"}}):
            ok, out = verdict(make(GOOD, handoff=broken))
            self.assertFalse(ok)
            self.assertIn("FAIL 2", out)
        ok, out = verdict(make(GOOD, handoff="{not json"))
        self.assertIn("FAIL 2", out)

    def test_rule3_a_missing_gensingo_file_or_blueprint_fails(self):
        for drop in ("gensingo/gradlew", "gensingo/base44.md", "gensingo/docs/blueprints/singnav-integration.md"):
            ok, out = verdict(make({k: v for k, v in GOOD.items() if k != drop}))
            self.assertFalse(ok, drop)
            self.assertIn("FAIL 3", out)

    def test_rule4_singnav_needs_package_json_and_source(self):
        ok, out = verdict(make({k: v for k, v in GOOD.items() if k != "singnav-web/src/App.jsx"}))
        self.assertIn("FAIL 4", out)
        ok, out = verdict(make({**GOOD, "singnav-web/package.json": "{oops"}))
        self.assertIn("FAIL 4", out)

    def test_rule5_build_output_keystores_env_and_data_fail(self):
        rows = json.dumps([{"lat": 35.5 + i / 100, "lng": -83.0} for i in range(6)])
        for extra in ({"gensingo/app/build/outputs/x.txt": "x"},
                      {"singnav-web/node_modules/a/index.js": "x"},
                      {"gensingo/release.jks": "x"},
                      {"gensingo/keystore.properties": "x"},
                      {"singnav-web/.env": "X=1"},
                      {"gensingo/app-field.apk": "x"},
                      {"singnav-web/export/finds.csv": "lat,lng"},
                      {"singnav-web/src/data/finds.json": rows}):
            ok, out = verdict(make({**GOOD, **extra}))
            self.assertFalse(ok, extra)
            self.assertIn("FAIL 5", out)
        ok, out = verdict(make({**GOOD, "singnav-web/.env.example": "X="}))
        self.assertTrue(ok, out)

    def test_rule6_a_secret_fails_and_is_not_printed(self):
        key = "sk-ant-" + "a1B2" * 10
        ok, out = verdict(make({**GOOD, "singnav-web/src/api.js": f"const k = '{key}'"}))
        self.assertFalse(ok)
        self.assertIn("FAIL 6", out)
        self.assertNotIn(key, out)


if __name__ == "__main__":
    unittest.main()
