/* Shellpack screen. It only draws what the server sends and reports what the player clicked or dragged;
   every rule (stack limits, nesting, capacity, ownership, costs) is enforced again on the server. */
(function () {
  'use strict';
  var CH = 'shellpack';
  var $ = function (id) { return document.getElementById(id); };
  if (/[?&]mode=gui/.test(location.search)) document.body.className = 'gui';   // full-screen mode: the mouse works at once, the page paints its own backdrop
  var TIERID = ['basic', 'small', 'medium', 'large', 'gigantic', 'colossal'];
  var PAL = [
    { pb: '#dcc7a2', pd: '#8a6a3a', acc: '#a96a2b', l: '#b0702f' },
    { pb: '#cfd9b0', pd: '#6f8240', acc: '#5f7a33', l: '#7f9a4a' },
    { pb: '#ead9a6', pd: '#a88a3a', acc: '#b98f3a', l: '#d9b25a' },
    { pb: '#e6c3b8', pd: '#a05a4c', acc: '#b24a3c', l: '#d96a58' },
    { pb: '#bfdfdf', pd: '#4a8a92', acc: '#34828a', l: '#4aa6a8' },
    { pb: '#d6c6ea', pd: '#7a5aa6', acc: '#7e50b4', l: '#a070d6' }
  ];
  var MODES = [['name', 'N', 'name'], ['count', 'C', 'amount'], ['type', 'T', 'item type']];
  var REDUCED = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  var S = {
    st: null, prev: null, sel: null, pending: false, pendingTimer: 0, mode: 0, tab: null,
    scale: 1, popAll: true, icons: null, fly: null, lastPct: -1, lastTier: -1
  };
  try { var m0 = parseInt(window.localStorage.getItem('shellpack.mode'), 10); if (m0 >= 0 && m0 < 3) S.mode = m0; } catch (e) { /* storage may be blocked */ }

  var SHELLS = TIERID.map(function (id) { var im = new Image(); im.src = 'shells/' + id + '.png'; return im; });

  // ------------------------------------------------------------ plumbing
  function rid() { return Math.random().toString(36).slice(2) + Date.now().toString(36); }
  function send(o) {
    o.channel = CH;
    if (o.action !== 'ping' && o.action !== 'close') o.rid = rid();
    if (window.webgui && typeof window.webgui.postToGame === 'function') window.webgui.postToGame(o);
    else console.log('[shellpack] no webgui bridge', o);
  }
  function norm(x) {
    if (x && x.detail !== undefined) x = x.detail;
    if (typeof x === 'string') { try { x = JSON.parse(x); } catch (e) { return null; } }
    if (x && typeof x === 'object' && x.payload && !x.rev && !x.message) {
      var p = x.payload; if (typeof p === 'string') { try { p = JSON.parse(p); } catch (e) { return null; } } x = p;
    }
    return x;
  }
  window.addEventListener('webgui:state', function (e) { var d = norm(e); if (d) onState(d); });
  window.addEventListener('webgui:result', function (e) { var d = norm(e); if (d) onResult(d); });
  window.addEventListener('webgui:closed', function (e) { var d = norm(e); if (d) onClosed(d); });

  function fit() {
    var s = Math.min(window.innerWidth / 1600, window.innerHeight / 900);
    S.scale = s;
    $('stage').style.transform = 'translate(' + (-800 * s) + 'px,' + (-450 * s) + 'px) scale(' + s + ')';
  }
  window.addEventListener('resize', fit); fit();

  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text !== undefined) e.textContent = text; return e; }
  function mix(a, b, t) {
    var p = function (h) { return [1, 3, 5].map(function (i) { return parseInt(h.slice(i, i + 2), 16); }); };
    var x = p(a), y = p(b);
    return '#' + x.map(function (v, i) { var n = Math.round(v + (y[i] - v) * t); return (n < 16 ? '0' : '') + n.toString(16); }).join('');
  }
  /* client coordinates -> stage coordinates */
  function toStage(cx, cy) {
    var r = $('stage').getBoundingClientRect();
    return { x: (cx - r.left) / S.scale, y: (cy - r.top) / S.scale };
  }
  function centerOf(node) {
    var r = node.getBoundingClientRect();
    return toStage(r.left + r.width / 2, r.top + r.height / 2);
  }

  // ------------------------------------------------------------ item pictures (same sheets the server builds)
  function loadIcons(base) {
    var file = base === 'icons' ? 'icons.json' : 'vanilla_icons.json';
    return fetch(base + '/' + file).then(function (r) { return r.ok ? r.json() : null; }).catch(function () { return null; }).then(function (j) {
      if (!j || !j.icons) return;
      S.icons = S.icons || { map: {} };
      Object.keys(j.icons).forEach(function (id) {
        var p = j.icons[id], sh = j.sheets[p[2] || 0];
        S.icons.map[id] = { url: base + '/' + sh.file, w: sh.w, h: sh.h, c: p[0], r: p[1], cell: j.cell };
      });
    });
  }
  Promise.all([loadIcons('icons'), loadIcons('icons/vanilla')]).then(function () { if (S.st && S.icons) rerender(); });

  /* WebGUI limits how fast it sends files, so sheets are fetched one at a time and retried if dropped. */
  var sheetState = {}, sheetQueue = [], sheetBusy = false, sheetTries = {};
  function wantSheet(url) {
    if (sheetState[url]) return;
    sheetState[url] = 'queued'; sheetQueue.push(url); pumpSheets();
  }
  function pumpSheets() {
    if (sheetBusy || !sheetQueue.length) return;
    sheetBusy = true;
    var url = sheetQueue.shift(), img = new Image();
    sheetState[url] = 'loading';
    var finish = function (ok) {
      sheetTries[url] = (sheetTries[url] || 0) + 1;
      if (ok) { sheetState[url] = 'ready'; rerender(); }
      else if (sheetTries[url] < 4) { sheetState[url] = 'queued'; sheetQueue.push(url); }
      else sheetState[url] = 'fail';
      setTimeout(function () { sheetBusy = false; pumpSheets(); }, ok ? 400 : 2500);
    };
    img.onload = function () { finish(true); };
    img.onerror = function () { finish(false); };
    img.src = url;
  }
  function hue(s) { var h = 0; for (var i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360; return h; }

  /* An item picture at a whole-number multiple of its own size so the pixels stay sharp. */
  function iconNode(it, px) {
    var box = el('div', 'ic');
    if (it.shell !== undefined) {
      var im = document.createElement('img'); im.src = 'shells/' + TIERID[it.shell] + '.png'; box.appendChild(im);
      return box;
    }
    var ic = S.icons && (S.icons.map[it.id] || (it.ic && S.icons.map[it.ic]));
    if (ic && sheetState[ic.url] !== 'ready') { wantSheet(ic.url); ic = null; }
    if (ic) {
      var draw = px >= ic.cell ? Math.floor(px / ic.cell) * ic.cell : px, k = draw / ic.cell, i = el('div', 'sheet');
      i.style.width = draw + 'px'; i.style.height = draw + 'px';
      i.style.backgroundImage = 'url("' + ic.url + '")';
      i.style.backgroundSize = (ic.w * k) + 'px ' + (ic.h * k) + 'px';
      i.style.backgroundPosition = (-ic.c * draw) + 'px ' + (-ic.r * draw) + 'px';
      box.appendChild(i);
    } else {
      var f = el('div', 'fb', String(it.name || '?').replace(/[^A-Za-z0-9]/g, '').slice(0, 2).toUpperCase());
      f.style.borderColor = 'hsl(' + hue(it.id || it.name || '') + ',45%,55%)';
      box.appendChild(f);
    }
    return box;
  }

  // ------------------------------------------------------------ state
  function onState(d) {
    var tierUp = S.lastTier >= 0 && d.tier > S.lastTier;
    S.prev = S.st; S.st = d; S.pending = false; clearTimeout(S.pendingTimer);
    var first = S.lastTier < 0;
    S.lastTier = d.tier;
    if (tierUp) S.popAll = true;
    render();
    if (S.fly) { runPendingFly(); }
    if (tierUp) { var c = centerOf($('main')); sparkle(c.x, c.y - 120, PAL[d.tier].l, 40); }
    if (first) S.popAll = false;
  }
  function onResult(d) {
    toast(d.message, d.ok);
    if (d.ok && (d.action === 'stow' || d.action === 'withdraw')) flights(d.action);
    if (d.ok && d.action === 'sort') S.popAll = true;
  }
  function onClosed(d) {
    toast(d.message || 'Closed.', false);
    setTimeout(closeGui, 1300);
  }
  function closeGui() {
    send({ action: 'close' });
    if (window.webgui && typeof window.webgui.closeGui === 'function') window.webgui.closeGui();
  }
  function rerender() { if (S.st) render(); }

  // ------------------------------------------------------------ drawing
  function render() {
    hideTip();   // slots are rebuilt, so a tooltip for the old ones would be stale
    var d = S.st, pal = PAL[d.tier] || PAL[0], g = $('gui');
    g.style.setProperty('--pb', pal.pb);
    g.style.setProperty('--pl', mix(pal.pb, '#ffffff', .7));
    g.style.setProperty('--pd', pal.pd);
    g.style.setProperty('--sl', mix(pal.pd, '#000000', .1));
    g.style.setProperty('--acc', pal.acc);
    $('ticon').src = 'shells/' + d.tierId + '.png';
    $('ttext').textContent = d.tierName + ' Shellpack';

    var sg = $('sg'); sg.innerHTML = '';
    sg.style.gridTemplateColumns = 'repeat(' + d.cols + ', 46px)';
    for (var i = 0; i < d.slots; i++) sg.appendChild(slot('s', i, d.shell[i], i >= d.capacity));
    var ig = $('ig'); ig.innerHTML = '';
    ig.style.gridTemplateColumns = 'repeat(9, 46px)';
    for (var j = 9; j < 36; j++) ig.appendChild(slot('p', j, d.inv[j], false));
    ig.appendChild(el('div', 'gap'));
    for (var h = 0; h < 9; h++) ig.appendChild(slot('p', h, d.inv[h], false));
    S.popAll = false;

    renderMeter(d);
    renderTabs();
    $('bMode').textContent = MODES[S.mode][1];
    $('bMode').title = 'Sort by: ' + MODES[S.mode][2] + ' (click to change)';
    paintCursor();
  }

  function slot(c, i, it, over) {
    var n = el('div', 'slot' + (over ? ' over' : '') + (it && it.bad ? ' bad' : '') + (S.popAll ? ' in' : ''));
    n.dataset.c = c; n.dataset.i = i;
    if (S.popAll) n.style.animationDelay = Math.min(i * 5, 450) + 'ms';
    if (S.sel && S.sel.c === c && S.sel.i === i) n.classList.add('sel');
    if (it) {
      n.appendChild(iconNode(it, 34));
      if (it.foil) n.appendChild(el('div', 'foil'));
      if (it.n > 1) n.appendChild(el('i', '', String(it.n)));
      if (it.dmg !== undefined) {
        var bar = el('div', 'dur'), b = el('b');
        b.style.width = Math.max(4, Math.round((1 - it.dmg) * 100)) + '%';
        b.style.background = 'hsl(' + Math.round((1 - it.dmg) * 120) + ',75%,45%)';
        bar.appendChild(b); n.appendChild(bar);
      }
    }
    return n;
  }
  function itemAt(c, i) {
    if (!S.st) return null;
    var a = c === 's' ? S.st.shell : S.st.inv;
    return a[i] || null;
  }

  // ------------------------------------------------------------ meter: the snail on its slime trail
  var snailCanvas = null, snailTier = -1;
  function drawSnail(tier) {
    var c = document.createElement('canvas'); c.width = 32; c.height = 12;
    var g = c.getContext('2d'); g.imageSmoothingEnabled = false;
    var shell = SHELLS[tier];
    for (var f = 0; f < 2; f++) {
      var o = f * 16, w = f ? 1 : 0;
      g.fillStyle = '#efe3bf'; g.fillRect(o + 1, 9, 13, 3); g.fillRect(o + 10, 6, 4, 4);
      g.fillStyle = '#cdbf96'; g.fillRect(o + 1, 11, 13, 1);
      g.fillStyle = '#d9cca0'; g.fillRect(o + 11, 2 + w, 1, 4); g.fillRect(o + 13, 3 - w, 1, 4);
      g.fillStyle = '#222'; g.fillRect(o + 11, 1 + w, 1, 1); g.fillRect(o + 13, 2 - w, 1, 1);
      if (shell.complete) g.drawImage(shell, o + 1, 0, 9, 9);
    }
    return c;
  }
  function renderMeter(d) {
    var pct = d.slots ? Math.round(d.used / d.slots * 100) : 0;
    var sn = $('snail');
    if (snailTier !== d.tier || !snailCanvas) {
      snailTier = d.tier; sn.innerHTML = '';
      snailCanvas = drawSnail(d.tier); sn.appendChild(snailCanvas);
      if (!SHELLS[d.tier].complete) SHELLS[d.tier].onload = function () { snailCanvas = null; rerender(); };
    }
    $('meterLbl').textContent = d.used + ' / ' + d.slots + ' slots  ·  ' + pct + '% full';
    if (pct !== S.lastPct) {
      sn.classList.toggle('flip', S.lastPct >= 0 && pct < S.lastPct);
      if (S.lastPct >= 0 && !REDUCED) { sn.classList.add('moving'); clearTimeout(sn._t); sn._t = setTimeout(function () { sn.classList.remove('moving'); }, 900); }
      S.lastPct = pct;
    }
    $('trail').style.width = pct + '%';
    // the snail's own width is left out so it stays inside the track at both ends
    sn.style.left = 'calc((100% - 48px) * ' + (pct / 100) + ')';
  }

  // ------------------------------------------------------------ left tabs
  function tabIcon(kind) {
    var c = document.createElement('canvas'); c.width = c.height = 12;
    var g = c.getContext('2d'), f = function (x, y, w, h, col) { g.fillStyle = col; g.fillRect(x, y, w, h); }, k = '#3a2a18';
    if (kind === 'info') { f(5, 1, 2, 2, k); f(5, 4, 2, 6, k); f(4, 10, 4, 1, k); }
    else if (kind === 'opt') { f(5, 1, 2, 10, k); f(1, 5, 10, 2, k); f(3, 3, 6, 6, k); f(5, 5, 2, 2, '#dcc7a2'); }
    else { f(5, 1, 2, 2, k); f(3, 3, 6, 2, k); f(1, 5, 10, 2, k); f(5, 7, 2, 4, k); }
    return c;
  }
  var TABS = ['info', 'opt', 'up'];
  function renderTabs() {
    var tabs = $('tabs'); tabs.innerHTML = '';
    TABS.forEach(function (k) {
      var b = el('div', 'tab' + (S.tab === k ? ' on' : ''));
      b.appendChild(tabIcon(k));
      b.title = k === 'info' ? 'Shellpack info' : k === 'opt' ? 'Options' : 'Upgrade';
      b.addEventListener('click', function () { S.tab = S.tab === k ? null : k; renderTabs(); });
      tabs.appendChild(b);
    });
    var tp = $('tabpanel'); tp.classList.toggle('show', !!S.tab);
    if (!S.tab) return;
    var d = S.st; tp.innerHTML = '';
    if (S.tab === 'info') {
      tp.appendChild(el('h5', '', d.tierName + ' Shellpack'));
      tp.appendChild(el('p', '', 'Tier ' + (d.tier + 1) + ' of 6  ·  ' + d.capacity + ' slots'));
      tp.appendChild(el('p', '', d.used + ' slots in use.'));
      tp.appendChild(el('p', '', d.owner ? 'Bound to ' + d.owner + '.' : 'Not bound to anyone yet.'));
      tp.appendChild(el('p', '', 'Shellpacks cannot be stored inside shellpacks. Slots marked red are above this shellpack’s capacity: you can take their items out, but not put anything in.'));
    } else if (S.tab === 'opt') {
      tp.appendChild(el('h5', '', 'Options'));
      tp.appendChild(el('p', '', 'The ⇅ button sorts by:'));
      var row = el('div', 'opt');
      MODES.forEach(function (m, idx) {
        var b = el('button', idx === S.mode ? 'on' : '', m[2].charAt(0).toUpperCase() + m[2].slice(1));
        b.addEventListener('click', function () { setMode(idx); });
        row.appendChild(b);
      });
      tp.appendChild(row);
      tp.appendChild(el('p', '', '⇧ moves items you carry into the shellpack when it already holds that item type' + (d.hotbarStow ? '' : ' (your hotbar is left alone)') + '. ⇩ does the reverse. Shift-click moves one stack.'));
    } else {
      tp.appendChild(el('h5', '', 'Upgrade path'));
      d.tiers.forEach(function (t, idx) {
        var r = el('div', 'row' + (idx === d.tier ? ' now' : ''));
        r.appendChild(el('span', '', (idx < d.tier ? '✔ ' : idx === d.tier ? '▶ ' : '') + t.name));
        r.appendChild(el('span', '', t.slots + ' slots'));
        tp.appendChild(r);
      });
      var up = d.upgrade;
      if (up && up.next) {
        tp.appendChild(el('p', '', 'To reach ' + up.next.name + ' (' + up.next.slots + ' slots):'));
        if (!up.cost.length) tp.appendChild(el('p', '', 'Free.'));
        up.cost.forEach(function (c) {
          var line = el('div', 'cost'), ci = el('div', 'ci');
          ci.appendChild(iconNode({ id: c.id, name: c.name }, 28));
          var ok = c.have >= c.need;
          line.appendChild(ci);
          line.appendChild(el('span', ok ? 'yes' : 'no', c.need + ' × ' + c.name + '  (' + c.have + ')'));
          tp.appendChild(line);
        });
        var go = el('button', 'go', up.enabled ? 'Upgrade' : 'Upgrading is off');
        go.disabled = !up.can;
        go.addEventListener('click', function () { if (!S.pending) { busy(); send({ action: 'upgrade' }); } });
        tp.appendChild(go);
        tp.appendChild(el('p', '', 'Your items stay exactly where they are.'));
      } else {
        tp.appendChild(el('p', '', 'Fully grown. There is nothing above Colossal.'));
      }
    }
  }
  function setMode(i) {
    S.mode = i;
    try { window.localStorage.setItem('shellpack.mode', String(i)); } catch (e) { /* ignore */ }
    $('bMode').textContent = MODES[i][1]; $('bMode').title = 'Sort by: ' + MODES[i][2] + ' (click to change)';
    renderTabs();
  }

  // ------------------------------------------------------------ moving items
  function busy() {
    S.pending = true; clearTimeout(S.pendingTimer);
    S.pendingTimer = setTimeout(function () { S.pending = false; }, 1500);
  }
  function half(n) { return Math.max(1, Math.ceil(n / 2)); }
  function clearSel() { S.sel = null; paintCursor(); var s = document.querySelector('.slot.sel'); if (s) s.classList.remove('sel'); }

  function slotOf(node) { return node && node.closest ? node.closest('.slot') : null; }
  var down = null, lastPointer = { x: 0, y: 0 };
  document.addEventListener('contextmenu', function (e) { e.preventDefault(); });
  document.addEventListener('pointerdown', function (e) {
    var n = slotOf(e.target); if (!n) return;
    e.preventDefault();
    down = { n: n, btn: e.button, x: e.clientX, y: e.clientY, moved: false };
    if (!S.sel) { var it0 = itemAt(n.dataset.c, +n.dataset.i); if (it0) paintCursor(it0); }
  });
  document.addEventListener('pointermove', function (e) {
    $('hint').className = 'gone';
    lastPointer = { x: e.clientX, y: e.clientY };
    if (down && !down.moved && Math.abs(e.clientX - down.x) + Math.abs(e.clientY - down.y) > 6) down.moved = true;
    placeCursor(); placeTip(e);
    var n = slotOf(e.target);
    if (n) showTip(n); else hideTip();
  });
  document.addEventListener('pointerup', function (e) {
    var d = down; down = null;
    if (!d) return;
    var up = slotOf(document.elementFromPoint(e.clientX, e.clientY));
    if (!S.sel) paintCursor();
    onSlotInput(d, up, e);
  });

  function onSlotInput(d, up, e) {
    if (S.pending) return;
    var c = d.n.dataset.c, i = +d.n.dataset.i, it = itemAt(c, i);
    if (e.shiftKey && d.btn === 0) {
      if (it) quick(d.n, c, i, it);
      return;
    }
    var dragged = up && up !== d.n && d.moved;
    if (dragged) {
      var from = S.sel || (it ? { c: c, i: i, k: it.k, count: d.btn === 2 ? half(it.n) : it.n, it: it } : null);
      if (from) moveTo(from, up);
      return;
    }
    // a click
    if (S.sel) {
      if (S.sel.c === c && S.sel.i === i) { clearSel(); return; }
      var f2 = S.sel;
      if (d.btn === 2) f2 = { c: f2.c, i: f2.i, k: f2.k, count: 1, it: f2.it };
      moveTo(f2, d.n);
      return;
    }
    if (it) {
      S.sel = { c: c, i: i, k: it.k, count: d.btn === 2 ? half(it.n) : it.n, it: it };
      d.n.classList.add('sel'); paintCursor(it, S.sel.count);
    }
  }

  function moveTo(from, targetNode) {
    var tc = targetNode.dataset.c, ti = +targetNode.dataset.i, tIt = itemAt(tc, ti);
    var src = from.it || itemAt(from.c, from.i);
    if (!src) { clearSel(); return; }
    if (tc === 's' && src.bad) { toast(src.bad, false); clearSel(); return; }
    if (from.c === tc && from.i === ti) { clearSel(); return; }
    if (from.c === 's' && tIt && tIt.bad && from.count === src.n) { toast(tIt.bad, false); clearSel(); return; }
    busy();
    pendingFlyFrom(targetNode, src);
    send({ action: 'move', from: { c: from.c, i: from.i, k: from.k }, to: { c: tc, i: ti, k: tIt ? tIt.k : '' }, count: from.count });
    clearSel();
  }
  function quick(node, c, i, it) {
    if (c === 'p' && it.bad) { toast(it.bad, false); return; }
    busy();
    S.fly = { from: centerOf(node), toGrid: c === 'p' ? 'sg' : 'ig', id: it, c: c, i: i, key: it.k };
    send({ action: 'quick', from: { c: c, i: i, k: it.k } });
    clearSel();
  }
  function pendingFlyFrom() { /* moves are drag/drop: the picture already travelled with the pointer */ }
  function runPendingFly() {
    var f = S.fly; S.fly = null;
    if (!f || !S.prev) return;
    var now = itemAt(f.c, f.i);
    if (now && now.k === f.key) return;   // nothing left the slot
    var to = centerOf($(f.toGrid));
    flyIcon(f.from, to, f.id);
  }

  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') { if (S.sel) { clearSel(); e.stopPropagation(); } } });

  $('bSort').addEventListener('click', function () { if (S.pending) return; busy(); send({ action: 'sort', mode: MODES[S.mode][0] }); S.popAll = true; });
  $('bMode').addEventListener('click', function () { setMode((S.mode + 1) % 3); });
  $('bUp').addEventListener('click', function () { if (S.pending) return; busy(); send({ action: 'stow' }); });
  $('bDn').addEventListener('click', function () { if (S.pending) return; busy(); send({ action: 'withdraw' }); });
  $('bClose').addEventListener('click', closeGui);
  window.addEventListener('beforeunload', function () { send({ action: 'close' }); });

  // ------------------------------------------------------------ cursor picture, tooltips
  function paintCursor(it, count) {
    var c = $('cur');
    if (!it && S.sel) { it = S.sel.it; count = S.sel.count; }
    if (!it) { c.style.display = 'none'; c.innerHTML = ''; return; }
    c.innerHTML = '';
    var ic = iconNode(it, 32); ic.style.position = 'static'; c.appendChild(ic);
    if (count > 1 || (count === undefined && it.n > 1)) c.appendChild(el('i', '', String(count || it.n)));
    c.style.display = 'block'; placeCursor();
  }
  function placeCursor() {
    var c = $('cur'); if (c.style.display === 'none') return;
    var p = toStage(lastPointer.x, lastPointer.y);
    c.style.left = (p.x - 4) + 'px'; c.style.top = (p.y - 4) + 'px';
  }
  function showTip(n) {
    var it = itemAt(n.dataset.c, +n.dataset.i), tip = $('tip');
    if (!it || S.sel) { hideTip(); return; }
    tip.innerHTML = '';
    var b = el('b', '', it.name + (it.n > 1 ? '  ×' + it.n : '')); tip.appendChild(b);
    (it.t || []).forEach(function (l) { tip.appendChild(el('span', '', l)); });
    if (it.bad) tip.appendChild(el('span', 'warn', it.bad));
    if (n.classList.contains('over')) tip.appendChild(el('span', 'warn', 'Above this shellpack’s capacity: take it out.'));
    tip.style.display = 'block';
  }
  function hideTip() { $('tip').style.display = 'none'; }
  function placeTip(e) {
    var tip = $('tip'); if (tip.style.display === 'none') return;
    var p = toStage(e.clientX, e.clientY);
    var x = p.x + 16, y = p.y + 16;
    if (x + tip.offsetWidth > 1590) x = p.x - tip.offsetWidth - 12;
    if (y + tip.offsetHeight > 890) y = p.y - tip.offsetHeight - 12;
    tip.style.left = x + 'px'; tip.style.top = y + 'px';
  }

  // ------------------------------------------------------------ flare (short, never looping)
  function flyIcon(a, b, it) {
    if (REDUCED || !a || !b) return;
    var n = iconNode(it, 32); n.className = 'fly'; n.style.left = (a.x - 17) + 'px'; n.style.top = (a.y - 17) + 'px';
    n.style.position = 'absolute'; n.style.inset = 'auto';
    $('stage').appendChild(n);
    var dx = b.x - a.x, dy = b.y - a.y;
    n.animate([{ transform: 'translate(0,0) scale(1)' }, { transform: 'translate(' + dx / 2 + 'px,' + (dy / 2 - 36) + 'px) scale(1.25)', offset: .5 },
      { transform: 'translate(' + dx + 'px,' + dy + 'px) scale(.7)', opacity: .3 }], { duration: 480, easing: 'ease-in-out' }).onfinish = function () { n.remove(); };
  }
  function flights(kind) {
    if (REDUCED || !S.st) return;
    var from = kind === 'stow' ? 'p' : 's', to = kind === 'stow' ? $('sg') : $('ig');
    var target = centerOf(to), sel = document.querySelectorAll('.slot[data-c="' + from + '"]'), shown = 0;
    var other = kind === 'stow' ? S.st.shell : S.st.inv, have = {};
    other.forEach(function (x) { if (x) have[x.id] = 1; });
    Array.prototype.forEach.call(sel, function (n) {
      if (shown >= 6) return;
      var it = itemAt(from, +n.dataset.i);
      if (!it || !have[it.id]) return;
      var a = centerOf(n), delay = shown * 70;
      shown++;
      setTimeout(function () { flyIcon(a, target, it); }, delay);
    });
  }
  function sparkle(x, y, col, n) {
    if (REDUCED) return;
    for (var i = 0; i < n; i++) {
      var d = el('div', 'spk'); d.style.left = x + 'px'; d.style.top = y + 'px'; d.style.background = i % 3 ? col : '#fff';
      $('stage').appendChild(d);
      var a = Math.random() * 6.28, r = 40 + Math.random() * 110;
      d.animate([{ transform: 'translate(0,0) scale(1)', opacity: 1 }, { transform: 'translate(' + Math.cos(a) * r + 'px,' + (Math.sin(a) * r - 20) + 'px) scale(0)', opacity: 0 }],
        { duration: 650 + Math.random() * 450, easing: 'ease-out' }).onfinish = (function (q) { return function () { q.remove(); }; })(d);
    }
  }
  var toastTimer = 0;
  function toast(msg, ok) {
    if (!msg) return;
    var t = $('toast'); t.textContent = msg; t.className = 'show ' + (ok ? 'ok' : 'bad');
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { t.className = ''; }, 2600);
  }

  // ------------------------------------------------------------ start
  setInterval(function () { send({ action: 'ping' }); }, 3000);
  send({ action: 'ready' });
})();
