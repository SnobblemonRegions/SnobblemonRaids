"""Native narrow-GUI layout check in the owned development client; restore its window afterward.

Requires VeilCaptureProbe and a reward-free TEST scene. The 50-fighter/8k/legacy
caption is a client presentation fixture, not a server capacity or combat test.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rig',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--player',default='WorldBossTester')
    args=parser.parse_args();out=args.output.resolve();assert out.is_dir()
    assert re.fullmatch(r'[A-Za-z0-9_]{1,16}',args.player)
    runner=Path(__file__).with_name('worldboss_rehearsal.py')
    response=subprocess.check_output([sys.executable,str(runner),'command','--rig',str(args.rig.resolve()),'list'],text=True)
    assert args.player in response,'Private client must be connected'
    captures=[]
    def capture(label,expected_width=None):
        name=label+'-'+uuid.uuid4().hex[:8]+'.png';(out/'request.txt').write_text(name)
        report=out/(name+'.txt');deadline=time.monotonic()+10
        while time.monotonic()<deadline and not(report.exists() and (out/'screenshots'/name).exists()):time.sleep(.05)
        assert report.exists() and (out/'screenshots'/name).exists(),'Viewport capture timeout'
        text=report.read_text();match=re.search(r'guiWidth=(\d+)',text);assert match,text
        assert re.search(r'hudOwner=[0-9a-f-]{36}',text),text
        if expected_width:assert int(match[1])==expected_width,text
        captures.append({'label':label,'image':name,'report':text})
        print('PASS: '+label+' guiWidth='+match[1],flush=True)
        return int(match[1])
    original=capture('rift-before-resize')
    try:
        (out/'hud-mode.txt').write_text('crowded')
        (out/'hud-command.txt').write_text('progress:0.63');time.sleep(.8)
        (out/'hud-command.txt').write_text('viewport:small');time.sleep(1.5)
        capture('rift-small-crowded',320)
        (out/'hud-mode.txt').write_text('')
        (out/'hud-command.txt').write_text('progress:1');time.sleep(1.5)
        capture('rift-small-gathering',320)
        (out/'hud-command.txt').write_text('extra:on');time.sleep(.8)
        capture('rift-small-coexistence',320)
    finally:
        (out/'hud-mode.txt').write_text('')
        (out/'hud-command.txt').write_text('extra:off');time.sleep(.7)
        (out/'hud-command.txt').write_text('viewport:restore');time.sleep(1.5)
        (out/'hud-command.txt').write_text('progress:1');time.sleep(.8)
    capture('rift-restored-viewport',original)
    (out/'viewport-native-result.json').write_text(json.dumps({'captures':captures},indent=2)+'\n')
    print('PASS: native minimum GUI, legacy/crowded caption and original viewport restoration')


if __name__=='__main__':
    main()
