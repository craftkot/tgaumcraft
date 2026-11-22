#!/usr/bin/env python3
"""Fail-closed acceptance gate for the Thaumcraft multi-lane integration.

Usage:
  python3 tools/integration_acceptance_gate.py analysis/evidence/integration/acceptance.json <git-sha>

The evidence JSON is generated only after independent real runs. This script
does not execute Minecraft or infer success from the existence of a workflow.
"""
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit

REQUIRED = (
    "java_core",
    "neoforge_build",
    "native_gametests",
    "dedicated_server",
    "real_client_smoke",
    "cold_save_reload",
    "anti_duplication",
)


def is_github_run_url(value: object) -> bool:
    """Check URL shape only; caller must verify run, SHA, jobs and artifacts via API."""
    if not isinstance(value, str):
        return False
    try:
        url = urlsplit(value)
    except ValueError:
        return False
    return (
        url.scheme == "https"
        and url.hostname == "github.com"
        and url.port in (None, 443)
        and not url.username
        and not url.password
        and not url.query
        and not url.fragment
        and re.fullmatch(
            r"/craftkot/tgaumcraft/actions/runs/[1-9][0-9]*(?:/job/[1-9][0-9]*)?/?",
            url.path,
        ) is not None
    )


def verify(evidence: object, expected_sha: str) -> list[str]:
    problems = []
    if not re.fullmatch(r"[0-9a-f]{40}", expected_sha):
        problems.append("expected SHA must be a full lowercase 40-character git SHA")
    if not isinstance(evidence, dict):
        return problems + ["evidence must be a JSON object"]
    if evidence.get("commit_sha") != expected_sha:
        problems.append("evidence commit_sha does not match integration HEAD")
    gates = evidence.get("gates")
    if not isinstance(gates, dict):
        return problems + ["gates must be an object"]
    for name in REQUIRED:
        entry = gates.get(name)
        if not isinstance(entry, dict):
            problems.append(f"{name}: missing evidence object")
            continue
        if entry.get("conclusion") != "success":
            problems.append(f"{name}: conclusion is not success")
        # A named workflow alone is not evidence. Demand a retrievable run/log.
        proof = entry.get("evidence_url")
        if not is_github_run_url(proof):
            problems.append(f"{name}: expected craftkot/tgaumcraft GitHub Actions run URL")
        if entry.get("commit_sha") != expected_sha:
            problems.append(f"{name}: evidence SHA mismatch")
    return problems


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: integration_acceptance_gate.py EVIDENCE.json FULL_COMMIT_SHA", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        print(f"BLOCKED: cannot load evidence: {exc}", file=sys.stderr)
        return 1
    problems = verify(data, sys.argv[2])
    if problems:
        for problem in problems:
            print(f"BLOCKED: {problem}", file=sys.stderr)
        return 1
    print("STRUCTURAL EVIDENCE COMPLETE: seven gates self-report success for exact SHA.")
    print("NOT VERIFIED: independently inspect GitHub API run SHA, job status, artifacts and in-game coverage before merging.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
