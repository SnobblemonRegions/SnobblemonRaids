"""Generates the native Shellpack screen (shellpack.json): shell grid (scrolls for big tiers), inventory grid, snail meter, left tabs with
info / options / upgrade. Every rule stays in the Shellpack mod; WardrobeViews-style view (ShellpackViews) holds the picked-up stack."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
SCR, PANEL, HEAD, ROW, WELL, LN = '#1B1510', '#2A2118', '#352A1E', '#3B2F22', '#241B13', '#8A6A3A'
INK, MU, ACC, GOOD, WARN = '#F3E7CC', '#BFA97E', '#E2B35C', '#8FD08A', '#FF8F7A'
SLOT, HOV = '#241B13', '#4A3A28'
# the web page changed colours per tier; the native screen keeps one warm look


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
    d = {'type': 'button', 'text': text, 'h': 20, 'pad': [2, 8, 2, 8], 'cross': 'center', 'align': 'center', 'bg': PANEL, 'border': LN, 'radius': 1,
         'hover': HOV, 'disabled': '#1A140E', 'color': INK}
    d.update(kw)
    return d


def slot_cell(nid):
    tip = ['{c.name}', '{c.t0}', '{c.t1}', '{c.t2}', '{c.t3}', '{c.t4}', '{c.t5}', '{c.warn}']
    inner = {'type': 'column', 'w': 'fill', 'h': 'fill', 'cross': 'center', 'children': [
        {'type': 'item', 'if': 'c.has && !c.shell', 'item': '{c.item}', 'count': 1, 'w': 22, 'h': 22},
        {'type': 'image', 'if': 'c.has && c.shell', 'pokemon': '{c.art}', 'w': 22, 'h': 22},
        label('{c.n}', color=INK, **{'if': 'c.has'}),
        {'type': 'bar', 'if': 'c.dmg', 'w': 26, 'h': 2, 'value': 'c.dpct', 'max': '100', 'fill': '{c.dcol}', 'fill_bg': '#000000'}]}
    return {'type': 'button', 'id': nid, 'w': 36, 'h': 38, 'bg': '{c.bg}', 'border': '{c.border}', 'hover': HOV, 'radius': 1, 'pad': [3, 2, 1, 2],
            'args': {'pane': 'enum:s|p', 'idx': 'int:0:215', 'btn': 'int:0:1', 'shift': 'int:0:1'},
            'argv': {'pane': 'c.pane', 'idx': 'c.idx', 'btn': '0', 'shift': '0'},
            'msg': vmsg('slot', pane='{pane}', idx='{idx}', btn='{btn}', shift='{shift}'),
            'tooltip': tip, 'repeat': {'for': 'r.cells', 'as': 'c', 'limit': 12}, 'children': [inner]}


def grid(src, limit, rvar='r'):
    return {'type': 'column', 'w': 'fill', 'gap': 2, 'children': [
        {'type': 'row', 'gap': 2, 'repeat': {'for': src, 'as': rvar, 'limit': limit}, 'pad': [0, 0, 0, 0], 'children': [slot_cell(src)]}]}


def shellpack():
    close = btn('X', id='close', w=26, bg='#3A1A14', border=WARN, hover='#5A281E', close=True)
    title = {'type': 'row', 'w': 'fill', 'bg': HEAD, 'border': LN, 'pad': [4, 8, 4, 8], 'gap': 6, 'cross': 'center', 'children': [
        {'type': 'image', 'pokemon': '{vm.icon}', 'w': 18, 'h': 18}, label('{vm.title}', color=ACC, scale=1.2), sp(w='fill'),
        btn('SORT', id='sort', w=44, msg=vmsg('sort')),

        close]}
    meter = {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'children': [
        {'type': 'bar', 'w': 'fill', 'h': 8, 'value': 'vm.pct', 'max': '100', 'fill': ACC, 'fill_bg': WELL, 'radius': 1}, label('{vm.meter}', color=MU)]}
    held = label('{vm.held}', **{'if': 'vm.hasHeld', 'color': ACC, 'w': 'fill', 'wrap': True})
    shell_scroll = {'type': 'scroll', 'id': 'shellscroll', 'w': 'fill', 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [4, 4, 4, 4], 'children': [
        {'type': 'column', 'w': 'fill', 'gap': 2, 'children': [
            {'type': 'row', 'gap': 2, 'repeat': {'for': 'vm.shellRows', 'as': 'r', 'limit': 12}, 'children': [slot_cell('slot_s')]}]}]}
    inv_head = {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [
        label('Inventory', color=ACC), sp(w='fill'),
        btn('STOW', id='stow', w=52, msg=vmsg('stow')), btn('TAKE OUT', id='withdraw', w=64, msg=vmsg('withdraw'))]}
    inv = {'type': 'column', 'w': 'fill', 'bg': PANEL, 'border': LN, 'pad': [4, 4, 4, 4], 'gap': 2, 'children': [
        {'type': 'row', 'gap': 2, 'repeat': {'for': 'vm.invRows', 'as': 'r', 'limit': 4}, 'children': [slot_cell('slot_p')]}]}
    main = {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 5, 'children': [title, shell_scroll, meter, held, inv_head, inv]}

    def tab(k, text):
        return btn(text, id='tab_' + k, w=30, h=30, border='{vm.tab_%s}' % k, args={'k': 'str:8'}, argv={'k': "'%s'" % k}, msg=vmsg('tab', k='{k}'))
    tabs = {'type': 'column', 'w': 30, 'gap': 4, 'children': [tab('info', 'i'), tab('opt', '+'), tab('up', '^')]}
    modes = btn('{o.label}', id='opt', w='fill', h=20, border='{o.border}', args={'k': 'int:0:2'}, argv={'k': 'o.k'}, msg=vmsg('mode', k='{k}'),
                repeat={'for': 'vm.rowsT', 'as': 'o', 'limit': 3}, **{'if': 'vm.pOpt'})
    tiers = {'type': 'row', 'w': 'fill', 'gap': 6, 'repeat': {'for': 'vm.rowsT', 'as': 't', 'limit': 6}, 'if': 'vm.pUp', 'children': [
        label('{t.name}', color='{t.color}'), sp(w='fill'), label('{t.slots}', color=MU)]}
    cost = {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'repeat': {'for': 'vm.cost', 'as': 'k', 'limit': 6}, 'children': [
        {'type': 'item', 'item': '{k.item}', 'count': 1, 'w': 18, 'h': 18}, label('{k.text}', color='{k.color}', wrap=True, w='fill')]}
    panel = {'type': 'column', 'if': 'vm.panel', 'w': 220, 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 8, 8, 8], 'gap': 5, 'children': [
        label('{vm.pTitle}', color=ACC, scale=1.1),
        {'type': 'scroll', 'id': 'pscroll', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 4, 'children': [
            label('{l.text}', color=INK, wrap=True, w='fill', repeat={'for': 'vm.lines', 'as': 'l', 'limit': 8}), modes, tiers, cost]}]},
        btn('{vm.upText}', id='upgrade', w='fill', h=24, bg='#5A431F', border=ACC, enabled='vm.canUp', msg=vmsg('upgrade'), **{'if': 'vm.pUp && vm.upText'})]}
    ping = {'type': 'spacer', 'id': 'ping', 'w': 0, 'h': 0, 'every_ms': 3000, 'args': {}, 'msg': {'channel': 'shellpack', 'action': 'ping'}}
    root = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': SCR, 'border': LN, 'pad': [10, 10, 10, 10], 'children': [
        {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'children': [tabs, panel, main]}, ping]}
    return {'v': 2, 'id': 'shellpack', 'channel': 'shellpack', 'hello': 'ready', 'bye': True, 'bye_action': 'close', 'title': 'Shellpack', 'width': 820, 'height': 560, 'root': root}


d = shellpack()
with open(os.path.join(OUT, 'shellpack.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, indent=1)
print('wrote shellpack')
