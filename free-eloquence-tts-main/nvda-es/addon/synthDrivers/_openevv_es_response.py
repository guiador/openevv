"""Experimental response settings, independent of NVDA's WASAPI buffer."""
DEFAULTS = dict(enabled=False, pieceChars=500, sentenceEnds=True,
                frameSamples=2048, feedSamples=2048, hardCut=False, lightOutput=False)
LIMITS = dict(pieceChars=(32, 2000), frameSamples=(128, 4096), feedSamples=(128, 8192))
PROFILES = {
    'Referencia': dict(DEFAULTS, enabled=True),
    'Equilibrado': dict(DEFAULTS, enabled=True, pieceChars=250, frameSamples=1024, feedSamples=1024),
    'Rápido': dict(DEFAULTS, enabled=True, pieceChars=100, frameSamples=512, feedSamples=512),
    'Extremo': dict(DEFAULTS, enabled=True, pieceChars=32, frameSamples=128, feedSamples=128, hardCut=True),
}

def normalize(options):
    result = dict(DEFAULTS)
    for key, default in DEFAULTS.items():
        value = options.get(key, default)
        try:
            result[key] = bool(value) if isinstance(default, bool) else max(LIMITS[key][0], min(LIMITS[key][1], int(value)))
        except (TypeError, ValueError, OverflowError):
            pass
    return result

def effective(options):
    values = normalize(options)
    return values if values['enabled'] else dict(DEFAULTS)

def install(driver):
    from autoSettingsUtils.driverSetting import BooleanDriverSetting, NumericDriverSetting
    original = driver.__init__
    def initialize(self):
        self._responseOptions = dict(DEFAULTS)
        original(self)
    driver.__init__ = initialize
    settings = []
    for name, default in DEFAULTS.items():
        key = '_response_' + name
        if isinstance(default, bool):
            settings.append(BooleanDriverSetting(key, name, False, defaultVal=default))
        else:
            lo, hi = LIMITS[name]
            settings.append(NumericDriverSetting(key, name, False, defaultVal=default, minVal=lo, maxVal=hi))
        def put(self, value, name=name):
            self._responseOptions = normalize(dict(self._responseOptions, **{name: value}))
            self._engine.control([(self._engine.configureResponse, (dict(self._responseOptions),))])
        setattr(driver, key, property(lambda self, name=name: self._responseOptions[name], put))
    driver.supportedSettings += tuple(settings)
