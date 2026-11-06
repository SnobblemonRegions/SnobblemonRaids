"""Builds the animation preview GIFs from the real-client frame recordings (tests/shots_ui/g_<script>/f000.png ... , one frame every 50 ms of animation time,
the UI clock is stepped exactly by the test harness). Usage: python make_gifs.py <out_dir>"""
import os, sys, glob
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'tests', 'shots_ui'))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'Animation Previews', 'Real client 2.6.0'))

# name -> (script, crop box in the 1280x720 frame or None, scale, frame step in ms, title)
JOBS = [
    ('ascension_capture_reveal', 'capgif', (180, 0, 1100, 720), 0.9, 50, 'AscensionLib: Pokemon obtained (card turns from its back, bloom, shockwave, shards, sparks)'),
    ('cobbletowers_rental_draft', 'rentalgif', None, 0.75, None, 'CobbleTowers rental draft: sealed pack, tear, card flips, slam, flash, rings, picks'),
    ('cobbletowers_rental_hover_and_skip', 'rentalhover', None, 0.75, 50, 'Rental draft: hover stat card and picked cards'),
    ('cobbletowers_rental_skip', 'rentalskip', None, 0.75, 50, 'Rental draft: skipping the reveal'),
    ('cobbletowers_reward_reveal', 'rewardgif', None, 0.75, 50, 'CobbleTowers reward reveal modal'),
    ('cobbletowers_vendor_purchase', 'vendgif', None, 0.75, 50, 'CobbleTowers vendor purchase burst'),
    ('gem_shop_purchase_burst', 'gemgif', None, 0.75, 50, 'Gem shop purchase burst'),
    ('auction_purchase', 'aucgif', None, 0.75, 50, 'Auction House purchase: gold sweep and sparks'),
    ('cases_stamp_closed', 'stampgif', None, 0.75, 50, 'Cases: STAMP CLOSED rubber stamp'),
    ('wardrobe_detail_open', 'wardgif', None, 0.75, 30, 'Wardrobe: item window opens'),
]
STEPS = {'rentalgif': None}


def build(name, script, crop, scale, step):
    d = os.path.join(ROOT, ('h_' if script=='capgif' else 'q_' if script.startswith('rental') else 'r_') + script)
    fr = sorted(glob.glob(os.path.join(d, '[fhs]*.png')))
    if not fr:
        print('NO FRAMES', script)
        return None
    ims = []
    for f in fr:
        im = Image.open(f).convert('RGB')
        if crop:
            im = im.crop(crop)
        im = im.resize((int(im.width * scale), int(im.height * scale)), Image.LANCZOS)
        ims.append(im)
    # the rental recording steps 100 ms for the first 8 frames (floating pack), then 50 ms
    delays = [100] * 8 + [50] * (len(ims) - 8) if script == 'rentalgif' else [step or 50] * len(ims)
    delays[-1] = 1200
    pal = ims[len(ims) // 2].quantize(colors=200, method=Image.Quantize.MEDIANCUT)
    q = [im.quantize(palette=pal, dither=Image.Dither.NONE) for im in ims]
    path = os.path.join(OUT, name + '.gif')
    q[0].save(path, save_all=True, append_images=q[1:], duration=delays, loop=0, optimize=False, disposal=1)
    # a contact sheet of 8 evenly spaced frames
    n = min(8, len(ims))
    idx = [round(i * (len(ims) - 1) / (n - 1)) for i in range(n)] if n > 1 else [0]
    w, h = ims[0].size
    tw = 380
    th = int(h * tw / w)
    sheet = Image.new('RGB', (tw * 4, th * 2), (20, 20, 20))
    for k, i in enumerate(idx):
        sheet.paste(ims[i].resize((tw, th), Image.LANCZOS), ((k % 4) * tw, (k // 4) * th))
    sheet.save(os.path.join(OUT, name + '_frames.png'))
    print(name, len(ims), 'frames', os.path.getsize(path) // 1024, 'KB')
    return path


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for j in JOBS:
        build(*j[:5])
    # card mouse tilt: the hover at the four corners, centre and leave (q_rentaltilt frames from the first hover on)
    d = os.path.join(ROOT, 'q_rentaltilt')
    fr = [f for f in sorted(glob.glob(os.path.join(d, 't*.png'))) if int(os.path.basename(f)[3:6]) >= 129]
    ims = [Image.open(f).convert('RGB').crop((40, 150, 1240, 560)) for f in fr]
    ims = [im.resize((int(im.width * .8), int(im.height * .8)), Image.LANCZOS) for im in ims]
    pal = ims[len(ims) // 2].quantize(colors=200, method=Image.Quantize.MEDIANCUT)
    q = [im.quantize(palette=pal, dither=Image.Dither.NONE) for im in ims]
    q[0].save(os.path.join(OUT, 'cobbletowers_card_mouse_tilt.gif'), save_all=True, append_images=q[1:], duration=[50] * (len(q) - 1) + [1200], loop=0, optimize=False, disposal=1)
    print('tilt', len(q), 'frames')
