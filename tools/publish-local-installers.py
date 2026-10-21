"""Replace local deliverables only after matching-source native validation."""
from pathlib import Path
import hashlib,json,shutil

root=Path(__file__).resolve().parents[1]
project=root/'free-eloquence-tts-main'
revision=(project/'native/openevv-revision.txt').read_text().strip()
review=root/'build/upstream-review-20261012'
android=json.loads((review/'android-delivery.json').read_text(encoding='utf8'))
signature=json.loads((review/'android-signature.json').read_text(encoding='utf-8-sig'))
symbol_report=json.loads((project/'build/symbols-host/results.json').read_text(encoding='utf-8-sig'))
assert signature['verified']
android['certificate_sha256']=signature['certificate_sha256']
android['file']='openevv-principal-0.4.4-es.apk'
nvda=json.loads((project/'dist/nvda/openevv-es-0.4.5-info.json').read_text(encoding='utf8'))
assert android['native_commit']==nvda['native_commit']==revision
assert nvda['reports']['baseline']['moved']==0
sources={
 'openevv-principal-0.4.4-es.apk':project/'app/build/outputs/apk/principal/debug/app-principal-debug.apk',
 'openevv-es-0.4.5-nvda2026.3-x64.nvda-addon':project/'dist/nvda'/nvda['file'],
 'openevv-es-0.4.5-info.json':project/'dist/nvda/openevv-es-0.4.5-info.json',
}
assert hashlib.sha256(sources['openevv-principal-0.4.4-es.apk'].read_bytes()).hexdigest()==android['sha256']
assert hashlib.sha256(sources[nvda['file']].read_bytes()).hexdigest()==nvda['sha256']
out=root/'Instaladores';backup=review/'previous-installers';backup.mkdir(parents=True,exist_ok=True)
previous=[
 'openevv-principal-0.4.3-es.apk','openevv-es-0.4.4-nvda2026.3-x64.nvda-addon',
 'openevv-es-0.4.4-info.json','instaladores-caracteristicas-info.json',
 'CARACTERISTICAS-RECIENTES.md','CORRECCION-PARAMETROS.md','RECOMPILACION-MOTOR.md',
]
for name in previous:
    old=out/name;target=backup/name
    assert old.resolve().is_relative_to(root) and target.resolve().is_relative_to(root)
    if old.exists():
        assert not target.exists(),f'No se sobrescribe el respaldo {target}'
        shutil.move(str(old),str(target))
for name,path in sources.items():shutil.copy2(path,out/name)
shutil.copy2(root/'CONTROL-SIMBOLOS-Y-AUDITORIA.md',out/'SIMBOLOS-Y-FUNCIONES.md')
summary=dict(date='2026-10-12',native_commit=revision,apk=android,nvda={key:nvda[key] for key in ('file','bytes','sha256','architecture')},validation=dict(matrix_cases=nvda['reports']['baseline']['passed'],voice_stability=nvda['reports']['voice-stability']['passed'],native_modes_dictionaries=nvda['reports']['latest-features']['passed'],wav=nvda['reports']['export-mode']['passed'],panel=nvda['reports']['panel-reset']['passed'],android_symbols=symbol_report['passed']),hardware_validation=False)
(out/'instaladores-info.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf8')
notes=f'''# Instaladores actualizados — 12 de octubre de 2026

Se distribuyen únicamente Android Principal 0.4.4-es (código 13) y el
complemento NVDA 0.4.5 x64. Motor: `{revision}`.

Incluyen los cambios oficiales posteriores a f8a9e91, conservan nuestros
tres parches de estabilidad y audio, los créditos y los restablecimientos.
Android ofrece Lectura de textos → Símbolos y signos de puntuación; NVDA ofrece
OpenEVV → Lectura → Verbalización de símbolos y signos de puntuación.
Véase SIMBOLOS-Y-FUNCIONES.md para niveles, personalización y otras funciones.

Validación: {summary['validation']['matrix_cases']} casos de la matriz oficial
con reglas C, arnés oficial de banda ancha y tres formas de cierre de DLL,
regresiones de voz, modos y diccionarios y exportación WAV. El panel se probó
con sustitutos de NVDA/wx; símbolos Kotlin con JVM. Android compiló para
arm64-v8a, armeabi-v7a y x86_64; se comprobaron firma y alineación ELF/ZIP
de 16 KB. No se probaron una sesión real de NVDA ni un teléfono Android.

Los paquetes anteriores se retiraron de esta carpeta y están respaldados en
build/upstream-review-20261012/previous-installers.
Los tamaños, hashes y resultados están en instaladores-info.json.
'''
(out/'RECOMPILACION-MOTOR.md').write_text(notes,encoding='utf8')
file=root/'ACTUALIZACION-MOTOR.md'
text=file.read_text(encoding='utf8')
before='''El 12 de octubre se revisaron e integraron 16 commits posteriores en ambas
copias de las fuentes, hasta `a25d0ee`. Los instaladores siguen siendo los
compilados el 10 de octubre con `f8a9e91`; no contienen todavía estas nuevas
mejoras. Véase `REVISION-UPSTREAM-2026-10-12.md` para el alcance y la validación.'''
after='''El 12 de octubre se integraron 16 commits posteriores en ambas copias,
hasta `a25d0ee`, y se recompilaron Android Principal `0.4.4-es` (código 13)
y NVDA `0.4.5`. Incluyen el control de verbalización de símbolos y signos de
puntuación y los cambios oficiales del motor. Los instaladores anteriores
se retiraron de la carpeta de entrega y se conservaron en un respaldo.
Véanse `Instaladores/RECOMPILACION-MOTOR.md` y
`CONTROL-SIMBOLOS-Y-AUDITORIA.md`. Lo que sigue registra etapas anteriores.'''
assert before in text
file.write_text(text.replace(before,after,1),encoding='utf8')
file=root/'REVISION-UPSTREAM-2026-10-12.md'
text=file.read_text(encoding='utf8')
text=text.replace('experimental de 16 kHz. No se modificaron los controles ni la configuración\npersonal de NVDA o Android. Permanecen los créditos y los restablecimientos.',
    'experimental de 16 kHz. Además se añadió el control de símbolos de NVDA\ny una página de símbolos y puntuación en Android, con nombres según el idioma\nde la voz. Se conservaron las preferencias, créditos y restablecimientos.')
text=text.replace('## Qué está actualizado y qué falta validar','## Compilación y validación')
text=text.replace('''Esta revisión actualiza **las fuentes**, no los instaladores publicados.
La APK Principal 0.4.3-es y el complemento NVDA 0.4.4 de `Instaladores`
siguen conteniendo el motor anterior `f8a9e91`. Sus informes y hashes previos
se conservan y no sirven para validar el nuevo motor.''',f'''Se actualizaron las fuentes y los instaladores: Android Principal 0.4.4-es
y NVDA 0.4.5 contienen el motor `a25d0ee`. Los paquetes anteriores y sus
informes se conservaron en build/upstream-review-20261012/previous-installers.
La nueva matriz pasó {summary['validation']['matrix_cases']} casos sin cambios
respecto a las referencias oficiales de esta revisión. Se probaron banda
ancha, cierre de DLL, estabilidad, diccionarios y WAV. Los informes de cada
paquete corresponden a los binarios actuales; no se reutilizan los hashes
de la DLL anterior. No se probó hardware Android ni una sesión real de NVDA.''')
text=text.replace('''Antes de distribuir nuevos instaladores se debe recompilar la DLL y las
tres bibliotecas Android, repetir las regresiones de parámetros, modos,
diccionarios y WAV, y utilizar las referencias nuevas de la matriz del motor.
También deben ejecutarse las pruebas nuevas de banda ancha y de cierre de
DLL en Windows.''','''Se recompilaron la DLL y las tres bibliotecas Android y se ejecutaron las
regresiones de parámetros, modos, diccionarios y WAV, con las referencias
nuevas de la matriz. También pasaron los arneses oficiales de banda ancha
y de cierre de DLL en Windows.''')
file.write_text(text,encoding='utf8')
print(json.dumps(summary,ensure_ascii=False,indent=2))
