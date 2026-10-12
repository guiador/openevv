"""Regression checks using the shipped driver methods and actual x64 DLL.

NVDA audio/UI modules are simulated; the worker, queues, ctypes calls, speech
annotations and synthesis are real. No installed NVDA configuration is changed.
"""
import ast
import ctypes
import importlib
import importlib.util
import hashlib
import json
import logging
from pathlib import Path
import re
import sys
import threading
import types

ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parents[1]
ADDON = ROOT / 'addon/synthDrivers'
sys.dont_write_bytecode = True
logging.basicConfig(level=logging.CRITICAL)
log = logging.getLogger('voice-regression')
log.debugWarning = log.warning
conf = {'audio': {'outputDevice': 'default'}, 'speech': {'openevv_es': {'pitch': 58, 'rate': 50, 'volume': 81}}}
sys.modules['config'] = types.SimpleNamespace(conf=conf)
sys.modules['logHandler'] = types.SimpleNamespace(log=log)

class Player:
    def __init__(self, **kwargs): self.samples = 0
    def feed(self, pcm, onDone=None):
        self.samples += len(pcm)
        if onDone: onDone()
    def idle(self): pass
    def sync(self): pass
    def stop(self): pass
    def close(self): pass
    def pause(self, switch): pass

sys.modules['nvwave'] = types.SimpleNamespace(WavePlayer=Player)
package = types.ModuleType('synthDrivers')
package.__path__ = [str(ADDON)]
sys.modules['synthDrivers'] = package

def load_engine(path, name):
    spec = importlib.util.spec_from_file_location('synthDrivers.' + name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.libraryPath = lambda: str(ADDON / 'openevv_es_engine/eci.dll')
    return module

native = load_engine(ADDON / '_openevv_es.py', '_test_engine')

class Command:
    def __init__(self, newValue=0, isDefault=False):
        self.newValue = newValue; self.isDefault = isDefault

PitchCommand = type('PitchCommand', (Command,), {})
RateCommand = type('RateCommand', (Command,), {})
VolumeCommand = type('VolumeCommand', (Command,), {})
IndexCommand = type('IndexCommand', (Command,), {})
CharacterModeCommand = type('CharacterModeCommand', (Command,), {})
LangChangeCommand = type('LangChangeCommand', (Command,), {})
BreakCommand = type('BreakCommand', (Command,), {})

def driver_class(path):
    tree = ast.parse(path.read_text(encoding='utf8'))
    cls = next(n for n in tree.body if isinstance(n, ast.ClassDef) and n.name == 'SynthDriver')
    cls.bases = []; cls.keywords = []
    cls.body = [n for n in cls.body if isinstance(n, ast.FunctionDef)]
    nodes = [n for n in tree.body if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id in ('MIN_RATE','MAX_RATE','RATE_BOOST','BREAK_FACTORS','PIECE_CAP','_BOUNDARY','_CLOSERS') for t in n.targets)]
    nodes += [n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == '_endsSentence']
    env = dict(globals(), _openevv=native, effective=lambda options: dict(pieceChars=500, sentenceEnds=True, hardCut=False), shorten_pauses=lambda text,*a:text)
    import __future__
    exec(compile(ast.fix_missing_locations(ast.Module(body=nodes+[cls], type_ignores=[])),str(path),'exec',flags=__future__.annotations.compiler_flag),env)
    cls = env['SynthDriver']
    for key in list(cls.__dict__):
        if key.startswith('_get_'):
            name=key[5:]
            setattr(cls,name,property(getattr(cls,key),getattr(cls,'_set_'+name,None)))
    cls._processText=lambda self,text,**kw:text
    cls._percentToParam=staticmethod(lambda v,lo,hi:round(lo+(hi-lo)*v/100))
    cls._paramToPercent=staticmethod(lambda v,lo,hi:round((v-lo)*100/(hi-lo)))
    return cls

def make_driver(cls, engine):
    driver=cls.__new__(cls)
    driver._engine=engine;driver._voiceParams=dict(engine.voiceParams)
    driver._voice=driver._voiceId(engine.language,1)
    driver._rateBoost=False;driver._responseOptions={}
    driver._voiceTags=False;driver._pauses=0;driver._phrasePrediction=False
    driver._forcePitch=False;driver._forceRate=False;driver._forceVolume=False;driver._forceLanguage=False
    return driver

def barrier(engine):
    done=threading.Event();engine.control([(done.set,())])
    assert done.wait(20), 'Worker did not finish within 20 seconds'

def read_params(engine):
    result={}
    engine.control([(lambda:result.update({p:engine.getVoiceParam(p) for p in native.VOICE_RANGE}),())])
    barrier(engine)
    return result

checks=[]
engine=native.Engine(lambda index:None)
engine.open()
try:
    assert len(engine.voiceDefaultsByLanguage)==8
    assert all(len(v)==8 for v in engine.voiceDefaultsByLanguage.values())
    assert read_params(engine)==engine.voiceDefaultsByLanguage[engine.language][1]
    checks.append('64 native voice defaults and deterministic initial voice')
    cls=driver_class(ADDON/'openevv_es.py')
    driver=make_driver(cls,engine)

    # Hold the actual worker to reproduce slow-machine startup ordering.
    entered=threading.Event();release=threading.Event()
    def hold(): entered.set();assert release.wait(20)
    engine.control([(hold,())]);assert entered.wait(10)
    for i in range(80):
        language=engine.languages[i % 8]
        driver.voice=driver._voiceId(language,1+i%8)
        driver.pitch=58;driver.inflection=37;driver.volume=81
    expected=dict(driver._voiceParams)
    assert driver.pitch==58 and driver.inflection==37 and driver.volume==81
    release.set();barrier(engine)
    assert driver._voiceParams==expected and read_params(engine)==expected
    checks.append('80 queued voice/profile loads preserve configured parameters')

    driver.voice=driver._voiceId(engine.languages[0],1);barrier(engine)
    entered.clear();release.clear();engine.control([(hold,())]);assert entered.wait(10)
    driver.voice=driver._voiceId(engine.languages[1],2)
    driver.voice=driver._voiceId(engine.languages[0],1)
    driver.pitch=58;driver.inflection=37;driver.volume=81
    expected=dict(driver._voiceParams)
    release.set();barrier(engine)
    assert engine.language==engine.languages[0] and read_params(engine)==expected
    checks.append('rapid language switch and return respects queued selection order')

    # Real synthesis without a closing pitch command, as with an interrupted
    # capitals announcement. Check native values and GUI getters afterwards.
    for name,sequence in (
        ('unclosed capital pitch',[PitchCommand(88),'A']),
        ('closed capital pitch',[PitchCommand(88),'A',PitchCommand(58,True)]),
        ('unclosed rate and volume',[RateCommand(85),VolumeCommand(20),'Example']),
        ('capitals pitch above range',[PitchCommand(140),'A']),
        ('pitch and volume below range',[PitchCommand(-30),VolumeCommand(-20),'Example']),
    ):
        driver.speak(sequence);barrier(engine)
        assert read_params(engine)==expected, name
        assert driver._voiceParams==expected, name
        driver.speak(['Normal speech']);barrier(engine)
        assert read_params(engine)==expected, name
        checks.append(name+' and following normal speech restore native parameters')
    assert driver._pitchToParam(140)==100 and driver._pitchToParam(-30)==0
    assert driver._volumeToParam(140)==100 and driver._volumeToParam(-20)==0

    def interrupted():
        engine.addText(b'`vb99 `vs190 `vv15 Capital A')
        engine.cancel()
        engine.synthesize()
    engine.post([(interrupted,())]);barrier(engine)
    assert read_params(engine)==expected
    checks.append('cancellation inside native synthesis restores parameters')

    def fault():
        engine._dll.eciSetVoiceParam(engine._instance,0,native.VOICE_PITCH,99)
        raise RuntimeError('intentional regression fault')
    engine.post([(fault,())]);barrier(engine)
    assert read_params(engine)==expected
    checks.append('batch exception restores parameters and worker remains usable')

    # Prove that the regressions observe the original bugs, using the same DLL.
    before=ROOT/'tests/fixtures'
    if not before.exists():before=WORKSPACE/'.codex-engine-update'
    old=driver_class(before/'openevv_es-before-stability.py')
    previous=make_driver(old,engine)
    controls=[];real_control=engine.control
    engine.control=lambda batch:controls.append(batch)
    previous.voice=previous._voiceId(engine.languages[0],2)
    previous.pitch=58
    engine.control=real_control
    real_control(controls[0])
    barrier(engine)
    assert previous.pitch!=58, 'Startup regression did not detect the old bug'
    for batch in controls[1:]:real_control(batch)
    barrier(engine)
    checks.append('old controller demonstrably fails the delayed-load regression')

    old_native=load_engine(before/'engine-before-stability.py','_old_engine')
    old_engine=old_native.Engine(lambda index:None);old_engine.open()
    try:
        initial=read_params(old_engine)
        old_engine.post([(old_engine.addText,(b'`vb99 Capital A',)),(old_engine.synthesize,())])
        barrier(old_engine)
        assert read_params(old_engine)[native.VOICE_PITCH]!=initial[native.VOICE_PITCH], 'Speech regression did not detect the old bug'
        checks.append('old worker demonstrably leaks the capitals pitch')
    finally:old_engine.close()
finally:
    engine.close()

report=dict(passed=len(checks),checks=checks,dll='actual packaged x64 eci.dll',dll_sha256=hashlib.sha256((ADDON/'openevv_es_engine/eci.dll').read_bytes()).hexdigest(),nvda_ui='simulated',native_commit=(ROOT.parent/'native/openevv-revision.txt').read_text().strip())
(ROOT/'tests/voice-stability-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
for check in checks:print('PASS:',check)
