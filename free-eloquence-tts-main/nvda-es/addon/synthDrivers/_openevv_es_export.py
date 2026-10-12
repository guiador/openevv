"""Exportación WAV independiente; nunca usa ni bloquea el reproductor de NVDA."""
import ctypes, os, re, shutil, tempfile, wave
from contextlib import ExitStack
from pathlib import Path
from . import _openevv_es as native
from ._openevv_es_audio import Processor, stereo
from ._openevv_es_text import apply, shorten_pauses

def render(path,text,language,voiceParams,audioOptions,readingOptions,allowTags=False,cancel=None):
    """Una copia privada de la DLL aísla sus opciones globales de la voz activa.

    El llamador debe usar un hilo de trabajo. El destino se reemplaza sólo
    después de una síntesis completa, y la DLL se libera tras eciDelete.
    """
    if not text.strip():raise ValueError('No hay texto')
    if len(text)>100000:raise ValueError('Máximo: 100000 caracteres por exportación')
    target=Path(path);fd,temporary=tempfile.mkstemp(suffix='.wav',dir=target.parent);os.close(fd)
    dll=None;handle=None;frames=0;dictionary=None
    try:
        with ExitStack() as cleanup:
            directory=cleanup.enter_context(tempfile.TemporaryDirectory(prefix='openevv-wav-'))
            private=Path(directory)/'eci.dll';shutil.copyfile(native.libraryPath(),private)
            dll=ctypes.WinDLL(str(private))
            def release():
                nonlocal dll,handle,dictionary
                if dll:
                    if dictionary:dictionary._dropDictionaries();dictionary=None
                    if handle:dll.eciDelete(handle);handle=None
                    arena=None
                    if hasattr(dll,'evvGetExportArenaBase'):
                        dll.evvGetExportArenaBase.argtypes=[];dll.evvGetExportArenaBase.restype=ctypes.c_void_p
                        arena=dll.evvGetExportArenaBase()
                    import _ctypes
                    _ctypes.FreeLibrary(dll._handle);dll=None
                    # The native CRT/TLS destructors use arena pointers during
                    # unload, so release its Windows mapping only afterwards.
                    if arena:
                        free=ctypes.WinDLL('kernel32',use_last_error=True).VirtualFree
                        free.argtypes=[ctypes.c_void_p,ctypes.c_size_t,ctypes.c_ulong];free.restype=ctypes.c_int
                        if not free(arena,0,0x8000):raise ctypes.WinError(ctypes.get_last_error())
            cleanup.callback(release)
            ptr=ctypes.c_void_p;integer=ctypes.c_int
            signatures={'eciNewEx':[integer],'eciDelete':[ptr],'eciSetParam':[ptr,integer,integer],
                'eciNewDict':[ptr],'eciSetDict':[ptr,ptr],'eciDeleteDict':[ptr,ptr],'eciLoadDict':[ptr,ptr,integer,ctypes.c_char_p],
                'eciSetVoiceParam':[ptr,integer,integer,integer],'eciRegisterCallback':[ptr,ptr,ptr],
                'eciSetOutputBuffer':[ptr,integer,ptr],'eciAddText':[ptr,ctypes.c_char_p],
                'eciSynthesize':[ptr],'eciSynchronize':[ptr],'evvSetUpsampleMethod':[integer]}
            for name,args in signatures.items():getattr(dll,name).argtypes=args
            dll.eciNewEx.restype=ptr;dll.eciDelete.restype=ptr;dll.evvSetUpsampleMethod.restype=None
            dll.eciNewDict.restype=ptr;dll.eciDeleteDict.restype=ptr
            method=int(audioOptions['resampling']);quality=int(audioOptions['quality'])
            if method==-1:method={0:3,1:0,2:4,3:4}[quality]
            dll.evvSetUpsampleMethod(method)
            handle=dll.eciNewEx(language)
            if not handle:raise native.OpenEvvError('No se pudo abrir el motor de exportación')
            if dll.eciSetParam(handle,32,int(audioOptions.get('synthesisMode',0)))<0:raise native.OpenEvvError('Modo de síntesis rechazado')
            dll.eciSetParam(handle,33,int(readingOptions['pauses']))
            dictionary=native.Engine.__new__(native.Engine)
            dictionary._dll=dll;dictionary._instance=handle;dictionary.language=language
            dictionary._dictionaryFolders=();dictionary._dictionaries={};dictionary._dictionaryStamps={}
            dictionary.setDictionaryFolders(audioOptions.get('dictionaryFolders',()))
            rate=int(audioOptions['samplerate']);code=next(code for code,hz in native.SAMPLE_RATES if hz==rate)
            if dll.eciSetParam(handle,native.PARAM_SAMPLE_RATE,code)<0:raise native.OpenEvvError('Frecuencia rechazada')
            dll.eciSetParam(handle,native.PARAM_INPUT_TYPE,1)
            for param,value in voiceParams.items():dll.eciSetVoiceParam(handle,0,int(param),int(value))
            processor=Processor(rate,quality,[int(audioOptions['eq%d'%i]) for i in range(5)],int(audioOptions['preamp']))
            buffer=(ctypes.c_short*native.FRAME)();errors=[]
            try:
                with wave.open(temporary,'wb') as wav:
                    wav.setnchannels(2);wav.setsampwidth(2);wav.setframerate(rate)
                    def callback(h,message,count,data):
                        nonlocal frames
                        if message==native.MSG_WAVEFORM:
                            try:
                                pcm=stereo(processor.process(bytes(buffer)[:count*2]),int(audioOptions.get('balance',0)))
                                wav.writeframesraw(pcm);frames+=count
                            except Exception as error:errors.append(error)
                        return native.DATA_PROCESSED
                    pin=native._CALLBACK(callback)
                    dll.eciRegisterCallback(handle,pin,None);dll.eciSetOutputBuffer(handle,native.FRAME,buffer)
                    if not allowTags:text=text.replace('`',' ')
                    # Bound native work and provide cancellation between pieces.
                    pieces=[];pending=''
                    for word in re.findall(r'\S+\s*|\s+',text):
                        if pending and len(pending)+len(word)>512:pieces.append(pending);pending=''
                        while len(word)>512:pieces.append(pending+word[:512]);pending='';word=word[512:]
                        pending+=word
                    if pending:pieces.append(pending)
                    for i,piece in enumerate(pieces):
                        if cancel and cancel.is_set():raise InterruptedError('Exportación cancelada')
                        cooked=shorten_pauses(apply(piece,readingOptions),int(readingOptions['pauses']),i==len(pieces)-1)
                        if i<len(pieces)-1 and readingOptions['pauses']:cooked+=' `p1 '
                        cooked=('`pp1 ' if readingOptions['phrasePrediction'] else '`pp0 ')+cooked
                        if not dll.eciAddText(handle,native.textFor(language,cooked)):raise native.OpenEvvError('Texto rechazado')
                        if not dll.eciSynthesize(handle):raise native.OpenEvvError('Síntesis rechazada')
                        dll.eciSynchronize(handle)
                        if errors:raise errors[0]
                    if cancel and cancel.is_set():raise InterruptedError('Exportación cancelada')
                    if not frames:raise native.OpenEvvError('El motor no produjo audio')
            finally:
                if dictionary:dictionary._dropDictionaries();dictionary=None
                dll.eciDelete(handle);handle=None
                # All callbacks have completed synchronously before unloading.
                release()
        os.replace(temporary,target)
        return frames
    finally:
        if dictionary:dictionary._dropDictionaries();dictionary=None
        if handle and dll:dll.eciDelete(handle)
        if dll:
            release()
        if os.path.exists(temporary):os.unlink(temporary)
