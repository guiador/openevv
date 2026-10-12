"""Build offline emoji TTS names from pinned Unicode/CLDR data. No third-party packages."""
from pathlib import Path
import urllib.request, json, hashlib, re
ROOT=Path(__file__).resolve().parents[1]
VERSION="48.2.3"
UNICODE="17.0.0"
SOURCE=ROOT/"unicode"/f"cldr-{VERSION}"
ASSETS=ROOT/"app/src/main/assets/unicode"
SOURCE.mkdir(parents=True,exist_ok=True)
ASSETS.mkdir(parents=True,exist_ok=True)
provenance=[]
def fetch(url,name):
    data=urllib.request.urlopen(urllib.request.Request(url,headers={"User-Agent":"OpenEVV-unicode-import"}),timeout=60).read()
    (SOURCE/name).write_bytes(data)
    provenance.append({"file":name,"url":url,"sha256":hashlib.sha256(data).hexdigest()})
    return data
base=f"https://raw.githubusercontent.com/unicode-org/cldr-json/{VERSION}/"
emoji=fetch(f"https://www.unicode.org/Public/{UNICODE}/emoji/emoji-test.txt","emoji-test.txt").decode("utf-8")
def normalized(s): return s.replace("\ufe0f","").replace("\ufe0e","")
allowed=set()
for line in emoji.splitlines():
    if not line or line.startswith("#"): continue
    codes=line.split(";",1)[0].strip()
    if re.fullmatch(r"[0-9A-F ]+",codes): allowed.add(normalized("".join(chr(int(x,16)) for x in codes.split())))
counts={}
base_names={}
built_assets={}
for locale in ("es","es-419"):
    names=dict(base_names) if locale=="es-419" else {}
    # es-419 inherits es; apply its explicit regional overrides after the base.
    for kind in ("annotations","annotationsDerived"):
        package="cldr-annotations-full" if kind=="annotations" else "cldr-annotations-derived-full"
        filename=f"{locale}-{kind}.json"
        raw=fetch(base+f"cldr-json/{package}/{kind}/{locale}/annotations.json",filename)
        entries=json.loads(raw)[kind]["annotations"]
        for key,item in entries.items():
            key=normalized(key)
            values=item.get("tts",[])
            if key in allowed and values and values[0] not in ("↑↑↑","∅∅∅"):
                names[key]=values[0]
    if set(names)!=allowed: raise RuntimeError(f"Incomplete {locale}: {len(allowed-set(names))} missing sequences")
    built_assets[f"{locale}.json"]=json.dumps(names,ensure_ascii=False,separators=(",",":")).encode("utf-8")
    counts[locale]=len(names)
    if locale=="es": base_names=dict(names)
license_data=fetch(base+"LICENSE","LICENSE.txt")
built_assets["LICENSE.txt"]=license_data
for name,data in built_assets.items():
    temporary=ASSETS/(name+".tmp")
    temporary.write_bytes(data)
    temporary.replace(ASSETS/name)
manifest={"cldr":VERSION,"unicode":UNICODE,"entries":counts,"unicodeSequences":len(allowed),"sources":provenance}
(SOURCE/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
(ASSETS/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
print(json.dumps(manifest,ensure_ascii=False,indent=2))
