#!/usr/bin/env python3
"""Parse Kotlin syntax with a local Kotlin compiler distribution. No Android symbol/type check."""
from pathlib import Path
import os, shutil, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[2]
def main():
    kotlinc=shutil.which('kotlinc')
    if not kotlinc or not shutil.which('javac'):
        raise SystemExit('NOT_RUN: local Kotlin compiler and JDK required; this never downloads an Android SDK.')
    lib=Path(kotlinc).resolve().parents[1]/'lib'
    with tempfile.TemporaryDirectory(prefix='airrelay-syntax-') as d:
        tmp=Path(d)
        subprocess.run(['javac','-proc:none','-cp',str(lib/'*'),'-d',str(tmp),
            str(ROOT/'tests/productization/ParseKotlin.java')],check=True)
        subprocess.run(['java','-Djava.awt.headless=true','-cp',os.pathsep.join([str(tmp),str(lib/'*')]),
            'ParseKotlin',str(ROOT/'app/src/main/kotlin')],check=True)
if __name__=='__main__':main()
