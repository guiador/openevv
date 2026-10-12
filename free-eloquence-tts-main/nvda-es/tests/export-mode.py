"""Actual private-DLL WAV export with native16 and pronunciation dictionaries."""
from pathlib import Path
import hashlib, importlib, json, tempfile, wave
source=Path(__file__).with_name('voice-stability.py').read_text(encoding='utf8')
exec(compile(source.split('\nchecks=[]\n')[0],str(__file__),'exec'))
export=importlib.import_module('synthDrivers._openevv_es_export')
text=importlib.import_module('synthDrivers._openevv_es_text')
engine=native.Engine(lambda index:None);engine.open()
try:
    params=read_params(engine)
    options=dict(engine.audioOptions,samplerate=16000,synthesisMode=2,quality='3',balance=0)
    with tempfile.TemporaryDirectory() as folder:
        directory=Path(folder);(directory/'ESMMain.dic').write_bytes(b'codexprueba\thola\r\n')
        plain=directory/'plain.wav';custom=directory/'dictionary.wav'
        without=export.render(plain,'codexprueba.',0x20001,params,options,dict(text.DEFAULTS))
        withDictionary=export.render(custom,'codexprueba.',0x20001,params,dict(options,dictionaryFolders=(folder,)),dict(text.DEFAULTS))
        assert withDictionary<without, 'Export did not use the dictionary'
        for path in (plain,custom):
            with wave.open(str(path),'rb') as wav:
                assert wav.getframerate()==16000 and wav.getnchannels()==2 and wav.getsampwidth()==2
                assert wav.getnframes()>0 and any(wav.readframes(wav.getnframes()))
        assert engine._dll.eciGetParam(engine._instance,32)==0, 'Export changed the live mode'
        assert read_params(engine)==params, 'Export changed the live voice'
finally:engine.close()
report=dict(passed=3,checks=['native16 export produces stereo PCM16 at 16000 Hz','export applies selected pronunciation dictionaries','private export DLL leaves live synthesis mode and voice unchanged'],dll_sha256=hashlib.sha256((ADDON/'openevv_es_engine/eci.dll').read_bytes()).hexdigest())
(ROOT/'tests/export-mode-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
for check in report['checks']:print('PASS:',check)
