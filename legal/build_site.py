"""Render the privacy policy markdown into self-contained HTML pages for GitHub Pages.

Google Play requires the store-listing privacy policy to live on an active, non-editable URL and
explicitly rejects PDFs, so the pages here are plain HTML with inline CSS: no external stylesheets,
no CDN, no JavaScript. The markdown files stay the single source of truth.
"""

from __future__ import annotations

import html
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEGAL = ROOT / "legal"
PAGES = {
    "PRIVACY.md": ("privacy.html", "privacy-tr.html", "en"),
    "PRIVACY.tr.md": ("privacy-tr.html", "privacy.html", "tr"),
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
        body.append("<p>" + inline(" ".join(paragraph)) + "</p>")

    title = next((re.sub(r"^#\s+", "", l) for l in lines if l.startswith("# ")), "Privacy Policy")
    return f"""<!DOCTYPE html>
<html lang="{'tr' if 'Gizlilik' in title else 'en'}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<style>{STYLE}</style>
</head>
<body>
<nav class="lang"><a href="{other_page}">{other_label}</a></nav>
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
  <li><a href="privacy-tr.html">Türkçe — Gizlilik Politikası</a></li>
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
| Privacy policy (English) | https://{user}.github.io/airrelay-legal/privacy.html |
| Gizlilik politikası (Türkçe) | https://{user}.github.io/airrelay-legal/privacy-tr.html |
| Landing page | https://{user}.github.io/airrelay-legal/ |

The privacy-policy URL to enter in the Google Play Console store listing is the **English** page.

## Sources

| File | Purpose |
|---|---|
| `PRIVACY.md` | English source (single source of truth) |
| `PRIVACY.tr.md` | Turkish translation |
| `build_site.py` | Renders the markdown above into `privacy.html`, `privacy-tr.html`, `index.html` |

Pages are plain HTML with inline CSS — no external stylesheet, no CDN, no JavaScript — because the
Play Console requires the policy to be on an active, non-editable URL and rejects PDFs.

Rebuild after editing the markdown:

```bash
python build_site.py
```

## Content basis

Every statement about the application was verified against the 1.0.0 source tree, in particular:

- no analytics, advertising or crash-reporting SDK is present,
- the app contains no hard-coded internet address and contacts no developer or third-party server,
- `RECORD_AUDIO` is required only as the prerequisite of Android's audio-playback-capture API, and
  capture is restricted to the application's own audio output (its own UID); the microphone is never
  read,
- recordings, saved videos, settings and support logs are stored on the device, with
  `android:allowBackup="false"`.
"""


def main() -> int:
    for source, (page, other, other_label) in PAGES.items():
        markdown = (LEGAL / source).read_text(encoding="utf-8")
        label = "Türkçe" if other_label == "tr" else "English"
        rendered = convert(markdown, other, f"{label} version" if label == "English" else f"{label} sürüm")
        (ROOT / page).write_text(rendered, encoding="utf-8", newline="\n")
        print(f"  {source} -> {page} ({len(rendered)} bytes)")
    (ROOT / "index.html").write_text(INDEX.format(style=STYLE), encoding="utf-8", newline="\n")
    print("  index.html")
    user = "USER"
    (LEGAL / "README.md").write_text(README.replace("{user}", user), encoding="utf-8", newline="\n")
    print("  legal/README.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
