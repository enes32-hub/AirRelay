import re, pathlib

root = pathlib.Path('.')
files = [
    p for p in root.rglob('*')
    if p.suffix in {'.md', '.json', '.py', '.ps1', '.kts', '.toml', '.sh', '.cmd'}
    and '.tmpcheck' not in str(p) and '__pycache__' not in str(p)
]
pat = re.compile(r'(?<![\w/])(scripts|tools|tests)/[A-Za-z0-9_\-\./]+')
refs = {}
for f in files:
    try:
        t = f.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        continue
    for m in pat.finditer(t):
        r = m.group(0).rstrip('.,')
        r = r.rstrip(')')
        refs.setdefault(r, set()).add(str(f))
missing = {r: sorted(v) for r, v in refs.items() if not pathlib.Path(r).exists()}
for r, v in sorted(missing.items()):
    print('MISSING', r, '<-', ', '.join(v[:3]))
print('total refs', len(refs), 'missing', len(missing))
