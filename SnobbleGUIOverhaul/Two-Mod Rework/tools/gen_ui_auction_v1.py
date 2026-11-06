"""Generates the native Auction House page (auction.json): browse grid (Pokemon + items), sell Pokemon / items, my listings + sales,
listing detail, confirm window. All logic lives in AuctionViews; SnobbleAuctions decides every price, tax, slot and purchase."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
SCR, PANEL, HEAD, ROW, WELL, LN = '#11181F', '#1A242D', '#202D38', '#18222B', '#0C1218', '#2A4658'
INK, MU, ACC, GOOD, WARN, GOLD = '#E4ECF1', '#8FA0B3', '#4AD8F6', '#62D06A', '#FF7A6B', '#E7B65C'
HOV = '#2A4052'


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
         'hover': HOV, 'disabled': '#10171D', 'color': INK}
    d.update(kw)
    return d


def kbtn(text, action, src, var, **kw):
    """a button repeated over vm list `src` that sends its own key"""
    return btn(text, args={'k': 'str:24'}, argv={'k': var + '.k'}, msg=vmsg(action, k='{k}'), border='{%s.border}' % var, repeat={'for': src, 'as': var, 'limit': 10}, **kw)


def art(var, size, extra=None):
    a = {'type': 'item', 'if': '%s.isItem' % var, 'item': '{%s.item}' % var, 'count': 1, 'w': size, 'h': size}
    b = {'type': 'image', 'if': '!%s.isItem' % var, 'pokemon': '{%s.sprite}' % var, 'w': size, 'h': size}
    return [a, b]


def auction():
    close = btn('X', id='close', w=28, bg='#3A1A1A', border=WARN, hover='#5A2A2A', close=True)
    title = {'type': 'row', 'w': 'fill', 'bg': HEAD, 'border': LN, 'pad': [6, 10, 6, 10], 'gap': 14, 'cross': 'center', 'children': [
        label('{vm.title}', color=GOLD, scale=1.4), sp(w='fill'),
        label('Slots', color=MU), label('{vm.slots}', color=INK), label('Balance', color=MU), label('{vm.balance}', color=GOLD), close]}
    nav = {'type': 'row', 'w': 'fill', 'gap': 4, 'cross': 'center', 'children': [
        btn('BROWSE', id='nav_b', w=90, border='{vm.nav_browse}', args={'k': 'str:8'}, argv={'k': "'browse'"}, msg=vmsg('nav', k='{k}')),
        btn('SELL', id='nav_s', w=70, border='{vm.nav_sell}', args={'k': 'str:8'}, argv={'k': "'sell'"}, msg=vmsg('nav', k='{k}')),
        btn('MY LISTINGS', id='nav_m', w=110, border='{vm.nav_mine}', args={'k': 'str:8'}, argv={'k': "'mine'"}, msg=vmsg('nav', k='{k}')),
        sp(w=20),
        btn('POKEMON', id='tab_m', w=90, border='{vm.tab_mon}', args={'k': 'str:8'}, argv={'k': "'mon'"}, msg=vmsg('tab', k='{k}')),
        btn('ITEMS', id='tab_i', w=70, border='{vm.tab_item}', args={'k': 'str:8'}, argv={'k': "'item'"}, msg=vmsg('tab', k='{k}'), **{'if': 'vm.allowItems'})]}

    # ------------------------------------------------------------ left rail
    search = {'type': 'input', 'id': 'search', 'bind': 'q', 'hint': 'search...', 'w': 'fill', 'h': 22, 'max_len': 40, 'bg': WELL, 'border': LN, 'radius': 2, 'color': INK,
              'debounce_ms': 450, 'args': {'q': 'str:40'}, 'argv': {'q': '$q'}, 'msg': vmsg('search', q='{q}')}
    rail = {'type': 'column', 'w': 200, 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 8, 8, 8], 'gap': 4, 'children': [
        {'type': 'column', 'if': 'vm.isBrowse', 'w': 'fill', 'gap': 4, 'children': [
            label('SEARCH', color=ACC), search,
            label('FILTER', color=ACC, **{'if': 'vm.isMonTab'}),
            btn('{f.mark}{f.label}', id='flt', w='fill', h=18, args={'k': 'str:16'}, argv={'k': 'f.k'}, msg=vmsg('flt', k='{k}'), border='{f.border}', repeat={'for': 'vm.filters', 'as': 'f', 'limit': 5}, **{'if': 'vm.isMonTab'}),
            label('CATEGORY', color=ACC, **{'if': 'vm.isItemTab'}),
            btn('{c.label}', id='cat', w='fill', h=18, args={'k': 'str:16'}, argv={'k': 'c.k'}, msg=vmsg('cat', k='{k}'), border='{c.border}', repeat={'for': 'vm.cats', 'as': 'c', 'limit': 6}, **{'if': 'vm.isItemTab'}),
            btn('{vm.modLabel}', id='mod', w='fill', h=18, msg=vmsg('mod'), **{'if': 'vm.isItemTab'}),
            label('SORT', color=ACC),
            btn('{s.label}', id='sort', w='fill', h=18, args={'k': 'str:16'}, argv={'k': 's.k'}, msg=vmsg('sort', k='{k}'), border='{s.border}', repeat={'for': 'vm.sorts', 'as': 's', 'limit': 5})]},
        {'type': 'column', 'if': '!vm.isBrowse', 'w': 'fill', 'gap': 4, 'children': [
            label('AUCTION HOUSE', color=ACC), label('{vm.hint}', color=MU, wrap=True, w='fill')]}]}

    # ------------------------------------------------------------ browse
    card = {'type': 'button', 'id': 'card', 'w': 'fill', 'h': 150, 'bg': ROW, 'border': '{c.border}', 'hover': HOV, 'radius': 2, 'pad': [4, 4, 4, 4],
            'args': {'k': 'str:48'}, 'argv': {'k': 'c.id'}, 'msg': vmsg('detail', k='{k}'), 'if': '!c.empty',
            'tooltip': ['{c.t0}', '{c.t1}', '{c.t2}', '{c.t3}', '{c.t4}', '{c.t5}', '{c.t6}', '{c.t7}'], 'children': [
        {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 2, 'cross': 'center', 'children': [
            {'type': 'row', 'w': 'fill', 'children': [sp(w='fill'), label('{c.cnt}', color=INK)]},
            art('c', 52)[0], art('c', 52)[1],
            label('{c.name}', color='{c.color}', w='fill', text_align='center'),
            label('{c.sub}', color=MU, w='fill', text_align='center'),
            label('{c.tags}', color=GOLD, w='fill', text_align='center'), sp(h='fill'),
            {'type': 'row', 'w': 'fill', 'children': [label('{c.time}', color=MU), sp(w='fill'), label('{c.price}', color=GOLD)]}]}]}
    cell = {'type': 'column', 'w': 'fill', 'repeat': {'for': 'r.cells', 'as': 'c', 'limit': 4}, 'children': [card]}
    browse = {'type': 'column', 'if': 'vm.isBrowse && !vm.overlay', 'w': 'fill', 'h': 'fill', 'gap': 4, 'children': [
        {'type': 'scroll', 'id': 'bscroll', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 6, 'children': [
            {'type': 'row', 'w': 'fill', 'gap': 6, 'repeat': {'for': 'vm.rows', 'as': 'r', 'limit': 6}, 'children': [cell]},
            label('{vm.noneText}', color=MU, w='fill', text_align='center', wrap=True)]}]},
        {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'children': [
            sp(w='fill'), btn('< PREV', id='prev', w=70, enabled='vm.canPrev', args={'d': 'int:-1:1'}, argv={'d': '-1'}, msg=vmsg('page', d='{d}')),
            label('{vm.pageLabel}', color=MU),
            btn('NEXT >', id='next', w=70, enabled='vm.canNext', args={'d': 'int:-1:1'}, argv={'d': '1'}, msg=vmsg('page', d='{d}')), sp(w='fill')]}]}

    # ------------------------------------------------------------ sell pokemon
    prow = btn('', id='party', w='fill', h=38, border='{p.border}', enabled='p.ok', args={'n': 'int:0:5'}, argv={'n': 'p.n'}, msg=vmsg('sellsel', n='{n}'),
               repeat={'for': 'vm.party', 'as': 'p', 'limit': 6}, children=[
        {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [
            {'type': 'image', 'if': '!p.isEmpty', 'pokemon': '{p.sprite}', 'w': 30, 'h': 30},
            {'type': 'column', 'w': 'fill', 'children': [label('{p.name}', color='{p.color}'), label('{p.meta}', color=MU)]}]}])
    prow.pop('text')
    price = {'type': 'input', 'id': 'price', 'bind': 'price', 'hint': 'price', 'w': 'fill', 'h': 24, 'max_len': 12, 'bg': WELL, 'border': GOLD, 'radius': 2, 'color': GOLD,
             'debounce_ms': 250, 'args': {'v': 'str:12'}, 'argv': {'v': '$price'}, 'msg': vmsg('price', v='{v}')}
    sellmon = {'type': 'row', 'if': 'vm.isSellMon && !vm.overlay', 'w': 'fill', 'h': 'fill', 'gap': 8, 'children': [
        {'type': 'column', 'w': 260, 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 8, 8, 8], 'gap': 4, 'children': [label('YOUR PARTY', color=ACC), prow]},
        {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 10, 8, 10], 'gap': 6, 'children': [
            label('SET YOUR PRICE', color=ACC),
            label('Pick a Pokemon from your party.', color=MU, **{'if': '!vm.sellPick'}),
            {'type': 'column', 'if': 'vm.sellPick', 'w': 'fill', 'gap': 6, 'children': [
                {'type': 'row', 'gap': 10, 'cross': 'center', 'children': [
                    {'type': 'image', 'pokemon': '{vm.sm.sprite}', 'w': 72, 'h': 72},
                    {'type': 'column', 'gap': 2, 'children': [label('{vm.sm.name}', color=INK, scale=1.3), label('{vm.sm.meta}', color=MU), label('{vm.sm.tags}', color=GOLD)]}]},
                {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [label('$', color=GOLD, scale=1.3), price]},
                label('{vm.sm.help}', color='{vm.sm.helpColor}'),
                {'type': 'row', 'w': 'fill', 'children': [label('{vm.sm.tax}', color=MU), sp(w='fill'), label('{vm.sm.receive}', color=GOLD)]},
                btn('LIST FOR SALE', id='list', w='fill', h=28, bg='#5A431F', border=GOLD, enabled='vm.sm.ok', msg=vmsg('list'))]}]}]}

    # ------------------------------------------------------------ sell items
    islot = {'type': 'button', 'id': 'islot', 'w': 34, 'h': 34, 'bg': WELL, 'border': '{c.border}', 'hover': HOV, 'radius': 1, 'pad': [2, 2, 2, 2],
             'args': {'n': 'int:0:40'}, 'argv': {'n': 'c.n'}, 'msg': vmsg('isel', n='{n}'), 'enabled': 'c.has', 'tooltip': ['{c.name}'],
             'repeat': {'for': 'r.cells', 'as': 'c', 'limit': 9}, 'children': [
        {'type': 'item', 'if': 'c.has', 'item': '{c.item}', 'count': 1, 'w': 24, 'h': 24}]}
    iprice = {'type': 'input', 'id': 'iprice', 'bind': 'iprice', 'hint': 'price', 'w': 'fill', 'h': 24, 'max_len': 12, 'bg': WELL, 'border': GOLD, 'radius': 2, 'color': GOLD,
              'debounce_ms': 250, 'args': {'v': 'str:12'}, 'argv': {'v': '$iprice'}, 'msg': vmsg('iprice', v='{v}')}
    sellitem = {'type': 'row', 'if': 'vm.isSellItem && !vm.overlay', 'w': 'fill', 'h': 'fill', 'gap': 8, 'children': [
        {'type': 'column', 'w': 'content', 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 8, 8, 8], 'gap': 3, 'children': [
            label('YOUR INVENTORY', color=ACC),
            {'type': 'row', 'gap': 2, 'repeat': {'for': 'vm.invRows', 'as': 'r', 'limit': 4}, 'pad': [0, 0, 0, 0], 'children': [islot]},
            label('Dimmed items cannot be sold on this server.', color=MU)]},
        {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 10, 8, 10], 'gap': 6, 'children': [
            label('SET YOUR PRICE', color=ACC),
            label('Pick an item from your inventory.', color=MU, **{'if': '!vm.itemPick'}),
            {'type': 'column', 'if': 'vm.itemPick', 'w': 'fill', 'gap': 6, 'children': [
                {'type': 'row', 'gap': 10, 'cross': 'center', 'children': [
                    {'type': 'item', 'item': '{vm.si.item}', 'count': 1, 'w': 48, 'h': 48},
                    {'type': 'column', 'gap': 2, 'children': [label('{vm.si.name}', color=INK, scale=1.3), label('{vm.si.mod}', color=GOLD), label('{vm.si.have}', color=MU)]}]},
                {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [
                    label('QUANTITY', color=MU),
                    btn('-', id='qm', w=26, args={'d': 'int:-1:1', 'k': 'str:4'}, argv={'d': '-1', 'k': "'n'"}, msg=vmsg('qty', d='{d}', k='{k}')),
                    label('{vm.si.qty}', color=INK),
                    btn('+', id='qp', w=26, args={'d': 'int:-1:1', 'k': 'str:4'}, argv={'d': '1', 'k': "'n'"}, msg=vmsg('qty', d='{d}', k='{k}')),
                    btn('ALL', id='qa', w=40, args={'d': 'int:-1:1', 'k': 'str:4'}, argv={'d': '0', 'k': "'all'"}, msg=vmsg('qty', d='{d}', k='{k}'))]},
                {'type': 'row', 'w': 'fill', 'gap': 4, 'children': [
                    btn('PRICE FOR ALL', id='mall', w='fill', border='{vm.si.modeAll}', args={'k': 'str:4'}, argv={'k': "'all'"}, msg=vmsg('imode', k='{k}')),
                    btn('PRICE EACH', id='meach', w='fill', border='{vm.si.modeEach}', args={'k': 'str:4'}, argv={'k': "'each'"}, msg=vmsg('imode', k='{k}'))]},
                {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [label('$', color=GOLD, scale=1.3), iprice]},
                label('{vm.si.help}', color='{vm.si.helpColor}'),
                label('{vm.si.total}', color=GOLD),
                {'type': 'row', 'w': 'fill', 'children': [label('{vm.si.tax}', color=MU), sp(w='fill'), label('{vm.si.receive}', color=GOLD)]},
                btn('LIST FOR SALE', id='ilist', w='fill', h=28, bg='#5A431F', border=GOLD, enabled='vm.si.ok', msg=vmsg('ilist'))]}]}]}

    # ------------------------------------------------------------ my listings
    lrow = {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'bg': ROW, 'border': LN, 'pad': [4, 8, 4, 8], 'repeat': {'for': 'vm.mine', 'as': 'm', 'limit': 12}, 'children': [
        art('m', 28)[0], art('m', 28)[1],
        {'type': 'column', 'w': 'fill', 'children': [label('{m.name}', color=INK), label('{m.price}', color=GOLD), label('{m.status}', color='{m.statusColor}')]},
        btn('{m.actText}', id='cancel', w=100, enabled='m.canAct', args={'k': 'str:48'}, argv={'k': 'm.id'}, msg=vmsg('cancelask', k='{k}'), **{'if': 'm.active'}),
        btn('COLLECT', id='collect', w=100, bg='#5A431F', border=GOLD, args={'k': 'str:48'}, argv={'k': 'm.id'}, msg=vmsg('collect', k='{k}'), **{'if': '!m.active'})]}
    srow = {'type': 'row', 'w': 'fill', 'gap': 6, 'bg': ROW, 'border': LN, 'pad': [4, 8, 4, 8], 'repeat': {'for': 'vm.sales', 'as': 's', 'limit': 12}, 'children': [
        label('{s.text}', color=INK, w='fill', wrap=True), label('{s.gain}', color=GOOD)]}
    mine = {'type': 'row', 'if': 'vm.isMine && !vm.overlay', 'w': 'fill', 'h': 'fill', 'gap': 8, 'children': [
        {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 8, 8, 8], 'gap': 4, 'children': [
            label('MY LISTINGS', color=ACC),
            {'type': 'scroll', 'id': 'mscroll', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 3, 'children': [
                lrow, label('You have nothing listed. Use SELL to start.', color=MU, **{'if': 'vm.noMine'})]}]}]},
        {'type': 'column', 'w': 280, 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 8, 8, 8], 'gap': 4, 'children': [
            label('RECENT SALES', color=ACC),
            {'type': 'scroll', 'id': 'sscroll', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 3, 'children': [
                srow, label('No sales yet.', color=MU, **{'if': 'vm.noSales'})]}]}]}]}

    # ------------------------------------------------------------ detail + confirm
    iv = {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'repeat': {'for': 'vm.d.ivs', 'as': 'i', 'limit': 6}, 'children': [
        label('{i.stat}', color=MU, w=34), {'type': 'bar', 'w': 'fill', 'h': 6, 'value': 'i.v', 'max': '31', 'fill': '{i.color}', 'fill_bg': WELL, 'radius': 1}, label('{i.v}', color=INK, w=20)]}
    detail = {'type': 'column', 'if': 'vm.showDetail', 'w': 'fill', 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [12, 14, 12, 14], 'gap': 8, 'children': [
        {'type': 'row', 'w': 'fill', 'gap': 12, 'cross': 'center', 'children': [
            {'type': 'item', 'if': 'vm.d.isItem', 'item': '{vm.d.item}', 'count': 1, 'w': 64, 'h': 64},
            {'type': 'image', 'if': '!vm.d.isItem', 'pokemon': '{vm.d.sprite}', 'w': 110, 'h': 110},
            {'type': 'column', 'w': 'fill', 'gap': 3, 'children': [label('{vm.d.name}', color=INK, scale=1.5), label('{vm.d.meta}', color=MU), label('{vm.d.tags}', color=GOLD)]}]},
        {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 12, 'children': [
            {'type': 'column', 'if': 'vm.d.isMon', 'w': 260, 'gap': 4, 'children': [label('INDIVIDUAL VALUES', color=ACC), iv]},
            {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 3, 'children': [
                label('MOVES', color=ACC, **{'if': 'vm.d.isMon'}), label('DETAILS', color=ACC, **{'if': '!vm.d.isMon'}),
                {'type': 'scroll', 'id': 'dscroll', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 2, 'children': [
                    label('{l.text}', color=INK, wrap=True, w='fill', repeat={'for': 'vm.d.lines', 'as': 'l', 'limit': 16})]}]}]}]},
        label('{vm.d.extra}', color=MU),
        {'type': 'row', 'w': 'fill', 'gap': 10, 'cross': 'center', 'children': [
            label('{vm.d.price}', color=GOLD, scale=1.6), label('{vm.d.time}', color=MU), sp(w='fill'),
            btn('BACK', id='back', w=80, h=28, msg=vmsg('back')),
            btn('{vm.d.buyText}', id='buy', w=150, h=28, bg='#5A431F', border=GOLD, enabled='vm.d.buyOk', msg=vmsg('buy'))]}]}
    confirm = {'type': 'column', 'if': 'vm.showConfirm', 'w': 'fill', 'h': 'fill', 'cross': 'center', 'pad': [60, 80, 10, 80], 'gap': 10, 'children': [
        {'type': 'column', 'w': 520, 'bg': PANEL, 'border': GOLD, 'pad': [14, 20, 14, 20], 'gap': 8, 'cross': 'center', 'anim': 'pop', 'anim_ms': 200, 'children': [
            label('{vm.cf.title}', color=GOLD, scale=1.4), label('{vm.cf.text}', color=INK, wrap=True, w='fill', text_align='center'),
            label('{l.text}', color=MU, wrap=True, w='fill', text_align='center', repeat={'for': 'vm.cf.math', 'as': 'l', 'limit': 6}),
            {'type': 'row', 'w': 'fill', 'gap': 10, 'children': [
                btn('{vm.cf.yes}', id='yes', w='fill', h=28, bg='#5A431F', border=GOLD, msg=vmsg('yes')),
                btn('{vm.cf.no}', id='no', w='fill', h=28, msg=vmsg('no'))]}]}]}

    main = {'type': 'column', 'w': 'fill', 'h': 'fill', 'children': [browse, sellmon, sellitem, mine, detail, confirm]}
    tick = {'type': 'spacer', 'id': 'tick', 'w': 0, 'h': 0, 'every_ms': 1000, 'args': {}, 'msg': vmsg('tick')}
    root = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': SCR, 'border': LN, 'pad': [10, 10, 10, 10], 'gap': 6, 'children': [
        title, nav, {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'children': [rail, main]}, tick]}
    return {'v': 2, 'id': 'auction', 'channel': 'snobbleauctions', 'hello': 'ready', 'bye': True, 'title': 'Auction House', 'width': 1000, 'height': 620,
            'state': {'q': '', 'price': '', 'iprice': ''}, 'root': root}


d = auction()
with open(os.path.join(OUT, 'auction.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, indent=1)
print('wrote auction')
