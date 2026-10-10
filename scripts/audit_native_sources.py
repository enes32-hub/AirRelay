"""Inventory pinned candidate files without saving or executing third-party source."""

import csv
import hashlib
import json
import re
import sys
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parent))
from inspect_candidates import MAX_BYTES, source_url

ROOT = Path(__file__).resolve().parents[1]
EXTENSIONS = {".c", ".cpp", ".h", ".hpp", ".cmake"}


def fetch(url: str, maximum: int = MAX_BYTES) -> bytes:
    """Read bounded HTTPS content; network errors abort rather than imply approval."""
    with urlopen(Request(url, headers={"User-Agent": "AirRelay-Static-Audit"}), timeout=30) as response:
        data = response.read(maximum + 1)
    if len(data) > maximum:
        raise ValueError("Research response exceeds byte limit")
    return data


def selected(repository: dict, path: str) -> bool:
    """Include the reviewed allowlist and source/license files in scoped directories."""
    if path in repository["files"]:
        return True
    return any(path.startswith(prefix) for prefix in repository["prefixes"]) and (
        Path(path).suffix in EXTENSIONS or "LICENSE" in Path(path).name or Path(path).name == "CMakeLists.txt")


def license_signals(text: str) -> list[str]:
    """Report explicit text signals only; absence or inherited licensing requires review."""
    text = re.sub(r"\s+", " ", re.sub(r"(?m)^\s*[*#]+\s?", "", text)).lower()
    signals = []
    if "gnu lesser general public license" in text:
        signals.append("LGPL-text")
    if "gnu general public license" in text:
        signals.append("GPL-text")
    if "permission is hereby granted, free of charge" in text:
        signals.append("MIT-style-text")
    if "apache license" in text and "2.0" in text:
        signals.append("Apache-2.0-text")
    if "redistribution and use in source and binary forms" in text:
        signals.append("BSD-style-text")
    return signals


def inspect_file(job: tuple) -> dict:
    """Match raw bytes to the commit's Git blob and emit no source or crypto constants."""
    repo, blob = job
    url = source_url(repo, blob["path"])
    data = fetch(url)
    git_hash = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
    if git_hash != blob["sha"]:
        raise ValueError(f"Pinned Git blob mismatch: {repo['id']}:{blob['path']}")
    text = data.decode("utf-8", errors="replace")
    warning_lines = [i for i, line in enumerate(text.splitlines(), 1) if "legal status" in line.lower() and "unclear" in line.lower()]
    return {"repository_id": repo["id"], "repository": repo["repository"], "commit": repo["commit"],
            "path": blob["path"], "url": url, "git_blob_sha1": git_hash,
            "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data),
            "license_signals": license_signals(text), "copyright_notice_present": "copyright" in text.lower(),
            "upstream_legal_uncertainty_lines": warning_lines,
            "contains_private_key_pem_marker": bool(re.search(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", text)),
            "review_status": "MANUAL_REVIEW_REQUIRED"}


def inventory(lock: dict) -> dict:
    """Read source trees and all selected files; make no completeness claim for link graphs."""
    jobs, sources, submodules = [], [], []
    for repo in lock["repositories"]:
        source_url(repo, "LICENSE")  # Validate repository and commit before constructing API URL.
        tree_url = f"https://api.github.com/repos/{repo['repository']}/git/trees/{repo['commit']}?recursive=1"
        tree = json.loads(fetch(tree_url, 8 * 1024 * 1024))
        if tree.get("truncated"):
            raise ValueError(f"Truncated upstream tree: {repo['id']}")
        files = {item["path"]: item for item in tree["tree"] if item["type"] == "blob"}
        missing = set(repo["files"]) - files.keys()
        if missing:
            raise ValueError(f"Audit allowlist files missing in {repo['id']}: {sorted(missing)}")
        chosen = [blob for path, blob in files.items() if selected(repo, path)]
        jobs.extend((repo, blob) for blob in chosen)
        sources.append({"id": repo["id"], "repository": repo["repository"], "commit": repo["commit"],
                        "tree_url": tree_url, "selected_files": len(chosen), "tree_truncated": False})
        submodules.extend({"repository_id": repo["id"], "path": item["path"], "commit": item["sha"]}
                          for item in tree["tree"] if item["type"] == "commit")
    with ThreadPoolExecutor(max_workers=6) as pool:
        records = list(pool.map(inspect_file, jobs))
    records.sort(key=lambda r: (r["repository_id"], r["path"]))
    return {"observed_at": datetime.now(timezone.utc).isoformat(), "scope": "static-source-license-signals",
            "source_code_saved": False, "code_executed": False, "dependency_approval": False,
            "complete_transitive_link_graph": False, "repositories": sources, "submodules": submodules,
            "files": records, "total_files": len(records),
            "files_without_license_text": sum(not r["license_signals"] for r in records),
            "upstream_legal_uncertainty_files": [r["url"] for r in records if r["upstream_legal_uncertainty_lines"]]}


def main() -> None:
    lock = json.loads((ROOT / "docs/research/native-audit.lock.json").read_text(encoding="utf-8"))
    result = inventory(lock)
    folder = ROOT / "docs/research"
    (folder / "native-source-audit.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    fields = ["repository_id", "commit", "path", "sha256", "license_signals", "copyright_notice_present", "review_status", "url"]
    with (folder / "native-source-inventory.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, extrasaction="ignore", lineterminator="\n")
        writer.writeheader()
        for record in result["files"]:
            writer.writerow({**record, "license_signals": ";".join(record["license_signals"])})
    print(json.dumps({"total_files": result["total_files"],
                      "per_repository": dict(Counter(r["repository_id"] for r in result["files"])),
                      "uncertainty_files": result["upstream_legal_uncertainty_files"],
                      "approval": False}, indent=2))


if __name__ == "__main__":
    main()
