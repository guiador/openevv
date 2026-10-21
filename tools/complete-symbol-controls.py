from pathlib import Path
import json
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]/'free-eloquence-tts-main'
labels={
    'en':('Symbols and punctuation','Choose which symbols and punctuation marks are spoken.'),
    'es':('Símbolos y signos de puntuación','Elige qué símbolos y signos de puntuación se verbalizan.'),
    'de':('Symbole und Satzzeichen','Wählen Sie, welche Symbole und Satzzeichen vorgelesen werden.'),
    'fr':('Symboles et ponctuation','Choisissez les symboles et les signes de ponctuation à prononcer.'),
    'it':('Simboli e punteggiatura','Scegli quali simboli e segni di punteggiatura pronunciare.'),
}
strings={}
for language,(label,description) in labels.items():
    directory='values' if language=='en' else 'values-'+language
    file=root/'app/src/main/res'/directory/'strings.xml'
    text=file.read_text(encoding='utf8')
    import re
    for key,value in [('symbol_label',label),('symbol_mode_label',label),('symbol_description',description)]:
        text,n=re.subn(r'(<string name="'+key+r'">).*?(</string>)',lambda m:m[1]+value+m[2],text)
        assert n==1,key
    file.write_text(text,encoding='utf8')
    strings[language]={node.attrib['name']:node.text for node in ET.fromstring(text) if node.tag=='string'}
(root/'nvda-es/addon/synthDrivers/openevv_es_ui.json').write_text(json.dumps(strings,ensure_ascii=False),encoding='utf8')
file=root/'nvda-es/tests/panel-reset.py'
text=file.read_text(encoding='utf8').replace("'passed':8","'passed':10")
text=text.replace("'checks':[","'checks':['symbol selector stages changes until Apply and uses shared NVDA configuration','all four symbol levels save correctly without overwriting external changes when unchanged',",1)
file.write_text(text,encoding='utf8')
file=root/'nvda-es/addon/manifest.ini'
text=file.read_text(encoding='utf8').replace('version = 0.4.4','version = 0.4.5').replace('lectura de números y código','verbalización de símbolos y puntuación, lectura de números y código')
file.write_text(text,encoding='utf8')
file=root/'app/build.gradle.kts'
text=file.read_text(encoding='utf8').replace('versionCode = 12','versionCode = 13').replace('versionName = "0.4.3-es"','versionName = "0.4.4-es"')
file.write_text(text,encoding='utf8')
