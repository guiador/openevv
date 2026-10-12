"""Extensiones persistentes del controlador; prepare.py las incorpora al generar."""
from autoSettingsUtils.driverSetting import BooleanDriverSetting, DriverSetting, NumericDriverSetting
from ._openevv_es_text import DEFAULTS, apply

def before_symbols(sequence,synth):
    """Conserva operadores, SSML y contexto OTP antes de que NVDA lea símbolos.

    Sólo las transformaciones sin anotaciones se anticipan. Deletreo,
    mayúsculas y pausas siguen junto a los comandos de la secuencia final.
    """
    from speech.commands import CharacterModeCommand
    options=dict(synth._readingOptions,capitalMode=0,spellingMode=0)
    result=[];character=False
    for item in sequence:
        if isinstance(item,CharacterModeCommand):character=item.state
        if isinstance(item,str) and not character:
            item=apply(item,options)
        result.append(item)
    return result

def install(driver):
    original_init=driver.__init__
    original_process=driver._processText
    def initialize(self):
        self._readingOptions=dict(DEFAULTS)
        self._sourceMode=0
        self._dictSet='none'
        self._dictPersonal=True
        original_init(self)
        self._audioOptions["balance"]=0
        self._applyDictionaries()
        self._engine.control([(self._engine.setParam,(33,self._readingOptions['pauses']))])
    driver.__init__=initialize

    def process(self,text,spelling=False,language=None):
        raw=original_process(self,text)
        try:return apply(raw,dict(self._readingOptions),spelling)
        except Exception:
            from logHandler import log
            log.error('OpenEVV: no se pudo transformar el texto; se conserva la lectura',exc_info=True)
            return raw
    driver._processText=process

    settings=[]
    for name,default in DEFAULTS.items():
        key="_"+name
        if isinstance(default,bool):
            settings.append(BooleanDriverSetting(key,name,defaultVal=default))
        else:
            maximum=100 if name=="breakPercent" else 4 if name=="spellingMode" else 3 if name=="numberMode" else 2
            settings.append(NumericDriverSetting(key,name,defaultVal=default,minVal=0,maxVal=maximum))
        def put(self,value,n=name,d=default):
            if isinstance(d,bool): value=bool(value)
            else:
                limit=100 if n=="breakPercent" else 4 if n=="spellingMode" else 3 if n=="numberMode" else 2
                value=max(0,min(limit,int(value)))
            self._readingOptions[n]=value
            if n=='pauses':self._engine.control([(self._engine.setParam,(33,value))])
        setattr(driver,key,property(lambda self,n=name:self._readingOptions[n],put))
    settings.append(NumericDriverSetting("_balance","Balance",defaultVal=0,minVal=-100,maxVal=100))
    def balance(self,value):
        self._audioOptions["balance"]=max(-100,min(100,int(value)))
        self._engine.control([(self._engine.configureAudio,(dict(self._audioOptions),))])
    driver._balance=property(lambda self:self._audioOptions.get("balance",0),balance)
    settings.extend((
        NumericDriverSetting('_synthesisMode','Modo de síntesis',defaultVal=0,minVal=0,maxVal=2),
        DriverSetting('_dictionarySet','Conjunto de diccionarios',defaultVal='none'),
        BooleanDriverSetting('_personalDictionary','Usar entradas personales',defaultVal=True),
    ))
    def sourceMode(self,value):
        self._sourceMode=max(0,min(2,int(value)))
        self._engine.control([(self._engine.setParam,(32,self._sourceMode))])
    driver._synthesisMode=property(lambda self:self._sourceMode,sourceMode)
    def applyDictionaries(self):
        from . import _openevv_es_dictionaries as dictionaries
        from logHandler import log
        try: folders=dictionaries.folders(self._dictSet,self._dictPersonal)
        except Exception:
            log.error('OpenEVV: no se pudieron localizar los diccionarios',exc_info=True)
            return
        self._engine.control([(self._engine.setDictionaryFolders,(folders,))])
    driver._applyDictionaries=applyDictionaries
    def dictSet(self,value):
        self._dictSet=str(value or 'none');self._applyDictionaries()
    def dictPersonal(self,value):
        self._dictPersonal=bool(value);self._applyDictionaries()
    driver._dictionarySet=property(lambda self:self._dictSet,dictSet)
    driver._personalDictionary=property(lambda self:self._dictPersonal,dictPersonal)
    driver.supportedSettings=driver.supportedSettings+tuple(settings)

