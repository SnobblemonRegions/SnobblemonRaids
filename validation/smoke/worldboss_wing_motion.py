"""Capture a wing cycle in an owned private rehearsal using the development probe.

Checks attachment completeness, finite coordinates, continuing animation and large
discontinuities. The PNGs still require visual review; this is not an FPS benchmark.
"""
import argparse
import json
import math
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rig', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--player', default='WorldBossTester')
    args = parser.parse_args()
    assert re.fullmatch(r'[A-Za-z0-9_]{1,16}', args.player), 'Invalid fixture player'
    out = args.output.resolve()
    assert out.is_dir(), 'Capture directory must already exist'
    runner = Path(__file__).with_name('worldboss_rehearsal.py')
    # The runner validates loopback endpoints and process ownership before RCON.
    subprocess.run([sys.executable, str(runner), 'command', '--rig', str(args.rig.resolve()),
                    'list'], check=True)
    captures = []
    owner = None
    for index in range(12):
        name = f'wing-cycle-{index:02d}-{uuid.uuid4().hex[:8]}.png'
        (out / 'request.txt').write_text(name)
        report = out / (name + '.txt')
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline and not (report.exists() and (out / 'screenshots' / name).exists()):
            time.sleep(.05)
        assert report.exists() and (out / 'screenshots' / name).exists(), 'Probe capture timed out'
        text = report.read_text()
        assert 'captured=16' in text and 'render=true' in text, text
        match = re.search(r'owner=([0-9a-f-]{36})', text)
        assert match, text
        if owner is None:
            owner = match[1]
        assert match[1] == owner, 'Showpiece changed during capture'
        points = [tuple(map(float, point)) for point in re.findall(r'V\[x=([^,]+), y=([^,]+), z=([^\]]+)\]', text)]
        assert len(points) == 16 and all(math.isfinite(v) for p in points for v in p), text
        captures.append({'image': name, 'time': time.monotonic(), 'points': points})
        time.sleep(.4)
    displacement = max(math.dist(a, b) for frame in captures[1:]
                       for a, b in zip(captures[0]['points'], frame['points']))
    jump = max(math.dist(a, b) for before, after in zip(captures, captures[1:])
               for a, b in zip(before['points'], after['points']))
    result = {'owner': owner, 'anchors': 16, 'captures': captures,
              'max_displacement_blocks': displacement, 'max_sample_step_blocks': jump,
              'note': '2.5-block discontinuity guard applies only to this stationary scale-4 private fixture.'}
    (out / 'wing-motion-result.json').write_text(json.dumps(result, indent=2) + '\n')
    assert displacement > .01, 'Wing anchors are frozen'
    assert jump < 2.5, f'Large attachment discontinuity: {jump}'
    print(f'PASS: 16 anchors, 12 frames, movement={displacement:.4f}, largest sample step={jump:.4f} blocks')


if __name__ == '__main__':
    main()
