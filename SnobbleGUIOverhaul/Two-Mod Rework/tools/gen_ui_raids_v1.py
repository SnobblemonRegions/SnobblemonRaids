"""Generates the layout-v2 pages for CobbleRaids: hub, leaders, legends, shop, trophy (cobbleraids_<name>.json).
Same look as the web pages (Raid Network: dark blue panels, cyan lines), drawn natively. Texts come from RaidViews (server)."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
BG, BG2, LN, HI, TX, MU = '#04121F', '#08233A', '#1D6F8F', '#46E3FF', '#E6F7FF', '#7AA7BD'
HOV = '#0E4668'


def label(text, **kw):
    d = {'type': 'label', 'text': text}
    d.update(kw)
    return d


def sp(**kw):
    d = {'type': 'spacer'}
    d.update(kw)
    return d


def btn(text, **kw):
    d = {'type': 'button', 'text': text, 'h': 22, 'pad': [3, 10, 3, 10], 'cross': 'center', 'align': 'center', 'bg': BG, 'border': LN, 'radius': 3,
         'hover': HOV, 'disabled': '#0A1A26', 'color': HI}
    d.update(kw)
    return d


def solid(text, **kw):
    return btn(text, **dict({'bg': HI, 'border': HI, 'hover': '#8FF0FF', 'color': '#021018'}, **kw))


def header(title, crumb, extras=()):
    return {'type': 'row', 'w': 'fill', 'pad': [12, 18, 8, 18], 'gap': 12, 'cross': 'center', 'children': [
        label(title, scale=1.5, color=HI), label(crumb, color=MU), sp(w='fill')] + list(extras) + [btn('Close', w=60, close=True)]}


def chip(text, **kw):
    return label(text, color=HI, bg='#0B3550', border=LN, radius=3, pad=[3, 10, 3, 10], **kw)


def page(pid, channel, title, width, height, body, state=None, hello='ready'):
    d = {'v': 2, 'id': pid, 'channel': channel, 'hello': hello, 'bye': False, 'title': title, 'width': width, 'height': height,
         'root': {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': BG, 'border': LN, 'radius': 4, 'children': body}}
    if state:
        d['state'] = state
    return d


def write(name, d):
    with open(os.path.join(OUT, name + '.json'), 'w', encoding='utf8') as f:
        json.dump(d, f, indent=1)
    print('wrote', name)


# ---------------------------------------------------------------- hub

def tile(key, no, title, desc, stat):
    return {'type': 'button', 'id': 'open_' + key, 'w': 'fill', 'h': 'fill', 'bg': BG2, 'border': LN, 'radius': 4, 'hover': HOV, 'pad': [10, 14, 12, 14], 'gap': 3,
            'msg': {'action': 'open', 'target': key}, 'args': {}, 'children': [
                {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 3, 'children': [
                    label(no, color=MU), sp(h='fill'), label(title, scale=1.5, color=TX), label(desc, color=MU), label(stat, color=HI, scale=1.2)]}]}


def hub():
    body = [
        header('RAID HUB', '{vm.who}', [chip('{vm.rp}')]),
        {'type': 'column', 'w': 'fill', 'h': 'fill', 'pad': [4, 18, 18, 18], 'gap': 12, 'children': [
            {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 12, 'children': [
                tile('shop', '01', 'Raid Shop', 'Spend your Raid Points', '{vm.rp}'),
                tile('trophy', '02', 'Trophy Room', 'Every boss you have beaten', '{vm.trophies}')]},
            {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 12, 'children': [
                tile('legends', '03', 'Hall of Legends', "The server's first renowned wins", '{vm.legends}'),
                tile('leaders', '04', 'Leaderboards', 'See where you rank', '{vm.raids}')]},
        ]},
    ]
    return page('cobbleraids_hub', 'cobbleraids.hub', 'Raid Hub', 720, 400, body)


# ---------------------------------------------------------------- leaderboards

def leaders():
    q = {'stat': 'str:32', 'scope': 'enum:alltime|weekly'}
    row = {'type': 'row', 'repeat': {'for': 'vm.rows', 'as': 'r', 'limit': 12}, 'w': 'fill', 'bg': '{r.bg}', 'border': '#12475F', 'radius': 3, 'pad': [4, 14, 4, 14], 'gap': 10, 'children': [
        label('{r.rank}', w=50, color='{r.color}'), label('{r.name}', w='fill', color='{r.color}'), label('{r.val}', w=120, color=HI, text_align='right')]}
    body = [
        header('LEADERBOARDS', '{vm.crumb}', [btn('{vm.scopeLabel}', id='scope', w=80, args=q, argv={'stat': 'lp.stat', 'scope': 'vm.otherScope'},
                                                   msg={'action': 'query', 'stat': '{stat}', 'scope': '{scope}'})]),
        {'type': 'column', 'w': 'fill', 'h': 'fill', 'pad': [4, 18, 14, 18], 'gap': 6, 'children': [
            {'type': 'row', 'w': 'fill', 'cross': 'center', 'gap': 10, 'children': [
                btn('<', id='prev', w=30, args=q, argv={'stat': 'vm.prevStat', 'scope': 'vm.scope'}, msg={'action': 'query', 'stat': '{stat}', 'scope': '{scope}'}),
                label('{lp.title}', w='fill', scale=1.4, color=TX, text_align='center'),
                btn('>', id='next', w=30, args=q, argv={'stat': 'vm.nextStat', 'scope': 'vm.scope'}, msg={'action': 'query', 'stat': '{stat}', 'scope': '{scope}'})]},
            {'type': 'row', 'w': 'fill', 'pad': [2, 14, 2, 14], 'gap': 10, 'children': [
                label('RANK', w=50, color=MU), label('PLAYER', w='fill', color=MU), label('{vm.valueHead}', w=120, color=MU, text_align='right')]},
            sp(h=1, w='fill', bg=LN),
            {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 3, 'children': [
                label('Nobody is on this board yet.', **{'if': 'vm.empty', 'color': MU, 'w': 'fill', 'text_align': 'center', 'pad': [30, 0, 0, 0]}), row]},
            label('{vm.counts}', color=MU, w='fill'),
        ]},
    ]
    return page('cobbleraids_leaders', 'cobbleraids.leaders', 'Leaderboards', 720, 460, body)


# ---------------------------------------------------------------- legends

def legends():
    q = {'page': 'int:0:9999', 'mine': 'bool', 'tier': 'str:32'}
    m = {'action': 'query', 'page': '{page}', 'mine': '{mine}', 'tier': '{tier}'}

    def mine_btn(on):
        return btn('Mine' if on else 'Everyone', id='mine_on' if on else 'mine_off', w=70, args=q,
                   **{'if': "$mine == '%s'" % ('true' if on else 'false'), 'set': {'mine': "'false'" if on else "'true'"},
                      'argv': {'page': '0', 'mine': "'false'" if on else "'true'", 'tier': '$tier'}, 'msg': m})

    tiers = {'type': 'row', 'gap': 4, 'w': 'content', 'children': [
        btn('All tiers', id='tier_all', args=q, set={'tier': "''"}, argv={'page': '0', 'mine': '$mine', 'tier': "''"}, msg=m),
        btn('{t.name}', id='tier_pick_on', args=q, bg=HOV, border=HI, color=TX, repeat={'for': 'lg.tiers', 'as': 't', 'limit': 8, 'if': '$tier == t.id'},
            set={'tier': 't.id'}, argv={'page': '0', 'mine': '$mine', 'tier': 't.id'}, msg=m),
        btn('{t.name}', id='tier_pick', args=q, repeat={'for': 'lg.tiers', 'as': 't', 'limit': 8, 'if': '$tier != t.id'},
            set={'tier': 't.id'}, argv={'page': '0', 'mine': '$mine', 'tier': 't.id'}, msg=m),
    ]}
    card = {'type': 'row', 'repeat': {'for': 'vm.rows', 'as': 'e', 'limit': 8}, 'w': 'fill', 'bg': BG2, 'border': '{e.color}', 'radius': 4, 'pad': [5, 12, 5, 12], 'gap': 10, 'cross': 'center', 'children': [
        {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [
            label('{e.line1}', color='{e.color}', scale=1.2), label('{e.line2}   {e.when|day}', color=MU)]},
        label('{e.victors}', color=HI, w=240, text_align='right')]}
    body = [
        header('HALL OF LEGENDS', '/ {vm.total}', [mine_btn(False), mine_btn(True)]),
        {'type': 'row', 'w': 'fill', 'pad': [0, 18, 4, 18], 'gap': 6, 'children': [label('Tier:', color=MU), tiers]},
        {'type': 'column', 'w': 'fill', 'h': 'fill', 'pad': [2, 18, 4, 18], 'gap': 4, 'children': [
            label('{vm.emptyText}', **{'if': 'vm.none', 'color': MU, 'w': 'fill', 'text_align': 'center', 'pad': [40, 0, 0, 0]}), card]},
        {'type': 'row', 'w': 'fill', 'pad': [4, 18, 12, 18], 'cross': 'center', 'children': [
            btn('< Previous', id='prev', w=80, args=q, enabled='vm.canPrev', argv={'page': 'vm.prev', 'mine': '$mine', 'tier': '$tier'}, msg=m),
            label('{vm.where}', w='fill', color=MU, text_align='center'),
            btn('Next >', id='next', w=80, args=q, enabled='vm.canNext', argv={'page': 'vm.next', 'mine': '$mine', 'tier': '$tier'}, msg=m)]},
    ]
    return page('cobbleraids_legends', 'cobbleraids.legends', 'Hall of Legends', 760, 470, body, state={'mine': 'false', 'tier': ''})


# ---------------------------------------------------------------- shop

def shop():
    q = {'page': 'int:0:9999', 'id': 'str:64'}
    item_cell = {'type': 'button', 'id': 'buy', 'w': 'fill', 'h': 50, 'bg': '{e.bg}', 'border': '{e.border}', 'radius': 3, 'hover': HOV, 'pad': [4, 2, 3, 2], 'gap': 1, 'cross': 'center',
                 'args': q, 'argv': {'page': 'sh.pageIndex', 'id': 'e.id'}, 'msg': {'action': 'buy', 'page': '{page}', 'id': '{id}'},
                 'tooltip': ['{e.tipName}', '{e.tipPrice}', '{e.tipLimit}'],
                 'repeat': {'for': 'cells', 'as': 'e'}, 'children': [{'type': 'column', 'w': 'fill', 'cross': 'center', 'gap': 1, 'children': [
                     {'type': 'item', 'item': '{e.itemId}', 'count': 1, 'w': 24, 'h': 24, 'if': 'e.itemId'},
                     label('{e.name}', **{'if': '!e.itemId', 'color': '{e.nameColor}'}),
                     label('{e.price}', color='{e.priceColor}'),
                     label('{e.meta}', **{'if': 'e.meta', 'color': MU})]}]}
    item_cell['repeat'] = {'for': 'row.cells', 'as': 'e', 'if': '!e.blank'}
    gap_cell = sp(w='fill', repeat={'for': 'row.cells', 'as': 'e', 'if': 'e.blank'})
    pbtn = dict(item_cell)
    pbtn.pop('repeat')
    pbtn.update({'id': 'buyp', 'h': 56, 'cross': 'start', 'pad': [4, 8, 3, 8]})
    pbtn['children'] = [{'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'children': [
        {'type': 'image', 'pokemon': '{e.sprite}', 'w': 48, 'h': 44, 'if': 'e.sprite'},
        {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [
            label('{e.name}', color='{e.nameColor}', scale=1.1),
            label('{e.meta}   {e.meta2}', color=MU),
            label('{e.price}', color='{e.priceColor}')]}]}]
    reroll = btn('{e.rerollText}', id='reroll', h=16, w='fill', args=q, color='#FFD84A', border='#FFD84A', bg='#281F06', hover='#4A3A0A',
                 argv={'page': 'sh.pageIndex', 'id': 'e.rerollId'}, msg={'action': 'buy', 'page': '{page}', 'id': '{id}'}, **{'if': 'e.canReroll'})
    poke = {'type': 'column', 'repeat': {'for': 'row.cells', 'as': 'e', 'if': '!e.blank'}, 'w': 'fill', 'gap': 2, 'children': [pbtn, reroll]}
    rows_items = {'type': 'row', 'repeat': {'for': 'vm.rows', 'as': 'row'}, 'if': '!vm.pokemon', 'w': 'fill', 'gap': 5, 'children': [item_cell, gap_cell]}
    rows_poke = {'type': 'row', 'repeat': {'for': 'vm.rows', 'as': 'row'}, 'if': 'vm.pokemon', 'w': 'fill', 'gap': 5, 'children': [poke, dict(gap_cell)]}
    body = [
        header('RAID SHOP', '{vm.crumb}', [chip('NEW STOCK IN {vm.deadline|countdown}', **{'if': 'vm.rotates'}), chip('{vm.balanceText}')]),
        {'type': 'scroll', 'id': 'grid', 'w': 'fill', 'h': 'fill', 'pad': [4, 18, 4, 18], 'children': [
            {'type': 'column', 'w': 'fill', 'gap': 5, 'children': [
                label('Nothing for sale right now.', **{'if': 'vm.none', 'color': MU, 'w': 'fill', 'text_align': 'center', 'pad': [40, 0, 0, 0]}),
                rows_items, rows_poke]}]},
        {'type': 'row', 'w': 'fill', 'pad': [6, 18, 12, 18], 'cross': 'center', 'children': [
            btn('< Previous', id='prev', w=80, enabled='!vm.single', args={'page': 'int:0:9999'}, argv={'page': 'vm.prev'}, msg={'action': 'turn', 'page': '{page}'}),
            label('{vm.where}', w='fill', color=MU, text_align='center'),
            btn('Next >', id='next', w=80, enabled='!vm.single', args={'page': 'int:0:9999'}, argv={'page': 'vm.next'}, msg={'action': 'turn', 'page': '{page}'})]},
        sp(id='restock', **{'if': 'vm.rotates', 'every_ms': 20000, 'args': {'page': 'int:0:9999'}, 'argv': {'page': 'sh.pageIndex'}, 'msg': {'action': 'turn', 'page': '{page}'}}),
    ]
    return page('cobbleraids_shop', 'cobbleraids.shop', 'Raid Shop', 880, 500, body)


# ---------------------------------------------------------------- trophy room

def trophy():
    q = {'page': 'int:0:9999', 'query': 'str:64', 'tier': 'str:32', 'shiny': 'int:0:2', 'sort': 'int:0:3'}
    m = {'action': 'query', 'page': '{page}', 'query': '{query}', 'tier': '{tier}', 'shiny': '{shiny}', 'sort': '{sort}'}

    def av(**over):
        d = {'page': '0', 'query': '$q', 'tier': '$tier', 'shiny': '$shiny', 'sort': '$sort'}
        d.update(over)
        return d

    def pick(group, value, text, ident):
        lit = "'%s'" % value
        on = btn(text, id=ident + '_on', args=q, bg=HOV, border=HI, color=TX, set={group: lit}, argv=av(**{group: lit}), msg=m,
                 **{'if': "$%s == %s" % (group, lit)})
        off = btn(text, id=ident, args=q, set={group: lit}, argv=av(**{group: lit}), msg=m, **{'if': "$%s != %s" % (group, lit)})
        return [on, off]

    tier_on = btn('{t.name}', id='tier_pick_on', args=q, bg=HOV, border=HI, color=TX, repeat={'for': 'tp.tiers', 'as': 't', 'limit': 8, 'if': '$tier == t.id'},
                  set={'tier': 't.id'}, argv=av(tier='t.id'), msg=m)
    tier_off = btn('{t.name}', id='tier_pick', args=q, repeat={'for': 'tp.tiers', 'as': 't', 'limit': 8, 'if': '$tier != t.id'},
                   set={'tier': 't.id'}, argv=av(tier='t.id'), msg=m)
    case = {'type': 'column', 'repeat': {'for': 'vm.cases', 'as': 'c'}, 'w': 'fill', 'h': 'fill', 'bg': BG2, 'border': '{c.tierColor}', 'radius': 4, 'pad': [10, 12, 10, 12], 'gap': 4, 'children': [
        label('{c.name}', **{'if': 'c.filled', 'scale': 1.5, 'color': TX}),
        {'type': 'image', 'pokemon': '{c.sprite}', 'w': 'fill', 'h': 56, 'if': 'c.sprite'},
        label('{c.level}', **{'if': 'c.filled', 'color': HI}),
        {'type': 'row', 'if': 'c.filled', 'w': 'fill', 'gap': 8, 'children': [label('{c.tier}', color='{c.tierColor}'), label('{c.shiny}', color='{c.shinyColor}')]},
        sp(h=4, **{'if': 'c.filled'}),
        {'type': 'row', 'if': 'c.filled', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [
            label('IV', w=20, color=MU), {'type': 'bar', 'w': 'fill', 'h': 7, 'value': 'c.iv', 'max': '100', 'fill': HI, 'fill_bg': '#0A2A40', 'radius': 2}, label('{c.ivText}', w=36, color=TX, text_align='right')]},
        {'type': 'row', 'if': 'c.filled', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [
            label('EV', w=20, color=MU), {'type': 'bar', 'w': 'fill', 'h': 7, 'value': 'c.ev', 'max': '100', 'fill': '#7DDF64', 'fill_bg': '#0A2A40', 'radius': 2}, label('{c.evText}', w=36, color=TX, text_align='right')]},
        sp(h='fill', **{'if': 'c.filled'}),
        label('First {c.first|day}', **{'if': 'c.filled', 'color': MU}),
        label('{c.times}', **{'if': 'c.filled', 'color': MU}),
        label('Unclaimed', **{'if': '!c.filled', 'color': MU, 'scale': 1.3, 'w': 'fill', 'text_align': 'center', 'pad': [50, 0, 0, 0]}),
        label('{c.blank}', **{'if': '!c.filled', 'color': MU, 'w': 'fill', 'text_align': 'center'}),
    ]}
    body = [
        header('TROPHY ROOM', '{vm.crumb}'),
        {'type': 'row', 'w': 'fill', 'pad': [0, 18, 4, 18], 'gap': 6, 'cross': 'center', 'children': [
            {'type': 'input', 'id': 'search', 'bind': 'q', 'hint': 'Search species...', 'w': 170, 'h': 22, 'max_len': 64, 'bg': '#021018', 'border': LN, 'radius': 3, 'color': TX,
             'debounce_ms': 400, 'args': q, 'argv': av(), 'msg': m},
            btn('All tiers', id='tier_all', args=q, set={'tier': "''"}, argv=av(tier="''"), msg=m),
            tier_on, tier_off]},
        {'type': 'row', 'w': 'fill', 'pad': [0, 18, 6, 18], 'gap': 6, 'cross': 'center', 'children':
            pick('shiny', 0, 'Shiny: all', 'sh0') + pick('shiny', 1, 'Shiny only', 'sh1') + pick('shiny', 2, 'Normal only', 'sh2')
            + [sp(w=10)] + pick('sort', 0, 'Newest', 'so0') + pick('sort', 1, 'Oldest', 'so1') + pick('sort', 2, 'Species', 'so2') + pick('sort', 3, 'Best IV', 'so3')},
        {'type': 'row', 'w': 'fill', 'h': 'fill', 'pad': [2, 18, 4, 18], 'gap': 10, 'children': [case]},
        {'type': 'row', 'w': 'fill', 'pad': [4, 18, 12, 18], 'cross': 'center', 'children': [
            btn('< Previous', id='prev', w=80, args=q, enabled='vm.canPrev', argv=av(page='vm.prev', query='$q'), msg=m),
            label('{vm.where}', w='fill', color=MU, text_align='center'),
            btn('Next >', id='next', w=80, args=q, enabled='vm.canNext', argv=av(page='vm.next', query='$q'), msg=m)]},
    ]
    return page('cobbleraids_trophy', 'cobbleraids.trophy', 'Trophy Room', 840, 480, body, state={'q': '', 'tier': '', 'shiny': '0', 'sort': '0'})


# ---------------------------------------------------------------- reward reveal

def reveal():
    def kv(k, v):
        return {'type': 'row', 'w': 'fill', 'children': [label(k, color=MU, w='fill'), label(v, color=TX)]}

    side = {'type': 'column', 'w': 190, 'h': 'fill', 'bg': BG2, 'border': LN, 'radius': 4, 'pad': [10, 12, 10, 12], 'gap': 4, 'children': [
        label('RAID SUMMARY', color=HI), kv('Boss defeated', '{of.species}'), kv('Raid tier', '{of.tierName}'), kv('Completion time', '{vm.time}'),
        sp(h=8), label('YOUR CONTRIBUTION', color=HI), kv('Damage dealt', '{vm.contribution}'), kv('Raiders', '{of.participants}')]}
    slot = {'type': 'column', 'repeat': {'for': 'row.slots', 'as': 's'}, 'w': 92, 'h': 66, 'anim': 'pop', 'bg': BG2, 'border': LN, 'radius': 4, 'pad': [4, 3, 4, 3], 'cross': 'center', 'gap': 1, 'children': [
        {'type': 'item', 'item': '{s.itemId}', 'count': 1, 'w': 24, 'h': 24, 'if': 's.itemId'},
        {'type': 'image', 'pokemon': '{s.sprite}', 'w': 32, 'h': 24, 'if': 's.sprite'},
        label('{s.name}', color=TX, text_align='center', w='fill'),
        label('{s.amount}', color=HI)]}
    chamber = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': BG2, 'border': LN, 'radius': 4, 'pad': [12, 14, 12, 14], 'gap': 8, 'cross': 'center', 'children': [
        label('{vm.bannerText}', **{'if': 'vm.hasBanner', 'color': '#FF6BD6', 'scale': 1.4}),
        label('{vm.boon}', **{'if': 'vm.boon', 'color': MU}),
        sp(h='fill'),
        {'type': 'column', 'if': "vm.stage == 'CHOOSING' && $picked != '1'", 'cross': 'center', 'gap': 4, 'w': 'fill', 'children': [
            label('{vm.promptBig}', scale=1.8, color=HI, text_align='center', w='fill'), label('{vm.promptSmall}', color=MU, text_align='center', w='fill')]},
        label('REVEALING...', **{'if': "vm.stage == 'CHOOSING' && $picked == '1'", 'scale': 1.8, 'color': HI, 'w': 'fill', 'text_align': 'center', 'anim': 'pulse', 'anim_ms': 700}),
        label('Something went wrong. Check chat for details.', **{'if': "vm.stage == 'RESULT' && !vm.ok", 'color': '#FF6B6B', 'w': 'fill', 'text_align': 'center'}),
        {'type': 'column', 'if': "vm.stage == 'RESULT' && vm.ok", 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [
            {'type': 'row', 'repeat': {'for': 'vm.grows', 'as': 'row'}, 'gap': 6, 'children': [slot]}]},
        sp(h='fill')]}
    choice = btn('{c.label}', id='choose', h=26, pad=[4, 16, 4, 16], bg=HI, border=HI, hover='#8FF0FF', color='#021018', enabled="$picked != '1'",
                 repeat={'for': 'vm.choices', 'as': 'c', 'limit': 6, 'if': "vm.stage == 'CHOOSING'"}, args={'choice': 'str:48'}, argv={'choice': 'c.id'},
                 msg={'action': 'choose', 'choice': '{choice}'}, set={'picked': "'1'"})
    body = [
        {'type': 'row', 'w': 'fill', 'pad': [12, 18, 8, 18], 'gap': 12, 'cross': 'center', 'children': [
            label('RAID REWARDS', scale=1.5, color=HI), label('{vm.crumb}', color=MU), sp(w='fill'), chip('{vm.hint}', **{'if': 'vm.hint'})]},
        {'type': 'row', 'w': 'fill', 'h': 'fill', 'pad': [4, 18, 6, 18], 'gap': 10, 'children': [side, chamber]},
        {'type': 'row', 'w': 'fill', 'pad': [4, 18, 12, 18], 'gap': 8, 'align': 'center', 'children': [choice, btn('Close', id='done', w=90, h=26, close=True, **{'if': "vm.stage == 'RESULT'"})]},
    ]
    return page('cobbleraids_reveal', 'cobbleraids.reveal', 'Raid Rewards', 780, 420, body, state={'picked': ''})


# ---------------------------------------------------------------- raid capture

def capture():
    OFFER = "vm.stage == 'OFFER' && $go != '1'"
    PULSE = "($go == '1' && vm.stage == 'OFFER') || vm.stage == 'PULSE'"
    THROW = "vm.stage == 'THROW'"
    RESULT = "vm.stage == 'RESULT'"
    TIMING = "(%s) || vm.stage == 'THROW'" % PULSE
    lamp = lambda i: sp(w=12, h=12, bg='{vm.lamp%d}' % i, border=LN)
    head = {'type': 'row', 'w': 'fill', 'pad': [12, 18, 8, 18], 'gap': 10, 'cross': 'center', 'children': [
        label('RAID CAPTURE', **{'if': OFFER, 'scale': 1.5, 'color': HI}),
        label('STABILIZE ECHO  {vm.pnum} / 3', **{'if': PULSE, 'scale': 1.5, 'color': HI}),
        label('THROW THE BALL', **{'if': THROW, 'scale': 1.5, 'color': HI}),
        label('{vm.resultTitle}', **{'if': RESULT, 'scale': 1.5, 'color': HI}),
        sp(w='fill'), label('{vm.state}', color=MU), lamp(0), lamp(1), lamp(2)]}
    info = {'type': 'column', 'w': 210, 'h': 'fill', 'bg': BG2, 'border': LN, 'radius': 4, 'pad': [10, 12, 10, 12], 'gap': 4, 'children': [
        label('{vm.name}', scale=1.4, color=TX), label('{vm.tier}', color='#FFB02E'), label('{vm.rpText}', color=HI), sp(h=6),
        label('Capture chance', color=MU), label('{vm.odds}', color=TX),
        label('Three pulses. One throw. Timing improves your odds.', **{'if': OFFER, 'color': MU, 'wrap': True, 'w': 'fill'}),
        {'type': 'column', 'if': '!(%s)' % OFFER, 'w': 'fill', 'gap': 2, 'children': [
            sp(h=4), {'type': 'row', 'repeat': {'for': 'vm.pulseRows', 'as': 'r'}, 'w': 'fill', 'children': [label('{r.a}', color=MU, w='fill'), label('{r.b}', color='{r.color}')]}]},
        label('Match the white marker to the center zone. Misses keep your base chance.', **{'if': TIMING, 'color': MU, 'wrap': True, 'w': 'fill'})]}
    chamber = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': BG2, 'border': LN, 'radius': 4, 'pad': [10, 14, 10, 14], 'gap': 8, 'cross': 'center', 'children': [
        sp(h='fill'),
        {'type': 'image', 'pokemon': '{vm.sprite}', 'w': 'fill', 'h': 110, 'if': "vm.sprite && !(%s && $ws == vm.step) && !(%s && !vm.ok)" % (THROW, RESULT)},
        label('{vm.name}', **{'if': "!(%s && $ws == vm.step) && !(%s)" % (THROW, RESULT), 'scale': 1.6, 'color': TX, 'text_align': 'center', 'w': 'fill'}),
        {'type': 'item', 'item': 'cobblemon:poke_ball', 'count': 1, 'w': 48, 'h': 48, 'anim': 'shake', 'if': "(%s && $ws == vm.step) || (%s && vm.ok)" % (THROW, RESULT)},
        sp(h='fill'),
        label('Waiting for server score...', **{'if': "(%s) && $ws == vm.step" % TIMING, 'color': MU, 'w': 'fill', 'text_align': 'center'}),
        label('Press SPACE or click Stabilize at the center', **{'if': "(%s) && $ws != vm.step" % PULSE, 'color': TX, 'w': 'fill', 'text_align': 'center'}),
        label('Time your throw at the center!', **{'if': "(%s) && $ws != vm.step" % THROW, 'color': TX, 'w': 'fill', 'text_align': 'center'}),
        {'type': 'sweep', 'if': TIMING, 'w': 'fill', 'h': 18, 'bg': '#021018', 'travel': 'vm.travel', 'good': 'vm.good', 'perfect': 'vm.perfect', 'anchor': '$_sent'},
        label('GOLD: GOOD   |   CYAN CENTER: PERFECT', **{'if': TIMING, 'color': MU, 'w': 'fill', 'text_align': 'center'}),
        label('{vm.outro1}', **{'if': OFFER, 'color': TX, 'w': 'fill', 'text_align': 'center'}),
        label('Other loot is safe. Choose within {vm.deadline|countdown}.', **{'if': OFFER, 'color': MU, 'w': 'fill', 'text_align': 'center'}),
        label('{vm.result1}', **{'if': RESULT, 'color': TX, 'w': 'fill', 'text_align': 'center'}),
        label('{vm.result2}', **{'if': RESULT, 'color': MU, 'w': 'fill', 'text_align': 'center'})]}
    actions = {'type': 'row', 'w': 'fill', 'pad': [4, 18, 12, 18], 'gap': 8, 'align': 'center', 'children': [
        solid('Attempt Capture', id='attempt', w=130, h=26, **{'if': OFFER, 'args': {'attempt': 'bool'}, 'argv': {'attempt': "'true'"}, 'msg': {'action': 'choice', 'attempt': '{attempt}'}, 'set': {'go': "'1'"}}),
        btn('{vm.keepText}', id='keep', w=130, h=26, close=True, **{'if': OFFER, 'args': {'attempt': 'bool'}, 'argv': {'attempt': "'false'"}, 'msg': {'action': 'choice', 'attempt': '{attempt}'}}),
        solid('Stabilize [SPACE]', id='press_pulse', w=150, h=26, key='space', enabled='$ws != vm.step',
              **{'if': PULSE, 'args': {'index': 'int:0:2'}, 'argv': {'index': 'vm.pidx'}, 'msg': {'action': 'pulse', 'index': '{index}'}, 'set': {'ws': 'vm.step'}}),
        solid('Throw Ball [SPACE]', id='press_throw', w=150, h=26, key='space', enabled='$ws != vm.step',
              **{'if': THROW, 'args': {}, 'msg': {'action': 'throw'}, 'set': {'ws': 'vm.step'}}),
        solid('Close', id='done', w=100, h=26, close=True, **{'if': RESULT})]}
    body = [head, {'type': 'row', 'w': 'fill', 'h': 'fill', 'pad': [4, 18, 6, 18], 'gap': 10, 'children': [info, chamber]}, actions]
    return page('cobbleraids_capture', 'cobbleraids.capture', 'Raid Capture', 780, 420, body, state={'go': '', 'ws': ''})


if __name__ == '__main__':
  for name, fn in (('cobbleraids_hub', hub), ('cobbleraids_leaders', leaders), ('cobbleraids_legends', legends), ('cobbleraids_shop', shop), ('cobbleraids_trophy', trophy), ('cobbleraids_reveal', reveal), ('cobbleraids_capture', capture)):
    write(name, fn())
