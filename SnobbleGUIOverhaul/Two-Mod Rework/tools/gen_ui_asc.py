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


# ---------------------------------------------------------------- the original tablet (ascension.css .frame + ui.js), applied to every tablet page
# 1040 x 600 frame, scaled like ui.js A.fit (min(w * .82 / 1040, h * .82 / 600) x the player's size %), see-through around it; the rarity bezel
# (border-image 8 / 18px, sprite ascensionlib/bezel_*), the scanline overlay, Monocraft 12px/1.4, the pixel keys (#0f3347, crimson primary).
FRAME_W, FRAME_H, BZ = 1040, 600, 18


def head(title, extras=()):
    deco = {'type': 'column', 'w': 90, 'h': 14, 'children': [{'type': 'column', 'x': -14, 'y': -13, 'w': 120, 'h': 40, 'bg': 'tex:ascensionlib/headdeco.png', 'children': []}]}
    xbtn = lambda text, **kw: dict({'type': 'button', 'text': text, 'font': 'monocraft', 'size': 12, 'lh': 16.8, 'shadow': False, 'color': INK, 'hover_color': '#ffffff',
                                    'bg': '#0d2230', 'hover': '#0d2230', 'border': LN, 'hover_border': ACC, 'bw': 2, 'pad': [1, 8, 1, 8], 'shrink': True, 'h': 22}, **kw)
    calm = [xbtn('Calm', id='calm_off', set={'calm': "'1'"}, args={'calm': 'bool'}, argv={'calm': "'true'"}, msg={'channel': 'ascension_ui_set', 'calm': '{calm}'},
                 **{'if': "$calm != '1'"}),
            xbtn('Calm', id='calm_on', color='#ffffff', border=ACC, set={'calm': "''"}, args={'calm': 'bool'}, argv={'calm': "'false'"},
                 msg={'channel': 'ascension_ui_set', 'calm': '{calm}'}, **{'if': "$calm == '1'"})]
    size = [xbtn('−', id='ui_minus', set={'zoompct': '$zoompct - 10'}, args={'percent': 'int:60:160'}, argv={'percent': '$zoompct - 10'},
                 msg={'channel': 'ascension_ui_set', 'percent': '{percent}'}),
            {'type': 'label', 'text': '{$zoompct}%', 'font': 'monocraft', 'size': 11, 'lh': 15.4, 'color': MU, 'shadow': False, 'min_w': 44, 'text_align': 'center'},
            xbtn('+', id='ui_plus', set={'zoompct': '$zoompct + 10'}, args={'percent': 'int:60:160'}, argv={'percent': '$zoompct + 10'},
                 msg={'channel': 'ascension_ui_set', 'percent': '{percent}'})]
    return {'type': 'row', 'w': 'fill', 'pad': [8, 14, 8, 14], 'gap': 10, 'cross': 'center', 'bg': HEAD, 'bsides': 'b', 'border': LN, 'bw': 2, 'children': [
        deco, {'type': 'label', 'text': title, 'font': 'monocraft', 'size': 12, 'lh': 16.8, 'ls': 1.2, 'color': ACC, 'shadow': False}, sp(w='fill')]
        + list(extras) + calm + [{'type': 'row', 'gap': 4, 'cross': 'center', 'children': size}, xbtn('X', id='x_close', close=True)]}


def restyle(n):
    """the original's font and keys on a v1 node tree"""
    if isinstance(n, list):
        return [restyle(k) for k in n]
    if not isinstance(n, dict):
        return n
    n = {k: restyle(v) for k, v in n.items()}
    t = n.get('type')
    if t in ('label', 'button') and 'text' in n and 'font' not in n:
        size = round(12 * n.pop('scale', 1), 1)
        n.update({'font': 'monocraft', 'size': size, 'lh': round(size * 1.4, 1), 'shadow': False})
    if t == 'button' and 'children' not in n or (t == 'button' and n.get('bg') in (PANEL, ACC)):
        if n.get('bg') == ACC:                      # button.primary
            n.update({'bg': '#8e1426', 'hover': '#a51a30', 'border': '#eef2f7', 'color': '#ffffff', 'hover_color': '#ffffff', 'disabled': '#2a1a22'})
        elif n.get('bg') == PANEL:                  # button
            n.update({'bg': '#0f3347', 'hover': '#0f3347', 'border': LN, 'hover_border': ACC, 'hover_color': '#ffffff', 'disabled': '#0a1b27'})
        n['bw'] = 2
        n.pop('radius', None)
    if n.get('bg') == PANEL and n.get('border') == LN and t != 'button':
        n['bw'] = 2
        n.pop('radius', None)
    if n.get('bg') == HEAD and n.get('border') == LN and not n.get('bsides'):
        n.update({'bsides': 't', 'bw': 2})
    return n


def tablet(d, fw=1040, fh=600, bezel=None, modal=None):
    """wraps a v1 page in the tablet frame (fw x fh: the page's own .frame size)"""
    old = restyle(d['root'])
    old.update({'x': BZ, 'y': BZ, 'w': fw - 2 * BZ, 'h': fh - 2 * BZ, 'bg': SCR})
    for k in ('border', 'radius'):
        old.pop(k, None)
    over = [{'type': 'column', 'x': BZ, 'y': BZ, 'w': fw - 2 * BZ, 'h': fh - 2 * BZ, 'bg': 'tex:ascensionlib/scan.png', 'children': []},
            dict({'type': 'column', 'x': 0, 'y': 0, 'w': fw, 'h': fh, 'bg': 'sprite:ascensionlib/bezel_none', 'children': []}, **(bezel or {}))]
    d.update({'width': fw, 'height': fh, 'fit': [fw / 0.82, fh / 0.82], 'nodim': True,
              'root': {'type': 'column', 'w': fw, 'h': fh, 'children': [old] + over + ([modal(fw, fh)] if modal else [])}})
    st = dict(d.get('state') or {})
    st.setdefault('zoompct', 100)
    st.setdefault('calm', '')
    d['state'] = st
    return d


BEZEL = {'ascensionlib_craft': {'bg': '{vm.bezel}'},
         'ascensionlib_inspect': {'bg': '{e.bezel}', 'repeat': {'for': 'vm.entries', 'as': 'e', 'limit': 24, 'if': 'e.i == $ix'}}}
def craft_dialog(fw, fh):
    """craft.html #scrim .dialog: the confirmation over a dark scrim (the original's scrim covers the whole window; here the tablet)"""
    t = lambda text, color, **kw: dict({'type': 'label', 'text': text, 'font': 'monocraft', 'size': 12, 'lh': 16.8, 'color': color, 'shadow': False, 'w': 'fill', 'wrap': True}, **kw)
    key = lambda text, id_, prim, **kw: dict({'type': 'button', 'id': id_, 'text': text, 'font': 'monocraft', 'size': 12, 'lh': 16.8, 'shadow': False,
                                               'color': '#ffffff' if prim else INK, 'hover_color': '#ffffff', 'bg': '#8e1426' if prim else '#0f3347',
                                               'hover': '#a51a30' if prim else '#0f3347', 'border': '#eef2f7' if prim else LN, 'hover_border': '#eef2f7' if prim else ACC,
                                               'bw': 2, 'pad': [6, 14, 6, 14], 'shrink': True, 'args': {}}, **kw)
    dialog = {'type': 'column', 'w': 540, 'bg': PANEL, 'border': ACC, 'bw': 2, 'pad': [16, 16, 16, 16], 'children': [
        t('{vm.dlg.title}', ACC, ls=1.2, upper=True, pad=[0, 0, 8, 0]),
        t('{vm.dlg.change}', INK, pad=[0, 0, 8, 0]), t('{vm.dlg.cost}', GOLD, pad=[0, 0, 8, 0]), t('{vm.dlg.warn}', INK),
        {'type': 'row', 'w': 'fill', 'gap': 10, 'align': 'end', 'pad': [12, 0, 0, 0], 'children': [
            key('Cancel', 'cancel', False, msg=vmsg('cancel')), key('Confirm', 'confirm', True, msg=vmsg('confirm'))]}]}
    return {'type': 'column', 'if': 'vm.dlgOn', 'x': 0, 'y': 0, 'w': fw, 'h': fh, 'z': 120, 'screen_bg': '#02080c9e', 'align': 'center', 'cross': 'center', 'children': [dialog]}


def fusion_picker(fw, fh):
    """fusion.html #scrim .picker: 600 x 520 over the scrim (head, tabs, search, tile grid, reason line)"""
    t = lambda text, color, **kw: dict({'type': 'label', 'text': text, 'font': 'monocraft', 'size': 12, 'lh': 16.8, 'color': color, 'shadow': False}, **kw)
    key = lambda text, id_, **kw: dict({'type': 'button', 'id': id_, 'text': text, 'font': 'monocraft', 'size': 12, 'lh': 16.8, 'shadow': False, 'color': INK,
                                        'hover_color': '#ffffff', 'bg': '#0f3347', 'hover': '#0f3347', 'border': LN, 'hover_border': ACC, 'bw': 2,
                                        'pad': [6, 14, 6, 14], 'shrink': True, 'args': {}}, **kw)
    deco = {'type': 'column', 'w': 90, 'h': 14, 'children': [{'type': 'column', 'x': -14, 'y': -13, 'w': 120, 'h': 40, 'bg': 'tex:ascensionlib/headdeco.png', 'children': []}]}
    head_ = {'type': 'row', 'w': 'fill', 'pad': [8, 14, 8, 14], 'gap': 10, 'cross': 'center', 'bg': HEAD, 'bsides': 'b', 'border': LN, 'bw': 2, 'children': [
        deco, t('{vm.pickTitle}', ACC, ls=0.72), sp(w='fill'),
        key('\u00d7', 'pick_close', bg='#0d2230', hover='#0d2230', pad=[1, 8, 1, 8], msg=vmsg('pickClose'))]}
    tabs = {'type': 'row', 'w': 'fill', 'gap': 6, 'pad': [8, 12, 0, 12], 'children': [
        key('Party', 'tab_party', bg='{vm.tabPartyBg}', hover='{vm.tabPartyBg}', border='{vm.tabPartyEdge}', msg=vmsg('tab', k='party')),
        key('PC (first page)', 'tab_pc', bg='{vm.tabPcBg}', hover='{vm.tabPcBg}', border='{vm.tabPcEdge}', msg=vmsg('tab', k='pc')),
        key('{vm.sortText}', 'sort', msg=vmsg('sort'))]}
    search = {'type': 'row', 'w': 'fill', 'pad': [8, 12, 0, 12], 'children': [
        {'type': 'input', 'id': 'search', 'bind': 'q', 'hint': 'Search name, species or Unique', 'w': 'fill', 'h': 32, 'max_len': 40, 'font': 'monocraft', 'size': 12,
         'bg': HEAD, 'border': LN, 'bw': 2, 'pad': [0, 8, 0, 8], 'color': INK, 'hint_color': '#757575', 'debounce_ms': 400,
         'args': {'q': 'str:40'}, 'argv': {'q': '$q'}, 'msg': vmsg('search', q='{q}')}]}
    tile = {'type': 'button', 'id': 'tile', 'w': 'fill', 'min_h': 84, 'bg': ROW, 'border': LN, 'hover_border': ACC, 'bw': 2, 'btop': '5,{t.border}', 'pad': [9, 8, 8, 8],
            'enabled': 't.ok', 'opacity': 't.op', 'args': {'k': 'str:64'}, 'argv': {'k': 't.id'}, 'msg': vmsg('choose', k='{k}'),
            'repeat': {'for': 'vm.tileList', 'as': 't', 'limit': 40}, 'children': [{'type': 'column', 'w': 'fill', 'cross': 'center', 'gap': 1, 'children': [
                {'type': 'image', 'pokemon': '{t.sprite}', 'w': 56, 'h': 40, 'if': 't.sprite'},
                t('{t.name}', INK, w='fill', text_align='center', wrap=True),
                t('{t.sub}', '{t.color}', size=10, lh=14, w='fill', text_align='center', wrap=True)]}]}
    grid = {'type': 'scroll', 'id': 'tiles', 'w': 'fill', 'h': 'fill', 'pad': [10, 12, 10, 12], 'children': [
        {'type': 'grid', 'w': 'fill', 'min_col': 104, 'gap': 8, 'children': [tile]}]}
    reason = t('{vm.pickNote}', WARN, size=11, lh=15.4, w='fill', wrap=True, min_h=28, pad=[6, 12, 6, 12])
    picker = {'type': 'column', 'w': 600, 'h': 520, 'bg': PANEL, 'border': ACC, 'bw': 2, 'children': [head_, tabs, search, grid, reason]}
    return {'type': 'column', 'if': 'vm.pickOpen', 'x': 0, 'y': 0, 'w': fw, 'h': fh, 'z': 120, 'screen_bg': '#02080c9e', 'align': 'center', 'cross': 'center', 'children': [picker]}


MODAL = {'ascensionlib_craft': craft_dialog, 'ascensionlib_fusion': fusion_picker}
TABLET = {'ascensionlib_wallet': (760, 560), 'ascensionlib_guide': (960, 620), 'ascensionlib_party': (860, 600), 'ascensionlib_inspect': (1040, 600),
          'ascensionlib_craft': (1040, 600), 'ascensionlib_fusion': (1040, 600), 'ascensionlib_completion': (720, 560)}


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
    detail = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 10, 8, 10], 'gap': 4, 'children': [
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
        tabs, banner, {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'children': [listbox, detail]}]}
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
    main = {'type': 'row', 'w': 'fill', 'h': 'fill', 'pad': [12, 14, 12, 14], 'gap': 12, 'children': [host, middle, donor]}
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
    body = [head('FUSION'), main, foot]
    return page('ascensionlib_fusion', 'ascension_fusion_ready', 'Fusion', 900, 580, body)


# ---------------------------------------------------------------- capture reveal card (centered, over the world)
# Port of ascensionlib/capture.html + capture.css + capture-flip.js: a 440 px card (16 px rarity bezel) with the LCD (head, name, portrait with the rarity aura,
# rarity pill, traits, promise, rarity path, actions, foot), the Disable key, and the 1.8 s "Prism Rupture" entrance (the card turns from its back; bloom, shockwave, shards, sparks).
# Three card heights (1 / 2 / 3+ traits) are three variants, because the bezel and the back are baked at the card's size.
CAP_W = 440                      # .capture-card width: 16 px bezel + 408 px screen + 16 px bezel
CAP_AUR = ['#c1c5cf', '#1bd7e8', '#38ccef', '#af69ff', '#ffd437', '#ff3448']
CAP_RAR = 'sprite:ascensionlib/bezel_none'


def mono(text, size, color=INK, **kw):
    d = {'type': 'label', 'text': text, 'font': 'monocraft', 'size': size, 'lh': round(size * 1.4, 2), 'shadow': False, 'color': color}
    d.update(kw)
    return d


def cap_variant(hv):
    inner = {1: 545, 2: 596, 3: 615}[hv]               # screen height (without the 16 px bezel); see build_cap_art.py
    card_h = inner + 32
    trait = {'type': 'row', 'w': 'fill', 'gap': 10, 'cross': 'center', 'bg': PANEL, 'pad': [10, 10, 10, 10], 'repeat': {'for': 'vm.traits', 'as': 'tr', 'limit': 24}, 'children': [
        {'type': 'image', 'pokemon': '{tr.icon}', 'w': 30, 'h': 30},
        {'type': 'column', 'w': 'fill', 'gap': 3, 'children': [mono('{tr.name}', 13, INK, wrap=True, w='fill'), mono('{tr.sub}', 11, MU, w='fill')]},
        mono('{tr.value}', 22, INK)]}
    tcol = {'type': 'column', 'w': 'fill', 'gap': 1, 'bg': LN, 'children': [trait]}
    if hv == 3:
        traits = {'type': 'scroll', 'id': 'traits' + str(hv), 'w': 'fill', 'h': 120, 'thumb': '#1f6b86', 'children': [tcol]}
    else:
        traits = tcol
    tbox = {'type': 'column', 'w': 'fill', 'h': {1: 54, 2: 105, 3: 124}[hv], 'bg': PANEL, 'border': LN, 'bw': 2, 'pad': [0, 0, 0, 0], 'children': [traits]}
    # head: lamps (17 px outlined square, red, gold, green), the label, room for the Disable key
    outline, white = '#1b120d', '#eef2f7'
    lamp1 = {'type': 'column', 'w': 11, 'h': 11, 'bg': ACC, 'children': [
        {'type': 'column', 'x': -3, 'y': -3, 'w': 17, 'h': 17, 'bg': outline, 'pad': [1, 1, 1, 1], 'children': [
            {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': white, 'pad': [1, 1, 1, 1], 'children': [
                {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': outline, 'pad': [1, 1, 1, 1], 'children': [{'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': ACC, 'children': []}]}]}]}]}
    lamp = lambda col: {'type': 'column', 'w': 5, 'h': 5, 'bg': col, 'children': [{'type': 'column', 'x': -1, 'y': -1, 'w': 7, 'h': 7, 'bg': outline, 'pad': [1, 1, 1, 1],
                                                                                    'children': [{'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': col, 'children': []}]}]}
    head = {'type': 'row', 'w': 'fill', 'bg': HEAD, 'bsides': 'b', 'border': LN, 'bw': 2, 'pad': [11, 86, 11, 14], 'gap': 10, 'cross': 'center', 'children': [
        {'type': 'row', 'gap': 5, 'cross': 'center', 'children': [lamp1, {'type': 'spacer', 'w': 5}, lamp('#e0434e'), lamp(GOLD), lamp('#52c24a')]},
        sp(w='fill'), mono('CAPTURE REGISTERED', 11, ACC, ls=1)]}
    name = {'type': 'column', 'w': 'fill', 'pad': [10, 18, 9, 18], 'children': [mono('{vm.name}', 23, INK, w='fill', wrap=True, lh=29.9)]}
    pill = {'type': 'column', 'bg': SCR, 'pad': [2, 2, 2, 2], 'children': [
        mono('{vm.rarity}', 12, '{vm.color}', ls=2, bg='{vm.bg}', border='{vm.color}', bw=2, pad=[3, 17, 3, 17], text_align='center')]}
    portrait = {'type': 'column', 'w': 384, 'h': 164, 'bg': '{vm.portrait}', 'children': [
        mono('NEW PARTNER', 11, MU, x=27, y=12),
        {'type': 'image', 'pokemon': '{vm.sprite}', 'x': 72, 'y': -8, 'w': 240, 'h': 180, 'anim': 'pulse', 'anim_ms': 2400} if False else
        {'type': 'image', 'pokemon': '{vm.sprite}', 'x': 72, 'y': -8, 'w': 240, 'h': 180},
        {'type': 'row', 'x': 0, 'y': 151, 'w': 384, 'align': 'center', 'children': [pill]}]}
    quote = {'type': 'row', 'w': 'fill', 'align': 'center', 'children': [mono('Every great partner starts with ', 12, INK, lh=20.4), mono('potential.', 12, GOLD, lh=20.4)]}
    content = {'type': 'column', 'w': 'fill', 'pad': [14, 16, 12, 16], 'children': [
        {'type': 'row', 'w': 'fill', 'cross': 'center', 'pad': [0, 0, 6, 0], 'children': [mono('{vm.traitsLabel}', 11, MU, ls=.5), sp(w='fill'), mono('SCROLL ↓', 11, ACC, **{'if': 'vm.nTraits > 2'})]},
        tbox,
        {'type': 'column', 'w': 'fill', 'pad': [10, 0, 10, 0], 'cross': 'center', 'children': [quote, mono('{vm.sub}', 11, MU, w='fill', text_align='center', lh=17.6, pad=[4, 0, 0, 0])]},
        {'type': 'row', 'w': 'fill', 'gap': 5, 'pad': [0, 20, 0, 20], 'children': [{'type': 'column', 'w': 'fill', 'h': 5, 'bg': '{pp.c}', 'repeat': {'for': 'vm.pips', 'as': 'pp', 'limit': 6}, 'children': []}]},
        {'type': 'row', 'w': 'fill', 'pad': [5, 20, 10, 20], 'children': [mono('COMMON', 11, MU), sp(w='fill'), mono('MYTHIC', 11, '#ff5a6c')]},
        {'type': 'row', 'w': 'fill', 'gap': 8, 'children': [
            {'type': 'button', 'id': 'explore' + str(hv), 'text': 'Explore Ascension >', 'font': 'monocraft', 'size': 12, 'lh': 16.8, 'shadow': False, 'color': '#ffffff', 'hover_color': '#ffffff', 'bg': '#8e1426',
             'hover': '#a51a30', 'border': '#eef2f7', 'hover_border': '#eef2f7', 'bw': 2, 'w': 231, 'h': 44, 'align': 'center', 'cross': 'center', 'pad': [9, 12, 9, 12], 'msg': vmsg('explore'), 'args': {}},
            {'type': 'button', 'id': 'continue' + str(hv), 'text': 'Continue', 'font': 'monocraft', 'size': 12, 'lh': 16.8, 'shadow': False, 'color': INK, 'hover_color': '#ffffff', 'bg': '#0f3347',
             'hover': '#0f3347', 'border': LN, 'hover_border': ACC, 'bw': 2, 'w': 'fill', 'h': 44, 'align': 'center', 'cross': 'center', 'pad': [9, 12, 9, 12], 'msg': vmsg('continue'), 'args': {}}]}]}
    foot = {'type': 'row', 'w': 'fill', 'bg': HEAD, 'bsides': 't', 'border': LN, 'bw': 2, 'pad': [8, 12, 8, 12], 'cross': 'center', 'children': [
        {'type': 'row', 'gap': 7, 'cross': 'center', 'children': [{'type': 'column', 'w': 18, 'h': 18, 'bg': 'tex:ascensionlib/ascension_dex.png', 'children': []}, mono('ASCENSION DEX', 11, MU)]},
        sp(w='fill'), mono('PROFILE SAVED', 11, MU)]}
    lcd = {'type': 'column', 'w': 'fill', 'children': [head, name, {'type': 'row', 'w': 'fill', 'pad': [0, 12, 0, 12], 'children': [portrait]},
                                                       {'type': 'column', 'w': 'fill', 'h': 13.8, 'children': []}, content, foot]}
    scan = {'type': 'column', 'x': 0, 'y': 0, 'w': 408, 'h': inner, 'bg': 'tex:ascensionlib/scan.png', 'opacity': 0.7, 'children': [], 'if': 'vm.anim'}
    disable = {'type': 'button', 'id': 'disable' + str(hv), 'text': 'Disable', 'font': 'monocraft', 'size': 11, 'lh': 15.4, 'shadow': False, 'color': INK, 'hover_color': '#ffffff', 'bg': '#0f3347',
               'hover': '#0f3347', 'border': LN, 'hover_border': ACC, 'bw': 2, 'x': 354, 'y': 21, 'w': 64, 'h': 30, 'align': 'center', 'cross': 'center', 'pad': [3, 3, 3, 3], 'msg': vmsg('disable'), 'args': {}}
    bezel = {'type': 'column', 'x': 0, 'y': 0, 'w': CAP_W, 'h': card_h, 'bg': '{vm.bezel}', 'children': []}
    front = {'type': 'column', 'x': 0, 'y': 0, 'w': CAP_W, 'h': card_h, 'bg': SCR, 'anim': 'capb', 'anim_key': 'vm.revealKey', 'children': [
        {'type': 'column', 'x': 16, 'y': 16, 'w': 408, 'h': inner, 'children': [lcd]},
        bezel, disable]}
    back = {'type': 'column', 'x': 0, 'y': 0, 'w': CAP_W, 'h': card_h, 'bg': SCR, 'anim': 'capa', 'anim_key': 'vm.revealKey', 'children': [
        {'type': 'column', 'x': 16, 'y': 16, 'w': 408, 'h': inner, 'bg': 'tex:ascensionlib/cap_back_%d.png' % hv, 'children': [
            {'type': 'column', 'x': 120, 'y': int(round(inner / 2 - 97.2 + 50 - 84)), 'w': 168, 'h': 168, 'bg': 'tex:ascensionlib/cap_crest.png', 'children': []},
            mono('ASCENSION', 25, INK, x=0, y=int(round(inner / 2 - 97.2 + 132)), w=408, text_align='center', ls=3),
            mono('A NEW BOND AWAITS', 11, MU, x=0, y=int(round(inner / 2 - 97.2 + 179)), w=408, text_align='center', ls=2)]},
        dict(bezel)]}
    card = {'type': 'column', 'if': 'vm.hv == %d' % hv, 'w': CAP_W, 'h': card_h, 'hover_set': {'hov': '1'}, 'children': [back, front]}
    return card, card_h


def capreveal():
    cards = [cap_variant(h) for h in (1, 2, 3)]
    fx = {'type': 'capfx', 'x': 0, 'y': 0, 'w': 440, 'h': 640, 'text': '{vm.fxText}', 'hint': '{vm.fxCols}', 'fill': '{vm.revealKey}'}
    stage = {'type': 'column', 'w': 'fill', 'h': 'fill', 'align': 'center', 'cross': 'center', 'if': 'vm.name', 'children': [fx] + [c for c, _ in cards]}
    tick = {'type': 'spacer', 'id': 'tick', 'w': 0, 'h': 0, 'every_ms': 1000, 'args': {'hov': 'int:0:1'}, 'argv': {'hov': '$hov'}, 'msg': vmsg('tick', hov='{hov}')}
    root = {'type': 'column', 'w': 'fill', 'h': 'fill', 'hover_set': {'hov': '0'}, 'children': [stage, tick, beat()]}
    return {'v': 2, 'id': 'ascensionlib_capture', 'channel': 'ascension_capture_ready', 'hello': 'ready', 'bye': False, 'title': 'Capture', 'width': 440, 'height': 640, 'fit': [1920, 1080],
            'nodim': True, 'state': {'hov': 0}, 'root': root}


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
    body = [head('TOWER COMPLETION'), main, foot, tick, beat()]
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


if __name__ == '__main__':
  for name, fn in (('ascensionlib_wallet', wallet), ('ascensionlib_guide', guide), ('ascensionlib_party', party), ('ascensionlib_inspect', inspect), ('ascensionlib_craft', craft), ('ascensionlib_fusion', fusion), ('ascensionlib_capture', capreveal), ('ascensionlib_completion', completion), ('ascensionlib_hud', hud)):
    d = fn()
    if name in TABLET:
        d = tablet(d, *TABLET[name], bezel=BEZEL.get(name), modal=MODAL.get(name))
    write(name, d)
