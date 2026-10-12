"""Run native synthesis in isolated processes, containing faults and hangs."""
import ctypes as c
import json, subprocess, sys, zipfile, time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'investigacion/crash-2026-10-06'
DLL = OUT / 'eci.dll'
TEXT = (OUT / 'texto.txt').read_text(encoding='utf-8')

def child(lang, rate, voice, speed, text):
    d = c.CDLL(str(DLL))
    d.eciNewEx.argtypes = [c.c_int]; d.eciNewEx.restype = c.c_void_p
    for name, args in {
        'eciDelete':[c.c_void_p], 'eciSetParam':[c.c_void_p,c.c_int,c.c_int],
        'eciCopyVoice':[c.c_void_p,c.c_int,c.c_int],
        'eciSetVoiceParam':[c.c_void_p,c.c_int,c.c_int,c.c_int],
        'eciAddText':[c.c_void_p,c.c_char_p], 'eciSynthesize':[c.c_void_p],
        'eciSynchronize':[c.c_void_p],
        'eciSetOutputBuffer':[c.c_void_p,c.c_int,c.POINTER(c.c_short)],
    }.items(): getattr(d,name).argtypes=args
    CB = c.CFUNCTYPE(c.c_int,c.c_void_p,c.c_int,c.c_int,c.c_void_p)
    d.eciRegisterCallback.argtypes=[c.c_void_p,CB,c.c_void_p]
    total=0
    @CB
    def callback(h,msg,n,unused):
        nonlocal total
        if msg == 0: total += n
        return 1
    h=d.eciNewEx(lang)
    if not h: raise RuntimeError('Cannot create engine')
    buf=(c.c_short*2048)()
    d.eciRegisterCallback(h,callback,None)
    assert d.eciSetOutputBuffer(h,2048,buf)
    d.eciSetParam(h,5,rate); d.eciSetParam(h,1,1)
    assert d.eciCopyVoice(h,voice,0)
    if speed >= 0: d.eciSetVoiceParam(h,0,6,speed)
    assert d.eciAddText(h,text.encode('cp1252'))
    assert d.eciSynthesize(h)
    d.eciSynchronize(h)
    d.eciDelete(h)
    print(json.dumps({'samples':total}),flush=True)

def main():
    if '--child' in sys.argv:
        child(*map(int,sys.argv[2:6]),sys.argv[6]); return
    OUT.mkdir(exist_ok=True,parents=True)
    addon=ROOT.parent/'Instaladores/openevv-es-0.3.0-nvda2026.3-x64.nvda-addon'
    with zipfile.ZipFile(addon) as z:
        name=next(n for n in z.namelist() if n.endswith('/eci.dll'))
        DLL.write_bytes(z.read(name))
    results=[]
    def run(job):
        lang,rate,voice,speed,label,text=job
        start=time.monotonic()
        args=[sys.executable,__file__,'--child',str(lang),str(rate),str(voice),str(speed),text]
        try:
            p=subprocess.run(args,capture_output=True,timeout=15)
            r=dict(language=hex(lang),rate=rate,voice=voice,speed=speed,case=label,exit=p.returncode,stdout=p.stdout.decode(errors='replace'),stderr=p.stderr.decode(errors='replace'))
        except subprocess.TimeoutExpired:
            r=dict(language=hex(lang),rate=rate,voice=voice,speed=speed,case=label,exit=None,error='timeout')
        r['seconds']=round(time.monotonic()-start,3)
        return r
    if '--matrix' in sys.argv:
        jobs=[(lang,rate,voice,speed,'full',TEXT) for lang in [0x20000,0x20001]
              for rate in [0,1,3,2,24000,4,5,6] for voice in range(1,9) for speed in [-1,250]]
        with ThreadPoolExecutor(max_workers=4) as pool:
            for r in pool.map(run,jobs):
                results.append(r)
                (OUT/'matriz.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
                if r['exit'] != 0: print(r,flush=True)
                if len(results)%32==0: print(f'{len(results)}/{len(jobs)}',flush=True)
        return
    cases=[('full',TEXT),('spaces', ' '.join(TEXT.split()))]
    cases += [(f'line-{i+1}',t) for i,t in enumerate(TEXT.splitlines())]
    if '--pieces' in sys.argv:
        cases=[(p.stem,p.read_text(encoding='utf-8')) for p in sorted(OUT.glob('pieza-*.txt'))]
    for lang in [0x20000,0x20001]:
        for label,text in cases:
            r=run((lang,1,1,-1,label,text))
            results.append(r)
            destination='piezas-resultados.json' if '--pieces' in sys.argv else 'resultados.json'
            (OUT/destination).write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
            print(r,flush=True)
if __name__=='__main__': main()
