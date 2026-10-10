"""Compile original ARM probes and verify layout; no mirroring or device claims."""

import argparse
import hashlib
import json
import os
import subprocess
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from environment_doctor import properties
from verify_native import inspect_apk, inspect_elf

ROOT = Path(__file__).resolve().parents[1]
NDK_REVISION = "29.0.14206865"


def probe(sdk: Path) -> dict:
    """Run real NDK/CMake/Ninja tools and retain independently inspectable artifacts."""
    ndk = sdk / "ndk" / NDK_REVISION
    if properties(ndk / "source.properties").get("Pkg.Revision") != NDK_REVISION:
        raise ValueError("Required NDK r29 is not completely installed")
    suffix = ".exe" if os.name == "nt" else ""
    cmake = sdk / "cmake/3.22.1/bin" / f"cmake{suffix}"
    ninja = sdk / "cmake/3.22.1/bin" / f"ninja{suffix}"
    zipalign = sdk / "build-tools/37.0.0" / f"zipalign{suffix}"
    staging = ROOT / ".research/toolchain-probe"
    staging.mkdir(parents=True, exist_ok=True)
    report = {"scope": "compiler-and-artifact-layout-only", "ndk": NDK_REVISION,
              "runtime_verified": False, "mirroring_verified": False, "commands": [], "libraries": []}

    def execute(command: list[str]) -> None:
        result = subprocess.run(command, capture_output=True, text=True, errors="replace", timeout=180)
        report["commands"].append({"command": [part.replace(str(ROOT), "$PROJECT_ROOT").replace(str(sdk), "$ANDROID_SDK") for part in command],
                                   "exit_code": result.returncode})
        if result.returncode:
            raise RuntimeError(f"Tool failed: {Path(command[0]).name}\n{result.stdout}\n{result.stderr}")

    for abi in ("armeabi-v7a", "arm64-v8a"):
        build = staging / abi
        execute([str(cmake), "-S", str(ROOT / "tools/toolchain-probe"), "-B", str(build), "-G", "Ninja",
                 f"-DCMAKE_TOOLCHAIN_FILE={ndk / 'build/cmake/android.toolchain.cmake'}",
                 f"-DCMAKE_MAKE_PROGRAM={ninja}", f"-DANDROID_ABI={abi}", "-DANDROID_PLATFORM=android-26",
                 "-DANDROID_STL=none", "-DCMAKE_BUILD_TYPE=Release"])
        execute([str(cmake), "--build", str(build), "--parallel", "2"])
        library = build / "libtoolchain_probe.so"
        data = library.read_bytes()
        report["libraries"].append({"path": library.relative_to(ROOT).as_posix(),
                                    "sha256": hashlib.sha256(data).hexdigest(), **inspect_elf(data, abi)})
    unaligned = staging / "layout-unaligned.zip"
    aligned = staging / "layout-16kb.zip"
    with zipfile.ZipFile(unaligned, "w", compression=zipfile.ZIP_STORED) as archive:
        for record in report["libraries"]:
            archive.write(ROOT / record["path"], f"lib/{record['abi']}/libtoolchain_probe.so")
    execute([str(zipalign), "-f", "-P", "16", "4", str(unaligned), str(aligned)])
    execute([str(zipalign), "-c", "-P", "16", "4", str(aligned)])
    report["zip_layout"] = inspect_apk(aligned)
    report["zip_layout"]["is_installable_apk"] = False
    report["zip_layout"]["path"] = aligned.relative_to(ROOT).as_posix()
    report["zip_layout"]["sha256"] = hashlib.sha256(aligned.read_bytes()).hexdigest()
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = {"ok": True, **probe(args.sdk.resolve())}
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        result = {"ok": False, "scope": "compiler-and-artifact-layout-only", "error": str(error),
                  "runtime_verified": False, "mirroring_verified": False}
    result["observed_at"] = datetime.now(timezone.utc).isoformat()
    output = ROOT / "artifacts/toolchain-probe.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    raise SystemExit(0 if result["ok"] else 2)


if __name__ == "__main__":
    main()
