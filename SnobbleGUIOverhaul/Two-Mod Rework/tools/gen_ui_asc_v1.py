"""Generates the native AscensionLib pages (ascensionlib_<name>.json): the dark-LCD Pokedex tablet look (screen, panel, cyan glow lines).
Texts and numbers come from AscViews (server); AscensionLib still decides every balance, fee, craft and fusion."""
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


def btn(text, **kw):
    d = {'type': 'button', 'text': text, 'h': 22, 'pad': [3, 10, 3, 10], 'cross': 'center', 'align': 'center', 'bg': PANEL, 'border': LN, 'radius': 2,
         'hover': HOV, 'disabled': '#0A1A26', 'color': INK}
    d.update(kw)
    return d


def primary(text, **kw):
    return btn(text, **dict({'bg': ACC, 'border': ACC, 'hover': '#8FE8FF', 'color': '#021018'}, **kw))


def beat():
    """Every 2 seconds the page tells AscensionLib that a full-screen page is open, as the web page did (the overlay must not open over it)."""
    return {'type': 'spacer', 'id': 'beat', 'w': 0, 'h': 0, 'every_ms': 2000, 'args': {}, 'msg': {'channel': 'ascension_gui_beat'}}


def head(title, extras=()):
    return {'type': 'row', 'w': 'fill', 'pad': [8, 14, 8, 14], 'gap': 10, 'cross': 'center', 'bg': HEAD, 'border': LN, 'children': [
        label(title, scale=1.3, color=ACC)] + [sp(w='fill')] + list(extras) + [btn('X', w=26, close=True)]}


def page(pid, channel, title, width, height, body, state=None, hello='ready'):
    d = {'v': 2, 'id': pid, 'channel': channel, 'hello': hello, 'bye': False, 'title': title, 'width': width, 'height': height,
         'root': {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': SCR, 'border': LN, 'radius': 2, 'children': body + [beat()]}}
    if state:
        d['state'] = state
    return d


def write(name, d):
    with open(os.path.join(OUT, name + '.json'), 'w', encoding='utf8') as f:
        json.dump(d, f, indent=1)
    print('wrote', name)


# ---------------------------------------------------------------- wallet panel (the wallet page and the Wallet tab of the AscensionDex)

def wallet_panel(cond=None):
    line = {'type': 'row', 'repeat': {'for': 'vm.lines', 'as': 'l', 'limit': 24}, 'w': 'fill', 'gap': 10, 'cross': 'center', 'bg': ROW, 'border': LN, 'pad': [6, 8, 6, 8], 'children': [
        {'type': 'image', 'pokemon': '{l.icon}', 'w': 32, 'h': 32, 'bg': WELL, 'border': LN},
        label('{l.name}', color='{l.color}', w='fill'),
        label('{l.amount}', color=ACC, w=60, text_align='right'),
        btn('{l.takeText}', id='take', w=56, args={'material': 'str:48', 'amount': 'int:1:2304'}, argv={'material': 'l.id', 'amount': 'vm.amts[$ai]'},
            enabled='l.takeOk[$ai]', msg={'channel': 'ascension_wallet_withdraw', 'material': '{material}', 'amount': '{amount}'}),
        label('{l.feeText[$ai]}', color=MU, w=170, wrap=True),
        {'type': 'row', 'if': 'l.hasCarried', 'gap': 6, 'cross': 'center', 'w': 130, 'children': [
            label('{l.carried}', color=MU),
            btn('Deposit', id='deposit', w=58, h=18, args={'material': 'str:48'}, argv={'material': 'l.id'}, msg={'channel': 'ascension_wallet_deposit', 'material': '{material}'})]}]}
    chips = [btn(str(n), id='amt%d' % i, w=40, h=18, set={'ai': i}, color=ACC) for i, n in enumerate((1, 8, 64, 256))]
    col = {'type': 'column', 'w': 'fill', 'h': 'fill', 'pad': [10, 14, 6, 14], 'gap': 8, 'children': [
        label('Take currency out as items you can trade (a {vm.fee}% fee, rounded up, is paid on top), then redeem them back with Deposit. Tower Marks are bound to you.',
              color=MU, wrap=True, w='fill'),
        {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [label('Amount per Take:', color=MU), label('{vm.amts[$ai]}', color=ACC, w=40)] + chips},
        {'type': 'row', 'if': 'vm.hasCatalyst', 'w': 'fill', 'gap': 10, 'cross': 'center', 'bg': ROW, 'border': LN, 'pad': [6, 10, 6, 10], 'children': [
            label('Catalyst', color=INK), {'type': 'bar', 'w': 'fill', 'h': 10, 'value': 'vm.catPct', 'max': '100', 'fill': ACC, 'fill_bg': WELL, 'radius': 1},
            label('{vm.catText}', color=MU)]},
        {'type': 'scroll', 'id': 'lines', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 6, 'children': [line]}]},
        label('{vm.msg}', **{'if': 'vm.hasMsg', 'w': 'fill', 'wrap': True, 'color': '{vm.msgColor}', 'bg': ROW, 'border': LN, 'pad': [6, 10, 6, 10]})]}
    if cond:
        col['if'] = cond
    return col


def deposit_all():
    return primary('Deposit all vouchers', id='deposit_all', w=150, enabled='vm.canDepositAll', args={}, msg={'channel': 'ascension_wallet_deposit'})


def wallet():
    body = [head('WALLET'), wallet_panel(),
            {'type': 'row', 'w': 'fill', 'pad': [8, 14, 8, 14], 'gap': 10, 'cross': 'center', 'bg': HEAD, 'border': LN, 'children': [
                label('{vm.free}', color=MU, w='fill'), deposit_all(), btn('Close', w=70, close=True)]}]
    return page('ascensionlib_wallet', 'ascension_wallet_ready', 'Wallet', 760, 560, body, state={'ai': 2})


# ---------------------------------------------------------------- guide panel

def guide_panel(cond=None):
    cells = [label('{cl.x}', color='{cl.color}', w='fill', wrap=True, repeat={'for': 'b.cells', 'as': 'cl', 'limit': 6})]
    blocks = {'type': 'column', 'repeat': {'for': 't.blocks', 'as': 'b', 'limit': 120}, 'w': 'fill', 'gap': 2, 'children': [
        label('{b.text}', **{'if': "b.k == 'p'", 'color': INK, 'wrap': True, 'w': 'fill', 'pad': [2, 0, 2, 0]}),
        label('{b.text}', **{'if': "b.k == 'li'", 'color': INK, 'wrap': True, 'w': 'fill', 'pad': [0, 0, 0, 8]}),
        label('{b.text}', **{'if': "b.k == 'tip'", 'color': INK, 'wrap': True, 'w': 'fill', 'bg': ROW, 'border': ACC, 'pad': [6, 8, 6, 8]}),
        label('{b.text}', **{'if': "b.k == 'warn'", 'color': INK, 'wrap': True, 'w': 'fill', 'bg': '#2B1612', 'border': WARN, 'pad': [6, 8, 6, 8]}),
        {'type': 'row', 'if': "b.k == 'th'", 'w': 'fill', 'gap': 8, 'bg': HEAD, 'pad': [2, 4, 2, 4], 'children': cells},
        {'type': 'row', 'if': "b.k == 'tr'", 'w': 'fill', 'gap': 8, 'pad': [1, 4, 1, 4], 'children': cells}]}
    topic_btn = btn('{t.title}', id='topic', w='fill', h=24, align='start', set={'tp': 't.i'}, bg=ROW, repeat={'for': 'vm.topics', 'as': 't', 'limit': 20, 'if': '$tp != t.i'})
    topic_on = btn('{t.title}', id='topic_on', w='fill', h=24, set={'tp': 't.i'}, bg=PANEL, border=ACC, color='#FFFFFF', repeat={'for': 'vm.topics', 'as': 't', 'limit': 20, 'if': '$tp == t.i'})
    content = {'type': 'column', 'repeat': {'for': 'vm.topics', 'as': 't', 'limit': 20, 'if': '$tp == t.i'}, 'w': 'fill', 'gap': 6, 'children': [
        label('{t.title}', scale=1.4, color=ACC), blocks,
        {'type': 'row', 'w': 'fill', 'pad': [6, 0, 0, 0], 'children': [
            btn('{t.prev}', id='gprev', w=170, **{'if': 't.hasPrev', 'set': {'tp': 't.pi'}}), sp(w='fill'),
            btn('{t.next}', id='gnext', w=170, **{'if': 't.hasNext', 'set': {'tp': 't.ni'}})]}]}
    col = {'type': 'row', 'w': 'fill', 'h': 'fill', 'pad': [10, 14, 6, 14], 'gap': 12, 'children': [
        {'type': 'scroll', 'id': 'topics', 'w': 200, 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 4, 'children': [topic_btn, topic_on]}]},
        {'type': 'scroll', 'id': 'content', 'w': 'fill', 'h': 'fill', 'pad': [0, 6, 0, 0], 'children': [{'type': 'column', 'w': 'fill', 'gap': 6, 'children': [
            content, label('The guide is not available.', **{'if': '!vm.hasTopics', 'color': MU})]}]}]}
    if cond:
        col['if'] = cond
    return col


def guide():
    body = [head('GUIDE'), guide_panel(),
            {'type': 'row', 'w': 'fill', 'pad': [8, 14, 8, 14], 'gap': 10, 'cross': 'center', 'bg': HEAD, 'border': LN, 'children': [
                label('Also in the Guide tab of your AscensionDex (/ascend dex).', color=MU, w='fill'), btn('Close', w=70, close=True)]}]
    return page('ascensionlib_guide', 'ascension_guide_facts', 'AscensionLib guide', 960, 620, body, state={'tp': 0})


# ---------------------------------------------------------------- party (AscensionDex)

def party_card(selected):
    cond = 's.i == $sel' if selected else 's.i != $sel'
    c = {'type': 'button', 'id': 'pick_on' if selected else 'pick', 'w': 'fill', 'h': 'fill', 'bg': PANEL if selected else ROW, 'border': GOLD if selected else LN, 'radius': 2,
         'hover': HOV, 'pad': [4, 6, 4, 6], 'set': {'sel': 's.i'}, 'repeat': {'for': 'vm.slots', 'as': 's', 'limit': 6, 'if': cond + ' && !s.empty'}, 'children': [
             {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 2, 'cross': 'center', 'children': [
                 {'type': 'row', 'w': 'fill', 'children': [label('{s.num}', color=MU), sp(w='fill'), label('{s.badge}', **{'if': 's.badge', 'color': GOOD})]},
                 {'type': 'image', 'pokemon': '{s.sprite}', 'w': 96, 'h': 60},
                 label('{s.name}', color='{s.nameColor}', scale=1.1),
                 {'type': 'row', 'gap': 6, 'cross': 'center', 'children': [
                     label('{s.level}', color=MU), label('{s.rarity}', color='{s.rarityColor}', bg='{s.rarityBg}', pad=[0, 5, 0, 5])]},
                 {'type': 'bar', 'w': 'fill', 'h': 6, 'value': 's.hp', 'max': '100', 'fill': '{s.hpColor}', 'fill_bg': WELL, 'radius': 1}]}]}
    return c


def party_rows():
    empty = {'type': 'column', 'repeat': {'for': 'vm.slots', 'as': 's', 'limit': 6, 'if': 's.empty'}, 'w': 'fill', 'h': 'fill', 'bg': ROW, 'border': LN, 'radius': 2, 'pad': [4, 6, 4, 6],
             'children': [label('{s.num}', color=MU), sp(h='fill'), label('Empty slot', color=MU, w='fill', text_align='center'), sp(h='fill')]}
    return empty


def party():
    # slots 0-2 on the top row and 3-5 on the bottom row: the same three nodes are used for each row, filtered by the slot number
    def row(lo, hi):
        sel_on = dict(party_card(True))
        sel_off = dict(party_card(False))
        sel_on['repeat'] = dict(sel_on['repeat'], **{'if': 's.i >= %d && s.i < %d && s.i == $sel && !s.empty' % (lo, hi)})
        sel_off['repeat'] = dict(sel_off['repeat'], **{'if': 's.i >= %d && s.i < %d && s.i != $sel && !s.empty' % (lo, hi)})
        sel_on['id'] = 'pick_on%d' % lo
        sel_off['id'] = 'pick%d' % lo
        emp = dict(party_rows())
        emp['repeat'] = dict(emp['repeat'], **{'if': 's.i >= %d && s.i < %d && s.empty' % (lo, hi)})
        return {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 10, 'children': [sel_on, sel_off, emp]}
    party_pane = {'type': 'column', 'if': "$tab == 'party'", 'w': 'fill', 'h': 'fill', 'pad': [10, 14, 6, 14], 'gap': 8, 'children': [
        label('Your party. Pick a Pokemon.', color=MU), row(0, 3), row(3, 6)]}
    tab = lambda text, key, **kw: btn(text, id='tab_' + key, w=100, h=26, set={'tab': "'%s'" % key}, **kw)
    wallet_tab = btn('Wallet', id='tab_wallet', w=100, h=26, set={'tab': "'wallet'"}, args={}, msg={'channel': 'ascension_wallet_ready'})
    guide_tab = tab('Guide', 'guide')
    fuse = btn('Fuse', id='fuse', w=100, h=26, enabled='vm.slots[$sel].canInspect', args={'hostId': 'str:64'}, argv={'hostId': 'vm.slots[$sel].id'},
               msg={'channel': 'ascension_fusion_open', 'hostId': '{hostId}'})
    tabbar = {'type': 'row', 'w': 'fill', 'pad': [4, 14, 0, 14], 'gap': 4, 'bg': HEAD, 'border': LN, 'children': [tab('Party', 'party'), wallet_tab, guide_tab, fuse]}
    foot_party = {'type': 'row', 'if': "$tab == 'party'", 'gap': 10, 'cross': 'center', 'children': [
        btn('Upgrade', id='upgrade', w=90, enabled='vm.slots[$sel].canUpgrade', args={'pokemonId': 'str:64'}, argv={'pokemonId': 'vm.slots[$sel].id'},
            msg={'channel': 'ascension_craft_open', 'pokemonId': '{pokemonId}'}),
        primary('Inspect', id='inspect', w=90, enabled='vm.slots[$sel].canInspect', args={'id': 'str:64'}, argv={'id': 'vm.slots[$sel].id'},
                msg={'channel': 'ascension_inspect_open', 'kind': 'owned', 'id': '{id}'})]}
    foot_wallet = {'type': 'row', 'if': "$tab == 'wallet'", 'gap': 10, 'cross': 'center', 'children': [deposit_all()]}
    foot = {'type': 'row', 'w': 'fill', 'pad': [8, 14, 8, 14], 'gap': 10, 'cross': 'center', 'bg': HEAD, 'border': LN, 'children': [
        label('{vm.slots[$sel].info}', **{'if': "$tab == 'party'", 'color': MU, 'w': 'fill'}),
        label('{vm.free}', **{'if': "$tab == 'wallet'", 'color': MU, 'w': 'fill'}),
        label('How AscensionLib works. The same text is on the wiki.', **{'if': "$tab == 'guide'", 'color': MU, 'w': 'fill'}),
        foot_party, foot_wallet, btn('Close', w=70, close=True)]}
    body = [head('ASCENSIONDEX'), party_pane, wallet_panel("$tab == 'wallet'"), guide_panel("$tab == 'guide'"), tabbar, foot]
    return page('ascensionlib_party', 'ascension_party_ready', 'AscensionDex', 860, 600, body,
                state={'sel': {'from': 'vm.first', 'default': 0}, 'tab': 'party', 'tp': 0, 'ai': 2})


# ---------------------------------------------------------------- inspect (owned Pokemon / scouting)

def inspect():
    left = {'type': 'column', 'w': 280, 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 10, 8, 10], 'gap': 4, 'cross': 'center', 'children': [
        {'type': 'row', 'w': 'fill', 'cross': 'center', 'children': [label('{e.dex}', color=MU), sp(w='fill'),
            label('{e.type}', **{'if': 'e.hasType', 'color': '#FFFFFF', 'bg': '{e.typeColor}', 'pad': [0, 5, 0, 5]})]},
        {'type': 'image', 'pokemon': '{e.sprite}', 'w': 120, 'h': 100},
        label('{e.name}', scale=1.3, color=INK),
        label('{e.sub}', color=MU),
        sp(h=4),
        {'type': 'column', 'w': 'fill', 'gap': 3, 'children': [
            {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'repeat': {'for': 'e.stats', 'as': 'st', 'limit': 6}, 'children': [
                label('{st.k}', color=MU, w=30),
                {'type': 'bar', 'w': 'fill', 'h': 8, 'value': 'st.bar', 'max': '160', 'fill': ACC, 'fill_bg': WELL, 'radius': 1},
                label('{st.v}', color=INK, w=28, text_align='right')]}]},
        sp(h='fill'),
        label('{e.grade}', color='{e.gradeColor}', bg='{e.gradeBg}', w='fill', pad=[6, 6, 6, 6], text_align='center')]}

    def rowbody():
        return [{'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'cross': 'center', 'children': [
            label('>', **{'if': 'r.i == $sel', 'color': GOLD, 'w': 8}), label(' ', **{'if': 'r.i != $sel', 'w': 8}),
            {'type': 'image', 'pokemon': '{r.icon}', 'w': 22, 'h': 22},
            {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [
                label('{r.name}', color=INK),
                {'type': 'row', 'gap': 8, 'children': [label('{r.rank}', color=MU), label('{r.tag}', **{'if': 'r.tag', 'color': '{r.tagColor}'})]}]},
            label('{r.value}', color=ACC, scale=1.2)]}]
    row = {'type': 'button', 'id': 'arow', 'w': 'fill', 'h': 38, 'bg': ROW, 'border': LN, 'hover': HOV, 'pad': [4, 8, 4, 8], 'set': {'sel': 'r.i'},
           'repeat': {'for': 'e.rows', 'as': 'r', 'limit': 12}, 'children': rowbody()}
    row_on = row
    right = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [0, 0, 8, 0], 'gap': 6, 'children': [
        {'type': 'row', 'w': 'fill', 'bg': HEAD, 'border': LN, 'pad': [6, 10, 6, 10], 'children': [label('Affixes', color=MU), sp(w='fill'), label('{e.count}', color=MU)]},
        {'type': 'scroll', 'id': 'arows', 'w': 'fill', 'h': 'fill', 'pad': [0, 8, 0, 8], 'children': [{'type': 'column', 'w': 'fill', 'gap': 4, 'children': [
            row, label('{e.emptyText}', **{'if': '!e.hasRows', 'color': MU, 'wrap': True, 'w': 'fill', 'pad': [8, 8, 8, 8]})]}]},
        label('{e.reso}', **{'if': 'e.hasReso', 'color': ACC, 'w': 'fill', 'pad': [0, 10, 0, 10], 'wrap': True}),
        label('{e.rows[$sel].cond}', **{'if': 'e.hasRows', 'color': INK, 'wrap': True, 'w': 'fill', 'h': 40, 'bg': HEAD, 'border': LN, 'pad': [6, 10, 6, 10]}),
        label('Click a modifier to see what it does.', **{'if': '!e.hasRows', 'color': MU, 'w': 'fill', 'pad': [0, 10, 0, 10]})]}
    foot = {'type': 'row', 'w': 'fill', 'pad': [8, 14, 8, 14], 'gap': 8, 'cross': 'center', 'bg': HEAD, 'border': LN, 'children': [
        btn('<', id='aprev', w=30, **{'if': 'vm.multi', 'set': {'ix': 'e.pi', 'sel': '0'}}),
        btn('>', id='anext', w=30, **{'if': 'vm.multi', 'set': {'ix': 'e.ni', 'sel': '0'}}),
        label('{vm.info}', color=MU, w='fill'),
        primary('Scout', id='scout', w=80, enabled='e.scoutOk', args={'encounterId': 'str:64', 'subjectId': 'str:64'},
                argv={'encounterId': 'vm.encounterId', 'subjectId': 'e.subjectId'}, msg={'channel': 'ascension_inspect_use', 'encounterId': '{encounterId}', 'subjectId': '{subjectId}'}, **{'if': 'vm.scout'}),
        primary('{e.sigText}', id='sigil', w=190, enabled='e.sigOk', **{'if': 'e.bare && !$arm', 'set': {'arm': 'true'}}),
        primary('Click again to spend 1 Sigil', id='sigil2', w=190, args={'pokemonId': 'str:64'},
                argv={'pokemonId': 'e.pokemonId'}, msg={'channel': 'ascension_inspect_sigil', 'pokemonId': '{pokemonId}'}, **{'if': 'e.bare && $arm', 'set': {'arm': 'false'}}),
        primary('{e.upText}', id='upgrade', w=110, enabled='e.upOk', args={'pokemonId': 'str:64'}, argv={'pokemonId': 'e.pokemonId'},
                msg={'channel': 'ascension_craft_open', 'pokemonId': '{pokemonId}'}, **{'if': 'e.canUp'}),
        btn('Close', w=70, close=True)]}
    main = {'type': 'row', 'w': 'fill', 'h': 'fill', 'pad': [12, 14, 6, 14], 'gap': 12, 'children': [left, right]}
    entry = {'type': 'column', 'w': 'fill', 'h': 'fill', 'repeat': {'for': 'vm.entries', 'as': 'e', 'limit': 24, 'if': 'e.i == $ix'}, 'children': [
        main, label('{e.bottom}', color=MU, w='fill', pad=[2, 16, 6, 16]), foot]}
    body = [{'type': 'row', 'w': 'fill', 'pad': [8, 14, 8, 14], 'gap': 10, 'cross': 'center', 'bg': HEAD, 'border': LN, 'children': [
        label('{vm.title}', scale=1.3, color=ACC), sp(w='fill'), btn('X', w=26, close=True)]},
        entry]
    return page('ascensionlib_inspect', 'ascension_inspect_ready', 'Inspect', 900, 580, body, state={'ix': 0, 'sel': 0, 'arm': False})


# ---------------------------------------------------------------- craft ("Modifiers"): tabs, affix list, detail + review, wallet

def vmsg(action, **extra):
    d = {'channel': 'snobble_view', 'action': action}
    d.update(extra)
    return d


def craft():
    top = {'type': 'row', 'w': 'fill', 'pad': [6, 14, 6, 14], 'gap': 14, 'cross': 'center', 'bg': PANEL, 'border': LN, 'children': [
        {'type': 'image', 'pokemon': '{vm.sprite}', 'w': 80, 'h': 52},
        {'type': 'column', 'gap': 2, 'children': [
            label('{vm.name}', scale=1.4, color=INK),
            {'type': 'row', 'gap': 8, 'cross': 'center', 'children': [label('{vm.lvl}', color=MU), label('{vm.grade}', color='{vm.gradeColor}', bg='{vm.gradeBg}', pad=[0, 6, 0, 6])]}]},
        sp(w='fill'),
        {'type': 'column', 'cross': 'end', 'children': [label('{vm.pend}', scale=2.2, color=ACC), label('{vm.pendLabel}', color=MU)]}]}
    tab = btn('{t.label}', id='tab', w=84, h=24, bg='{t.bg}', border='{t.border}', color='{t.color}', args={'k': 'str:16'}, argv={'k': 't.m'}, msg=vmsg('mode', k='{k}'),
              repeat={'for': 'vm.tabs', 'as': 't', 'limit': 8}, enabled='vm.browse')
    tabs = {'type': 'row', 'w': 'fill', 'gap': 3, 'children': [tab]}
    banner = label('{vm.banner}', **{'if': 'vm.hasBanner', 'color': '{vm.bannerColor}', 'bg': ROW, 'border': LN, 'w': 'fill', 'wrap': True, 'pad': [4, 10, 4, 10]})

    def rowbody():
        return [{'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'cross': 'center', 'children': [
            {'type': 'image', 'pokemon': '{r.icon}', 'w': 22, 'h': 22},
            {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [
                label('{r.name}', color='{r.nameColor}', wrap=True, w='fill'),
                label('{r.sub}', **{'if': 'r.sub', 'color': MU})]},
            label('{r.value}', color=ACC)]}]
    rowbtn = {'type': 'button', 'id': 'lrow', 'w': 'fill', 'h': 40, 'bg': '{r.bg}', 'border': '{r.border}', 'hover': HOV, 'pad': [4, 8, 4, 8],
              'args': {'k': 'str:24'}, 'argv': {'k': 'r.k'}, 'msg': vmsg('sel', k='{k}'), 'enabled': 'vm.browse', 'if': 'r.clickable', 'children': rowbody()}
    rowplain = {'type': 'row', 'w': 'fill', 'h': 40, 'bg': ROW, 'border': LN, 'pad': [4, 8, 4, 8], 'if': '!r.clickable', 'children': rowbody()}
    listbox = {'type': 'scroll', 'id': 'list', 'w': 250, 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [6, 6, 6, 6], 'children': [{'type': 'column', 'w': 'fill', 'gap': 3, 'children': [
        {'type': 'column', 'w': 'fill', 'repeat': {'for': 'vm.rows', 'as': 'r', 'limit': 24}, 'children': [rowbtn, rowplain]}]}]}

    def box(children, cond=None, **kw):
        d = {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': children}
        d.update(kw)
        return d
    vbox_l = lambda a, b, c: {'type': 'column', 'w': 'fill', 'bg': ROW, 'border': LN, 'pad': [6, 8, 6, 8], 'gap': 1, 'cross': 'center', 'children': [label(a, color=MU), label(b, scale=1.5, color=ACC), label(c, color=MU)]}
    vbox_r = lambda a, b, c: {'type': 'column', 'w': 'fill', 'bg': ROW, 'border': ACC, 'pad': [6, 8, 6, 8], 'gap': 1, 'cross': 'center', 'children': [label(a, color=MU), label(b, scale=1.5, color=ACC), label(c, color=MU)]}
    line = {'type': 'column', 'w': 'fill', 'repeat': {'for': 'vm.dl', 'as': 'd', 'limit': 40}, 'gap': 2, 'children': [
        label('{d.text}', **{'if': "d.t == 'text'", 'color': INK, 'wrap': True, 'w': 'fill'}),
        label('{d.text}', **{'if': "d.t == 'muted'", 'color': MU, 'wrap': True, 'w': 'fill'}),
        label('{d.text}', **{'if': "d.t == 'bad'", 'color': WARN, 'wrap': True, 'w': 'fill'}),
        label('{d.text}', **{'if': "d.t == 'gold'", 'color': GOLD, 'wrap': True, 'w': 'fill'}),
        label('{d.text}', **{'if': "d.t == 'big'", 'color': INK, 'scale': 1.3, 'wrap': True, 'w': 'fill'}),
        {'type': 'row', 'if': "d.t == 'pair'", 'w': 'fill', 'gap': 8, 'cross': 'center', 'pad': [6, 0, 6, 0], 'children': [
            vbox_l('{d.l1}', '{d.l2}', '{d.l3}'), label('>', color=MU), vbox_r('{d.r1}', '{d.r2}', '{d.r3}')]},
        {'type': 'bar', 'if': "d.t == 'rank'", 'w': 'fill', 'h': 8, 'value': 'd.v', 'max': '5', 'fill': ACC, 'fill_bg': WELL, 'radius': 1},
        {'type': 'bar', 'if': "d.t == 'bar'", 'w': 'fill', 'h': 8, 'value': 'd.v', 'max': '100', 'fill': ACC, 'fill_bg': WELL, 'radius': 1}]}
    extra = {'type': 'row', 'w': 'fill', 'gap': 10, 'cross': 'center', 'border': LN, 'pad': [6, 0, 0, 0], 'repeat': {'for': 'vm.extras', 'as': 'x', 'limit': 4}, 'children': [
        label('{x.note}', color=MU, w='fill', wrap=True),
        btn('{x.text}', id='extra', w=170, enabled='x.enabled', args={'k': 'str:16'}, argv={'k': 'x.id'}, msg=vmsg('{k}'))]}
    costrow = {'type': 'row', 'w': 'fill', 'gap': 10, 'cross': 'center', 'pad': [8, 0, 0, 0], 'children': [
        {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [label('Cost', color=MU), label('{vm.cost}', color='{vm.costColor}', wrap=True, w='fill')]},
        primary('Review', id='review', w=90, enabled='vm.canReview', msg=vmsg('review'), args={})]}
    detail = {'type': 'column', 'if': '!vm.dlgOn', 'w': 'fill', 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 10, 8, 10], 'gap': 4, 'children': [
        label('{vm.detailTitle}', color=ACC), {'type': 'scroll', 'id': 'detail', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 4, 'children': [line]}]}, extra, costrow]}
    dlg = {'type': 'column', 'if': 'vm.dlgOn', 'w': 'fill', 'h': 'fill', 'bg': PANEL, 'border': ACC, 'pad': [14, 16, 14, 16], 'gap': 10, 'children': [
        label('{vm.dlg.title}', scale=1.4, color=ACC),
        label('{vm.dlg.change}', color=INK, wrap=True, w='fill'),
        label('{vm.dlg.cost}', color=GOLD, wrap=True, w='fill'),
        label('{vm.dlg.warn}', color=MU, wrap=True, w='fill'),
        sp(h='fill'),
        {'type': 'row', 'w': 'fill', 'gap': 10, 'children': [sp(w='fill'), btn('Cancel', id='cancel', w=100, msg=vmsg('cancel'), args={}),
                                                              primary('Confirm', id='confirm', w=120, msg=vmsg('confirm'), args={})]}]}
    work = {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 6, 'children': [
        tabs, banner, {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'children': [listbox, detail, dlg]}]}
    coin = {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'repeat': {'for': 'vm.wallet', 'as': 'c', 'limit': 12}, 'children': [
        {'type': 'image', 'pokemon': '{c.icon}', 'w': 22, 'h': 22}, label('{c.n}', color='{c.color}'), sp(w='fill'), label('{c.name}', color=MU)]}
    wallet = {'type': 'scroll', 'id': 'wal', 'w': 170, 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 8, 8, 8], 'children': [{'type': 'column', 'w': 'fill', 'gap': 4, 'children': [label('Wallet', color=MU), coin]}]}
    foot = {'type': 'row', 'w': 'fill', 'pad': [8, 14, 8, 14], 'gap': 8, 'cross': 'center', 'bg': HEAD, 'border': LN, 'children': [
        label('{vm.next}', color=MU, w='fill'), btn('Wallet', id='bank', w=80, enabled='vm.browse', msg=vmsg('wallet'), args={}),
        btn('Fuse', id='fuse', w=70, enabled='vm.browse', msg=vmsg('fuse'), args={}, **{'if': 'vm.showFuse'}), btn('Decide later', id='later', w=110, close=True)]}
    body = [head('MODIFIERS'), top,
            {'type': 'row', 'w': 'fill', 'h': 'fill', 'pad': [8, 14, 8, 14], 'gap': 12, 'children': [work, wallet]}, foot]
    # the page talks to AscensionLib on its own channel; its first message wakes AscensionLib's craftView
    return page('ascensionlib_craft', 'ascension_craft_ready', 'Modifiers', 980, 620, body)


# ---------------------------------------------------------------- fusion: host, result, donor + picker

def fusion():
    def chamber(key, title, button):
        pre = 'vm.%s.' % key
        return {'type': 'column', 'w': 210, 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 10, 8, 10], 'gap': 4, 'cross': 'center', 'children': [
            label(title, color=MU, w='fill'),
            {'type': 'column', 'w': 'fill', 'h': 130, 'bg': WELL, 'border': LN, 'cross': 'center', 'children': [
                {'type': 'image', 'pokemon': '{%ssprite}' % pre, 'w': 110, 'h': 90},
                label('empty', **{'if': '!' + pre + 'has', 'color': MU})]},
            label('{%sname}' % pre, color=INK, scale=1.1, wrap=True, w='fill', text_align='center'),
            label('{%srarity}' % pre, color='{%scolor}' % pre),
            label('{%sunique}' % pre, color=MU, wrap=True, w='fill', text_align='center'),
            sp(h='fill'), button]}
    host = chamber('host', 'Host (kept)', btn('Change host', id='change_host', w='fill', enabled='vm.canChangeHost', msg=vmsg('pickHost'), args={}))
    donor = chamber('donor', 'Donor (consumed)', primary('{vm.donorText}', id='change_donor', w='fill', enabled='vm.canChangeDonor', msg=vmsg('pickDonor'), args={}))
    dl = {'type': 'column', 'w': 'fill', 'repeat': {'for': 'vm.dl', 'as': 'd', 'limit': 24}, 'gap': 2, 'children': [
        label('{d.text}', **{'if': "d.t == 'text'", 'color': INK, 'wrap': True, 'w': 'fill'}),
        label('{d.text}', **{'if': "d.t == 'muted'", 'color': MU, 'wrap': True, 'w': 'fill'}),
        label('{d.text}', **{'if': "d.t == 'bad'", 'color': WARN, 'wrap': True, 'w': 'fill'}),
        label('{d.text}', **{'if': "d.t == 'gold'", 'color': GOLD, 'wrap': True, 'w': 'fill'}),
        label('{d.text}', **{'if': "d.t == 'big'", 'color': ACC, 'scale': 1.4, 'wrap': True, 'w': 'fill'})]}
    middle = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 10, 8, 10], 'gap': 6, 'children': [
        label('Result', color=MU), {'type': 'scroll', 'id': 'result', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 4, 'children': [dl]}]},
        {'type': 'row', 'w': 'fill', 'children': [sp(w='fill'), btn('Swap', id='swap', w=90, enabled='vm.canSwap', msg=vmsg('swap'), args={}), sp(w='fill')]}]}
    main = {'type': 'row', 'if': '!vm.pickOpen', 'w': 'fill', 'h': 'fill', 'pad': [12, 14, 12, 14], 'gap': 12, 'children': [host, middle, donor]}
    tile = btn('{t.name}', id='tile', w='fill', h=92, enabled='t.ok', border='{t.border}', bg=ROW, pad=[4, 4, 4, 4], args={'k': 'str:64'}, argv={'k': 't.id'}, msg=vmsg('choose', k='{k}'),
               repeat={'for': 'tr.cells', 'as': 't', 'limit': 4})
    tile['text'] = ''
    tile['children'] = [{'type': 'column', 'w': 'fill', 'h': 'fill', 'cross': 'center', 'gap': 1, 'children': [
        {'type': 'image', 'pokemon': '{t.sprite}', 'w': 56, 'h': 44}, label('{t.name}', color=INK), label('{t.sub}', color='{t.color}', wrap=True, w='fill', text_align='center')]}]
    picker = {'type': 'column', 'if': 'vm.pickOpen', 'w': 'fill', 'h': 'fill', 'pad': [12, 14, 12, 14], 'gap': 8, 'children': [
        label('{vm.pickTitle}', color=ACC, scale=1.2),
        {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [
            btn('Party', id='tab_party', w=80, border='{vm.tabParty}', msg=vmsg('tab', k='party'), args={}),
            btn('PC (first page)', id='tab_pc', w=120, border='{vm.tabPc}', msg=vmsg('tab', k='pc'), args={}),
            btn('{vm.sortText}', id='sort', w=110, msg=vmsg('sort'), args={}),
            {'type': 'input', 'id': 'search', 'bind': 'q', 'hint': 'Search name, species or Unique', 'w': 'fill', 'h': 20, 'max_len': 40, 'bg': '#021018', 'border': LN, 'radius': 3, 'color': INK,
             'debounce_ms': 400, 'args': {'q': 'str:40'}, 'argv': {'q': '$q'}, 'msg': vmsg('search', q='{q}')},
            btn('Back', id='pick_close', w=70, msg=vmsg('pickClose'), args={})]},
        {'type': 'scroll', 'id': 'tiles', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 6, 'children': [
            {'type': 'row', 'w': 'fill', 'gap': 6, 'repeat': {'for': 'vm.tiles', 'as': 'tr', 'limit': 8}, 'children': [tile]},
            label('{vm.pickNote}', **{'if': 'vm.hasPickNote', 'color': MU, 'wrap': True, 'w': 'fill'})]}]}]}
    foot = {'type': 'row', 'w': 'fill', 'pad': [8, 14, 8, 14], 'gap': 8, 'cross': 'center', 'bg': HEAD, 'border': LN, 'children': [
        label('{vm.msg}', color='{vm.msgColor}', w='fill', wrap=True),
        primary('{vm.fuseText}', id='fuse', w=150, enabled='vm.canFuse', msg=vmsg('fuse'), args={}, **{'if': '!vm.pickOpen'}),
        btn('Close', id='close_b', w=80, close=True, **{'if': 'vm.cancelCloses'}),
        btn('Cancel', id='cancel', w=80, msg=vmsg('cancel'), args={}, **{'if': '!vm.cancelCloses'})]}
    body = [head('FUSION'), main, picker, foot]
    return page('ascensionlib_fusion', 'ascension_fusion_ready', 'Fusion', 900, 580, body)


# ---------------------------------------------------------------- capture reveal card (centered, over the world)

def capreveal():
    trait = {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'bg': ROW, 'border': LN, 'pad': [4, 8, 4, 8], 'repeat': {'for': 'vm.traits', 'as': 'tr', 'limit': 12}, 'children': [
        {'type': 'image', 'pokemon': '{tr.icon}', 'w': 22, 'h': 22},
        {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [label('{tr.name}', color=INK), label('{tr.sub}', color=MU)]},
        label('{tr.value}', color=ACC, scale=1.2)]}
    pip = {'type': 'bar', 'w': 'fill', 'h': 6, 'value': 'pp.v', 'max': '1', 'fill': '{pp.c}', 'fill_bg': '#16303F', 'radius': 1, 'repeat': {'for': 'vm.pips', 'as': 'pp', 'limit': 6}}
    card = {'type': 'column', 'if': 'vm.name', 'w': 'fill', 'h': 'fill', 'bg': PANEL, 'border': '{vm.color}', 'radius': 3, 'pad': [10, 14, 10, 14], 'gap': 6, 'cross': 'center',
            'anim': 'pop', 'anim_ms': 500, 'children': [
        {'type': 'row', 'w': 'fill', 'cross': 'center', 'bg': HEAD, 'pad': [4, 8, 4, 8], 'children': [label('CAPTURE REGISTERED', color=ACC), sp(w='fill'), label('{vm.left}', color=MU)]},
        label('{vm.name}', scale=1.8, color=INK, anim='fade', anim_ms=700),
        {'type': 'column', 'w': 'fill', 'h': 120, 'bg': WELL, 'border': LN, 'cross': 'center', 'children': [
            label('NEW PARTNER', color=MU, w='fill', text_align='center'),
            {'type': 'image', 'pokemon': '{vm.sprite}', 'w': 120, 'h': 96, 'anim': 'pulse', 'anim_ms': 2400}]},
        label('{vm.rarity}', color='{vm.color}', bg='{vm.bg}', w='fill', text_align='center', pad=[4, 4, 4, 4], scale=1.2),
        label('{vm.traitsLabel}', color=MU, w='fill'),
        {'type': 'scroll', 'id': 'traits', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 4, 'children': [trait]}]},
        label('Every great partner starts with potential.', color=INK, wrap=True, w='fill', text_align='center'),
        label('{vm.sub}', color=MU, wrap=True, w='fill', text_align='center'),
        {'type': 'row', 'w': 'fill', 'gap': 3, 'children': [pip]},
        {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'children': [
            primary('Explore Ascension >', id='explore', w='fill', msg=vmsg('explore'), args={}),
            btn('Continue', id='continue', w=90, msg=vmsg('continue'), args={}),
            btn('Disable', id='disable', w=70, msg=vmsg('disable'), args={})]}]}
    tick = {'type': 'spacer', 'id': 'tick', 'w': 0, 'h': 0, 'every_ms': 1000, 'args': {}, 'msg': vmsg('tick')}
    d = {'v': 2, 'id': 'ascensionlib_capture', 'channel': 'ascension_capture_ready', 'hello': 'ready', 'bye': False, 'title': 'Capture', 'width': 460, 'height': 620,
         'root': {'type': 'column', 'w': 'fill', 'h': 'fill', 'children': [card, tick]}}
    return d


# ---------------------------------------------------------------- tower completion reveal

def completion():
    seg = {'type': 'column', 'w': 'fill', 'gap': 2, 'cross': 'center', 'repeat': {'for': 'vm.tiers', 'as': 'tt', 'limit': 6}, 'children': [
        {'type': 'bar', 'w': 'fill', 'h': 8, 'value': 'tt.v', 'max': '1', 'fill': '{tt.color}', 'fill_bg': WELL, 'radius': 1}, label('{tt.text}', color=MU)]}
    pl = {'type': 'row', 'w': 'fill', 'gap': 10, 'cross': 'center', 'bg': ROW, 'border': LN, 'pad': [5, 8, 5, 8], 'anim': 'slide', 'anim_ms': 250,
          'repeat': {'for': 'vm.pile', 'as': 'l', 'limit': 12}, 'children': [
              {'type': 'image', 'pokemon': '{l.icon}', 'w': 32, 'h': 32, 'bg': WELL, 'border': LN},
              label('{l.name}', color='{l.color}', w='fill'), label('{l.amount}', color=ACC, scale=1.2)]}
    cr = {'type': 'row', 'w': 'fill', 'h': 64, 'gap': 12, 'cross': 'center', 'bg': ROW, 'border': '{c.border}', 'pad': [8, 10, 8, 10], 'anim': 'pop', 'anim_ms': 350,
          'repeat': {'for': 'vm.crowns', 'as': 'c', 'limit': 6}, 'children': [
              {'type': 'image', 'pokemon': '{c.icon}', 'w': 48, 'h': 48, 'bg': WELL, 'border': LN},
              label('*', **{'if': 'c.star', 'color': GOLD, 'scale': 2.0, 'w': 48, 'text_align': 'center'}),
              label('?', **{'if': 'c.rolling', 'color': GOLD, 'scale': 2.0, 'w': 48, 'text_align': 'center', 'anim': 'pulse', 'anim_ms': 500}),
              {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [label('{c.name}', color='{c.color}', scale=1.2), label('{c.hint}', color=MU)]}]}
    main = {'type': 'column', 'w': 'fill', 'h': 'fill', 'pad': [10, 16, 10, 16], 'gap': 8, 'children': [
        label('{vm.banner}', color='{vm.tierColor}', bg='{vm.tierBg}', border='{vm.tierColor}', scale=2.0, w='fill', text_align='center', pad=[12, 8, 12, 8], anim='pop', anim_ms=500),
        label('{vm.sub}', color=MU, w='fill', text_align='center'),
        {'type': 'row', 'w': 'fill', 'gap': 4, 'children': [seg]},
        {'type': 'scroll', 'id': 'reveal', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 5, 'children': [
            pl, label('THE CROWN', **{'if': 'vm.crownLabel', 'color': GOLD, 'w': 'fill', 'text_align': 'center', 'pad': [6, 0, 2, 0]}), cr,
            label('{vm.report}', color=MU, wrap=True, w='fill', text_align='center', pad=[6, 0, 0, 0])]}]}]}
    foot = {'type': 'row', 'w': 'fill', 'pad': [8, 14, 8, 14], 'gap': 8, 'cross': 'center', 'bg': HEAD, 'border': LN, 'children': [
        label('{vm.msg}', color=MU, w='fill'),
        btn('Share', id='share', w=80, enabled='vm.canShare', msg=vmsg('share'), args={}),
        btn('Replay', id='replay', w=80, msg=vmsg('replay'), args={}),
        primary('Collect', id='finish', w=100, msg=vmsg('finish'), args={})]}
    tick = {'type': 'spacer', 'id': 'tick', 'w': 0, 'h': 0, 'every_ms': 500, 'args': {}, 'msg': vmsg('tick')}
    body = [head('TOWER COMPLETION'), main, foot, tick]
    d = {'v': 2, 'id': 'ascensionlib_completion', 'channel': 'ascension_completion_ready', 'hello': 'ready', 'bye': False, 'title': 'Tower completion', 'width': 720, 'height': 560,
         'root': {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': SCR, 'border': LN, 'radius': 2, 'children': body}}
    return d


# ---------------------------------------------------------------- battle HUD (display only, drawn over the world)

def hud():
    badge = label('{b.text}', color='{b.color}', bg='{b.bg}', border='{b.border}', pad=[3, 9, 3, 9], anim='slide', anim_ms=250, repeat={'for': 'vm.badges', 'as': 'b', 'limit': 12})
    row = lambda src, var: {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'bg': ROW, 'border': LN, 'pad': [4, 8, 4, 8], 'repeat': {'for': src, 'as': var, 'limit': 8}, 'children': [
        label('{%s.name}' % var, color=INK, w='fill'), label('{%s.rarity}' % var, color='{%s.color}' % var, bg='{%s.bg}' % var, pad=[0, 5, 0, 5])]}
    panel = {'type': 'column', 'if': 'vm.panel', 'w': 270, 'bg': PANEL, 'border': LN, 'gap': 2, 'children': [
        {'type': 'row', 'w': 'fill', 'bg': HEAD, 'pad': [5, 10, 5, 10], 'children': [label('Ascension', color=INK)]},
        {'type': 'row', 'w': 'fill', 'pad': [4, 10, 2, 10], 'children': [label('OPPONENTS', color=MU), sp(w='fill'), label('{vm.scouters}', color=MU)]},
        row('vm.opp', 'o'),
        label('YOUR TEAM', color=MU, pad=[4, 10, 2, 10]),
        row('vm.team', 'm'),
        label('Inspect with /ascend scout or the AscensionDex.', color=MU, wrap=True, w='fill', pad=[4, 10, 6, 10])]}
    tick = {'type': 'spacer', 'id': 'tick', 'w': 0, 'h': 0, 'every_ms': 500, 'args': {}, 'msg': vmsg('tick')}
    main = {'type': 'row', 'w': 'fill', 'h': 'fill', 'pad': [90, 0, 0, 0], 'gap': 10, 'children': [
        {'type': 'column', 'w': 330, 'gap': 5, 'children': [badge]}, sp(w='fill'), panel]}
    return {'v': 2, 'id': 'ascensionlib_hud', 'channel': 'ascension_hud_ready', 'hello': 'ready', 'bye': False, 'title': 'Ascension HUD', 'width': 1000, 'height': 700,
            'hud_anchor': 'tl', 'hud_x': 0, 'hud_y': 0, 'root': {'type': 'column', 'w': 'fill', 'h': 'fill', 'pad': [0, 12, 0, 12], 'children': [main, tick]}}


for name, fn in (('ascensionlib_wallet', wallet), ('ascensionlib_guide', guide), ('ascensionlib_party', party), ('ascensionlib_inspect', inspect), ('ascensionlib_craft', craft), ('ascensionlib_fusion', fusion), ('ascensionlib_capture', capreveal), ('ascensionlib_completion', completion), ('ascensionlib_hud', hud)):
    write(name, fn())
