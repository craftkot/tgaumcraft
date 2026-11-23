#!/usr/bin/env python3
"""Independently check GitHub Actions run/job identities in integration evidence.

This is NOT proof of in-world gameplay, save/load, or anti-duplication.
Each gate must supply an exact successful job_name in addition to evidence_url.
"""
import json
import os
import re
import sys
import urllib.error
import urllib.request
from urllib.parse import urlparse

from integration_acceptance_gate import REQUIRED, verify

REPO = "craftkot/tgaumcraft"
RUN_RE = re.compile(r"https://github[.]com/craftkot/tgaumcraft/actions/runs/([1-9][0-9]*)(?:/)?")
SHA_RE = re.compile(r"[0-9a-f]{40}")


def github_json(path, token, opener=urllib.request.urlopen):
    request = urllib.request.Request(
        "https://api.github.com" + path,
        headers={"Accept": "application/vnd.github+json",
                 "Authorization": "Bearer " + token,
                 "X-GitHub-Api-Version": "2022-11-28"},
    )
    with opener(request, timeout=20) as response:
        return json.load(response)


def check(evidence, sha, token, fetch=github_json):
    errors = verify(evidence, sha)
    if errors:
        return errors
    if not SHA_RE.fullmatch(sha):
        return ["invalid SHA"]
    if not token:
        return ["missing GITHUB_TOKEN; cannot authenticate evidence"]
    cache = {}
    for gate in REQUIRED:
        entry = evidence["gates"][gate]
        url = entry["evidence_url"]
        match = RUN_RE.fullmatch(url)
        if not match:
            errors.append(f"{gate}: evidence must be an exact repository Actions run URL")
            continue
        job_name = entry.get("job_name")
        if not isinstance(job_name, str) or not job_name.strip():
            errors.append(f"{gate}: missing exact job_name")
            continue
        run_id = int(match.group(1))
        try:
            if run_id not in cache:
                run = fetch(f"/repos/{REPO}/actions/runs/{run_id}", token)
                jobs = []
                page = 1
                while True:
                    response = fetch(f"/repos/{REPO}/actions/runs/{run_id}/jobs?per_page=100&page={page}", token)
                    batch = response.get("jobs", [])
                    jobs.extend(batch)
                    if len(batch) < 100:
                        break
                    page += 1
                    if page > 100:
                        raise ValueError("job pagination limit exceeded")
                cache[run_id] = (run, jobs)
            run, jobs = cache[run_id]
            if run.get("head_sha") != sha or run.get("status") != "completed" or run.get("conclusion") != "success":
                errors.append(f"{gate}: run SHA/status/conclusion mismatch")
            matches = [j for j in jobs if j.get("name") == job_name and j.get("status") == "completed" and j.get("conclusion") == "success"]
            if len(matches) != 1:
                errors.append(f"{gate}: exact successful job not found uniquely")
        except (urllib.error.URLError, ValueError, KeyError, TypeError, OSError) as exc:
            errors.append(f"{gate}: GitHub API verification failed: {exc}")
    return errors


def main():
    if len(sys.argv) != 3:
        print("usage: verify_actions_evidence.py EVIDENCE.json FULL_SHA", file=sys.stderr)
        return 2
    try:
        with open(sys.argv[1], encoding="utf-8") as handle:
            evidence = json.load(handle)
        errors = check(evidence, sys.argv[2], os.getenv("GITHUB_TOKEN", ""))
    except (OSError, ValueError) as exc:
        errors = [f"cannot read evidence: {exc}"]
    for error in errors:
        print("BLOCKED:", error, file=sys.stderr)
    if errors:
        return 1
    print("Actions run SHA and job conclusions independently verified.")
    print("NOT a substitute for gameplay logs, artifacts or manual acceptance.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
