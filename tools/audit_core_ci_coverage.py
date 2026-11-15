#!/usr/bin/env python3
"""Audit Java core tests explicitly executed by the GitHub Actions parity step.

Inventory only: a discovered class is NOT evidence that it was executed.
Use --strict to fail when runnable standalone test classes are omitted.
"""
import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE_TESTS = ROOT / "src/test/java/dev/ehtid/thaumcraft/port/core"
CI = ROOT / ".github/workflows/ci.yml"
MAIN = re.compile(r"public\s+static\s+void\s+main\s*\(\s*String\s*(?:\[\s*\]\s*\w+|\.\.\.\s*\w+)\s*\)")
JAVA_TOKENS = re.compile(r"""//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""")


def uncommented_java(source):
    """Mask comments, strings and character literals before finding a Java main."""
    def mask(match):
        token = match.group(0)
        if token.startswith(("//", "/*", "\"", "'")):
            return re.sub(r"[^\n]", " ", token)
        return token
    return JAVA_TOKENS.sub(mask, source)


RUN = re.compile(r"\bjava\s+-cp\s+[^\n]*?\bdev\.ehtid\.thaumcraft\.port\.core\.([A-Za-z_$][\w$]*)")


def inventory(root=ROOT):
    test_dir = root / "src/test/java/dev/ehtid/thaumcraft/port/core"
    ci = (root / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    # Limit scan to the Core parity test step, not unrelated workflow commands.
    start = ci.index("      - name: Core parity test")
    end = ci.find("\n      - name:", start + 1)
    step = ci[start:end if end >= 0 else None]
    runnable = {
        path.stem for path in test_dir.glob("*Test.java")
        if MAIN.search(uncommented_java(path.read_text(encoding="utf-8")))
    }
    executed = set(RUN.findall(step))
    compiled = set(re.findall(r"src/test/java/dev/ehtid/thaumcraft/port/core/([A-Za-z_$][\w$]*)\.java", step))
    return sorted(runnable - executed), sorted(runnable - compiled), sorted(executed - runnable)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--strict", action="store_true", help="exit nonzero for missing runnable tests")
    args = parser.parse_args()
    unrun, uncompiled, stale = inventory()
    print("Runnable core tests not executed by CI:", len(unrun))
    for name in unrun:
        print("  NOT RUN:", name)
    print("Runnable core tests not explicitly compiled:", len(uncompiled))
    for name in uncompiled:
        print("  NOT COMPILED:", name)
    if stale:
        print("CI invocations without matching standalone *Test.java:", ", ".join(stale))
    if args.strict and (unrun or uncompiled or stale):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
