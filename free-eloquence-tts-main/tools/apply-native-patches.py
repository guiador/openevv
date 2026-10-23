"""Check/apply native patches in a nested checkout or a source-only snapshot."""
import argparse
import os
from pathlib import Path
import subprocess

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--engine',type=Path,default=Path(__file__).resolve().parents[1]/'native/openevv')
parser.add_argument('--check-only',action='store_true')
args=parser.parse_args()
engine=args.engine.resolve()
assert (engine/'Makefile').is_file(), 'Missing native engine sources'
# A source snapshot is inside the distribution repository. Prevent Git from
# discovering that parent repository and silently skipping subdirectory paths.
env=dict(os.environ,GIT_CEILING_DIRECTORIES=str(engine.parent))
patches=sorted((Path(__file__).resolve().parents[1]/'native/patches').glob('*.patch'))
for patch in patches:
    command=['git','-C',str(engine),'apply']
    reverse=subprocess.run([*command,'--reverse','--check',str(patch)],env=env,capture_output=True)
    if reverse.returncode==0:continue
    if args.check_only:
        raise SystemExit('Native patch missing or inconsistent: '+patch.name)
    subprocess.run([*command,'--check',str(patch)],env=env,check=True)
    subprocess.run([*command,'--whitespace=nowarn',str(patch)],env=env,check=True)
print(f'PASS: {len(patches)} native patches present and reversible')
