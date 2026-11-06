"""Generates the native QuestDex journal (questdex.json): nav on the left, quest-card grid in the middle, quest detail on the right."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
SCR, PANEL, HEAD, WELL, LN = '#06121C', '#0A1B27', '#0D2434', '#04101A', '#1F4A5E'
INK, MU, ACC, GOOD, WARN = '#E4ECF1', '#7F9AAB', '#5EE6FF', '#3AFF8C', '#FF7A7A'
HOV = '#12354A'


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
         'hover': HOV, 'disabled': '#07121B', 'color': INK}
    d.update(kw)
    return d


def act(kind, idx="''"):
    return {'args': {'k': 'str:12', 'id': 'str:64'}, 'argv': {'k': "'%s'" % kind, 'id': idx}, 'msg': vmsg('act', k='{k}', id='{id}')}


def objs_rows(var, src, limit):
    return {'type': 'column', 'w': 'fill', 'gap': 1, 'repeat': {'for': src, 'as': var, 'limit': limit}, 'children': [
        {'type': 'row', 'w': 'fill', 'gap': 6, 'children': [label('{%s.text}' % var, color='{%s.color}' % var, wrap=True, w='fill'), label('{%s.amt}' % var, color='{%s.color}' % var)]},
        {'type': 'bar', 'w': 'fill', 'h': 4, 'value': '%s.pct' % var, 'max': '100', 'fill': '{%s.color}' % var, 'fill_bg': WELL, 'radius': 1}]}


def nav():
    head = label('{n.label}', id='nhead', color=MU, scale=0.9, pad=[6, 2, 0, 2], **{'if': 'n.isHead'})
    item = btn('', id='ntab', w='fill', h=26, bg='{n.bg}', border='{n.border}', args={'k': 'str:12'}, argv={'k': 'n.k'}, msg=vmsg('tab', k='{k}'),
               children=[{'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [
                   {'type': 'image', 'pokemon': '{n.icon}', 'w': 16, 'h': 16}, label('{n.label}', color='{n.color}'), sp(w='fill'), label('{n.count}', color=MU)]}],
               **{'if': '!n.isHead'})
    item.pop('text')
    wrap = {'type': 'column', 'w': 'fill', 'gap': 3, 'repeat': {'for': 'vm.nav', 'as': 'n', 'limit': 16}, 'children': [head, item]}
    trainer = {'type': 'column', 'w': 'fill', 'bg': PANEL, 'border': LN, 'pad': [6, 8, 6, 8], 'gap': 1, 'children': [
        label('{vm.trainer}', color=INK, scale=1.1), label('{vm.trainerSub}', color=MU, wrap=True, w='fill')]}
    return {'type': 'column', 'w': 190, 'h': 'fill', 'gap': 6, 'children': [
        trainer, {'type': 'scroll', 'id': 'nscroll', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 3, 'children': [wrap]}]}]}


def cards():
    card = {'type': 'button', 'id': 'card', 'w': 'fill', 'h': 72, 'bg': PANEL, 'border': '{c.border}', 'hover': HOV, 'radius': 2, 'pad': [5, 6, 5, 6],
            'args': {'k': 'str:64'}, 'argv': {'k': 'c.id'}, 'msg': vmsg('sel', k='{k}'), 'if': 'c.has', 'children': [
        {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 6, 'children': [
            {'type': 'item', 'item': '{c.item}', 'count': 1, 'w': 28, 'h': 28},
            {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 1, 'children': [
                {'type': 'row', 'w': 'fill', 'gap': 4, 'children': [
                    label('{c.name}', color=INK, w='fill'), label('NEW', color='#FFD23A', **{'if': 'c.isNew'}), label('TRACKED', color=ACC, **{'if': 'c.pin'})]},
                label('{c.sum}', color=MU, wrap=True, w='fill'),
                sp(h='fill'),
                {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [
                    label('{c.chip}', color='{c.chipColor}', scale=0.9), label('{c.cat}', color=MU, scale=0.9)]},
                {'type': 'bar', 'if': 'c.showBar', 'w': 'fill', 'h': 4, 'value': 'c.pct', 'max': '100', 'fill': '{c.chipColor}', 'fill_bg': WELL, 'radius': 1}]}]}]}
    empty = {'type': 'column', 'if': '!c.has', 'w': 'fill', 'h': 72, 'children': []}
    row = {'type': 'row', 'w': 'fill', 'gap': 6, 'repeat': {'for': 'vm.rows', 'as': 'r', 'limit': 30}, 'children': [
        {'type': 'column', 'w': 'fill', 'repeat': {'for': 'r.cells', 'as': 'c', 'limit': 2}, 'children': [card, empty]}]}
    search = {'type': 'input', 'id': 'search', 'bind': 'q', 'hint': 'search quests...', 'w': 200, 'h': 22, 'max_len': 40, 'bg': WELL, 'border': LN, 'radius': 2, 'color': INK,
              'debounce_ms': 350, 'args': {'q': 'str:40'}, 'argv': {'q': '$q'}, 'msg': vmsg('q', q='{q}')}
    head = {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'children': [
        label('{vm.listName}', color='{vm.listColor}', scale=1.4), label('{vm.slots}', color=MU), sp(w='fill'), search]}
    return {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 6, 'children': [
        head,
        label('{vm.listDesc}', color=MU, wrap=True, w='fill'),
        label('{vm.banner}', color='#FFB36A', wrap=True, w='fill', bg='#2A1A0A', border='#8A5A1A', pad=[4, 6, 4, 6], **{'if': 'vm.banner'}),
        {'type': 'scroll', 'id': 'cscroll', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 6, 'children': [
            {'type': 'column', 'if': '!vm.empty', 'w': 'fill', 'gap': 6, 'children': [row]},
            label('{vm.emptyText}', color=MU, w='fill', wrap=True, **{'if': 'vm.empty'})]}]}]}


def detail():
    step = {'type': 'column', 'w': 'fill', 'gap': 2, 'bg': WELL, 'border': LN, 'pad': [4, 6, 4, 6], 'repeat': {'for': 'vm.d.steps', 'as': 's', 'limit': 8}, 'children': [
        {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [
            label('{s.mk}', color='{s.color}'), label('{s.name}', color='{s.color}', w='fill'),
            btn('TAKE', id='take', w=44, h=16, bg='#0B3A4C', **act('accept', 's.id'), **{'if': 's.take'})]},
        label('{s.meta}', color=MU, wrap=True, w='fill', scale=0.9),
        objs_rows('so', 's.objs', 4)]}
    req = btn('{r.text}', id='req', w='fill', h=18, color='{r.color}', args={'k': 'str:64'}, argv={'k': 'r.id'}, msg=vmsg('goto', k='{k}'),
              repeat={'for': 'vm.d.reqs', 'as': 'r', 'limit': 4})
    rew = label('+ {w.text}', color='#FFD23A', wrap=True, w='fill', repeat={'for': 'vm.d.rewards', 'as': 'w', 'limit': 8})
    actions = {'type': 'row', 'w': 'fill', 'gap': 6, 'children': [
        btn('OPEN THE RULES', id='rules', w='fill', bg='#0B3A4C', border=ACC, **act('rules'), **{'if': 'vm.d.showRules'}),
        btn('ACCEPT', id='accept', w='fill', bg='#0B3A4C', border=ACC, enabled='vm.d.acceptOk', **act('accept'), **{'if': 'vm.d.showAccept'}),
        btn('TURN IN', id='submit', w='fill', bg='#0B4A2C', border=GOOD, enabled='vm.d.submitOk', **act('submit'), **{'if': 'vm.d.showSubmit'}),
        btn('{vm.d.trackText}', id='track', w=90, **act('track'), **{'if': 'vm.d.showTrack'})]}
    body = {'type': 'column', 'if': 'vm.hasDetail', 'w': 'fill', 'h': 'fill', 'gap': 6, 'children': [
        {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'children': [
            {'type': 'item', 'item': '{vm.d.item}', 'count': 1, 'w': 32, 'h': 32},
            {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [
                label('{vm.d.name}', color=INK, scale=1.3, wrap=True, w='fill'), label('{vm.d.meta}', color='{vm.d.metaColor}', scale=0.9)]}]},
        {'type': 'row', 'w': 'fill', 'gap': 8, 'children': [label('{vm.d.chip}', color='{vm.d.chipColor}'), label('{vm.d.repeat}', color=MU, w='fill', wrap=True)]},
        {'type': 'scroll', 'id': 'dscroll', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 6, 'children': [
            label('BRIEFING', color=ACC, scale=0.9, **{'if': 'vm.d.hasBrief'}),
            label('{vm.d.brief}', color=INK, wrap=True, w='fill', **{'if': 'vm.d.hasBrief'}),
            label('OBJECTIVES', color=ACC, scale=0.9, **{'if': 'vm.d.single'}),
            {'type': 'column', 'if': 'vm.d.single', 'w': 'fill', 'gap': 4, 'children': [objs_rows('o', 'vm.d.objs', 6)]},
            label('{vm.d.stepsHead}', color=ACC, scale=0.9, **{'if': '!vm.d.single'}),
            {'type': 'column', 'if': '!vm.d.single', 'w': 'fill', 'gap': 4, 'children': [step]},
            label('REQUIRES', color=ACC, scale=0.9, **{'if': 'vm.d.hasReqs'}),
            {'type': 'column', 'if': 'vm.d.hasReqs', 'w': 'fill', 'gap': 2, 'children': [req]},
            label('REWARDS', color=ACC, scale=0.9),
            {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [rew]}]}]},
        label('{vm.d.note}', color='{vm.d.noteColor}', wrap=True, w='fill', **{'if': 'vm.d.note'}),
        actions]}
    placeholder = label('Pick a quest to see its details.', color=MU, wrap=True, w='fill', **{'if': '!vm.hasDetail'})
    return {'type': 'column', 'w': 310, 'h': 'fill', 'bg': PANEL, 'border': LN, 'pad': [8, 10, 8, 10], 'gap': 6, 'children': [body, placeholder]}


def page():
    tracker = {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'bg': PANEL, 'border': LN, 'pad': [4, 8, 4, 8], 'children': [
        label('TRACKING', color=ACC, scale=0.9), label('{vm.trackName}', color=INK),
        {'type': 'bar', 'w': 160, 'h': 6, 'value': 'vm.trackPct', 'max': '100', 'fill': ACC, 'fill_bg': WELL, 'radius': 1}, label('{vm.trackText}', color=MU)]}
    trackline = {'type': 'column', 'w': 'fill', 'children': [tracker], 'if': 'vm.tracking'}
    close = btn('X', id='close', w=28, bg='#3A1A2A', border=WARN, hover='#5A2A3E', close=True)
    head = {'type': 'row', 'w': 'fill', 'bg': HEAD, 'border': LN, 'pad': [6, 10, 6, 10], 'gap': 10, 'cross': 'center', 'children': [
        label('QUESTDEX', color=ACC, scale=1.4), sp(w='fill'), close]}
    tick = {'type': 'spacer', 'id': 'tick', 'w': 0, 'h': 0, 'every_ms': 1000, 'args': {}, 'msg': vmsg('tick')}
    ping = {'type': 'spacer', 'id': 'ping', 'w': 0, 'h': 0, 'every_ms': 4000, 'args': {}, 'msg': {'channel': 'questdex', 'kind': 'ping'}}
    root = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': SCR, 'border': LN, 'pad': [10, 10, 10, 10], 'gap': 6, 'children': [
        head, {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'children': [nav(), cards(), detail()]}, trackline, tick, ping]}
    return {'v': 2, 'id': 'questdex', 'channel': 'questdex', 'hello': 'hello', 'bye': True, 'title': 'QuestDex', 'width': 1000, 'height': 600, 'root': root}


d = page()
with open(os.path.join(OUT, 'questdex.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, indent=1)
print('wrote questdex')
