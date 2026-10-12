"""Isolated process reference for upstream's direct 16 kHz environment mode."""
from pathlib import Path
import hashlib
source=Path(__file__).with_name('voice-stability.py').read_text(encoding='utf8')
# Reuse only the NVDA stand-ins and actual worker loader, without its tests.
exec(compile(source.split('\nchecks=[]\n')[0],str(__file__),'exec'))
class Capture(Player):
    def __init__(self,**kw):super().__init__(**kw);self.pcm=bytearray()
    def feed(self,pcm,onDone=None):self.pcm.extend(pcm);super().feed(pcm,onDone)
native.nvwave.WavePlayer=Capture
engine=native.Engine(lambda index:None);engine.open()
try:
    engine.control([(engine.setSampleRate,(16000,))]);barrier(engine)
    engine.post([(engine.addText,(b'Una prueba del motor.',)),(engine.synthesize,())]);barrier(engine)
    assert engine.player.pcm
    print(hashlib.sha256(engine.player.pcm).hexdigest())
finally:engine.close()
