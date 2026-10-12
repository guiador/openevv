"""Reproducible fixes applied after the localized and response generators."""

def replace_once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError('Voice stability source anchor changed: ' + old[:80])
    return text.replace(old, new, 1)

def apply(name, text):
    if name == 'openevv_es.py':
        # NVDA's settings thread must never read the worker's transient state.
        text = text.replace('self._engine.voiceParams', 'self._voiceParams')
        text = replace_once(text, '\t\tself._engine.open()\n',
            '\t\tself._engine.open()\n\t\tself._voiceParams = dict(self._engine.voiceParams)\n')
        text = replace_once(text, '\t\tself._voice = self._voiceId(language, number)\n',
            '\t\tself._voice = self._voiceId(language, number)\n'
            '\t\tself._voiceParams = dict(self._engine.voiceDefaultsByLanguage[language][number])\n')
        text = replace_once(text, 'return _openevv.localeOf(self._engine.language)',
            'return _openevv.localeOf(self._home())')
        for name, param in (('pitch','VOICE_PITCH'),('volume','VOICE_VOLUME')):
            text = replace_once(text, f'\tdef _{name}ToParam(self, percent):\n\t\treturn int(percent)',
                f'\tdef _{name}ToParam(self, percent):\n'
                f'\t\tlo, hi = _openevv.VOICE_RANGE[_openevv.{param}]\n'
                '\t\treturn max(lo, min(hi, int(percent)))')
        text = replace_once(text, 'if language != self._engine.language:\n\t\t\tbatch.append((self._engine.setLanguage, (language,)))',
            'batch.append((self._engine.setLanguage, (language,)))')
    elif name == '_openevv_es.py':
        text = replace_once(text, '\t\tself.voiceNamesByLanguage = {}\n',
            '\t\tself.voiceNamesByLanguage = {}\n\t\tself.voiceDefaultsByLanguage = {}\n')
        text = replace_once(text, '\t\t\tself.voiceNamesByLanguage[language] = self._voiceNames()\n',
            '\t\t\tself.voiceNamesByLanguage[language] = self._voiceNames()\n'
            '\t\t\tself.voiceDefaultsByLanguage[language] = {}\n'
            '\t\t\tfor number in self.voiceNamesByLanguage[language]:\n'
            '\t\t\t\tif not dll.eciCopyVoice(handle, number, 0):\n'
            '\t\t\t\t\traise OpenEvvError("The engine refused a voice preset")\n'
            '\t\t\t\tself.voiceDefaultsByLanguage[language][number] = {\n'
            '\t\t\t\t\twhich: self.getVoiceParam(which) for which in VOICE_RANGE\n'
            '\t\t\t\t}\n')
        text = replace_once(text, '\t\tself.voiceNames = self.voiceNamesByLanguage[self.language]\n\n\t\tself._readVoiceParams()\n',
            '\t\tself.voiceNames = self.voiceNamesByLanguage[self.language]\n'
            '\t\tif not dll.eciCopyVoice(handle, VOICE_FIRST, 0):\n'
            '\t\t\traise OpenEvvError("The engine refused the initial voice")\n'
            '\t\tself._readVoiceParams()\n')
        text = replace_once(text, '\t\t\ttry:\n\t\t\t\tfor fn, args in batch:\n',
            '\t\t\t# Text annotations change the native voice, never user settings.\n'
            '\t\t\t# Restore even if speech is cancelled or a batch raises.\n'
            '\t\t\tkept = dict(self.voiceParams) if kind == SPEECH else None\n'
            '\t\t\ttry:\n\t\t\t\tfor fn, args in batch:\n')
        text = replace_once(text, '\t\t\t\tlog.error("openevv: a call into the engine failed", exc_info=True)\n\t\t\tself._work.task_done()\n',
            '\t\t\t\tlog.error("openevv: a call into the engine failed", exc_info=True)\n'
            '\t\t\tfinally:\n'
            '\t\t\t\ttry:\n'
            '\t\t\t\t\tif kept is not None:\n'
            '\t\t\t\t\t\tfor which, value in kept.items():\n'
            '\t\t\t\t\t\t\tself._dll.eciSetVoiceParam(self._instance, 0, which, value)\n'
            '\t\t\t\texcept Exception:\n'
            '\t\t\t\t\tlog.error("openevv: could not restore voice parameters", exc_info=True)\n'
            '\t\t\t\tfinally:\n'
            '\t\t\t\t\tself._work.task_done()\n')
    else:
        raise ValueError(name)
    return text
