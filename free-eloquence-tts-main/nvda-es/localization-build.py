"""Localize generated driver labels while keeping settings and voice IDs stable."""

def apply(name, text):
    if name == 'openevv_es.py':
        labels = {
            'OpenEVV Español — sonido ampliado': 'driverDescription',
            'Tipo de voz': 'gender_label',
            'Tamaño y resonancia': 'set_head_size',
            'Aspereza': 'set_roughness',
            'Aire en la voz': 'set_breathiness',
            'Expandir abreviaturas': 'abbreviations_label',
            'Permitir anotaciones del motor': 'allowVoiceTags',
            'Frecuencia de muestreo': 'sample_rate_label',
            'Mejora de audio': 'audio_optimization_label',
            'Remuestreo': 'eq_text_21',
            'Perfil del ecualizador': 'eq_text_10',
            'Ganancia': 'eq_text_11',
        }
        for label, key in labels.items():
            old = '"' + label + '"'
            assert text.count(old) == 1, (name, label)
            text = text.replace(old, '_openevv.uiText(' + repr(key) + ')')
        for hz in (120, 350, 1000, 2500, 3500):
            old = '"Banda %d Hz"' % hz
            assert text.count(old) == 1, old
            text = text.replace(old, "_openevv.uiText('eqBand') % " + str(hz))
    elif name == '_openevv_es.py':
        old = 'return LANGUAGE_NAMES.get(language, "0x%x" % language)'
        assert text.count(old) == 1
        text = text.replace(old, '''keys = {0x10000: 'language_enus', 0x10001: 'language_engb',
        0x20000: 'language_eses', 0x20001: 'language_esus',
        0x30000: 'language_frfr', 0x30001: 'language_frca',
        0x40000: 'language_dede', 0x50000: 'language_itit'}
	return uiText(keys[language]) if language in keys else LANGUAGE_NAMES.get(language, "0x%x" % language)''')
        old = 'name or ("Voice %d" % number)'
        assert old in text
        text = text.replace(old, "name or (uiText('voiceFallback') % number)")
        text += '''

def uiText(key):
    from ._openevv_es_ui import uiText as translate
    return translate(key)
'''
    return text

def write_manifests(root):
    import importlib.util
    import re
    spec=importlib.util.spec_from_file_location('openevv_ui',root/'addon/synthDrivers/_openevv_es_ui.py')
    ui=importlib.util.module_from_spec(spec);spec.loader.exec_module(ui)
    path=root/'addon/manifest.ini'
    text=path.read_text(encoding='utf8')
    t=ui.translator('en')
    text=re.sub(r'^summary\s*=.*$', 'summary = "'+t('addonSummary')+'"',text,flags=re.M)
    text=re.sub(r'^description\s*=\s*""".*?"""', 'description = """'+t('addonDescription')+'"""',text,flags=re.M|re.S)
    path.write_text(text,encoding='utf8')
    for language in ('en','es','de','fr','it'):
        t=ui.translator(language)
        target=root/'addon/locale'/language/'manifest.ini';target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text('summary = "'+t('addonSummary')+'"\ndescription = """'+t('addonDescription')+'"""\n',encoding='utf8')
