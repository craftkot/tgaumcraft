#!/usr/bin/env python3
"""Offline regression tests for GitHub API evidence verification."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from integration_acceptance_gate import REQUIRED
from verify_actions_evidence import check

SHA = "a" * 40
URL = "https://github.com/craftkot/tgaumcraft/actions/runs/123"


def fixture():
    return {"commit_sha": SHA, "gates": {
        gate: {"conclusion": "success", "commit_sha": SHA,
               "evidence_url": URL, "job_name": "test-" + gate}
        for gate in REQUIRED}}


def fake_fetch(path, token):
    if path.endswith("/jobs?per_page=100&page=1"):
        return {"jobs": [{"name": "test-" + gate, "status": "completed", "conclusion": "success"}
                         for gate in REQUIRED]}
    return {"head_sha": SHA, "status": "completed", "conclusion": "success"}


class EvidenceAPITests(unittest.TestCase):
    def test_successful_run_and_jobs(self):
        self.assertEqual([], check(fixture(), SHA, "token", fake_fetch))

    def test_missing_token_fails_closed(self):
        self.assertTrue(check(fixture(), SHA, "", fake_fetch))

    def test_forged_url_rejected_without_api(self):
        evidence = fixture()
        evidence["gates"]["java_core"]["evidence_url"] = "https://github.com.evil.test/craftkot/tgaumcraft/actions/runs/123"
        self.assertTrue(check(evidence, SHA, "token", fake_fetch))

    def test_wrong_run_sha_rejected(self):
        def wrong_sha(path, token):
            result = fake_fetch(path, token)
            if "head_sha" in result:
                result["head_sha"] = "b" * 40
            return result
        self.assertTrue(any("SHA/status" in e for e in check(fixture(), SHA, "token", wrong_sha)))

    def test_missing_job_rejected(self):
        def no_jobs(path, token):
            result = fake_fetch(path, token)
            if "jobs" in result:
                result["jobs"] = []
            return result
        self.assertTrue(any("job not found" in e for e in check(fixture(), SHA, "token", no_jobs)))

    def test_skipped_job_rejected(self):
        def skipped(path, token):
            result = fake_fetch(path, token)
            if "jobs" in result:
                result["jobs"][0]["conclusion"] = "skipped"
            return result
        self.assertTrue(check(fixture(), SHA, "token", skipped))

    def test_api_failure_fails_closed(self):
        def unavailable(path, token):
            raise OSError("network unavailable")
        self.assertTrue(any("API verification failed" in e for e in check(fixture(), SHA, "token", unavailable)))


if __name__ == "__main__":
    unittest.main()
