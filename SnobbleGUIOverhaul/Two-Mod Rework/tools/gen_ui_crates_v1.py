"""Generates the native SnobbleCrates "Crate Loot" page (snobblecrates_crate.json): the radiant silver / pink / sky / violet chest.
The reward list, odds and key text come from CrateViews (server); the box is view only."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
INK, MU, LINE, DARK, DARK2, SLOT, SILVER, SILVER_D = '#EEF0FB', '#A9A6C9', '#3A3355', '#14111F', '#1D1930', '#191528', '#C9CDE2', '#8D90AB'
PINK, SKY, VIOLET, GOLD, OUTLINE, HOV = '#F2559A', '#52B2EE', '#A448E8', '#FFD75A', '#0E0B18', '#251F3B'


def label(text, **kw):
    d = {'type': 'label', 'text': text}
    d.update(kw)
    return d


def sp(**kw):
    d = {'type': 'spacer'}
    d.update(kw)
    return d


def vmsg(action, **extra):
    d = {'channel': 'snobble_view', 'action': action}
    d.update(extra)
    return d


def crate():
    slot = {'type': 'button', 'id': 'slot', 'w': 'fill', 'h': 'fill', 'bg': SLOT, 'border': '{c.border}', 'hover': HOV, 'radius': 1, 'pad': [4, 3, 3, 3],
            'args': {'k': 'int:0:100000'}, 'argv': {'k': 'c.i'}, 'msg': vmsg('sel', k='{k}'), 'if': 'c.has', 'repeat': {'for': 'r.cells', 'as': 'c', 'limit': 6}, 'children': [
                {'type': 'column', 'w': 'fill', 'h': 'fill', 'cross': 'center', 'gap': 2, 'children': [
                    {'type': 'row', 'w': 'fill', 'children': [label('PERK', **{'if': 'c.perk', 'color': '#FFFFFF', 'bg': VIOLET, 'pad': [0, 3, 0, 3]}), sp(w='fill'), label('{c.count}', color='#FFFFFF')]},
                    {'type': 'item', 'item': '{c.item}', 'count': 1, 'w': 32, 'h': 32},
                    label('{c.name}', color='{c.color}', wrap=True, w='fill', text_align='center')]}]}
    # an empty cell keeps the grid even
    empty = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': SLOT, 'border': OUTLINE, 'radius': 1, 'if': '!c.has', 'repeat': {'for': 'r.cells', 'as': 'c', 'limit': 6}, 'children': []}
    cells = {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 6, 'repeat': {'for': 'vm.rows', 'as': 'r', 'limit': 3}, 'children': []}
    # six cells per row: one node that is either the reward button or an empty well, chosen per cell
    cell = {'type': 'column', 'w': 'fill', 'h': 'fill', 'repeat': {'for': 'r.cells', 'as': 'c', 'limit': 6}, 'children': [
        {k: v for k, v in slot.items() if k != 'repeat'},
        {k: v for k, v in empty.items() if k != 'repeat'}]}
    cells['children'] = [cell]
    grid = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': DARK, 'border': OUTLINE, 'pad': [8, 8, 8, 8], 'gap': 6, 'children': [
        {'type': 'column', 'if': '!vm.empty', 'w': 'fill', 'h': 'fill', 'gap': 6, 'children': [cells]},
        label('{vm.emptyText}', **{'if': 'vm.empty', 'color': MU, 'w': 'fill', 'h': 'fill', 'wrap': True, 'text_align': 'center'})]}
    seg = {'type': 'spacer', 'h': 10, 'grow': 'g.g', 'bg': '{g.color}', 'repeat': {'for': 'vm.odds', 'as': 'g', 'limit': 8}}
    legend = {'type': 'row', 'w': 'fill', 'gap': 12, 'cross': 'center', 'children': [label('{g.text}', color='{g.color}', repeat={'for': 'vm.odds', 'as': 'g', 'limit': 8})]}
    lid = {'type': 'column', 'w': 'fill', 'h': 150, 'bg': DARK2, 'border': OUTLINE, 'pad': [10, 60, 8, 60], 'gap': 4, 'cross': 'center', 'children': [
        {'type': 'column', 'bg': SILVER, 'border': OUTLINE, 'pad': [4, 20, 5, 20], 'cross': 'center', 'w': 360, 'children': [
            label('{vm.name}', scale=1.6, color=OUTLINE), label('{vm.tag}', color='#4B4668')]},
        label('{vm.key}', color=GOLD),
        sp(h='fill'),
        {'type': 'row', 'if': 'vm.hasBar', 'w': 'fill', 'bg': DARK, 'border': OUTLINE, 'pad': [1, 1, 1, 1], 'gap': 2, 'children': [seg]},
        legend]}
    info = {'type': 'row', 'w': 'fill', 'h': 66, 'bg': DARK, 'border': OUTLINE, 'pad': [5, 10, 5, 10], 'gap': 10, 'cross': 'center', 'children': [
        {'type': 'item', 'if': 'vm.hasInfoItem', 'item': '{vm.info.item}', 'count': 1, 'w': 40, 'h': 40},
        {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [
            label('{vm.info.title}', color='{vm.info.color}', scale=1.1),
            label('{vm.info.rarity}', color='{vm.info.color}'), label('{vm.info.detail}', color=MU),
            label('{vm.info.desc}', color=INK, wrap=True, w='fill')]}]}
    pager = {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'children': [
        info,
        {'type': 'row', 'w': 150, 'h': 66, 'bg': DARK, 'border': OUTLINE, 'pad': [5, 6, 5, 6], 'gap': 6, 'cross': 'center', 'children': [
            {'type': 'button', 'id': 'prev', 'text': '<', 'w': 28, 'h': 30, 'align': 'center', 'cross': 'center', 'bg': SKY, 'border': OUTLINE, 'hover': '#8FD0F6', 'disabled': '#4A4766', 'color': '#FFFFFF',
             'enabled': 'vm.canPrev', 'args': {'d': 'int:-1:1'}, 'argv': {'d': '-1'}, 'msg': vmsg('go', d='{d}')},
            label('{vm.pageText}', color=INK, w='fill', text_align='center'),
            {'type': 'button', 'id': 'next', 'text': '>', 'w': 28, 'h': 30, 'align': 'center', 'cross': 'center', 'bg': SKY, 'border': OUTLINE, 'hover': '#8FD0F6', 'disabled': '#4A4766', 'color': '#FFFFFF',
             'enabled': 'vm.canNext', 'args': {'d': 'int:-1:1'}, 'argv': {'d': '1'}, 'msg': vmsg('go', d='{d}')}]}]}
    close = {'type': 'button', 'id': 'close', 'text': 'X', 'w': 28, 'h': 24, 'align': 'center', 'cross': 'center', 'bg': PINK, 'border': OUTLINE, 'hover': '#FF6EAE', 'color': '#FFFFFF', 'close': True}
    root = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': SILVER, 'border': OUTLINE, 'radius': 2, 'pad': [16, 16, 16, 16], 'gap': 6, 'children': [
        {'type': 'row', 'w': 'fill', 'cross': 'center', 'children': [label('CRATE LOOT  -  view only', color=OUTLINE), sp(w='fill'), close]},
        lid, grid, pager]}
    return {'v': 2, 'id': 'snobblecrates_crate', 'channel': 'snobblecrates', 'hello': 'hello', 'bye': True, 'title': 'Crate Loot', 'width': 720, 'height': 620, 'root': root}


d = crate()
with open(os.path.join(OUT, 'snobblecrates_crate.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, indent=1)
print('wrote snobblecrates_crate')
