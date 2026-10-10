#!/usr/bin/env python3
"""Run actual UI-decision/draft/NSD-wrapper logic with explicit host doubles.
Does not compile Compose, build Android packages, exercise JNI, or run devices.
"""
from pathlib import Path
import os, shutil, subprocess, tempfile
ROOT = Path(__file__).resolve().parents[2]
def main():
    compiler = shutil.which("kotlinc")
    if not compiler:
        raise SystemExit("NOT_RUN: a local Kotlin/JVM compiler is required.")
    coroutines = Path(compiler).resolve().parents[1] / "lib/kotlinx-coroutines-core-jvm.jar"
    if not coroutines.is_file():
        raise SystemExit("NOT_RUN: Kotlin distribution coroutines JAR is missing.")
    src = ROOT / "app/src/main/kotlin/io/github/jqssun/airplay"
    files = [src / x for x in ["Prefs.kt", "receiver/ReceiverUiState.kt", "receiver/InteractionPolicy.kt",
        "receiver/ReceiverController.kt", "receiver/PendingReceiverSettings.kt", "discovery/NsdServiceManager.kt"]]
    files += [ROOT / "tests/productization/host_fakes" / x for x in ["AndroidContent.kt", "AirPlayService.kt", "MediaFormat.kt"]]
    files += sorted((ROOT / "tests/refinement2/host_fakes").glob("*.kt"))
    files += [ROOT / "tests/refinement2/InteractionChecks.kt"]
    with tempfile.TemporaryDirectory(prefix="airrelay-r2-") as name:
        jar = Path(name) / "interaction-checks.jar"
        subprocess.run([compiler, *map(str,files), "-nowarn", "-cp", str(coroutines), "-include-runtime", "-d", str(jar)], check=True, cwd=ROOT)
        subprocess.run(["java", "-cp", os.pathsep.join([str(jar),str(coroutines)]), "InteractionChecksKt"], check=True, cwd=ROOT)
if __name__ == "__main__": main()
