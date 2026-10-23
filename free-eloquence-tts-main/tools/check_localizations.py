"""Check language coverage, Android formatting and translated plural resources."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import ast
import importlib.util
import json
import string

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
available = set(base)
for path in (RES/'values').glob('*.xml'):
    available.update(node.attrib['name'] for node in ET.parse(path).getroot() if node.tag in ('string','plurals'))
for path in (ROOT/'app/src/main/kotlin').rglob('*.kt'):
    references.update(re.findall(r'R\.string\.(\w+)', path.read_text(encoding='utf-8')))
assert references <= available, f'Unresolved resources: {references - available}'
engine = (ROOT/'app/src/main/kotlin/com/blindtechnexus/freeeloquencetts/Eci.kt').read_text(encoding='utf-8')
families = set(re.findall(r'Language\(\w+, "([a-z]{2})"', engine))
assert families == {'en', 'es', 'de', 'fr', 'it'}, families
print(f'PASS: {len(required)} localized resources, all five TTS languages, plurals and format arguments.')

for flavor in ('principal','auxiliar'):
    folder=ROOT/'app/src'/flavor/'res'
    fallback=read(folder/'values/strings.xml')
    for language in ('es','de','fr','it'):
        localized=read(folder/f'values-{language}/strings.xml')
        assert localized.keys()==fallback.keys(), (flavor,language)
        assert all(node.text and node.text.strip() for node in localized.values())
print('PASS: both Android flavors contain all five interface locales.')

addon=ROOT/'nvda-es/addon'
spec=importlib.util.spec_from_file_location('openevv_ui',addon/'synthDrivers/_openevv_es_ui.py')
ui=importlib.util.module_from_spec(spec);spec.loader.exec_module(ui)
languages=('en','es','de','fr','it')
for key,values in ui.EXTRA.items():
    assert len(values)==5 and all(isinstance(value,str) and value.strip() for value in values),key
    for value in values:
        assert sorted(PATTERN.findall(value))==sorted(PATTERN.findall(values[0])),key
        fields=lambda text:{(name,format_spec) for _,name,format_spec,_ in string.Formatter().parse(text) if name is not None}
        assert fields(value)==fields(values[0]),key
        assert '\ufffd' not in value,key
keys=set()
for path in addon.rglob('*.py'):
    for node in ast.walk(ast.parse(path.read_text(encoding='utf8'))):
        if isinstance(node,ast.Call) and node.args and isinstance(node.args[0],ast.Constant) and isinstance(node.args[0].value,str):
            name=node.func.id if isinstance(node.func,ast.Name) else node.func.attr if isinstance(node.func,ast.Attribute) else ''
            if name in ('t','uiText'):keys.add(node.args[0].value)
for language in languages:
    assert {key for key in required if base[key].tag=='string'} <= ui.STRINGS[language].keys(),language
    for key in keys:
        assert key in ui.EXTRA or key in ui.STRINGS[language],(language,key)
        assert ui.translator(language)(key)!=key,(language,key)
    manifest=(addon/'locale'/language/'manifest.ini').read_text(encoding='utf8')
    assert ui.translator(language)('addonSummary') in manifest,language
    assert ui.translator(language)('addonDescription') in manifest,language
    doc=(addon/'doc'/language/'readme.html').read_text(encoding='utf8')
    assert 'lang="'+language+'"' in doc and ui.translator(language)('response_title') in doc,language
    if language!='es':
        assert 'Con colaboración de Guiador' not in doc,language
        assert ui.translator(language)('response_explanation')!=ui.translator('es')('response_explanation'),language
assert not any("\\'" in value or '\\"' in value for values in ui.STRINGS.values() for value in values.values()),'Android quote escapes leaked into NVDA'
print(f'PASS: NVDA {len(keys)} referenced translation keys, {len(ui.EXTRA)} extra rows, five manifests and installed documents; placeholders preserved.')
