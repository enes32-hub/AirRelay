"""Read local Android tooling and ADB status; do not install or connect devices."""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def properties(path: Path) -> dict:
    """Read SDK metadata without trusting directory names as installations."""
    if not path.is_file():
        return {}
    return dict(tuple(part.strip() for part in line.split("=", 1)) for line in path.read_text(encoding="utf-8").splitlines()
                if "=" in line and not line.lstrip().startswith("#"))


def run(command: list[str]) -> tuple[int, str]:
    """Run a read-only tool with a finite timeout, never through a shell."""
    try:
        process = subprocess.run(command, capture_output=True, text=True, timeout=15, errors="replace")
        return process.returncode, process.stdout + process.stderr
    except (OSError, subprocess.TimeoutExpired) as error:
        return -1, type(error).__name__


def adb_counts(output: str) -> dict:
    """Return counts only; omit serials, addresses and model identifiers."""
    counts = {"authorized": 0, "unauthorized": 0, "offline": 0, "emulators": 0}
    for line in output.splitlines():
        parts = line.split()
        if len(parts) < 2 or parts[1] not in ("device", "unauthorized", "offline"):
            continue
        state = "authorized" if parts[1] == "device" else parts[1]
        counts[state] += 1
        if parts[0].startswith("emulator-"):
            counts["emulators"] += 1
    return counts


def sdk_report(sdk: Path) -> dict:
    """Inspect actual platform/NDK/CMake binaries, including partial installs."""
    suffix = ".exe" if os.name == "nt" else ""
    platforms = []
    for directory in sorted((sdk / "platforms").glob("*")):
        data = properties(directory / "source.properties")
        platforms.append({"directory": directory.name, "api": data.get("AndroidVersion.ApiLevel"),
                          "revision": data.get("Pkg.Revision"), "android_jar": (directory / "android.jar").is_file(),
                          "preview": data.get("AndroidVersion.PreviewSdkInt")})
    ndks = []
    for directory in sorted((sdk / "ndk").glob("*")):
        data = properties(directory / "source.properties")
        clang = list(directory.glob(f"toolchains/llvm/prebuilt/*/bin/clang{suffix}"))
        ndks.append({"directory": directory.name, "revision": data.get("Pkg.Revision"),
                     "clang": bool(clang), "complete_basics": bool(data.get("Pkg.Revision") and clang)})
    cmakes = [{"directory": d.name, "cmake": (d / f"bin/cmake{suffix}").is_file(),
               "ninja": (d / f"bin/ninja{suffix}").is_file()}
              for d in sorted((sdk / "cmake").glob("*"))]
    adb = sdk / f"platform-tools/adb{suffix}"
    code, output = run([str(adb), "devices", "-l"]) if adb.is_file() else (-1, "missing")
    return {"platforms": platforms, "ndks": ndks, "cmakes": cmakes,
            "adb_available": adb.is_file(), "adb_query_ok": code == 0,
            "adb_counts": adb_counts(output) if code == 0 else None}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/environment-report.json")
    args = parser.parse_args()
    default_sdk = Path(os.environ.get("LOCALAPPDATA", Path.home() / "Android")) / "Android/Sdk"
    sdk = args.sdk or Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or default_sdk)
    java = shutil.which("java")
    code, output = run([java, "-version"]) if java else (-1, "missing")
    version = re.search(r'version "([^\"]+)"', output)
    report = {"observed_at": datetime.now(timezone.utc).isoformat(), "scope": "local-tooling-only",
              "python": sys.version.split()[0], "java_ok": code == 0,
              "java_version": version.group(1) if version else None,
              "sdk_found": sdk.is_dir(), "sdk": sdk_report(sdk),
              "gradle_wrapper_present": (ROOT / "gradlew.bat").is_file(),
              "physical_tv_verified": False, "iphone_verified": False}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
