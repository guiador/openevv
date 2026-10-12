"""Valida la corrección del selector WIDE en una copia, sin editar el motor.
Ejecutar después de build-acoustic-host.ps1 y acoustic-audit.py --generate.
"""
import hashlib, json, os, subprocess
from pathlib import Path
import numpy as np
import importlib.util

ROOT=Path(__file__).resolve().parents[1]
ENGINE=ROOT/'native/openevv'
BUILD=ENGINE/'build/acoustic-host'
OUT=ROOT/'investigacion/klatt-2026-10-06'
compiler=sorted((Path.home()/'.codex/build-tools/evvdroid').glob('llvm-mingw-*-ucrt-x86_64/bin/x86_64-w64-mingw32-clang.exe'))[-1]
source=(ENGINE/'src/klatt/klatt_fx.c').read_text()
old='wide_on = allowed && rate > 11025;'
assert source.count(old)==1
fixed=BUILD/'klatt_fx_wide_checked.c'
fixed.write_text(source.replace(old,'wide_on = rate > 11025 ? allowed : 0;'))
lines=(BUILD/'link.rsp').read_text(encoding='utf-8-sig').splitlines()
common=[]
for line in lines:
    if line.startswith('test/') or line.startswith('cli/'):break
    common.append(line)
compile_rsp=BUILD/'wide-compile.rsp'
compile_rsp.write_text('\n'.join(common+['-c',str(fixed).replace('\\','/'),'-o','build/acoustic-host/klatt_fx_wide_checked.o']),encoding='utf8')
subprocess.run([str(compiler),'@'+str(compile_rsp)],cwd=ENGINE,check=True)
old_object='/objwin-c/eses-esus/klatt_fx.o'
newlines=[]
for line in lines:
    if line.startswith('test/'):line='cli/evv.c'
    if old_object in line:line='"'+str(BUILD/'klatt_fx_wide_checked.o').replace('\\','/')+'"'
    if line=='build/acoustic-host/rates.exe':line='build/acoustic-host/evv-wide-checked.exe'
    newlines.append(line)
link_rsp=BUILD/'wide-link.rsp';link_rsp.write_text('\n'.join(newlines),encoding='utf8')
subprocess.run([str(compiler),'@'+str(link_rsp)],cwd=ENGINE,check=True)
spec=importlib.util.spec_from_file_location('audit',ROOT/'tools/acoustic-audit.py');audit=importlib.util.module_from_spec(spec);spec.loader.exec_module(audit)
audit.EXE=BUILD/'evv-wide-checked.exe'
audit.run_case('eses_frase_wide_emulate_corregido_32000',audit.PHRASE,'eses',32000,'none',{'EVV_WIDE':'emulate'})
fs,a=audit.wav_read(OUT/'eses_frase_directa_32000.wav');_,b=audit.wav_read(OUT/'eses_frase_wide_emulate_corregido_32000.wav')
n=min(len(a),len(b));delta=a[:n]-b[:n]
result=dict(original_samples=len(a),corrected_emulation_samples=len(b),differing_samples=int(np.count_nonzero(delta)),max_sample_difference=float(abs(delta).max()),rms_sample_difference=float(np.sqrt(np.mean(delta*delta))),identical_pcm=bool(len(a)==len(b) and np.all(delta==0)),note='Sólo selector corregido en copia experimental. APK y fuente original sin cambios.')
(OUT/'wide-validacion.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(result,ensure_ascii=False))
