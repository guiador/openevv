"""Deriva el controlador de la revisión nativa usada por Android, sin editarla."""
from pathlib import Path
root=Path(__file__).resolve().parent
# The customized driver is generated from its original source snapshot.
# Native engine updates are independent; upgrading this snapshot requires
# adapting the transformations below to the new driver's structure.
upstream=root/'upstream/synthDrivers'
target=root/'addon/synthDrivers'
driver=(upstream/'openevv.py').read_text(encoding='utf8')
engine=(upstream/'_openevv.py').read_text(encoding='utf8')
driver=driver.replace('from . import _openevv','from . import _openevv_es as _openevv')
driver=driver.replace('name = "openevv"','name = "openevv_es"').replace('description = "Eloquence (openevv)"','description = "OpenEVV Español — sonido ampliado"')
for key,label in [('headSize','Tamaño y resonancia'),('roughness','Aspereza'),('breathiness','Aire en la voz')]:
    import re
    driver=re.sub(r'NumericDriverSetting\("'+key+r'", _\("[^"]+"\), False\)',f'NumericDriverSetting("_{key}", "{label}", False)',driver)
for key,label in [('abbreviations','Expandir abreviaturas'),('voiceTags','Permitir anotaciones del motor')]:
    setting={'abbreviations':'expandAbbreviations','voiceTags':'allowVoiceTags'}[key]
    driver=re.sub(r'BooleanDriverSetting\("'+key+r'", _\("[^"]+"\), False\)',f'BooleanDriverSetting("_{setting}", "{label}", False)',driver)
driver=driver.replace('NumericDriverSetting("_roughness", "Aspereza", False)','NumericDriverSetting("_roughness", "Aspereza", False, defaultVal=0)')
driver=driver.replace('NumericDriverSetting("_breathiness", "Aire en la voz", False)','NumericDriverSetting("_breathiness", "Aire en la voz", False, defaultVal=0)')
driver=driver.replace('DriverSetting("samplerate", _("Sa&mple rate"), False)','DriverSetting("_samplerate", "Frecuencia de muestreo", False, defaultVal="11025")')
driver=driver.replace('\t\tNumericDriverSetting("_headSize"','\t\tNumericDriverSetting("_gender", "Tipo de voz", defaultVal=0, minVal=0, maxVal=1),\n\t\tNumericDriverSetting("_headSize"',1)
marker='\t\tsupportedCommands = {'
# Add persistent, hidden settings: grouped panel exposes them.
at=driver.index('\n\tsupportedCommands = {')
driver=driver[:at]+driver[at:]
driver=driver.replace('\n\t)\n\n\tsupportedCommands', '\n\t\tDriverSetting("_quality", "Mejora de audio", defaultVal="2"),\n\t\tDriverSetting("_resampling", "Remuestreo", defaultVal="4"),\n\t\tDriverSetting("_eqPreset", "Perfil del ecualizador", defaultVal="neutral"),\n\t\tNumericDriverSetting("_preamp", "Ganancia", defaultVal=0, minVal=-12, maxVal=12),\n'+''.join(f'\t\tNumericDriverSetting("_eq{i}", "Banda {hz} Hz", defaultVal=0, minVal=-12, maxVal=12),\n' for i,hz in enumerate([120,350,1000,2500,3500]))+'\t)\n\n\tsupportedCommands')
driver=driver.replace('self._engine = _openevv.Engine(self._onIndexReached)','self._audioOptions = {"quality": "2", "resampling": "4", "eqPreset": "neutral", "preamp": 0, **{"eq%d" % i: 0 for i in range(5)}}\n\t\tself._engine = _openevv.Engine(self._onIndexReached)')
driver=driver.replace('\t\tself._engine.control([(self._engine.setVoiceParam, (which, value))])','\t\tlo, hi = _openevv.VOICE_RANGE[which]\n\t\tvalue = max(lo, min(hi, int(value)))\n\t\tself._engine.voiceParams[which] = value\n\t\tself._engine.control([(self._engine.setVoiceParam, (which, value))])')
driver += '''

# Hidden settings still use NVDA persistence and profiles; the grouped panel
# reads these properties. Basic voice controls remain in NVDA's speech panel.
for _id, _name in (("headSize","headSize"),("roughness","roughness"),("breathiness","breathiness"),("expandAbbreviations","abbreviations"),("allowVoiceTags","voiceTags"),("samplerate","samplerate")):
    setattr(SynthDriver, "_"+_id, property(
        lambda self, n=_name: getattr(self, n),
        lambda self, value, n=_name: setattr(self, n, value)))

def _audioGet(self, name):
    return self._audioOptions[name]

def _audioSet(self, name, value):
    if name in ("quality", "resampling", "eqPreset"):
        value=str(value)
        legal={"quality": {"0","1","2","3"}, "resampling": {"-1","0","2","3","4"}, "eqPreset": {"neutral","profunda","aguda","suave","claridad","manual"}}[name]
        if value not in legal:return
    else:
        value=max(-12,min(12,int(value)))
    self._audioOptions[name]=value
    if name=="eqPreset" and value!="manual":
        from ._openevv_es_audio import PRESETS
        bands,pre=PRESETS[value]
        self._audioOptions.update({"eq%d" % i: db for i,db in enumerate(bands)})
        self._audioOptions["preamp"]=pre
    options=dict(self._audioOptions)
    self._engine.control([(self._engine.configureAudio,(options,))])

for _name in ("quality","resampling","eqPreset","preamp","eq0","eq1","eq2","eq3","eq4"):
    setattr(SynthDriver,"_"+_name,property(lambda self,n=_name:_audioGet(self,n),lambda self,v,n=_name:_audioSet(self,n,v)))
SynthDriver._gender=property(lambda self:self._engine.voiceParams.get(_openevv.VOICE_GENDER,0),lambda self,v:self._post(_openevv.VOICE_GENDER,v))
'''
engine=engine.replace('import config','from ._openevv_es_audio import Processor, stereo\n\nimport config',1)
engine=engine.replace('"openevv_engine"','"openevv_es_engine"')
engine=engine.replace('(4, 32000),','(24000, 24000),\n\t(4, 32000),')
translations={'US English':'Inglés de Estados Unidos','British English':'Inglés británico','Castilian Spanish':'Español de España','Latin American Spanish':'Español latinoamericano','Canadian French':'Francés canadiense','French':'Francés','German':'Alemán','Italian':'Italiano'}
for old,new in translations.items():engine=engine.replace('"'+old+'"','"'+new+'"')
engine=engine.replace('self.sampleRate = SAMPLE_RATE','self.sampleRate = SAMPLE_RATE\n\t\tself.audioOptions = {"quality": "2", "resampling": "4", "preamp": 0, **{"eq%d" % i: 0 for i in range(5)}}\n\t\tself._processor = Processor(SAMPLE_RATE)',1)
engine=engine.replace('\t\tself._held = bytearray()\n\t\tself._pendingIndexes = []\n\t\tself._produced = 0\n\t\tself._finished = False','\t\tself._processor.reset()\n\t\tself._held = bytearray()\n\t\tself._pendingIndexes = []\n\t\tself._produced = 0\n\t\tself._finished = False')
engine=engine.replace('\t\t\taudio = bytes(self._held)','\t\t\taudio = self._processor.process(bytes(self._held))')
engine=engine.replace('\t\tself.sampleRate = hz\n','\t\tself.sampleRate = hz\n\t\tself._configureProcessor()\n')
engine=engine.replace('\t\tdll = ctypes.WinDLL(path)','\t\tdll = ctypes.WinDLL(path)\n\t\tdll.evvSetUpsampleMethod.argtypes = [ctypes.c_int]\n\t\tdll.evvSetUpsampleMethod.restype = None\n\t\tdll.evvSetUpsampleMethod(4)')
position=engine.index('\n\tdef getVoiceParam(')
engine=engine[:position]+'''
    def _configureProcessor(self):
        opt=self.audioOptions
        self._processor=Processor(self.sampleRate,int(opt["quality"]),[int(opt["eq%d" % i]) for i in range(5)],int(opt["preamp"]))

    def configureAudio(self, options):
        method=int(options["resampling"])
        if method==-1:method={0:3,1:0,2:4,3:4}[int(options["quality"])]
        self._dll.evvSetUpsampleMethod(method)
        code=next(code for code,hz in SAMPLE_RATES if hz==self.sampleRate)
        if self.setParam(PARAM_SAMPLE_RATE,code)<0:
            previous=int(self.audioOptions["resampling"])
            if previous==-1: previous={0:3,1:0,2:4,3:4}[int(self.audioOptions["quality"])]
            self._dll.evvSetUpsampleMethod(previous)
            self.setParam(PARAM_SAMPLE_RATE,code)
            raise OpenEvvError("El motor rechazó el método de remuestreo")
        self.audioOptions=dict(options)
        self._configureProcessor()

    def processOutput(self, pcm):
        return stereo(self._processor.process(pcm), int(self.audioOptions.get("balance",0)))

    def refreshOutput(self):
        previous=self.player
        self.player=self._newPlayer(self.sampleRate)
        if previous: previous.close()
'''.replace('    ','\t',1).replace('\n    def','\n\tdef').replace('\n        ','\n\t\t')+engine[position:]
def change(source, before, after, count=1):
    assert source.count(before) == count, f'La base del controlador cambió: {before[:70]}'
    return source.replace(before,after)

driver=change(driver,'from . import _openevv_es as _openevv',
    'from . import _openevv_es as _openevv\nfrom ._openevv_es_text import shorten_pauses')
driver=change(driver,'def flush():','def flush(final=False):')
driver=change(driver,'joined = _openevv.textFor(speaking, "".join(text))',
    'joined = _openevv.textFor(speaking, shorten_pauses("".join(text), self._pauses, final))')
driver=change(driver,'said = self._processText(item)',
    'said = self._processText(item, spelling=spelling, language=speaking)')
driver=change(driver,'text.append(" `p%d " % self._breakToPause(item.time))',
    'text.append(" `p%d " % self._breakToPause(item.time * self._breakPercent / 100))')
driver=change(driver,'elif isinstance(item, PitchCommand):',
    'elif isinstance(item, PitchCommand):\n\t\t\t\tif self._forcePitch: continue')
driver=change(driver,'elif isinstance(item, RateCommand):',
    'elif isinstance(item, RateCommand):\n\t\t\t\tif self._forceRate: continue')
driver=change(driver,'elif isinstance(item, VolumeCommand):',
    'elif isinstance(item, VolumeCommand):\n\t\t\t\tif self._forceVolume: continue')
driver=change(driver,'elif isinstance(item, LangChangeCommand):',
    'elif isinstance(item, LangChangeCommand):\n\t\t\t\tif self._forceLanguage: continue')
driver=change(driver,'\n\t\tflush()\n\t\tif spelling:',
    '\n\t\tflush()\n\t\tif self._pauses and words:\n\t\t\t# A trailing index can flush all text before the final call.\n\t\t\t# Put the final pause after that index, without changing its position.\n\t\t\tlastText = next((x for x in reversed(speechSequence) if isinstance(x,str) and x.strip()), "")\n\t\t\tif lastText.rstrip()[-1:] not in "-,.:;?!–—":\n\t\t\t\tbatch.append((engine.addText,(b" `p100 ",)))\n\t\tif spelling:')
driver=change(driver,'piece.append((engine.synthesize if last else engine.synthesizePart, (expectAudio,)))',
    'if expectAudio:\n\t\t\t\tpiece.insert(0, (engine.addText, (b"`pp1 " if self._phrasePrediction else b"`pp0 ",)))\n\t\t\t\tif not last and self._pauses:\n\t\t\t\t\tpiece.append((engine.addText, (b" `p1 ",)))\n\t\t\tpiece.append((engine.synthesize if last else engine.synthesizePart, (expectAudio,)))')
driver += '\nfrom ._openevv_es_features import install\ninstall(SynthDriver)\n'
engine=change(engine,'channels=1,','channels=2,')
engine=change(engine,'audio = self._processor.process(bytes(self._held))','audio = self.processOutput(bytes(self._held))')
# A piece split out of one utterance retains the EQ and gain state.
engine=change(engine,'\t\tself._processor.reset()\n\t\tself._held = bytearray()',
    '\t\tif not getattr(self, "_continuingAudio", False): self._processor.reset()\n\t\tself._continuingAudio = not last\n\t\tself._held = bytearray()')
engine=change(engine,'\t\tself._discarding = True',
    '\t\tself._continuingAudio = False\n\t\tself._discarding = True',count=engine.count('\t\tself._discarding = True'))
import runpy
responseBuild=runpy.run_path(str(root/'response-build.py'))['apply']
driver=responseBuild('openevv_es.py',driver)
engine=responseBuild('_openevv_es.py',engine)
stabilityBuild=runpy.run_path(str(root/'stability-build.py'))['apply']
driver=stabilityBuild('openevv_es.py',driver)
engine=stabilityBuild('_openevv_es.py',engine)
latestBuild=runpy.run_path(str(root/'latest-build.py'))['apply']
engine=latestBuild('_openevv_es.py',engine)
localizationBuild=runpy.run_path(str(root/'localization-build.py'))['apply']
driver=localizationBuild('openevv_es.py',driver)
engine=localizationBuild('_openevv_es.py',engine)
dictionarySource=root.parent/'native/openevv/nvda/addon/synthDrivers/_openevv_dictionaries.py'
(target/'_openevv_es_dictionaries.py').write_text(dictionarySource.read_text(encoding='utf8'),encoding='utf8')
(target/'openevv_es.py').write_text(driver,encoding='utf8')
(target/'_openevv_es.py').write_text(engine,encoding='utf8')
# Reuse the five complete UI dictionaries maintained for Android.
import json, xml.etree.ElementTree as ET
locales={}
for language in ('en','es','de','fr','it'):
    directory='values' if language=='en' else 'values-'+language
    nodes=ET.parse(root.parent/'app/src/main/res'/directory/'strings.xml').getroot()
    # Android's quote escapes are resource syntax, not text for NVDA to speak.
    locales[language]={node.attrib['name']:(node.text or '').replace("\\'", "'").replace('\\"', '"') for node in nodes if node.tag=='string'}
(target/'openevv_es_ui.json').write_text(json.dumps(locales,ensure_ascii=False),encoding='utf8')
runpy.run_path(str(root/'localization-build.py'))['write_manifests'](root)
print('Controlador derivado preparado.')

