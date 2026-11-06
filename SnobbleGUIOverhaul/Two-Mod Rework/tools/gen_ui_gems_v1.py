"""Generates the native SnobbleGems shop (snobblegems_shop.json): balance + categories on the left, product grid, detail strip, confirm window."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
SCR, PANEL, HEAD, ROW, WELL, LN = '#120A26', '#1A0F38', '#24144A', '#1E1240', '#0E0720', '#4A3A86'
INK, MU, ACC, GOOD, WARN, PRISM = '#FBF6FF', '#8F82B8', '#B58CFF', '#62D06A', '#FF7A88', '#D9A8FF'
HOV = '#3A2278'


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
         'hover': HOV, 'disabled': '#140B2A', 'color': INK}
    d.update(kw)
    return d


def price_row(var, size=14, color='{%s.priceColor}'):
    return {'type': 'row', 'gap': 4, 'cross': 'center', 'children': [
        {'type': 'image', 'pokemon': 'a:gems_gem', 'w': size, 'h': size}, label('{%s.price}' % var, color=color % var if '%s' in color else color)]}


def icon(var, size):
    return [{'type': 'item', 'if': '!%s.isArt' % var, 'item': '{%s.item}' % var, 'count': 1, 'w': size, 'h': size},
            {'type': 'image', 'if': '%s.isArt' % var, 'pokemon': '{%s.art}' % var, 'w': size, 'h': size}]


def shop():
    cat = btn('', id='cat', w='fill', h=26, bg='{k.bg}', border='{k.border}', args={'k': 'str:24'}, argv={'k': 'k.id'}, msg=vmsg('cat', k='{k}'),
              repeat={'for': 'vm.cats', 'as': 'k', 'limit': 12}, children=[
        {'type': 'row', 'w': 'fill', 'cross': 'center', 'children': [label('{k.name}', color=INK), sp(w='fill'), label('{k.count}', color=MU)]}])
    cat.pop('text')
    left = {'type': 'column', 'w': 230, 'h': 'fill', 'gap': 6, 'children': [
        {'type': 'column', 'w': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 10, 8, 10], 'gap': 4, 'children': [
            label('{vm.yourCur}', color=ACC, scale=1.1),
            {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [{'type': 'image', 'pokemon': 'a:gems_gem', 'w': 22, 'h': 22}, label('{vm.balance}', color=INK, scale=1.5)]},
            {'type': 'row', 'w': 'fill', 'children': [label('Earned today', color=MU), sp(w='fill'), label('{vm.earned}', color=INK)]},
            {'type': 'row', 'w': 'fill', 'children': [label('Items for sale', color=MU), sp(w='fill'), label('{vm.forSale}', color=INK)]}]},
        label('Categories', color=ACC, pad=[4, 4, 0, 4]),
        {'type': 'column', 'w': 'fill', 'gap': 3, 'children': [cat]},
        sp(h='fill'),
        label('{vm.tip}', color=MU, wrap=True, w='fill')]}

    card = {'type': 'button', 'id': 'card', 'w': 'fill', 'h': 'fill', 'bg': '{c.bg}', 'border': '{c.border}', 'hover': HOV, 'radius': 2, 'pad': [4, 4, 4, 4],
            'args': {'k': 'str:48'}, 'argv': {'k': 'c.id'}, 'msg': vmsg('sel', k='{k}'), 'if': 'c.has', 'children': [
        {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 2, 'cross': 'center', 'children': [
            {'type': 'row', 'w': 'fill', 'children': [label('*', color='#FFD75A', **{'if': 'c.star'}), label('{c.timer}', color='#F6D2FF'), sp(w='fill'), label('{c.qty}', color=INK)]},
            icon('c', 36)[0], icon('c', 36)[1],
            label('{c.name}', color='{c.color}', wrap=True, w='fill', text_align='center'), sp(h='fill'),
            {'type': 'row', 'gap': 4, 'cross': 'center', 'children': [{'type': 'image', 'pokemon': 'a:gems_gem', 'w': 12, 'h': 12}, label('{c.price}', color='{c.priceColor}')]}]}]}
    empty = {'type': 'column', 'if': '!c.has', 'w': 'fill', 'h': 'fill', 'children': []}
    cell = {'type': 'column', 'w': 'fill', 'h': 'fill', 'repeat': {'for': 'r.cells', 'as': 'c', 'limit': 5}, 'children': [card, empty]}
    rows = {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 6, 'repeat': {'for': 'vm.rows', 'as': 'r', 'limit': 2}, 'children': [cell]}
    grid = {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 6, 'children': [
        {'type': 'column', 'if': '!vm.empty', 'w': 'fill', 'h': 'fill', 'gap': 6, 'children': [rows]},
        label('{vm.emptyText}', **{'if': 'vm.empty', 'color': MU, 'w': 'fill', 'h': 'fill', 'wrap': True, 'text_align': 'center'})]}
    pager = {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'if': 'vm.paged', 'children': [
        sp(w='fill'), btn('<', id='prev', w=28, enabled='vm.canPrev', args={'d': 'int:-1:1'}, argv={'d': '-1'}, msg=vmsg('page', d='{d}')),
        label('{vm.pageText}', color=INK),
        btn('>', id='next', w=28, enabled='vm.canNext', args={'d': 'int:-1:1'}, argv={'d': '1'}, msg=vmsg('page', d='{d}')), sp(w='fill')]}
    detail = {'type': 'row', 'w': 'fill', 'h': 74, 'bg': PANEL, 'border': LN, 'pad': [6, 10, 6, 10], 'gap': 10, 'cross': 'center', 'children': [
        {'type': 'column', 'w': 'fill', 'gap': 1, 'if': 'vm.hasSel', 'children': [
            label('{vm.sel.name}', color='{vm.sel.color}', scale=1.2), label('{vm.sel.desc}', color=INK, wrap=True, w='fill'),
            label('{vm.sel.limit}', color='{vm.sel.limitColor}'), label('{vm.sel.ends}', color='#F6D2FF')]},
        label('Select an item', color=MU, **{'if': '!vm.hasSel', 'w': 'fill'}),
        {'type': 'row', 'if': 'vm.hasSel', 'gap': 4, 'cross': 'center', 'children': [
            {'type': 'image', 'pokemon': 'a:gems_gem', 'w': 18, 'h': 18}, label('{vm.sel.price}', color='{vm.sel.priceColor}', scale=1.4)]},
        btn('BUY', id='buy', w=80, h=32, bg='#5A32B4', border='#E7C8FF', enabled='vm.sel.ok', msg=vmsg('quote'), **{'if': 'vm.hasSel'})]}
    close = btn('X', id='close', w=28, bg='#3A1A2A', border=WARN, hover='#5A2A3E', close=True)
    head = {'type': 'row', 'w': 'fill', 'bg': HEAD, 'border': LN, 'pad': [6, 10, 6, 10], 'gap': 10, 'cross': 'center', 'children': [
        label('{vm.title}', color=ACC, scale=1.4), label('/ {vm.catName}', color=PRISM, scale=1.1), sp(w='fill'),
        label('PURCHASED! {vm.bannerName}', color='#FFD75A', anim='pop', anim_ms=300, **{'if': 'vm.banner'}), close]}
    normal = {'type': 'column', 'if': '!vm.modal', 'w': 'fill', 'h': 'fill', 'gap': 6, 'children': [
        label('{vm.catDesc}', color=MU, wrap=True, w='fill'), grid, pager, detail]}
    modal = {'type': 'column', 'if': 'vm.modal', 'w': 'fill', 'h': 'fill', 'cross': 'center', 'pad': [30, 60, 10, 60], 'gap': 10, 'children': [
        label('CONFIRM PURCHASE', color=ACC, scale=1.5),
        {'type': 'column', 'w': 460, 'bg': PANEL, 'border': ACC, 'pad': [14, 20, 14, 20], 'gap': 8, 'cross': 'center', 'anim': 'pop', 'anim_ms': 220, 'children': [
            icon('vm.q', 48)[0], icon('vm.q', 48)[1],
            label('{vm.q.name}', color='{vm.q.color}', scale=1.3), label('{vm.q.desc}', color=MU, wrap=True, w='fill', text_align='center'),
            {'type': 'row', 'w': 'fill', 'children': [label('Price', color=MU), sp(w='fill'), {'type': 'image', 'pokemon': 'a:gems_gem', 'w': 14, 'h': 14}, label('{vm.q.price}', color=PRISM)]},
            {'type': 'row', 'w': 'fill', 'children': [label('Your balance', color=MU), sp(w='fill'), {'type': 'image', 'pokemon': 'a:gems_gem', 'w': 14, 'h': 14}, label('{vm.q.balance}', color=INK)]},
            {'type': 'row', 'w': 'fill', 'children': [label('Balance after', color=MU), sp(w='fill'), {'type': 'image', 'pokemon': 'a:gems_gem', 'w': 14, 'h': 14}, label('{vm.q.after}', color=PRISM)]},
            label('{vm.q.held}', color=MU),
            {'type': 'row', 'w': 'fill', 'gap': 10, 'children': [
                btn('CONFIRM', id='confirm', w='fill', h=28, bg='#5A32B4', border='#E7C8FF', msg=vmsg('confirm')),
                btn('CANCEL', id='cancel', w='fill', h=28, msg=vmsg('cancel'))]}]}]}
    right = {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 6, 'children': [head, normal, modal]}
    tick = {'type': 'spacer', 'id': 'tick', 'w': 0, 'h': 0, 'every_ms': 1000, 'args': {}, 'msg': vmsg('tick')}
    ping = {'type': 'spacer', 'id': 'ping', 'w': 0, 'h': 0, 'every_ms': 4000, 'args': {}, 'msg': {'channel': 'snobblegems', 'kind': 'ping'}}
    root = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': SCR, 'border': LN, 'pad': [10, 10, 10, 10], 'children': [
        {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'children': [left, right]}, tick, ping]}
    return {'v': 2, 'id': 'snobblegems_shop', 'channel': 'snobblegems', 'hello': 'hello', 'bye': True, 'title': 'Gem Shop', 'width': 960, 'height': 580, 'root': root}


d = shop()
with open(os.path.join(OUT, 'snobblegems_shop.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, indent=1)
print('wrote snobblegems_shop')
