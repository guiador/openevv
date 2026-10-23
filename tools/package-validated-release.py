"""Copy the validated October 2026 release outside Git, preserving older files."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--destination', type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
project = root / 'free-eloquence-tts-main'
evidence = root / 'build/auditoria-20261023'
out = args.destination.resolve()
if out.is_relative_to(root):
    # The existing Entregas/OpenEVV index is a junction to ignored Instaladores.
    # Accept that established location, but never a source or tracked directory.
    assert out.is_relative_to(root / 'Instaladores'), 'Destino dentro de las fuentes'
    relative = out.relative_to(root).as_posix()
    subprocess.run(['git', '-C', str(root), 'check-ignore', '-q', relative + '/manifest.json'], check=True)
    assert not subprocess.check_output(['git', '-C', str(root), 'ls-files', '--', relative]).strip()

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

matrix = read(root / 'build/safety-bytecode/matrix.json')
assert matrix['total'] == 1081 and matrix['moved'] == 0
assert all(row['returncode'] == 0 for row in matrix['results'])
product_matrix = read(project / 'native/openevv/build/nvda-es/host-matrix.json')
assert product_matrix['total'] == 871 and product_matrix['moved'] == 0
assert all(row['returncode'] == 0 for row in product_matrix['results'])
apk_report = read(root / 'build/upstream-review-20261012/android-delivery.json')
signature = read(root / 'build/upstream-review-20261012/android-signature.json')
source = read(root / 'build/upstream-review-20261012/source-verification.json')
assert signature['verified'] and source['source_verified'] and source['local_patches'] == 6
device = read(evidence / 'device-final.json')
assert device['service_test_passed'] and device['apk_sha256'] == apk_report['sha256']
addon_report = read(project / 'dist/nvda/openevv-es-0.4.5-info.json')
assert len(addon_report['native_patches']) == 6
apk = project / 'app/build/outputs/apk/principal/debug/app-principal-debug.apk'
addon = project / 'dist/nvda' / addon_report['file']
assert sha(apk) == apk_report['sha256'] and sha(addon) == addon_report['sha256']
product_dll = project / 'nvda-es/addon/synthDrivers/openevv_es_engine/eci.dll'
with zipfile.ZipFile(addon) as z:
    assert z.read('synthDrivers/openevv_es_engine/eci.dll') == product_dll.read_bytes()
assert sha(root / 'build/safety-bytecode/probe-enus-engb-dede-eses-esus-frfr-frca-itit-plpl-jajp.exe') == matrix['probe_sha256']

files = {}
def copy(src, relative):
    target = out / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        assert sha(target) == sha(src), f'Ya existe una entrega distinta: {target}'
    else:
        shutil.copy2(src, target)
    files[relative] = dict(sha256=sha(target), bytes=target.stat().st_size)

copy(apk, 'openevv-principal-0.4.5-es.apk')
copy(addon, addon.name)
for arch, name in [('x64', 'eci.dll'), ('x86', 'eci32.dll')]:
    copy(root / 'build/safety-bytecode' / name, f'bibliotecas/Windows-{arch}-10idiomas/eci.dll')
    copy(root / 'lib/eci.ini', f'bibliotecas/Windows-{arch}-10idiomas/eci.ini')
copy(product_dll, 'bibliotecas/Windows-x64-8idiomas/eci.dll')
copy(product_dll.with_suffix('.ini'), 'bibliotecas/Windows-x64-8idiomas/eci.ini')
for name in ('LICENSE', 'NOTICE'):
    copy(root / name, name)
copy(root / 'docs/notes/auditoria-seguridad-20261023.md', 'REVISION.md')
for src in [
    root / 'build/safety-bytecode/matrix.json',
    project / 'native/openevv/build/nvda-es/host-matrix.json',
    project / 'dist/nvda/openevv-es-0.4.5-info.json',
    root / 'build/upstream-review-20261012/android-delivery.json',
    root / 'build/upstream-review-20261012/android-signature.json',
    root / 'build/upstream-review-20261012/source-verification.json',
    evidence / 'device-final.json',
    evidence / 'device-service-final.log',
    evidence / 'device-service-after.log',
    evidence / 'android-safety/results.json',
    evidence / 'android-message-after-all.json',
    evidence / 'crashers-sample.json',
    evidence / 'native-safety.log',
    evidence / 'nvda-regressions.log',
    evidence / 'source-validation-final.log',
    project / 'build-android-current.log',
    project / 'build-android-native.log',
    root / 'build/safety-bytecode-dll32-delete.log',
    root / 'build/safety-bytecode-dll32-live.log',
    root / 'build/safety-bytecode-dll32-unload.log',
    root / 'build/safety-bytecode-dll64-delete.log',
    root / 'build/safety-bytecode-dll64-live.log',
    root / 'build/safety-bytecode-dll64-unload.log',
]:
    relative = 'evidencia/' + ('android-safety-' if src.parent.name == 'android-safety' else '') + src.name
    copy(src, relative)
for patch in sorted((project / 'native/patches').glob('*.patch')):
    copy(patch, 'parches/' + patch.name)
readme = '''# OpenEVV — entrega del 23 de octubre de 2026

Android Principal 0.4.5-es: actualizar con el APK conservando la instalación.
Paquete org.openevv.android.principal, versión interna 14, misma firma debug
anterior; incluye ARM64, ARMv7 y x86_64. Se actualizó y probó un Samsung ARM64.
ARMv7 y x86_64 se comprobaron estructuralmente; no se ejecutaron en dispositivos.

El complemento 0.4.5 está destinado a NVDA 2026.3 x64. No se instaló ni probó
en una sesión real de NVDA: falta verificar allí teclado y uso con el lector.

Las carpetas bibliotecas contienen ECI DLL, no instaladores ni complementos.
Conservar eci.ini junto a la DLL y elegir la arquitectura del proceso anfitrión.
Windows-x64-8idiomas contiene la misma DLL del complemento, con reglas C.
Windows-x64-10idiomas y Windows-x86-10idiomas usan el intérprete bytecode.
Los ocho idiomas de producto son español de España/Latinoamérica, inglés
estadounidense/británico, alemán, francés de Francia/Canadá e italiano.
Las DLL de diez idiomas añaden japonés y el módulo polaco experimental,
que conserva su procedencia italiana y no representa polaco completo.

Pruebas: 871 casos C de producto y 1081 casos bytecode x64 coinciden con los
hashes de audio y API registrados. x86 se probó con síntesis y tres formas de
cierre; no se ejecutó su matriz completa. El mensaje comunicado pasó 48
combinaciones JNI y el servicio TTS real con las preferencias del teléfono.
La actualización universal conserva exactamente la biblioteca ARM64 probada.
El barrido completo de entradas problemáticas quedó interrumpido: sólo se
declaran aprobados los 32 casos muestreados. No se garantiza ausencia de fallos.

REVISION.md explica las correcciones y los hallazgos que se difirieron.
manifest.json y SHA256SUMS.txt identifican los archivos; evidencia/ conserva
los resultados. Los parches permiten reproducir las diferencias sobre la
revisión nativa indicada. LICENSE y NOTICE mantienen los avisos originales;
no se impone una licencia global a recursos de terceros.
'''
(out / 'LEEME.md').write_text(readme, encoding='utf8')
files['LEEME.md'] = dict(sha256=sha(out / 'LEEME.md'), bytes=(out / 'LEEME.md').stat().st_size)
manifest = dict(version='0.4.5-es / 0.4.5', native_commit=source['native_commit'],
    workspace_head=subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip(),
    source_state='Local uncommitted fixes recorded in the six native patches; see REVISION.md',
    android_variant='principalDebug', android_device=device,
    validation=dict(windows_x64_bytecode_matrix=1081, windows_x64_product_c_matrix=871,
        windows_x86='Load, synthesize, delete/live/unload and PCM equality',
        android='Real ARM64 JNI and service tests; universal APK installed with same ARM64 library',
        nvda='Native synthesis and mocked NVDA API; no real NVDA session',
        crashers='32 sampled cases; full sweep interrupted'), files=files)
(out / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf8')
(out / 'SHA256SUMS.txt').write_text(''.join(f"{info['sha256']}  {name}\n" for name, info in sorted(files.items())), encoding='utf8')
print(json.dumps(dict(destination=str(out), artifacts=len(files), apk_sha256=sha(apk), addon_sha256=sha(addon)), indent=2))
