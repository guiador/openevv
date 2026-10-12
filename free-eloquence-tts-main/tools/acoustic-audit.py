"""Experimento reproducible; no modifica las reglas ni el APK.

Python con numpy, scipy y matplotlib. Ejecutar desde cualquier carpeta:
python tools/acoustic-audit.py --generate --analyze
"""
import argparse, csv, hashlib, json, math, os, re, subprocess, wave
from pathlib import Path
import numpy as np
from scipy import signal
from scipy.linalg import solve_toeplitz

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / 'native/openevv'
OUT = ROOT / 'investigacion/klatt-2026-10-06'
EXE = ENGINE / 'build/acoustic-host/evv.exe'
LANGS = {'eses': '0x20000', 'esus': '0x20001'}
PHRASE = 'Susana sale de su casa. Cecilia caza cerca del cerro. La lluvia moja la calle. Mi voz pronuncia a, e, i, o, u.'

def run_case(tag, text, lang, rate, method='sinc', extra=None, annotation=False):
    base = OUT / tag
    textfile = base.with_suffix('.txt')
    textfile.write_bytes(text.encode('cp1252'))
    tap = base.with_suffix('.tsv')
    if tap.exists(): tap.unlink()
    env = {k:v for k,v in os.environ.items() if not k.startswith('EVV_')}
    env.update(EVV_UPSAMPLE=method, EVV_KLATT_TAP=str(tap))
    if extra: env.update(extra)
    args = [str(EXE), '-v', '1', '-L', LANGS[lang], '-R', str(rate), '-f', str(textfile), '-o', str(base.with_suffix('.wav'))]
    if annotation: args += ['-A']
    meta = dict(tag=tag, text=text, language=lang, output_rate_hz=rate, method=method, extra=extra or {}, annotation=annotation, command=args)
    try:
        result = subprocess.run(args, env=env, capture_output=True, timeout=25)
        meta.update(returncode=result.returncode, stderr=result.stderr.decode('utf8', errors='replace'))
    except subprocess.TimeoutExpired:
        meta.update(returncode=None, stderr='TIMEOUT 25 segundos')
    base.with_suffix('.run.json').write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding='utf8')
    print(tag, meta['returncode'], flush=True)

def generate():
    OUT.mkdir(parents=True, exist_ok=True)
    for lang in LANGS:
        for rate in [8000,11025,22050,24000,32000,44100]:
            run_case(f'{lang}_frase_directa_{rate}', PHRASE, lang, rate, 'none')
        for rate in [24000,32000]:
            run_case(f'{lang}_frase_sinc_{rate}', PHRASE, lang, rate)
        for text in ['asa','aza','casa','caza','acha','aja','afa','ata']:
            run_case(f'{lang}_{text}_11025', text + '.', lang, 11025)
        for vowel in 'aeiou':
            run_case(f'{lang}_vocal_{vowel}_11025', '`[.1'+vowel+']', lang, 11025, annotation=True)
    for rate in [24000,32000]:
        for vowel in 'aeiou':
            run_case(f'eses_vocal_{vowel}_directa_{rate}', '`[.1'+vowel+']', 'eses', rate, 'none', annotation=True)
    for mode in ['on','emulate']:
        run_case(f'eses_frase_wide_{mode}_32000', PHRASE, 'eses', 32000, 'none', {'EVV_WIDE':mode})
    run_case('eses_frase_ruido_flat_32000', PHRASE, 'eses', 32000, 'none', {'EVV_NOISE':'flat'})

def wav_read(path):
    with wave.open(str(path),'rb') as w:
        assert w.getnchannels()==1 and w.getsampwidth()==2
        return w.getframerate(), np.frombuffer(w.readframes(w.getnframes()),dtype='<i2').astype(float)

def psd(x, rate):
    return signal.welch(x, fs=rate, nperseg=min(len(x), int(rate*.046)), scaling='density')

def summarize(path):
    rate,x = wav_read(path)
    f,p = psd(x,rate)
    db = lambda v: float(10*np.log10(max(v,1e-30)))
    band = lambda lo,hi: float(np.sum(p[(f>=lo)&(f<hi)]))
    total = float(p.sum())
    data = dict(file=path.name, rate_hz=rate, duration_s=len(x)/rate, samples=len(x), peak=float(abs(x).max()), rms=float(np.sqrt(np.mean(x*x))), dc=float(x.mean()), endpoint_samples=int(np.sum((x==32767)|(x==-32768))), high_band_db_relative=db(band(5512.5,rate/2)/max(total,1e-30)), sha256=hashlib.sha256(path.read_bytes()).hexdigest())
    tap = path.with_suffix('.tsv')
    if tap.exists() and tap.stat().st_size:
        rows = list(csv.DictReader(tap.open(encoding='ascii'), delimiter='\t'))
        params = {k:np.array([int(r[k]) for r in rows]) for k in rows[0]}
        data['frames'] = len(rows)
        data['parameters'] = {k:dict(min=int(v.min()),max=int(v.max()),median=float(np.median(v))) for k,v in params.items()}
        data['changing_parameters'] = [k for k,v in params.items() if len(np.unique(v))>1]
        # Parameter timestamps follow integer per-frame duration rounding.
        # Native 11,025 Hz only: no resampling latency to account for.
        if rate==11025 and 'vocal' not in path.stem and 'frase' not in path.stem:
            starts=np.r_[0,np.cumsum((params['step']*rate)//1000)]
            selected = np.zeros(len(x),dtype=bool)
            active=(params['af']>0)&(params['av']==0)
            edges=np.diff(np.r_[False,active,False].astype(int))
            for first,last in zip(np.where(edges==1)[0], np.where(edges==-1)[0]):
                # Ignore 10 ms at either edge of a contiguous frication run.
                a,b=int(starts[first]+rate*.010),int(starts[last]-rate*.010)
                if b>a: selected[a:min(b,len(x))]=True
            noise=x[selected]
            if len(noise)>rate*.02:
                nf,npow=psd(noise,rate)
                mask=(nf>=500)&(nf<5000)
                weights=npow[mask]; freqs=nf[mask]
                data['frication']=dict(selected_s=len(noise)/rate, centroid_500_5000_hz=float(np.sum(freqs*weights)/np.sum(weights)), peak_500_5000_hz=float(freqs[np.argmax(weights)]), rms=float(np.sqrt(np.mean(noise*noise))), af_frames=int(np.sum((params['af']>0)&(params['av']==0))))
                data['frication_targets']={k:float(np.median(params[k][active])) for k in ['f1','f2','f3','f4','f5','b1','b2','b3','b4','b5','af','a1f','a2f','a3f','a4f','a5f','ab']}
        if 'vocal' in path.stem:
            chosen=(params['av']>0)&(params['af']==0)
            data['vowel_targets']={k:float(np.median(params[k][chosen])) for k in ['f0','f1','f2','f3','b1','b2','b3'] if chosen.any()}
            data['vowel_targets']['f0_hz']=data['vowel_targets'].pop('f0')/10
            # Independent estimate from PCM: LPC order 12, 40 ms central frames,
            # preemphasis 0.97; nearest pole in +/-25% of commanded F1..F3.
            # This uses targets to match poles, so it is NOT a blind estimator.
            starts=np.r_[0,np.cumsum((params['step']*rate)//1000)]
            active_indices=np.where(chosen)[0]
            estimates=[]
            for i in active_indices[len(active_indices)//4:3*len(active_indices)//4:4]:
                at=int(starts[i]);win=int(rate*.04)
                if at+win>len(x):continue
                chunk=x[at:at+win]
                # Same analysis bandwidth/order at every synthesis rate.
                # Anti-aliased downsampling is for measurement only.
                analysis_rate=11025
                if rate!=analysis_rate:
                    divisor=math.gcd(rate,analysis_rate)
                    chunk=signal.resample_poly(chunk,analysis_rate//divisor,rate//divisor)
                chunk=signal.lfilter([1,-.97],[1],chunk);chunk*=np.hanning(len(chunk))
                size=len(chunk)
                ac=signal.correlate(chunk,chunk,mode='full',method='fft')[size-1:size+12]
                if ac[0]<1:continue
                coeff=np.r_[1,solve_toeplitz((ac[:-1],ac[:-1]),-ac[1:])]
                roots=np.roots(coeff);roots=roots[np.imag(roots)>0]
                frequencies=np.angle(roots)*analysis_rate/(2*np.pi)
                bandwidths=-analysis_rate*np.log(np.abs(roots))/np.pi
                frequencies=frequencies[(bandwidths>0)&(bandwidths<600)&(frequencies>100)&(frequencies<5000)]
                sample=[]
                for k in ['f1','f2','f3']:
                    target=data['vowel_targets'][k];near=frequencies[abs(frequencies-target)<max(120,target*.25)]
                    sample.append(float(near[np.argmin(abs(near-target))]) if len(near) else np.nan)
                estimates.append(sample)
            if estimates:
                data['vowel_lpc_estimates_hz']={k:(float(np.nanmedian(np.array(estimates)[:,j])) if np.any(np.isfinite(np.array(estimates)[:,j])) else None) for j,k in enumerate(['f1','f2','f3'])}
    return data

def table_audit():
    source=(ENGINE/'src/klatt/klatt_tables.c').read_text()
    q15=lambda v:np.clip(np.where(v>=0,np.floor(v*32768+.5),-np.floor(-v*32768+.5)),-32768,32767).astype(np.int64)
    result={}
    for rate in [8000,11025,22050,24000,32000,44100]:
        ex=q15(np.exp(-np.pi*np.arange(10,4001)/rate))
        co=q15(np.cos(2*np.pi*np.arange(10,5001)/rate))
        record={}
        if rate in [8000,11025]:
            for kind,new in [('EX',ex),('CO',co)]:
                name='klatt_'+kind+('8' if rate==8000 else '11')
                match=re.search(r'\b'+name+r'\[\d+\]\s*=\s*\{(.*?)\};',source,re.S)
                old=np.array([int(n) for n in re.findall(r'-?\d+',match[1])])[:len(new)]
                record[kind+'_differences']=[dict(hz=int(i+10),original=int(old[i]),formula=int(new[i])) for i in np.where(old!=new)[0]]
                if kind=='EX':ex=old
                else:co=old
        # Exhaust all legal steady-state frequency/bandwidth pairs in chunks.
        minmargin=100000; unstable=0; maxradius=0.0; examples=[]
        for start in range(0,len(ex),128):
            e=ex[start:start+128,None]
            c=(-e*e//32768)&~3
            b=(e*co[None,:]//32768)&~1
            # Recurrence: y=A*x+B*y1+C*y2, with A=4sa/Q15.
            # Integer Jury margins: 1-B-C, 1+B-C, 1+C.
            margins=np.minimum(32768-2*b-c, np.minimum(32768+2*b-c,32768+c))
            minmargin=min(minmargin,int(margins.min()))
            unstable+=int(np.sum(margins<=0))
            maxradius=max(maxradius,float(np.sqrt(-c/32768).max()))
            for bi,fi in np.argwhere(margins<=0)[:max(0,8-len(examples))]:
                examples.append(dict(f_hz=int(fi+10),b_hz=int(start+bi+10)))
        record.update(pairs_checked=3991*4991,non_strictly_stable_pairs=unstable,min_jury_margin_q15=minmargin,max_complex_pole_radius=maxradius)
        record['non_strict_examples']=examples
        # A typical vowel-like resonator; pole angle is not the spectral peak.
        e=int(ex[60-10]); c=int((-e*e//32768)&~3); b=int((e*int(co[500-10])//32768)&~1)
        radius=np.sqrt(-c/32768)
        record['example_F500_B60']=dict(sa=8192-(b>>1)-(c>>2),sb=b,sc=c,pole_radius=float(radius),pole_angle_hz=float(np.arccos(np.clip((2*b/32768)/(2*radius),-1,1))*rate/(2*np.pi)),effective_bandwidth_hz=float(-rate*np.log(radius)/np.pi))
        result[str(rate)]=record
    return result

def analyze():
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    records=[summarize(p) for p in sorted(OUT.glob('*.wav')) if p.stat().st_size>44 and not p.stem.endswith('_escucha')]
    tables=table_audit()
    payload=dict(engine_commit='520585138206f1692c619ae2b45856cbbe4255b3',platform='Windows x86_64 LLVM-MinGW; same native source as APK 0.2, not Android runtime',voice=1,encoding='cp1252',phrase=PHRASE,coefficient_audit=tables,measurements=records)
    (OUT/'resultados.json').write_text(json.dumps(payload,ensure_ascii=False,indent=2,allow_nan=False),encoding='utf8')
    fields=['file','rate_hz','duration_s','peak','rms','dc','endpoint_samples','high_band_db_relative']
    with (OUT/'resumen.csv').open('w',newline='',encoding='utf8') as fh:
        writer=csv.DictWriter(fh,fieldnames=fields,extrasaction='ignore'); writer.writeheader();writer.writerows(records)
    fig,axes=plt.subplots(2,1,figsize=(11,9))
    for lang,ax in zip(LANGS,axes):
        for kind,rate in [('directa',11025),('sinc',32000),('directa',24000),('directa',32000),('wide_on',32000)]:
            p=OUT/f'{lang}_frase_{kind}_{rate}.wav'
            if not p.exists():continue
            fs,x=wav_read(p);f,power=psd(x,fs)
            ax.plot(f,10*np.log10(np.maximum(power,1e-10)),label=f'{kind} {rate} Hz')
        ax.axvline(5512.5,color='black',linestyle='--',label='Nyquist original 5512,5 Hz')
        ax.set(xlim=(0,10000),ylim=(-45,60),xlabel='Frecuencia (Hz)',ylabel='Densidad de potencia (dB re 1 cuenta PCM²/Hz)',title=lang+' — espectro medio, sin normalizar volumen')
        ax.legend(fontsize=8);ax.grid(alpha=.2)
    fig.tight_layout();fig.savefig(OUT/'comparacion-espectros.png',dpi=160);plt.close(fig)
    fig,ax=plt.subplots(figsize=(10,5))
    for lang,word in [('eses','asa'),('esus','asa'),('eses','aza')]:
        fs,x=wav_read(OUT/f'{lang}_{word}_11025.wav')
        rows=list(csv.DictReader((OUT/f'{lang}_{word}_11025.tsv').open(),delimiter='\t'))
        starts=np.r_[0,np.cumsum([int(r['step'])*fs//1000 for r in rows])]
        active=np.array([int(r['af'])>0 and int(r['av'])==0 for r in rows])
        idx=np.where(active)[0];lo=starts[idx[0]]+int(fs*.01);hi=starts[idx[-1]+1]-int(fs*.01)
        f,p=psd(x[lo:hi],fs)
        ax.plot(f,10*np.log10(np.maximum(p/p.sum(),1e-12)),label=f'{lang} {word}')
    ax.set(xlim=(500,5000),xlabel='Frecuencia (Hz)',ylabel='Potencia por bin / potencia total (dB)',title='Ruido central: /s/ castellana, /s/ latinoamericana y /θ/ castellana')
    ax.legend();ax.grid(alpha=.2);fig.tight_layout();fig.savefig(OUT/'comparacion-fricativas.png',dpi=160);plt.close(fig)
    for r in records:
        if 'frase' in r['file']:print(r['file'],round(r['duration_s'],3),r['peak'],round(r['rms'],1),round(r['high_band_db_relative'],1),r['endpoint_samples'])

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--generate',action='store_true');parser.add_argument('--analyze',action='store_true');args=parser.parse_args()
    if args.generate:generate()
    if args.analyze:analyze()
