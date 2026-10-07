#!/usr/bin/env python3
"""Read an SRS markdown file, break it into small tasks with GitHub Copilot CLI,
and create GitHub milestones and one detailed issue per task.
Needs the `copilot` CLI on PATH (npm i -g @github/copilot). Standard library only."""
import json
import os
import pathlib
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

TOKEN = os.environ["GITHUB_TOKEN"]
REPO = os.environ["GITHUB_REPOSITORY"]
SRS_PATH = os.environ.get("SRS_PATH", ".github/srs/SRS.md")
MODEL = os.environ.get("MODEL", "").strip()  # optional Copilot model name
DRY_RUN = os.environ.get("DRY_RUN", "true").lower() == "true"
MAX_CHARS = int(os.environ.get("MAX_CHARS", "12000"))
BASE_LABEL = "srs-generated"

SYSTEM_PROMPT = """You are a senior engineer and project planner.
You receive one part of a Software Requirements Specification (SRS).
Break it into small, independently implementable engineering tasks
(each should take roughly 0.5 to 2 days). Do not invent requirements
that are not in the text. Group tasks into milestones: one milestone per
feature area or delivery phase (e.g. "M1 - User Authentication"), roughly
5-15 milestones for the whole SRS. If a list of existing milestones is
given, reuse the exact name whenever a task fits one; only create a new
milestone when none fits. Return ONLY JSON in this shape:

{"issues": [{
  "title": "short, imperative title",
  "milestone": "milestone name",
  "milestone_description": "one sentence on what this milestone delivers",
  "requirement_ids": ["FR-1.2"],
  "summary": "what and why, 2-4 sentences",
  "tasks": ["concrete checklist step", "..."],
  "acceptance_criteria": ["testable condition", "..."],
  "dependencies": ["title of another task, if any"],
  "labels": ["feature|bug|nfr|docs|testing|security|infra", "area:xyz"],
  "estimate": "S|M|L"
}]}"""


def http(url, method="GET", data=None, headers=None):
    req = urllib.request.Request(
        url, method=method,
        data=json.dumps(data).encode() if data is not None else None,
        headers={"Authorization": f"Bearer {TOKEN}",
                 "Content-Type": "application/json",
                 "Accept": "application/vnd.github+json",
                 "X-GitHub-Api-Version": "2022-11-28",
                 **(headers or {})})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=180) as r:
                raw = r.read().decode("utf-8", errors="replace").strip()
                ctype = r.headers.get("Content-Type", "")
                status = r.status
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503, 504) and attempt < 4:
                time.sleep(2 ** attempt * 5)
                continue
            sys.exit(f"HTTP {e.code} on {method} {url}: {e.read().decode()[:500]}")
        except (urllib.error.URLError, TimeoutError) as e:
            if attempt < 4:
                time.sleep(2 ** attempt * 5)
                continue
            sys.exit(f"Network error on {method} {url}: {e}")
        if not raw:
            return None
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            if attempt < 4:
                print(f"  non-JSON response (HTTP {status}, {ctype}), retrying...")
                time.sleep(2 ** attempt * 5)
                continue
            sys.exit(f"Non-JSON response from {method} {url} "
                     f"(HTTP {status}, {ctype}): {raw[:500]!r}")


def chunk_srs(text):
    """Split on level-1/2 headings, then pack sections up to MAX_CHARS."""
    sections = re.split(r"(?m)^(?=#{1,2} )", text)
    chunks, cur = [], ""
    for s in sections:
        if cur and len(cur) + len(s) > MAX_CHARS:
            chunks.append(cur)
            cur = ""
        cur += s
    if cur.strip():
        chunks.append(cur)
    return chunks


def ask_model(chunk, known_milestones):
    """Ask GitHub Copilot CLI (headless) to turn one SRS chunk into issues.
    Copilot writes its JSON answer to a file, which we then parse."""
    work = pathlib.Path(".srs_work")
    work.mkdir(exist_ok=True)
    out = (work / "out.json").resolve()

    ms = ""
    if known_milestones:
        ms = ("Existing milestones (reuse these exact names where they fit):\n- "
              + "\n- ".join(sorted(known_milestones)) + "\n\n")
    prompt = (f"{SYSTEM_PROMPT}\n\n"
              "Use only the SRS text below. Do not explore the repository and do "
              "not run any commands.\n"
              f"Write the JSON object, and nothing else, to this file: {out}\n\n"
              f"{ms}SRS part:\n{chunk}")

    cmd = ["copilot", "-p", prompt, "--allow-tool=write", "--no-ask-user"]
    if MODEL:
        cmd += ["--model", MODEL]

    for attempt in range(3):
        out.unlink(missing_ok=True)
        try:
            proc = subprocess.run(cmd, capture_output=True, text=True, timeout=900)
        except subprocess.TimeoutExpired:
            print(f"  copilot timed out (attempt {attempt + 1}/3)")
            continue
        try:
            raw = out.read_text(encoding="utf-8").strip()
            raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw).strip()
            data = json.loads(raw)
            issues = data.get("issues", []) if isinstance(data, dict) else data
            if isinstance(issues, list):
                return issues
        except (FileNotFoundError, json.JSONDecodeError):
            pass
        print(f"  no usable output from Copilot (attempt {attempt + 1}/3), "
              f"exit code {proc.returncode}")
        print("  stdout tail:", proc.stdout[-500:])
        print("  stderr tail:", proc.stderr[-500:])
        time.sleep(5)
    sys.exit("Copilot did not produce valid JSON after 3 attempts.")


def load_milestones():
    """Return {title: number} for all existing milestones (open and closed)."""
    found, page = {}, 1
    while True:
        batch = http(f"https://api.github.com/repos/{REPO}/milestones"
                     f"?state=all&per_page=100&page={page}")
        found.update({m["title"]: m["number"] for m in batch})
        if len(batch) < 100:
            return found
        page += 1


def ensure_milestone(milestones, title, description):
    """Create the milestone if missing. Returns its number (None in dry run)."""
    if title in milestones:
        return milestones[title]
    if DRY_RUN:
        print(f"  [dry-run] would create milestone: {title}")
        milestones[title] = None
        return None
    m = http(f"https://api.github.com/repos/{REPO}/milestones", "POST",
             {"title": title, "description": description or ""})
    milestones[title] = m["number"]
    print(f"  created milestone: {title}")
    return m["number"]


def existing_titles():
    titles, page = set(), 1
    while True:
        batch = http(f"https://api.github.com/repos/{REPO}/issues"
                     f"?state=all&labels={BASE_LABEL}&per_page=100&page={page}")
        titles |= {i["title"].strip().lower() for i in batch}
        if len(batch) < 100:
            return titles
        page += 1


def render_body(i):
    def bullets(items, box=False):
        return "\n".join(f"- {'[ ] ' if box else ''}{x}" for x in items) or "_None_"
    return f"""## Summary
{i.get('summary', '')}

**SRS reference:** {', '.join(i.get('requirement_ids', [])) or 'n/a'}
**Estimate:** {i.get('estimate', '?')}

## Tasks
{bullets(i.get('tasks', []), box=True)}

## Acceptance criteria
{bullets(i.get('acceptance_criteria', []), box=True)}

## Dependencies
{bullets(i.get('dependencies', []))}

---
_Generated from `{SRS_PATH}`_"""


def main():
    with open(SRS_PATH, encoding="utf-8") as f:
        chunks = chunk_srs(f.read())
    print(f"SRS split into {len(chunks)} chunk(s). Dry run: {DRY_RUN}")

    seen = existing_titles()
    milestones = load_milestones()
    created = skipped = 0
    for n, chunk in enumerate(chunks, 1):
        issues = ask_model(chunk, milestones.keys())
        print(f"Chunk {n}: model proposed {len(issues)} issue(s)")
        for i in issues:
            title = i["title"].strip()
            if title.lower() in seen:
                skipped += 1
                continue
            seen.add(title.lower())
            labels = [BASE_LABEL] + [l for l in i.get("labels", []) if l]
            ms_title = (i.get("milestone") or "").strip()
            ms_number = (ensure_milestone(milestones, ms_title,
                                          i.get("milestone_description"))
                         if ms_title else None)
            if DRY_RUN:
                print(f"  [dry-run] {title}  {labels}  milestone: {ms_title or '-'}")
            else:
                payload = {"title": title, "body": render_body(i), "labels": labels}
                if ms_number:
                    payload["milestone"] = ms_number
                http(f"https://api.github.com/repos/{REPO}/issues", "POST", payload)
                print(f"  created: {title}  (milestone: {ms_title or '-'})")
                time.sleep(1.5)  # stay under secondary rate limits
            created += 1
    print(f"Done. {created} {'proposed' if DRY_RUN else 'created'}, {skipped} duplicate(s) skipped.")


if __name__ == "__main__":
    main()
