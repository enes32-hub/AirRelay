"""Install the current build on the device and photograph the theme picker and both themes.

Environment: ADB (default "adb"), AIRRELAY_TV (device serial), SHOT_DIR (output folder).
"""

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = os.environ.get("ADB", "adb")
SERIAL = os.environ.get("AIRRELAY_TV", "")
SHOTS = Path(os.environ.get("SHOT_DIR", r"C:\Users\ASUS\Desktop\AirRelay\06-EKRAN-GORUNTULERI\themes"))
APK = os.environ.get("AIRRELAY_APK", r"C:\AirRelayThemes\app\build\outputs\apk\release\app-release.apk")
PKG = "com.airrelay.receiver"


def adb(*args: str, timeout: int = 120) -> str:
    cmd = [ADB] + (["-s", SERIAL] if SERIAL else []) + list(args)
    done = subprocess.run(cmd, capture_output=True, encoding="utf-8", errors="replace", timeout=timeout)
    return (done.stdout or "") + (done.stderr or "")


def screenshot(name: str) -> None:
    SHOTS.mkdir(parents=True, exist_ok=True)
    data = subprocess.run([ADB] + (["-s", SERIAL] if SERIAL else []) + ["exec-out", "screencap", "-p"],
                          capture_output=True, timeout=120).stdout
    target = SHOTS / f"{name}.png"
    target.write_bytes(data)
    print(f"    ekran goruntusu: {target}  ({len(data)} bayt)")


def dump() -> list[tuple[str, tuple[int, int, int, int]]]:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    start = xml.find("<?xml")
    if start < 0:
        return []
    try:
        root = ET.fromstring(xml[start:])
    except ET.ParseError:
        return []
    nodes = []
    for node in root.iter("node"):
        text = (node.get("text") or node.get("content-desc") or "").strip()
        bounds = re.findall(r"\d+", node.get("bounds", ""))
        if text and len(bounds) == 4:
            nodes.append((text, tuple(int(v) for v in bounds)))
    return nodes


def tap(bounds) -> None:
    adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))


def find(nodes, *labels):
    for text, bounds in nodes:
        if text in labels:
            return bounds
    return None


def find_prefix(nodes, prefix):
    for text, bounds in nodes:
        if text.startswith(prefix):
            return text, bounds
    return None, None


def main() -> int:
    print("== kurulum ==")
    print("  " + adb("install", "-r", APK).strip().splitlines()[-1])
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "am", "force-stop", PKG)
    time.sleep(2)
    adb("shell", "am", "start", "-n", f"{PKG}/io.github.jqssun.airplay.MainActivity")
    time.sleep(8)
    screenshot("01-main-classic")

    print("== Ayarlar -> Gorunum ==")
    nodes = dump()
    if (b := find(nodes, "Ayarlar", "Settings")):
        tap(b); time.sleep(4)
    nodes = dump()
    if (b := find(nodes, "Görünüm", "Appearance")):
        tap(b); time.sleep(3)
    nodes = dump()
    row, b = find_prefix(nodes, "Tema")
    if b is None:
        print("  !! tema satiri bulunamadi; gorunenler:")
        for t, _ in nodes:
            print("    · " + t[:70])
        return 1
    print(f"  tema satiri: {row}")
    screenshot("02-settings-classic")

    print("== tema secici ==")
    tap(b); time.sleep(3)
    nodes = dump()
    options = [t for t, _ in nodes if t in ("Klasik", "Aydınlık", "Karanlık", "Classic", "Light", "Dark")]
    print(f"  secenekler: {options}")
    screenshot("03-theme-picker")
    return 0


if __name__ == "__main__":
    sys.exit(main())
