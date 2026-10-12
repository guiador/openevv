"""Empaquetado y comprobaciones estructurales; no ejecuta NVDA."""
import ast, hashlib, importlib.util, json, re, shutil, struct, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
PROJECT=ROOT.parent
ADDON=ROOT/'addon'
spec=importlib.util.spec_from_file_location('upstream_build',PROJECT/'native/openevv/nvda/build.py')
builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)
dll=ADDON/'synthDrivers/openevv_es_engine/eci.dll'
blob=dll.read_bytes();pe=struct.unpack_from('<I',blob,0x3c)[0]
assert struct.unpack_from('<H',blob,pe+4)[0]==0x8664, 'Se requiere DLL x64.'
engine='\n'.join(path.read_text(encoding='utf8') for path in (ADDON/'synthDrivers').glob('*.py'))
required=set(re.findall(r'\bdll\.(eci\w+|evv\w+)',engine))
missing=required-builder.exported_names(str(dll))
assert not missing, f'Exportaciones ausentes: {missing}'
for path in ADDON.rglob('*.py'):ast.parse(path.read_text(encoding='utf8'),filename=str(path))
for language in ('en','es','de','fr','it'):
    assert (ADDON/'doc'/language/'readme.html').is_file()
for name in ['LICENSE','NOTICE']:shutil.copyfile(PROJECT/'native/openevv'/name,ADDON/name)
shutil.copyfile(PROJECT/'native/openevv/lib/eci.ini',dll.with_suffix('.ini'))
out=PROJECT/'dist/nvda';out.mkdir(parents=True,exist_ok=True)
version=re.search(r'^version\s*=\s*(\S+)',(ADDON/'manifest.ini').read_text(encoding='utf8'),re.M).group(1)
target=out/f'openevv-es-{version}-nvda2026.3-x64.nvda-addon'
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
    for path in sorted(ADDON.rglob('*')):
        if path.is_file() and '__pycache__' not in path.parts and path.suffix not in ('.pyc','.pyo'):z.write(path,path.relative_to(ADDON).as_posix())
with zipfile.ZipFile(target) as z:
    assert z.testzip() is None
    assert z.read('manifest.ini')==(ADDON/'manifest.ini').read_bytes()
    assert z.read('synthDrivers/openevv_es_engine/eci.dll')==blob
reports={}
for name in ('native','pause','baseline','export-memory','nvda-api','voice-stability','panel-reset','latest-features','export-mode'):
    report=ROOT/'tests'/f'{name}-results.json'
    if report.is_file():reports[name]=json.loads(report.read_text(encoding='utf8'))
native_commit=(PROJECT/'native/openevv-revision.txt').read_text(encoding='utf8').strip()
for name in ('voice-stability','latest-features','export-mode'):
    assert reports[name]['dll_sha256']==hashlib.sha256(blob).hexdigest(), f'Informe {name} no corresponde a la DLL empaquetada'
info=dict(file=target.name,sha256=hashlib.sha256(target.read_bytes()).hexdigest(),bytes=target.stat().st_size,architecture='x86_64',target_nvda='2026.3',native_commit=native_commit,native_patches=[path.stem for path in sorted((PROJECT/'native/patches').glob('*.patch'))],languages=['eses','esus','enus','engb','dede','frfr','frca','itit'],exported_driver_symbols_checked=len(required),validation='Este empaquetado comprueba sintaxis, ZIP, PE x64 y exportaciones. Los informes adicionales solo se incluyen si existen. No valida por sí solo ejecución dentro de NVDA.',reports=reports)
previous=PROJECT.parent/'Instaladores/openevv-es-0.3.0-nvda2026.3-x64.nvda-addon'
if previous.is_file():
    with zipfile.ZipFile(previous) as old, zipfile.ZipFile(target) as new:
        before={name:hashlib.sha256(old.read(name)).hexdigest() for name in old.namelist() if not name.endswith('/')}
        after={name:hashlib.sha256(new.read(name)).hexdigest() for name in new.namelist() if not name.endswith('/')}
    info['comparison']=dict(previous_file=previous.name,previous_sha256=hashlib.sha256(previous.read_bytes()).hexdigest(),added=sorted(set(after)-set(before)),removed=sorted(set(before)-set(after)),changed=sorted(name for name in set(before)&set(after) if before[name]!=after[name]))
(out/f'openevv-es-{version}-info.json').write_text(json.dumps(info,ensure_ascii=False,indent=2),encoding='utf8')
print(target)
print(json.dumps({key:value for key,value in info.items() if key not in ('reports','comparison')},ensure_ascii=False))
