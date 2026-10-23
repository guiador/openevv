"""Controles accesibles equivalentes a Android; persistencia y perfiles de NVDA."""
import globalPluginHandler
import config
import wx
from wx.lib.scrolledpanel import ScrolledPanel
from gui.settingsDialogs import SettingsPanel, NVDASettingsDialog
from synthDriverHandler import getSynth
from synthDrivers._openevv_es_audio import PRESETS
from synthDrivers._openevv_es_ui import translator, currentLocale
from synthDrivers import _openevv_es as native

class OpenEVVPanel(SettingsPanel):
    title='OpenEVV'
    def makeSettings(self, settingsSizer):
        self.synth=getSynth();self.controls={};self.pendingPitch=None;self.pendingInflection=None
        t=translator(native.localeOf(self.synth._home()) if self.synth and self.synth.name=='openevv_es' else currentLocale())
        if not self.synth or self.synth.name!='openevv_es':
            settingsSizer.Add(wx.StaticText(self,label=t('select')),flag=wx.ALL,border=10);return
        notebook=wx.Notebook(self);settingsSizer.Add(notebook,1,wx.EXPAND);pages=[]
        def page(key):
            p=ScrolledPanel(notebook);s=wx.BoxSizer(wx.VERTICAL);p.SetSizer(s);notebook.AddPage(p,t(key));pages.append(p);return p,s
        def note(p,s,key):
            text=wx.StaticText(p,label=t(key));text.Wrap(480);s.Add(text,0,wx.EXPAND|wx.ALL,5)
        def spin(p,s,key,label,lo,hi):
            row=wx.BoxSizer(wx.HORIZONTAL);ctrl=wx.SpinCtrl(p,min=lo,max=hi,initial=int(getattr(self.synth,key)))
            ctrl.SetName(label);row.Add(wx.StaticText(p,label=label),1,wx.ALIGN_CENTER_VERTICAL);row.Add(ctrl,0,wx.LEFT,8)
            s.Add(row,0,wx.EXPAND|wx.ALL,5);self.controls[key]=(ctrl,None)
        def choice(p,s,key,label,values):
            s.Add(wx.StaticText(p,label=label),0,wx.ALL,5);ctrl=wx.Choice(p,choices=[text for value,text in values]);ctrl.SetName(label)
            ids=[str(value) for value,text in values];value=str(getattr(self.synth,key));ctrl.SetSelection(ids.index(value) if value in ids else 0)
            self.controls[key]=(ctrl,ids);s.Add(ctrl,0,wx.EXPAND|wx.ALL,5);return ctrl
        def check(p,s,key,label):
            ctrl=wx.CheckBox(p,label=label);ctrl.SetValue(bool(getattr(self.synth,key)));self.controls[key]=(ctrl,None);s.Add(ctrl,0,wx.ALL,5)
        p,s=page('section_voice');note(p,s,'basic')
        choice(p,s,'_gender',t('gender_label'),[(0,t('gender_male')),(1,t('gender_female'))])
        for key,label in (('_headSize','set_head_size'),('_roughness','set_roughness'),('_breathiness','set_breathiness')):spin(p,s,key,t(label),0,100)
        for label,head,pitch,rough,breath in [('eq_text_13',75,30,15,10),('eq_text_14',30,75,5,10),('eq_text_15',50,50,0,25)]:
            button=wx.Button(p,label=t(label))
            def timbre(event,h=head,tone=pitch,r=rough,b=breath):
                for key,value in (('_headSize',h),('_roughness',r),('_breathiness',b)):self.controls[key][0].SetValue(value)
                self.pendingPitch=tone
            button.Bind(wx.EVT_BUTTON,timbre);s.Add(button,0,wx.ALL,5)
        button=wx.Button(p,label=t('reset_defaults'))
        def resetVoice(event):
            language,number=self.synth._splitVoiceId(self.synth.voice)
            defaults=self.synth._engine.voiceDefaultsByLanguage[language][number]
            for key,param in (('_gender',native.VOICE_GENDER),('_headSize',native.VOICE_HEAD_SIZE),('_roughness',native.VOICE_ROUGHNESS),('_breathiness',native.VOICE_BREATHINESS)):
                ctrl,ids=self.controls[key]
                if ids:ctrl.SetSelection(ids.index(str(defaults[param])))
                else:ctrl.SetValue(defaults[param])
            self.pendingPitch=defaults[native.VOICE_PITCH]
            self.pendingInflection=defaults[native.VOICE_FLUCTUATION]
        button.Bind(wx.EVT_BUTTON,resetVoice);s.Add(button,0,wx.ALL,5)
        note(p,s,'reset_voice_help')
        p,s=page('section_quality')
        choice(p,s,'_samplerate',t('sample_rate_label'),[(r,f'{r} Hz') for r in (8000,11025,16000,22050,24000,32000,44100,48000)])
        sourceMode=choice(p,s,'_synthesisMode',t('synthesis_mode'),[(0,t('synthesis_classic')),(1,t('synthesis_wideband')),(2,t('synthesis_native16'))])
        def selectMode(event):
            mode=int(self.controls['_synthesisMode'][1][sourceMode.GetSelection()])
            rate,ids=self.controls['_samplerate']
            if mode==2:rate.SetSelection(ids.index('16000'))
            elif mode==1 and int(ids[rate.GetSelection()])<=11025:rate.SetSelection(ids.index('22050'))
        sourceMode.Bind(wx.EVT_CHOICE,selectMode)
        note(p,s,'synthesis_mode_help')
        resetSource=wx.Button(p,label=t('reset_defaults'))
        resetSource.Bind(wx.EVT_BUTTON,lambda event:sourceMode.SetSelection(0));s.Add(resetSource,0,wx.ALL,5)
        note(p,s,'synthesis_reset_help')
        quality=choice(p,s,'_quality',t('audio_optimization_label'),[(0,t('audio_optimization_balanced')),(1,t('audio_optimization_volume')),(2,t('audio_optimization_quality')),(3,t('plain'))])
        methods={'4':t('eq_text_20'),'3':t('eq_text_19'),'2':t('eq_text_18'),'0':t('eq_text_17')}
        resampling=choice(p,s,'_resampling',t('eq_text_21'),list(methods.items())+[('-1',t('eq_text_16'))])
        methodText=wx.StaticText(p);s.Add(methodText,0,wx.ALL,5)
        def describe(event=None):
            method=self.controls['_resampling'][1][resampling.GetSelection()]
            if method=='-1':method={0:'3',1:'0',2:'4',3:'4'}[quality.GetSelection()]
            methodText.SetLabel(t('eq_text_21')+': '+methods[method])
            if event:event.Skip()
        quality.Bind(wx.EVT_CHOICE,describe);resampling.Bind(wx.EVT_CHOICE,describe);describe()
        choice(p,s,'_balance',t('balance'),[(-100,t('left')),(0,t('center')),(100,t('right'))])
        ctrl,ids=self.controls['_balance'];current=str(self.synth._balance)
        if current not in ids:
            ctrl.Append(t('left' if int(current)<0 else 'right')+f' ({abs(int(current))})');ids.append(current);ctrl.SetSelection(len(ids)-1)
        note(p,s,'eq_text_22')
        p,s=page('dictionary_page')
        from synthDrivers import _openevv_es_dictionaries as dictionaries
        installed=dictionaries.managedSets()
        values=[('none',t('dictionary_none'))]+[(entry.id,entry.name+' ('+entry.version+')') for entry in sorted(installed.values(),key=lambda entry:entry.name.lower())]
        if self.synth._dictionarySet not in [value for value,label in values]:values.append((self.synth._dictionarySet,self.synth._dictionarySet))
        choice(p,s,'_dictionarySet',t('dictionary_set'),values)
        check(p,s,'_personalDictionary',t('dictionary_personal'))
        note(p,s,'dictionary_help')
        p,s=page('eq_title')
        preset=choice(p,s,'_eqPreset',t('eq_text_10'),[(key,t('eq_text_'+str(i))) for key,i in [('neutral',4),('profunda',5),('aguda',6),('suave',7),('claridad',8),('manual',9)]])
        spin(p,s,'_preamp',t('eq_text_11')+' (dB)',-12,12)
        for i,hz in enumerate((120,350,1000,2500,3500)):spin(p,s,f'_eq{i}',f'{hz} Hz (dB)',-12,12)
        def applyPreset(event):
            key=self.controls['_eqPreset'][1][preset.GetSelection()]
            if key in PRESETS:
                self.applyingPreset=True
                try:
                    bands,pre=PRESETS[key];self.controls['_preamp'][0].SetValue(pre)
                    for i,value in enumerate(bands):self.controls[f'_eq{i}'][0].SetValue(value)
                finally:self.applyingPreset=False
        preset.Bind(wx.EVT_CHOICE,applyPreset)
        button=wx.Button(p,label=t('reset_defaults'))
        def resetEqualizer(event):
            preset.SetSelection(self.controls['_eqPreset'][1].index('neutral'))
            applyPreset(event)
        button.Bind(wx.EVT_BUTTON,resetEqualizer);s.Add(button,0,wx.ALL,5)
        def manual(event):
            if not getattr(self,'applyingPreset',False):preset.SetSelection(self.controls['_eqPreset'][1].index('manual'))
            event.Skip()
        for key,(ctrl,ids) in self.controls.items():
            if key=='_preamp' or key.startswith('_eq') and key!='_eqPreset':ctrl.Bind(wx.EVT_SPINCTRL,manual);ctrl.Bind(wx.EVT_TEXT,manual)
        p,s=page('pauses_label')
        choice(p,s,'_pauses',t('pauses_label'),[(0,t('pauses_keep')),(1,t('pauses_end')),(2,t('pauses_all'))])
        spin(p,s,'_breakPercent',t('break'),0,100)
        for key,label in (('_phrasePrediction','prediction'),('_forceLanguage','force_language_label'),('_forceRate','lockRate'),('_forcePitch','lockPitch'),('_forceVolume','lockVolume')):check(p,s,key,t(label))
        p,s=page('section_text')
        # Use NVDA's own symbol processing, shared with Speech settings and profiles.
        # Keep this outside self.controls: it is not a synthesizer parameter.
        self.oldSymbols=int(config.conf['speech']['symbolLevel'])
        self.symbolIds=[0,100,200,300]
        symbolNames=[t('symbol_mode_'+key) for key in ('none','some','most','all')]
        if self.oldSymbols not in self.symbolIds:
            # Preserve levels from a manually edited or future NVDA profile.
            self.symbolIds.append(self.oldSymbols)
            symbolNames.append(t('symbol_mode_custom')+f' ({self.oldSymbols})')
        symbolLabel=t('symbolPronunciation')
        s.Add(wx.StaticText(p,label=symbolLabel),0,wx.ALL,5)
        self.symbols=wx.Choice(p,choices=symbolNames)
        self.symbols.SetName(symbolLabel)
        self.symbols.SetSelection(self.symbolIds.index(self.oldSymbols))
        s.Add(self.symbols,0,wx.EXPAND|wx.ALL,5)
        note(p,s,'symbolShared')
        choice(p,s,'_numberMode',t('number_mode_label'),[(i,t(key)) for i,key in enumerate(('number_as_is','number_digits','number_pairs','number_triplets'))])
        check(p,s,'_otp',t('otp_label'))
        choice(p,s,'_spellingMode',t('spelling_label'),[(i,t(key)) for i,key in enumerate(('spelling_off','spelling_letters','spelling_nato','spelling_capitals','spelling_capitals_nato'))])
        choice(p,s,'_capitalMode',t('capital_label'),[(i,t(key)) for i,key in enumerate(('capital_none','capital_cap','capital_pitch'))])
        for key,label in (('_stripSsml','ssml_label'),('_textFixes','fixes'),('_expandAbbreviations','abbreviations_label'),('_allowVoiceTags','tags')):check(p,s,key,t(label))
        note(p,s,'nativeText')
        p,s=page('section_code')
        for key,label in (('_codeMode','code_mode_label'),('_camel','code_camel_label'),('_snake','code_snake_label'),('_operators','code_operators_label'),('_braces','code_braces_label'),('_indentation','code_indent_label'),('_paths','code_paths_label'),('_urls','code_urls_label')):check(p,s,key,t(label))
        p,s=page('awake');note(p,s,'awake')
        current=int(config.conf['audio']['audioAwakeTime']);self.oldAwake=current;self.oldNoise=int(config.conf['audio']['whiteNoiseVolume'])
        self.awakeIds=sorted(set((0,10,15,20,30,45,60,90,120,180,240,300,current)))
        self.awake=wx.Choice(p,choices=[t('off') if n==0 else f'{n} s' for n in self.awakeIds]);self.awake.SetName(t('awake'));self.awake.SetSelection(self.awakeIds.index(current));s.Add(self.awake,0,wx.EXPAND|wx.ALL,5)
        self.noise=wx.SpinCtrl(p,min=0,max=100,initial=int(config.conf['audio']['whiteNoiseVolume']));self.noise.SetName(t('noise'))
        s.Add(wx.StaticText(p,label=t('noise')),0,wx.ALL,5);s.Add(self.noise,0,wx.ALL,5)
        from synthDrivers._openevv_es_response import DEFAULTS, PROFILES
        p,s=page('response_title')
        note(p,s,'response_explanation')
        fields=[('enabled',None,None),('pieceChars',32,2000),('sentenceEnds',None,None),
                ('frameSamples',128,4096),('feedSamples',128,8192),('hardCut',None,None),
                ('lightOutput',None,None)]
        for name,lo,hi in fields:
            key='_response_'+name;label=t('response_'+name);warning=t('response_'+name+'_help')
            if lo is None:check(p,s,key,label)
            else:spin(p,s,key,label,lo,hi)
            ctrl=self.controls[key][0];ctrl.SetHelpText(warning);ctrl.SetName(label+'. '+warning)
            description=wx.StaticText(p,label=warning);description.Wrap(480);s.Add(description,0,wx.EXPAND|wx.ALL,5)
        profiles=[('response_reset',DEFAULTS)]+[(key,PROFILES[name]) for key,name in
            [('response_reference','Referencia'),('response_balanced','Equilibrado'),
             ('response_fast','Rápido'),('response_extreme','Extremo')]]
        for key,values in profiles:
            button=wx.Button(p,label=t(key))
            def responsePreset(event,values=values):
                for key,value in values.items():self.controls['_response_'+key][0].SetValue(value)
            button.Bind(wx.EVT_BUTTON,responsePreset);s.Add(button,0,wx.ALL,5)
        theoretical=wx.StaticText(p);s.Add(theoretical,0,wx.EXPAND|wx.ALL,5)
        def updateTheory(event=None):
            hz=int(self.synth._samplerate)
            frame=self.controls['_response_frameSamples'][0].GetValue()
            feed=self.controls['_response_feedSamples'][0].GetValue()
            theoretical.SetLabel(t('response_theory').format(hz=hz,frame=1000*frame/hz,callbacks=hz/frame,feed=1000*feed/hz))
            if event:event.Skip()
        calculate=wx.Button(p,label=t('response_refresh'));calculate.Bind(wx.EVT_BUTTON,updateTheory)
        s.Add(calculate,0,wx.ALL,5);updateTheory()
        p,s=page('section_test')
        self.preview=wx.TextCtrl(p,value=t('preview'),style=wx.TE_MULTILINE);self.preview.SetName(t('section_test'));s.Add(self.preview,1,wx.EXPAND|wx.ALL,5)
        button=wx.Button(p,label=t('test'));s.Add(button,0,wx.ALL,5)
        def preview(event):
            import speech
            if getSynth() is self.synth:speech.speakText(self.preview.GetValue())
        button.Bind(wx.EVT_BUTTON,preview)
        self.exportButton=wx.Button(p,label=t('export'));s.Add(self.exportButton,0,wx.ALL,5)
        self.cancelExport=wx.Button(p,label=t('cancel'));self.cancelExport.Disable();s.Add(self.cancelExport,0,wx.ALL,5)
        def export(event):
            if getSynth() is not self.synth:return
            import threading, speech
            from synthDrivers._openevv_es_export import render
            text=self.preview.GetValue()
            if not text.strip():return
            with wx.FileDialog(self,message=t('export'),wildcard='WAV (*.wav)|*.wav',style=wx.FD_SAVE|wx.FD_OVERWRITE_PROMPT) as dialog:
                if dialog.ShowModal()!=wx.ID_OK:return
                path=dialog.GetPath()
            if not path.lower().endswith('.wav'):path+='.wav'
            language=self.synth._home()
            # This is the same dictionary/symbol processing used by normal NVDA speech.
            from speech.speech import processText
            from synthDrivers._openevv_es_features import before_symbols
            text=before_symbols([text],self.synth)[0]
            text=processText(native.localeOf(language),text,config.conf['speech']['symbolLevel'])
            audio=dict(self.synth._audioOptions,samplerate=int(self.synth._samplerate),synthesisMode=self.synth._synthesisMode)
            audio['dictionaryFolders']=dictionaries.folders(self.synth._dictionarySet,self.synth._personalDictionary)
            params=dict(self.synth._voiceParams);reading=dict(self.synth._readingOptions)
            allow=self.synth._allowVoiceTags;self.exportStop=threading.Event()
            self.exportButton.Disable();self.cancelExport.Enable()
            def finished(message):
                import ui
                if self:
                    self.exportButton.Enable();self.cancelExport.Disable()
                ui.message(message)
            def worker():
                try:render(path,text,language,params,audio,reading,allow,self.exportStop);message=t('exported')+': '+path
                except InterruptedError:message=t('cancelled')
                except Exception as error:message=t('failed')+': '+str(error)
                wx.CallAfter(finished,message)
            threading.Thread(target=worker,name='OpenEVV WAV export',daemon=True).start()
        self.exportButton.Bind(wx.EVT_BUTTON,export)
        self.cancelExport.Bind(wx.EVT_BUTTON,lambda event:self.exportStop.set())
        def destroyed(event):
            if event.GetEventObject() is self and hasattr(self,'exportStop'):self.exportStop.set()
            event.Skip()
        self.Bind(wx.EVT_WINDOW_DESTROY,destroyed)
        p,s=page('about_title')
        note(p,s,'local_credits')
        s.Add(wx.StaticText(p,label=t('upstreamCredits')),0,wx.EXPAND|wx.ALL,5)
        from pathlib import Path
        import re
        manifest=(Path(__file__).resolve().parents[1]/'manifest.ini').read_text(encoding='utf8')
        version=re.search(r'^version\s*=\s*(\S+)',manifest,re.M).group(1)
        s.Add(wx.StaticText(p,label=t('about_version').replace('%1$s',version)),0,wx.ALL,5)
        for panel in pages:panel.SetupScrolling(scroll_x=False)

    def onSave(self):
        if not self.controls or getSynth() is not self.synth:return
        section=config.conf['speech']['openevv_es']
        integers={'_gender','_pauses','_breakPercent','_numberMode','_spellingMode','_capitalMode','_balance','_synthesisMode'}
        for key,(ctrl,ids) in self.controls.items():
            value=ids[ctrl.GetSelection()] if ids else ctrl.GetValue()
            if key in integers:value=int(value)
            setattr(self.synth,key,value);section[key]=value
        if self.pendingPitch is not None:
            self.synth.pitch=self.pendingPitch;section['pitch']=self.pendingPitch;self.pendingPitch=None
        if self.pendingInflection is not None:
            self.synth.inflection=self.pendingInflection;section['inflection']=self.pendingInflection;self.pendingInflection=None
        symbols=self.symbolIds[self.symbols.GetSelection()]
        if symbols!=self.oldSymbols:
            config.conf['speech']['symbolLevel']=symbols;self.oldSymbols=symbols
        awake=self.awakeIds[self.awake.GetSelection()]
        if awake!=self.oldAwake:
            config.conf['audio']['audioAwakeTime']=awake;self.oldAwake=awake
            self.synth._engine.control([(self.synth._engine.refreshOutput,())])
        if self.noise.GetValue()!=self.oldNoise:
            config.conf['audio']['whiteNoiseVolume']=self.noise.GetValue();self.oldNoise=self.noise.GetValue()

class GlobalPlugin(globalPluginHandler.GlobalPlugin):
    def __init__(self,*args,**kwargs):
        super().__init__(*args,**kwargs)
        if OpenEVVPanel not in NVDASettingsDialog.categoryClasses:NVDASettingsDialog.categoryClasses.append(OpenEVVPanel)
        from speech.extensions import filter_speechSequence
        filter_speechSequence.register(self.filterText)
    def filterText(self,value):
        synth=getSynth()
        if not synth or synth.name!='openevv_es':return value
        try:
            from synthDrivers._openevv_es_features import before_symbols
            return before_symbols(value,synth)
        except Exception:
            from logHandler import log
            log.error('OpenEVV: no se pudo preparar el texto antes de símbolos',exc_info=True)
            return value
    def terminate(self):
        from speech.extensions import filter_speechSequence
        filter_speechSequence.unregister(self.filterText)
        if OpenEVVPanel in NVDASettingsDialog.categoryClasses:NVDASettingsDialog.categoryClasses.remove(OpenEVVPanel)
        super().terminate()
