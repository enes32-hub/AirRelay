"""Export the editable project and its vendored source, with a verified manifest."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SKIP_DIRS = {'.git', '.gradle', '.kotlin', '.cxx', '.idea', '.research', '.signing', '.codex', '.agents', 'artifacts',
             '__pycache__', 'build', 'node_modules', '.venv', 'venv'}
SKIP_PREFIXES = ('tools/windows-airplay-diagnostic/portable/',
                 'tools/windows-airplay-diagnostic/build-uxplay/', 'artifacts/local/')
SKIP_SUFFIXES = {'.pyc', '.pyo', '.jks', '.keystore', '.p12', '.pfx', '.pem', '.key',
                 '.apk', '.aab', '.exe', '.dll', '.o', '.obj', '.zip', '.log', '.pid'}
ARTIFACT_SUFFIXES = {'.py', '.ps1', '.sh', '.cmd', '.bat', '.json'}


def _allowed(path: Path, relative: str, tracked_only: bool = False) -> bool:
    name = path.name
    if path.is_symlink() or not path.is_file():
        return False
    if name == 'local.properties' or name == '.env' or name.startswith('.env.'):
        return False
    if path.suffix.lower() in SKIP_SUFFIXES or relative.startswith(SKIP_PREFIXES):
        return False
    # The artifacts/ suffix allow-list exists to drop UNTRACKED build output from a filesystem
    # walk. Tracked evidence (.md/.txt) belongs to the published tree, so the archive must carry
    # exactly the same files as the repository.
    if not tracked_only and relative.startswith('artifacts/') and path.suffix.lower() not in ARTIFACT_SUFFIXES:
        return False
    if name in {'gst-registry.bin', '.ninja_log', '.ninja_deps', 'CMakeCache.txt'}:
        return False
    return True


def files():
    # In a Git checkout, export strictly the tracked public tree so gitignored local notes,
    # scratch scripts and private acceptance files never leak into a published source archive.
    if (ROOT / '.git').exists():
        done = subprocess.run(['git', 'ls-files', '-z'], cwd=ROOT, capture_output=True, check=True)
        tracked = sorted(entry.replace('\\', '/') for entry in done.stdout.decode('utf-8').split('\0') if entry)
        # 'artifacts' is skipped during a filesystem walk because it holds build output; tracked
        # files under it (the evidence records) are part of the published tree and stay.
        blocked_dirs = SKIP_DIRS - {'artifacts'}
        for relative in tracked:
            if any(part in blocked_dirs for part in relative.split('/')[:-1]):
                continue
            path = ROOT / relative
            if _allowed(path, relative, tracked_only=True):
                yield path, relative
        return
    for current, dirs, names in os.walk(ROOT, followlinks=False):
        dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS
                         and not Path(current, d).is_symlink()
                         and not (Path(current, d).relative_to(ROOT).as_posix() + '/').startswith(SKIP_PREFIXES))
        for name in sorted(names):
            path = Path(current, name)
            relative = path.relative_to(ROOT).as_posix()
            if _allowed(path, relative):
                yield path, relative


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    manifest = []
    with zipfile.ZipFile(args.output, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path, relative in files():
            data = path.read_bytes()
            archive.writestr('AirRelay/' + relative, data)
            manifest.append({'path': relative, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
        # Historical handoffs can already contain SOURCE_MANIFEST.json. Keep
        # those bytes intact; export metadata lives outside the source prefix
        # so it never creates a duplicate ZIP member or replaces old evidence.
        archive.writestr('EXPORT_MANIFEST.json', json.dumps(manifest, ensure_ascii=False, indent=2))
    with zipfile.ZipFile(args.output) as archive:
        for item in manifest:
            data = archive.read('AirRelay/' + item['path'])
            if hashlib.sha256(data).hexdigest() != item['sha256']:
                raise RuntimeError('Archive verification failed: ' + item['path'])
    print(json.dumps({'archive': str(args.output.resolve()), 'source_files': len(manifest),
                      'zip_bytes': args.output.stat().st_size,
                      'sha256': hashlib.sha256(args.output.read_bytes()).hexdigest(),
                      'verified': True}, ensure_ascii=False))


if __name__ == '__main__':
    main()
