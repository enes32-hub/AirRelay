"""Render the privacy policy markdown into self-contained HTML pages for GitHub Pages.

Google Play requires the store-listing privacy policy to live on an active, non-editable URL and
explicitly rejects PDFs, so the pages here are plain HTML with inline CSS: no external stylesheets,
no CDN, no JavaScript. The markdown files stay the single source of truth.
"""

from __future__ import annotations

import html
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEGAL = ROOT / "legal"
PAGES = {
    "PRIVACY.md": ("privacy.html", None, "en"),
}

STYLE = """
    :root { color-scheme: light dark; }
    body { margin: 0 auto; max-width: 52rem; padding: 2.5rem 1.25rem 4rem; line-height: 1.65;
           font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif; }
    h1 { font-size: 1.9rem; line-height: 1.25; margin: 0 0 .4rem; }
    h2 { font-size: 1.35rem; margin: 2.4rem 0 .6rem; }
    h3 { font-size: 1.1rem; margin: 1.8rem 0 .5rem; }
    p, li, td, th { font-size: 1rem; }
    code { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: .9em;
           background: rgba(127,127,127,.16); padding: .1em .35em; border-radius: .25rem; }
    table { border-collapse: collapse; width: 100%; margin: 1rem 0; display: block; overflow-x: auto; }
    th, td { border: 1px solid rgba(127,127,127,.4); padding: .5rem .6rem; text-align: left;
             vertical-align: top; }
    th { background: rgba(127,127,127,.12); }
    hr { border: 0; border-top: 1px solid rgba(127,127,127,.35); margin: 2.5rem 0 1.5rem; }
    .lang { margin: 0 0 2rem; font-size: .95rem; }
    footer { margin-top: 3rem; font-size: .9rem; opacity: .75; }
"""


def inline(text: str) -> str:
    text = html.escape(text, quote=False)
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"(?<!\*)\*([^*\n]+?)\*(?!\*)", r"<em>\1</em>", text)
    text = re.sub(r"`(.+?)`", r"<code>\1</code>", text)
    return text


def table_block(lines: list[str]) -> str:
    rows = [[c.strip() for c in line.strip().strip("|").split("|")] for line in lines]
    head, body = rows[0], rows[2:]
    out = ["<table>", "<thead><tr>" + "".join(f"<th>{inline(c)}</th>" for c in head) + "</tr></thead>", "<tbody>"]
    for row in body:
        out.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in row) + "</tr>")
    out += ["</tbody>", "</table>"]
    return "\n".join(out)


def convert(markdown: str, other_page: str, other_label: str) -> str:
    body: list[str] = []
    lines = markdown.splitlines()
    index = 0
    while index < len(lines):
        line = lines[index]
        stripped = line.strip()
        if not stripped:
            index += 1
            continue
        if stripped == "---":
            body.append("<hr>")
            index += 1
            continue
        if stripped.startswith("|"):
            block = []
            while index < len(lines) and lines[index].strip().startswith("|"):
                block.append(lines[index])
                index += 1
            body.append(table_block(block))
            continue
        heading = re.match(r"^(#{1,4})\s+(.*)$", stripped)
        if heading:
            level = len(heading.group(1))
            body.append(f"<h{level}>{inline(heading.group(2))}</h{level}>")
            index += 1
            continue
        if re.match(r"^[-*]\s+", stripped):
            items = []
            while index < len(lines) and re.match(r"^[-*]\s+", lines[index].strip()):
                items.append(f"<li>{inline(lines[index].strip()[2:])}</li>")
                index += 1
            body.append("<ul>" + "".join(items) + "</ul>")
            continue
        paragraph = [stripped]
        index += 1
        while index < len(lines) and lines[index].strip() and not re.match(
                r"^(#{1,4}\s|[-*]\s|\||---)", lines[index].strip()):
            paragraph.append(lines[index].strip())
            index += 1
        # A block of "**Label:** value" lines is a header/metadata block, not prose: keep its line
        # breaks instead of collapsing everything into one run-on paragraph.
        parts: list[str] = []
        for position, entry in enumerate(paragraph):
            if position and (entry.startswith("**") or paragraph[position - 1].endswith("**")):
                parts.append("<br>")
            parts.append(inline(entry))
        body.append("<p>" + " ".join(parts) + "</p>")

    title = next((re.sub(r"^#\s+", "", l) for l in lines if l.startswith("# ")), "Privacy Policy")
    language_nav = f'<nav class="lang"><a href="{other_page}">{other_label}</a></nav>' if other_page else ""
    return f"""<!DOCTYPE html>
<html lang="{'tr' if 'Gizlilik' in title else 'en'}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<style>{STYLE}</style>
</head>
<body>
{language_nav}
{chr(10).join(body)}
<footer>This page is the privacy policy for the Android TV application AirRelay Receiver
(<code>com.airrelay.receiver</code>). It is served as plain HTML because Google Play does not accept
PDF privacy policies.</footer>
</body>
</html>
"""


INDEX = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>AirRelay Receiver — Legal</title>
<style>{style}</style>
</head>
<body>
<h1>AirRelay Receiver — Legal</h1>
<p>Legal documents for the Android TV application <strong>AirRelay Receiver</strong>
(<code>com.airrelay.receiver</code>), developed by <strong>Mahmut Enes Bozdoğan</strong>.</p>
<h2>Privacy policy</h2>
<ul>
  <li><a href="privacy.html">English — Privacy Policy</a></li>
</ul>
<h2>Licence and source code</h2>
<p>AirRelay Receiver is free software, conveyed as a whole under the
<a href="https://www.gnu.org/licenses/gpl-3.0.html">GNU General Public License, version 3</a>.
It includes third-party components under GPL-3.0, LGPL-2.1-or-later, Apache-2.0, MIT and BSD-2-Clause
licences; the full licence texts and copyright notices are listed in the corresponding source and in
the app under <em>Settings → About → Component licences</em>.</p>
<ul>
  <li><a href="https://github.com/enes32-hub/AirRelay">Corresponding source code</a></li>
  <li><a href="https://github.com/enes32-hub/AirRelay/blob/main/legal/NOTICE.md">Third-party notices</a></li>
  <li><a href="https://github.com/enes32-hub/AirRelay/blob/main/LICENSE">GNU General Public License v3</a></li>
</ul>
<h2>Contact</h2>
<p>E-mail: <a href="mailto:alquariz@yandex.com">alquariz@yandex.com</a></p>
<hr>
<p>Apple, AirPlay, iPhone, iPad and Mac are trademarks of Apple Inc. This application is not
affiliated with, endorsed by or sponsored by Apple Inc.</p>
</body>
</html>
"""

README = """# AirRelay Receiver — legal documents

Public legal pages for **AirRelay Receiver** (`com.airrelay.receiver`), an AirPlay receiver for
Android TV developed by **Mahmut Enes Bozdoğan**.

| Page | URL |
|---|---|
| Privacy policy (English) | https://{user}.github.io/AirRelay/privacy.html |
| Landing page | https://{user}.github.io/AirRelay/ |

The privacy-policy URL to enter in the Google Play Console store listing is the **English** page.

## Sources

| File | Purpose |
|---|---|
| `PRIVACY.md` | English source (single source of truth) |
| `build_site.py` | Renders the markdown above into `privacy.html` and `index.html` |

Pages are plain HTML with inline CSS — no external stylesheet, no CDN, no JavaScript — because the
Play Console requires the policy to be on an active, non-editable URL and rejects PDFs.

Rebuild after editing the markdown:

```bash
python build_site.py          # regenerate privacy.html, index.html and this README
python build_site.py --check  # fail if any generated page is stale (run by scripts/run_full_check.ps1)
```

## Content basis

Every statement about the application was verified against the 1.0.8 source tree, in particular:

- no analytics, advertising or crash-reporting SDK is present,
- the app contains no hard-coded internet address and contacts no developer or third-party server,
- `RECORD_AUDIO` is required only as the prerequisite of Android's audio-playback-capture API, and
  capture is restricted to the application's own audio output (its own UID); the microphone is never
  read,
- recordings, app settings and support logs are stored on the device, with
  `android:allowBackup="false"`,
- the licence list in section 8 matches the components compiled into the shipped binary.
"""


def outputs() -> dict[Path, str]:
    """Every generated file, so a --check run and a write run cannot disagree."""
    result: dict[Path, str] = {}
    for source, (page, other, _label) in PAGES.items():
        result[ROOT / page] = convert((LEGAL / source).read_text(encoding="utf-8"), other, "")
    result[ROOT / "index.html"] = INDEX.format(style=STYLE)
    result[LEGAL / "README.md"] = README.replace("{user}", "enes32-hub")
    return result


def main() -> int:
    files = outputs()
    if "--check" in sys.argv[1:]:
        stale = [path for path, text in files.items()
                 if not path.is_file() or path.read_text(encoding="utf-8") != text]
        for path in stale:
            print(f"{path.relative_to(ROOT)} is stale; run this script without --check", file=sys.stderr)
        if stale:
            return 1
        print(f"  {len(files)} generated pages are up to date")
        return 0
    for path, text in files.items():
        path.write_text(text, encoding="utf-8", newline="\n")
        print(f"  {path.relative_to(ROOT)} ({len(text)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
