"""Native regression for the disposable arena at (0,65,0); requires VeilCaptureProbe.

The runner verifies private rig ownership before every RCON command. No rewards or live
server credentials are used. Point --output at the probe's configured capture directory.
"""
from pathlib import Path
import argparse
import re
import subprocess
import sys
import time
import uuid

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--rig',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--player',default='WorldBossTester')
args=parser.parse_args()
assert re.fullmatch(r'[A-Za-z0-9_]{1,16}',args.player),'Invalid fixture player name'
r=Path(__file__).with_name('worldboss_rehearsal.py')
rig=args.rig.resolve()
out=args.output.resolve()
assert out.is_dir(),'The development capture directory must already exist'
for label,position in [('near','40 80 38 133 5'),('far','85 75 40 115 0'),('observer','105 70 0 90 0')]:
 subprocess.run([sys.executable,str(r),'command','--rig',str(rig),'tp '+args.player+' '+position],check=True)
 time.sleep(2)
 name=label+'-'+uuid.uuid4().hex[:8]+'.png'
 (out/'request.txt').write_text(name)
 deadline=time.monotonic()+8
 while time.monotonic()<deadline and not (out/'screenshots'/name).exists():time.sleep(.2)
 assert (out/'screenshots'/name).exists(),'SETUP: native probe did not capture'
 report=out/(name+'.txt')
 report_deadline=time.monotonic()+1
 while not report.exists() and time.monotonic()<report_deadline:time.sleep(.05)
 assert report.exists(),'FAIL: owned showpiece not present on client at '+label+' position; screenshot '+name
 assert 'render=true' in report.read_text(),'FAIL: owned boss culled at '+label+' position'
 print('PASS: '+label+' '+report.read_text(),flush=True)
