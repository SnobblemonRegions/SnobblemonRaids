/* Regions Cases page. It only draws what the server sends and reports what the player clicked;
   every rule and permission is enforced again on the server. */
(function () {
  'use strict';
  var CH = 'snobblestaff';
  var $ = function (id) { return document.getElementById(id); };

  var S = {
    st: null,            // last state from the server
    skew: 0,             // server time minus local time
    q: { open: true, progress: true, closed: false, category: '', mineOnly: false, unclaimed: false, sort: 'oldest', text: '', page: 0 },
    draft: {},           // text typed into boxes, kept across redraws
    newMode: false,      // "mine" view: writing a new ticket
    detected: null,      // category the server guessed for the new ticket text
    reportsClosed: false,
    logKind: '',
    first: true,
    zoom: 100,           // window size in percent
    newSug: false,       // "my suggestions" view: writing a new one
    sq: { status: '', text: '', page: 0 }   // staff suggestion list filter
  };
  try { var z = parseInt(window.localStorage.getItem('snobblestaff.zoom'), 10); if (z >= 60 && z <= 160) S.zoom = z; } catch (e) { /* storage may be blocked */ }
  var ROT = [-1.2, .8, -.6, .9, -.8, 1];

  // ------------------------------------------------------------ plumbing
  function send(o) {
    o.channel = CH;
    if (window.webgui && typeof window.webgui.postToGame === 'function') window.webgui.postToGame(o);
    else console.log('[cases] no webgui bridge', o);
  }
  function norm(x) {
    if (x && x.detail !== undefined) x = x.detail;
    if (typeof x === 'string') { try { x = JSON.parse(x); } catch (e) { return null; } }
    if (x && typeof x === 'object' && x.payload && !x.view && !x.message && !x.category) {
      var p = x.payload; if (typeof p === 'string') { try { p = JSON.parse(p); } catch (e) { return null; } } x = p;
    }
    return x;
  }
  window.addEventListener('webgui:state', function (e) { var d = norm(e); if (d) onState(d); });
  window.addEventListener('webgui:result', function (e) { var d = norm(e); if (d) onResult(d); });
  window.addEventListener('webgui:preview', function (e) { var d = norm(e); if (d) onPreview(d); });
  window.addEventListener('webgui:close', function () { closeGui(); });

  // The window is a fixed 1500x837 box in the middle of a see-through page; the zoom buttons scale it.
  function fit() {
    var s = Math.min(window.innerWidth / 1700, window.innerHeight / 960) * (S.zoom / 100);
    $('win').style.transform = 'translate(-50%,-50%) scale(' + s + ')';
    var zl = $('zoomlbl'); if (zl) zl.textContent = S.zoom + '%';
  }
  function setZoom(z) {
    S.zoom = Math.max(60, Math.min(160, z));
    try { window.localStorage.setItem('snobblestaff.zoom', String(S.zoom)); } catch (e) { /* storage may be blocked */ }
    fit();
  }
  window.addEventListener('resize', fit); fit();

  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function now() { return Date.now() + S.skew; }
  function ago(ms) {
    var m = Math.max(0, Math.floor((now() - ms) / 60000));
    if (m < 1) return 'just now';
    if (m < 60) return m + 'm ago';
    if (m < 1440) return Math.floor(m / 60) + 'h ' + (m % 60) + 'm ago';
    return Math.floor(m / 1440) + 'd ' + Math.floor((m % 1440) / 60) + 'h ago';
  }
  var MON = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  function when(ms) {
    var d = new Date(ms); var h = d.getHours(); var ap = h >= 12 ? 'PM' : 'AM'; h = h % 12 || 12;
    return MON[d.getMonth()] + ' ' + d.getDate() + ', ' + h + ':' + ('0' + d.getMinutes()).slice(-2) + ' ' + ap;
  }
  function dur(ms) {
    var m = Math.max(0, Math.round(ms / 60000));
    if (m >= 1440) return Math.floor(m / 1440) + 'd ' + Math.floor((m % 1440) / 60) + 'h';
    if (m >= 60) return Math.floor(m / 60) + 'h ' + (m % 60) + 'm';
    return m + 'm';
  }
  function statusInfo(t) {
    if (t.status === 'CLOSED') return ['s-closed', 'CLOSED'];
    if (t.urgent) return ['s-urg', 'URGENT'];
    if (t.status === 'IN_PROGRESS') return ['s-prog', 'IN PROGRESS'];
    return ['s-open', 'OPEN'];
  }
  function catTag(t) { return '<span class="cat cat-' + esc(t.category) + '">' + esc(t.categoryName).toUpperCase() + '</span>'; }

  var toastTimer = 0;
  function toast(msg, ok) {
    var t = $('toast'); t.textContent = msg; t.className = ok ? '' : 'bad'; t.hidden = false;
    t.style.animation = 'none'; void t.offsetWidth; t.style.animation = '';
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { t.hidden = true; }, 3200);
  }

  // ------------------------------------------------------------ server messages
  function onState(d) {
    if (d.now) S.skew = d.now - Date.now();
    S.st = d;
    render();
  }
  function onResult(d) {
    toast(d.message || (d.ok ? 'Done.' : 'That did not work.'), d.ok);
    if (d.ok && d.action === 'create') { S.newMode = false; S.draft.newText = ''; S.detected = null; S.draft.newCat = ''; }
    if (d.ok && d.action === 'screate') { S.newSug = false; S.draft.sugTitle = ''; S.draft.sugText = ''; }
    if (d.ok && d.action === 'rcreate') { S.draft.repName = ''; S.draft.repText = ''; }
    if (d.ok && (d.action === 'preply' || d.action === 'sreply' || d.action === 'sgpreply' || d.action === 'sgsreply')) S.draft.reply = '';
    if (d.ok && d.action === 'punish') S.draft.preason = '';
  }
  function onPreview(d) { S.detected = d; var el = $('detected'); if (el) el.innerHTML = detectedHtml(); }

  function detectedHtml() {
    if (!S.detected) return '<span class="dim">Type your message and I will pick a category.</span>';
    return 'Category: <b class="cat cat-' + esc(S.detected.category) + '">' + esc(S.detected.name).toUpperCase() + '</b>';
  }

  // ------------------------------------------------------------ requests
  function ask(view, extra) {
    var o = extra || {}; o.action = view; send(o);
  }
  function queueReq() {
    var q = S.q;
    send({ action: 'queue', open: q.open, progress: q.progress, closed: q.closed, category: q.category, mineOnly: q.mineOnly, unclaimed: q.unclaimed, sort: q.sort, text: q.text, page: q.page });
  }
  function refresh() {
    var d = S.st; if (!d) return;
    switch (d.view) {
      case 'queue': queueReq(); break;
      case 'ticket': send({ action: 'ticket', id: d.ticket.id }); break;
      case 'reports': send({ action: 'reports', closed: S.reportsClosed }); break;
      case 'sugs': sugsReq(); break;
      case 'sug': send({ action: 'sug', id: d.suggestion.id }); break;
      case 'psug': send({ action: 'psug', id: d.selected ? d.selected.id : 0 }); break;
      case 'prep': send({ action: 'prep' }); break;
      case 'punish': send({ action: 'player', name: d.query || '' }); break;
      case 'logs': send({ action: 'logs', kind: S.logKind }); break;
      default: send({ action: 'mine', id: d.selected ? d.selected.id : 0 });
    }
  }
  function typing() {
    var a = document.activeElement;
    return a && (a.tagName === 'TEXTAREA' || a.tagName === 'INPUT') && a.value;
  }
  setInterval(function () { if (!typing()) refresh(); }, 25000);

  // ------------------------------------------------------------ shell
  function shell(d, subtitle, activeTab, body, pager) {
    var staff = d.staff, c = d.counts || {};
    var tabs = '';
    if (staff) {
      tabs += tab('queue', 'HELP TICKETS', activeTab === 'queue', c.open, 'help') + tab('sugs', 'SUGGESTIONS', activeTab === 'sugs', c.sugs, 'sug') +
        tab('reports', 'REPORTS', activeTab === 'reports', c.reports, 'rep') +
        tab('punish', 'PUNISHMENTS', activeTab === 'punish') + (d.canLogs ? tab('logs', 'LOGS', activeTab === 'logs') : '') +
        tab('mine', 'MY TICKETS', activeTab === 'mine');
    } else {
      tabs += tab('mine', 'HELP TICKETS', activeTab === 'mine', c.myHelpUnread, 'help') + tab('psug', 'SUGGESTIONS', activeTab === 'psug', c.mySugUnread, 'sug') +
        (d.canReport === false ? '' : tab('prep', 'REPORTS', activeTab === 'prep', c.myReportsLive, 'rep'));
    }
    var meters = staff ?
      '<div class="meter"><label>HELP OPEN</label><b class="hot">' + (c.open || 0) + '</b></div><div class="meter"><label>NEW IDEAS</label><b>' + (c.sugs || 0) + '</b></div>' +
      '<div class="meter"><label>REPORTS</label><b class="hot">' + (c.reports || 0) + '</b></div><div class="meter"><label>STAFF ON DUTY</label><b>' + (c.staffOnline || 0) + '</b></div>' :
      '<div class="meter"><label>YOUR OPEN HELP TICKETS</label><b>' + (d.openCount != null ? d.openCount : '-') + (d.maxOpen ? ' / ' + d.maxOpen : '') + '</b></div>';
    var zoom = '<div id="zoom"><button data-act="zoom" data-d="-10" title="Smaller">-</button><span id="zoomlbl">' + S.zoom + '%</span><button data-act="zoom" data-d="10" title="Bigger">+</button><button class="x" data-act="close" title="Close">x</button></div>';
    return '<div id="logo">R</div><div id="title">' + esc((d.title || 'Regions Cases').toUpperCase()) + '</div><div id="sub">' + subtitle + '</div>' +
      '<div id="meters">' + meters + '</div>' + zoom + '<div id="tabs">' + tabs + '</div><div id="desk">' + body + '</div>' +
      '<div id="foot"><div id="pager">' + (pager || '') + '</div><div><button class="btn sm" data-act="refresh">REFRESH</button> <button class="btn sm" data-act="close">CLOSE</button></div></div>';
  }
  function tab(v, label, on, n, kind) {
    return '<button class="tab' + (kind ? ' k-' + kind : '') + (on ? ' on' : '') + '" data-act="tab" data-v="' + v + '">' + label + (n ? '<i>' + n + '</i>' : '') + '</button>';
  }

  function render() {
    var d = S.st; if (!d) return;
    var html;
    switch (d.view) {
      case 'queue': html = vQueue(d); break;
      case 'ticket': html = vTicket(d); break;
      case 'reports': html = vReports(d); break;
      case 'sugs': html = vSugs(d); break;
      case 'sug': html = vSug(d); break;
      case 'psug': html = vMySug(d); break;
      case 'prep': html = vMyRep(d); break;
      case 'punish': html = vPunish(d); break;
      case 'logs': html = vLogs(d); break;
      default: html = vMine(d);
    }
    var keep = document.activeElement ? document.activeElement.id : '';
    // The small entrance effects play only when the page changes, not on every refresh.
    var changed = S.lastView !== d.view; S.lastView = d.view;
    $('view').className = changed ? 'enter' : '';
    $('view').innerHTML = html;
    restoreDrafts();
    var msgs = document.querySelector('.msgs'); if (msgs) msgs.scrollTop = msgs.scrollHeight;
    if (keep && $(keep) && (keep.indexOf('f_') === 0)) { try { $(keep).focus(); var v = $(keep).value; $(keep).setSelectionRange(v.length, v.length); } catch (e) {} }
  }

  function restoreDrafts() {
    var els = document.querySelectorAll('[data-draft]');
    for (var i = 0; i < els.length; i++) {
      var k = els[i].getAttribute('data-draft');
      if (S.draft[k] !== undefined) els[i].value = S.draft[k];
    }
  }
  document.addEventListener('input', function (e) {
    var k = e.target && e.target.getAttribute && e.target.getAttribute('data-draft');
    if (!k) return;
    S.draft[k] = e.target.value;
    if (k === 'newText') schedulePreview(e.target.value);
    if (k === 'qtext') { /* search is applied with Enter */ }
  });
  var previewTimer = 0;
  function schedulePreview(text) {
    clearTimeout(previewTimer);
    previewTimer = setTimeout(function () { if (text.trim().length >= 3) send({ action: 'preview', text: text }); else { S.detected = null; var el = $('detected'); if (el) el.innerHTML = detectedHtml(); } }, 350);
  }

  // ------------------------------------------------------------ player view: my tickets
  function vMine(d) {
    var tickets = d.tickets || [], sel = d.selected;
    var list = '<button class="btn green wide" data-act="newticket" style="margin-bottom:14px"' + ((d.openCount >= d.maxOpen) ? ' disabled' : '') + '>+ NEW TICKET</button>';
    if (!tickets.length) list += '<div class="dim" style="font-size:15px;line-height:1.6">You have no tickets yet. Press NEW TICKET, or type <b>/ticket your message</b> in chat.</div>';
    tickets.forEach(function (t) {
      var si = statusInfo(t);
      list += '<button class="mini' + (!S.newMode && sel && sel.id === t.id ? ' on' : '') + '" data-act="selmine" data-id="' + t.id + '"><b>#' + t.id + '</b> ' +
        '<span class="tag ' + si[0] + '" style="float:right">' + si[1] + '</span><small>' + esc(t.categoryName) + ' - ' + ago(t.createdAt) + (t.unread ? ' - <b style="color:#b3261e">NEW REPLY</b>' : '') + '</small></button>';
    });
    var left = '<div class="paper" style="left:24px;top:24px;width:360px;height:616px;overflow-y:auto;background:#f7ecc8"><div class="h">YOUR TICKETS</div>' + list + '</div>';
    var main;
    if (S.newMode || !sel) main = newForm(d);
    else main = ticketPaper(d, sel, false);
    var sub = S.newMode || !sel ? 'OPEN A NEW CASE' : 'CASE #' + sel.id + ' - ' + esc(sel.categoryName);
    return shell(d, sub, 'mine', left + main, '');
  }

  function slaBox(d, t) {
    var from = t.createdAt + d.replyMin * 3600000, until = t.createdAt + d.replyMax * 3600000;
    if (t.status === 'CLOSED') return '<div class="sla">This ticket is <b>closed</b>' + (t.closedBy ? ' (by ' + esc(t.closedBy) + ')' : '') + '.</div>';
    if (!t.messages.some(function (m) { return m.staff && !m.system; }))
      return '<div class="sla">Opened <b>' + when(t.createdAt) + '</b>. Our staff will get to your ticket within <b>' + d.replyMin + '-' + d.replyMax + ' hours</b> of that time (between <b>' + when(from) + '</b> and <b>' + when(until) + '</b>).</div>';
    return '';
  }

  function ticketPaper(d, t, staff) {
    var msgs = '';
    t.messages.forEach(function (m) {
      if (m.system) { msgs += '<div class="msg sys">' + esc(m.text) + ' - ' + when(m.at) + '</div>'; return; }
      msgs += '<div class="msg ' + (m.staff ? 'st' : 'pl') + '"><small>' + (m.staff ? 'Staff ' : '') + esc(m.name) + ' - ' + ago(m.at) + '</small><span class="t">' + esc(m.text) + '</span></div>';
    });
    var closed = t.status === 'CLOSED';
    var act = staff ? 'sreply' : 'preply';
    var box = closed ? '<div class="reply"><div style="flex:1;text-align:center;color:#8a7a52;padding:20px">This ticket is closed.</div></div>' :
      '<div class="reply"><textarea class="fld" id="f_reply" data-draft="reply" maxlength="' + (d.maxLen || 500) + '" placeholder="Write a message..." autocomplete="off" spellcheck="false"></textarea>' +
      '<button class="btn" data-act="' + act + '" data-id="' + t.id + '" style="height:74px">SEND</button></div>';
    var top = (staff ? '' : slaBox(d, t));
    var hdr = '<div class="h">CORRESPONDENCE - CASE #' + t.id + '</div>';
    return '<div class="paper" style="left:408px;top:24px;width:' + (staff ? 880 : 1028) + 'px;height:616px">' + hdr +
      (top ? '<div style="position:absolute;left:24px;right:24px;top:44px">' + top + '</div>' : '') +
      '<div class="msgs" style="top:' + (top ? 132 : 46) + 'px">' + msgs + '</div>' + box + '</div>' +
      (staff ? '' : '<div style="position:absolute;right:24px;bottom:12px;width:0;height:0"></div>') +
      (!staff && !closed ? '<div style="position:absolute;left:408px;top:650px;width:300px"></div>' : '') +
      (!staff && !closed ? '<button class="btn alt sm" data-act="pclose" data-id="' + t.id + '" style="position:absolute;right:40px;top:30px">CLOSE MY TICKET</button>' : '');
  }

  function newForm(d) {
    var cur = S.draft.newCat || '';
    var cats = '<button class="chip' + (cur === '' ? ' on' : '') + '" data-act="newcat" data-v="">AUTO</button>' +
      (d.categories || []).map(function (c) { return '<button class="chip' + (cur === c.id ? ' on' : '') + '" data-act="newcat" data-v="' + esc(c.id) + '">' + esc(c.name.toUpperCase()) + '</button>'; }).join('');
    return '<div class="paper" style="left:408px;top:24px;width:1028px;height:616px"><div class="h">NEW CASE</div>' +
      '<div class="sla">Tell us what happened: what, where and when. Our staff will get to your ticket within <b>' + d.replyMin + '-' + d.replyMax + ' hours</b> of the time you open it.</div>' +
      '<textarea class="fld" id="f_new" data-draft="newText" maxlength="' + (d.maxLen || 500) + '" style="height:250px;font-size:18px" placeholder="Describe the problem..." autocomplete="off" spellcheck="false"></textarea>' +
      '<div style="display:flex;gap:10px;align-items:center;margin-top:16px;flex-wrap:wrap"><div id="detected" style="font-size:16px;margin-right:auto">' + detectedHtml() + '</div>' +
      '<label class="h" style="margin:0 6px 0 0">CATEGORY</label>' + cats + '</div>' +
      '<div style="margin-top:26px;display:flex;gap:14px"><button class="btn green" data-act="create" style="padding:14px 34px;font-size:17px">SUBMIT TICKET</button>' +
      (d.tickets && d.tickets.length ? '<button class="btn alt" data-act="cancelnew">CANCEL</button>' : '') + '</div>' +
      '<div class="dim" style="margin-top:20px;font-size:14px;color:#8a7a52">Your position is saved with the ticket so staff can find the place you mean. Staff can change the category if it was guessed wrong.</div></div>';
  }

  // ------------------------------------------------------------ staff: queue
  function vQueue(d) {
    var q = S.q;
    var cats = '<button class="chip' + (q.category === '' ? ' on' : '') + '" data-act="cat" data-v="">ALL</button>' +
      (d.categories || []).map(function (c) { return '<button class="chip' + (q.category === c.id ? ' on' : '') + '" data-act="cat" data-v="' + esc(c.id) + '">' + esc(c.name.toUpperCase()) + '</button>'; }).join('');
    var f = '<div id="filters">' +
      '<button class="chip' + (q.open ? ' on' : '') + '" data-act="flt" data-k="open">OPEN</button>' +
      '<button class="chip' + (q.progress ? ' on' : '') + '" data-act="flt" data-k="progress">IN PROGRESS</button>' +
      '<button class="chip' + (q.closed ? ' on' : '') + '" data-act="flt" data-k="closed">CLOSED</button><span class="sep"></span>' + cats + '<span class="sep"></span>' +
      '<button class="chip' + (q.mineOnly ? ' on' : '') + '" data-act="flt" data-k="mineOnly">MINE</button>' +
      '<button class="chip' + (q.unclaimed ? ' on' : '') + '" data-act="flt" data-k="unclaimed">UNCLAIMED</button></div>';
    var f2 = '<div style="position:absolute;left:24px;top:56px;right:24px;display:flex;gap:8px;align-items:center;height:30px">' +
      '<input class="fld" id="f_qtext" data-draft="qtext" placeholder="Search player or text, press Enter" style="height:30px;width:340px;font-size:14px" autocomplete="off" spellcheck="false">' +
      '<span class="sep"></span><span style="color:#7a5f2e;font-size:13px;font-weight:700;letter-spacing:1px">SORT</span>' +
      [['oldest', 'OLDEST'], ['newest', 'NEWEST'], ['urgent', 'URGENT FIRST']].map(function (s) { return '<button class="chip' + (q.sort === s[0] ? ' on' : '') + '" data-act="sort" data-v="' + s[0] + '" style="padding:3px 10px;font-size:13px">' + s[1] + '</button>'; }).join('') +
      '<span style="margin-left:auto;color:#7a5f2e;font-size:14px">' + d.total + ' case' + (d.total === 1 ? '' : 's') + '</span></div>';
    var grid = '<div class="grid" style="top:100px">';
    (d.items || []).forEach(function (t, i) {
      var si = statusInfo(t), c = i % 3, r = Math.floor(i / 3);
      var who = t.status === 'IN_PROGRESS' ? (t.mine ? 'yours' : esc(t.assignee)) : '';
      grid += '<button class="folder" data-act="openticket" data-id="' + t.id + '" style="left:' + (c * 460) + 'px;top:' + (r * 266 + 16) + 'px;--rot:' + ROT[i % 6] + 'deg;--i:' + i + '">' +
        '<div class="no">CASE #' + t.id + '</div><div class="clip"></div><div class="stamp ' + si[0] + '">' + si[1] + '</div>' +
        '<div class="who">' + esc(t.owner) + '</div>' + catTag(t) +
        '<div class="q hand">' + esc(t.preview) + '</div>' +
        '<div class="ft"><span>' + ago(t.createdAt) + (t.waiting && t.status !== 'CLOSED' ? ' <b style="color:#b3261e">- needs reply</b>' : '') + (who ? ' - ' + who : '') + '</span><span class="btn sm">OPEN FILE</span></div></button>';
    });
    grid += '</div>';
    if (!(d.items || []).length) grid = '<div class="empty">No cases match these filters.<br>The pile is clear.</div>';
    var pager = '<button data-act="pg" data-d="-1"' + (d.page <= 0 ? ' disabled' : '') + '>&lt; PREV PAGE</button><span>PAGE ' + (d.page + 1) + ' OF ' + d.pages + '</span><button data-act="pg" data-d="1"' + (d.page + 1 >= d.pages ? ' disabled' : '') + '>NEXT PAGE &gt;</button>';
    return shell(d, (d.counts.open || 0) + ' open - ' + (d.counts.progress || 0) + ' in progress - you hold ' + (d.counts.mine || 0) + ' of ' + d.maxActive, 'queue', f + f2 + grid, pager);
  }

  // ------------------------------------------------------------ staff: one ticket
  function vTicket(d) {
    var t = d.ticket, p = d.player, closed = t.status === 'CLOSED';
    var si = statusInfo(t);
    var cats = (d.categories || []).map(function (c) {
      return '<button class="chip' + (c.id === t.category ? ' on' : '') + '" data-act="setcat" data-id="' + t.id + '" data-v="' + esc(c.id) + '"' + (closed ? ' disabled' : '') + '>' + esc(c.name.toUpperCase()) + '</button>';
    }).join('');
    var holder = t.assignee ? (t.mine ? 'You' : esc(t.assignee)) : 'Nobody yet';
    var client = '<div class="paper" style="left:928px;top:24px;width:508px;height:228px;transform:rotate(.5deg)"><div class="h">CLIENT</div>' +
      '<div style="font-size:24px;font-weight:700">' + esc(p.name) + (p.online ? ' <span style="font-size:13px;color:#2f7d3a">ONLINE</span>' : ' <span style="font-size:13px;color:#8a7a52">OFFLINE</span>') + '</div>' +
      '<div class="kv"><span>Opened at</span> ' + esc(p.openedAt) + '<br>' + (p.online ? '<span>Now at</span> ' + esc(p.world) + ' ' + p.x + ', ' + p.y + ', ' + p.z + '<br>' : '') +
      '<span>Past cases</span> ' + p.pastTickets + ' &nbsp; <span>Warnings</span> ' + p.warnings + ' &nbsp; <span>Mutes</span> ' + p.mutes + '</div>' +
      '<div class="stamp ' + si[0] + '" style="top:18px;right:18px">' + si[1] + '</div></div>';
    var claimBtn = closed ? '' : (t.mine ? '<button class="btn alt" data-act="release" data-id="' + t.id + '">RELEASE</button>' : '<button class="btn green" data-act="claim" data-id="' + t.id + '">' + (t.assignee ? 'TAKE OVER' : 'CLAIM') + '</button>');
    var tp = '';
    if (d.canTp) tp += '<button class="btn alt" data-act="tpto" data-name="' + esc(p.name) + '" data-world="' + esc(t.world || '') + '">' + (p.online ? 'TELEPORT TO' : 'GO TO SPOT') + '</button>';
    var inv = '';
    if (d.canInvsee && p.online) inv += '<button class="btn alt" data-act="inv" data-k="invsee" data-name="' + esc(p.name) + '">INVENTORY</button>';
    if (d.canEnder && p.online) inv += '<button class="btn alt" data-act="inv" data-k="endersee" data-name="' + esc(p.name) + '">ENDER CHEST</button>';
    var actions = '<div style="position:absolute;left:928px;top:276px;width:508px;display:grid;grid-template-columns:1fr 1fr;gap:12px">' +
      claimBtn + (closed ? '' : '<button class="btn alt' + (t.urgent ? ' on' : '') + '" data-act="urgent" data-id="' + t.id + '" data-on="' + (!t.urgent) + '">' + (t.urgent ? 'NOT URGENT' : 'SET URGENT') + '</button>') +
      tp + inv +
      '<button class="btn alt" data-act="history" data-name="' + esc(p.name) + '">HISTORY</button><button class="btn red" data-act="history" data-name="' + esc(p.name) + '">PUNISH</button></div>' +
      '<div style="position:absolute;left:928px;top:510px;width:508px"><div class="h">CATEGORY (CLICK TO CHANGE) - HELD BY: ' + holder.toUpperCase() + '</div>' +
      '<div style="display:flex;gap:6px;flex-wrap:wrap">' + cats + '</div></div>' +
      (closed ? '' : '<div style="position:absolute;left:928px;top:588px;width:508px"><button class="btn green wide" data-act="sclose" data-id="' + t.id + '" style="padding:16px;font-size:19px">STAMP CLOSED</button></div>');
    var paper = ticketPaper(d, t, true);
    paper = paper.replace('left:408px;top:24px', 'left:24px;top:24px');
    var pager = '<button data-act="tab" data-v="queue">&lt; BACK TO PILE</button>';
    return shell(d, 'CASE #' + t.id + ' - ' + esc(t.owner) + ' - ' + esc(t.categoryName), 'queue', paper + client + actions, pager);
  }

  // ------------------------------------------------------------ staff: reports
  function vReports(d) {
    var rows = '';
    (d.reports || []).forEach(function (r) {
      var st = r.status === 'CLOSED' ? '<span class="tag s-closed">CLOSED</span>' : r.status === 'CLAIMED' ? '<span class="tag s-prog">' + esc(r.handler).toUpperCase() + '</span>' : '<span class="tag s-open">OPEN</span>';
      rows += '<div class="row"><div style="width:96px">' + st + '</div><div class="r"><b>' + esc(r.target) + '</b> <span style="color:#7a5f2e">reported by ' + esc(r.reporter) + '</span><br><span class="hand">' + esc(r.reason) + '</span>' + (r.note ? '<br><small style="color:#2f7d3a">Note: ' + esc(r.note) + '</small>' : '') + '</div>' +
        '<div class="d">' + ago(r.createdAt) + '<br><small>' + esc(r.where) + '</small></div><div style="display:flex;gap:8px">' +
        (r.status !== 'CLOSED' ? (r.status === 'OPEN' ? '<button class="btn sm green" data-act="rclaim" data-id="' + r.id + '">CLAIM</button>' : '') + '<button class="btn sm alt" data-act="rclose" data-id="' + r.id + '">CLOSE</button>' : '') +
        (d.canTp && r.targetOnline ? '<button class="btn sm alt" data-act="tpto" data-name="' + esc(r.target) + '">TP</button>' : '') +
        '<button class="btn sm red" data-act="history" data-name="' + esc(r.target) + '">PUNISH</button></div></div>';
    });
    if (!rows) rows = '<div class="empty" style="position:static;padding:40px">No reports ' + (d.showClosed ? '' : 'waiting. ') + '</div>';
    var head = '<div style="position:absolute;left:24px;top:14px;right:24px;display:flex;gap:10px;align-items:center"><div class="h" style="margin:0">PLAYER REPORTS</div><span style="margin-left:auto"></span>' +
      '<button class="chip' + (!d.showClosed ? ' on' : '') + '" data-act="rep" data-closed="false">OPEN</button><button class="chip' + (d.showClosed ? ' on' : '') + '" data-act="rep" data-closed="true">CLOSED</button></div>';
    return shell(d, (d.counts.reports || 0) + ' open report' + (d.counts.reports === 1 ? '' : 's'), 'reports', head + '<div style="position:absolute;left:24px;right:24px;top:56px;bottom:14px;overflow-y:auto">' + rows + '</div>', '');
  }

  // ------------------------------------------------------------ staff: punishments
  var PTYPES = [['warn', 'Warn', 'canWarn'], ['note', 'Staff note', 'canWarn'], ['mute', 'Mute', 'canMute'], ['tempmute', 'Temp mute', 'canMute'], ['kick', 'Kick', 'canKick'], ['tempban', 'Temp ban', 'canBan'], ['ban', 'Ban', 'canBan']];
  function vPunish(d) {
    var p = d.player;
    var search = '<div style="position:absolute;left:24px;top:14px;right:24px;display:flex;gap:10px;align-items:center"><div class="h" style="margin:0">FIND PLAYER</div>' +
      '<input class="fld" id="f_pname" data-draft="pname" placeholder="Player name, press Enter" style="width:300px" autocomplete="off" spellcheck="false"><button class="btn sm" data-act="lookup">SEARCH</button></div>';
    var body = '';
    if (p) {
      var stats = '<div class="paper" style="left:24px;top:64px;width:1412px;height:140px;padding:16px 24px"><div style="display:flex;gap:30px;align-items:center">' +
        '<div style="width:70px;height:70px;background:#c9b078;border:3px solid #8a6a28;display:grid;place-items:center;font-size:34px;font-weight:700">' + esc(p.name.charAt(0).toUpperCase()) + '</div>' +
        '<div><div style="font-size:28px;font-weight:700">' + esc(p.name) + '</div><div style="color:#7a5f2e;font-size:15px">' + (p.online ? '<b style="color:#2f7d3a">ONLINE</b>' : 'last seen ' + ago(p.lastSeen)) + (p.firstJoin ? ' - first joined ' + when(p.firstJoin) : '') + '</div>' +
        '<div style="margin-top:8px">' + (p.banned ? '<span class="tag s-urg">BANNED - ' + esc(p.banInfo) + '</span> ' : '') + (p.muted ? '<span class="tag s-prog">MUTED - ' + esc(p.muteInfo) + '</span> ' : '') + (p.frozen ? '<span class="tag s-prog">FROZEN</span>' : '') + '</div></div>' +
        '<div style="margin-left:auto;display:flex;gap:34px;text-align:center"><div><div style="font-size:32px;font-weight:700;color:#b87500">' + p.warnings + '</div><small>WARNINGS</small></div><div><div style="font-size:32px;font-weight:700;color:#b3261e">' + p.mutes + '</div><small>MUTES</small></div><div><div style="font-size:32px;font-weight:700">' + p.bans + '</div><small>BANS</small></div><div><div style="font-size:32px;font-weight:700">' + p.kicks + '</div><small>KICKS</small></div></div></div></div>';
      var hist = '<div class="paper" style="left:24px;top:224px;width:860px;height:414px;padding:16px 20px"><div class="h">RECORD</div><div style="position:absolute;left:20px;right:20px;top:42px;bottom:10px;overflow-y:auto">';
      (p.history || []).forEach(function (h) {
        var cls = h.type === 'BAN' ? 's-urg' : h.type === 'MUTE' ? 's-prog' : h.type === 'WARN' ? 's-prog' : 's-closed';
        var len = h.until ? ' until ' + when(h.until) : (h.type === 'BAN' || h.type === 'MUTE' ? ' (permanent)' : '');
        hist += '<div class="row" style="min-height:54px"><span class="tag ' + cls + '" style="width:74px;text-align:center">' + h.type + '</span><div class="r"><span class="hand">' + esc(h.reason) + '</span><br><small style="color:#7a5f2e">' + esc(h.by) + ' - ' + when(h.at) + len + (h.lifted ? ' - lifted by ' + esc(h.liftedBy) : '') + '</small></div>' +
          (h.inForce ? '<button class="btn sm alt" data-act="lift" data-k="' + (h.type === 'BAN' ? 'ban' : 'mute') + '" data-name="' + esc(p.name) + '">LIFT</button>' : '') + '</div>';
      });
      if (!(p.history || []).length) hist += '<div style="padding:30px;color:#8a7a52">Clean record.</div>';
      hist += '</div></div>';
      var allowed = PTYPES.filter(function (t) { return d[t[2]]; });
      var ptype = S.draft.ptype;
      if (!allowed.some(function (t) { return t[0] === ptype; })) ptype = allowed.length ? allowed[0][0] : '';
      var opts = allowed.map(function (t) { return '<button class="chip' + (t[0] === ptype ? ' on' : '') + '" data-act="ptype" data-v="' + t[0] + '" style="padding:4px 10px;font-size:13px">' + t[1].toUpperCase() + '</button>'; }).join('');
      var temp = ptype.indexOf('temp') === 0;
      var form = '<div class="paper" style="left:908px;top:224px;width:528px;height:414px"><div class="h">NEW ENTRY - PICK A TYPE</div>' +
        '<div style="display:flex;gap:6px;flex-wrap:wrap">' + opts + '</div>' +
        (temp ? '<input class="fld" id="f_pdur" data-draft="pdur" placeholder="Length, e.g. 30m, 2h, 7d, 1w" style="width:100%;margin-top:12px" autocomplete="off" spellcheck="false">' : '') +
        '<textarea class="fld" id="f_preason" data-draft="preason" style="height:' + (temp ? 140 : 190) + 'px;margin-top:12px" maxlength="240" placeholder="Reason (the player sees it)" autocomplete="off" spellcheck="false"></textarea>' +
        '<button class="btn red wide" data-act="punish" data-type="' + ptype + '" data-name="' + esc(p.name) + '" style="margin-top:16px;padding:14px">APPLY ' + esc(ptype.toUpperCase()) + '</button>' +
        '<div style="margin-top:12px;font-size:13px;color:#8a7a52">Staff protected by the exempt permission cannot be punished.</div></div>';
      body = stats + hist + form;
    } else if (d.matches && d.matches.length) {
      body = '<div class="paper" style="left:24px;top:64px;width:700px;height:300px"><div class="h">DID YOU MEAN</div>' + d.matches.map(function (n) { return '<button class="mini" data-act="history" data-name="' + esc(n) + '"><b>' + esc(n) + '</b></button>'; }).join('') + '</div>';
    } else {
      body = '<div class="empty" style="top:90px">' + (d.query ? 'No player called "' + esc(d.query) + '" has joined this server.' : 'Type a player name to see their record, then warn, mute, kick or ban them.') + '</div>';
    }
    return shell(d, 'PLAYER RECORDS', 'punish', search + body, '');
  }

  // ------------------------------------------------------------ staff: logs
  function vLogs(d) {
    var kinds = [['', 'ALL'], ['STAFF', 'STAFF'], ['TICKET', 'TICKETS'], ['CHAT', 'CHAT'], ['CMD', 'COMMANDS']];
    var head = '<div style="position:absolute;left:24px;top:14px;right:24px;display:flex;gap:8px;align-items:center"><div class="h" style="margin:0;margin-right:14px">AUDIT LOG</div>' +
      kinds.map(function (k) { return '<button class="chip' + (d.kind === k[0] ? ' on' : '') + '" data-act="logk" data-k="' + k[0] + '">' + k[1] + '</button>'; }).join('') + '</div>';
    var rows = '';
    (d.entries || []).forEach(function (e) {
      var cls = e.kind === 'STAFF' ? 's-urg' : e.kind === 'TICKET' ? 's-open' : e.kind === 'CHAT' ? 's-closed' : 's-prog';
      rows += '<div class="row" style="min-height:38px;padding:4px 14px"><span class="d" style="width:100px;text-align:left">' + when(e.at).replace(/^[A-Za-z]+ \d+, /, '') + '</span><span class="tag ' + cls + '" style="width:76px;text-align:center">' + e.kind + '</span><b style="width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' + esc(e.actor) + '</b><span class="r" style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;user-select:text">' + esc(e.text) + '</span></div>';
    });
    if (!rows) rows = '<div class="empty" style="position:static;padding:40px">Nothing logged yet.</div>';
    return shell(d, 'NEWEST ENTRIES FIRST - FULL LOGS ARE FILES IN config/snobble_staff/audit', 'logs', head + '<div style="position:absolute;left:24px;right:24px;top:56px;bottom:14px;overflow-y:auto">' + rows + '</div>', '');
  }

  // ------------------------------------------------------------ suggestions
  var SUG_STATUS = [['NEW', 'NEW'], ['CONSIDERING', 'CONSIDERING'], ['PLANNED', 'PLANNED'], ['DONE', 'DONE'], ['DECLINED', 'DECLINED']];
  var SUG_CLS = { NEW: 'new', CONSIDERING: 'con', PLANNED: 'plan', DONE: 'done', DECLINED: 'dec' };
  function sugTag(s) { return '<span class="stg ' + SUG_CLS[s.status] + '">' + esc(s.statusName).toUpperCase() + '</span>'; }
  function sugsReq() { send({ action: 'sugs', status: S.sq.status, text: S.sq.text, page: S.sq.page }); }

  function sugPaper(d, s, staff, left, width) {
    var msgs = '';
    s.messages.forEach(function (m) {
      if (m.system) { msgs += '<div class="msg sys">' + esc(m.text) + ' - ' + when(m.at) + '</div>'; return; }
      msgs += '<div class="msg ' + (m.staff ? 'st' : 'pl') + '"><small>' + (m.staff ? 'Staff ' : '') + esc(m.name) + ' - ' + ago(m.at) + '</small><span class="t">' + esc(m.text) + '</span></div>';
    });
    var canWrite = staff || s.open;
    var act = staff ? 'sgsreply' : 'sgpreply';
    var box = canWrite ?
      '<div class="reply"><textarea class="fld" id="f_reply" data-draft="reply" maxlength="' + (d.maxLen || 500) + '" placeholder="' + (staff ? 'Write a reply to the player...' : 'Add more details...') + '" autocomplete="off" spellcheck="false"></textarea>' +
      '<button class="btn" data-act="' + act + '" data-id="' + s.id + '" style="height:74px">SEND</button></div>' :
      '<div class="reply"><div style="flex:1;text-align:center;color:#8a7a52;padding:20px">Staff marked this <b>' + esc(s.statusName.toUpperCase()) + '</b>, so it is closed for new messages.</div></div>';
    var hint = !staff ? '<div class="sla" style="position:absolute;left:24px;right:24px;top:44px">Staff will read your idea and set a status: <b>Considering</b>, <b>Planned</b>, <b>Done</b> or <b>Declined</b>. You get a message in chat when it changes.</div>' : '';
    return '<div class="paper" style="left:' + left + 'px;top:24px;width:' + width + 'px;height:616px"><div class="h">' + esc(s.title.toUpperCase()) + ' - IDEA #' + s.id + '</div>' +
      '<div style="position:absolute;right:24px;top:16px">' + sugTag(s) + '</div>' + hint +
      '<div class="msgs" style="top:' + (hint ? 130 : 46) + 'px">' + msgs + '</div>' + box + '</div>';
  }

  function vMySug(d) {
    var list = d.suggestions || [], sel = d.selected;
    var left = '<div class="paper" style="left:24px;top:24px;width:360px;height:616px;overflow-y:auto;background:#f7ecc8"><div class="h">YOUR SUGGESTIONS</div>' +
      '<button class="btn blue wide" data-act="newsug" style="margin-bottom:14px">+ NEW SUGGESTION</button>';
    if (!list.length) left += '<div class="dim" style="font-size:15px;line-height:1.6">You have not sent an idea yet. Press NEW SUGGESTION and tell us what would make the server better.</div>';
    list.forEach(function (s) {
      left += '<button class="mini note-' + SUG_CLS[s.status] + (!S.newSug && sel && sel.id === s.id ? ' on' : '') + '" data-act="selsug" data-id="' + s.id + '"><b>' + esc(s.title) + '</b>' +
        sugTag(s) + '<small>#' + s.id + ' - ' + ago(s.createdAt) + (s.unread ? ' - <b style="color:#b3261e">NEW REPLY</b>' : '') + '</small></button>';
    });
    left += '</div>';
    var main;
    if (S.newSug || !sel) {
      main = '<div class="paper" style="left:408px;top:24px;width:1028px;height:616px"><div class="h">NEW SUGGESTION</div>' +
        '<div class="sla">Share an idea for the server. Staff read every one and tell you what they decide. Please keep it to one idea per suggestion.</div>' +
        '<div class="h" style="margin-top:6px">SHORT TITLE (OPTIONAL)</div>' +
        '<input class="fld" id="f_stitle" data-draft="sugTitle" maxlength="60" placeholder="Example: Sell Great Balls at the Pokecenter" style="width:100%" autocomplete="off" spellcheck="false">' +
        '<div class="h" style="margin-top:14px">YOUR IDEA</div>' +
        '<textarea class="fld" id="f_stext" data-draft="sugText" maxlength="' + (d.maxLen || 500) + '" style="height:230px;font-size:18px" placeholder="Describe your idea and why it would help..." autocomplete="off" spellcheck="false"></textarea>' +
        '<div style="margin-top:22px;display:flex;gap:14px"><button class="btn blue" data-act="sugcreate" style="padding:14px 34px;font-size:17px">SEND SUGGESTION</button>' +
        (list.length ? '<button class="btn alt" data-act="cancelsug">CANCEL</button>' : '') + '</div></div>';
    } else {
      main = sugPaper(d, sel, false, 408, 1028);
    }
    var sub = S.newSug || !sel ? 'SHARE AN IDEA' : 'IDEA #' + sel.id + ' - ' + esc(sel.statusName);
    return shell(d, sub, 'psug', left + main, '');
  }

  function vMyRep(d) {
    var list = d.myReports || [];
    var left = '<div class="paper" style="left:24px;top:24px;width:560px;height:616px;overflow-y:auto;background:#f7ecc8"><div class="h">YOUR REPORTS</div>';
    if (!list.length) left += '<div class="dim" style="font-size:15px;line-height:1.6">You have not reported anyone. Use the form to tell staff about a player who broke a rule.</div>';
    list.forEach(function (r) {
      var st = r.status === 'CLOSED' ? '<span class="tag s-closed">CLOSED</span>' : r.status === 'CLAIMED' ? '<span class="tag s-prog">STAFF LOOKING</span>' : '<span class="tag s-open">OPEN</span>';
      left += '<div class="slip"><span style="float:right">' + st + '</span><div class="no">REPORT #' + r.id + ' - ' + ago(r.createdAt) + '</div><div class="w">' + esc(r.target) + '</div><div class="hand rs">' + esc(r.reason) + '</div></div>';
    });
    left += '</div>';
    var online = (d.online || []).map(function (n) { return '<button class="chip" data-act="pickname" data-name="' + esc(n) + '"' + (S.draft.repName === n ? '' : '') + '>' + esc(n) + '</button>'; }).join('');
    var form = '<div class="paper" style="left:608px;top:24px;width:828px;height:616px"><div class="h">REPORT A PLAYER</div>' +
      '<div class="sla">Only report rule-breaking such as griefing, cheating, harassment or spam. Staff also see where you are standing when you send it. For problems that are not about a player, use <b>Help Tickets</b>.</div>' +
      '<div class="h" style="margin-top:6px">PLAYER NAME</div>' +
      '<input class="fld" id="f_rname" data-draft="repName" maxlength="32" placeholder="Type the name, or pick one below" style="width:100%" autocomplete="off" spellcheck="false">' +
      (online ? '<div class="h" style="margin-top:12px">ONLINE NOW</div><div style="display:flex;gap:6px;flex-wrap:wrap;max-height:76px;overflow:hidden">' + online + '</div>' : '') +
      '<div class="h" style="margin-top:14px">WHAT HAPPENED?</div>' +
      '<textarea class="fld" id="f_rtext" data-draft="repText" maxlength="' + (d.maxLen || 500) + '" style="height:' + (online ? 130 : 190) + 'px;font-size:17px" placeholder="What did they do, and when?" autocomplete="off" spellcheck="false"></textarea>' +
      '<div style="margin-top:18px"><button class="btn red" data-act="repcreate" style="padding:14px 34px;font-size:17px">SEND REPORT</button></div></div>';
    return shell(d, 'REPORT A PLAYER TO THE STAFF', 'prep', left + form, '');
  }

  function vSugs(d) {
    var q = S.sq;
    var chips = [['', 'ALL']].concat(SUG_STATUS).map(function (s) { return '<button class="chip' + (q.status === s[0] ? ' on' : '') + '" data-act="sugfilter" data-v="' + s[0] + '">' + s[1] + '</button>'; }).join('');
    var bar = '<div style="position:absolute;left:24px;top:14px;right:24px;display:flex;gap:8px;align-items:center;height:36px">' + chips + '<span class="sep"></span>' +
      '<input class="fld" id="f_sugtext" data-draft="sugsearch" placeholder="Search idea or player, press Enter" style="height:30px;width:340px;font-size:14px" autocomplete="off" spellcheck="false">' +
      '<span style="margin-left:auto;color:#7a5f2e;font-size:14px">' + d.total + ' suggestion' + (d.total === 1 ? '' : 's') + '</span></div>';
    var grid = '<div class="grid" style="top:70px">';
    (d.items || []).forEach(function (s, i) {
      var c = i % 3, r = Math.floor(i / 3);
      grid += '<button class="note n-' + SUG_CLS[s.status] + '" data-act="opensug" data-id="' + s.id + '" style="left:' + (c * 460) + 'px;top:' + (r * 280 + 16) + 'px;--rot:' + ROT[i % 6] + 'deg;--i:' + i + '">' +
        '<span class="pin"></span><div class="no">IDEA #' + s.id + '</div><div class="t">' + esc(s.title) + '</div><div class="q hand">' + esc(s.preview) + '</div>' +
        '<div class="ft"><span>' + esc(s.owner) + ' - ' + ago(s.createdAt) + '</span>' + sugTag(s) + '</div></button>';
    });
    grid += '</div>';
    if (!(d.items || []).length) grid = '<div class="empty">No suggestions here yet.<br>Ideas players send will be pinned up on this board.</div>';
    var pager = '<button data-act="sugpg" data-d="-1"' + (d.page <= 0 ? ' disabled' : '') + '>&lt; PREV PAGE</button><span>PAGE ' + (d.page + 1) + ' OF ' + d.pages + '</span><button data-act="sugpg" data-d="1"' + (d.page + 1 >= d.pages ? ' disabled' : '') + '>NEXT PAGE &gt;</button>';
    return shell(d, (d.counts.sugs || 0) + ' new - ' + (d.counts.reports || 0) + ' open reports - ' + (d.counts.open || 0) + ' help tickets open', 'sugs', bar + grid, pager);
  }

  function vSug(d) {
    var s = d.suggestion, p = d.player;
    var card = '<div class="paper" style="left:928px;top:24px;width:508px;height:170px;transform:rotate(.5deg)"><div class="h">SUGGESTED BY</div>' +
      '<div style="font-size:24px;font-weight:700">' + esc(p.name) + (p.online ? ' <span style="font-size:13px;color:#2f7d3a">ONLINE</span>' : ' <span style="font-size:13px;color:#8a7a52">OFFLINE</span>') + '</div>' +
      '<div class="kv"><span>Sent</span> ' + when(s.createdAt) + '<br><span>Other ideas by them</span> ' + p.otherSuggestions + (s.handledBy ? '<br><span>Last decision by</span> ' + esc(s.handledBy) : '') + '</div></div>';
    var chips = SUG_STATUS.map(function (x) {
      return '<button class="chip' + (s.status === x[0] ? ' on' : '') + '" data-act="sugset" data-id="' + s.id + '" data-v="' + x[0] + '">' + x[1] + '</button>';
    }).join('');
    var side = '<div style="position:absolute;left:928px;top:220px;width:508px"><div class="h">SET A DECISION (THE PLAYER IS TOLD IN CHAT)</div><div style="display:flex;gap:6px;flex-wrap:wrap">' + chips + '</div>' +
      '<div class="sla" style="margin-top:18px">Setting <b>Declined</b> or <b>Done</b> closes the idea for new player messages. Write a reply first so the player knows why.</div></div>';
    var paper = sugPaper(d, s, true, 24, 880);
    return shell(d, 'IDEA #' + s.id + ' - ' + esc(s.owner) + ' - ' + esc(s.statusName), 'sugs', paper + card + side,
      '<button data-act="tab" data-v="sugs">&lt; BACK TO BOARD</button>');
  }

  // ------------------------------------------------------------ clicks
  function closeGui() { if (window.webgui && typeof window.webgui.closeGui === 'function') window.webgui.closeGui(); }
  document.addEventListener('click', function (e) {
    var el = e.target; while (el && el !== document && !(el.getAttribute && el.getAttribute('data-act'))) el = el.parentNode;
    if (!el || el === document || el.disabled) return;
    var a = el.getAttribute('data-act'), id = parseInt(el.getAttribute('data-id') || '0', 10), v = el.getAttribute('data-v'), k = el.getAttribute('data-k'), name = el.getAttribute('data-name');
    switch (a) {
      case 'close': closeGui(); break;
      case 'zoom': setZoom(S.zoom + parseInt(el.getAttribute('data-d'), 10)); break;
      case 'refresh': refresh(); break;
      case 'newsug': S.newSug = true; render(); var st = $('f_stitle'); if (st) st.focus(); break;
      case 'cancelsug': S.newSug = false; render(); break;
      case 'selsug': S.newSug = false; send({ action: 'psug', id: id }); break;
      case 'sugcreate': {
        var stx = (S.draft.sugText || '').trim();
        if (stx.length < 5) { toast('Please describe your idea in a little more detail.', false); break; }
        send({ action: 'screate', title: (S.draft.sugTitle || '').trim(), text: stx }); break;
      }
      case 'sgpreply': case 'sgsreply': {
        var gt = (S.draft.reply || '').trim(); if (!gt) { toast('Write a message first.', false); break; }
        send({ action: a, id: id, text: gt, status: S.sq.status }); S.draft.reply = ''; break;
      }
      case 'opensug': send({ action: 'sug', id: id, status: S.sq.status }); break;
      case 'sugfilter': S.sq.status = v; S.sq.page = 0; sugsReq(); break;
      case 'sugpg': S.sq.page = Math.max(0, S.sq.page + parseInt(el.getAttribute('data-d'), 10)); sugsReq(); break;
      case 'sugset': send({ action: 'sgstatus', id: id, set: v, status: S.sq.status }); break;
      case 'pickname': S.draft.repName = name; var rn = $('f_rname'); if (rn) rn.value = name; break;
      case 'repcreate': {
        var rnm = (S.draft.repName || '').trim(), rtx = (S.draft.repText || '').trim();
        if (!rnm) { toast('Type the name of the player first.', false); break; }
        if (rtx.length < 3) { toast('Please say what happened.', false); break; }
        send({ action: 'rcreate', name: rnm, reason: rtx }); break;
      }
      case 'tab':
        if (v === 'queue') queueReq(); else if (v === 'reports') send({ action: 'reports', closed: false });
        else if (v === 'sugs') sugsReq();
        else if (v === 'psug') { S.newSug = false; send({ action: 'psug', id: 0 }); }
        else if (v === 'prep') send({ action: 'prep' });
        else if (v === 'punish') send({ action: 'player', name: '' }); else if (v === 'logs') send({ action: 'logs', kind: S.logKind });
        else { S.newMode = false; send({ action: 'mine', id: 0 }); }
        break;
      case 'newticket': S.newMode = true; render(); var ta = $('f_new'); if (ta) ta.focus(); break;
      case 'cancelnew': S.newMode = false; render(); break;
      case 'selmine': S.newMode = false; send({ action: 'mine', id: id }); break;
      case 'create': {
        var text = (S.draft.newText || '').trim();
        if (text.length < 5) { toast('Please describe the problem in a little more detail.', false); break; }
        send({ action: 'create', text: text, category: S.draft.newCat || '' }); break;
      }
      case 'preply': case 'sreply': {
        var rt = (S.draft.reply || '').trim(); if (!rt) { toast('Write a message first.', false); break; }
        send({ action: a, id: id, text: rt }); S.draft.reply = ''; break;
      }
      case 'pclose': send({ action: 'pclose', id: id }); break;
      case 'flt': S.q[k] = !S.q[k]; S.q.page = 0; queueReq(); break;
      case 'cat': S.q.category = v; S.q.page = 0; queueReq(); break;
      case 'pg': S.q.page = Math.max(0, S.q.page + parseInt(el.getAttribute('data-d'), 10)); queueReq(); break;
      case 'openticket': send({ action: 'ticket', id: id }); break;
      case 'claim': case 'release': case 'sclose': send({ action: a, id: id }); break;
      case 'urgent': send({ action: 'urgent', id: id, on: el.getAttribute('data-on') === 'true' }); break;
      // The server does the work and then sends a "close" event; the page never closes itself before the server answered.
      case 'tpto': {
        var st = S.st, o = { action: 'tpto', name: name };
        if (st && st.view === 'ticket') o.id = st.ticket.id;
        send(o); break;
      }
      case 'inv': send({ action: k, name: name }); break;
      case 'setcat': send({ action: 'setcat', id: id, category: v }); break;
      case 'newcat': S.draft.newCat = v; render(); break;
      case 'sort': S.q.sort = v; S.q.page = 0; queueReq(); break;
      case 'ptype': S.draft.ptype = v; render(); break;
      case 'history': S.draft.pname = name; send({ action: 'player', name: name }); break;
      case 'rep': S.reportsClosed = el.getAttribute('data-closed') === 'true'; send({ action: 'reports', closed: S.reportsClosed }); break;
      case 'rclaim': send({ action: 'rclaim', id: id, closed: S.reportsClosed }); break;
      case 'rclose': send({ action: 'rclose', id: id, closed: S.reportsClosed }); break;
      case 'lookup': send({ action: 'player', name: (S.draft.pname || '').trim() }); break;
      case 'lift': send({ action: 'lift', type: k, name: name }); break;
      case 'punish': {
        send({ action: 'punish', type: el.getAttribute('data-type'), name: name, reason: (S.draft.preason || '').trim(), duration: (S.draft.pdur || '').trim() });
        break;
      }
      case 'logk': S.logKind = k; send({ action: 'logs', kind: k }); break;
    }
  });
  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Enter' || e.shiftKey) return;
    var t = e.target;
    if (t.id === 'f_qtext') { S.q.text = t.value.trim(); S.q.page = 0; queueReq(); e.preventDefault(); }
    else if (t.id === 'f_pname') { send({ action: 'player', name: t.value.trim() }); e.preventDefault(); }
    else if (t.id === 'f_sugtext') { S.sq.text = t.value.trim(); S.sq.page = 0; sugsReq(); e.preventDefault(); }
    else if (t.id === 'f_reply' && t.value.trim()) {
      var st = S.st; if (!st) return;
      var kind = st.view === 'sug' ? 'sgsreply' : st.view === 'psug' ? 'sgpreply' : st.view === 'ticket' ? 'sreply' : 'preply';
      var id = st.view === 'ticket' ? st.ticket.id : st.view === 'sug' ? st.suggestion.id : (st.selected ? st.selected.id : 0);
      send({ action: kind, id: id, text: t.value.trim(), status: S.sq.status }); S.draft.reply = ''; e.preventDefault();
    }
  });

  // ------------------------------------------------------------ start
  var params = new URLSearchParams(window.location.search);
  var first = params.get('view') || 'mine';
  var o = { action: 'ready', view: first };
  if (params.get('id')) o.id = parseInt(params.get('id'), 10);
  if (params.get('name')) { o.name = params.get('name'); S.draft.pname = o.name; }
  send(o);
})();
