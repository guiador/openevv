from pathlib import Path
import subprocess, ast, json, io, zipfile
ROOT=Path(__file__).resolve().parents[1]
ENGINE=ROOT/'free-eloquence-tts-main/native/openevv'
REV='a25d0ee67dd6470198f766995361eabc84e351a8'
norm=lambda data:data.replace(b'\r\n',b'\n')
git=lambda *args:subprocess.check_output(['git','-C',str(ENGINE),*args])
assert git('rev-parse','HEAD').decode().strip()==REV
allowed={'src/eci/api/eci_env.c','src/eci/api/eci_state.c','src/eci/synth/eci_synthrun.c','src/eci/synth/eci_synthwork.c'}
tracked=git('ls-tree','-r','--name-only',REV).decode().splitlines()
archive=zipfile.ZipFile(io.BytesIO(git('archive','--format=zip',REV)))
assert norm((ROOT/'.gitignore').read_bytes()).startswith(norm(archive.read('.gitignore'))), 'Root ignore rules removed upstream entries'
for target,exceptions in ((ROOT,allowed|{'.gitignore'}),(ENGINE,allowed|{'src/eci/sound/eci_pcm.c'})):
    differences=[]
    for name in tracked:
        path=target/name
        expected=archive.read(name)
        if not path.exists():
            differences.append(name)
            continue
        actual=norm(path.read_bytes())
        # Accept only the documented handoff link, keeping upstream README checks strict.
        continuity=b'[Continuidad, construcci\xc3\xb3n y pendientes para otros mantenedores](CONTINUIDAD.md).'
        if target==ROOT and name=='README.md' and actual.rstrip()==norm(expected).rstrip()+b'\n\n'+continuity:
            actual=norm(expected)
        if actual!=norm(expected):differences.append(name)
    assert set(differences)==exceptions, (str(target),differences)
    print('PASS:',target.name,'all tracked upstream files match except documented local patches',flush=True)
for patch in sorted((ENGINE.parent/'patches').glob('*.patch')):
    subprocess.run(['git','-C',str(ENGINE),'apply','--reverse','--check',str(patch)],check=True)
print('PASS: all three local patches remain reproducible')
for path in (ROOT/'free-eloquence-tts-main/nvda-es').rglob('*.py'):
    ast.parse(path.read_text(encoding='utf-8-sig'),str(path))
for path in (ENGINE/'tools').rglob('*.py'):
    ast.parse(path.read_text(encoding='utf-8-sig'),str(path))
print('PASS: Python syntax for the localized addon and updated engine tools')
report=dict(native_commit=REV,upstream_files=len(tracked),source_verified=True,local_patches=3,python_syntax=True,binary_validation=False)
output=ROOT/'build/upstream-review-20261012/source-verification.json'
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(report,indent=2),encoding='utf8')
