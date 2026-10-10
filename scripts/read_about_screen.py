"""Read the About section of the installed receiver from the device UI.

Dumps the accessibility tree, navigates to Settings, walks down the list until the version
line appears, then reports every visible string plus the state of the licence toggle.
"""

import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

import os

ADB = os.environ.get("ADB", "adb")
SERIAL = os.environ.get("AIRRELAY_TV", "<tv-serial-or-ip:5555>")
PKG = "com.airrelay.receiver"


def adb(*args: str, timeout: int = 60) -> str:
    done = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, text=True,
                          errors="replace", timeout=timeout)
    return (done.stdout or "") + (done.stderr or "")


def dump() -> list[tuple[str, str, tuple[int, int, int, int]]]:
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
        text = node.get("text") or node.get("content-desc") or ""
        bounds = re.findall(r"\d+", node.get("bounds", ""))
        if text.strip() and len(bounds) == 4:
            nodes.append((text.strip(), node.get("class", ""),
                          tuple(int(v) for v in bounds)))
    return nodes


def tap(bounds) -> None:
    x = (bounds[0] + bounds[2]) // 2
    y = (bounds[1] + bounds[3]) // 2
    adb("shell", "input", "tap", str(x), str(y))


def texts(nodes) -> list[str]:
    return [n[0] for n in nodes]


def main() -> int:
    print("== uygulama aciliyor ==")
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "am", "start", "-n", f"{PKG}/io.github.jqssun.airplay.MainActivity")
    time.sleep(8)
    nodes = dump()
    print("  ana ekran:", " | ".join(texts(nodes))[:160])

    settings = next((n for n in nodes if n[0] in ("Ayarlar", "Settings")), None)
    if settings is None:
        print("  !! Ayarlar ogesi bulunamadi")
        return 1
    tap(settings[2])
    time.sleep(4)
    nodes = dump()
    print(f"  ayarlar ekrani ({len(nodes)} oge):", " | ".join(texts(nodes))[:200])

    print("== Hakkinda basligi araniyor (liste odakla kaydirilir) ==")
    about = None
    for step in range(25):
        about = next((n for n in nodes if n[0] in ("Hakkında", "About")), None)
        if about is not None:
            print(f"  {step} adimda bulundu")
            break
        adb("shell", "input", "keyevent", "KEYCODE_DPAD_DOWN")
        time.sleep(0.6)
        nodes = dump()
    if about is None:
        print("  !! bulunamadi; son ekranda gorunenler:")
        for text in texts(nodes):
            print("    · " + text[:80])
        return 1

    tap(about[2])
    time.sleep(1)
    adb("shell", "input", "keyevent", "KEYCODE_DPAD_CENTER")
    time.sleep(3)
    nodes = dump()

    print("== EKRANDA GORUNENLER ==")
    for text in texts(nodes):
        print(f"  · {text}")

    version = [t for t in texts(nodes) if t.startswith(("Version", "Sürüm"))]
    print("  SURUM SATIRI:", version[0] if version else "!! BULUNAMADI")

    toggle = next((n for n in nodes if n[0] in ("Bileşen lisansları", "Component licences")), None)
    if toggle:
        print("== lisans listesi aciliyor ==")
        tap(toggle[2])
        time.sleep(5)
        after = dump()
        fresh = [t for t in texts(after) if t not in texts(nodes)]
        print(f"  {len(fresh)} yeni satir geldi; ilk 6:")
        for text in fresh[:6]:
            print("    · " + text[:120])
        missing = [t for t in fresh if "could not be opened" in t or "açılamadı" in t]
        print("  HATA MESAJI:" , "VAR -> " + missing[0] if missing else "yok (lisanslar yuklendi)")
    else:
        print("  !! lisans dugmesi bulunamadi")
    return 0


if __name__ == "__main__":
    sys.exit(main())
