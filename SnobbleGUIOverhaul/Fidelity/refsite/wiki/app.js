/* Snobblemon Wiki: a read-only page. All text comes from content.js (window.WIKI); the game is only asked for the player's name. */
(function () {
  'use strict';
  var CH = 'snobblewiki';
  var DATA = window.WIKI || { groups: [], mods: [] };
  var $ = function (id) { return document.getElementById(id); };

  // ------------------------------------------------------------ plumbing
  function send(o) {
    o.channel = CH;
    if (window.webgui && typeof window.webgui.postToGame === 'function') window.webgui.postToGame(o);
  }
  function norm(x) {
    if (x && x.detail !== undefined) x = x.detail;
    if (typeof x === 'string') { try { x = JSON.parse(x); } catch (e) { return null; } }
    if (x && typeof x === 'object' && x.payload && !x.player) {
      var p = x.payload; if (typeof p === 'string') { try { p = JSON.parse(p); } catch (e) { return null; } } x = p;
    }
    return x;
  }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  function store(k, v) { try { if (v === undefined) return window.localStorage.getItem('snobblewiki.' + k); window.localStorage.setItem('snobblewiki.' + k, v); } catch (e) { /* private mode */ } return null; }

  // ------------------------------------------------------------ data
  var mods = DATA.mods || [], groups = DATA.groups || [];
  var byId = {}; mods.forEach(function (m) { byId[m.id] = m; (m.pages || []).forEach(function (p) { p._mod = m; }); });
  function pageOf(m, id) { var l = m.pages || []; for (var i = 0; i < l.length; i++) if (l[i].id === id) return l[i]; return null; }
  function tile(icon, c, cls) {
    var u = window.wikiIcon ? window.wikiIcon(icon || 'star') : '';
    return '<span class="tile ' + (cls || '') + ' c' + (c || 7) + '" style="background-image:' + u + '"></span>';
  }
  function gIcon(id) { var u = window.wikiGroupIcon ? window.wikiGroupIcon(id) : ''; return u ? '<i class="gi" style="background-image:' + u + '"></i>' : ''; }
  function modTile(m, cls) { return tile(m.icon, m.c, cls); }
  function pageTile(p, cls) { return tile(p.icon || p._mod.icon, p.c || p._mod.c, cls); }
  function plural(n, w) { return n + ' ' + w + (n === 1 ? '' : 's'); }
  var totalPages = 0; mods.forEach(function (m) { totalPages += (m.pages || []).length; });

  // ------------------------------------------------------------ text
  /* **bold**  `code`  [[route|label]] */
  function inline(t) {
    t = esc(t);
    t = t.replace(/\[\[([^\]|]+)\|([^\]]+)\]\]/g, function (_, r, l) { return '<a data-go="' + r + '">' + l + '</a>'; });
    t = t.replace(/\[\[([^\]]+)\]\]/g, function (_, r) { return '<a data-go="' + r + '">' + esc(label(r)) + '</a>'; });
    t = t.replace(/`([^`]+)`/g, '<code>$1</code>');
    t = t.replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>');
    return t;
  }
  function label(route) {
    var s = route.split('/'), m = byId[s[0]]; if (!m) return route;
    if (s[1]) { var p = pageOf(m, s[1]); return p ? p.title : route; }
    return m.name;
  }
  function plain(t) { return String(t == null ? '' : t).replace(/\[\[([^\]|]+)\|([^\]]+)\]\]/g, '$2').replace(/\[\[([^\]]+)\]\]/g, function (_, r) { return label(r); }).replace(/[`*]/g, ''); }

  function cell(c) {
    if (c && typeof c === 'object') {
      if (c.pill) return '<span class="pill ' + esc(c.pill) + '">' + esc(c.x) + '</span>';
      if (c.go) return '<a data-go="' + esc(c.go) + '">' + esc(c.x) + '</a>';
      return inline(c.x);
    }
    return inline(c);
  }
  var tblN = 0;
  function table(b) {
    var id = 't' + (++tblN), rows = b.rows || [], big = b.filter || rows.length > 14;
    var h = '<div class="tblwrap">' + (big ? '<input class="tfilter" data-t="' + id + '" placeholder="Filter ' + rows.length + ' rows..." autocomplete="off" spellcheck="false">' : '');
    h += '<table id="' + id + '"><thead><tr>' + (b.cols || []).map(function (c) { return '<th>' + esc(c) + '</th>'; }).join('') + '</tr></thead><tbody>';
    rows.forEach(function (r) { h += '<tr>' + r.map(function (c, i) { return '<td' + (i === 0 && b.key !== false ? ' class="k"' : '') + '>' + cell(c) + '</td>'; }).join('') + '</tr>'; });
    return h + '</tbody></table></div>';
  }
  var hN = 0;
  function block(b) {
    switch (b.t) {
      case 'p': return '<p>' + inline(b.x) + '</p>';
      case 'h': return '<h4 id="h' + (hN++) + '">' + esc(b.x) + '</h4>';
      case 'h3': return '<h5>' + esc(b.x) + '</h5>';
      case 'ul': return '<ul>' + (b.items || []).map(function (i) { return '<li>' + inline(i) + '</li>'; }).join('') + '</ul>';
      case 'ol': return '<ol>' + (b.items || []).map(function (i) { return '<li>' + inline(i) + '</li>'; }).join('') + '</ol>';
      case 'table': return table(b);
      case 'cmds': return table({ cols: ['Command', 'What it does'].concat(b.perm ? ['Who'] : []), rows: (b.rows || []).map(function (r) { r = r.slice(); r[0] = { x: '`' + r[0] + '`' }; return r; }) });
      case 'kv': return table({ cols: [b.k || 'Setting', b.v || 'Value'], rows: b.rows || [] });
      case 'call': var k = b.k || 'important'; return '<div class="call ' + esc(k) + '"><b>' + esc(k.toUpperCase()) + '</b> &middot; ' + inline(b.x) + '</div>';
      case 'code': return '<pre class="code">' + esc((b.lines || [b.x || '']).join('\n')) + '</pre>';
      case 'cards': return '<div class="minicards">' + (b.items || []).map(function (c) {
        var inner = tile(c.icon, c.c, 's') + '<div><b>' + esc(c.title) + '</b><span>' + inline(c.x || '') + '</span></div>';
        return c.go ? '<button class="mini" data-go="' + esc(c.go) + '">' + inner + '</button>' : '<div class="mini">' + inner + '</div>';
      }).join('') + '</div>';
      default: return '';
    }
  }
  function headings(p) { return (p.blocks || []).filter(function (b) { return b.t === 'h'; }).map(function (b) { return b.x; }); }

  // ------------------------------------------------------------ search index
  var index = [];
  function flat(p) {
    var f0 = (p.blocks || [])[0], dupDesc = f0 && f0.t === 'p' && plain(f0.x).indexOf(plain(p.desc || '').slice(0, 40)) === 0;
    var out = [p.title, dupDesc ? '' : p.desc || ''];
    (p.blocks || []).forEach(function (b) {
      if (b.x) out.push(b.x);
      (b.items || []).forEach(function (i) { out.push(typeof i === 'string' ? i : [i.title, i.x].join(' ')); });
      if (b.cols) out.push(b.cols.join(' '));
      (b.rows || []).forEach(function (r) { out.push(r.map(function (c) { return c && typeof c === 'object' ? c.x : c; }).join(' ')); });
      (b.lines || []).forEach(function (l) { out.push(l); });
    });
    return plain(out.join(' \n ')).replace(/\s+/g, ' ');
  }
  mods.forEach(function (m) {
    index.push({ route: m.id, m: m, p: null, title: m.name, trail: 'Mod', text: plain([m.name, m.tagline, m.summary, (m.info || []).join(' ')].join(' ')), low: '' });
    (m.pages || []).forEach(function (p) { index.push({ route: m.id + '/' + p.id, m: m, p: p, title: p.title, trail: m.name, text: flat(p), low: '' }); });
  });
  index.forEach(function (e) { e.low = e.text.toLowerCase(); e.tl = e.title.toLowerCase(); e.ml = e.m.name.toLowerCase(); });
  function search(q) {
    var toks = q.toLowerCase().split(/\s+/).filter(Boolean); if (!toks.length) return [];
    var res = [];
    index.forEach(function (e) {
      var score = 0;
      for (var i = 0; i < toks.length; i++) {
        var t = toks[i], a = e.tl.indexOf(t) >= 0, b = e.ml.indexOf(t) >= 0, c = e.low.indexOf(t) >= 0;
        if (!a && !b && !c) return;
        score += (a ? 6 : 0) + (b ? 3 : 0) + (c ? 1 : 0);
      }
      if (!e.p) score += 2;
      res.push({ e: e, s: score });
    });
    res.sort(function (x, y) { return y.s - x.s; });
    return res.slice(0, 40);
  }
  /* A short piece of the page around the first match, cut at word edges, without repeating the title. */
  function snippet(e, toks) {
    var body = e.text.indexOf(e.title) === 0 ? e.text.slice(e.title.length).replace(/^\s+/, '') : e.text, low = body.toLowerCase();
    var i = -1; for (var k = 0; k < toks.length && i < 0; k++) i = low.indexOf(toks[k]);
    var a = i < 0 ? 0 : Math.max(0, i - 45);
    if (a > 0) { var sp = body.indexOf(' ', a); if (sp > 0 && sp < i) a = sp + 1; }
    var end = Math.min(body.length, a + 140), cut = end < body.length;
    if (cut) { var sp2 = body.lastIndexOf(' ', end); if (sp2 > a + 60) end = sp2; }
    var out = esc(body.slice(a, end));
    toks.forEach(function (t) { out = out.replace(new RegExp('(' + t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')', 'ig'), '<mark>$1</mark>'); });
    return (a > 0 ? '...' : '') + out + (end < body.length ? '...' : '');
  }

  // ------------------------------------------------------------ state + navigation
  var S = { view: 'home', mod: null, page: null, sel: null, q: '', zoom: 100, who: null, note: DATA.note || '' };
  var stack = [];
  function snap() { return { view: S.view, mod: S.mod, page: S.page, sel: S.sel, q: S.q }; }
  function go(route, noPush) {
    var s = String(route || '').split('/'), m = byId[s[0]];
    if (!noPush) stack.push(snap());
    S.q = ''; $('q').value = '';
    if (!m) { S.view = 'home'; S.mod = null; S.page = null; S.sel = S.sel; }
    else if (s[1] && pageOf(m, s[1])) { S.view = 'page'; S.mod = m.id; S.page = s[1]; }
    else { S.view = 'mod'; S.mod = m.id; S.page = null; S.sel = (m.pages && m.pages[0]) ? m.pages[0].id : null; }
    render(true);
  }
  function back() {
    var p = stack.pop(); if (!p) { if (S.view !== 'home') { S.view = 'home'; S.mod = null; S.page = null; S.q = ''; $('q').value = ''; render(true); } return; }
    S.view = p.view; S.mod = p.mod; S.page = p.page; S.sel = p.sel; S.q = p.q; $('q').value = p.q; render(true);
  }
  function runSearch(q) {
    if (q === S.q) return;
    if (!S.q && q) stack.push(snap());
    var typing = !!(S.q && q);
    S.q = q;
    if (q) { S.view = 'search'; } else { var p = stack.pop(); if (p) { S.view = p.view; S.mod = p.mod; S.page = p.page; S.sel = p.sel; } else { S.view = 'home'; } }
    render(true, typing);
  }

  // ------------------------------------------------------------ views
  function sideHtml() {
    var h = '<div class="scroll">';
    groups.forEach(function (g) {
      var ms = mods.filter(function (m) { return m.group === g.id; }); if (!ms.length) return;
      h += '<div class="lbl">' + gIcon(g.id) + esc(g.name) + '</div>';
      ms.forEach(function (m) {
        var on = S.mod === m.id && S.view !== 'home' && S.view !== 'search';
        h += '<button class="nav' + (on && S.view === 'mod' ? ' on' : '') + '" data-go="' + esc(m.id) + '">' + modTile(m, 's') + '<span class="nm">' + esc(m.name) + '</span><span class="n">' + (m.pages || []).length + '</span></button>';
        if (on && S.view === 'page') {
          (m.pages || []).forEach(function (p) { h += '<button class="sub2' + (p.id === S.page ? ' on' : '') + '" data-go="' + esc(m.id + '/' + p.id) + '">' + esc(p.title) + '</button>'; });
        }
      });
    });
    return h + '</div>';
  }
  function modCard(m) {
    return '<button class="card' + (S.sel === m.id ? ' sel' : '') + '" data-sel="' + esc(m.id) + '" data-route="' + esc(m.id) + '"><div class="h">' + modTile(m) + '<span class="t">' + esc(m.name) + '</span></div><p>' + esc(m.tagline || '') + '</p><span class="pill ' + (m.pillc || 'g') + '">' + plural((m.pages || []).length, 'page') + '</span></button>';
  }
  function pageCard(m, p) {
    return '<button class="card' + (S.sel === p.id ? ' sel' : '') + '" data-sel="' + esc(p.id) + '" data-route="' + esc(m.id + '/' + p.id) + '"><div class="h">' + pageTile(p) + '<span class="t">' + esc(p.title) + '</span></div><p>' + esc(p.desc || '') + '</p>' + (p.pill ? '<span class="pill ' + (p.pillc || 'b') + '">' + esc(p.pill) + '</span>' : '') + '</button>';
  }
  function related(m) { return (m.related || []).filter(function (r) { return byId[r]; }).map(function (r) { return '<button class="chip" data-go="' + esc(r) + '">' + esc(byId[r].name) + '</button>'; }).join(''); }
  function groupName(id) { for (var i = 0; i < groups.length; i++) if (groups[i].id === id) return groups[i].name; return ''; }

  function homeMain() {
    var h = '<div class="scroll"><div class="crumbs">Wiki</div><h2 class="pt">Snobblemon Wiki</h2><div class="desc">' + esc(DATA.intro || 'Everything about this server in one place. Pick a mod, or search.') + '</div>';
    groups.forEach(function (g) {
      var ms = mods.filter(function (m) { return m.group === g.id; }); if (!ms.length) return;
      h += '<div class="gh">' + gIcon(g.id) + esc(g.name) + '</div><div class="cards">' + ms.map(modCard).join('') + '</div>';
    });
    return h + '</div>';
  }
  function homeDetail() {
    var m = byId[S.sel] || mods[0]; if (!m) return '<div class="scroll"></div>';
    var h = '<div class="scroll"><div class="head">' + modTile(m, 'l') + '<div><h3>' + esc(m.name) + '</h3><div class="red">' + esc(groupName(m.group)) + (m.kind ? ' &middot; ' + esc(m.kind) : '') + '</div></div></div>';
    h += '<div class="lbl">Overview</div><p>' + inline(m.summary || m.tagline || '') + '</p>';
    if (m.info && m.info.length) { h += '<div class="lbl">Quick facts</div>' + m.info.slice(0, 4).map(function (r) { return '<div class="row"><span>' + esc(r[0]) + '</span><span>' + esc(r[1]) + '</span></div>'; }).join(''); }
    var rel = related(m); if (rel) h += '<div class="lbl">Related</div>' + rel;
    return h + '<br><button class="openBtn" data-open="' + esc(m.id) + '">Open</button></div>';
  }
  function modMain() {
    var m = byId[S.mod], h = '<div class="scroll"><div class="crumbs"><a data-go="">Wiki</a> &rsaquo; ' + esc(groupName(m.group)) + ' &rsaquo; ' + esc(m.name) + '</div><h2 class="pt">' + esc(m.name) + '</h2><div class="desc">' + inline(m.summary || m.tagline || '') + '</div>';
    h += '<div class="cards">' + (m.pages || []).map(function (p) { return pageCard(m, p); }).join('') + '</div>';
    if (!(m.pages || []).length) h += '<div class="empty">No pages yet.</div>';
    return h + '</div>';
  }
  function modDetail() {
    var m = byId[S.mod], p = pageOf(m, S.sel) || (m.pages || [])[0]; if (!p) return '<div class="scroll"></div>';
    var hs = headings(p);
    var h = '<div class="scroll"><div class="head">' + pageTile(p, 'l') + '<div><h3>' + esc(p.title) + '</h3><div class="red">' + esc(m.name) + ' &middot; ' + esc(groupName(m.group)) + '</div></div></div>';
    h += '<div class="lbl">Overview</div><p>' + inline(p.desc || '') + '</p>';
    if (p.pill) h += '<div class="row"><span>Contents</span><span>' + esc(p.pill) + '</span></div>';
    if (hs.length) h += '<div class="lbl">In this page</div>' + hs.slice(0, 8).map(function (x) { return '<div class="row"><span>' + esc(x) + '</span><span></span></div>'; }).join('');
    var rel = related(m); if (rel) h += '<div class="lbl">Related</div>' + rel;
    return h + '<br><button class="openBtn" data-open="' + esc(m.id + '/' + p.id) + '">Open page</button></div>';
  }
  function articleHtml() {
    var m = byId[S.mod], p = pageOf(m, S.page); hN = 0; tblN = 0;
    var hs = headings(p);
    var h = '<div id="article"><div class="read" id="read"><div class="crumbs"><a data-go="">Wiki</a> &rsaquo; <a data-go="' + esc(m.id) + '">' + esc(m.name) + '</a> &rsaquo; ' + esc(p.title) + '</div>';
    // The page summary is shown under the title, unless the first paragraph already starts with it.
    var first = (p.blocks || [])[0], dup = first && first.t === 'p' && plain(first.x).indexOf(plain(p.desc || '').slice(0, 40)) === 0;
    h += '<h2>' + esc(p.title) + '</h2>' + (p.desc && !dup ? '<p class="lede">' + inline(p.desc) + '</p>' : '');
    h += (p.blocks || []).map(block).join('') + '</div>';
    h += '<div class="rail"><div class="ibox"><div class="t">' + esc(m.name) + '</div><div class="img">' + modTile(m, 'xl') + '</div><dl>' + (m.info || []).map(function (r) { return '<div><dt>' + esc(r[0]) + '</dt><dd>' + esc(r[1]) + '</dd></div>'; }).join('') + '</dl></div>';
    if (hs.length) h += '<div class="lbl">On this page</div>' + hs.map(function (x, i) { return '<a data-jump="h' + i + '">' + esc(x) + '</a>'; }).join('');
    var rel = (m.related || []).filter(function (r) { return byId[r]; }); if (rel.length) h += '<div class="lbl">Related</div>' + rel.map(function (r) { return '<a data-go="' + esc(r) + '">' + esc(byId[r].name) + '</a>'; }).join('');
    return h + '</div></div>';
  }
  function searchMain() {
    var toks = S.q.toLowerCase().split(/\s+/).filter(Boolean), res = search(S.q);
    var h = '<div class="scroll"><div class="crumbs"><a data-go="">Wiki</a> &rsaquo; Search</div><h2 class="pt">Search</h2><div class="desc">' + (res.length ? plural(res.length, 'result') + ' for "' + esc(S.q) + '"' : 'Nothing found for "' + esc(S.q) + '". Try a shorter word.') + '</div>';
    res.forEach(function (r) {
      var e = r.e;
      h += '<button class="res" data-open="' + esc(e.route) + '">' + (e.p ? pageTile(e.p) : modTile(e.m)) + '<div><b>' + esc(e.title) + '</b><small>' + esc(e.trail) + '</small><em>' + snippet(e, toks) + '</em></div></button>';
    });
    return h + '</div>';
  }

  function barHtml() {
    var crumb = 'Wiki', m = byId[S.mod];
    if (S.view === 'search') crumb += ' > Search';
    else if (m) { crumb += ' > ' + m.name; if (S.view === 'page') { var p = pageOf(m, S.page); crumb += ' > ' + (p ? p.title : ''); } }
    var h = '<button class="btn" id="bBack">&lt; Back</button><button class="btn" id="bHome">Home</button><div class="stat">' + esc(crumb) + (S.note ? ' &nbsp;&middot;&nbsp; ' + esc(S.note) : '') + '</div>';
    if (S.view === 'page') {
      var l = m.pages || [], i = l.findIndex(function (x) { return x.id === S.page; });
      h += '<button class="btn" id="bPrev"' + (i <= 0 ? ' disabled' : '') + '>&lt; Prev</button><button class="btn" id="bNext"' + (i >= l.length - 1 ? ' disabled' : '') + '>Next &gt;</button>';
    }
    return h + '<div class="zoom"><button class="zb" id="zMinus">-</button>' + S.zoom + '%<button class="zb" id="zPlus">+</button></div>';
  }

  function render(resetScroll, quiet) {
    var body = $('body'), prev = [];
    if (!resetScroll) body.querySelectorAll('.scroll,.read').forEach(function (n) { prev.push(n.scrollTop); });
    var two = S.view === 'page' || S.view === 'search';
    body.className = two ? 'two' : '';
    var main;
    if (S.view === 'home') main = '<div class="pane" id="main">' + homeMain() + '</div><div class="pane" id="detail">' + homeDetail() + '</div>';
    else if (S.view === 'mod') main = '<div class="pane" id="main">' + modMain() + '</div><div class="pane" id="detail">' + modDetail() + '</div>';
    else if (S.view === 'page') main = '<div class="pane" id="main">' + articleHtml() + '</div>';
    else main = '<div class="pane" id="main">' + searchMain() + '</div>';
    body.innerHTML = '<div class="pane" id="side">' + sideHtml() + '</div>' + main;
    $('bar').innerHTML = barHtml();
    if (!resetScroll) { var k = 0; body.querySelectorAll('.scroll,.read').forEach(function (n) { n.scrollTop = prev[k++] || 0; }); }
    $('bBack').disabled = !stack.length && S.view === 'home';
    // keep the active nav entry visible
    var on = body.querySelector('.nav.on,.sub2.on'); if (on && resetScroll) { try { on.scrollIntoView({ block: 'nearest' }); } catch (e) { /* ignore */ } }
    if (resetScroll && !quiet) {
      // short fade-up of the content that changed, plus one ring on the orb (CSS animations play once)
      ['main', 'detail'].forEach(function (id) { var p = $(id), c = p && p.firstElementChild; if (c) c.classList.add('enter'); });
      ring();
    }
  }
  function ring() { var o = document.querySelector('.orb'); if (!o) return; o.classList.remove('go'); void o.offsetWidth; o.classList.add('go'); }
  function spark() {
    var c = document.querySelector('#main .card.sel'); if (!c) return;
    var i = document.createElement('i'); i.className = 'sp'; c.appendChild(i); c.classList.add('pop');
    setTimeout(function () { c.classList.remove('pop'); if (i.parentNode) i.parentNode.removeChild(i); }, 900);
  }

  // ------------------------------------------------------------ events
  document.addEventListener('click', function (e) {
    var t = e.target, el;
    if ((el = t.closest('[data-jump]'))) { var j = $(el.getAttribute('data-jump')); if (j) j.scrollIntoView({ block: 'start' }); return; }
    if ((el = t.closest('[data-open]'))) { go(el.getAttribute('data-open')); return; }
    if ((el = t.closest('[data-sel]'))) {
      // With the quick-look pane hidden (narrow window) a click opens straight away.
      if ($('stage').classList.contains('narrow')) { go(el.getAttribute('data-route')); return; }
      S.sel = el.getAttribute('data-sel'); render(false); spark(); return;
    }
    if ((el = t.closest('[data-go]'))) { go(el.getAttribute('data-go')); return; }
    if (t.closest('#bBack')) return back();
    if (t.closest('#bHome')) return go('');
    if (t.closest('#bPrev') || t.closest('#bNext')) {
      var m = byId[S.mod], l = m.pages || [], i = l.findIndex(function (x) { return x.id === S.page; }) + (t.closest('#bNext') ? 1 : -1);
      if (l[i]) { stack.push(snap()); S.page = l[i].id; render(true); } return;
    }
    if (t.closest('#zMinus')) return setZoom(S.zoom - 10);
    if (t.closest('#zPlus')) return setZoom(S.zoom + 10);
    if (t.closest('#close')) { if (window.webgui && typeof window.webgui.closeGui === 'function') window.webgui.closeGui(); }
  });
  document.addEventListener('dblclick', function (e) { var el = e.target.closest('[data-sel]'); if (el) go(el.getAttribute('data-route')); });
  document.addEventListener('input', function (e) {
    var t = e.target;
    if (t.id === 'q') { clearTimeout(qT); qT = setTimeout(function () { runSearch($('q').value.trim()); }, 140); return; }
    if (t.classList.contains('tfilter')) {
      var f = t.value.toLowerCase(), rows = $(t.getAttribute('data-t')).querySelectorAll('tbody tr');
      for (var i = 0; i < rows.length; i++) rows[i].style.display = !f || rows[i].textContent.toLowerCase().indexOf(f) >= 0 ? '' : 'none';
    }
  });
  var qT;
  window.addEventListener('keydown', function (e) {
    var typing = /^(INPUT|TEXTAREA)$/.test((document.activeElement || {}).tagName || '');
    if (e.key === '/' && !typing) { e.preventDefault(); $('q').focus(); }
    else if (e.key === 'Escape') { if (typing) { if ($('q') === document.activeElement && $('q').value) { $('q').value = ''; runSearch(''); } document.activeElement.blur(); } else back(); }
  });

  // ------------------------------------------------------------ size
  function setZoom(z) { S.zoom = Math.max(60, Math.min(160, z)); store('zoom', String(S.zoom)); fit(); var zz = document.querySelector('.zoom'); if (zz) $('bar').innerHTML = barHtml(); }
  function fit() {
    var s = Math.max(.4, window.innerHeight / 720 * S.zoom / 100), st = $('stage');
    st.style.zoom = s; st.style.width = (window.innerWidth / s) + 'px'; st.style.height = (window.innerHeight / s) + 'px';
    var lw = window.innerWidth / s;
    st.classList.toggle('narrow', lw < 1160); st.classList.toggle('tiny', lw < 900);
  }
  window.addEventListener('resize', fit);

  // ------------------------------------------------------------ server
  window.addEventListener('webgui:state', function (e) {
    var d = norm(e); if (!d || !d.player) return;
    S.who = d; if (d.note) S.note = d.note;
    $('who').innerHTML = '<b>' + esc(d.player) + '</b><br>' + plural(mods.length, 'mod') + ' &middot; ' + plural(totalPages, 'page');
    $('bar').innerHTML = barHtml();
  });

  // ------------------------------------------------------------ start
  var z = parseInt(store('zoom'), 10); if (z >= 60 && z <= 160) S.zoom = z;
  fit();
  var qs = {}; (window.WIKI_QS !== undefined ? window.WIKI_QS : (location.search || '')).replace(/^\?/, '').split('&').forEach(function (kv) { if (!kv) return; var p = kv.split('='); qs[decodeURIComponent(p[0])] = decodeURIComponent((p[1] || '').replace(/\+/g, ' ')); });
  if (qs.p) go(qs.p, true); else render(true);
  if (qs.q) { $('q').value = qs.q; runSearch(qs.q); }
  S.sel = S.sel || (mods[0] && mods[0].id);
  if (S.view === 'home') render(true);
  send({ action: 'hello' });
  var helloT = setInterval(function () { if (S.who) clearInterval(helloT); else send({ action: 'hello' }); }, 2500);
})();
