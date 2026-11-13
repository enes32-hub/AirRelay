#!/usr/bin/env python3
"""Enforce the publication rules for everything that ships in the public repository.

The GitHub tree is read by people who never built this project: a Play reviewer, a security
researcher, a downstream packager. Three rules therefore apply to what is published, and this
script makes them rules instead of good intentions — ``scripts/run_full_check.ps1`` runs it, so a
violation fails the gate instead of reaching the repository.

1. **English only.** Every published document — the repository README, the legal pages, the docs
   directory, the evidence records and the two in-app legal documents — is written in English. The
   author's own name keeps its spelling; nothing else does. App translations under
   ``res/values-*/`` are the product's user interface, not documentation, and are exempt.
2. **No private information.** No home-directory paths, no private network addresses, no MAC
   addresses, no credentials, and no personal e-mail address other than the published project
   contact.
3. **No unfinished text.** No TODO/FIXME/TBD markers and no ``<FILL THIS IN>``-style placeholders
   in a published document, and no unresolved SPDX ``NOASSERTION`` in a shipped legal document.

Vendored third-party trees are excluded: they are upstream sources, reproduced as they are.

    python scripts/check_publication.py
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Upstream trees reproduced verbatim; their wording is not ours to edit.
EXCLUDED_PREFIXES = ("app/src/main/cpp/third_party/", "gradle/wrapper/")

TEXT_SUFFIXES = {
    ".md", ".txt", ".csv", ".html", ".json", ".xml", ".kts", ".kt", ".py", ".ps1",
    ".sh", ".cmd", ".bat", ".toml", ".pro", ".yml", ".yaml", ".properties", ".example",
}

# Documents that must read as English prose. App translations and source files are not documents.
DOCUMENT_PREFIXES = ("legal/", "docs/", "artifacts/", "app/src/main/assets/", "app/src/main/res/values/")
DOCUMENT_FILES = {"README.md", "ACCEPTANCE.md", "index.html", "privacy.html"}

PUBLIC_CONTACT = "alquariz@yandex.com"
AUTHOR_NAME = "Bozdoğan"

TURKISH_ONLY_LETTERS = re.compile(r"[çğıöşüÇĞİÖŞÜ]")
# Distinctive Turkish words. ASCII-folded spellings are included on purpose: a Turkish sentence
# typed without diacritics is still a Turkish sentence, and those spellings do not occur in English.
TURKISH_WORDS = re.compile(
    r"\b(ve|bir|için|icin|ile|daha|olarak|kadar|sonra|ancak|veya|gibi|tüm|tum|değil|degil|"
    r"Ayarlar|Hakkında|Hakkinda|Gizlilik|politikası|politikasi|sürüm|surum|kaynak|"
    r"kullanıcı|kullanici|uygulama|cihaz|bağlantı|baglanti|yayın|yayin|alıcı|alici|"
    r"yansıtma|yansitma|kayıt|kayit|izin|güvenlik|guvenlik|tercihleri|ayarları|ayarlari|"
    r"lütfen|lutfen|GEREKÇE|GEREKCE|SÜRÜM|SURUM|KANIT|DOGRULAMA|YAPILACAK|GELISTIRME|"
    r"kurulum|Kurulum|adımları|adimlari|lisans)\b"
)

PLACEHOLDERS = (
    ("unfinished-marker", re.compile(r"\b(TODO|FIXME|TBD|TBA)\b")),
    ("not-set-marker", re.compile(r"<[A-Z0-9][A-Z0-9 _\-]*(?:NOT[ _-]?SET|FILL|TODO)[^>]*>")),
    ("bracket-placeholder", re.compile(r"\[(?:insert|fill|your|name of|put )[^\]]*\]", re.I)),
    ("fill-instruction", re.compile(r"\b(fill (?:this|here|in)\b|buraya yaz|doldurun)", re.I)),
)

PRIVATE_PATH = re.compile(r"[A-Za-z]:\\Users\\[^\\\s\"']+|/home/[a-z_][a-z0-9_-]*/")
IPV4 = re.compile(r"\b(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})\b")
MAC = re.compile(r"\b(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\b")
# A credential needs a literal value, not the name of a property that is read from a file.
SECRET = re.compile(
    r"(?:storePassword|keyPassword)\s*=\s*[\"'][^\"'\n]{8,}[\"']|"
    r"(?:storePassword|keyPassword)\s*=\s*(?!localProps|providers|Providers|getProperty|System|env)"
    r"[A-Za-z0-9!@#$%^&*()_+\-]{8,}|"
    r"-----BEGIN [A-Z ]*PRIVATE KEY-----|"
    r"gh[pousr]_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|xox[baprs]-[A-Za-z0-9-]{10,}"
)
EMAIL = re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b")
EMAIL_ALLOW = {PUBLIC_CONTACT}

# The rule sheet has to quote the very patterns it forbids (a home-directory path, a TODO
# marker, a fill-in placeholder), so it is the one file exempt from the pattern scan. It is still
# an English document and is reviewed by hand when the rules change.
EXEMPT_FILES = {"docs/PUBLICATION_POLICY.md"}

HARMLESS_IPV4 = {"127.0.0.1", "0.0.0.0", "255.255.255.255"}

NOASSERTION_FILES = (
    "legal/SBOM.spdx.json",
    "app/src/main/assets/OPEN_SOURCE_NOTICES.txt",
    "app/src/main/assets/PRIVACY_POLICY.txt",
)


def published_files() -> list[str]:
    if (ROOT / ".git").exists():
        done = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=True)
        tracked = [entry.replace("\\", "/") for entry in done.stdout.decode("utf-8").split("\0") if entry]
    else:
        tracked = [p.relative_to(ROOT).as_posix() for p in ROOT.rglob("*") if p.is_file()]
    return sorted(
        rel for rel in tracked
        if Path(rel).suffix.lower() in TEXT_SUFFIXES
        and not any(rel.startswith(prefix) for prefix in EXCLUDED_PREFIXES)
    )


def is_document(relative: str) -> bool:
    return relative in DOCUMENT_FILES or any(relative.startswith(prefix) for prefix in DOCUMENT_PREFIXES)


def private_address(text: str) -> str | None:
    for match in IPV4.finditer(text):
        value = match.group(0)
        if value in HARMLESS_IPV4:
            continue
        octets = [int(part) for part in match.groups()]
        if any(octet > 255 for octet in octets):
            continue
        first, second = octets[0], octets[1]
        if first == 10 or (first == 172 and 16 <= second <= 31) or (first == 192 and second == 168) \
                or (first == 100 and 64 <= second <= 127) or first == 127:
            return value
    return None


def scan(relative: str, text: str) -> list[str]:
    problems: list[str] = []
    document = is_document(relative)

    for number, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()

        if document:
            # 1. English only — the author's name is the single permitted exception.
            without_name = stripped.replace(AUTHOR_NAME, "")
            if TURKISH_ONLY_LETTERS.search(without_name):
                problems.append(f"{relative}:{number}: non-English letter -> {stripped[:100]}")
            elif TURKISH_WORDS.search(without_name):
                problems.append(f"{relative}:{number}: non-English word -> {stripped[:100]}")
            for email in EMAIL.findall(line):
                if email.lower() not in EMAIL_ALLOW and not email.lower().endswith((".example", ".invalid")):
                    problems.append(f"{relative}:{number}: unexpected e-mail {email}")

            # 3. Unfinished text.
            for label, pattern in PLACEHOLDERS:
                match = pattern.search(stripped)
                if match:
                    problems.append(f"{relative}:{number}: {label} -> {match.group(0)[:60]}")

        # 2. Private information — applies to code as well, where a leaked path is equally real.
        if PRIVATE_PATH.search(line):
            problems.append(f"{relative}:{number}: home-directory path -> {stripped[:100]}")
        address = private_address(line)
        if address:
            problems.append(f"{relative}:{number}: private network address {address}")
        if MAC.search(line):
            problems.append(f"{relative}:{number}: MAC address -> {stripped[:100]}")
        if SECRET.search(line):
            problems.append(f"{relative}:{number}: credential-shaped text -> {stripped[:100]}")

    if relative in NOASSERTION_FILES and re.search(
            r'"licenseDeclared"\s*:\s*"NOASSERTION"|^Licence: NOASSERTION', text, re.M):
        problems.append(f"{relative}: shipped legal document still declares an unresolved licence")

    return problems


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    files = published_files()
    problems: list[str] = []
    for relative in files:
        if relative in EXEMPT_FILES:
            continue
        try:
            text = (ROOT / relative).read_text(encoding="utf-8-sig")
        except (UnicodeDecodeError, OSError):
            continue
        problems.extend(scan(relative, text))

    if problems:
        print(f"FAIL publication rules: {len(problems)} problem(s) in {len(files)} published files")
        for problem in problems[:40]:
            print("  " + problem)
        if len(problems) > 40:
            print(f"  ... {len(problems) - 40} more")
        return 1

    documents = sum(1 for rel in files if is_document(rel))
    print(f"PASS publication rules: {len(files)} published files are free of private information "
          f"and placeholders; {documents} documents are English-only")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
