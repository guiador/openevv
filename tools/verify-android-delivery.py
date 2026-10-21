"""Verify newly built native libraries, APK contents and 16 KB ELF alignment."""
from pathlib import Path
import hashlib,json,struct,zipfile

root=Path(__file__).resolve().parents[1]
project=root/'free-eloquence-tts-main'
apk=project/'app/build/outputs/apk/principal/debug/app-principal-debug.apk'
revision=(project/'native/openevv-revision.txt').read_text().strip()
abis={'arm64-v8a':183,'armeabi-v7a':40,'x86_64':62}
checks=[]
with zipfile.ZipFile(apk) as package:
    assert package.testzip() is None
    packaged={n.split('/')[1] for n in package.namelist() if n.startswith('lib/') and n.endswith('/libevvjni.so')}
    assert packaged==set(abis),packaged
    for abi,machine in abis.items():
        path=project/'app/build/native/jniLibs'/abi/'libevvjni.so'
        blob=path.read_bytes()
        assert package.read(f'lib/{abi}/libevvjni.so')==blob, f'{abi}: stale APK library'
        # Full native rebuild log is required; equality alone cannot identify an old .so.
        unstripped=project/'native/openevv/build'/f'android-{abi}'/'libevvjni.unstripped.so'
        assert unstripped.is_file() and unstripped.stat().st_mtime> (root/'build/upstream-review-20261012/integration.json').stat().st_mtime
        assert blob[:4]==b'\x7fELF' and blob[5]==1
        assert struct.unpack_from('<H',blob,18)[0]==machine
        is64=blob[4]==2
        phoff=struct.unpack_from('<Q' if is64 else '<I',blob,32 if is64 else 28)[0]
        entsize,count=struct.unpack_from('<HH',blob,54 if is64 else 42)
        loads=0
        for i in range(count):
            at=phoff+i*entsize
            if struct.unpack_from('<I',blob,at)[0]!=1:continue
            alignment=struct.unpack_from('<Q' if is64 else '<I',blob,at+(48 if is64 else 28))[0]
            assert alignment>=16384,(abi,alignment)
            loads+=1
        assert loads
        info=package.getinfo(f'lib/{abi}/libevvjni.so')
        assert info.compress_type==zipfile.ZIP_STORED
        with apk.open('rb') as stream:
            stream.seek(info.header_offset)
            header=stream.read(30)
        name_len,extra_len=struct.unpack_from('<HH',header,26)
        data_at=info.header_offset+30+name_len+extra_len
        assert data_at%16384==0,(abi,data_at)
        checks.append(dict(abi=abi,sha256=hashlib.sha256(blob).hexdigest(),elf_load_alignment=16384,zip_alignment=16384))
report=dict(file=apk.name,version='0.4.4-es',bytes=apk.stat().st_size,sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),native_commit=revision,libraries=checks,validation='APK ZIP integrity, equality with rebuilt libraries, ELF architectures, 16 KB ELF and ZIP alignment; no Android hardware test')
(root/'build/upstream-review-20261012/android-delivery.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(report,ensure_ascii=False,indent=2))
