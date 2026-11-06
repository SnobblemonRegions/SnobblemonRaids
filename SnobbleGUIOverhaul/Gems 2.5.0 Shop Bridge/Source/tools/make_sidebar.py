"""Adds a "Gems:" row under "Community Points" in Snobblemon_Sidebar_Stylized.json (Community Points is KEPT, untouched).

    python make_sidebar.py <original.json> <output.json> <report.md>

Text-level insertion only; the file is never re-serialised, so every original line, gradient, glyph, the Ditto/title frames, line endings
and spacing stay byte-identical. For each of the 210 animation frames one new line is inserted right after that frame's Community Points line:

  <gem glyph><spacer><gradient:GEM COLOURS><b>Gems:</b></gradient> <gradient:GEM COLOURS><b>%snobblegems:balance%</b></gradient>

The gem gradient is a smooth loop (pink -> magenta -> purple -> blue -> cyan -> light cyan -> lavender -> pink) sampled at 13 points + the
first repeated, exactly how the file writes its own gradients. Each frame uses the SAME phase as that frame's Community Points rainbow,
so the new row animates in step with the others, at the same speed.
"""
import colorsys
import json
import re
import sys

SRC, DST, REPORT = sys.argv[1], sys.argv[2], sys.argv[3]
raw = open(SRC, 'rb').read().decode('utf-8')

F = "<font:'snobblemon:sidebar_icons'>"
GEM_ICON = "<font:'snobblegemsv2:icons'>" + chr(0xE300) + "</font>" + F + chr(0xE100) + "</font>"
GEM_CYCLE = ['#FF9AF0', '#F27AE6', '#D65AEA', '#B04AF0', '#8A44F5', '#6A52FF', '#4C7CFF', '#3AA8FF', '#3DDCFF', '#8FEEFF', '#B4F7FF', '#D9D2FF', '#FFB8F6']

def _rgb(h):
    return [int(h[i:i + 2], 16) for i in (1, 3, 5)]


def gem_color(u):
    """Smooth loop through the gem colours; u in [0,1) wraps."""
    u %= 1.0
    pos = u * 13
    k = int(pos) % 13
    t = pos - int(pos)
    a, b = _rgb(GEM_CYCLE[k]), _rgb(GEM_CYCLE[(k + 1) % 13])
    return '#%02X%02X%02X' % tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gem_stops(phase):
    """13 evenly spaced samples + the first repeated, with the same phase as the frame's rainbow (so both rows move together)."""
    s = [gem_color(phase + j / 13) for j in range(13)]
    return s + [s[0]]


lines = raw.split('\r\n')
base = None
out_lines, inserted, offsets = [], 0, []
for ln in lines:
    out_lines.append(ln)
    if 'Community Points:' in ln:
        stops = re.search(r'<gradient:([^>]*)>', ln).group(1).split(':')
        assert len(stops) == 14 and stops[0] == stops[-1], 'unexpected gradient shape'
        # the rainbow of each frame = the same 13-point hue wheel with its phase shifted; read this frame's phase from its first colour
        r, g, bl = (int(stops[0][i:i + 2], 16) / 255 for i in (1, 3, 5))
        phase = colorsys.rgb_to_hsv(r, g, bl)[0]          # 0..1
        offsets.append(round(phase * 360))
        grad = ':'.join(gem_stops(phase))
        indent = re.match(r'^(\s*)"', ln).group(1)
        # the Community Points line is never the last of its page (the ip line follows), so it ends with a comma and so does the new line
        assert ln.rstrip().endswith(','), 'Community Points line is last in its page'
        out_lines.append(indent + '"' + GEM_ICON + '<gradient:' + grad + '><b>Gems:</b></gradient> <gradient:' + grad + '><b>%snobblegems:balance%</b></gradient>",')
        inserted += 1
assert inserted == 210, inserted
out = '\r\n'.join(out_lines)
open(DST, 'wb').write(out.encode('utf-8'))

# ------------------------------------------------------------------ verification
a, b = json.loads(raw), json.loads(out)
rep, ok = [], True


def chk(name, cond, detail=''):
    global ok
    ok = ok and cond
    rep.append(('PASS ' if cond else 'FAIL ') + name + (' - ' + detail if detail else ''))


chk('output is valid JSON', True)
chk('top-level settings unchanged (update_tick_time, page_change, title_change, scroll_*)', all(a[k] == b[k] for k in a if k != 'pages'))
chk('title list identical (30 frames)', a['title'] == b['title'])
chk('still 210 pages; each page grew from 13 to 14 lines', len(b['pages']) == 210 and all(len(x) == 13 for x in a['pages']) and all(len(x) == 14 for x in b['pages']))
chk('every ORIGINAL line is unchanged and in the same order (only one new line per page)', all(pb[:12] == pa[:12] and pb[13:] == pa[12:] for pa, pb in zip(a['pages'], b['pages'])))
chk('"Community Points" is kept, byte-identical, on all 210 frames', all(pb[11] == pa[11] and 'Community Points:' in pb[11] for pa, pb in zip(a['pages'], b['pages'])))
chk('the new Gems line sits directly under Community Points on all 210 frames', all('Gems:' in pb[12] and 'Community' not in pb[12] for pb in b['pages']))
chk('removing the 210 new lines gives back the original file byte for byte', '\r\n'.join(l for l in out.split('\r\n') if GEM_ICON not in l) == raw)
chk('placeholders: Gems row uses %snobblegems:balance%; Community Points still uses %snobblemoncompanion:snobblepoints%', out.count('%snobblegems:balance%') == 210 and out.count('%snobblemoncompanion:snobblepoints%') == 210)
chk('line endings unchanged (CRLF everywhere)', out.count('\r\n') == out.count('\n') and out.count('\n') == raw.count('\n') + 210)
chk('gem gradient uses the same per-frame phase as the Community Points rainbow (so both animate together)', len(offsets) == 210 and len(set(offsets)) > 20, 'hue phase of frames 0,1,2 = %d, %d, %d degrees; %d distinct phases' % (offsets[0], offsets[1], offsets[2], len(set(offsets))))
with open(REPORT, 'w', encoding='utf-8') as f:
    f.write('# Sidebar change report (Gems row ADDED; Community Points kept)\n\nSource: `Snobblemon_Sidebar_Stylized.json` (R:\\Downloads). Output: `Snobblemon_Sidebar_Stylized_Gems.json`.\n\n## Checks\n\n' + '\n'.join('* ' + r for r in rep) + '\n\n')
    f.write('## Frame 0, the two rows\n\n```\nCOMMUNITY POINTS (unchanged): %s\nGEMS (new)                  : %s\n```\n\n' % (b['pages'][0][11], b['pages'][0][12]))
    f.write('Gem gradient (13-colour loop; the first colour is repeated at the end, like your own gradients): ' + ' '.join(GEM_CYCLE) + '\n')
print('\n'.join(rep))
print('ALL OK' if ok else 'PROBLEMS')
sys.exit(0 if ok else 1)
