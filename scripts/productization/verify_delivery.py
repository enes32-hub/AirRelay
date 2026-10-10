#!/usr/bin/env python3
"""Verify this source ZIP's contents; not an Android package verifier."""
from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parents[2]
def main():
    manifest=json.loads((ROOT/'UPDATED_SOURCE_MANIFEST.json').read_text(encoding='utf-8'))
    failed=[]
    for item in manifest['files']:
        p=(ROOT/item['path']).resolve()
        if not p.is_relative_to(ROOT) or not p.is_file():
            failed.append(item['path']+': missing/unsafe');continue
        data=p.read_bytes()
        if len(data)!=item['bytes'] or hashlib.sha256(data).hexdigest()!=item['sha256']:
            failed.append(item['path']+': mismatch')
    if failed: raise SystemExit('\n'.join(failed))
    print(f"SOURCE DELIVERY VERIFIED: {len(manifest['files'])} files; no APK or media test performed.")
if __name__=='__main__':main()
