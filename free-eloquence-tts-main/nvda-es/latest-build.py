"""Port upstream f8a9e91 dictionaries and per-instance experimental modes."""
import ast
from pathlib import Path

ROOT=Path(__file__).resolve().parent
SOURCE=ROOT.parent/'native/openevv/nvda/addon/synthDrivers/_openevv.py'
upstream=SOURCE.read_text(encoding='utf8')
tree=ast.parse(upstream)
lines=upstream.splitlines(True)
def extract(node):return ''.join(lines[node.lineno-1:node.end_lineno])+'\n'
helpers=''.join(extract(n) for n in tree.body if isinstance(n,(ast.Assign,ast.FunctionDef)) and
    ((isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id.startswith(('DICTIONARY_','PATH_ROOM')) for t in n.targets)) or
     (isinstance(n,ast.FunctionDef) and n.name in ('dictionaryFiles','_stamp','_pathBytes'))))
engineClass=next(n for n in tree.body if isinstance(n,ast.ClassDef) and n.name=='Engine')
methods=''.join(extract(n) for n in engineClass.body if isinstance(n,ast.FunctionDef) and n.name in (
    'setDictionaryFolders','refreshDictionaries','dictionariesChanged','_reloadDictionaries','_dropDictionaries','_useDictionary','_loadVolume','_watchDictionaries'))
def once(text,old,new):
    assert text.count(old)==1,old[:90]
    return text.replace(old,new)

def apply(name,text):
    if name=='_openevv_es.py':
        text=once(text,'import threading\n','import threading\nimport tempfile\n')
        text=once(text,'class Engine:',helpers+'\nclass Engine:')
        text=once(text,'\t\tself.languages = []\n','''		self._dictionaryFolders = ()
		self._dictionaries = {}
		self._dictionaryStamps = {}
		self._dictionaryRefreshing = False
		self._dictionaryLooked = 0.0
		self.languages = []
''')
        text=once(text,'\tdef getVoiceParam(self, which):',methods+'\n\tdef getVoiceParam(self, which):')
        text=once(text,'\t\tself.language = language\n','\t\tself.language = language\n\t\tself._useDictionary(language)\n')
        text=once(text,'\t\twhile not self._stopWatching.wait(WATCH_EVERY):\n','\t\twhile not self._stopWatching.wait(WATCH_EVERY):\n\t\t\tself._watchDictionaries()\n')
        text=once(text,'\t\t\t\tself._dll.eciDelete(self._instance)','\t\t\t\tself._dropDictionaries()\n\t\t\t\tself._dll.eciDelete(self._instance)')
        signatures='''		dll.eciNewDict.restype = ctypes.c_void_p
		dll.eciNewDict.argtypes = [ctypes.c_void_p]
		dll.eciSetDict.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
		dll.eciLoadDict.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_int, ctypes.c_char_p]
		dll.eciDeleteDict.restype = ctypes.c_void_p
		dll.eciDeleteDict.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
'''
        text=once(text,'\t\tdll.eciDelete.restype = ctypes.c_void_p\n',signatures+'\t\tdll.eciDelete.restype = ctypes.c_void_p\n')
    return text

