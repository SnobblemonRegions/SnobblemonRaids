"""CobbleRaids pages (cobbleraids_<name>.json), faithful copies of the original web pages (cobbleraids/*/ + shared/theme.css).

Every original page is a 16:9 .stage, min(96vw, 170.6vh) wide, laid out in container units: here the stage is 1600 x 900 page units
(1cqw = 16) inside a 1667 x 938 page fitted like the original ("fit": [1666.7, 937.5]: scale = min(w / 1666.7, h / 937.5)).
Fonts: Silkscreen 700 ("silk_b") and IBM Plex Mono ("plex", "plex_b" for 600), the pages' own OFL fonts. Panels are the page's
cut-corner shapes (cut + cut_tl, gradient fill, 1px line, corner tick); the stage backdrop, header rule and hub icons are rendered from
the page's own CSS/SVG (tex:cobbleraids/*). Hub, shop, trophy room, Hall of Legends and leaderboards are v2; reveal and capture are
still the v1 layouts (gen_ui_raids_v1.py).
"""
import json, os, sys

sys.path.insert(0, os.path.dirname(__file__))
import gen_ui_raids_v1 as v1

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
BG, LN, HI, TX, MU, BAD = '#04121f', '#1d6f8f', '#46e3ff', '#e6f7ff', '#7aa7bd', '#ff6b6b'
PX, M, MB = 'silk_b', 'plex', 'plex_b'
PANEL = 'gradv:#08233af0,#051626f0'
PANEL_HV = 'gradv:#0e4668f2,#07233af2'
SX, SY = 33, 19          # where the 1600 x 900 stage sits in the page


def T(text, size=19.2, color=TX, font=M, **kw):
    d = {'type': 'label', 'text': text, 'font': font, 'size': size, 'color': color, 'shadow': False, 'lh': round(size * 1.3, 2)}
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


def ticks(size=25.6, cut=22.4):
    """.panel::after: the 2px cyan corner tick (clipped by the cut corner)"""
    return [box([], x=cut, y=0, w=size - cut + 2, h=2, bg=HI), box([], x=0, y=cut, w=2, h=size - cut + 2, bg=HI)]


def ghost(text, id_, **kw):
    """.btn.ghost: Silkscreen 700 1.3cqw, padding .9cqw 1.8cqw, 1px inset line, cut .9cqw"""
    d = {'type': 'button', 'id': id_, 'text': text, 'font': PX, 'size': 20.8, 'ls': 1.25, 'color': HI, 'shadow': False, 'h': 55, 'pad': [0, 29, 0, 29],
         'shrink': True, 'text_align': 'center', 'border': LN, 'hover_border': HI, 'hover': '#46e3ff1f', 'bw': 1, 'cut': 14, 'cut_tl': 14}
    d.update(kw)
    return d


def chip(text, **kw):
    d = T(text, 16, HI, M, ls=1.6, lh=20.8, pad=[9, 19, 9, 19], bg='#46e3ff14', border=LN, bw=1, cut=13, cut_tl=13, shrink=True)
    d.update(kw)
    return d


def header(title, crumb_nodes, extras=()):
    return row([T(title, 30.4, HI, PX, ls=1.82, lh=39), *crumb_nodes,
                box([box([], w='fill', h=1, bg='tex:cobbleraids/rule.png')], w='fill', h=1)] + list(extras),
               x=48, y=35, w=1504, h=83, gap=25.6, cross='center')


def crumb(text, **kw):
    return T(text, 16, MU, M, ls=2.24, upper=True, lh=20.8, **kw)


def close():
    return ghost('Close', 'close', close=True)


def stage(children, pid, channel, title, state=None, hello='ready'):
    st = box(children, x=SX, y=SY, w=1600, h=900, bg='tex:cobbleraids/stage.png')
    d = {'v': 2, 'id': pid, 'channel': channel, 'hello': hello, 'bye': False, 'title': title, 'width': 1667, 'height': 938, 'fit': [1666.7, 937.5], 'nodim': True,
         'root': box([st], w=1667, h=938, bg='#02080ecc')}
    if state:
        d['state'] = state
    return d


def pager(prev, nxt, middle):
    return [dict(prev, x=48, y=810), T(middle, 16, MU, M, ls=2.24, lh=20.8, x=48, y=827, w=1504, text_align='center'), dict(nxt, right=48, y=810)]


# ---------------------------------------------------------------- hub
def hub():
    def tile(key, no, title, desc, stat, x, y):
        return {'type': 'button', 'id': 'open_' + key, 'x': x, 'y': y, 'w': 731.6, 'h': 333.7, 'bg': PANEL, 'hover': PANEL_HV, 'border': LN, 'hover_border': HI, 'bw': 1,
                'cut': 22, 'cut_tl': 22, 'msg': {'action': 'open', 'target': key}, 'args': {}, 'children': ticks() + [
                    box([], x=38.4, y=32, w=71, h=71, bg='tex:cobbleraids/icon_%s.png' % key),
                    T(no, 16, MU, MB, right=32, y=22.4, lh=20.8),
                    T(title, 40, TX, PX, x=39.6, y=166.8, lh=52),
                    T(desc, 19.2, MU, M, x=39.6, y=225.3, lh=25),
                    T(stat, 24, HI, MB, x=39.6, y=269.3, lh=31.2)]}
    return stage([
        header('RAID HUB', [crumb('{vm.who}')], [chip('{vm.rp}'), close()]),
        tile('shop', '01', 'Raid Shop', 'Spend your Raid Points', '{vm.rp}', 48, 145.1),
        tile('trophy', '02', 'Trophy Room', 'Every boss you have beaten', '{vm.trophies}', 819.2, 145.1),
        tile('legends', '03', 'Hall of Legends', "The server's first renowned wins", '{vm.legends}', 48, 517),
        tile('leaders', '04', 'Leaderboards', 'See where you rank', '{vm.raids}', 819.2, 517)],
        'cobbleraids_hub', 'cobbleraids.hub', 'Raid Hub')


# ---------------------------------------------------------------- leaderboards
def leaders():
    q = {'stat': 'str:32', 'scope': 'enum:alltime|weekly'}
    qm = {'action': 'query', 'stat': '{stat}', 'scope': '{scope}'}
    navbtn = lambda t, id_, stat: ghost(t, id_, h=43, pad=[0, 26, 0, 26], args=q, argv={'stat': stat, 'scope': 'vm.scope'}, msg=qm)
    cols = lambda a, b, c, **kw: [T(a, w=112, **kw.get('a', {})), T(b, w='fill', **kw.get('b', {})), T(c, w=256, text_align='right', **kw.get('c', {}))]
    line = row([T('{r.rank}', 24, '{r.rankColor}', MB, w=112, lh=31.2),
                row([T('{r.name}', 24, TX, M, lh=31.2, ellipsis=True, shrink=True), T('  YOU', 16, HI, M, ls=1.6, lh=31.2, **{'if': 'r.you'})], w='fill', cross='center'),
                T('{r.val}', 24, HI, MB, w=256, lh=31.2, text_align='right')],
               w='fill', h=59.2, gap=16, cross='center', pad=[0, 22.4, 0, 28], bg='{r.rowBg}', border='{r.youBorder}', bw=1,
               repeat={'for': 'vm.rows', 'as': 'r', 'limit': 12},
               children_before=None)
    line.pop('children_before')
    line['children'].insert(0, box([], x=0, y=0, w=5.6, h=59.2, bg='{r.edge}'))
    board = box(ticks() + [
        row([navbtn('‹', 'prev', 'vm.prevStat'), T('{lp.title}', 32, TX, PX, ls=1.28, lh=41.6, w='fill', text_align='center'), navbtn('›', 'next', 'vm.nextStat')],
            w='fill', gap=25.6, cross='center'),
        row([T('RANK', 16, MU, M, ls=2.24, lh=20.8, w=112), T('PLAYER', 16, MU, M, ls=2.24, lh=20.8, w='fill'),
             T('{vm.valueHead}', 16, MU, M, ls=2.24, lh=20.8, w=256, text_align='right')],
            w='fill', gap=16, pad=[0, 22.4, 6.4, 22.4], bsides='b', border='#46e3ff40', bw=1, dash=True),
        box([T('Nobody is on this board yet.', 19.2, MU, M, ls=1.5, w='fill', text_align='center', pad=[96, 0, 96, 0], **{'if': 'vm.empty'}), line],
            w='fill', h='fill', gap=6.4, clip=True),
        T('{vm.counts}', 16, MU, M, ls=1.28, lh=20.8, w='fill', text_align='center')],
        x=48, y=141.9, w=1504, h=733.6, pad=[25.6, 38.4, 19.2, 38.4], gap=12.8, bg=PANEL, border=LN, bw=1, cut=22, cut_tl=22)
    return stage([header('LEADERBOARDS', [crumb('{vm.crumb}')], [
        ghost('{vm.scopeLabel}', 'scope', args=q, argv={'stat': 'lp.stat', 'scope': 'vm.otherScope'}, msg=qm), close()]), board],
        'cobbleraids_leaders', 'cobbleraids.leaders', 'Leaderboards')


# ---------------------------------------------------------------- Hall of Legends
def legends():
    q = {'page': 'int:0:9999', 'mine': 'bool', 'tier': 'str:32'}
    m = {'action': 'query', 'page': '{page}', 'mine': '{mine}', 'tier': '{tier}'}
    mine = [ghost('Everyone', 'mine_off', args=q, set={'mine': "'true'"}, argv={'page': '0', 'mine': "'true'", 'tier': '$tier'}, msg=m, **{'if': "$mine != 'true'"}),
            ghost('Mine', 'mine_on', args=q, set={'mine': "'false'"}, argv={'page': '0', 'mine': "'false'", 'tier': '$tier'}, msg=m, **{'if': "$mine == 'true'"})]
    tier = [ghost('All tiers', 'tier_all', args=q, set={'tier': 'vm.firstTier'}, argv={'page': '0', 'mine': '$mine', 'tier': 'vm.firstTier'}, msg=m, **{'if': "$tier == ''"}),
            ghost('{t.name}', 'tier_pick', args=q, set={'tier': 't.next'}, argv={'page': '0', 'mine': '$mine', 'tier': 't.next'}, msg=m,
                  repeat={'for': 'vm.tierCycle', 'as': 't', 'limit': 12, 'if': '$tier == t.id'})]
    card = box([box([], x=0, y=0, w=8, h=69.6, bg='{e.color}'),
                row([T('{e.title}', 24, TX, PX, lh=31.2), T('{e.species}', 24, '{e.color}', PX, lh=31.2)], x=32, y=8.8),
                row([T('{e.tierName}', 16, '{e.color}', MB, ls=1.92, lh=20.8), T('{e.l2}', 16, MU, M, ls=0.96, lh=20.8, ellipsis=True, shrink=True)], x=32, y=40),
                T('{e.victors}', 19.2, HI, M, right=25.6, y=22.3, lh=25, ellipsis=True, max_w=544, shrink=True)] + ticks(16, 16),
               w='fill', h=69.6, bg=PANEL, border=LN, bw=1, cut=16, cut_tl=16, repeat={'for': 'vm.rows', 'as': 'e', 'limit': 10})
    body = box([T('{vm.emptyText}', 19.2, MU, M, ls=1.5, w='fill', text_align='center', pad=[128, 0, 128, 0], **{'if': 'vm.none'}), card], x=48, y=138.7, w=1504, h=655, gap=8,
               clip=True)
    prev = ghost('‹ Previous', 'prev', args=q, enabled='vm.canPrev', argv={'page': 'vm.prev', 'mine': '$mine', 'tier': '$tier'}, msg=m, disabled='', opacity='vm.prevOp')
    nxt = ghost('Next ›', 'next', args=q, enabled='vm.canNext', argv={'page': 'vm.next', 'mine': '$mine', 'tier': '$tier'}, msg=m, disabled='', opacity='vm.nextOp')
    return stage([header('HALL OF LEGENDS', [crumb('{vm.crumbAll}', **{'if': "$mine != 'true'"}), crumb('{vm.crumbMine}', **{'if': "$mine == 'true'"})],
                         mine + tier + [close()]), body] + pager(prev, nxt, '{vm.counts}'),
                 'cobbleraids_legends', 'cobbleraids.legends', 'Hall of Legends', state={'mine': 'false', 'tier': ''})


# ---------------------------------------------------------------- shop
def shop():
    q = {'page': 'int:0:9999', 'id': 'str:64'}
    buy = {'args': q, 'argv': {'page': 'sh.pageIndex', 'id': 'e.id'}, 'msg': {'action': 'buy', 'page': '{page}', 'id': '{id}'},
           'tooltip': ['{e.tipName}', '{e.tipPrice}', '{e.tipLimit}']}
    item = dict({'type': 'button', 'id': 'buy', 'x': 'e.x', 'y': 'e.y', 'w': 175.4, 'h': 134.4, 'bg': PANEL, 'hover': PANEL_HV, 'border': LN, 'hover_border': HI, 'bw': 1,
                 'cut': 14, 'cut_tl': 14, 'repeat': {'for': 'vm.cells', 'as': 'e', 'limit': 64, 'if': '!vm.pokemon'}, 'children': ticks(16, 14) + [
                     box([{'type': 'item', 'item': '{e.itemId}', 'count': 1, 'w': 70.4, 'h': 70.4, 'if': 'e.itemId'},
                          {'type': 'image', 'pokemon': '{e.sprite}', 'w': 70.4, 'h': 70.4, 'if': 'e.sprite'}], x=52.5, y=14.4, w=70.4, h=70.4, opacity='e.op'),
                     T('{e.countText}', 16, TX, MB, x=11.2, y=6.4, lh=20.8, **{'if': 'e.countText'}),
                     T('{e.left}', 16, MU, M, right=11.2, y=6.4, lh=20.8, **{'if': 'e.left'}),
                     T('{e.price}', 16, '{e.priceCol}', MB, x=0, bottom=11.2, w=175.4, lh=20.8, text_align='center')]}, **buy)
    reroll = {'type': 'button', 'id': 'reroll', 'text': '{e.rerollText}', 'font': PX, 'size': 16, 'color': '#ffd84a', 'hover_color': '#221a02', 'shadow': False,
              'pad': [2.4, 9.6, 2.4, 9.6], 'shrink': True, 'h': 26, 'bg': '#281f06e6', 'hover': '#ffd84a', 'border': '#ffd84a', 'bw': 1,
              'args': q, 'argv': {'page': 'sh.pageIndex', 'id': 'e.rerollId'}, 'msg': {'action': 'buy', 'page': '{page}', 'id': '{id}'}, 'if': 'e.canReroll'}
    poke = dict({'type': 'button', 'id': 'buyp', 'x': 'e.x', 'y': 'e.y', 'w': 361.6, 'h': 142.4, 'bg': PANEL, 'hover': PANEL_HV, 'border': LN, 'hover_border': HI, 'bw': 1,
                 'cut': 14, 'cut_tl': 14, 'repeat': {'for': 'vm.cells', 'as': 'e', 'limit': 64, 'if': 'vm.pokemon'}, 'children': ticks(16, 14) + [
                     box([{'type': 'image', 'pokemon': '{e.sprite}', 'w': 153.6, 'h': 102.4, 'if': 'e.sprite'}], x=9.6, y=20, w=153.6, h=102.4, opacity='e.op'),
                     box([T('{e.pname}', 24, '{e.pcolor}', MB, w='fill', lh=31.2, ellipsis=True, opacity='e.op'),
                          T('{e.meta}', 16, MU, M, lh=20.8, opacity='e.op'),
                          T('{e.ivev}', 16, MU, M, lh=20.8, opacity='e.op', **{'if': 'e.ivev'}),
                          row([T('{e.price}', 19.2, '{e.priceCol}', MB, lh=25, w='fill', ellipsis=True), reroll], w='fill', gap=9.6, cross='center')],
                         x=169.6, y=0, w=179.2, h=142.4, align='center', gap=2.4),
                     T('{e.left}', 16, MU, M, right=12.8, y=6.4, lh=20.8, **{'if': 'e.left'})]}, **buy)
    grid = box([T('Nothing for sale right now.', 19.2, MU, M, ls=1.5, w='fill', text_align='center', pad=[128, 0, 128, 0], **{'if': 'vm.none'}), item, poke],
               x=48, y=141.9, w=1504, h=650, clip=True)
    prev = ghost('‹ Previous', 'prev', args={'page': 'int:0:9999'}, argv={'page': 'vm.prev'}, msg={'action': 'turn', 'page': '{page}'}, **{'if': '!vm.single'})
    nxt = ghost('Next ›', 'next', args={'page': 'int:0:9999'}, argv={'page': 'vm.next'}, msg={'action': 'turn', 'page': '{page}'}, **{'if': '!vm.single'})
    restock = {'type': 'spacer', 'id': 'restock', 'if': 'vm.rotates', 'every_ms': 20000, 'args': {'page': 'int:0:9999'}, 'argv': {'page': 'sh.pageIndex'},
               'msg': {'action': 'turn', 'page': '{page}'}}
    return stage([header('RAID SHOP', [crumb('{vm.crumb}')], [chip('NEW STOCK IN {vm.deadline|countdown}', **{'if': 'vm.rotates'}), chip('{vm.balanceText}'), close()]),
                  grid, restock] + pager(prev, nxt, '{vm.where}'),
                 'cobbleraids_shop', 'cobbleraids.shop', 'Raid Shop')


# ---------------------------------------------------------------- trophy room
def trophy():
    q = {'page': 'int:0:9999', 'query': 'str:64', 'tier': 'str:32', 'shiny': 'int:0:2', 'sort': 'int:0:3'}
    m = {'action': 'query', 'page': '{page}', 'query': '{query}', 'tier': '{tier}', 'shiny': '{shiny}', 'sort': '{sort}'}

    def av(**over):
        d = {'page': '0', 'query': '$q', 'tier': '$tier', 'shiny': '$shiny', 'sort': '$sort'}
        d.update(over)
        return d

    fbtn = dict(h=54.3, min_w=192)
    tier = [ghost('All tiers', 'tier_all', args=q, set={'tier': 'vm.firstTier'}, argv=av(tier='vm.firstTier'), msg=m, **fbtn, **{'if': "$tier == ''"}),
            ghost('{t.name}', 'tier_pick', args=q, set={'tier': 't.next'}, argv=av(tier='t.next'), msg=m, **fbtn,
                  repeat={'for': 'vm.tierCycle', 'as': 't', 'limit': 12, 'if': '$tier == t.id'})]
    shiny = [ghost(lbl, 'sh%d' % i, args=q, set={'shiny': "'%d'" % ((i + 1) % 3)}, argv=av(shiny="'%d'" % ((i + 1) % 3)), msg=m, **fbtn, **{'if': "$shiny == '%d'" % i})
             for i, lbl in enumerate(('Shiny: all', 'Shiny only', 'Normal only'))]
    sort = [ghost(lbl, 'so%d' % i, args=q, set={'sort': "'%d'" % ((i + 1) % 4)}, argv=av(sort="'%d'" % ((i + 1) % 4)), msg=m, **fbtn, **{'if': "$sort == '%d'" % i})
            for i, lbl in enumerate(('Newest', 'Oldest', 'Species', 'Best IV'))]
    search = {'type': 'input', 'id': 'search', 'bind': 'q', 'hint': 'Search species...', 'w': 'fill', 'h': 54.3, 'max_len': 64, 'font': M, 'size': 19.2, 'color': TX,
              'hint_color': '#4f7a90', 'bg': '#020e18e6', 'border': LN, 'hover_border': HI, 'bw': 1, 'pad': [0, 16, 0, 16], 'debounce_ms': 400, 'args': q, 'argv': av(), 'msg': m}
    filters = row([search] + tier + shiny + sort, x=48, y=137.1, w=1504, h=54.3, gap=16, cross='center')

    def stat(lbl, w, val, y):
        return [T(lbl, 16, MU, M, x=23.5, y=y, lh=20.8),
                box([box([], x=0, y=0, w=0, w_expr=w, h=8.7, bg=HI)], x=55.5, y=y + 6, w=358.4, h=8.7, bg='#46e3ff24'),
                T(val, 16, TX, MB, right=23.5, y=y, lh=20.8)]
    filled = [
        box([{'type': 'image', 'pokemon': '{c.glow}', 'x': 0, 'y': 0, 'w': 431.9, 'h': 360.9},
             {'type': 'image', 'pokemon': '{c.sprite}', 'x': 43.2, 'y': 65.2, 'w': 345.5, 'h': 230.4, 'if': 'c.sprite'},
             T('?', 96, MU, PX, x=0, y=113, w=431.9, lh=125, text_align='center', **{'if': '!c.sprite'}),
             T('{c.level}', 16, MU, M, ls=1.6, right=12.8, y=9.6, lh=20.8)],
            x=23.5, y=20.4, w=431.9, h=360.9, bg='#020a12e6', border='#46e3ff2e', bw=1, clip=True),
        T('{c.name}', 30.4, TX, PX, x=23.5, y=392.4, w=431.9, lh=39.5, clip=True),
        T('{c.tier}', 16, '{c.tierColor}', MB, ls=1.92, x=23.5, y=442.7, lh=20.8),
        T('{c.shinyText}', 16, '{c.shinyColor}', M, ls=1.92, right=23.5, y=442.7, lh=20.8)] + \
        stat('IV', 'c.ivW', '{c.ivText}', 474.6) + stat('EV', 'c.evW', '{c.evText}', 498.6) + [
        box([], x=23.5, y=530.5, w=431.9, h=1, border='#46e3ff38', bw=1, bsides='t', dash=True),
        T('{c.firstText}', 16, MU, M, x=23.5, y=539.8, lh=20.8),
        T('{c.timesText}', 16, MU, M, right=23.5, y=539.8, lh=20.8)]
    for n in filled:
        n['if'] = 'c.filled' if 'if' not in n else n['if']
    blank = box([T('Unclaimed', 24, '#4a7488', PX, lh=31.2), T('{c.blank}', 19.2, MU, M, ls=2.3, lh=25)], x=0, y=0, w=479.2, h=581.1, align='center', cross='center',
                gap=8, **{'if': '!c.filled'})
    case = box(ticks() + filled + [blank], x='c_i * 511.1', w=479.2, h=581.1, bg=PANEL, border=LN, bw=1, cut=22, cut_tl=22,
               repeat={'for': 'vm.cases', 'as': 'c', 'limit': 3})
    case.pop('x')
    cases = row([case], x=48, y=215.4, w=1504, h=581.1, gap=32)
    prev = ghost('‹ Previous', 'prev', args=q, enabled='vm.canPrev', argv=av(page='vm.prev'), msg=m, disabled='', opacity='vm.prevOp')
    nxt = ghost('Next ›', 'next', args=q, enabled='vm.canNext', argv=av(page='vm.next'), msg=m, disabled='', opacity='vm.nextOp')
    return stage([header('TROPHY ROOM', [crumb('{vm.crumb2}')], [close()]), filters, cases] + pager(prev, nxt, '{vm.where}'),
                 'cobbleraids_trophy', 'cobbleraids.trophy', 'Trophy Room', state={'q': '', 'tier': '', 'shiny': '0', 'sort': '0'})


# ---------------------------------------------------------------- reward reveal (reveal.html / reveal.css)
def solid(text, id_, **kw):
    """.btn: Silkscreen 700, cyan fill, dark text, cut .9cqw"""
    d = {'type': 'button', 'id': id_, 'text': text, 'font': PX, 'size': 20, 'ls': 0.4, 'color': '#021018', 'shadow': False, 'text_align': 'center',
         'bg': HI, 'hover': '#8ff0ff', 'disabled': '#2a5266', 'cut': 14, 'cut_tl': 14}
    d.update(kw)
    return d


def reveal():
    def kv(k, v, color=TX):
        return row([T(k, 19.2, MU, M, lh=25, w='fill'), T(v, 19.2, color, MB, lh=25, text_align='right')], w='fill', pad=[9.6, 0, 9.6, 0], bsides='b',
                   border='#46e3ff33', bw=1, dash=True)
    side = box(ticks() + [
        T('RAID SUMMARY', 20.8, HI, PX, ls=1.66, lh=27, pad=[0, 0, 12.8, 0]),
        kv('Boss defeated', '{vm.species}', '{vm.tierColor}'), kv('Raid tier', '{vm.tierName}', '{vm.tierColor}'), kv('Completion time', '{vm.time}'),
        T('YOUR CONTRIBUTION', 20.8, HI, PX, ls=1.66, lh=27, pad=[22.4, 0, 12.8, 0]),
        kv('Damage dealt', '{vm.contribution}'), kv('Raiders', '{vm.raiders}')],
        x=48, y=147.2, w=384, h=704.8, pad=[25.6, 28.8, 25.6, 28.8], gap=3.2, bg=PANEL, border=LN, bw=1, cut=22, cut_tl=22)
    CHOOSING = "vm.stage == 'CHOOSING'"
    plate = row([box([], w=38.4, h=38.4, bg='{vm.boonTex}', **{'if': 'vm.boonTex'}),
                 box([], w=29, h=29, bg='tex:cobbleraids/skull.png'), T('{vm.bannerName}', 28.8, '#ffaa00', PX, lh=37.4),
                 T('{vm.bannerEp}', 28.8, '#ff6b6b', PX, lh=37.4, **{'if': 'vm.bannerEp'}), box([], w=29, h=29, bg='tex:cobbleraids/skull.png')],
                gap=14.4, cross='center', pad=[9.6, 28.8, 9.6, 28.8], bg='#020a12e0', border='#5a2a2a', bw=1, cut=14, cut_tl=14, shrink=True)
    slot = box(ticks(16, 16) + [
        box([{'type': 'item', 'item': '{s.itemId}', 'count': 1, 'w': 83.2, 'h': 83.2, 'if': 's.itemId'},
             {'type': 'image', 'pokemon': '{s.sprite}', 'w': 83.2, 'h': 83.2, 'if': 's.sprite'},
             T('{s.amount}', 28.8, '{s.valColor}', PX, lh=37.4, **{'if': 's.chip'})], x=0, y=22, w=169.6, h=83.2, align='center', cross='center'),
        T('{s.name}', 16, MU, M, x=8, y=113, w=153.6, lh=19.2, text_align='center', wrap=True, lines=2),
        T('{s.cnt}', 19.2, TX, MB, right=12.8, y=8, lh=25, **{'if': 's.cnt'})],
        x='s.x', y='s.y', w=169.6, h=169.6, bg=PANEL, border='{s.border}', bw=1, cut=16, cut_tl=16, anim='popin', anim_ms=380, anim_delay='s.delay', anim_key='s.name',
        repeat={'for': 'vm.slots', 'as': 's', 'limit': 30, 'if': "vm.stage == 'RESULT' && vm.ok"})
    chamber = box([
        box([], x=0, y=0, w=1072, h=567, bg='tex:cobbleraids/beam.png', opacity='vm.beamOp'),
        row([plate], x=0, y=22.4, w=1072, align='center', **{'if': 'vm.hasBanner'}),
        box([T('{vm.promptBig}', 32, HI, PX, lh=41.6, w='fill', text_align='center', pad=[0, 0, 8, 0]),
             T('{vm.promptSmall}', 19.2, MU, M, ls=2.7, lh=25, w='fill', text_align='center')], x=0, y=261, w=1072, **{'if': CHOOSING + " && $picked != '1'"}),
        T('REVEALING...', 32, HI, PX, lh=41.6, x=0, y=261, w=1072, text_align='center', **{'if': CHOOSING + " && $picked == '1'"}),
        T('Something went wrong. Check chat for details.', 24, BAD, M, lh=31, x=0, y=260, w=1072, text_align='center', **{'if': "vm.stage == 'RESULT' && !vm.ok"}),
        slot] + ticks(),
        x=480, y=147.2, w=1072, h=567.2, bg=PANEL, border=LN, bw=1, cut=22, cut_tl=22, clip=True)
    choice = solid('{c.label}', 'choose', w=0, w_expr='vm.choiceW', h=102.4, enabled="$picked != '1'", args={'choice': 'str:48'}, argv={'choice': 'c.id'},
                   msg={'action': 'choose', 'choice': '{choice}'}, set={'picked': "'1'"}, repeat={'for': 'vm.choices', 'as': 'c', 'limit': 6, 'if': CHOOSING})
    done = solid('Close', 'done', w=480, h=102.4, close=True, **{'if': "vm.stage == 'RESULT'"})
    buttons = row([choice, done], x=480, y=749.6, w=1072, h=102.4, gap=25.6, align='center', cross='center')
    return stage([header('RAID REWARDS', [crumb('{vm.crumb}')], [chip('{vm.hint}', **{'if': 'vm.hint'})]), side, chamber, buttons],
                 'cobbleraids_reveal', 'cobbleraids.reveal', 'Raid Rewards', state={'picked': ''})


# ---------------------------------------------------------------- raid capture (capture.js / capture.css)
def capture():
    OFFER = "vm.stage == 'OFFER' && $go != '1'"
    PULSE = "($go == '1' && vm.stage == 'OFFER') || vm.stage == 'PULSE'"
    THROW = "vm.stage == 'THROW'"
    RESULT = "vm.stage == 'RESULT'"
    TIMING = "(%s) || vm.stage == 'THROW'" % PULSE
    lamp = lambda i: box([], w=14.4, h=14.4, bg='{vm.lampBg%d}' % i, border=HI, bw=1)
    head = row([T('RAID CAPTURE', 30.4, HI, PX, ls=1.82, lh=39, **{'if': OFFER}),
                T('STABILIZE ECHO  {vm.pnum} / 3', 30.4, HI, PX, ls=1.82, lh=39, **{'if': PULSE}),
                T('THROW THE BALL', 30.4, HI, PX, ls=1.82, lh=39, **{'if': THROW}),
                T('{vm.resultTitle}', 30.4, HI, PX, ls=1.82, lh=39, **{'if': RESULT}),
                box([box([], w='fill', h=1, bg='tex:cobbleraids/rule.png')], w='fill', h=1),
                T('{vm.state}', 16, HI, M, ls=1.92, lh=20.8), row([lamp(0), lamp(1), lamp(2)], gap=8, cross='center')],
               x=48, y=35, w=1504, h=83, gap=25.6, cross='center')
    pulses = box([row([T('{r.a}', 19.2, '{r.color}', M, lh=25, w='fill'), T('{r.b}', 19.2, '{r.color}', M, lh=25)], w='fill', repeat={'for': 'vm.pulseRows', 'as': 'r', 'limit': 3})],
                 w='fill', gap=5.6, pad=[6.4, 0, 0, 0], **{'if': '!(%s)' % OFFER})
    info = box(ticks() + [
        T('{vm.name}', 27.2, TX, PX, lh=35.4, w='fill', ellipsis=True),
        T('{vm.tier}', 16, HI, MB, ls=1.92, lh=20.8),
        T('{vm.rpText}', 19.2, TX, M, lh=25),
        T('Capture chance', 16, MU, M, lh=20.8, pad=[9.6, 0, 0, 0]),
        T('{vm.odds}', 24, HI, MB, lh=31.2),
        T('Three pulses. One throw. Timing improves your odds.', 16, TX, M, lh=23.2, w='fill', wrap=True, pad=[6.4, 0, 0, 0], **{'if': OFFER}),
        pulses,
        T('Match the white marker to the center zone. Misses keep your base chance.', 16, TX, M, lh=23.2, w='fill', wrap=True, pad=[6.4, 0, 0, 0], **{'if': TIMING})],
        x=48, y=137.6, w=384, h=482.4, pad=[22.4, 25.6, 22.4, 25.6], gap=8, bg=PANEL, border=LN, bw=1, cut=22, cut_tl=22, clip=True)
    ball_if = "(%s && $ws == vm.step) || (%s && vm.ok)" % (THROW, RESULT)
    chamber = box([box([], x=0, y=0, w=1008, h=482, bg='tex:cobbleraids/cap_chamber.png'),
                   {'type': 'image', 'pokemon': '{vm.sprite}', 'x': 191.5, 'y': 32.3, 'w': 625, 'h': 417.4,
                    'if': "vm.sprite && !(%s) && !(%s && !vm.ok)" % (ball_if, RESULT)},
                   box([], x=432, y=169, w=144, h=144, bg='tex:cobbleraids/ball.png', anim='shake', anim_ms=267, **{'if': ball_if})] + ticks(),
                  x=464, y=137.6, w=1008, h=482.4, border=LN, bw=1, cut=22, cut_tl=22, clip=True)
    cell = box([], w='fill', h=25.6, bg='{m.bg}', repeat={'for': 'vm.meter', 'as': 'm', 'limit': 12})
    meter = [T('ODDS', 16, MU, M, ls=1.6, lh=20.8, x=1488, y=150.4, w=73.6, text_align='center'),
             box([cell], x=1497.6, y=192, w=54.4, h=372, pad=[9.6, 9.6, 9.6, 9.6], gap=4.8, bg=PANEL, border=LN, bw=1, cut=13, cut_tl=13)]
    timing = [
        T('Waiting for server score...', 24, TX, M, lh=31.2, x=48, y=644.8, w=1504, text_align='center', **{'if': '(%s) && $ws == vm.step' % TIMING}),
        T('Press SPACE or click Stabilize at the center', 24, TX, M, lh=31.2, x=48, y=644.8, w=1504, text_align='center', **{'if': '(%s) && $ws != vm.step' % PULSE}),
        T('Time your throw at the center!', 24, TX, M, lh=31.2, x=48, y=644.8, w=1504, text_align='center', **{'if': '(%s) && $ws != vm.step' % THROW}),
        box([{'type': 'sweep', 'x': 8, 'y': 8, 'w': 1488, 'h': 54, 'bg': '#001a30e6', 'travel': 'vm.travel', 'good': 'vm.good', 'perfect': 'vm.perfect', 'anchor': '$_sent'}],
            x=48, y=695.2, w=1504, h=70.4, bg=PANEL, border=LN, bw=1, cut=14, cut_tl=14, **{'if': TIMING}),
        T('GOLD: GOOD   |   CYAN CENTER: PERFECT', 16, MU, M, ls=1.6, lh=20.8, x=48, y=776.8, w=1504, text_align='center', **{'if': TIMING})]
    outro = [
        box([T('{vm.outro1}', 24, TX, M, lh=31.2, w='fill', text_align='center'),
             T('Other loot is safe. Choose within {vm.deadline|countdownsec}s.', 19.2, HI, M, lh=25, w='fill', text_align='center')],
            x=48, bottom=134.4, w=1504, gap=9.6, **{'if': OFFER}),
        box([T('{vm.result1}', 24, TX, M, lh=31.2, w='fill', text_align='center'), T('{vm.result2}', 19.2, HI, M, lh=25, w='fill', text_align='center')],
            x=48, bottom=134.4, w=1504, gap=9.6, **{'if': RESULT})]
    actions = row([
        solid('Attempt Capture', 'attempt', w=544, h=67.2, **{'if': OFFER, 'args': {'attempt': 'bool'}, 'argv': {'attempt': "'true'"},
                                                               'msg': {'action': 'choice', 'attempt': '{attempt}'}, 'set': {'go': "'1'"}}),
        ghost('{vm.keepText}', 'keep', w=544, h=67.2, close=True, shrink=False, **{'if': OFFER, 'args': {'attempt': 'bool'}, 'argv': {'attempt': "'false'"},
                                                                                     'msg': {'action': 'choice', 'attempt': '{attempt}'}}),
        solid('Stabilize [SPACE]', 'press_pulse', w=416, h=67.2, key='space', enabled='$ws != vm.step',
              **{'if': PULSE, 'args': {'index': 'int:0:2'}, 'argv': {'index': 'vm.pidx'}, 'msg': {'action': 'pulse', 'index': '{index}'}, 'set': {'ws': 'vm.step'}}),
        solid('Throw Ball [SPACE]', 'press_throw', w=416, h=67.2, key='space', enabled='$ws != vm.step',
              **{'if': THROW, 'args': {}, 'msg': {'action': 'throw'}, 'set': {'ws': 'vm.step'}}),
        solid('Close', 'done', w=416, h=67.2, close=True, **{'if': RESULT})],
        x=48, y=804, w=1504, h=67.2, gap=25.6, align='center', cross='center')
    return stage([head, info, chamber] + meter + timing + outro + [actions], 'cobbleraids_capture', 'cobbleraids.capture', 'Raid Capture', state={'go': '', 'ws': ''})


def check(d):
    seen, bad = set(), []

    def walk(n, rep):
        r = n.get('repeat')
        if r and not 1 <= r.get('limit', 1) <= 500:
            bad.append('limit')
        rep = rep or bool(r)
        if n.get('id') and not rep:
            if n['id'] in seen:
                bad.append('duplicate id ' + n['id'])
            seen.add(n['id'])
        for k in n.get('children', []):
            walk(k, rep)
    walk(d['root'], False)
    if bad:
        raise SystemExit('INVALID %s: %s' % (d['id'], ', '.join(bad)))


INTS = ('w', 'h', 'min_w', 'max_w', 'min_h', 'x', 'y', 'right', 'bottom')


def clean(o):
    if isinstance(o, dict):
        return {k: (int(round(v)) if k in INTS and isinstance(v, float) else clean(v)) for k, v in o.items()
                if v is not None and not (k in ('border', 'disabled') and v == '')}
    if isinstance(o, list):
        return [clean(v) for v in o]
    return o


if __name__ == '__main__':
    for name, fn in (('cobbleraids_hub', hub), ('cobbleraids_leaders', leaders), ('cobbleraids_legends', legends), ('cobbleraids_shop', shop),
                     ('cobbleraids_trophy', trophy), ('cobbleraids_reveal', reveal), ('cobbleraids_capture', capture)):
        d = clean(fn())
        check(d)
        with open(os.path.join(OUT, name + '.json'), 'w', encoding='utf8') as f:
            json.dump(d, f, ensure_ascii=False, separators=(',', ':'))
        print('wrote', name, os.path.getsize(os.path.join(OUT, name + '.json')))
