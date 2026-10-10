"""Generate legal/SBOM.spdx.json from the dependency sources declared in this checkout.

Scope and honesty rules
-----------------------
This generator reads only sources that exist in the repository:

* ``gradle/libs.versions.toml`` for the declared direct Gradle/AndroidX dependencies,
* ``app/build.gradle.kts`` for the application package identity and version,
* the native build files for the pinned native components (OpenSSL, FFmpeg, UxPlay, libplist).

It deliberately does **not** invent the transitive closure. The document records exactly what
was derived, states what was not, and names the command that produces the full graph. Licence
values come from an explicit table below; anything not in that table is ``NOASSERTION`` with a
comment naming the source, never a guess.
"""

import argparse
import datetime
import json
import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "legal" / "SBOM.spdx.json"
TOOL = "tools/generate_sbom.py"

# group/name prefix -> (SPDX licence id, where the claim comes from)
LICENCE_TABLE = [
    ("androidx.", "Apache-2.0", "AndroidX is published under the Apache License 2.0"),
    ("org.jetbrains.kotlin", "Apache-2.0", "Kotlin is published under the Apache License 2.0"),
    ("com.google.dagger", "Apache-2.0", "Dagger/Hilt is published under the Apache License 2.0"),
    ("com.google.android.material", "Apache-2.0", "Material Components for Android is Apache-2.0"),
    ("com.google.oboe", "Apache-2.0", "Oboe is published under the Apache License 2.0"),
    ("androidx.media3", "Apache-2.0", "AndroidX Media3 is published under the Apache License 2.0"),
]

NATIVE_LICENCES = {
    "UxPlay": ("GPL-3.0-only", "UxPlay is licensed under GPL-3.0; see OPEN_SOURCE_NOTICES.txt"),
    "FFmpeg": ("LGPL-2.1-or-later", "FFmpeg is built with the LGPL configuration recorded in legal/NOTICE.md"),
    "libplist": ("LGPL-2.1-or-later", "libplist is licensed under LGPL-2.1-or-later"),
    "OpenSSL": ("Apache-2.0", "OpenSSL 3.x is licensed under the Apache License 2.0"),
    "libairplay_native": ("GPL-3.0-only", "The receiver links the GPL-3.0 UxPlay core"),
}

TRANSITIVE_COMMAND = (
    "Run `gradlew :app:dependencies --configuration releaseRuntimeClasspath` (or the equivalent "
    "for the shipped variant) and merge the result into this document to obtain the transitive "
    "Gradle closure; the native closure is produced by the CMake ExternalProject pins recorded "
    "in app/src/main/cpp."
)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8-sig", errors="replace")


def app_identity() -> tuple[str, str]:
    text = read_text(ROOT / "app/build.gradle.kts")
    version_name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    version_code = re.search(r"versionCode\s*=\s*(\d+)", text)
    name = "AirRelay Receiver"
    version = version_name.group(1) if version_name else "NOASSERTION"
    if version_code:
        version = f"{version} (versionCode {version_code.group(1)})"
    return name, version


def gradle_libraries() -> list[dict]:
    catalog = tomllib.loads(read_text(ROOT / "gradle/libs.versions.toml"))
    versions = catalog.get("versions", {})
    components = []
    for alias, entry in sorted(catalog.get("libraries", {}).items()):
        group, name = entry.get("group"), entry.get("name")
        if not group or not name:
            # platform/BOM aliases without coordinates are recorded by the BOM they pin.
            continue
        # `version.ref = "x"` parses as a nested table, and libraries without any version are
        # versioned by the Compose BOM.
        raw_version = entry.get("version")
        reference = None
        if isinstance(raw_version, dict):
            reference = raw_version.get("ref")
            raw_version = None
        if isinstance(entry.get("version.ref"), str):
            reference = entry["version.ref"]
        version = raw_version or (versions.get(reference) if reference else None)
        components.append({"alias": alias, "group": group, "name": name,
                           "version": version or "NOASSERTION", "kind": "maven",
                           "managed_by_bom": version is None})
    return components


# name -> (lock id or None, version or None, SPDX licence, comment)
NATIVE_COMPONENTS = [
    ("UxPlay", "uxplay-pinned-submodule", None, "GPL-3.0-only",
     "UxPlay is licensed under GPL-3.0; see OPEN_SOURCE_NOTICES.txt"),
    ("libplist", "libplist", "2.3.1", "LGPL-2.1-or-later",
     "libplist is licensed under LGPL-2.1-or-later"),
    ("FFmpeg", "ffmpeg-selected-alac", None, "LGPL-2.1-or-later",
     "FFmpeg is built with the LGPL configuration recorded in legal/NOTICE.md"),
    ("OpenSSL", None, None, "Apache-2.0", "OpenSSL 3.x is licensed under the Apache License 2.0"),
    ("openssl-cmake", "openssl-cmake", None, "NOASSERTION",
     "CMake build wrapper; licence not recorded in this repository, verify before distribution"),
    ("RPiPlay (playfair antecedent)", "rpiplay-antecedent", None, "GPL-3.0-only",
     "Playfair's GPL-3.0 origin; see docs/research/native-audit.lock.json"),
    ("playfair", "playfair-origin-reference", None, "GPL-3.0-only",
     "Playfair's GPL-3.0 origin; see docs/research/native-audit.lock.json"),
    ("android-airplay-server (JNI wrapper)", "android-wrapper", None, "GPL-3.0-only",
     "Integration candidate core; see legal/NOTICE.md"),
]


def native_components() -> list[dict]:
    """Derive native provenance from the pinned inventory, not from guessed file contents."""
    lock = json.loads(read_text(ROOT / "docs/research/native-audit.lock.json"))
    pinned = {entry["id"]: entry for entry in lock["repositories"]}
    components = []
    openssl = re.search(r'set\(OPENSSL_BUILD_VERSION\s+"([^"]+)"',
                        read_text(ROOT / "app/src/main/cpp/CMakeLists.txt"))
    openssl_version = openssl.group(1) if openssl else "NOASSERTION"
    ffmpeg_release = ROOT / "app/src/main/cpp/third_party/ffmpeg/RELEASE"
    ffmpeg_version = read_text(ffmpeg_release).strip() if ffmpeg_release.is_file() else None

    for name, lock_id, override_version, licence, comment in NATIVE_COMPONENTS:
        entry = pinned.get(lock_id) if lock_id else None
        commit = entry["commit"] if entry else None
        repository = entry["repository"] if entry else None
        version = override_version
        if name == "OpenSSL":
            version = openssl_version
        elif name == "FFmpeg":
            version = ffmpeg_version
        if not version:
            version = commit or "NOASSERTION"
        elif commit:
            version = f"{version} (commit {commit[:12]})"
        source = f"docs/research/native-audit.lock.json ({repository})" if repository else \
            "app/src/main/cpp/CMakeLists.txt"
        components.append({"name": name, "version": version, "kind": "native",
                           "licence": licence, "comment": f"{comment} (source: {source})"})
    return components


def licence_for(component: dict) -> tuple[str, str]:
    if component["kind"] == "native":
        return NATIVE_LICENCES.get(component["name"], ("NOASSERTION", "not recorded in this repository"))
    qualified = f'{component["group"]}:{component["name"]}'
    for prefix, licence, comment in LICENCE_TABLE:
        if qualified.startswith(prefix):
            return licence, comment
    return "NOASSERTION", "licence not recorded in this repository; verify before distribution"


def build() -> dict:
    app_name, app_version = app_identity()
    libraries = gradle_libraries()
    natives = native_components()
    packages = [{
        "SPDXID": "SPDXRef-Package-AirRelay",
        "name": app_name,
        "versionInfo": app_version,
        "downloadLocation": "NOASSERTION",
        "filesAnalyzed": False,
        "licenseDeclared": "GPL-3.0-only",
        "licenseComments": "The receiver is distributed as a whole under GPL-3.0 because it links the GPL-3.0 UxPlay core; see legal/NOTICE.md.",
        "supplier": "NOASSERTION",
    }]
    relationships = [{"spdxElementId": "SPDXRef-DOCUMENT",
                      "relationshipType": "DESCRIBES",
                      "relatedSpdxElement": "SPDXRef-Package-AirRelay"}]

    for index, library in enumerate(libraries, 1):
        licence, comment = licence_for(library)
        spdx_id = f"SPDXRef-Maven-{index:03d}"
        version = library["version"]
        purl = f'pkg:maven/{library["group"]}/{library["name"]}'
        if version != "NOASSERTION":
            purl += f"@{version}"
        packages.append({
            "SPDXID": spdx_id,
            "name": f'{library["group"]}:{library["name"]}',
            "versionInfo": version,
            "downloadLocation": "NOASSERTION",
            "filesAnalyzed": False,
            "licenseDeclared": licence,
            "licenseComments": (comment + "; version resolved by the Compose BOM declared in "
                                "gradle/libs.versions.toml") if library.get("managed_by_bom") else comment,
            "supplier": "NOASSERTION",
            "externalRefs": [{"referenceCategory": "PACKAGE-MANAGER", "referenceType": "purl",
                              "referenceLocator": purl}],
        })
        relationships.append({"spdxElementId": "SPDXRef-Package-AirRelay",
                              "relationshipType": "DEPENDS_ON", "relatedSpdxElement": spdx_id})

    for index, native in enumerate(natives, 1):
        spdx_id = f"SPDXRef-Native-{index:03d}"
        packages.append({
            "SPDXID": spdx_id,
            "name": native["name"],
            "versionInfo": native["version"],
            "downloadLocation": "NOASSERTION",
            "filesAnalyzed": False,
            "licenseDeclared": native["licence"],
            "licenseComments": native["comment"],
            "supplier": "NOASSERTION",
        })
        relationships.append({"spdxElementId": "SPDXRef-Package-AirRelay",
                              "relationshipType": "DEPENDS_ON", "relatedSpdxElement": spdx_id})

    created = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat()
    unknown_versions = [p["name"] for p in packages if p["versionInfo"] == "NOASSERTION"]
    return {
        "spdxVersion": "SPDX-2.3",
        "dataLicense": "CC0-1.0",
        "SPDXID": "SPDXRef-DOCUMENT",
        "name": f"AirRelay Receiver SBOM ({app_version})",
        "documentNamespace": f"https://airrelay.local/spdx/{app_version.replace(' ', '-')}/{created}",
        "creationInfo": {"created": created, "creators": [f"Tool: {TOOL}", "Organization: AirRelay"],
                         "comment": ("Generated from declared dependency sources. Covers the direct "
                                     "Gradle dependencies and the pinned native components only.")},
        "comment": (f"Scope: {len(libraries)} declared Gradle dependencies and {len(natives)} pinned "
                    f"native components. This is NOT the transitive closure. {TRANSITIVE_COMMAND} "
                    f"Components whose version could not be derived from the repository are listed "
                    f"with versionInfo NOASSERTION: {', '.join(unknown_versions) if unknown_versions else 'none'}. "
                    "Licence values come from an explicit table in this generator; NOASSERTION means "
                    "the claim must be verified before distribution."),
        "packages": packages,
        "relationships": relationships,
    }


def comparable(document: dict) -> dict:
    """Strip the volatile fields so --check compares content, not the generation timestamp."""
    clone = json.loads(json.dumps(document))
    clone.pop("documentNamespace", None)
    clone.get("creationInfo", {}).pop("created", None)
    return clone


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--check", action="store_true",
                        help="exit non-zero if the file on disk differs from a fresh generation "
                             "(the generation timestamp and namespace are ignored)")
    args = parser.parse_args()
    document = build()
    rendered = json.dumps(document, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if not args.output.is_file():
            print(f"{args.output} is missing; run {TOOL} to generate it", file=sys.stderr)
            raise SystemExit(1)
        try:
            current = json.loads(args.output.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            print(f"{args.output} is not valid JSON: {error}", file=sys.stderr)
            raise SystemExit(1)
        if comparable(current) != comparable(document):
            print(f"{args.output} is stale; run {TOOL} to regenerate", file=sys.stderr)
            raise SystemExit(1)
        print(f"{args.output} is up to date ({len(document['packages'])} packages)")
        return
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(rendered, encoding="utf-8")
    print(f"Wrote {args.output} with {len(document['packages'])} packages "
          f"({len(document['relationships'])} relationships)")


if __name__ == "__main__":
    main()
