from pathlib import Path
import subprocess, ast, json, io, zipfile, hashlib, os
ROOT=Path(__file__).resolve().parents[1]
ENGINE=ROOT/'free-eloquence-tts-main/native/openevv'
REV='a25d0ee67dd6470198f766995361eabc84e351a8'
norm=lambda data:data.replace(b'\r\n',b'\n')
git=lambda *args:subprocess.check_output(['git','-C',str(ENGINE),*args])
manifest=json.loads((ENGINE.parent/'openevv-upstream-manifest.json').read_text(encoding='utf8'))
assert manifest['native_commit']==REV
assert (ENGINE.parent/'openevv-revision.txt').read_text(encoding='utf8').strip()==REV
if (ENGINE/'.git').exists():
    assert git('rev-parse','HEAD').decode().strip()==REV
    archive=zipfile.ZipFile(io.BytesIO(git('archive','--format=zip',REV)))
    expected={name for name in archive.namelist() if not name.endswith('/')}
    assert expected==manifest['files'].keys()
    for name,info in manifest['files'].items():
        assert hashlib.sha256(norm(archive.read(name))).hexdigest()==info['sha256'],name
allowed={'src/eci/api/eci_env.c','src/eci/api/eci_state.c','src/eci/synth/eci_synthrun.c','src/eci/synth/eci_synthwork.c'}
release_safety={'src/delta/delta_rules.c','src/delta/delta_rules_c.h',
    'rom/jajp/dictsearch.c',
    'src/eci/bridge/eci_deltadict.c',
    'src/port/port_win32.c','src/port/port_posix.c',
    'src/eci/ssml/eci_mbconvert.c','src/eci/ssml/eci_parserxml.c',
    'src/eci/dict/eci_dict.c','src/klatt/klatt_run.c'}
allowed |= release_safety | {'src/eci/sound/eci_pcm.c'}
tracked=list(manifest['files'])
ignore=manifest['files']['.gitignore']
assert hashlib.sha256(norm((ROOT/'.gitignore').read_bytes())[:ignore['normalized_bytes']]).hexdigest()==ignore['sha256'], 'Root ignore rules removed upstream entries'
for target,exceptions in ((ROOT,allowed|{'.gitignore'}),(ENGINE,allowed)):
    differences=[]
    for name in tracked:
        path=target/name
        expected=manifest['files'][name]
        if not path.exists():
            differences.append(name)
            continue
        actual=norm(path.read_bytes())
        # Accept only the documented handoff link, keeping upstream README checks strict.
        continuity=b'[Continuidad, construcci\xc3\xb3n y pendientes para otros mantenedores](CONTINUIDAD.md).'
        if target==ROOT and name=='README.md':
            prefix=actual[:expected['normalized_bytes']]
            suffix=actual[expected['normalized_bytes']:].strip()
            local=suffix==continuity or (suffix.startswith(continuity+b'\n\n<!-- downstream-distributions -->') and suffix.endswith(b'<!-- /downstream-distributions -->'))
            if hashlib.sha256(prefix).hexdigest()==expected['sha256'] and local:
                continue
        if hashlib.sha256(actual).hexdigest()!=expected['sha256']:differences.append(name)
    assert set(differences)==exceptions, (str(target),differences)
    print('PASS:',target.name,'all tracked upstream files match except documented local patches',flush=True)
patches=sorted((ENGINE.parent/'patches').glob('*.patch'))
for patch in patches:
    subprocess.run(['git','-C',str(ENGINE),'apply','--reverse','--check',str(patch)],env=dict(os.environ,GIT_CEILING_DIRECTORIES=str(ENGINE.parent)),check=True)
print(f'PASS: all {len(patches)} local patches remain reproducible')
# Safety fixes must stay identical in the independently built engine and the
# pinned Android/NVDA copy; explicit exceptions above are not a content check.
for name in release_safety:
    assert norm((ROOT/name).read_bytes())==norm((ENGINE/name).read_bytes()), name
resampler='src/eci/sound/eci_pcm.c'
# The app also has the existing UI override below AudioConverter; the shared
# resampler implementation above that boundary must match byte for byte.
assert norm((ROOT/resampler).read_bytes()).split(b'typedef struct AudioConverter')[0] == norm((ENGINE/resampler).read_bytes()).split(b'typedef struct AudioConverter')[0]
print('PASS: shared safety fixes match in both engine trees')
for path in (ROOT/'free-eloquence-tts-main/nvda-es').rglob('*.py'):
    ast.parse(path.read_text(encoding='utf-8-sig'),str(path))
for path in (ENGINE/'tools').rglob('*.py'):
    ast.parse(path.read_text(encoding='utf-8-sig'),str(path))
print('PASS: Python syntax for the localized addon and updated engine tools')
report=dict(native_commit=REV,upstream_files=len(tracked),source_verified=True,local_patches=len(patches),python_syntax=True,binary_validation=False)
output=ROOT/'build/upstream-review-20261012/source-verification.json'
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(report,indent=2),encoding='utf8')
