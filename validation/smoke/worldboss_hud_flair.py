"""Verify native HUD shimmer changes in Full and stays still in Off.

Private 360px Rift Crown fixture only. --gui-scale must match the Minecraft GUI scale.
Reads original screenshots; the opaque upper channel excludes text and world motion.
Pillow is a validation dependency only. This does not benchmark rendering performance.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid
from PIL import Image


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rig',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--gui-scale',type=int,default=2)
    parser.add_argument('--player',default='WorldBossTester')
    args=parser.parse_args()
    assert 1<=args.gui_scale<=8 and re.fullmatch(r'[A-Za-z0-9_]{1,16}',args.player)
    out=args.output.resolve();assert out.is_dir()
    runner=Path(__file__).with_name('worldboss_rehearsal.py')
    def command(text):
        return subprocess.check_output([sys.executable,str(runner),'command','--rig',str(args.rig.resolve()),text],text=True)
    assert args.player in command('list'),'Private client not connected'
    samples=[];unique={}
    try:
        for setting,count in [('full',6),('off',3)]:
            command(f'execute as {args.player} at @s run cr worldboss scene effects {setting}')
            time.sleep(1.5)
            signatures=[]
            for index in range(count):
                name=f'flair-{setting}-{index}-{uuid.uuid4().hex[:8]}.png'
                (out/'request.txt').write_text(name)
                path=out/'screenshots'/name;report=out/(name+'.txt');deadline=time.monotonic()+10
                while time.monotonic()<deadline and not(path.exists() and report.exists()):time.sleep(.05)
                assert path.exists() and report.exists(),'Native capture timed out'
                assert re.search(r'hudOwner=[0-9a-f-]{36}',report.read_text()),'HUD not owned'
                while True:
                    try:
                        with Image.open(path) as source:
                            rgb=source.convert('RGB')
                            scale=args.gui_scale;gui_width=math.ceil(source.width/scale)
                            assert gui_width>=376,'Fixture requires the full-size Rift Crown'
                            # The renderer draws an opaque seam at y=70-2 plus its 7px origin.
                            origin=math.floor((gui_width-360)/2)
                            pixels=bytes(channel for x in range((origin+90)*scale,(origin+268)*scale)
                                         for channel in rgb.getpixel((x,75*scale)))
                        break
                    except OSError:
                        if time.monotonic()>=deadline:raise
                        time.sleep(.05)  # Screenshot saving can still be finishing asynchronously.
                signature=hashlib.sha256(pixels).hexdigest();signatures.append(signature)
                samples.append({'setting':setting,'image':name,'channel_sha256':signature})
                time.sleep(.4)
            unique[setting]=len(set(signatures))
    finally:
        command(f'execute as {args.player} at @s run cr worldboss scene effects full')
    (out/'flair-native-result.json').write_text(json.dumps({'unique_channel_frames':unique,'samples':samples},indent=2)+'\n')
    assert unique['full']>=2,'Full shimmer is frozen or GUI scale does not match'
    assert unique['off']==1,'Off channel moved or GUI scale does not match'
    print(f"PASS: native Full shimmer has {unique['full']} distinct frames; Off has one static frame")


if __name__=='__main__':
    main()
