"""Open Settings -> About on the device, expand the privacy policy and verify the text renders.

Usage:  python scripts/read_about_screen.py
Environment: ADB (adb path, default "adb"), AIRRELAY_TV (device serial, e.g. 192.168.0.10:5555)
"""

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", "adb")
SERIAL = os.environ.get("AIRRELAY_TV", "")
PKG = "com.airrelay.receiver"


def adb(*args: str, timeout: int = 60) -> str:
    cmd = [ADB] + (["-s", SERIAL] if SERIAL else []) + list(args)
    # uiautomator writes UTF-8; decoding with the Windows locale (cp1254 here) turned
    # "Hakkında" into "HakkÄ±nda" and broke every text lookup.
    done = subprocess.run(cmd, capture_output=True, encoding="utf-8", errors="replace", timeout=timeout)
    return (done.stdout or "") + (done.stderr or "")


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
            return text, bounds
    return None, None


def main() -> int:
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "am", "start", "-n", f"{PKG}/io.github.jqssun.airplay.MainActivity")
    time.sleep(8)

    nodes = dump()
    print("  ana ekran:", " | ".join(t for t, _ in nodes)[:120])

    label, bounds = find(nodes, "Ayarlar", "Settings")
    if bounds is None:
        print("  !! Ayarlar bulunamadi")
        return 1
    tap(bounds)
    time.sleep(4)

    print("== Hakkinda bolumu aciliyor ==")
    bounds = None
    for step in range(20):
        nodes = dump()
        label, bounds = find(nodes, "Hakkında", "About")
        if bounds is not None:
            print(f"  {step} adimda bulundu")
            break
        adb("shell", "input", "keyevent", "KEYCODE_DPAD_DOWN")
        time.sleep(0.6)
    if bounds is None:
        print("  !! Hakkinda bulunamadi; gorunenler:")
        for t, _ in dump():
            print("    · " + t[:70])
        return 1
    tap(bounds)
    time.sleep(3)
    nodes = dump()

    print("== Hakkinda icerigi ==")
    for text, _ in nodes:
        print("  · " + text[:110])

    version = [t for t, _ in nodes if t.startswith(("Version", "Sürüm"))]
    print("  SURUM SATIRI:", version[0] if version else "!! yok")

    print("== Gizlilik politikasi aciliyor ==")
    label, bounds = find(nodes, "Gizlilik politikası", "Privacy policy")
    if bounds is None:
        print("  !! gizlilik dugmesi yok")
        return 1
    tap(bounds)
    time.sleep(4)
    after = dump()
    previous = [x for x, _ in nodes]
    fresh = [t for t, _ in after if t not in previous]
    print(f"  {len(fresh)} yeni satir geldi")
    for text in fresh[:4]:
        print("    · " + text[:100])
    joined = " ".join(fresh)
    checks = {
        "politika metni geldi": "PRIVACY POLICY" in joined or "does not collect" in joined,
        "hata mesaji yok": "açılamadı" not in joined and "could not be opened" not in joined,
        "mikrofon beyani var": "microphone" in joined.lower(),
        "RECORD_AUDIO var": "RECORD_AUDIO" in joined,
    }
    for name, ok in checks.items():
        print(f"  {name:<24} {'EVET' if ok else 'HAYIR'}")
    return 0 if all(checks.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
