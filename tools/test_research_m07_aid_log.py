#!/usr/bin/env python3
"""Independent unit tests for fail-closed M07 server-log auditor."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location(
    "m07_log_audit", Path(__file__).with_name("audit_research_m07_aid_log.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ResearchAidLogTest(unittest.TestCase):
    def log(self, branch="ALCHEMY", stages=None):
        stages = stages or module.STAGES
        return "\n".join(f"[Server thread/INFO]: TC_SMOKE_M07_{branch}_AID_{step}=YES"
                         for step in stages)

    def test_success_alchemy(self):
        self.assertEqual(module.validate(self.log(), "ALCHEMY"), [0, 1, 2])

    def test_success_infusion(self):
        self.assertEqual(module.validate(self.log("INFUSION"), "INFUSION"), [0, 1, 2])

    def test_interleaved_other_markers(self):
        log = self.log().replace("\n", "\nTC_SMOKE_THEORY_CREATED=YES\n")
        self.assertEqual(module.validate(log, "ALCHEMY"), [0, 2, 4])

    def test_missing_paid_draw(self):
        with self.assertRaisesRegex(ValueError, "CARD_DRAWN"):
            module.validate(self.log(stages=("THEORY_CREATED", "CARD_ACTIVATED")), "ALCHEMY")

    def test_out_of_order(self):
        with self.assertRaisesRegex(ValueError, "CARD_ACTIVATED"):
            module.validate(self.log(stages=("THEORY_CREATED", "CARD_ACTIVATED", "CARD_DRAWN")), "ALCHEMY")

    def test_wrong_branch(self):
        with self.assertRaisesRegex(ValueError, "THEORY_CREATED"):
            module.validate(self.log("INFUSION"), "ALCHEMY")

    def test_empty_log(self):
        with self.assertRaises(ValueError):
            module.validate("", "ALCHEMY")

    def test_no_substring_false_positive(self):
        with self.assertRaises(ValueError):
            module.validate(" ".join("XTC_SMOKE_M07_ALCHEMY_AID_" + step + "=YES" for step in module.STAGES), "ALCHEMY")

    def test_repeated_events_can_form_valid_sequence(self):
        log = self.log(stages=("THEORY_CREATED", "THEORY_CREATED", "CARD_DRAWN", "CARD_ACTIVATED"))
        self.assertEqual(module.validate(log, "ALCHEMY"), [0, 2, 3])

    def test_reject_arbitrary_branch(self):
        with self.assertRaises(ValueError):
            module.validate(self.log(), "ELDRITCH")


if __name__ == "__main__":
    unittest.main()
