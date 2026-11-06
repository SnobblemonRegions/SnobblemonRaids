"""Generates the native Wiki page (wiki.json). Same layout as the web wiki: a mod list on the left, home cards / page cards /
article / search results in the middle, Back / Home / Prev / Next and a search box on top. Texts come from WikiViews (server)."""
import json, os, sys

sys.path.insert(0, os.path.dirname(__file__))
BG, BG2, LN, HI, TX, MU = '#04121F', '#08233A', '#1D6F8F', '#46E3FF', '#E6F7FF', '#7AA7BD'
HOV = '#0E4668'
OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')


def label(text, **kw):
    d = {'type': 'label', 'text': text}
    d.update(kw)
    return d


def sp(**kw):
    d = {'type': 'spacer'}
    d.update(kw)
    return d


def btn(text, **kw):
    d = {'type': 'button', 'text': text, 'h': 20, 'pad': [3, 10, 3, 10], 'cross': 'center', 'align': 'center', 'bg': BG, 'border': LN, 'radius': 3,
         'hover': HOV, 'disabled': '#0A1A26', 'color': HI}
    d.update(kw)
    return d


R = {'route': 'str:64'}


def go_btn(text, **kw):
    """A button that opens a wiki route given by the expression in kw['route_expr']."""
    expr = kw.pop('route_expr')
    return btn(text, args=R, argv={'route': expr}, msg={'action': 'go', 'route': '{route}'}, **kw)


def card(prefix):
    return {'type': 'button', 'id': prefix + '_card', 'w': 'fill', 'h': 54, 'bg': BG2, 'border': LN, 'radius': 4, 'hover': HOV, 'pad': [6, 8, 6, 8], 'gap': 2,
            'args': R, 'argv': {'route': 'cd.route'}, 'msg': {'action': 'go', 'route': '{route}'},
            'repeat': {'for': 'hr.cells', 'as': 'cd', 'if': '!cd.blank'}, 'children': [
                {'type': 'column', 'w': 'fill', 'gap': 2, 'children': [
                    label('{cd.title}', color=TX), label('{cd.text}', color=MU, wrap=True, w='fill')]}]}


def blank():
    return sp(w='fill', repeat={'for': 'hr.cells', 'as': 'cd', 'if': 'cd.blank'})


def card_rows(listname, prefix):
    return [
        {'type': 'row', 'repeat': {'for': listname, 'as': 'hr', 'limit': 120, 'if': '!hr.hdr'}, 'w': 'fill', 'gap': 6, 'children': [card(prefix), blank()]},
    ]


def main_home():
    return {'type': 'column', 'w': 'fill', 'h': 'fill', 'if': 'vm.isHome', 'children': [
        {'type': 'scroll', 'id': 'home_scroll', 'w': 'fill', 'h': 'fill', 'pad': [10, 14, 10, 14], 'children': [
            {'type': 'column', 'w': 'fill', 'gap': 6, 'children': [
                label('{vm.title}', scale=1.6, color=HI), label('{vm.intro}', color=MU, wrap=True, w='fill'), sp(h=6),
                {'type': 'column', 'repeat': {'for': 'vm.home', 'as': 'hr', 'limit': 120}, 'w': 'fill', 'gap': 4, 'children': [
                    label('{hr.name}', **{'if': 'hr.hdr', 'color': HI, 'pad': [8, 0, 0, 0]}),
                    {'type': 'row', 'if': '!hr.hdr', 'w': 'fill', 'gap': 6, 'children': [
                        dict(card('h'), repeat={'for': 'hr.cells', 'as': 'cd', 'if': '!cd.blank'}), blank()]}]}]}]}]}


def main_mod():
    return {'type': 'column', 'w': 'fill', 'h': 'fill', 'if': 'vm.isMod', 'children': [
        {'type': 'scroll', 'id': 'mod_scroll', 'w': 'fill', 'h': 'fill', 'pad': [10, 14, 10, 14], 'children': [
            {'type': 'column', 'w': 'fill', 'gap': 6, 'children': [
                label('{vm.modName}', scale=1.6, color=HI), label('{vm.modKind}', color=MU),
                label('{vm.summary}', color=TX, wrap=True, w='fill'), sp(h=4),
                {'type': 'row', 'repeat': {'for': 'vm.info', 'as': 'i', 'limit': 6}, 'w': 'fill', 'children': [label('{i.k}', color=MU, w='fill'), label('{i.v}', color=TX)]},
                sp(h=6), label('PAGES', color=HI),
                {'type': 'row', 'repeat': {'for': 'vm.cards', 'as': 'hr', 'limit': 60}, 'w': 'fill', 'gap': 6, 'children': [
                    dict(card('m'), repeat={'for': 'hr.cells', 'as': 'cd', 'if': '!cd.blank'}), blank()]},
                label('RELATED', color=HI, **{'if': 'vm.related'}),
                {'type': 'row', 'w': 'fill', 'gap': 6, 'children': [
                    go_btn('{r.text}', route_expr='r.route', id='rel_mod', repeat={'for': 'vm.related', 'as': 'r', 'limit': 6})]}]}]}]}


def block_node():
    cells = {'type': 'row', 'w': 'fill', 'gap': 8, 'children': [
        label('{cl.x}', color='{cl.color}', w='fill', wrap=True, repeat={'for': 'b.cells', 'as': 'cl', 'limit': 6})]}
    link_row = {'type': 'row', 'if': 'b.hasLinks', 'w': 'fill', 'gap': 4, 'pad': [0, 0, 0, 12], 'children': [
        go_btn('{l.text}', route_expr='l.route', id='lnk', h=16, pad=[1, 6, 1, 6], color='#7FD0FF', repeat={'for': 'b.links', 'as': 'l', 'limit': 4})]}
    return {'type': 'column', 'repeat': {'for': 'vm.blocks', 'as': 'b', 'limit': 400}, 'w': 'fill', 'gap': 2, 'children': [
        label('{b.text}', **{'if': "b.k == 'p'", 'color': TX, 'wrap': True, 'w': 'fill'}),
        label('{b.text}', **{'if': "b.k == 'h'", 'color': HI, 'scale': 1.3, 'pad': [8, 0, 2, 0]}),
        label('{b.text}', **{'if': "b.k == 'h3'", 'color': '#9FE8F5', 'pad': [4, 0, 0, 0]}),
        label('{b.text}', **{'if': "b.k == 'li'", 'color': TX, 'wrap': True, 'w': 'fill', 'pad': [0, 0, 0, 8]}),
        label('{b.text}', **{'if': "b.k == 'code'", 'color': '#B9F3CF', 'bg': '#020B13', 'wrap': True, 'w': 'fill', 'pad': [1, 6, 1, 6]}),
        label('{b.text}', **{'if': "b.k == 'call'", 'color': TX, 'bg': '{b.bg}', 'border': '{b.border}', 'radius': 3, 'wrap': True, 'w': 'fill', 'pad': [5, 8, 5, 8]}),
        {'type': 'column', 'if': "b.k == 'card' && b.hasRoute", 'w': 'fill', 'children': [
            go_btn('{b.text}  -  {b.desc}', route_expr='b.route', id='card_go', w='fill', align='start')]},
        label('{b.text}', **{'if': "b.k == 'card' && !b.hasRoute", 'color': TX}),
        {'type': 'row', 'if': "b.k == 'th'", 'w': 'fill', 'gap': 8, 'bg': '#0B3550', 'pad': [2, 4, 2, 4], 'children': cells['children']},
        {'type': 'row', 'if': "b.k == 'tr'", 'w': 'fill', 'gap': 8, 'pad': [1, 4, 1, 4], 'children': cells['children']},
        link_row]}


def main_page():
    return {'type': 'row', 'w': 'fill', 'h': 'fill', 'if': 'vm.isPage', 'gap': 8, 'children': [
        {'type': 'scroll', 'id': 'page_scroll', 'w': 'fill', 'h': 'fill', 'pad': [10, 14, 10, 14], 'children': [
            {'type': 'column', 'w': 'fill', 'gap': 4, 'children': [
                label('{vm.pageTitle}', scale=1.6, color=HI), label('{vm.desc}', color=MU, wrap=True, w='fill'), sp(h=4), block_node(), sp(h=8),
                label('RELATED', color=HI, **{'if': 'vm.related'}),
                {'type': 'row', 'w': 'fill', 'gap': 6, 'children': [
                    go_btn('{r.text}', route_expr='r.route', id='rel_page', repeat={'for': 'vm.related', 'as': 'r', 'limit': 6})]}]}]},
        {'type': 'column', 'w': 150, 'h': 'fill', 'bg': BG2, 'border': LN, 'radius': 4, 'pad': [8, 8, 8, 8], 'gap': 3, 'children': [
            label('{vm.modName}', color=HI),
            {'type': 'column', 'repeat': {'for': 'vm.info', 'as': 'i', 'limit': 6}, 'w': 'fill', 'gap': 0, 'children': [label('{i.k}', color=MU), label('{i.v}', color=TX, wrap=True, w='fill')]}]}]}


def main_search():
    return {'type': 'column', 'w': 'fill', 'h': 'fill', 'if': 'vm.isSearch', 'children': [
        {'type': 'scroll', 'id': 'search_scroll', 'w': 'fill', 'h': 'fill', 'pad': [10, 14, 10, 14], 'children': [
            {'type': 'column', 'w': 'fill', 'gap': 5, 'children': [
                label('Search', scale=1.6, color=HI), label('{vm.resultText}', color=MU),
                go_btn('{r.title}', route_expr='r.route', id='res', w='fill', h=40, align='start', bg=BG2,
                       repeat={'for': 'vm.results', 'as': 'r', 'limit': 30}, children=[
                           {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [
                               label('{r.title}  -  {r.trail}', color=TX), label('{r.snip}', color=MU, wrap=True, w='fill')]}])]}]}]}


def sidebar():
    return {'type': 'scroll', 'id': 'side', 'w': 170, 'h': 'fill', 'bg': BG2, 'border': LN, 'radius': 4, 'pad': [6, 6, 6, 6], 'children': [
        {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [
            {'type': 'column', 'repeat': {'for': 'vm.side', 'as': 's', 'limit': 80}, 'w': 'fill', 'gap': 0, 'children': [
                label('{s.name}', **{'if': 's.hdr', 'color': HI, 'pad': [6, 2, 1, 2]}),
                go_btn('{s.name}', route_expr='s.route', id='nav', **{'if': '!s.hdr', 'w': 'fill', 'h': 16, 'align': 'start', 'bg': '{s.bg}', 'border': '#04121F', 'color': '{s.color}', 'pad': [1, 6, 1, 6]})]}]}]}


def top():
    q = {'q': 'str:64'}
    return {'type': 'row', 'w': 'fill', 'pad': [10, 14, 6, 14], 'gap': 6, 'cross': 'center', 'children': [
        label('SNOBBLEMON WIKI', scale=1.3, color=HI),
        btn('< Back', id='back', w=52, enabled='vm.canBack', args={}, msg={'action': 'back'}),
        btn('Home', id='home', w=48, args={}, msg={'action': 'home'}),
        label('{vm.crumb}', color=MU), sp(w='fill'),
        btn('< Prev', id='prev', w=50, **{'if': 'vm.isPage', 'enabled': 'vm.canPrev', 'args': {'dir': 'str:8'}, 'argv': {'dir': "'prev'"}, 'msg': {'action': 'step', 'dir': '{dir}'}}),
        btn('Next >', id='next', w=50, **{'if': 'vm.isPage', 'enabled': 'vm.canNext', 'args': {'dir': 'str:8'}, 'argv': {'dir': "'next'"}, 'msg': {'action': 'step', 'dir': '{dir}'}}),
        {'type': 'input', 'id': 'search', 'bind': 'q', 'hint': 'Search the wiki...', 'w': 150, 'h': 20, 'max_len': 60, 'bg': '#021018', 'border': LN, 'radius': 3, 'color': TX,
         'debounce_ms': 450, 'args': q, 'argv': {'q': '$q'}, 'msg': {'action': 'search', 'q': '{q}'}},
        btn('Close', w=54, close=True)]}


def build():
    body = [top(), {'type': 'row', 'w': 'fill', 'h': 'fill', 'pad': [2, 14, 12, 14], 'gap': 8, 'children': [
        sidebar(),
        {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': BG, 'border': LN, 'radius': 4, 'children': [main_home(), main_mod(), main_page(), main_search()]}]}]
    return {'v': 2, 'id': 'wiki', 'channel': 'snobblewiki', 'hello': 'hello', 'bye': False, 'title': 'Snobblemon Wiki', 'width': 920, 'height': 520,
            'state': {'q': ''},
            'root': {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': BG, 'border': LN, 'radius': 4, 'children': body}}


if __name__ == '__main__':
    with open(os.path.join(OUT, 'wiki.json'), 'w', encoding='utf8') as f:
        json.dump(build(), f, indent=1)
    print('wrote wiki')
