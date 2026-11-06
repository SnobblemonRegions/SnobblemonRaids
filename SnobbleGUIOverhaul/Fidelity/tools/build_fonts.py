"""Builds the page fonts into Snobblemon SERVER's resource-pack namespace (delivered to players through Polymer, never bundled in the client).

For every font: subset to the characters the pages use (Latin, punctuation, arrows, common symbols), write the TTF, one Minecraft
TrueType font definition per pixel size, a licence copy, and the metrics the client needs to place text like CSS does.
"""
import json, os, shutil
from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
WEB = os.path.abspath(os.path.join(ROOT, '..', 'webgui_pages_src', 'extracted', 'webgui', 'web'))
DL = os.path.join(ROOT, 'fonts_src', 'dl')
NS = 'snobblemon_regions'
OUT = os.path.abspath(os.path.join(ROOT, '..', 'Two-Mod Rework', 'snobblemon-server', 'src', 'main', 'resources', 'assets', NS))
SIZES = [8, 9, 10, 11, 12, 13, 14, 16, 19, 22, 26, 32, 40, 52, 64]

# name -> (source file, licence file, why)
FONTS = {
    # substitutes for Windows system fonts the pages named (those cannot be redistributed)
    'courier': (os.path.join(DL, 'courierprime_CourierPrime-Regular.ttf'), os.path.join(DL, 'courierprime_OFL.txt'), 'Courier New (cases)'),
    'courier_b': (os.path.join(DL, 'courierprime_CourierPrime-Bold.ttf'), os.path.join(DL, 'courierprime_OFL.txt'), 'Courier New bold'),
    'hand': (os.path.join(DL, 'kalam_Kalam-Regular.ttf'), os.path.join(DL, 'kalam_OFL.txt'), 'Segoe Print (cases)'),
    'hand_b': (os.path.join(DL, 'kalam_Kalam-Bold.ttf'), os.path.join(DL, 'kalam_OFL.txt'), 'Segoe Print bold'),
    'mono': (os.path.join(DL, 'inconsolata_R.ttf'), os.path.join(DL, 'inconsolata_OFL.txt'), 'Consolas (questdex, wardrobe)'),
    'mono_b': (os.path.join(DL, 'inconsolata_B.ttf'), os.path.join(DL, 'inconsolata_OFL.txt'), 'Consolas bold'),
    'sans': (os.path.join(DL, 'opensans_400.woff2'), os.path.join(DL, 'opensans_OFL.txt'), 'Segoe UI (rules, verify, goal)'),
    'sans_b': (os.path.join(DL, 'opensans_700.woff2'), os.path.join(DL, 'opensans_OFL.txt'), 'Segoe UI bold'),
    'sans_xb': (os.path.join(DL, 'opensans_800.woff2'), os.path.join(DL, 'opensans_OFL.txt'), 'Segoe UI extra bold'),
    'treb': (os.path.join(DL, 'ptsans_400.woff2'), os.path.join(DL, 'ptsans_OFL.txt'), 'Trebuchet MS (goal)'),
    'treb_b': (os.path.join(DL, 'ptsans_700.woff2'), os.path.join(DL, 'ptsans_OFL.txt'), 'Trebuchet MS bold'),
    # the pages' own web fonts
    'nunito': (os.path.join(WEB, 'auction', 'font', 'Nunito-500.ttf'), None, 'Nunito 500'),
    'nunito_b': (os.path.join(WEB, 'auction', 'font', 'Nunito-700.ttf'), None, 'Nunito 700'),
    'oswald': (os.path.join(WEB, 'auction', 'font', 'Oswald-600.ttf'), None, 'Oswald 600'),
    'plex': (os.path.join(WEB, 'cobbleraids', 'shared', 'IBMPlexMono-400.woff2'), None, 'IBM Plex Mono 400'),
    'plex_b': (os.path.join(WEB, 'cobbleraids', 'shared', 'IBMPlexMono-600.woff2'), None, 'IBM Plex Mono 600'),
    'silk': (os.path.join(WEB, 'cobbleraids', 'shared', 'Silkscreen-400.woff2'), None, 'Silkscreen 400'),
    'silk_b': (os.path.join(WEB, 'cobbleraids', 'shared', 'Silkscreen-700.woff2'), None, 'Silkscreen 700'),
    'monocraft': (os.path.join(WEB, 'ascensionlib', 'fonts', 'Monocraft-latin.woff'), None, 'Monocraft'),
    'outfit': (os.path.join(DL, 'outfit_500.ttf'), os.path.join(DL, 'outfit_OFL.txt'), 'Outfit 500 (cobbletowers)'),
    'outfit_6': (os.path.join(DL, 'outfit_600.ttf'), os.path.join(DL, 'outfit_OFL.txt'), 'Outfit 600'),
    'outfit_b': (os.path.join(DL, 'outfit_700.ttf'), os.path.join(DL, 'outfit_OFL.txt'), 'Outfit 700'),
    'outfit_xb': (os.path.join(DL, 'outfit_800.ttf'), os.path.join(DL, 'outfit_OFL.txt'), 'Outfit 800'),
    'outfit_bl': (os.path.join(DL, 'outfit_900.ttf'), os.path.join(DL, 'outfit_OFL.txt'), 'Outfit 900'),
    'jersey': (os.path.join(WEB, 'cobbletowers', 'assets', 'font', 'Jersey15-Regular.woff2'), os.path.join(WEB, 'cobbletowers', 'assets', 'font', 'Jersey15-OFL.txt'), 'Jersey 15'),
}
WINF = r'C:\Windows\Fonts'
METRICS_FROM = {'courier': os.path.join(WINF, 'cour.ttf'), 'courier_b': os.path.join(WINF, 'courbd.ttf'),
                'hand': os.path.join(WINF, 'segoepr.ttf'), 'hand_b': os.path.join(WINF, 'segoeprb.ttf'),
                'mono': os.path.join(WINF, 'consola.ttf'), 'mono_b': os.path.join(WINF, 'consolab.ttf'),
                'sans': os.path.join(WINF, 'segoeui.ttf'), 'sans_b': os.path.join(WINF, 'segoeuib.ttf'), 'sans_xb': os.path.join(WINF, 'segoeuib.ttf'),
                'treb': os.path.join(WINF, 'trebuc.ttf'), 'treb_b': os.path.join(WINF, 'trebucbd.ttf')}
UNICODES = list(range(0x20, 0x7F)) + list(range(0xA0, 0x100)) + list(range(0x2010, 0x2028)) + list(range(0x2030, 0x205F)) + \
    list(range(0x2190, 0x2200)) + [0x20AC, 0x2122, 0x2116] + list(range(0x25A0, 0x2600)) + list(range(0x2600, 0x2700)) + list(range(0x2700, 0x27C0))


def build():
    fdir = os.path.join(OUT, 'font')
    if os.path.isdir(fdir):
        shutil.rmtree(fdir)
    os.makedirs(os.path.join(fdir, 'licenses'))
    meta = {}
    for name, (src, lic, why) in FONTS.items():
        f = TTFont(src)
        f.flavor = None
        opts = subset.Options()
        opts.layout_features = ['*']
        opts.hinting = True
        opts.name_IDs = ['*']
        opts.notdef_outline = True
        sub = subset.Subsetter(opts)
        sub.populate(unicodes=UNICODES)
        sub.subset(f)
        upm = f['head'].unitsPerEm
        os2, hhea = f['OS/2'], f['hhea']
        # Chrome on Windows (the reference renders) lays lines out with the Windows metrics
        asc, desc = os2.usWinAscent / upm, os2.usWinDescent / upm
        scale = 1.0
        if name in METRICS_FROM and os.path.exists(METRICS_FROM[name]):
            # same average letter width as the font the page named, so text wraps where it wrapped in the browser
            def avgw(ft):
                cm, hm = ft.getBestCmap(), ft['hmtx']
                ws = [hm[cm[ord(ch)]][0] for ch in 'abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ' if ord(ch) in cm]
                return sum(ws) / len(ws) / ft['head'].unitsPerEm
            scale = round(avgw(TTFont(METRICS_FROM[name])) / avgw(f), 4)
        if name in METRICS_FROM and os.path.exists(METRICS_FROM[name]):
            # place lines like the font the page named (only its numbers are read; that font is not shipped)
            m = TTFont(METRICS_FROM[name])
            asc, desc = m['OS/2'].usWinAscent / m['head'].unitsPerEm, m['OS/2'].usWinDescent / m['head'].unitsPerEm
        ttf = os.path.join(fdir, name + '.ttf')
        f.save(ttf)
        for s in SIZES:
            prov = {'providers': [
                {'type': 'ttf', 'file': '%s:%s.ttf' % (NS, name), 'size': s, 'oversample': 4.0, 'shift': [0, 0], 'skip': ''},
                {'type': 'reference', 'id': 'minecraft:default'}]}
            json.dump(prov, open(os.path.join(fdir, '%s_%d.json' % (name, s)), 'w'))
        meta[name] = {'asc': round(asc, 4), 'desc': round(desc, 4), 'scale': scale, 'sizes': SIZES, 'from': why,
                      'hhea': [round(hhea.ascent / upm, 4), round(-hhea.descent / upm, 4)]}
        lic_text = open(lic, encoding='utf8', errors='replace').read() if lic and os.path.exists(lic) else \
            'SIL Open Font License 1.1 - see the font\'s copyright notice:\n' + str(f['name'].getDebugName(0) or '')
        open(os.path.join(fdir, 'licenses', name + '.txt'), 'w', encoding='utf8').write(lic_text)
        print('%-10s %-22s %6d bytes  asc %.3f desc %.3f scale %.3f' % (name, why, os.path.getsize(ttf), asc, desc, scale))
    json.dump({'fonts': meta}, open(os.path.join(OUT, 'snobblemon_fonts.json'), 'w'), indent=1)


if __name__ == '__main__':
    build()
