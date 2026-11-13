"""Render the privacy policy into the plain-text asset shown inside the app.

Google Play requires the privacy policy to be reachable from within the app as well as from the
store listing. The app already renders bundled asset text (open-source notices) in a scrollable
viewer, so the policy ships the same way: this script converts legal/PRIVACY.md — the single source
of truth — into app/src/main/assets/PRIVACY_POLICY.txt.

Run it after editing the policy:

    python tools/render_privacy_asset.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "legal" / "PRIVACY.md"
TARGET = ROOT / "app" / "src" / "main" / "assets" / "PRIVACY_POLICY.txt"

HEADER = """AirRelay Receiver — PRIVACY POLICY
==================================

This is the same policy that is published at:
https://enes32-hub.github.io/AirRelay/privacy.html

The published English text is authoritative. This copy is bundled so that the
policy is readable on the TV without a browser.
"""


def render(markdown: str) -> str:
    lines: list[str] = []
    # HEADER already carries the document title, so the markdown's own H1 must not be repeated.
    skipped_title = False
    for raw in markdown.splitlines():
        line = raw.rstrip()
        if not skipped_title and line.startswith("# "):
            skipped_title = True
            lines.append("")
            continue
        if line.strip() == "---":
            lines.append("")
            continue
        heading = re.match(r"^(#{1,4})\s+(.*)$", line.strip())
        if heading:
            title = re.sub(r"\*\*(.+?)\*\*", r"\1", heading.group(2)).upper()
            lines += ["", title, "-" * min(len(title), 60)]
            continue
        if line.strip().startswith("|"):
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if all(set(c) <= set("-: ") for c in cells):
                continue
            line = "  · ".join(cells)
        line = re.sub(r"\*\*(.+?)\*\*", r"\1", line)
        line = re.sub(r"(?<!\*)\*([^*\n]+?)\*(?!\*)", r"\1", line)
        line = re.sub(r"`(.+?)`", r"\1", line)
        lines.append(line)
    body = "\n".join(lines)
    body = re.sub(r"\n{3,}", "\n\n", body)
    return HEADER.rstrip("\n") + "\n\n" + body.strip("\n") + "\n"


def main() -> int:
    if not SOURCE.is_file():
        raise SystemExit(f"missing {SOURCE}")
    text = render(SOURCE.read_text(encoding="utf-8"))
    if "--check" in sys.argv[1:]:
        if not TARGET.is_file():
            print(f"{TARGET.relative_to(ROOT)} is missing; run this tool without --check", file=sys.stderr)
            return 1
        if TARGET.read_text(encoding="utf-8") != text:
            print(f"{TARGET.relative_to(ROOT)} is stale; run this tool without --check", file=sys.stderr)
            return 1
        print(f"  {TARGET.relative_to(ROOT)} is up to date")
        return 0
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(text, encoding="utf-8", newline="\n")
    print(f"  {SOURCE.relative_to(ROOT)} -> {TARGET.relative_to(ROOT)} "
          f"({len(text.splitlines())} lines, {len(text)} characters)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
