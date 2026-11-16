#!/usr/bin/env python3
"""Regression tests for the read-only Java Core CI inventory."""
import tempfile
import unittest
from pathlib import Path

from audit_core_ci_coverage import inventory


class InventoryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.tests = self.root / "src/test/java/dev/ehtid/thaumcraft/port/core"
        self.tests.mkdir(parents=True)
        self.workflow = self.root / ".github/workflows/ci.yml"
        self.workflow.parent.mkdir(parents=True)

    def java(self, name, runnable=True):
        main = "public static void main(String[] args) {}" if runnable else "void helper() {}"
        (self.tests / (name + ".java")).write_text("class " + name + " { " + main + " }")

    def ci(self, compile_names=(), run_names=()):
        prefix = "src/test/java/dev/ehtid/thaumcraft/port/core/"
        lines = ["jobs:", "      - name: Core parity test", "        run: |",
                 "          javac " + " ".join(prefix + n + ".java" for n in compile_names)]
        lines.extend("          java -cp build/classes dev.ehtid.thaumcraft.port.core." + n
                     for n in run_names)
        lines.extend(["      - name: Other step", "        run: |",
                      "          java -cp build/classes dev.ehtid.thaumcraft.port.core.OutsideTest"])
        self.workflow.write_text("\n".join(lines) + "\n")

    def test_all_covered(self):
        self.java("AlphaTest")
        self.ci(["AlphaTest"], ["AlphaTest"])
        self.assertEqual(inventory(self.root), ([], [], []))

    def test_missing_compile_and_run(self):
        self.java("AlphaTest")
        self.java("BetaTest")
        self.ci(["AlphaTest"], ["AlphaTest"])
        self.assertEqual(inventory(self.root), (["BetaTest"], ["BetaTest"], []))

    def test_compiled_but_not_executed(self):
        self.java("AlphaTest")
        self.ci(["AlphaTest"], [])
        self.assertEqual(inventory(self.root), (["AlphaTest"], [], []))

    def test_stale_invocation_and_other_step_ignored(self):
        self.java("AlphaTest")
        self.ci(["AlphaTest"], ["AlphaTest", "MissingTest"])
        self.assertEqual(inventory(self.root), ([], [], ["MissingTest"]))

    def test_non_runnable_class_ignored(self):
        self.java("HelperTest", runnable=False)
        self.ci()
        self.assertEqual(inventory(self.root), ([], [], []))


    def test_commented_main_not_runnable(self):
        (self.tests / "CommentedTest.java").write_text(
            "class CommentedTest { // public static void main(String[] args) {}\n"
            "/* public static void main(String[] args) {} */ }"
        )
        self.ci()
        self.assertEqual(inventory(self.root), ([], [], []))

    def test_real_main_after_comments_is_runnable(self):
        (self.tests / "RealTest.java").write_text(
            "class RealTest { /* public static void main(String[] args) {} */ "
            "public static void main(String[] args) {} }"
        )
        self.ci(["RealTest"], ["RealTest"])
        self.assertEqual(inventory(self.root), ([], [], []))


    def test_string_literal_main_not_runnable(self):
        (self.tests / "StringOnlyTest.java").write_text(
            'class StringOnlyTest { String text = "public static void main(String[] args) {}"; }'
        )
        self.ci()
        self.assertEqual(inventory(self.root), ([], [], []))

    def test_java_text_block_main_not_runnable(self):
        (self.tests / "TextBlockOnlyTest.java").write_text(
            'class TextBlockOnlyTest { String example = """\n'
            'public static void main(String[] args) {}\n'
            '"""; }'
        )
        self.ci()
        self.assertEqual(inventory(self.root), ([], [], []))

    def test_java_text_block_and_real_main(self):
        (self.tests / "TextBlockRealTest.java").write_text(
            'class TextBlockRealTest { String example = """\n'
            'public static void main(String[] args) {}\n'
            '"""; public static void main(String[] args) {} }'
        )
        self.ci(["TextBlockRealTest"], ["TextBlockRealTest"])
        self.assertEqual(inventory(self.root), ([], [], []))

if __name__ == "__main__":
    unittest.main()
