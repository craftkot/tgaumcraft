"""Pure-Python regression tests for the fail-closed integration evidence gate."""
import importlib.util
import unittest
from pathlib import Path

MODULE = Path(__file__).with_name("integration_acceptance_gate.py")
spec = importlib.util.spec_from_file_location("integration_acceptance_gate", MODULE)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
SHA = "a" * 40


def valid():
    return {
        "commit_sha": SHA,
        "gates": {
            name: {
                "conclusion": "success",
                "evidence_url": "https://github.com/example/example/actions/runs/123",
                "commit_sha": SHA,
            }
            for name in gate.REQUIRED
        },
    }


class GateTests(unittest.TestCase):
    def test_complete_evidence(self):
        self.assertEqual(gate.verify(valid(), SHA), [])

    def test_missing_gate_fails_closed(self):
        data = valid()
        del data["gates"]["real_client_smoke"]
        self.assertTrue(gate.verify(data, SHA))

    def test_wrong_sha_rejected(self):
        self.assertTrue(gate.verify(valid(), "b" * 40))

    def test_unverified_or_failed_gate_rejected(self):
        data = valid()
        data["gates"]["anti_duplication"]["conclusion"] = "pending"
        self.assertTrue(gate.verify(data, SHA))

    def test_missing_proof_rejected(self):
        data = valid()
        data["gates"]["native_gametests"].pop("evidence_url")
        self.assertTrue(gate.verify(data, SHA))

    def test_non_https_proof_rejected(self):
        data = valid()
        data["gates"]["cold_save_reload"]["evidence_url"] = "file:///tmp/log"
        self.assertTrue(gate.verify(data, SHA))


if __name__ == "__main__":
    unittest.main()
