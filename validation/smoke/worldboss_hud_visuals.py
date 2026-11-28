"""Capture HUD presentation states in an owned disposable native client.

Requires VeilCaptureProbe and an active, reward-free TEST scene. Combat health here
is a client presentation fixture, not evidence about server damage or rewards.
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
    args=parser.parse_args()
    assert re.fullmatch(r'[A-Za-z0-9_]{1,16}',args.player)
    out=args.output.resolve(); assert out.is_dir()
    runner=Path(__file__).with_name('worldboss_rehearsal.py')
    def command(text):
        return subprocess.check_output([sys.executable,str(runner),'command','--rig',str(args.rig.resolve()),text],text=True)
    assert args.player in command('list'),'Fixture player not connected'
    captures=[]
    def capture(label,owned=True):
        name=label+'-'+uuid.uuid4().hex[:8]+'.png'
        (out/'request.txt').write_text(name)
        report=out/(name+'.txt'); deadline=time.monotonic()+10
        while time.monotonic()<deadline and not (report.exists() and (out/'screenshots'/name).exists()): time.sleep(.05)
        assert report.exists() and (out/'screenshots'/name).exists(),'Capture timeout: '+label
        text=report.read_text()
        match=re.search(r'hudOwner=([0-9a-f-]{36}|null)',text); assert match,text
        assert (match[1]!='null')==owned,'Unexpected ownership: '+text
        captures.append({'label':label,'image':name,'report':text})
        print('PASS: '+label+' owned='+str(owned),flush=True)
    try:
        print(command('execute as '+args.player+' at @s run cr worldboss scene effects full'),end='')
        (out/'hud-mode.txt').write_text(''); (out/'hud-command.txt').write_text('progress:1')
        time.sleep(1.2); capture('hud-gathering')
        (out/'hud-mode.txt').write_text('combat'); time.sleep(.6); capture('hud-full-health')
        # Commands are polled at 2 Hz; allow that poll and the native 100 ms lerp
        # before requesting a render-target capture (which contains the prior frame).
        (out/'hud-command.txt').write_text('progress:0.63'); time.sleep(.8); capture('hud-damage-echo')
        (out/'hud-command.txt').write_text('progress:0.16'); time.sleep(1); capture('hud-critical')
        (out/'hud-command.txt').write_text('progress:0'); time.sleep(1.2); capture('hud-empty')
        (out/'hud-command.txt').write_text('progress:0.16'); time.sleep(.8)
        (out/'hud-command.txt').write_text('extra:on'); time.sleep(.8); capture('hud-coexistence')
        (out/'hud-mode.txt').write_text('stale'); time.sleep(.8); capture('hud-native-fallback',False)
        (out/'hud-mode.txt').write_text('combat');time.sleep(1.3)
        print(command('execute as '+args.player+' at @s run cr worldboss scene effects off'),end='')
        time.sleep(1.3);capture('hud-effects-off')
    finally:
        (out/'hud-mode.txt').write_text('')
        (out/'hud-command.txt').write_text('extra:off');time.sleep(.7)
        (out/'hud-command.txt').write_text('progress:1')
        print(command('execute as '+args.player+' at @s run cr worldboss scene effects full'),end='')
    time.sleep(1.3);capture('hud-restored')
    (out/'hud-visual-result.json').write_text(json.dumps({'scope':__doc__,'captures':captures},indent=2)+'\n')
    print('PASS: nine native presentation captures; PNGs require visual review, no FPS claim')


if __name__=='__main__':
    main()
