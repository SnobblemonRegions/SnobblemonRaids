"""Generates the native Regions Cases page (cases.json): tabs, help tickets, suggestions, reports, punishments and the audit log."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
SCR, PANEL, HEAD, WELL, LN = '#5A3A22', '#F7ECC8', '#432A17', '#FFFDF4', '#8A6A28'
CREAM, DESK, BTN = '#F3E6C8', '#EFE2BD', '#3A2A17'
INK, MU, ACC, GOOD, WARN = '#2B2118', '#7A5F2E', '#5B4310', '#2F7D3A', '#B3261E'
HOV = '#55391E'

ARGS = {'a': 'str:16', 'id': 'int:0:2000000000', 'v': 'str:64', 't': 'str:500', 'n': 'str:64', 'd': 'str:16'}
MSG = {'channel': 'snobble_view', 'action': 'act', 'a': '{a}', 'id': '{id}', 'v': '{v}', 't': '{t}', 'n': '{n}', 'd': '{d}'}


_N = 0


def label(text, **kw):
    d = {'type': 'label', 'text': text}
    d.update(kw)
    return d


def sp(**kw):
    d = {'type': 'spacer'}
    d.update(kw)
    return d


def btn(text, **kw):
    d = {'type': 'button', 'text': text, 'h': 22, 'pad': [3, 10, 3, 10], 'cross': 'center', 'align': 'center', 'bg': BTN, 'border': '#7A5F2E', 'radius': 2,
         'hover': HOV, 'disabled': '#9A8A66', 'color': CREAM}
    d.update(kw)
    return d


def ab(text, a, id='0', v="''", t="''", n="''", d="''", **kw):
    """A button that sends the generic 'act' message; a starting with '@' is an expression, anything else a literal."""
    aexpr = a[1:] if a.startswith('@') else "'%s'" % a
    global _N
    _N += 1
    nid = 'b%d' % _N
    node = btn(text, args=dict(ARGS), argv={'a': aexpr, 'id': id, 'v': v, 't': t, 'n': n, 'd': d}, msg=dict(MSG), **kw)
    node['id'] = nid
    return node


def panel(children, **kw):
    d = {'type': 'column', 'bg': PANEL, 'border': LN, 'pad': [8, 10, 8, 10], 'gap': 6, 'children': children}
    d.update(kw)
    return d


def scroll(id, children, **kw):
    d = {'type': 'scroll', 'id': id, 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 4, 'children': children}]}
    d.update(kw)
    return d


def inp(id, bind, hint, h=22, maxlen=40, **kw):
    d = {'type': 'input', 'id': id, 'bind': bind, 'hint': hint, 'w': 'fill', 'h': h, 'max_len': maxlen, 'bg': WELL, 'border': LN, 'radius': 2, 'color': INK}
    d.update(kw)
    return d


def chips(src, var, act, id, limit=10, h=20, w='fill', **kw):
    """A repeated chip button; k/label/bg/border come from the row."""
    b = ab('{%s.label}' % var, act, v='%s.k' % var, id=id, w=w, h=h, bg='{%s.bg}' % var, border='{%s.border}' % var, color='{%s.color}' % var,
           repeat={'for': src, 'as': var, 'limit': limit}, **kw)
    return b


def thread(pfx, extra=None, tagged=True):
    """A conversation paper: heading, optional note, scrolling messages (stuck to the newest) and the reply box. Reads vm.th."""
    head = {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'children': [
        label('{vm.th.head}', color=ACC, w='fill', wrap=True)] + ([label('{vm.th.tag}', color='{vm.th.tagColor}', **{'if': 'vm.th.tag'})] if tagged else []) + (extra or [])}
    msgs = {'type': 'scroll', 'id': pfx + '_msgs', 'w': 'fill', 'h': 'fill', 'stick': True, 'value': 'vm.th.id', 'bg': WELL, 'border': LN, 'pad': [4, 6, 4, 6], 'children': [
        {'type': 'column', 'w': 'fill', 'gap': 5, 'children': [
            {'type': 'column', 'w': 'fill', 'gap': 0, 'repeat': {'for': 'vm.th.msgs', 'as': 'm', 'limit': 60}, 'children': [
                label('{m.head}', color=MU, scale=0.85, **{'if': 'm.hasHead'}), label('{m.text}', color='{m.color}', wrap=True, w='fill')]}]}]}
    reply = {'type': 'row', 'w': 'fill', 'gap': 6, 'if': 'vm.th.canWrite', 'children': [
        inp(pfx + '_reply', 'reply', 'Write a message...', h=44, maxlen=500, wrap=True),
        ab('SEND', '@vm.th.act', id='vm.th.id', t='$reply', w=70, h=44, bg=BTN, border='#7A5F2E')]}
    closed = label('{vm.th.closedText}', color=MU, w='fill', wrap=True, **{'if': '!vm.th.canWrite'})
    return panel([head, label('{vm.th.sla}', color='#3E3020', wrap=True, w='fill', **{'if': 'vm.th.hasSla'}), msgs, reply, closed], w='fill', h='fill')


def left_list(title, newbtn, src, selact, emptytext, noflag):
    item = ab('', selact, id='r.id', w='fill', h=40, bg='{r.bg}', border='{r.border}', repeat={'for': src, 'as': 'r', 'limit': 14}, children=[
        {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [
            {'type': 'row', 'w': 'fill', 'children': [label('{r.no}', color=INK, w='fill'), label('{r.tag}', color='{r.tagColor}', scale=0.85)]},
            label('{r.sub}', color='{r.subColor}', scale=0.85)]}])
    item.pop('text')
    return panel([label(title, color=ACC), newbtn, label(emptytext, color=MU, wrap=True, w='fill', **{'if': noflag}), scroll(selact + '_list', [item])], w=250, h='fill')


def grid(src_rows, card, prefix):
    cell = {'type': 'column', 'w': 'fill', 'repeat': {'for': 'r.cells', 'as': 'c', 'limit': 3}, 'children': [card, {'type': 'column', 'if': '!c.has', 'w': 'fill', 'h': 92, 'children': []}]}
    row = {'type': 'row', 'w': 'fill', 'gap': 6, 'repeat': {'for': src_rows, 'as': 'r', 'limit': 8}, 'children': [cell]}
    return row


# ---------------------------------------------------------------- views
def v_mine():
    newbtn = ab('+ NEW TICKET', 'newticket', w='fill', bg='#2F7D3A', border=GOOD, enabled='vm.canNew')
    left = left_list('YOUR TICKETS', newbtn, 'vm.list', 'selmine', 'You have no tickets yet. Press NEW TICKET, or type /ticket your message in chat.', 'vm.noList')
    form = panel([
        label('NEW CASE', color=ACC, scale=1.2),
        label('{vm.nf.sla}', color='#3E3020', wrap=True, w='fill'),
        inp('newtext', 'newtext', 'Describe the problem...', h=150, maxlen=500, wrap=True, debounce_ms=500,
            args=dict(ARGS), argv={'a': "'preview'", 'id': '0', 'v': "''", 't': '$newtext', 'n': "''", 'd': "''"}, msg=dict(MSG)),
        label('{vm.nf.detected}', color=ACC, w='fill', wrap=True),
        {'type': 'row', 'w': 'fill', 'gap': 4, 'children': [label('CATEGORY', color=MU), chips('vm.nf.cats', 'k', 'newcat', '0', limit=10, w=76)]},
        {'type': 'row', 'w': 'fill', 'gap': 10, 'children': [
            ab('SUBMIT TICKET', 'create', t='$newtext', w=160, h=28, bg='#2F7D3A', border=GOOD),
            ab('CANCEL', 'cancelnew', w=90, h=28, **{'if': 'vm.nf.canCancel'})]},
        label('Your position is saved with the ticket so staff can find the place you mean. Staff can change the category if it was guessed wrong.', color=MU, wrap=True, w='fill', scale=0.9)],
        w='fill', h='fill', **{'if': 'vm.showNew'})
    th = {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 4, 'if': '!vm.showNew', 'children': [
        thread('mine', [ab('CLOSE MY TICKET', 'pclose', id='vm.selId', w=120, h=18, **{'if': 'vm.canClose'})])]}
    return {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'if': 'vm.vMine', 'children': [left, form, th]}


def v_queue():
    filt = {'type': 'row', 'w': 'fill', 'gap': 4, 'cross': 'center', 'children': [
        chips('vm.flt', 'f', 'flt', '0', limit=6, w=84), sp(w=6), chips('vm.cats', 'k', 'cat', '0', limit=10, w=70)]}
    bar = {'type': 'row', 'w': 'fill', 'gap': 4, 'cross': 'center', 'children': [
        inp('qtext', 'qtext', 'Search player or text...', w=240, debounce_ms=600, args=dict(ARGS),
            argv={'a': "'qtext'", 'id': '0', 'v': "''", 't': '$qtext', 'n': "''", 'd': "''"}, msg=dict(MSG)),
        label('SORT', color=MU), chips('vm.sorts', 's', 'sort', '0', limit=4, w=96), sp(w='fill'), label('{vm.total}', color=MU)]}
    card = ab('', 'openticket', id='c.id', w='fill', h=92, bg='#E7C97A', hover='#F0D589', border='{c.border}', pad=[5, 7, 5, 7], anim='slide', anim_ms=300, **{'if': 'c.has'}, children=[
        {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 1, 'children': [
            {'type': 'row', 'w': 'fill', 'children': [label('{c.no}', color=ACC, w='fill'), label('[{c.stamp}]', color='{c.stampColor}', anim='pop', anim_ms=420)]},
            {'type': 'row', 'w': 'fill', 'gap': 6, 'children': [label('{c.who}', color=INK), label('{c.cat}', color=MU, scale=0.85)]},
            label('{c.preview}', color='#2B2118', wrap=True, w='fill', scale=0.95), sp(h='fill'),
            {'type': 'row', 'w': 'fill', 'gap': 4, 'children': [label('{c.foot}', color=MU, scale=0.85), label('needs reply', color=WARN, scale=0.85, **{'if': 'c.needs'})]}]}])
    card.pop('text')
    body = {'type': 'scroll', 'id': 'q_scroll', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 6, 'children': [
        {'type': 'column', 'if': '!vm.empty', 'w': 'fill', 'gap': 6, 'children': [grid('vm.rows', card, 'q')]},
        label('{vm.emptyText}', color=MU, w='fill', wrap=True, **{'if': 'vm.empty'})]}]}
    return {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 6, 'if': 'vm.vQueue', 'children': [filt, bar, body]}


def v_ticket():
    cl = panel([
        {'type': 'row', 'w': 'fill', 'gap': 6, 'children': [label('{vm.cl.name}', color=INK, scale=1.3, w='fill'), label('{vm.cl.online}', color='{vm.cl.onlineColor}', scale=0.9)]},
        label('{vm.cl.opened}', color='#3E3020', scale=0.9, wrap=True, w='fill'),
        label('{vm.cl.now}', color='#3E3020', scale=0.9, wrap=True, w='fill', **{'if': 'vm.cl.hasNow'}),
        label('{vm.cl.past}', color='#3E3020', scale=0.9, wrap=True, w='fill'),
        label('{vm.cl.stamp}', color='{vm.cl.stampColor}')], w='fill')
    cid = 'vm.cl.id'
    acts = {'type': 'column', 'w': 'fill', 'gap': 4, 'children': [
        {'type': 'row', 'w': 'fill', 'gap': 4, 'children': [
            ab('{vm.cl.claimLabel}', '@vm.cl.claimAct', id=cid, w='fill', bg='#2F7D3A', border=GOOD, **{'if': '!vm.cl.closed'}),
            ab('{vm.cl.urgentLabel}', 'urgent', id=cid, v='vm.cl.urgentVal', w='fill', **{'if': '!vm.cl.closed'})]},
        {'type': 'row', 'w': 'fill', 'gap': 4, 'children': [
            ab('{vm.cl.tpLabel}', 'tpto', v='vm.cl.name', w='fill', **{'if': 'vm.cl.canTp'}),
            ab('INVENTORY', 'invsee', v='vm.cl.name', w='fill', **{'if': 'vm.cl.canInv'})]},
        {'type': 'row', 'w': 'fill', 'gap': 4, 'children': [
            ab('ENDER CHEST', 'endersee', v='vm.cl.name', w='fill', **{'if': 'vm.cl.canEnder'}),
            ab('HISTORY', 'history', v='vm.cl.name', w='fill'),
            ab('PUNISH', 'history', v='vm.cl.name', w='fill', bg='#B3261E', border=WARN)]}]}
    cats = {'type': 'column', 'w': 'fill', 'gap': 2, 'children': [
        label('{vm.cl.holder}', color=MU, scale=0.85), label('CATEGORY (CLICK TO CHANGE)', color=MU, scale=0.85),
        chips('vm.tcats', 'k', 'setcat', 'vm.cl.id', limit=10, h=17)]}
    close = ab('STAMP CLOSED', 'sclose', id=cid, w='fill', h=28, bg='#2F7D3A', border=GOOD, **{'if': '!vm.cl.closed'})
    right = {'type': 'column', 'w': 320, 'h': 'fill', 'gap': 6, 'children': [cl, acts, cats, sp(h='fill'), close]}
    return {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'if': 'vm.vTicket', 'children': [thread('tick'), right]}


def v_reports():
    row = {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'bg': PANEL, 'border': LN, 'pad': [6, 8, 6, 8], 'repeat': {'for': 'vm.rows', 'as': 'r', 'limit': 14}, 'children': [
        label('{r.tag}', color='{r.tagColor}', w=90),
        {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [
            {'type': 'row', 'w': 'fill', 'gap': 6, 'children': [label('{r.target}', color=INK), label('{r.by}', color=MU, scale=0.9)]},
            label('{r.reason}', color='#3E3020', wrap=True, w='fill'), label('{r.note}', color=GOOD, scale=0.85, wrap=True, w='fill')]},
        label('{r.where}', color=MU, scale=0.85, w=150, wrap=True),
        ab('CLAIM', 'rclaim', id='r.id', w=52, h=18, bg='#2F7D3A', border=GOOD, **{'if': 'r.canClaim'}),
        ab('CLOSE', 'rclose', id='r.id', w=52, h=18, **{'if': 'r.canClose'}),
        ab('TP', 'tpto', v='r.target', w=30, h=18, **{'if': 'r.canTp'}),
        ab('PUNISH', 'history', v='r.target', w=60, h=18, bg='#B3261E', border=WARN)]}
    head = {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [
        label('PLAYER REPORTS', color=ACC, scale=1.2), sp(w='fill'), chips('vm.flt', 'f', 'rep', '0', limit=2, w=70)]}
    return {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 6, 'if': 'vm.vReports', 'children': [
        head, scroll('rep_scroll', [row, label('{vm.emptyText}', color=MU, w='fill', **{'if': 'vm.empty'})])]}


def v_sugs():
    bar = {'type': 'row', 'w': 'fill', 'gap': 4, 'cross': 'center', 'children': [
        chips('vm.flt', 'f', 'sugfilter', '0', limit=6, w=92),
        inp('sugtext', 'sugsearch', 'Search idea or player...', w=220, debounce_ms=600, args=dict(ARGS),
            argv={'a': "'sugtext'", 'id': '0', 'v': "''", 't': '$sugsearch', 'n': "''", 'd': "''"}, msg=dict(MSG)),
        sp(w='fill'), label('{vm.total}', color=MU)]}
    card = ab('', 'opensug', id='c.id', w='fill', h=92, bg='{c.bg}', hover='#FFFFFF', anim='slide', anim_ms=300, border='{c.border}', pad=[5, 7, 5, 7], **{'if': 'c.has'}, children=[
        {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 1, 'children': [
            {'type': 'row', 'w': 'fill', 'children': [label('{c.no}', color=MU, scale=0.85, w='fill'), label('{c.tag}', color='{c.tagColor}', scale=0.85)]},
            label('{c.title}', color=INK, w='fill'), label('{c.preview}', color='#3E3020', wrap=True, w='fill', scale=0.95), sp(h='fill'),
            label('{c.foot}', color=MU, scale=0.85)]}])
    card.pop('text')
    body = {'type': 'scroll', 'id': 's_scroll', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 6, 'children': [
        {'type': 'column', 'if': '!vm.empty', 'w': 'fill', 'gap': 6, 'children': [grid('vm.rows', card, 's')]},
        label('{vm.emptyText}', color=MU, w='fill', wrap=True, **{'if': 'vm.empty'})]}]}
    return {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 6, 'if': 'vm.vSugs', 'children': [bar, body]}


def v_sug():
    card = panel([
        label('SUGGESTED BY', color=MU, scale=0.85),
        {'type': 'row', 'w': 'fill', 'gap': 6, 'children': [label('{vm.cl.name}', color=INK, scale=1.3, w='fill'), label('{vm.cl.online}', color='{vm.cl.onlineColor}', scale=0.9)]},
        label('{vm.cl.sent}', color='#3E3020', scale=0.9), label('{vm.cl.others}', color='#3E3020', scale=0.9),
        label('{vm.cl.by}', color='#3E3020', scale=0.9, **{'if': 'vm.cl.hasBy'})], w='fill')
    dec = panel([
        label('SET A DECISION (THE PLAYER IS TOLD IN CHAT)', color=MU, scale=0.85, wrap=True, w='fill'),
        chips('vm.scats', 'k', 'sugset', 'vm.cl.id', limit=6, h=20),
        label('Setting Declined or Done closes the idea for new player messages. Write a reply first so the player knows why.', color='#3E3020', wrap=True, w='fill', scale=0.9)], w='fill')
    right = {'type': 'column', 'w': 320, 'h': 'fill', 'gap': 6, 'children': [card, dec]}
    return {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'if': 'vm.vSug', 'children': [thread('sug'), right]}


def v_psug():
    newbtn = ab('+ NEW SUGGESTION', 'newsug', w='fill', bg='#2B5FA8', border='#1B3F78')
    left = left_list('YOUR SUGGESTIONS', newbtn, 'vm.list', 'selsug', 'You have not sent an idea yet. Press NEW SUGGESTION and tell us what would make the server better.', 'vm.noList')
    form = panel([
        label('NEW SUGGESTION', color=ACC, scale=1.2),
        label('Share an idea for the server. Staff read every one and tell you what they decide. Please keep it to one idea per suggestion.', color='#3E3020', wrap=True, w='fill'),
        label('SHORT TITLE (OPTIONAL)', color=MU, scale=0.85),
        inp('sugtitle', 'sugtitle', 'Example: Sell Great Balls at the Pokecenter', maxlen=60),
        label('YOUR IDEA', color=MU, scale=0.85),
        inp('sugidea', 'sugidea', 'Describe your idea and why it would help...', h=150, maxlen=500, wrap=True),
        {'type': 'row', 'w': 'fill', 'gap': 10, 'children': [
            ab('SEND SUGGESTION', 'sugcreate', n='$sugtitle', t='$sugidea', w=170, h=28, bg='#2B5FA8', border='#1B3F78'),
            ab('CANCEL', 'cancelsug', w=90, h=28, **{'if': 'vm.canCancel'})]}], w='fill', h='fill', **{'if': 'vm.showNew'})
    th = {'type': 'column', 'w': 'fill', 'h': 'fill', 'if': '!vm.showNew', 'children': [thread('psug')]}
    return {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'if': 'vm.vPsug', 'children': [left, form, th]}


def v_prep():
    slip = {'type': 'column', 'w': 'fill', 'gap': 1, 'bg': WELL, 'border': LN, 'pad': [4, 6, 4, 6], 'repeat': {'for': 'vm.rows', 'as': 'r', 'limit': 12}, 'children': [
        {'type': 'row', 'w': 'fill', 'children': [label('{r.no}', color=MU, scale=0.85, w='fill'), label('{r.tag}', color='{r.tagColor}', scale=0.85)]},
        label('{r.target}', color=INK), label('{r.reason}', color='#3E3020', wrap=True, w='fill', scale=0.95)]}
    left = panel([label('YOUR REPORTS', color=ACC),
                  label('You have not reported anyone. Use the form to tell staff about a player who broke a rule.', color=MU, wrap=True, w='fill', **{'if': 'vm.empty'}),
                  scroll('rp_list', [slip])], w=330, h='fill')
    online = {'type': 'row', 'w': 'fill', 'gap': 4, 'if': 'vm.hasOnline', 'children': [
        label('ONLINE NOW', color=MU, scale=0.85), ab('{o.k}', 'pickname', v='o.k', w=90, h=18, repeat={'for': 'vm.online', 'as': 'o', 'limit': 6})]}
    form = panel([
        label('REPORT A PLAYER', color=ACC, scale=1.2),
        label('Only report rule-breaking such as griefing, cheating, harassment or spam. Staff also see where you are standing when you send it. For problems that are not about a player, use Help Tickets.',
              color='#3E3020', wrap=True, w='fill'),
        label('PLAYER NAME', color=MU, scale=0.85),
        inp('repname', 'repname', 'Type the name, or pick one below', maxlen=32),
        online,
        label('WHAT HAPPENED?', color=MU, scale=0.85),
        inp('reptext', 'reptext', 'What did they do, and when?', h=130, maxlen=500, wrap=True),
        ab('SEND REPORT', 'repcreate', n='$repname', t='$reptext', w=160, h=28, bg='#B3261E', border=WARN)], w='fill', h='fill')
    return {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'if': 'vm.vPrep', 'children': [left, form]}


def v_punish():
    search = {'type': 'row', 'w': 'fill', 'gap': 6, 'cross': 'center', 'children': [
        label('FIND PLAYER', color=ACC), inp('pname', 'pname', 'Player name', w=240, maxlen=32), ab('SEARCH', 'lookup', t='$pname', w=70)]}
    stats = panel([
        {'type': 'row', 'w': 'fill', 'gap': 12, 'cross': 'center', 'children': [
            label('{vm.pl.initial}', color=ACC, scale=2.0, w=30),
            {'type': 'column', 'w': 'fill', 'gap': 1, 'children': [
                label('{vm.pl.name}', color=INK, scale=1.4), label('{vm.pl.line}', color='{vm.pl.lineColor}', scale=0.9),
                label('{vm.pl.flags}', color=WARN, scale=0.9, **{'if': 'vm.pl.hasFlags'})]},
            {'type': 'column', 'gap': 0, 'cross': 'center', 'children': [label('{vm.pl.warnings}', color='#FFC15A', scale=1.6), label('WARNINGS', color=MU, scale=0.8)]},
            {'type': 'column', 'gap': 0, 'cross': 'center', 'children': [label('{vm.pl.mutes}', color=WARN, scale=1.6), label('MUTES', color=MU, scale=0.8)]},
            {'type': 'column', 'gap': 0, 'cross': 'center', 'children': [label('{vm.pl.bans}', color=INK, scale=1.6), label('BANS', color=MU, scale=0.8)]},
            {'type': 'column', 'gap': 0, 'cross': 'center', 'children': [label('{vm.pl.kicks}', color=INK, scale=1.6), label('KICKS', color=MU, scale=0.8)]}]}], w='fill')
    hrow = {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'bg': WELL, 'border': LN, 'pad': [4, 6, 4, 6], 'repeat': {'for': 'vm.hist', 'as': 'h', 'limit': 40}, 'children': [
        label('{h.type}', color='{h.color}', w=50),
        {'type': 'column', 'w': 'fill', 'gap': 0, 'children': [label('{h.reason}', color='#3E3020', wrap=True, w='fill'), label('{h.meta}', color=MU, scale=0.85, wrap=True, w='fill')]},
        ab('LIFT', 'lift', v='h.liftK', n='vm.pl.name', w=44, h=18, **{'if': 'h.canLift'})]}
    hist = panel([label('RECORD', color=ACC), scroll('hist_scroll', [hrow, label('Clean record.', color=MU, **{'if': 'vm.clean'})])], w='fill', h='fill')
    form = panel([
        label('NEW ENTRY - PICK A TYPE', color=ACC, scale=0.9),
        {'type': 'column', 'w': 'fill', 'gap': 2, 'children': [chips('vm.ptypes', 'k', 'ptype', '0', limit=8, h=17)]},
        inp('pdur', 'pdur', 'Length, e.g. 30m, 2h, 7d, 1w', maxlen=12, **{'if': 'vm.temp'}),
        inp('preason', 'preason', 'Reason (the player sees it)', h=80, maxlen=240, wrap=True),
        ab('{vm.applyLabel}', 'punish', v='vm.ptype', n='vm.pl.name', t='$preason', d='$pdur', w='fill', h=26, bg='#B3261E', border=WARN, **{'if': 'vm.hasType'}),
        label('Staff protected by the exempt permission cannot be punished.', color=MU, scale=0.85, wrap=True, w='fill')], w=300, h='fill')
    player = {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 6, 'if': 'vm.hasPlayer', 'children': [
        stats, {'type': 'row', 'w': 'fill', 'h': 'fill', 'gap': 8, 'children': [hist, form]}]}
    matches = panel([label('DID YOU MEAN', color=ACC), ab('{m.k}', 'history', v='m.k', w=200, h=20, repeat={'for': 'vm.matches', 'as': 'm', 'limit': 10})],
                    w=400, **{'if': 'vm.hasMatches'})
    empty = label('{vm.emptyText}', color=MU, w='fill', wrap=True, **{'if': 'vm.showEmpty'})
    return {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 6, 'if': 'vm.vPunish', 'children': [search, player, matches, empty]}


def v_logs():
    head = {'type': 'row', 'w': 'fill', 'gap': 4, 'cross': 'center', 'children': [label('AUDIT LOG', color=ACC, scale=1.2), sp(w=10), chips('vm.kinds', 'k', 'logk', '0', limit=6, w=80)]}
    row = {'type': 'row', 'w': 'fill', 'gap': 8, 'bg': PANEL, 'pad': [2, 8, 2, 8], 'repeat': {'for': 'vm.rows', 'as': 'r', 'limit': 80}, 'children': [
        label('{r.time}', color=MU, w=70, scale=0.9), label('{r.kind}', color='{r.color}', w=60, scale=0.9), label('{r.actor}', color=INK, w=120, scale=0.9),
        label('{r.text}', color='#3E3020', w='fill', wrap=True, scale=0.9)]}
    return {'type': 'column', 'w': 'fill', 'h': 'fill', 'gap': 6, 'if': 'vm.vLogs', 'children': [
        head, scroll('log_scroll', [row, label('Nothing logged yet.', color=MU, **{'if': 'vm.empty'})])]}


def page():
    tab = ab('', 'tab', v='tb.k', h=26, w=130, bg='{tb.bg}', border='{tb.border}', repeat={'for': 'vm.tabs', 'as': 'tb', 'limit': 8}, children=[
        {'type': 'row', 'w': 'fill', 'gap': 4, 'cross': 'center', 'children': [
            label('{tb.label}', color='{tb.color}', scale=0.9), sp(w='fill'), label(' {tb.n} ', color='#FFFFFF', bg='#B3261E', **{'if': 'tb.hasN'})]}])
    tab.pop('text')
    tabs = {'type': 'row', 'w': 'fill', 'gap': 4, 'children': [tab]}
    meter = {'type': 'column', 'gap': 0, 'cross': 'center', 'repeat': {'for': 'vm.meters', 'as': 'mt', 'limit': 4}, 'children': [
        label('{mt.label}', color='#D9C49A', scale=0.75), label('{mt.val}', color='{mt.color}', scale=1.3)]}
    meter['bg'] = '#3A2412'; meter['border'] = '#7A5F2E'; meter['pad'] = [3, 8, 3, 8]
    close = btn('X', id='close', w=28, bg='#B3261E', border='#7A1812', hover='#CF352C', close=True)
    head = {'type': 'row', 'w': 'fill', 'bg': HEAD, 'border': LN, 'pad': [6, 10, 6, 10], 'gap': 14, 'cross': 'center', 'children': [
        label('{vm.title}', color=CREAM, scale=1.4), label('{vm.sub}', color='#D9C49A', w='fill'), {'type': 'row', 'gap': 14, 'children': [meter]}, close]}
    foot = {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'children': [
        {'type': 'row', 'gap': 8, 'cross': 'center', 'if': 'vm.pager', 'children': [
            ab('<', '@vm.pgAct', v="'-1'", w=28, enabled='vm.pgPrev'), label('{vm.pgText}', color=CREAM),
            ab('>', '@vm.pgAct', v="'1'", w=28, enabled='vm.pgNext')]},
        ab('{vm.backLabel}', 'tab', v='vm.backTab', w=150, **{'if': 'vm.back'}), sp(w='fill'),
        ab('REFRESH', 'refresh', w=80), btn('CLOSE', id='close2', w=70, close=True)]}
    body = {'type': 'column', 'w': 'fill', 'h': 'fill', 'children': [
        v_mine(), v_queue(), v_ticket(), v_reports(), v_sugs(), v_sug(), v_psug(), v_prep(), v_punish(), v_logs(),
        label('Connecting to staff services...', color=MU, **{'if': 'vm.connecting'})]}
    tick = {'type': 'spacer', 'id': 'tick', 'w': 0, 'h': 0, 'every_ms': 25000, 'args': dict(ARGS),
            'argv': {'a': "'refresh'", 'id': '0', 'v': "''", 't': "''", 'n': "''", 'd': "''"}, 'msg': dict(MSG)}
    tock = {'type': 'spacer', 'id': 'tock', 'w': 0, 'h': 0, 'every_ms': 1000, 'args': dict(ARGS),
            'argv': {'a': "'tick'", 'id': '0', 'v': "''", 't': "''", 'n': "''", 'd': "''"}, 'msg': dict(MSG)}
    body = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': DESK, 'border': '#7A5F2E', 'pad': [10, 10, 10, 10], 'children': body['children']}
    main = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': SCR, 'border': LN, 'pad': [10, 10, 10, 10], 'gap': 6, 'children': [head, tabs, body, foot, tick, tock]}
    stamp = {'type': 'column', 'w': 'fill', 'h': 'fill', 'if': 'vm.stamp', 'cross': 'center', 'pad': [200, 0, 0, 0], 'children': [
        {'type': 'column', 'bg': '#FFFDF4', 'border': WARN, 'pad': [14, 40, 14, 40], 'cross': 'center', 'gap': 2, 'anim': 'pop', 'anim_ms': 420, 'children': [
            label('{vm.stampText}', color=WARN, scale=3.0), label('{vm.stampSub}', color=MU)]}]}
    root = {'type': 'stack', 'w': 'fill', 'h': 'fill', 'children': [main, stamp]}
    return {'v': 2, 'id': 'cases', 'channel': 'snobblestaff', 'hello': 'ready', 'bye': True, 'title': 'Regions Cases', 'width': 1000, 'height': 620, 'root': root}


d = page()
with open(os.path.join(OUT, 'cases.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, indent=1)
print('wrote cases')
