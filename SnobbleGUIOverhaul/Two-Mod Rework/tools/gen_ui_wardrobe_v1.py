"""Generates the native SnobbleCosmetics Wardrobe page (wardrobe.json): category tabs, filter/sort row, 4x2 card grid with pictures,
detail panel (set row, party picker). All state lives in WardrobeViews; the cosmetics mod still decides ownership and applies items."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
SCR, PANEL, HEAD, ROW, WELL, LN = '#07131C', '#0D2230', '#0A1B27', '#0C2231', '#0A2A3A', '#1F6B86'
INK, MU, ACC, GOOD, WARN, GOLD = '#BDF1FF', '#5DA6BD', '#4AD8F6', '#62D06A', '#FF7A6B', '#F2CF3A'
HOV = '#12425A'


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


def btn(text, **kw):
    d = {'type': 'button', 'text': text, 'h': 22, 'pad': [3, 10, 3, 10], 'cross': 'center', 'align': 'center', 'bg': PANEL, 'border': LN, 'radius': 2,
         'hover': HOV, 'disabled': '#0A1A26', 'color': INK}
    d.update(kw)
    return d


def wardrobe():
    tabs = btn('{t.label}', id='tab', w='fill', h=24, bg='{t.bg}', border='{t.border}', color='{t.color}', args={'k': 'str:16'}, argv={'k': 't.k'},
               msg=vmsg('cat', k='{k}'), repeat={'for': 'vm.tabs', 'as': 't', 'limit': 7})
    head = {'type': 'row', 'w': 'fill', 'bg': HEAD, 'border': LN, 'pad': [6, 10, 6, 10], 'gap': 10, 'cross': 'center', 'children': [
        label('WARDROBE', color=ACC, scale=1.3),
        label('Owned {vm.owned}', color=INK), label('Worn {vm.worn}', color=GOOD), sp(w='fill'),
        btn('TAKE EVERYTHING OFF', id='offall', w=150, msg=vmsg('offall')),
        btn('X', id='close', w=28, bg='#3A1218', border=WARN, hover='#5A1E28', close=True)]}
    coll = {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'children': [
        label('{vm.collName}', color=INK), label('{vm.collText}', color=MU),
        {'type': 'bar', 'w': 50, 'h': 6, 'value': 'vm.collPct', 'max': '100', 'fill': ACC, 'fill_bg': WELL, 'radius': 1}, sp(w='fill'),
        
        btn('OWNED', id='fowned', w=50, h=18, border='{vm.fOwned}', args={}, msg=vmsg('filter', k='owned')),
        btn('WORN', id='fworn', w=44, h=18, border='{vm.fWorn}', args={}, msg=vmsg('filter', k='worn')),
        sp(w=6),
        btn('RARITY', id='srar', w=50, h=18, border='{vm.s_rarity}', msg=vmsg('sort', k='rarity')),
        btn('NAME', id='snam', w=40, h=18, border='{vm.s_name}', msg=vmsg('sort', k='name')),
        btn('OWNED', id='sown', w=50, h=18, border='{vm.s_owned}', msg=vmsg('sort', k='owned'))]}

    card_btn = {'type': 'button', 'id': 'open', 'w': 'fill', 'h': 'fill', 'bg': ROW, 'border': '{c.border}', 'hover': HOV, 'radius': 2, 'pad': [4, 4, 4, 4],
                'args': {'k': 'str:48'}, 'argv': {'k': 'c.id'}, 'msg': vmsg('open', k='{k}'), 'if': 'c.has', 'children': [
        {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 2, 'cross': 'center', 'children': [
            {'type': 'row', 'w': 'fill', 'children': [label('{c.slot}', color=MU), sp(w='fill'), label('{c.state}', color='{c.stateColor}')]},
            {'type': 'image', 'pokemon': '{c.thumb}', 'w': 100, 'h': 100},
            label('{c.name}', color='{c.nameColor}', w='fill', text_align='center'),
            label('{c.sub}', color=MU, w='fill', text_align='center'),
            label('{c.rarity}', color='{c.rarityColor}')]}]}
    go_btn = btn('{c.go}', id='quick', w='fill', h=18, enabled='c.goOk', args={'k': 'str:48'}, argv={'k': 'c.id'}, msg=vmsg('quick', k='{k}'), **{'if': 'c.has'})
    cell = {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 2, 'repeat': {'for': 'r.cells', 'as': 'c', 'limit': 4}, 'children': [
        {k: v for k, v in card_btn.items()}, go_btn,
        {'type': 'column', 'if': '!c.has', 'w': 'fill', 'h': 'fill', 'bg': HEAD, 'border': WELL, 'children': []}]}
    rows = {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 6, 'repeat': {'for': 'vm.rows', 'as': 'r', 'limit': 2}, 'children': [cell]}
    grid = {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 6, 'children': [
        {'type': 'column', 'if': '!vm.empty', 'w': 'fill', 'h': 'fill', 'gap': 6, 'children': [rows]},
        label('{vm.emptyText}', **{'if': 'vm.empty', 'color': MU, 'w': 'fill', 'h': 'fill', 'wrap': True, 'text_align': 'center'})]}
    pager = {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'children': [
        sp(w='fill'),
        btn('<', id='prev', w=28, enabled='vm.canPrev', args={'d': 'int:-1:1'}, argv={'d': '-1'}, msg=vmsg('page', d='{d}')),
        label('{vm.pageLabel}', color=INK),
        btn('>', id='next', w=28, enabled='vm.canNext', args={'d': 'int:-1:1'}, argv={'d': '1'}, msg=vmsg('page', d='{d}')),
        sp(w='fill')]}
    left = {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 6, 'children': [coll, grid, pager]}

    # ---- detail
    line = label('{l.text}', color=INK, wrap=True, w='fill', repeat={'for': 'vm.d.lines', 'as': 'l', 'limit': 10})
    setcell = btn('{m.abbr}', id='mate', w=34, h=26, border='{m.border}', args={'k': 'str:48'}, argv={'k': 'm.id'}, msg=vmsg('open', k='{k}'),
                  repeat={'for': 'vm.d.set', 'as': 'm', 'limit': 6})
    mem = btn('', id='skin', w='fill', h=30, enabled='m.ok', args={'slot': 'int:0:5'}, argv={'slot': 'm.slot'}, msg=vmsg('skin', slot='{slot}'),
              repeat={'for': 'vm.d.members', 'as': 'm', 'limit': 6}, children=[
        {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [
            label('{m.name}', color='{m.color}'), label('{m.level}', color=MU), sp(w='fill'), label('{m.status}', color='{m.color}')]}])
    mem.pop('text')
    detail = {'type': 'column', 'if': 'vm.detail', 'w': 300, 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 8, 8, 8], 'gap': 5, 'children': [
        {'type': 'column', 'if': '!vm.partyMode', 'w': 'fill', 'h': 'fill', 'gap': 5, 'children': [
            label('{vm.d.slot}', color=MU),
            {'type': 'row', 'w': 'fill', 'cross': 'center', 'gap': 8, 'children': [
                {'type': 'image', 'pokemon': '{vm.d.thumb}', 'w': 72, 'h': 72},
                {'type': 'column', 'w': 'fill', 'gap': 2, 'children': [
                    label('{vm.d.name}', color='{vm.d.nameColor}', scale=1.2, wrap=True, w='fill'),
                    label('{vm.d.rarity}', color='{vm.d.rarityColor}'), label('{vm.d.state}', color='{vm.d.stateColor}')]}]},
            {'type': 'scroll', 'id': 'dscroll', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 3, 'children': [line]}]},
            label('{vm.d.setLabel}', **{'if': 'vm.d.hasSet', 'color': MU}),
            {'type': 'row', 'if': 'vm.d.hasSet', 'w': 'fill', 'gap': 3, 'children': [setcell]},
            label('{vm.d.status}', color='{vm.d.statusColor}'), label('{vm.d.statusNote}', color=MU, wrap=True, w='fill'),
            {'type': 'row', 'w': 'fill', 'gap': 4, 'children': [
                btn('{vm.d.tryText}', id='try', w='fill', **{'if': 'vm.d.canTry'}, msg=vmsg('try')),
                btn('{vm.d.mainText}', id='main', w='fill', bg='#12425A', border=ACC, enabled='vm.d.mainOk', msg=vmsg('main'))]}]},
        {'type': 'column', 'if': 'vm.partyMode', 'w': 'fill', 'h': 'fill', 'gap': 5, 'children': [
            label('Choose a Pokemon', color=ACC, scale=1.1), label('{vm.d.name}', color='{vm.d.nameColor}'),
            label('Loading your party...', **{'if': 'vm.d.partyLoading', 'color': MU}),
            {'type': 'column', 'w': 'fill', 'gap': 3, 'children': [mem]}, sp(h='fill')]},
        btn('{vm.d.backText}', id='back', w='fill', msg=vmsg('close'), **{'if': '!vm.partyMode'}),
        btn('{vm.d.backText}', id='back2', w='fill', msg=vmsg('back'), **{'if': 'vm.partyMode'})]}

    body = {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'children': [left, detail]}
    refresh = {'type': 'spacer', 'id': 'refresh', 'w': 0, 'h': 0, 'every_ms': 20000, 'args': {}, 'msg': {'channel': 'snobblecosmetics', 'action': 'refresh'}}
    root = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': SCR, 'border': LN, 'pad': [10, 10, 10, 10], 'gap': 6, 'children': [
        head, {'type': 'row', 'w': 'fill', 'gap': 4, 'children': [tabs]}, body, refresh]}
    return {'v': 2, 'id': 'wardrobe', 'channel': 'snobblecosmetics', 'hello': 'ready', 'bye': True, 'title': 'Wardrobe', 'width': 940, 'height': 600, 'root': root}


d = wardrobe()
with open(os.path.join(OUT, 'wardrobe.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, indent=1)
print('wrote wardrobe')
