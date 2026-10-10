"""Android locale completeness and formatter safety (no network dependencies)."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/res'
LOCALES = ['en', 'tr', 'de', 'fr', 'es', 'it', 'pt-rBR', 'ru', 'ar', 'hi', 'in', 'ja', 'ko', 'zh-rCN', 'vi', 'th']

def entries(directory):
    result = {}
    for file in sorted(directory.glob('*.xml')):
        for node in ET.parse(file).getroot():
            if node.tag not in ('string', 'string-array', 'plurals'):
                continue
            key = node.attrib['name']
            assert key not in result, f'Duplicate resource {directory.name}/{key}'
            result[key] = node
    return result

def tokens(node):
    return sorted(re.findall(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]', ''.join(node.itertext())))

def main():
    baseline = entries(ROOT / 'values')
    errors = []
    for locale in LOCALES:
        localized = entries(ROOT / ('values-' + locale))
        missing = set(baseline) - set(localized)
        if missing:
            errors.append(f'{locale}: {len(missing)} missing: {sorted(missing)[:6]}')
        for key in baseline.keys() & localized.keys():
            if tokens(baseline[key]) != tokens(localized[key]):
                errors.append(f'{locale}/{key}: format arguments changed')
            if not ''.join(localized[key].itertext()).strip():
                errors.append(f'{locale}/{key}: empty text')
            if baseline[key].tag == 'string-array' and len(baseline[key]) != len(localized[key]):
                errors.append(f'{locale}/{key}: array length changed')
        print(f'{locale}: {len(localized)}/{len(baseline)} resources')
    if errors:
        raise AssertionError('\n'.join(errors))
    print('PASS: all 16 locales complete; formatter arguments and array lengths preserved')

if __name__ == '__main__':
    main()
