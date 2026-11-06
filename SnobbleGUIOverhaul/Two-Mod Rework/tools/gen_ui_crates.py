"""Native SnobbleCrates loot box (snobblecrates_crate.json), a faithful copy of the original snobblecrates/crate.html.

The 700x600 chest is scaled like the original fit(): min(w*.55/700, h*.55/600) * size -> "fit": [1273, 1091] (+ the -/+ size buttons).
The page is see-through over the world ("nodim"). Font: Monocraft 12px/1.4. Plating, lid, slots, wells and buttons are the page's own
CSS / canvas drawings rendered to textures (tex:snobblecrates/*); reward pictures are its pixel art, drawn by the server (CrateArt).
The previous native version is gen_ui_crates_v1.py.
"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
INK, MUTED, OUTLINE, SILVER, GOLD = '#eef0fb', '#a9a6c9', '#0e0b18', '#c9cde2', '#ffd75a'
F = 'monocraft'
FX, FY = 30, 12          # the frame inside the page (room for the latches and the triple outline)
_N = [0]


def nid(p='n'):
    _N[0] += 1
    return '%s%d' % (p, _N[0])


def T(text, size=12, color=INK, **kw):
    d = {'type': 'label', 'text': text, 'font': F, 'size': size, 'color': color, 'shadow': False}
    d.update(kw)
    return d


def box(children=None, **kw):
    d = {'type': 'column', 'children': children or []}
    d.update(kw)
    return d


def row(children, **kw):
    d = {'type': 'row', 'children': children}
    d.update(kw)
    return d


def vmsg(action, **extra):
    d = {'channel': 'snobble_view', 'action': action}
    d.update(extra)
    return d


def tex(name, x, y, w, h, children=None, **kw):
    d = {'type': 'column', 'x': x, 'y': y, 'w': w, 'h': h, 'bg': 'tex:snobblecrates/%s.png' % name, 'children': children or []}
    d.update(kw)
    return d


def pic(key, size, **kw):
    """pic(rw, rgb, px): the pixel picture at a whole multiple of its size, centred."""
    d = {'type': 'image', 'pokemon': key, 'natural': True, 'size': size, 'w': size, 'h': size}
    d.update(kw)
    return d


def lid():
    gems = row([{'type': 'image', 'pokemon': '{g.art}', 'w': '{g.w}' if False else 40, 'h': 30, 'repeat': {'for': 'vm.gems', 'as': 'g', 'limit': 3}}],
               gap=6, h=36, cross='end')
    # the middle gem is 48x36 (nth-child(2)); drawn as its own node so the sizes match
    gems = row([{'type': 'image', 'pokemon': '{vm.gems[0].art}', 'w': 40, 'h': 30}, {'type': 'image', 'pokemon': '{vm.gems[1].art}', 'w': 48, 'h': 36},
                {'type': 'image', 'pokemon': '{vm.gems[2].art}', 'w': 40, 'h': 30}], gap=6, h=36, cross='end')
    plate = box([box([T('{vm.name}', 19, OUTLINE, ls=0.95, upper=True),
                      T('{vm.tag}', 10, '#4b4668')],
                     w='fill', cross='center', pad=[4, 16, 5, 16], bcolors=['#f1f2fb', SILVER, '#8d90ab', SILVER], bw=4)],
                bg=SILVER, border=OUTLINE, bw=4, shrink=True, min_w=260)
    key = T('{vm.key}', 11, MUTED, rich=True, rich_plain=True, bcolor=GOLD, pad=[4, 0, 0, 0])
    seg = box([], w_expr='o.w', h=6, bg='{o.color}', repeat={'for': 'vm.odds', 'as': 'o', 'limit': 8})
    bar = tex('bar', 0, 0, 508, 16, [row([seg], x=4, y=4, gap=2, h=6)], **{'if': 'vm.hasBar'})
    legend = row([row([box([], w=7, h=7, bg='{o.color}', ring='1,' + OUTLINE), box([], w=4, h=1), T('{o.label} ', 9, MUTED), T('{o.val}', 9, INK)],
                      cross='center', repeat={'for': 'vm.odds', 'as': 'o', 'limit': 8})], gap=12, wrap=True)
    odds = box([bar, box([legend], w=508, cross='center', pad=[19, 0, 0, 0] if False else [0, 0, 0, 0], y=19, x=0)], x=100, bottom=14, w=508, h=34)
    odds = box([box([bar], w=508, h=16), box([legend], w=508, cross='center', pad=[3, 0, 0, 0])], x=100, bottom=14, w=508, gap=0)
    return tex('lid', FX + 22, FY + 22, 656, 184, [
        box([gems, plate, key], x=0, y=14, w=656, cross='center', gap=0),
        odds], **{'if': '!vm.empty'})


def slots():
    s = {'type': 'button', 'x': 'c.x', 'y': 'c.y', 'w': 99, 'h': 82, 'bg': 'tex:snobblecrates/slot.png', 'hover': '{c.hover}',
         'repeat': {'for': 'vm.slots', 'as': 'c', 'limit': 18}, 'id': nid('slot'), 'args': {'k': 'int:0:100000'}, 'argv': {'k': 'c.i'}, 'msg': vmsg('sel', k='{k}'),
         'hover_set': {'info': 'c.info'}, 'children': [
             box([], x=3, y=82 - 3 - 5, w=93, h=5, bg='{c.color}', **{'if': 'c.has'}),
             box([pic('{c.art}', 40)], x=29, y=9, w=40, h=40, **{'if': 'c.has'}),
             T('{c.name}', 9, '{c.color}', lh=10.35, wrap=True, clip=True, text_align='center', x=6, y=52, w=87, h=21, **{'if': 'c.has'}),
             T('{c.count}', 10, '#ffffff', tshadow='1,1,' + OUTLINE, right=8, y=6, **{'if': 'c.has && c.count'}),
             T('PERK', 8, '#ffffff', bg='#a448e8', border=OUTLINE, bw=2, pad=[2, 5, 2, 5], x=7, y=6, **{'if': 'c.perk'}),
             box([], x=8, bottom=12, w=6, h=6, bg='{c.color}', ring='2,' + OUTLINE, **{'if': 'c.hi'})]}
    return tex('body', FX + 22, FY + 212, 656, 286, [s, T('{vm.emptyText}', 12, MUTED, x=0, y=130, w=656, text_align='center', **{'if': 'vm.empty'})])


def ledge():
    y = FY + 504
    info = tex('well_info', FX + 22, y, 374, 74, [row([
        box([pic('{$info.art}', 34)], w=46, h=46, bg='#191528', border='{$info.color}', bw=3, align='center', cross='center', pad=[3, 3, 3, 3]),
        box([T('{$info.title}', 12, '{$info.color}', ellipsis=True, w='fill'),
             row([T('{$info.rarity}', 9, '{$info.color}'), T('{$info.detail}', 9, MUTED, ellipsis=True, w='fill')], w='fill'),
             T('{$info.desc}', 9, INK, wrap=True, clip=True, w='fill', h=26, pad=[1, 0, 0, 0])], w='fill', gap=0)],
        x=14, y=9, w=346, h=56, gap=10, cross='center', **{'if': '$info.has'}),
        box([T('Point at a reward to inspect it.', 12, INK), T('{vm.hint}', 9, MUTED)], x=14, y=9, w=346, h=56, align='center', gap=0, **{'if': '!$info.has'})])

    def arrow(text, d, en):
        return {'type': 'button', 'text': text, 'font': F, 'size': 13, 'color': '#ffffff', 'shadow': False, 'w': 26, 'h': 34, 'text_align': 'center',
                'bg': 'tex:snobblecrates/arrow.png', 'hover': 'tex:snobblecrates/arrow.png', 'disabled': 'tex:snobblecrates/arrow_off.png', 'enabled': en,
                'id': nid('arr'), 'args': {'d': 'int:-1:1'}, 'argv': {'d': d}, 'msg': vmsg('go', d='{d}'), 'set': {'info': "''"}}
    pager = tex('well_pager', FX + 22 + 380, y, 120, 74, [row([arrow('◀', '-1', 'vm.canPrev'), T('{vm.pageText}', 11, INK, min_w=34, text_align='center'),
                                                               arrow('▶', '1', 'vm.canNext')], x=0, y=0, w=120, h=74, gap=6, align='center', cross='center')])

    def sz(text, d):
        return {'type': 'button', 'text': text, 'font': F, 'size': 13, 'color': '#ffffff', 'shadow': False, 'w': 22, 'h': 22, 'text_align': 'center',
                'bg': 'tex:snobblecrates/sz.png', 'hover': 'tex:snobblecrates/sz.png', 'id': nid('sz'), 'set': {'zoompct': '$zoompct %s 10' % d}}
    side = tex('well_side', FX + 22 + 506, y, 150, 74, [box([
        row([sz('−', '-'), T('{$zoompct}%', 10, INK, min_w=40, text_align='center'), sz('+', '+')], gap=4, cross='center'),
        T('View only · Esc closes', 9, MUTED, text_align='center')], x=0, y=0, w=150, h=74, align='center', cross='center', gap=3)])
    return [info, pager, side]


def page():
    frame = tex('frame', FX - 12, FY - 12, 724, 624)
    latches = [tex('latch_l', FX - 26, FY + 276, 22, 62), tex('latch_r', FX + 704, FY + 276, 22, 62)]
    close = {'type': 'button', 'text': '×', 'font': F, 'size': 16, 'color': '#ffffff', 'shadow': False, 'x': FX + 642, 'y': FY + 28, 'w': 30, 'h': 30,
             'text_align': 'center', 'bg': 'tex:snobblecrates/close.png', 'hover': 'tex:snobblecrates/close.png', 'id': 'close', 'close': True}
    root = {'type': 'column', 'w': 760, 'h': 624, 'children': [frame] + latches + [lid(), slots()] + ledge() + [close]}
    return {'v': 2, 'id': 'snobblecrates_crate', 'channel': 'snobblecrates', 'hello': 'hello', 'bye': True, 'title': 'Crate Loot', 'width': 760, 'height': 624,
            'fit': [1273, 1091], 'nodim': True, 'state': {'zoompct': 100, 'info': ''}, 'root': root}


d = page()
with open(os.path.join(OUT, 'snobblecrates_crate.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, separators=(',', ':'))
print('wrote snobblecrates_crate (%d bytes)' % os.path.getsize(os.path.join(OUT, 'snobblecrates_crate.json')))
