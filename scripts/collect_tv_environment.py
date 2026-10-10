"""Read allowlisted metadata from one explicitly selected, authorized ADB TV.

Never pairs, connects, installs, changes settings, or records the ADB address.
This report is environment preparation, never physical mirroring acceptance.
"""

import argparse
import json
import os
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROPERTIES = {
    "manufacturer": "ro.product.manufacturer",
    "model": "ro.product.model",
    "android_version": "ro.build.version.release",
    "android_api": "ro.build.version.sdk",
    "build_id": "ro.build.id",
    "supported_abis": "ro.product.cpu.abilist",
    "hardware": "ro.hardware",
    "kernel_qemu": "ro.kernel.qemu",
    "boot_qemu": "ro.boot.qemu",
}


def query(adb: Path, *args: str) -> str:
    result = subprocess.run([str(adb), *args], capture_output=True, text=True,
                            errors="replace", timeout=15)
    if result.returncode:
        # ADB error text can include serials/addresses: do not persist it.
        raise RuntimeError("ADB_QUERY_FAILED")
    return result.stdout.strip()


def collect(adb: Path, serial: str) -> dict:
    states = {}
    for line in query(adb, "devices").splitlines():
        fields = line.split()
        if len(fields) == 2 and fields[1] in ("device", "offline", "unauthorized"):
            states[fields[0]] = fields[1]
    if states.get(serial) != "device":
        return {"status": "TARGET_NOT_AUTHORIZED_OR_CONNECTED", "metadata": None}
    if serial.startswith("emulator-"):
        return {"status": "EMULATOR_REJECTED", "metadata": None}
    metadata = {key: query(adb, "-s", serial, "shell", "getprop", prop)[:256]
                for key, prop in PROPERTIES.items()}
    features = query(adb, "-s", serial, "shell", "pm", "list", "features").splitlines()
    metadata["leanback"] = "feature:android.software.leanback" in features
    if (metadata["kernel_qemu"] == "1" or metadata["boot_qemu"] == "1"
            or metadata["hardware"] in ("ranchu", "goldfish")):
        status = "EMULATOR_REJECTED"
    elif not metadata["leanback"]:
        status = "TV_FEATURE_NOT_FOUND"
    elif not all(metadata[key] for key in ("model", "android_api", "supported_abis")):
        status = "INCOMPLETE_METADATA"
    else:
        # Device properties are not proof of physical origin or codec performance.
        status = "TV_METADATA_COLLECTED_MANUAL_DEVICE_MATCH_REQUIRED"
    return {"status": status, "metadata": metadata}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit connected ADB target; not saved")
    parser.add_argument("--adb", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/local/tv-environment.json")
    args = parser.parse_args()
    default_sdk = Path(os.environ.get("LOCALAPPDATA", Path.home() / "Android")) / "Android/Sdk"
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or default_sdk)
    adb = args.adb or sdk / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
    try:
        result = collect(adb, args.serial)
    except (OSError, subprocess.TimeoutExpired, RuntimeError) as error:
        result = {"status": "QUERY_ERROR", "error_type": type(error).__name__, "metadata": None}
    report = {"observed_at": datetime.now(timezone.utc).isoformat(),
              "scope": "tv-environment-only", "acceptance_status": "NOT_RUN",
              "physical_mirroring_verified": False, **result}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    raise SystemExit(0 if result["status"] == "TV_METADATA_COLLECTED_MANUAL_DEVICE_MATCH_REQUIRED" else 2)


if __name__ == "__main__":
    main()
