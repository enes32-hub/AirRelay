"""Read pinned upstream files for provenance evidence; never save or execute code."""

import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from urllib.parse import quote
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
MAX_BYTES = 2 * 1024 * 1024


def fingerprint(data: bytes) -> dict:
    """Record hashes and license signals, without retaining source or crypto data."""
    text = data.decode("utf-8", errors="replace")
    return {
        "bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "lf_sha256": hashlib.sha256(data.replace(b"\r\n", b"\n")).hexdigest(),
        "mentions_gpl": "GNU GENERAL PUBLIC LICENSE" in text
        or "GNU General Public License" in text,
        "mentions_apache_2": "Apache License" in text and "2.0" in text,
        "mentions_rpiplay": "rpiplay" in text.lower(),
    }


def source_url(candidate: dict, file: str) -> str:
    """Restrict research fetches to pinned GitHub raw files."""
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", candidate["repository"]):
        raise ValueError("Invalid repository")
    if not re.fullmatch(r"[0-9a-f]{40}", candidate["commit"]):
        raise ValueError("A complete immutable commit is required")
    if not re.fullmatch(r"[\w./+-]+", file) or ".." in PurePosixPath(file).parts or file.startswith("/"):
        raise ValueError("Invalid source path")
    return f"https://raw.githubusercontent.com/{candidate['repository']}/{candidate['commit']}/{quote(file, safe='/')}"


def collect(lock: dict) -> dict:
    """Fetch only the explicit file allowlist; request failures fail the run."""
    result = {"observed_at": datetime.now(timezone.utc).isoformat(), "scope": "static-provenance-only", "candidates": []}
    for candidate in lock["candidates"]:
        entry = {k: candidate[k] for k in ("id", "repository", "commit")}
        entry["files"] = []
        for file in candidate["files"]:
            url = source_url(candidate, file)
            request = Request(url, headers={"User-Agent": "AirRelay-Research/1"})
            with urlopen(request, timeout=30) as response:
                if response.geturl() != url:
                    raise ValueError("Unexpected redirect")
                data = response.read(MAX_BYTES + 1)
            if len(data) > MAX_BYTES:
                raise ValueError("Upstream file exceeds research size limit")
            entry["files"].append({"path": file, "url": url, **fingerprint(data)})
        result["candidates"].append(entry)
    return result


def comparisons(result: dict) -> list:
    """Compare shared file basenames under playfair directories."""
    indexed = {
        candidate["id"]: {Path(f["path"]).name: f for f in candidate["files"] if "/playfair/" in f["path"]}
        for candidate in result["candidates"]
    }
    return [
        {"file": name, "identical_bytes": left["sha256"] == right["sha256"],
         "identical_after_lf": left["lf_sha256"] == right["lf_sha256"]}
        for name, left in indexed["uxplay"].items()
        if (right := indexed["phairplay"].get(name)) is not None
    ]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "docs/research/provenance-evidence.json")
    args = parser.parse_args()
    lock = json.loads((ROOT / "docs/research/candidates.lock.json").read_text(encoding="utf-8"))
    result = collect(lock)
    result["uxplay_phairplay_comparison"] = comparisons(result)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"candidates": len(result["candidates"]), "comparison": result["uxplay_phairplay_comparison"]}, indent=2))


if __name__ == "__main__":
    main()
