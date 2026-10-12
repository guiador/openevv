"""Generate complete Android resources from the reviewed multilingual glossary."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
source_path = RES / 'values-es/strings.xml'
source = ET.parse(source_path if source_path.exists() else RES / 'values/strings.xml').getroot()
translations = {}
for line in (ROOT / 'tools/translations.tsv').read_text(encoding='utf-8').splitlines():
    if not line.strip() or line.startswith('#'):
        continue
    key, *values = line.split('|')
    assert len(values) == 4, key
    assert key not in translations, key
    translations[key] = values
by_text = {node.text: translations[node.attrib['name']] for node in source
           if node.tag == 'string' and node.attrib['name'] in translations}
aliases = {
    'voice_label': 'section_voice', 'about_email_subject': 'app_name',
    'section_code': 'welcome_code', 'cancel': 'update_cancel',
}
translations['app_name'] = ['OpenEVV'] * 4
translations['engine_name'] = ['OpenEVV'] * 4
missing = []
for node in source:
    if node.tag != 'string' or node.attrib.get('translatable') == 'false':
        continue
    key = node.attrib['name']
    if key not in translations:
        if key in aliases:
            translations[key] = translations[aliases[key]]
        elif node.text in by_text:
            translations[key] = by_text[node.text]
        else:
            missing.append((key, node.text))
if missing:
    for key, text in missing:
        print(f'{key}|{text}')
    raise SystemExit(f'{len(missing)} missing translations')

def android_text(text):
    return text.replace("\\'", "'").replace('\\"', '"').replace("'", "\\'").replace('"', '\\"')

for index, language in enumerate(('en', 'de', 'fr', 'it')):
    output = ET.Element('resources')
    for node in source:
        key = node.attrib.get('name')
        if node.tag == 'string' and node.attrib.get('translatable') != 'false':
            ET.SubElement(output, 'string', name=key).text = android_text(translations[key][index])
        elif node.tag == 'plurals':
            plurals = ET.SubElement(output, 'plurals', name=key)
            units = {
                'battery_seconds': [('second', 'seconds'), ('Sekunde', 'Sekunden'), ('seconde', 'secondes'), ('secondo', 'secondi')],
                'battery_minutes': [('minute', 'minutes'), ('Minute', 'Minuten'), ('minute', 'minutes'), ('minuto', 'minuti')],
            }[key][index]
            for quantity, unit in zip(('one', 'other'), units):
                ET.SubElement(plurals, 'item', quantity=quantity).text = '%1$d ' + unit
    directory = RES / ('values' if language == 'en' else 'values-' + language)
    if language == 'en':
        # English is the fallback. Spanish remains an explicit, complete locale.
        spanish = RES / 'values-es'
        spanish.mkdir(exist_ok=True)
        for node in source:
            if node.text and node.tag == 'string':
                node.text = android_text(node.text)
        ET.indent(source, space='    ')
        ET.ElementTree(source).write(spanish/'strings.xml', encoding='utf-8', xml_declaration=True)
        for node in source:
            if node.attrib.get('translatable') == 'false':
                output.append(node)
    directory.mkdir(exist_ok=True)
    ET.indent(output, space='    ')
    ET.ElementTree(output).write(directory/'strings.xml', encoding='utf-8', xml_declaration=True)

for flavor in ('principal', 'auxiliar'):
    base = ROOT / 'app/src' / flavor / 'res'
    for language in ('en', 'es', 'de', 'fr', 'it'):
        name = 'OpenEVV ' + flavor.capitalize()
        output = ET.Element('resources')
        for key in ('app_name', 'engine_name'):
            ET.SubElement(output, 'string', name=key, translatable='false').text = name
        suffix = {'en': 'active', 'es': 'activo', 'de': 'aktiv', 'fr': 'actif', 'it': 'attivo'}[language]
        ET.SubElement(output, 'string', name='keep_alive_title').text = name + ' ' + suffix
        directory = base / ('values' if language == 'en' else 'values-' + language)
        directory.mkdir(exist_ok=True)
        ET.indent(output, space='    ')
        ET.ElementTree(output).write(directory/'strings.xml', encoding='utf-8', xml_declaration=True)
print(f'Generated {len(translations)} strings in five interface languages.')
