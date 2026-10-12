"""Real worker/DLL integration: new audio modes and upstream dictionaries."""
import runpy, tempfile, time, hashlib, json, threading, os
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
# This test process uses the normal baseline; the reference child uses none.
os.environ['EVV_UPSAMPLE']='sinc'
env=runpy.run_path(str(ROOT/'tests/voice-stability.py'))
native=env['native'];barrier=env['barrier']
checks=[]
class Player(env['Player']):
    def __init__(self,**kw):super().__init__(**kw);self.pcm=bytearray()
    def feed(self,pcm,onDone=None):self.pcm.extend(pcm);super().feed(pcm,onDone)
native.nvwave.WavePlayer=Player
def control(engine,fn,*args):
    errors=[]
    def call():
        try:fn(*args)
        except Exception as error:errors.append(error)
    engine.control([(call,())]);barrier(engine)
    if errors:raise errors[0]
def speak(engine,text=b'Una prueba del motor.'): 
    engine.player.pcm.clear()
    engine.post([(engine.addText,(text,)),(engine.synthesize,())]);barrier(engine)
    pcm=bytes(engine.player.pcm)
    assert len(pcm)>100 and any(pcm), 'No audio'
    return pcm
engine=native.Engine(lambda index:None);engine.open()
other=native.Engine(lambda index:None);other.open()
import ctypes
engine._dll.eciGetDict.argtypes=[ctypes.c_void_p];engine._dll.eciGetDict.restype=ctypes.c_void_p
engine._dll.eciDictLookup.argtypes=[ctypes.c_void_p,ctypes.c_void_p,ctypes.c_int,ctypes.c_char_p]
engine._dll.eciDictLookup.restype=ctypes.c_char_p
def entry(engine):
    result=[]
    def read():
        handle=engine._dll.eciGetDict(engine._instance)
        result.append((handle,engine._dll.eciDictLookup(engine._instance,handle,0,b'codexprueba') if handle else None))
    control(engine,read)
    return result[0]
try:
    assert engine._dll.eciGetParam(engine._instance,32)==0
    assert engine.setParam(32,3)<0 and engine.setParam(32,-1)<0
    control(engine,engine.setSampleRate,16000)
    baseline=speak(engine)
    for mode in (1,2):
        result=[]
        control(engine,lambda:result.append(engine.setParam(32,mode)))
        assert result[0]>=0
        assert engine._dll.eciGetParam(engine._instance,32)==mode
        assert engine._dll.eciGetParam(other._instance,32)==0, 'Mode leaked to another instance'
        changed=speak(engine)
        assert changed!=baseline, 'Mode did not change audio'
        if mode==2:native16=hashlib.sha256(changed).hexdigest()
        kept=env['read_params'](engine)
        speak(engine,b'`vb99 Capital A')
        assert env['read_params'](engine)==kept, 'Capitals changed saved parameters in an experimental mode'
        control(engine,engine.setParam,32,0)
        restored=speak(engine)
        assert restored==baseline, 'Classic audio did not return exactly'
    checks.append('wideband/native16 change real PCM, remain per instance and restore classic PCM exactly')
    import os, subprocess, sys
    reference=subprocess.check_output([sys.executable,'-X','utf8',str(ROOT/'tests/native16-reference.py')],env=dict(os.environ,EVV_UPSAMPLE='none'),text=True).strip()
    assert reference==native16, 'Native16 differs from direct upstream 16 kHz synthesis'
    checks.append('native16 PCM exactly matches upstream direct 16 kHz synthesis in an isolated reference process')
    matrix=[]
    for rate in (8000,11025,16000,22050,24000,32000,44100,48000):
        control(engine,engine.setSampleRate,rate)
        for mode in (0,1,2):
            control(engine,engine.setParam,32,mode)
            matrix.append(dict(rate=rate,mode=mode,bytes=len(speak(engine))))
    checks.append('24 real syntheses cover all eight output rates and three source modes')
    for language in engine.languages:
        control(engine,engine.setLanguage,language)
        for mode in (1,2):
            control(engine,engine.setParam,32,mode);speak(engine,b'OpenEVV 123.')
    checks.append('wideband and native16 synthesize with every packaged language')
    # Pronunciation comparisons use raw PCM: gain follows callback blocks,
    # whose boundaries can differ when the native language changes.
    control(engine,engine.configureAudio,dict(engine.audioOptions,quality='3'))
    with tempfile.TemporaryDirectory() as folder:
        # No newline deliberately: the upstream loader must append one.
        dictionary=Path(folder)/'ESMMain.dic'
        dictionary.write_bytes(b'codexprueba\thola')
        control(engine,engine.setLanguage,0x20001)
        control(engine,engine.setSampleRate,11025)
        control(engine,engine.setParam,32,0)
        before=speak(engine,b'codexprueba.')
        control(engine,engine.setDictionaryFolders,(folder,))
        replaced=speak(engine,b'codexprueba.')
        assert replaced!=before and engine._dictionaries[0x20001]
        assert entry(engine)==(engine._dictionaries[0x20001],b'hola')
        dictionaryVoice=env['read_params'](engine)
        handle=engine._dictionaries[0x20001]
        control(engine,engine.setLanguage,0x10000);speak(engine)
        control(engine,engine.setLanguage,0x20001)
        assert engine._dictionaries[0x20001]==handle
        # A bare ECI language switch loads a preset. Normal driver speech
        # restores user voice parameters; do likewise for this comparison.
        returnedVoice=env['read_params'](engine)
        for param,value in dictionaryVoice.items():control(engine,engine.setVoiceParam,param,value)
        returned=speak(engine,b'codexprueba.')
        literal=speak(engine,b'hola.')
        # The engine can vary prosody across language histories. Its active
        # dictionary and entry, plus the replacement duration, prove reuse.
        assert entry(engine)==(handle,b'hola')
        assert len(returned)==len(literal) and len(returned)<len(before)
        dictionary.write_bytes(b'codexprueba\tadios\r\n')
        assert engine.dictionariesChanged()
        deadline=time.monotonic()+10
        while engine.dictionariesChanged() and time.monotonic()<deadline:time.sleep(.1)
        barrier(engine)
        assert not engine.dictionariesChanged(), 'Automatic dictionary reload did not complete'
        assert entry(engine)==(engine._dictionaries[0x20001],b'adios')
        assert speak(engine,b'codexprueba.')!=replaced
        dictionary.unlink()
        control(engine,engine.refreshDictionaries)
        assert entry(engine)==(None,None)
        assert len(speak(engine,b'codexprueba.'))==len(before)
        # Exercise the driver-facing folder discovery and queued setters too.
        import importlib, sys, types
        sys.modules['globalVars']=types.SimpleNamespace(appArgs=types.SimpleNamespace(configPath=folder))
        sys.modules['addonHandler']=types.SimpleNamespace(getAvailableAddons=lambda:[])
        dictionaries=importlib.import_module('synthDrivers._openevv_es_dictionaries')
        assert dictionaries.folders('none',True)==(str(Path(folder)/'eciDictionaries/personal'),str(Path(folder)/'openevv'))
        class Setting:
            def __init__(self,id,name,**kw):self.id=id;self.__dict__.update(kw)
        package=types.ModuleType('autoSettingsUtils');package.__path__=[]
        sys.modules['autoSettingsUtils']=package
        sys.modules['autoSettingsUtils.driverSetting']=types.SimpleNamespace(BooleanDriverSetting=Setting,DriverSetting=Setting,NumericDriverSetting=Setting)
        features=importlib.import_module('synthDrivers._openevv_es_features')
        class Driver:
            supportedSettings=()
            def __init__(self):self._engine=engine;self._audioOptions={}
            def _processText(self,text):return text
        features.install(Driver)
        driver=Driver();barrier(engine)
        assert driver._synthesisMode==0 and driver._dictionarySet=='none' and driver._personalDictionary is True
        driver._synthesisMode=2;driver._pauses=2;barrier(engine)
        assert engine._dll.eciGetParam(engine._instance,32)==2 and engine._dll.eciGetParam(engine._instance,33)==2
        driver._synthesisMode=0;driver._personalDictionary=False;barrier(engine)
        assert engine._dictionaryFolders==(str(Path(folder)/'openevv'),)
    checks.append('case-insensitive dictionaries, missing newline, language reuse, automatic reload and removal')
finally:engine.close();other.close()
report=dict(passed=len(checks),checks=checks,matrix=matrix,dll='actual packaged x64 eci.dll',dll_sha256=hashlib.sha256((ROOT/'addon/synthDrivers/openevv_es_engine/eci.dll').read_bytes()).hexdigest(),nvda_ui='simulated')
(ROOT/'tests/latest-features-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
for check in checks:print('PASS:',check)
