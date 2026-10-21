"""Integrate reviewed upstream files without overwriting local modifications."""
from pathlib import Path
import subprocess, json, shutil

ROOT=Path(__file__).resolve().parents[1]
PROJECT=ROOT/'free-eloquence-tts-main'
ENGINE=PROJECT/'native/openevv'
OLD='f8a9e91221a15be68eb39845313186c683c1b90d'
NEW='a25d0ee67dd6470198f766995361eabc84e351a8'
BACKUP=ROOT/'build/upstream-review-20261012'
def git(*args):return subprocess.check_output(['git','-C',str(ENGINE),*args])
assert git('rev-parse','HEAD').decode().strip()==OLD
assert git('rev-parse','origin/main').decode().strip()==NEW
changes=[line.split('\t') for line in git('diff','--name-status',OLD,NEW).decode().splitlines()]
assert all(status in ('A','M') for status,path in changes)
norm=lambda data:data.replace(b'\r\n',b'\n')
for status,name in changes:
    target=ROOT/name
    if status=='M':assert target.is_file() and norm(target.read_bytes())==norm(git('show',OLD+':'+name)), 'Local root change: '+name
    else:assert not target.exists(), 'New root file already exists: '+name
for patch in sorted((PROJECT/'native/patches').glob('*.patch')):
    subprocess.run(['git','-C',str(ENGINE),'apply','--reverse','--check',str(patch)],check=True)
BACKUP.mkdir(parents=True,exist_ok=False)
(BACKUP/'native-local.patch').write_bytes(git('diff'))
for status,name in changes:
    target=ROOT/name
    if target.exists():
        saved=BACKUP/'root-before'/name;saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(target,saved)
subprocess.run(['git','-C',str(ENGINE),'merge','--ff-only',NEW],check=True)
for patch in sorted((PROJECT/'native/patches').glob('*.patch')):
    subprocess.run(['git','-C',str(ENGINE),'apply','--reverse','--check',str(patch)],check=True)
for status,name in changes:
    target=ROOT/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(git('show',NEW+':'+name))
(PROJECT/'native/openevv-revision.txt').write_text(NEW+'\n',encoding='utf8')
(BACKUP/'integration.json').write_text(json.dumps(dict(previous=OLD,current=NEW,upstream_files=len(changes),local_patches_preserved=True,files=changes),indent=2),encoding='utf8')
print('PASS: updated both source copies; all three local patches preserved; '+str(len(changes))+' upstream files integrated.')
