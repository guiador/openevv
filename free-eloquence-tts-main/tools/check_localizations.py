"""Check language coverage, Android formatting and translated plural resources."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
PATTERN = re.compile(r'%(?:\d+\$)?[dsf]|%%')

def read(path):
    elements = list(ET.parse(path).getroot())
    names = [node.attrib['name'] for node in elements]
    assert len(names) == len(set(names)), f'Duplicate resource: {path}'
    return {node.attrib['name']: node for node in elements}

base = read(RES/'values/strings.xml')
required = {name for name, node in base.items() if node.attrib.get('translatable') != 'false'}
for language in ('es', 'de', 'fr', 'it'):
    localized = read(RES/f'values-{language}/strings.xml')
    assert required <= localized.keys(), f'{language}: missing {required - localized.keys()}'
    for name in required:
        original, translated = base[name], localized[name]
        assert original.tag == translated.tag, f'{language}/{name}: type changed'
        if original.tag == 'plurals':
            original_items = {n.attrib['quantity']: n.text for n in original}
            translated_items = {n.attrib['quantity']: n.text for n in translated}
            assert original_items.keys() <= translated_items.keys(), f'{language}/{name}: missing quantities'
            pairs = [(original_items[q], translated_items[q]) for q in original_items]
        else:
            pairs = [(original.text, translated.text)]
        for a, b in pairs:
            assert b and b.strip(), f'{language}/{name}: empty'
            assert sorted(PATTERN.findall(a)) == sorted(PATTERN.findall(b)), f'{language}/{name}: format mismatch'
            assert 'Ã' not in b and '\ufffd' not in b, f'{language}/{name}: encoding error'

references = set()
for path in (ROOT/'app/src/main/kotlin').rglob('*.kt'):
    references.update(re.findall(r'R\.string\.(\w+)', path.read_text(encoding='utf-8')))
assert references <= base.keys(), f'Unresolved resources: {references - base.keys()}'
engine = (ROOT/'app/src/main/kotlin/com/blindtechnexus/freeeloquencetts/Eci.kt').read_text(encoding='utf-8')
families = set(re.findall(r'Language\(\w+, "([a-z]{2})"', engine))
assert families == {'en', 'es', 'de', 'fr', 'it'}, families
print(f'PASS: {len(required)} localized resources, all five TTS languages, plurals and format arguments.')
