"""Set the app language back to Turkish and switch themes, photographing both layouts.

Handles the language dialog by walking down the list until the wanted entry is visible, because the
list is longer than the screen.

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
PKG = "com.airrelay.receiver"


def adb(*args: str, timeout: int = 120) -> str:
    cmd = [ADB] + (["-s", SERIAL] if SERIAL else []) + list(args)
    done = subprocess.run(cmd, capture_output=True, encoding="utf-8", errors="replace", timeout=timeout)
    return (done.stdout or "") + (done.stderr or "")


def nodes() -> list[tuple[str, tuple[int, int, int, int]]]:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    start = xml.find("<?xml")
    if start < 0:
        return []
    try:
        root = ET.fromstring(xml[start:])
    except ET.ParseError:
        return []
    out = []
    for node in root.iter("node"):
        text = (node.get("text") or node.get("content-desc") or "").strip()
        bounds = re.findall(r"\d+", node.get("bounds", ""))
        if text and len(bounds) == 4:
            out.append((text, tuple(int(v) for v in bounds)))
    return out


def tap(bounds) -> None:
    adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
    time.sleep(1.5)


def texts() -> list[str]:
    return [t for t, _ in nodes()]


def tap_text(*labels) -> str | None:
    for text, bounds in nodes():
        if text in labels:
            tap(bounds)
            return text
    return None


def tap_prefix(*prefixes) -> str | None:
    for text, bounds in nodes():
        if any(text.startswith(p) for p in prefixes):
            tap(bounds)
            return text
    return None


def scroll_to(label: str, steps: int = 14) -> bool:
    """Walk the focused list until `label` is on screen, then tap it.

    The language dialog opens scrolled to the saved entry, so the wanted language can be either
    above or below the visible rows: try up first, then down.
    """
    for key in ("KEYCODE_DPAD_UP", "KEYCODE_DPAD_DOWN"):
        for _ in range(steps):
            found = tap_text(label)
            if found:
                return True
            adb("shell", "input", "keyevent", key)
            time.sleep(0.5)
    return False


def screenshot(name: str) -> None:
    SHOTS.mkdir(parents=True, exist_ok=True)
    data = subprocess.run([ADB] + (["-s", SERIAL] if SERIAL else []) + ["exec-out", "screencap", "-p"],
                          capture_output=True, timeout=120).stdout
    (SHOTS / f"{name}.png").write_bytes(data)
    print(f"    {name}.png ({len(data):,} bytes)")


def open_appearance() -> None:
    adb("shell", "am", "force-stop", PKG)
    time.sleep(2)
    adb("shell", "am", "start", "-n", f"{PKG}/io.github.jqssun.airplay.MainActivity")
    time.sleep(9)
    tap_text("Ayarlar", "Настройки", "Settings")
    time.sleep(2)
    tap_text("Görünüm", "Оформление", "Appearance")
    time.sleep(2)


def main() -> int:
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")

    print("== dil Turkce'ye cevriliyor ==")
    open_appearance()
    row = tap_prefix("Arayüz dili", "Язык интерфейса")
    print("   satir:", row)
    time.sleep(2)
    print("   'Türkçe' bulundu:", scroll_to("Türkçe"))
    time.sleep(4)
    now = next((t for t in texts() if t.startswith(("Arayüz dili", "Язык интерфейса"))), "?")
    print("   simdi:", now)

    print("== tema: Beyaz (aydinlik) ==")
    print("   satir:", tap_prefix("Tema", "Тема"))
    time.sleep(2)
    print("   secildi:", tap_text("Beyaz", "Белая"))
    time.sleep(4)
    print("   tema satiri:", next((t for t in texts() if t.startswith("Tema")), "?"))
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(3)
    screenshot("09-light-same-layout")

    print("== tema: Klasik (karsilastirma) ==")
    tap_text("Ayarlar", "Настройки")
    time.sleep(2)
    tap_text("Görünüm", "Оформление")
    time.sleep(2)
    tap_prefix("Tema", "Тема")
    time.sleep(2)
    print("   secildi:", tap_text("Klasik", "Классическая"))
    time.sleep(4)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(3)
    screenshot("10-classic-reference")

    print("== tema Beyaz'a geri (kullanicinin tercihi) ==")
    tap_text("Ayarlar", "Настройки")
    time.sleep(2)
    tap_text("Görünüm", "Оформление")
    time.sleep(2)
    tap_prefix("Tema", "Тема")
    time.sleep(2)
    tap_text("Beyaz", "Белая")
    time.sleep(4)
    print("   son tema satiri:", next((t for t in texts() if t.startswith("Tema")), "?"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
