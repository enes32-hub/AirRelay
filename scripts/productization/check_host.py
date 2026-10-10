#!/usr/bin/env python3
"""Run isolated phase 1–4 logic tests. Never invokes Gradle, Android SDK, APK or device tools."""
from pathlib import Path
import os, shutil, subprocess, tempfile
ROOT = Path(__file__).resolve().parents[2]
def run(args):
    subprocess.run([str(x) for x in args], cwd=ROOT, check=True)
def main():
    compiler = shutil.which("kotlinc")
    cpp = shutil.which("g++") or shutil.which("clang++")
    if not compiler or not cpp:
        raise SystemExit("NOT_RUN: install a local Kotlin/JVM compiler and C++ compiler; no Android tools are used.")
    lib = Path(compiler).resolve().parents[1] / "lib"
    coroutines = lib / "kotlinx-coroutines-core-jvm.jar"
    if not coroutines.exists():
        raise SystemExit("NOT_RUN: Kotlin distribution coroutines JAR is missing.")
    src = ROOT / "app/src/main/kotlin/io/github/jqssun/airplay"
    files = [src / x for x in ["Prefs.kt", "receiver/ReceiverUiState.kt", "receiver/PendingReceiverSettings.kt",
        "receiver/ReceiverController.kt", "receiver/InteractionPolicy.kt", "diagnostics/LogRedactor.kt", "diagnostics/DiagnosticStore.kt"]]
    files += list((ROOT / "tests/productization/host_fakes").glob("*.kt"))
    files += [ROOT / "tests/productization/HostChecks.kt"]
    with tempfile.TemporaryDirectory(prefix="airrelay-logic-") as name:
        tmp = Path(name)
        run([compiler, *files, "-cp", coroutines, "-include-runtime", "-d", tmp / "checks.jar"])
        run(["java", "-cp", os.pathsep.join([str(tmp / "checks.jar"), str(coroutines)]), "HostChecksKt"])
        run([cpp, "-std=c++17", "-Wall", "-Wextra", "-Werror", "-I", ROOT / "app/src/main/cpp",
             ROOT / "tests/productization/privacy_log_test.cpp", "-o", tmp / "privacy_check"])
        run([tmp / "privacy_check"])
if __name__ == "__main__":
    main()
