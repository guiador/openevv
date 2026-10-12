"""Port de AudioOptimization.kt y VoiceEqualizer.kt; PCM mono de 16 bits.
No requiere bibliotecas Python externas. Estado conservado entre buffers.
"""
import array
import math
import sys

PRESETS = {
    'neutral': ([0, 0, 0, 0, 0], 0),
    'profunda': ([6, 4, 0, -2, -3], -4),
    'aguda': ([-3, -2, 1, 4, 5], -4),
    'suave': ([2, 1, 0, -4, -6], -2),
    'claridad': ([-4, -2, 2, 4, 1], -3),
}
FREQUENCIES = [120, 350, 1000, 2500, 3500]

def stereo(pcm, balance=0):
    """Balance solo para PCM del complemento, con las ganancias de TtsStereo.kt."""
    balance=max(-100,min(100,int(balance)))
    left=1-balance/100 if balance>0 else 1
    right=1+balance/100 if balance<0 else 1
    source=array.array('h'); source.frombytes(pcm)
    if sys.byteorder!='little': source.byteswap()
    out=array.array('h')
    for sample in source: out.extend((int(sample*left),int(sample*right)))
    if sys.byteorder!='little': out.byteswap()
    return out.tobytes()

class Processor:
    def __init__(self, rate, mode=2, bands=(0,0,0,0,0), preamp=0, light=False):
        self.light = light
        self.rate = rate
        self.mode = mode
        self.target = [0.708,0.891,0.501,1.0][mode]
        self.low = [1.0,1.0,0.25,1.0][mode]
        self.high = [4.0,8.0,2.0,1.0][mode]
        self.top = [0.95,0.98,0.89,1.0][mode]
        self.release = 0.06 if mode==1 else 0.15
        self.blocker = math.exp(-2*math.pi*9/rate)
        self.preamp = 10**(preamp/20)
        self.filters = []
        for freq,db in zip(FREQUENCIES,bands):
            if not db: continue
            a=10**(db/40);w=2*math.pi*min(freq,rate*.45)/rate
            alpha=math.sin(w)/(2*.7);a0=1+alpha/a
            b0=(1+alpha*a)/a0;b1=-2*math.cos(w)/a0;b2=(1-alpha*a)/a0
            a2=(1-alpha/a)/a0
            self.filters.append([b0,b1,b2,b1,a2,0.0,0.0])
        self.reset()

    def reset(self):
        self.x1=self.y1=0.0;self.gain=1.0
        for f in self.filters:f[5]=f[6]=0.0

    def process(self, pcm):
        if self.mode==3 and not self.filters and self.preamp==1:return pcm
        samples=array.array('h');samples.frombytes(pcm)
        if sys.byteorder!='little':samples.byteswap()
        if not samples:return pcm
        if self.mode!=3 and not self.light:
            peak=max(abs(v) for v in samples)/32768
            want=1 if peak<=1e-6 else max(self.low,min(self.high,self.target/peak))
            self.gain=want if want<self.gain else self.gain+(want-self.gain)*(1-math.exp(-len(samples)/self.rate/self.release))
        for i,sample in enumerate(samples):
            v=sample/32768
            if self.mode!=3:
                y=v-self.x1+self.blocker*self.y1;self.x1=v;self.y1=y
                v=y*self.gain
            v*=self.preamp
            for f in self.filters:
                y=f[0]*v+f[5];f[5]=f[1]*v-f[3]*y+f[6];f[6]=f[2]*v-f[4]*y;v=y
            if self.mode!=3 and not self.light:v=self.top*math.tanh(v/self.top)
            samples[i]=max(-32768,min(32767,int(v*32768)))
        if sys.byteorder!='little':samples.byteswap()
        return samples.tobytes()
