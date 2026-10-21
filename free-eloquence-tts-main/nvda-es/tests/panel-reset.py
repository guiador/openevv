"""Exercise the actual settings panel handlers with wx/NVDA UI stand-ins."""
import ast
import importlib.util
import json
from pathlib import Path
import types
import sys

ROOT=Path(__file__).resolve().parents[1]
path=ROOT/'addon/globalPlugins/openevv_es_settings.py'
buttons=[]

class Event:
    def Skip(self):pass

class Widget:
    def __init__(self,*args,**kw):
        self.parent=args[0] if args else None
        self.label=kw.get('label','');self.choices=list(kw.get('choices',[]))
        self.value=kw.get('initial',kw.get('value',0));self.selection=0;self.bindings={}
    def Bind(self,event,handler):self.__dict__.setdefault('bindings',{})[event]=handler
    def SetValue(self,value):
        self.value=value
        if 'text' in self.bindings:self.bindings['text'](Event())
    def GetValue(self):return self.value
    def SetSelection(self,index):self.selection=index
    def GetSelection(self):return self.selection
    def AddPage(self,page,label):page.label=label
    def Append(self,label):self.choices.append(label)
    def Add(self,*args,**kw):pass
    def SetName(self,*args):pass
    def SetHelpText(self,*args):pass
    def SetSizer(self,*args):pass
    def Wrap(self,*args):pass
    def SetLabel(self,label):self.label=label
    def Disable(self):pass
    def Enable(self):pass
    def SetupScrolling(self,**kw):pass

class Button(Widget):
    def __init__(self,*args,**kw):super().__init__(*args,**kw);buttons.append(self)
    def click(self):self.bindings['button'](Event())

wx=types.SimpleNamespace(**{name:Widget for name in ('Notebook','BoxSizer','StaticText','SpinCtrl','Choice','CheckBox','TextCtrl')},Button=Button,
    EVT_BUTTON='button',EVT_TEXT='text',EVT_SPINCTRL='spin',EVT_CHOICE='choice',EVT_WINDOW_DESTROY='destroy',
    VERTICAL=1,HORIZONTAL=2,EXPAND=4,ALL=8,ALIGN_CENTER_VERTICAL=16,LEFT=32,TE_MULTILINE=64)
native=types.SimpleNamespace(VOICE_GENDER=0,VOICE_HEAD_SIZE=1,VOICE_PITCH=2,VOICE_FLUCTUATION=3,VOICE_ROUGHNESS=4,VOICE_BREATHINESS=5,localeOf=lambda lang:'es_ES')
def load(name):
    spec=importlib.util.spec_from_file_location(name,ROOT/'addon/synthDrivers'/f'{name}.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module
audio=load('_openevv_es_audio');ui=load('_openevv_es_ui')
response=load('_openevv_es_response')
dictionary=types.SimpleNamespace(managedSets=lambda:{})
package=types.ModuleType('synthDrivers');package._openevv_es_dictionaries=dictionary
sys.modules['synthDrivers']=package
import sys
sys.modules['synthDrivers._openevv_es_response']=response
defaults={0:1,1:62,2:43,3:22,4:9,5:17,6:70,7:89}
engine=types.SimpleNamespace(voiceDefaultsByLanguage={1:{6:defaults}},control=lambda batch:None,refreshOutput=lambda:None)

class Synth:
    name='openevv_es';voice='1:6';pitch=80;inflection=99;rate=61;volume=72;_engine=engine
    def _home(self):return 1
    def _splitVoiceId(self,value):return 1,6
    def __getattr__(self,key):
        if key.startswith('_response_'):return response.DEFAULTS[key[len('_response_'):]]
        return {'_gender':0,'_headSize':30,'_roughness':5,'_breathiness':10,'_samplerate':22050,'_quality':'2','_resampling':'4','_balance':0,'_eqPreset':'aguda','_preamp':-3}.get(key,0)

synth=Synth()
config=types.SimpleNamespace(conf={'speech':{'openevv_es':{},'symbolLevel':100},'audio':{'audioAwakeTime':30,'whiteNoiseVolume':10}})
tree=ast.parse(path.read_text(encoding='utf8'))
cls=next(n for n in tree.body if isinstance(n,ast.ClassDef) and n.name=='OpenEVVPanel')
cls.bases=[ast.Name(id='Widget',ctx=ast.Load())];cls.keywords=[]
env=dict(globals(),__file__=str(path),ScrolledPanel=Widget,translator=ui.translator,PRESETS=audio.PRESETS,getSynth=lambda:synth)
exec(compile(ast.fix_missing_locations(ast.Module(body=[cls],type_ignores=[])),str(path),'exec'),env)
panel=env['OpenEVVPanel']();panel.makeSettings(Widget())
assert panel.symbolIds[panel.symbols.GetSelection()]==100
panel.symbols.SetSelection(panel.symbolIds.index(300))
assert config.conf['speech']['symbolLevel']==100, 'Cancel must leave symbol pronunciation unchanged'
reset=[b for b in buttons if b.label==ui.translator('es')('reset_defaults')]
assert len(reset)==3
reset[0].click()
assert synth.pitch==80 and synth.inflection==99 and not config.conf['speech']['openevv_es'], 'Reset must wait for Apply or OK'
for key,param in (('_gender',0),('_headSize',1),('_roughness',4),('_breathiness',5)):
    ctrl,ids=panel.controls[key]
    assert (int(ids[ctrl.GetSelection()]) if ids else ctrl.GetValue())==defaults[param]
assert panel.pendingPitch==43 and panel.pendingInflection==22
mode,ids=panel.controls['_synthesisMode'];mode.SetSelection(ids.index('2'));mode.bindings['choice'](Event())
rate,rates=panel.controls['_samplerate'];assert rates[rate.GetSelection()]=='16000'
assert synth._synthesisMode==0 and not config.conf['speech']['openevv_es'], 'Mode selection must be staged until Apply'
reset[1].click();assert mode.GetSelection()==0
reset[2].click()
ctrl,ids=panel.controls['_eqPreset'];assert ids[ctrl.GetSelection()]=='neutral'
assert all(panel.controls[f'_eq{i}'][0].GetValue()==0 for i in range(5))
assert panel.controls['_preamp'][0].GetValue()==0
panel.onSave()
assert config.conf['speech']['symbolLevel']==300 and 'symbolLevel' not in config.conf['speech']['openevv_es']
assert synth.pitch==43 and synth.inflection==22
assert synth.rate==61 and synth.volume==72
section=config.conf['speech']['openevv_es']
assert section['pitch']==43 and section['inflection']==22 and section['_eqPreset']=='neutral'
assert all(section[f'_eq{i}']==0 for i in range(5))
synth.pitch=67;synth.inflection=44;section.update(pitch=67,inflection=44)
panel.onSave()
assert synth.pitch==67 and synth.inflection==44, 'Apply must not reapply an already saved timbre change'
for level in (0,100,200,300):
    panel.symbols.SetSelection(panel.symbolIds.index(level));panel.onSave()
    assert config.conf['speech']['symbolLevel']==level
config.conf['speech']['symbolLevel']=100
panel.onSave()
assert config.conf['speech']['symbolLevel']==100, 'Unchanged selector must not overwrite a change from NVDA Speech settings'
config.conf['speech']['symbolLevel']=150
otherPanel=env['OpenEVVPanel']();otherPanel.makeSettings(Widget());otherPanel.onSave()
assert config.conf['speech']['symbolLevel']==150, 'Unknown profile levels must not silently become Some'
otherPanel.symbols.SetSelection(otherPanel.symbolIds.index(100));otherPanel.onSave()
assert config.conf['speech']['symbolLevel']==100
for language in ('es','en','de','fr','it'):
    assert 'Guiador' in ui.translator(language)('local_credits') and 'GPT Codex' in ui.translator(language)('local_credits')
report={'passed':10,'checks':['symbol selector stages changes until Apply and uses shared NVDA configuration','all four symbol levels save correctly without overwriting external changes when unchanged','voice reset uses selected preset rather than generic 50','Cancel leaves synthesizer and profile untouched','EQ reset remains neutral despite programmatic text events','Apply restores and persists pitch, inflection, timbre and EQ while keeping rate and volume','repeated Apply does not reuse stale pending pitch or inflection','credits available to the About page in all five locales','native16 selection stages a 16000 Hz output without mutating the active synth','synthesis reset returns to classic mode independently of voice and EQ'],'ui':'simulated wx/NVDA; actual panel methods'}
report['checks'].append('unrecognized profile symbol levels stay unchanged until an explicit supported level is chosen')
report['passed']=len(report['checks'])
(ROOT/'tests/panel-reset-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
for check in report['checks']:print('PASS:',check)
