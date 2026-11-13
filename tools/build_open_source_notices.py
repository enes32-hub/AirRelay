"""Assemble app/src/main/assets/OPEN_SOURCE_NOTICES.txt from the vendored licence files.

The in-app "Component licences" screen must reproduce every licence and copyright notice that the
shipped binary is obliged to carry. Writing that by hand drifted: the file once declared the public
source channel as "pending", listed libplist under the wrong licence, and omitted the MIT/BSD
notices of components compiled into libairplay_native.so. This generator makes the file reproducible
from sources that are already pinned in the repository:

* ``LICENSE`` — the GPL-3.0 text the application is conveyed under,
* ``app/src/main/cpp/third_party/libplist/COPYING.LESSER`` — LGPL-2.1,
* ``legal/APACHE-2.0.txt`` — Apache-2.0 (OpenSSL 3.x and the AndroidX/Kotlin stack),
* ``app/src/main/cpp/third_party/ffmpeg/LICENSE.md`` — FFmpeg's licence notice,
* ``app/src/main/cpp/third_party/UxPlay/lib/llhttp/LICENSE-MIT`` — llhttp,
* ``app/src/main/cpp/third_party/openssl-cmake/LICENSE`` — openssl-cmake,
* the copyright headers of ``lib/srp.c``, ``libplist/src/jsmn.c``, ``libplist/src/time64.c`` and
  ``openssl-cmake/cmake/TargetArch.cmake`` — the embedded MIT and BSD-2-Clause parts.

Run it after touching any of those files:

    python tools/build_open_source_notices.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CPP = ROOT / "app" / "src" / "main" / "cpp"
TARGET = ROOT / "app" / "src" / "main" / "assets" / "OPEN_SOURCE_NOTICES.txt"

HEAD = """AirRelay Receiver — Open-source notices
=======================================

AirRelay Receiver (com.airrelay.receiver), an AirPlay receiver for Android TV.
Copyright (C) 2026 Mahmut Enes Bozdoğan.
The application is conveyed as a whole under the GNU General Public License, version 3.

Corresponding source for every published build:
    https://github.com/enes32-hub/AirRelay
and the corresponding-source archive that ships with each release. Both contain this checkout in
full: the application source, the vendored third-party sources in the form they are compiled in,
the patches applied to them, and the build instructions.

Apple, AirPlay, iPhone, iPad and Mac are trademarks of Apple Inc. This application is not
affiliated with, endorsed by or sponsored by Apple Inc.

==============================================================================
COMPONENTS INCLUDED IN THE APPLICATION
==============================================================================

GNU General Public License, version 3 (the licence of the application as a whole)
  - UxPlay — AirPlay mirroring server.
    Copyright (C) Juho Vähä-Herttua, Florian Draschbacher, Jaslo Ziska, F. Duncanh and
    contributors. https://github.com/FDH2/UxPlay
  - playfair / RPiPlay playfair layer — FairPlay handshake helpers.
    Copyright (C) F. Duncanh and the RPiPlay contributors.
    https://github.com/EstebanKubata/playfair, https://github.com/FD-/RPiPlay
  - android-airplay-server integration and Android JNI bridge.
    Copyright (C) the android-airplay-server authors.
    https://github.com/jqssun/android-airplay-server

GNU Lesser General Public License, version 2.1 or later
  - libplist — Apple property-list parsing.
    Copyright (C) 2008 Zach C., 2009 Jonathan Beck, 2010-2015 Martin Szulecki,
    2011-2023 Nikias Bassen. https://github.com/libimobiledevice/libplist
  - libcnary — linked-node container bundled with libplist.
    Copyright (C) 2011 Joshua Hill.
  - Shairplay-derived RAOP, HTTP and pairing layer used by UxPlay.
    Copyright (C) 2011-2015 Juho Vähä-Herttua, 2019 dsafa22.
  - FFmpeg — minimal static build (libavcodec + libavutil, ALAC decoder only, no GPL and no
    non-free parts enabled). Copyright (C) 2000-2026 the FFmpeg developers.
    https://ffmpeg.org

Apache License, version 2.0
  - OpenSSL 3.4.4 — cryptography used by the pairing and FairPlay handshake.
    Copyright (C) 1998-2025 The OpenSSL Project; Copyright (C) 1995-1998 Eric A. Young,
    Tim J. Hudson.
  - AndroidX, Jetpack Compose, Media3 (ExoPlayer), Material Components for Android,
    Dagger/Hilt and Oboe. Copyright (C) The Android Open Source Project and their authors.
  - Kotlin and kotlinx.coroutines. Copyright (C) JetBrains s.r.o.

MIT License
  - llhttp 9.4.2 — HTTP request parser embedded in UxPlay. Copyright Fedor Indutny, 2018.
  - csrp (UxPlay lib/srp.c) — SRP-6a implementation.
    Copyright (c) 2010-2014 Tom Cocagne.
  - jsmn (libplist src/jsmn.c) — JSON tokenizer. Copyright (c) 2010 Serge A. Zaitsev.
  - time64 (libplist src/time64.c) — 64-bit time helpers.
    Copyright (c) 2007-2010 Michael G Schwern.
  - openssl-cmake — CMake build wrapper for OpenSSL.
    Copyright (c) 2015-2024 The ViaDuck Project.

BSD 2-Clause License
  - TargetArch.cmake (openssl-cmake) — CMake architecture detection.
    Copyright (c) 2012 Petroules Corporation.

No advertising, analytics, attribution or tracking SDK is included. Every item above is either
compiled into the application or required to build it; nothing else is shipped.

==============================================================================
FULL LICENCE TEXTS
==============================================================================
"""


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8-sig", errors="replace").replace("\r\n", "\n").strip("\n")


def comment_block(path: Path) -> str:
    """Return the leading C comment of a source file without the delimiters."""
    text = read(path)
    match = re.match(r"\s*/\*(.*?)\*/", text, re.S)
    if not match:
        raise SystemExit(f"no leading comment block in {path}")
    lines = [line.rstrip() for line in match.group(1).split("\n")]
    stripped = [re.sub(r"^\s*\*( ?)", r"\1", line) for line in lines]
    # Some headers separate the licence text from the modification notes with a rule of
    # '=' characters. Inside a notice section that rule reads as a section separator, so drop it.
    kept = [line for line in stripped if not re.fullmatch(r"\s*=+\s*", line)]
    return "\n".join(kept).strip("\n")


def cmake_block(path: Path) -> str:
    """Return a ``#[[ ... ]]`` CMake comment without the delimiters."""
    text = read(path)
    match = re.match(r"\s*#\[\[(.*?)\]\]", text, re.S)
    if not match:
        raise SystemExit(f"no leading bracket comment in {path}")
    return "\n".join(line.rstrip() for line in match.group(1).split("\n")).strip("\n")


def section(title: str, body: str) -> str:
    return f"\n\n==============================================================================\n{title}\n==============================================================================\n\n{body}\n"


def main() -> int:
    parts = [HEAD]
    parts.append(section("GNU General Public License, version 3", read(ROOT / "LICENSE")))
    parts.append(section("GNU Lesser General Public License, version 2.1",
                         read(CPP / "third_party/libplist/COPYING.LESSER")))
    parts.append(section("Apache License, version 2.0", read(ROOT / "legal/APACHE-2.0.txt")))
    parts.append(section("FFmpeg licence notice",
                         read(CPP / "third_party/ffmpeg/LICENSE.md")))
    parts.append(section("MIT License — llhttp", read(CPP / "third_party/UxPlay/lib/llhttp/LICENSE-MIT")))
    parts.append(section("MIT License — openssl-cmake", read(CPP / "third_party/openssl-cmake/LICENSE")))
    parts.append(section("MIT License — csrp", comment_block(CPP / "third_party/UxPlay/lib/srp.c")))
    parts.append(section("MIT License — jsmn", comment_block(CPP / "third_party/libplist/src/jsmn.c")))
    parts.append(section("MIT License — time64", comment_block(CPP / "third_party/libplist/src/time64.c")))
    parts.append(section("BSD 2-Clause License — TargetArch.cmake",
                         cmake_block(CPP / "third_party/openssl-cmake/cmake/TargetArch.cmake")))
    text = "".join(parts).rstrip("\n") + "\n"
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
    print(f"  {TARGET.relative_to(ROOT)}: {len(text.splitlines())} lines, {len(text)} characters")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
