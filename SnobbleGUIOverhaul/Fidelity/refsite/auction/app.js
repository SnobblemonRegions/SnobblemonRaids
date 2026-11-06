/* Auction House page. It only draws what the server sends and reports what the player clicked;
   every rule (price, ownership, money, slots) is enforced again on the server. */
(function () {
  'use strict';
  var CH = 'snobbleauctions';
  var $ = function (id) { return document.getElementById(id); };
  var UNDER = 50000;
  var STAT = ['HP', 'ATK', 'DEF', 'SPA', 'SPD', 'SPE'];
  var SIZE = { tile: 128, big: 128, huge: 256, small: 64, slot: 32 };

  var S = {
    view: 'browse',
    q: { text: '', shiny: false, legendary: false, hiddenAbility: false, perfect5: false, under: false, sort: 'newest', page: 0 },
    st: null,          // last header info (balance, slots, tax...)
    pages: 1,
    sellSlot: -1,
    party: [],
    detail: null,
    skew: 0,           // server time minus local time
    atlas: null,
    tab: 'mon',        // 'mon' (Pokemon) or 'item'
    cat: '', mod: '', isort: 'newest',
    itemSlot: -1, inv: [], iqty: 1, imode: 'all',
    prefs: { theme: 'gray', zoom: 100, glow: false, tips: true },
    icons: null        // item picture index: item id -> where it is in a picture sheet
  };
  var ISORTS = [['newest', 'Newest'], ['price_asc', 'Price: low to high'], ['price_desc', 'Price: high to low'], ['unit', 'Price per item'], ['name', 'Name']];
  var MSORTS = [['newest', 'Newest'], ['price_asc', 'Price: low to high'], ['price_desc', 'Price: high to low'], ['level', 'Level'], ['iv', 'IV total']];

  // ------------------------------------------------------------ plumbing
  function send(o) {
    o.channel = CH;
    if (window.webgui && typeof window.webgui.postToGame === 'function') window.webgui.postToGame(o);
    else console.log('[auction] no webgui bridge', o);
  }
  function norm(x) {
    if (x && x.detail !== undefined) x = x.detail;
    if (typeof x === 'string') { try { x = JSON.parse(x); } catch (e) { return null; } }
    if (x && typeof x === 'object' && x.payload && !x.view && !x.message) {
      var p = x.payload; if (typeof p === 'string') { try { p = JSON.parse(p); } catch (e) { return null; } } x = p;
    }
    return x;
  }
  window.addEventListener('webgui:state', function (e) { var d = norm(e); if (d) onState(d); });
  window.addEventListener('webgui:result', function (e) { var d = norm(e); if (d) onResult(d); });

  /* The window is a fixed 1200x720 and is scaled to a share of the screen (like the Ascension pages); the player's size setting multiplies that. */
  var FILL = 0.74, W = 1200, H = 720;
  function fit() {
    var w = window.innerWidth || 1920, h = window.innerHeight || 1080;
    var s = Math.min(w * FILL / W, h * FILL / H) * S.prefs.zoom / 100;
    s = Math.min(s, w * 0.98 / W, h * 0.98 / H);   // however large the player asks, it never grows past the screen
    s = Math.max(0.4, Math.min(3, s));
    $('stage').style.transform = 'translate(' + (-W / 2 * s) + 'px,' + (-H / 2 * s) + 'px) scale(' + s + ')';
  }
  window.addEventListener('resize', fit);

  /* Look settings: kept on the server per player (setprefs), applied here as soon as they change. */
  var THEMES = [['gray', 'Gray', ['#242c33', '#5d7280', '#cfe3f0']], ['violet', 'Violet', ['#1d1442', '#6a4fd0', '#cdb4ff']], ['emerald', 'Emerald', ['#12362d', '#2f9a7c', '#7ff0c6']],
    ['crimson', 'Crimson', ['#3a1119', '#b0384c', '#ffb3bd']], ['ocean', 'Ocean', ['#10305a', '#2e7fc4', '#9fd4ff']], ['amber', 'Amber', ['#3a2a10', '#b98a32', '#ffd98a']]];
  function applyPrefs(p) {
    if (!p) return;
    S.prefs = { theme: p.theme || 'gray', zoom: p.zoom || 100, glow: !!p.glow, tips: p.tips !== false };
    document.documentElement.setAttribute('data-theme', S.prefs.theme);
    document.body.classList.toggle('glow', S.prefs.glow);
    $('zoomPct').textContent = S.prefs.zoom + '%'; $('zoomPct2').textContent = S.prefs.zoom + '%';
    $('glowSw').classList.toggle('on', S.prefs.glow); $('tipsSw').classList.toggle('on', S.prefs.tips);
    Array.prototype.forEach.call(document.querySelectorAll('.theme'), function (b) { b.classList.toggle('on', b.dataset.t === S.prefs.theme); });
    if (!S.prefs.tips) hideTip();
    fit();
  }
  function setPref(o) { applyPrefs(Object.assign({}, S.prefs, o)); var m = Object.assign({ action: 'setprefs' }, o); send(m); }
  function zoomBy(d) { setPref({ zoom: Math.max(60, Math.min(160, S.prefs.zoom + d)) }); }
  THEMES.forEach(function (t) {
    var b = el('button', 'theme'); b.dataset.t = t[0]; b.appendChild(el('span', '', t[1]));
    var sw = el('span', 'sw'); t[2].forEach(function (c) { var i = el('i'); i.style.background = c; sw.appendChild(i); }); b.appendChild(sw);
    b.addEventListener('click', function () { setPref({ theme: t[0] }); });
    $('themes').appendChild(b);
  });
  $('zoomOut').addEventListener('click', function () { zoomBy(-10); }); $('zoomIn').addEventListener('click', function () { zoomBy(10); });
  $('zoomOut2').addEventListener('click', function () { zoomBy(-10); }); $('zoomIn2').addEventListener('click', function () { zoomBy(10); });
  $('glowSw').addEventListener('click', function () { setPref({ glow: !S.prefs.glow }); });
  $('tipsSw').addEventListener('click', function () { setPref({ tips: !S.prefs.tips }); });
  $('setBtn').addEventListener('click', function () { hideTip(); $('settings').hidden = false; });
  $('setDone').addEventListener('click', function () { $('settings').hidden = true; });
  fit();

  /* Old saves may still hold a raw translation key such as cobblemon.nature.careful. */
function pretty(s) {
  s = String(s || '');
  s = s.substring(s.lastIndexOf('.') + 1).replace(/_/g, ' ');
  return s.replace(/\b\w/g, function (c) { return c.toUpperCase(); });
}
/* Counts a number up or down to its new value. */
function tickNumber(node, to, fmt) {
  var from = node._v === undefined ? to : node._v; node._v = to;
  if (node._r) cancelAnimationFrame(node._r);
  if (from === to || window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) { node.textContent = fmt(to); return; }
  var t0 = performance.now(), d = 600;
  (function step(t) {
    var k = Math.min(1, (t - t0) / d); k = 1 - Math.pow(1 - k, 3);
    node.textContent = fmt(Math.round(from + (to - from) * k));
    if (k < 1) node._r = requestAnimationFrame(step);
  })(t0);
}
function money(n) { return '$ ' + Number(n).toLocaleString('en-US'); }
  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text !== undefined) e.textContent = text; return e; }
  function now() { return Date.now() + S.skew; }
  function left(ms) {
    var m = Math.max(0, Math.floor((ms - now()) / 60000));
    if (m >= 1440) return Math.floor(m / 1440) + 'd ' + Math.floor((m % 1440) / 60) + 'h left';
    if (m >= 60) return Math.floor(m / 60) + 'h ' + (m % 60) + 'm left';
    return m + 'm left';
  }
  function genderText(g) { return g === 'MALE' ? 'Male' : g === 'FEMALE' ? 'Female' : 'Genderless'; }

  // ------------------------------------------------------------ sprites
  fetch('sprites/atlas.json').then(function (r) { return r.ok ? r.json() : null; }).then(function (a) {
    if (a && a.sprites && a.sheets) { S.atlas = a; if (S.st) rerender(); }
  }).catch(function () {});

  // Item pictures: sheets built on the server from the mods' own textures (icons/), plus an optional vanilla pack (icons/vanilla/).
  function loadIcons(base) {
    var file = base === 'icons' ? 'icons.json' : base === 'icons/custom' ? 'custom_icons.json' : 'vanilla_icons.json';
    return fetch(base + '/' + file).then(function (r) { return r.ok ? r.json() : null; }).catch(function () { return null; }).then(function (j) {
      if (!j || !j.icons) return;
      S.icons = S.icons || { map: {} };
      Object.keys(j.icons).forEach(function (id) {
        var p = j.icons[id], sh = j.sheets[p[2] || 0];
        S.icons.map[id] = { url: base + '/' + sh.file, w: sh.w, h: sh.h, c: p[0], r: p[1], cell: j.cell };
      });
    });
  }
  loadIcons('icons').then(function () { return loadIcons('icons/vanilla'); }).then(function () { return loadIcons('icons/custom'); }).then(function () { paintCats(); if (S.st && S.icons) rerender(); });

  /* Small item pictures on the category buttons. */
  function paintCats() {
    if (!S.icons) return;
    Array.prototype.forEach.call(document.querySelectorAll('.catbtn'), function (b) {
      var ic = S.icons.map[b.dataset.ic], box = b.firstChild;
      if (!ic || !box) return;
      if (sheetState[ic.url] !== 'ready') { wantSheet(ic.url); return; }
      var k = 24 / ic.cell;
      box.style.backgroundImage = 'url("' + ic.url + '")';
      box.style.backgroundSize = (ic.w * k) + 'px ' + (ic.h * k) + 'px';
      box.style.backgroundPosition = (-ic.c * 24) + 'px ' + (-ic.r * 24) + 'px';
    });
  }

  function hue(s) { var h = 0; for (var i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360; return h; }
  /* Server-side items share a plain item and differ by custom_model_data, so that is tried first. */
  function iconFor(it) {
    var m = S.icons.map, c = it.cmd ? '#' + it.cmd : '';
    return (c && (m[it.itemId + c] || (it.iconId && m[it.iconId + c]))) || m[it.itemId] || (it.iconId && m[it.iconId]) || null;
  }
  /* An item picture at a whole-number multiple of its 32px size so the pixels stay sharp. */
  function iconBox(it, cls) {
    var px = SIZE[cls] || 110, box = el('div', 'sprite icon ' + cls), ic = S.icons && iconFor(it);
    var waiting = false;
    if (ic && sheetState[ic.url] !== 'ready') { wantSheet(ic.url); ic = null; waiting = true; }
    if (ic) {
      var draw = px >= ic.cell ? Math.floor(px / ic.cell) * ic.cell : px, k = draw / ic.cell, i = el('div', 'cardicon');
      i.style.imageRendering = draw < ic.cell ? 'auto' : 'pixelated';
      i.style.width = draw + 'px'; i.style.height = draw + 'px';
      i.style.backgroundImage = 'url("' + ic.url + '")';
      i.style.backgroundSize = (ic.w * k) + 'px ' + (ic.h * k) + 'px';
      i.style.backgroundPosition = (-ic.c * draw) + 'px ' + (-ic.r * draw) + 'px';
      i.style.backgroundRepeat = 'no-repeat';
      box.appendChild(i);
    } else {
      var f = el('div', 'fallback' + (waiting ? ' loading' : ''), String(it.name || '?').replace(/[^A-Za-z0-9]/g, '').slice(0, 2).toUpperCase());
      f.style.borderColor = 'hsl(' + hue(it.itemId || it.name || '') + ',45%,55%)';
      box.appendChild(f);
    }
    if (it.count > 1 && (cls === 'small' || cls === 'big' || cls === 'slot')) box.appendChild(el('span', 'cnt', String(it.count)));
    return box;
  }
  function art(it, cls) { return it.kind === 'ITEM' ? iconBox(it, cls) : sprite(it, cls); }

  /* WebGUI limits how fast it sends files, so sprite sheets are fetched one at a time (the ones on screen first) and retried if dropped. */
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
      if (ok) { sheetState[url] = 'ready'; paintCats(); rerender(); }
      else if (sheetTries[url] < 4) { sheetState[url] = 'queued'; sheetQueue.push(url); }
      else sheetState[url] = 'fail';
      setTimeout(function () { sheetBusy = false; pumpSheets(); }, ok ? 400 : 2500);
    };
    img.onload = function () { finish(true); };
    img.onerror = function () { finish(false); };
    img.src = url;
  }
  function sprite(it, cls) {
    var px = SIZE[cls] || 110, box = el('div', 'sprite ' + cls);
    var a = S.atlas, key = it.sprite, pos = a && (a.sprites[key] || a.sprites[String(key).replace('+shiny', '')] || a.sprites[it.speciesId]);
    var pending = false;
    if (a && pos && sheetState['sprites/' + a.sheets[pos[2] || 0].file] !== 'ready') { wantSheet('sprites/' + a.sheets[pos[2] || 0].file); pos = null; pending = true; }
    if (a && pos) {
      var sc = px / a.cell, sh = a.sheets[pos[2] || 0];
      box.style.backgroundImage = 'url("sprites/' + sh.file + '")';
      box.style.backgroundSize = (sh.w * sc) + 'px ' + (sh.h * sc) + 'px';
      box.style.backgroundPosition = (-pos[0] * a.cell * sc) + 'px ' + (-pos[1] * a.cell * sc) + 'px';
      box.style.backgroundRepeat = 'no-repeat';
    } else {
      var f = el('div', 'fallback' + (pending || !a ? ' loading' : ''), String(it.species || '?').slice(0, 2).toUpperCase());
      box.appendChild(f);
    }
    return box;
  }

  function tags(it) {
    var w = el('div', 'tags');
    if (it.perfect !== undefined) w.appendChild(el('span', 'tag', it.perfect + '/6 IV'));
    if (it.aspects && it.aspects.indexOf('alpha') >= 0) w.appendChild(el('span', 'tag mag', 'ALPHA'));
    if (it.aspects && it.aspects.some(function (a) { return /boss/.test(a); })) w.appendChild(el('span', 'tag mag', 'BOSS'));
    if (it.shiny) w.appendChild(el('span', 'tag gold', 'SHINY'));
    if (it.legendary) w.appendChild(el('span', 'tag mag', 'LEGENDARY'));
    if (it.hiddenAbility) w.appendChild(el('span', 'tag dimt', 'HA'));
    return w;
  }

  // ------------------------------------------------------------ views
  function setView(v, fetchIt) {
    S.view = v; hideTip();
    $('vBrowse').hidden = v !== 'browse';
    $('vSell').hidden = !(v === 'sell' && S.tab === 'mon');
    $('vSellItem').hidden = !(v === 'sell' && S.tab === 'item');
    $('vMine').hidden = v !== 'mine';
    $('tabMon').classList.toggle('on', S.tab === 'mon'); $('tabItem').classList.toggle('on', S.tab === 'item');
    $('monFilters').hidden = S.tab !== 'mon'; $('itemFilters').hidden = S.tab !== 'item';
    buildSorts();
    $('railBrowse').hidden = v !== 'browse';
    $('railOther').hidden = v === 'browse';
    $('pager').style.visibility = v === 'browse' ? 'visible' : 'hidden';
    var tn = S.tab === 'item' ? ' - ITEMS' : '';
    $('search').placeholder = S.tab === 'item' ? 'item or mod name...' : 'species, move, ability...';
    $('crumbText').textContent = v === 'browse' ? '/ LIVE LISTINGS' + tn : v === 'sell' ? '/ SELL' + tn : '/ MY LISTINGS';
    $('railHint').textContent = v === 'sell'
      ? (S.tab === 'item' ? 'Pick items from your inventory, choose how many and set a price. They leave your inventory until sold or taken back.'
        : 'Pick a Pokemon from your party, set a price and list it. It leaves your party until it sells or you take it back.')
      : 'Your active listings, expired listings waiting to be collected, and your recent sales.';
    ['navBrowse', 'navSell', 'navMine'].forEach(function (id) { $(id).classList.remove('active'); });
    $(v === 'browse' ? 'navBrowse' : v === 'sell' ? 'navSell' : 'navMine').classList.add('active');
    if (fetchIt !== false) request();
  }
  function buildSorts() {
    var box = $('sorts'), list = S.tab === 'item' ? ISORTS : MSORTS, cur = S.tab === 'item' ? S.isort : S.q.sort;
    box.textContent = '';
    list.forEach(function (o) {
      var b = el('button', o[0] === cur ? 'on' : '', o[1]); b.dataset.s = o[0];
      b.addEventListener('click', function () {
        if (S.tab === 'item') S.isort = o[0]; else S.q.sort = o[0];
        S.q.page = 0; buildSorts(); request();
      });
      box.appendChild(b);
    });
  }
  function setTab(t) {
    if (S.tab === t) return;
    S.tab = t; S.q.page = 0; S.itemSlot = -1; closeOverlay();
    setView(S.view);
  }
  function request() {
    if (S.view === 'browse') {
      var q = S.q;
      if (S.tab === 'item') send({ action: 'browse', kind: 'item', text: q.text, category: S.cat, mod: S.mod, maxPrice: 0, sort: S.isort, page: q.page });
      else send({ action: 'browse', text: q.text, shiny: q.shiny, legendary: q.legendary, hiddenAbility: q.hiddenAbility,
        minPerfectIvs: q.perfect5 ? 5 : 0, maxPrice: q.under ? UNDER : 0, sort: q.sort, page: q.page });
    } else if (S.view === 'sell') send(S.tab === 'item' ? { action: 'sellview', kind: 'item' } : { action: 'sellview' });
    else send({ action: 'mine' });
  }

  function onState(d) {
    if (d.now) S.skew = d.now - Date.now();
    if (d.prefs) applyPrefs(d.prefs);
    if (d.balance !== undefined) {
      S.st = { balance: d.balance, slotsUsed: d.slotsUsed, slotsMax: d.slotsMax, taxRate: d.taxRate, maxPrice: d.maxPrice, canSell: d.canSell, buyOwn: d.buyOwn, cancelDelay: d.cancelDelay || 0,
        itemMax: d.itemMaxPrice, itemMinEach: d.itemMinEach, itemTax: d.itemTaxRate };
      $('tabItem').hidden = d.allowItems === false;
      if (d.title) $('title').textContent = d.title;
      header();
    }
    if (d.view === 'browse') renderBrowse(d);
    else if (d.view === 'detail') renderDetail(d.listing);
    else if (d.view === 'mine') renderMine(d);
    else if (d.view === 'sell') { if (d.kind === 'item') renderSellItems(d); else renderSell(d); }
  }
  function header() {
    tickNumber($('balance'), S.st.balance, money);
    $('slots').textContent = S.st.slotsUsed + ' / ' + S.st.slotsMax;
    $('slots').style.color = S.st.slotsUsed >= S.st.slotsMax ? 'var(--mag)' : '';
  }
  function rerender() { request(); }

  function burst() {
    var b = el('div', 'burst');
    for (var i = 0; i < 18; i++) {
      var s = el('i'), a = (i / 18) * Math.PI * 2 + Math.random() * 0.3, r = 140 + Math.random() * 180;
      s.style.setProperty('--dx', Math.round(Math.cos(a) * r) + 'px'); s.style.setProperty('--dy', Math.round(Math.sin(a) * r) + 'px');
      s.style.animationDelay = Math.round(Math.random() * 120) + 'ms'; b.appendChild(s);
    }
    $('stage').appendChild(b); setTimeout(function () { if (b.parentNode) b.parentNode.removeChild(b); }, 1300);
  }

  var toastTimer = null;
  function toast(msg, ok) {
    var t = $('toast'); t.textContent = msg; t.className = ok ? '' : 'bad'; t.hidden = false; void t.offsetWidth; t.classList.add('in');
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { t.hidden = true; }, 4500);
  }
  function onResult(d) {
    if (d.message) toast(d.message, d.ok);
    if (d.action === 'buy') {
      if (!d.ok) { closeOverlay(); return; }
      var host = !$('confirm').hidden ? $('confirm') : $('detail'), fx = el('i', 'sweepfx');
      host.appendChild(fx); burst();
      setTimeout(function () { if (fx.parentNode) fx.parentNode.removeChild(fx); closeOverlay(); request(); }, 700);
    }
    if (d.action === 'sell' && d.ok) { S.sellSlot = -1; S.itemSlot = -1; request(); }
    if (d.action === 'sell' || d.action === 'cancel') closeOverlay();
    if ((d.action === 'cancel' || d.action === 'collect') && S.view === 'mine') request();
    if (d.action === 'detail' && !d.ok) closeOverlay();
  }

  // ------------------------------------------------------------ rarity + hover tooltip
  var CATS = { tools: 'Tool', blocks: 'Block', food: 'Food', cobblemon: 'Cobblemon', misc: 'Item' };
  function rarityOf(it) {
    if (it.kind === 'ITEM') {
      var r = String(it.rarity || 'COMMON').toUpperCase();
      return { cls: r === 'UNCOMMON' ? 'r-unc' : r === 'RARE' ? 'r-rare' : r === 'EPIC' ? 'r-epic' : '', label: CATS[it.category] || 'Item' };
    }
    if (it.aspects && it.aspects.indexOf('alpha') >= 0) return { cls: 'r-leg', label: 'Alpha' };
    if (it.legendary) return { cls: 'r-leg', label: 'Legendary' };
    if (it.shiny) return { cls: 'r-gold', label: 'Shiny' };
    return { cls: it.perfect >= 5 ? 'r-epic' : it.perfect >= 3 ? 'r-rare' : '', label: 'Pokemon' };
  }
  function row(k, v, cls) {
    var d = el('div'); d.appendChild(el('span', 'k', k + '  ')); d.appendChild(el('span', cls || 'v', v)); return d;
  }
  function showTip(it, e) {
    if (!S.prefs.tips) return;
    var tt = $('tt'); tt.textContent = '';
    var isItem = it.kind === 'ITEM', rc = rarityOf(it);
    var t = el('div', 't ' + rc.cls, it.name); tt.appendChild(t);
    if (isItem) {
      var rar = String(it.rarity || 'COMMON'); tt.appendChild(el('div', 'mod', it.modName + '  -  ' + rar.charAt(0) + rar.slice(1).toLowerCase() + '  -  ' + rc.label));
      var lines = it.lines || [];
      if (lines.length) tt.appendChild(document.createElement('hr'));
      lines.slice(0, 9).forEach(function (x) { tt.appendChild(el('div', /^[A-Z].* [IVX]+$/.test(x) ? 'e' : 'l', x)); });
      if (lines.length > 9) tt.appendChild(el('div', 'k', '+ ' + (lines.length - 9) + ' more lines'));
    } else {
      var form = it.form && it.form.toLowerCase() !== 'normal' && it.form.toLowerCase() !== 'standard' ? ' - ' + it.form : '';
      tt.appendChild(el('div', 'mod', it.species + form + '  -  Lv. ' + it.level + '  -  ' + genderText(it.gender)));
      tt.appendChild(document.createElement('hr'));
      if (it.nature) tt.appendChild(row('Nature', pretty(it.nature)));
      if (it.ability) tt.appendChild(row('Ability', pretty(it.ability) + (it.hiddenAbility ? ' (HA)' : '')));
      if (it.ivs) {
        var g = el('div', 'iv');
        for (var i = 0; i < 6; i++) { var b = el('b', it.ivs[i] >= 31 ? 'max' : '', String(it.ivs[i])); var c = el('span', 'k', STAT[i] + ' '); c.appendChild(b); g.appendChild(c); }
        tt.appendChild(g);
      }
      if (it.moves && it.moves.length) tt.appendChild(row('Moves', it.moves.join(', ')));
    }
    tt.appendChild(document.createElement('hr'));
    tt.appendChild(row('Seller', it.seller));
    if (isItem && it.count > 1) tt.appendChild(row('Price each', money(Math.round(it.price / it.count)), 'p'));
    tt.appendChild(row(isItem && it.count > 1 ? 'Total' : 'Price', money(it.price), 'p'));
    tt.appendChild(row('Ends in', left(it.expiresAt).replace(' left', '')));
    tt.appendChild(el('div', 'hint', it.mine ? 'YOUR LISTING' : 'CLICK TO VIEW & BUY'));
    tt.hidden = false; moveTip(e);
  }
  var mouse = { x: -1, y: -1 };
  function moveTip(e) {
    mouse.x = e.clientX; mouse.y = e.clientY;
    var tt = $('tt'); if (tt.hidden) return;
    var st = $('stage').getBoundingClientRect(), s = st.width / $('stage').offsetWidth;
    var x = (e.clientX - st.left) / s + 22, y = (e.clientY - st.top) / s + 18;
    var w = tt.offsetWidth, h = tt.offsetHeight;
    if (x + w > W - 10) x = (e.clientX - st.left) / s - w - 22;
    if (y + h > H - 10) y = H - 10 - h;
    tt.style.left = Math.max(8, x) + 'px'; tt.style.top = Math.max(8, y) + 'px';
  }
  function hideTip() { $('tt').hidden = true; }
  // ------------------------------------------------------------ browse
  function renderBrowse(d) {
    S.pages = d.pages; S.q.page = d.page;
    var grid = $('grid'); grid.textContent = '';
    if (d.kind === 'item' && d.mods) fillMods(d.mods);
    var sig = S.tab + '|' + S.view + '|' + d.page + '|' + d.items.map(function (x) { return x.id; }).join(',');
    grid.classList.toggle('play', sig !== S.lastSig); S.lastSig = sig;
    d.items.forEach(function (it, idx) {
      var isItem = it.kind === 'ITEM', rc = rarityOf(it);
      var c = el('button', 'card ' + rc.cls);
      c.style.setProperty('--i', idx);
      c.appendChild(el('div', 'cat', rc.label));
      var pic = el('div', 'art'); pic.appendChild(art(it, 'tile'));
      if (isItem && it.count > 1) pic.appendChild(el('span', 'cnt', 'x' + it.count));
      c.appendChild(pic);
      c.appendChild(el('div', 'nm', it.name));
      if (isItem) {
        c.appendChild(el('div', 'lv', it.modName + (it.highlight ? '  -  ' + it.highlight : '')));
      } else {
        c.appendChild(el('div', 'lv', 'Lv. ' + it.level + '  ' + genderText(it.gender)));
        c.appendChild(tags(it));
      }
      var foot = el('div', 'foot');
      var tl;
      if (isItem && it.count > 1) tl = el('span', 'tl', money(Math.round(it.price / it.count)) + ' each');
      else { tl = el('span', 'tl', left(it.expiresAt)); tl.dataset.exp = it.expiresAt; }
      foot.appendChild(tl);
      foot.appendChild(el('span', 'pr', money(it.price)));
      c.appendChild(foot);
      c.addEventListener('click', function () { hideTip(); send({ action: 'detail', id: it.id }); });
      c._it = it;
      c.addEventListener('mouseenter', function (e) { showTip(it, e); });
      c.addEventListener('mousemove', moveTip);
      c.addEventListener('mouseleave', hideTip);
      grid.appendChild(c);
    });
    // the list is redrawn now and then; keep the tooltip of whatever card is under the pointer
    var under = document.elementFromPoint(mouse.x, mouse.y), uc = under && under.closest ? under.closest('.card') : null;
    if (uc && uc._it) showTip(uc._it, { clientX: mouse.x, clientY: mouse.y }); else hideTip();
    $('empty').hidden = d.items.length > 0;
    $('pageLabel').textContent = 'PAGE ' + (d.page + 1) + ' / ' + d.pages;
    $('prev').disabled = d.page <= 0; $('next').disabled = d.page >= d.pages - 1;
  }

  function fillMods(mods) {
    var sel = $('modSel'), cur = S.mod; sel.textContent = '';
    var any = el('option', '', 'Any mod'); any.value = ''; sel.appendChild(any);
    mods.forEach(function (m) { var o = el('option', '', m.name); o.value = m.mod; sel.appendChild(o); });
    sel.value = cur;
  }

  // ------------------------------------------------------------ detail + confirm
  function renderDetail(l) {
    S.detail = l;
    var isItem = l.kind === 'ITEM';
    ['hIv', 'dIvs', 'hMoves', 'dMoves'].forEach(function (id) { $(id).hidden = isItem; });
    $('hLines').hidden = !isItem; $('dLines').hidden = !isItem;
    if (isItem) { renderItemDetail(l); return; }
    $('dSprite').textContent = ''; $('dSprite').appendChild(sprite(l, 'huge'));
    $('dName').textContent = l.name + (l.name !== l.species ? ' (' + l.species + ')' : '');
    var form = l.form && l.form.toLowerCase() !== 'normal' && l.form.toLowerCase() !== 'standard' ? ' - ' + l.form : '';
    $('dMeta').textContent = 'Lv. ' + l.level + ' - ' + genderText(l.gender) + ' - ' + pretty(l.nature) + ' - ' + pretty(l.ability) + form;
    var tg = $('dTags'); tg.textContent = ''; tg.appendChild(tags(l));
    var ivs = $('dIvs'); ivs.textContent = '';
    for (var i = 0; i < 6; i++) {
      var v = l.ivs[i], row = el('div', 'ivrow'), bar = el('div', 'bar'), fill = el('i', v >= 31 ? 'max' : '');
      fill.style.width = Math.round(v / 31 * 100) + '%'; bar.appendChild(fill);
      row.appendChild(el('span', '', STAT[i])); row.appendChild(bar); row.appendChild(el('span', 'v', String(v)));
      ivs.appendChild(row);
    }
    var mv = $('dMoves'); mv.textContent = '';
    (l.moves || []).forEach(function (m) { mv.appendChild(el('div', '', m)); });
    $('dExtra').textContent = 'Ball: ' + pretty(l.ball) + (l.ot ? '   OT: ' + l.ot : '');
    $('dPrice').textContent = money(l.price);
    $('dTime').textContent = 'Listed by ' + l.seller + ' - ' + left(l.expiresAt);
    var own = l.mine && !(S.st && S.st.buyOwn);
    $('buyBtn').disabled = own || (S.st && S.st.balance < l.price);
    $('buyBtn').textContent = own ? 'YOUR LISTING' : (S.st && S.st.balance < l.price ? 'NOT ENOUGH MONEY' : 'BUY NOW');
    $('overlay').hidden = false; $('confirm').hidden = true; $('detail').hidden = false;
  }
  function renderItemDetail(l) {
    $('dSprite').textContent = ''; $('dSprite').appendChild(iconBox(l, 'huge'));
    $('dName').textContent = l.name;
    $('dMeta').textContent = l.modName + '  -  x' + l.count + (l.count > 1 ? '  -  ' + money(Math.round(l.price / l.count)) + ' each' : '');
    var tg = $('dTags'); tg.textContent = ''; if (l.highlight) { var w = el('div', 'tags'); w.appendChild(el('span', 'tag mag', l.highlight)); tg.appendChild(w); }
    var box = $('dLines'); box.textContent = '';
    box.appendChild(el('div', '', l.name));
    (l.lines || []).forEach(function (x) { box.appendChild(el('div', /^[A-Z].* [IVX]+$/.test(x) ? 'ench' : '', x)); });
    $('dExtra').textContent = 'Item id: ' + l.itemId;
    $('dPrice').textContent = money(l.price);
    $('dTime').textContent = 'Listed by ' + l.seller + ' - ' + left(l.expiresAt);
    var own = l.mine && !(S.st && S.st.buyOwn);
    $('buyBtn').disabled = own || (S.st && S.st.balance < l.price);
    $('buyBtn').textContent = own ? 'YOUR LISTING' : (S.st && S.st.balance < l.price ? 'NOT ENOUGH MONEY' : 'BUY NOW');
    $('overlay').hidden = false; $('confirm').hidden = true; $('detail').hidden = false;
  }
  function closeOverlay() { $('overlay').hidden = true; S.detail = null; }
  $('backBtn').addEventListener('click', closeOverlay);
  // one confirmation panel for buying, listing and cancelling
  var CF = null;
  function askConfirm(o) {
    CF = o;
    $('cTitle').textContent = o.title; $('cText').textContent = o.text;
    $('cMath').textContent = o.math; $('cMath').style.whiteSpace = 'pre-line';
    $('yesBtn').textContent = o.yes || 'CONFIRM'; $('noBtn').textContent = o.no || 'CANCEL';
    $('overlay').hidden = false; $('detail').hidden = true; $('confirm').hidden = false;
    hideTip();
  }
  $('buyBtn').addEventListener('click', function () {
    var l = S.detail; if (!l) return;
    var after = (S.st ? S.st.balance : 0) - l.price;
    askConfirm({
      title: 'CONFIRM PURCHASE', yes: 'CONFIRM', no: 'CANCEL', fromDetail: true,
      text: 'Pay ' + money(l.price) + ' for ' + l.name + (l.kind === 'ITEM' && l.count > 1 ? ' x' + l.count : '') + '?',
      math: (l.kind === 'ITEM' ? 'x' + l.count + ' ' + l.name + ' from ' + l.seller : 'Lv. ' + l.level + ' ' + l.species + ' from ' + l.seller) + '\nBalance after purchase: ' + money(after),
      onYes: function () { send({ action: 'buy', id: l.id }); }
    });
  });
  $('noBtn').addEventListener('click', function () {
    if (CF && CF.fromDetail && S.detail) { $('confirm').hidden = true; $('detail').hidden = false; } else closeOverlay();
  });
  $('yesBtn').addEventListener('click', function () {
    if (!CF) return;
    $('yesBtn').disabled = true; setTimeout(function () { $('yesBtn').disabled = false; }, 1500);
    var go = CF.onYes; if (!CF.fromDetail) closeOverlay();
    go();
  });
  function specials(it) { return it.aspects && it.aspects.indexOf('alpha') >= 0 ? ' (ALPHA)' : ''; }

  // ------------------------------------------------------------ my listings
  function renderMine(d) {
    var rows = $('mineRows'); rows.textContent = '';
    if (!d.items.length) rows.appendChild(el('div', 'none', 'You have nothing listed. Use SELL to start.'));
    d.items.forEach(function (it) {
      var isItem = it.kind === 'ITEM', thing = isItem ? 'items' : 'Pokemon';
      var r = el('div', 'lrow'); r.appendChild(art(it, 'small'));
      var info = el('div', 'li'); info.appendChild(el('div', 'ln', isItem ? it.name + '  x' + it.count : it.name + '  Lv. ' + it.level));
      info.appendChild(el('div', 'lp', money(it.price)));
      var st = it.state, label = st === 'ACTIVE' ? 'On sale - ' + left(it.expiresAt) : st === 'EXPIRED' ? 'Expired - collect your ' + thing : 'Cancelled - collect your ' + thing;
      info.appendChild(el('div', 'ls ' + (st === 'ACTIVE' ? 'ok' : 'warn'), label));
      r.appendChild(info);
      var b = el('button', st === 'ACTIVE' ? 'btn ghost danger' : 'btn solid gold', st === 'ACTIVE' ? 'CANCEL' : 'COLLECT');
      if (st === 'ACTIVE') {
        b.dataset.ready = it.createdAt + (S.st.cancelDelay || 0) * 1000;
        tickCancel(b);
        b.addEventListener('click', function () {
          if (b.disabled) return;
          askConfirm({
            title: 'CANCEL LISTING', yes: 'YES, CANCEL', no: 'KEEP IT',
            text: 'Take ' + it.name + (isItem ? ' x' + it.count : '') + ' off the Auction House?',
            math: 'It comes back to your ' + (isItem ? 'inventory.' : 'party, or your PC if the party is full.'),
            onYes: function () { send({ action: 'cancel', id: it.id }); }
          });
        });
      } else b.addEventListener('click', function () { send({ action: 'collect', id: it.id }); });
      r.appendChild(b); rows.appendChild(r);
    });
    var sales = $('saleRows'); sales.textContent = '';
    if (!d.sales.length) sales.appendChild(el('div', 'none', 'No sales yet.'));
    d.sales.forEach(function (s) {
      var r = el('div', 'srow'); r.appendChild(el('div', '', s.name + (s.kind === 'ITEM' && s.count > 1 ? ' x' + s.count : '') + ' to ' + s.buyer));
      var b = el('div', ''); b.appendChild(el('b', '', '+' + money(s.proceeds))); b.appendChild(document.createTextNode('  after tax')); r.appendChild(b);
      sales.appendChild(r);
    });
  }

  // a fresh listing cannot be cancelled for a short while, so the server can finish saving it
  function tickCancel(b) {
    var s = Math.ceil((Number(b.dataset.ready) - now()) / 1000);
    b.disabled = s > 0; b.textContent = s > 0 ? 'CANCEL (' + s + 's)' : 'CANCEL';
  }
  setInterval(function () {
    Array.prototype.forEach.call(document.querySelectorAll('button[data-ready]'), tickCancel);
  }, 1000);

  // ------------------------------------------------------------ sell
  function renderSell(d) {
    S.party = d.party;
    var list = $('partyList'); list.textContent = '';
    d.party.forEach(function (p) {
      var r = el('button', 'prow' + (p.empty ? ' empty' : '') + (p.blocked ? ' blocked' : '') + (S.sellSlot === p.slot ? ' sel' : ''));
      if (p.empty) { r.appendChild(el('div', 'pn', '(empty)')); }
      else {
        r.appendChild(sprite(p, 'small'));
        var info = el('div', ''); info.appendChild(el('div', 'pn', p.name));
        info.appendChild(el('div', 'pm', 'Lv. ' + p.level + ' - ' + p.perfect + '/6 IV' + (p.blocked ? ' - ' + p.blocked : '')));
        r.appendChild(info);
        if (!p.blocked) r.addEventListener('click', function () { S.sellSlot = p.slot; renderSell({ party: S.party }); });
      }
      list.appendChild(r);
    });
    var p = d.party[S.sellSlot];
    $('sellPick').hidden = !!(p && !p.empty && !p.blocked);
    $('sellBody').hidden = !(p && !p.empty && !p.blocked);
    if (p && !p.empty && !p.blocked) {
      var sp = $('sellSprite'); sp.textContent = ''; sp.appendChild(sprite(p, 'big'));
      $('sellName').textContent = p.name;
      $('sellMeta').textContent = 'Lv. ' + p.level + ' - ' + genderText(p.gender) + ' - ' + pretty(p.nature) + ' - ' + pretty(p.ability);
      var tg = $('sellTags'); tg.textContent = ''; tg.appendChild(tags(p));
      $('taxPct').textContent = Math.round(S.st.taxRate * 100) + '%';
      priceChanged();
    }
  }
  function priceValue() { return parseInt(($('price').value || '').replace(/[^0-9]/g, ''), 10) || 0; }
  function priceChanged() {
    var p = S.party[S.sellSlot]; if (!p || !S.st) return;
    var raw = ($('price').value || '').replace(/[^0-9]/g, '');
    $('price').value = raw ? Number(raw).toLocaleString('en-US') : '';
    var v = priceValue(), min = p.minPrice, max = S.st.maxPrice, tax = Math.floor(v * S.st.taxRate);
    var bad = v < min ? 'Minimum price is ' + money(min) : v > max ? 'Maximum price is ' + money(max) : S.st.slotsUsed >= S.st.slotsMax ? 'All your listing slots are in use' : '';
    $('priceHelp').textContent = bad || ('Minimum ' + money(min) + '   Maximum ' + money(max));
    $('priceHelp').style.color = bad && v ? 'var(--red)' : '';
    $('receive').textContent = money(v - tax);
    $('listBtn').disabled = !!bad || !v || !S.st.canSell;
  }
  $('price').addEventListener('input', priceChanged);
  $('listBtn').addEventListener('click', function () {
    var v = priceValue(), p = S.party[S.sellSlot]; if (S.sellSlot < 0 || !v || !p) return;
    var slot = S.sellSlot, tax = Math.floor(v * S.st.taxRate);
    askConfirm({
      title: 'CONFIRM LISTING', yes: 'LIST IT', no: 'BACK',
      text: 'List ' + p.name + specials(p) + ' for ' + money(v) + '?',
      math: 'Lv. ' + p.level + ' ' + p.species + ' - ' + p.perfect + '/6 IV\nTax ' + Math.round(S.st.taxRate * 100) + '%: you receive ' + money(v - tax) + ' when it sells\n' +
        'It leaves your party now and is held safely until it sells.' + cancelNote(),
      onYes: function () { $('listBtn').disabled = true; send({ action: 'sell', slot: slot, price: v }); $('price').value = ''; }
    });
  });
  function cancelNote() { var d = S.st && S.st.cancelDelay; return d ? '\nYou can cancel it ' + (d >= 60 ? Math.round(d / 60) + ' min' : d + 's') + ' after listing.' : ''; }

  // ------------------------------------------------------------ sell items
  function renderSellItems(d) {
    S.inv = d.inventory;
    var grid = $('invGrid'); grid.textContent = '';
    // backpack rows first (slots 9-35), then the hotbar (0-8), like the game shows them
    var order = []; for (var i = 9; i < 36; i++) order.push(i); order.push(-1); for (var h = 0; h < 9; h++) order.push(h);
    order.forEach(function (n) {
      if (n < 0) { grid.appendChild(el('div', 'gap')); return; }
      var s = d.inventory[n], b = el('button', 'islot' + (s && s.blocked ? ' blk' : '') + (S.itemSlot === n ? ' sel' : ''));
      if (s && !s.empty) {
        s.kind = 'ITEM'; b.appendChild(iconBox(s, 'slot')); b.title = s.name + (s.blocked ? ' (' + s.blocked + ')' : '');
        if (!s.blocked) b.addEventListener('click', function () { S.itemSlot = n; S.iqty = s.count; renderSellItems({ inventory: S.inv }); });
      }
      grid.appendChild(b);
    });
    var it = S.inv[S.itemSlot], ok = it && !it.empty && !it.blocked;
    $('itemPick').hidden = !!ok; $('itemBody').hidden = !ok;
    if (ok) {
      var ic = $('itemIcon'); ic.textContent = ''; var big = Object.assign({}, it, { count: 1 }); ic.appendChild(iconBox(big, 'big'));
      $('itemName').textContent = it.name; $('itemMod').textContent = it.modName; $('itemHave').textContent = 'You have ' + it.count;
      S.iqty = Math.max(1, Math.min(it.count, S.iqty || it.count));
      $('qty').value = S.iqty;
      $('itaxPct').textContent = Math.round((S.st.itemTax !== undefined ? S.st.itemTax : S.st.taxRate) * 100) + '%';
      itemPriceChanged(true);
    }
  }
  function itemPriceValue() { return parseInt(($('iprice').value || '').replace(/[^0-9]/g, ''), 10) || 0; }
  function itemTotal() { var v = itemPriceValue(); return S.imode === 'each' ? v * S.iqty : v; }
  function itemPriceChanged(keep) {
    var it = S.inv[S.itemSlot]; if (!it || !S.st) return;
    var raw = ($('iprice').value || '').replace(/[^0-9]/g, '');
    if (!keep) $('iprice').value = raw ? Number(raw).toLocaleString('en-US') : '';
    var total = itemTotal(), min = Math.max(1, (S.st.itemMinEach || 1) * S.iqty), max = S.st.itemMax || S.st.maxPrice;
    var rate = S.st.itemTax !== undefined ? S.st.itemTax : S.st.taxRate, tax = Math.floor(total * rate);
    var bad = total < min ? 'Minimum price is ' + money(min) : total > max ? 'Maximum price is ' + money(max) : S.st.slotsUsed >= S.st.slotsMax ? 'All your listing slots are in use' : '';
    $('ipriceHelp').textContent = bad || (S.imode === 'all' && S.iqty > 1 && total ? '= ' + money(Math.round(total / S.iqty)) + ' each' : 'Minimum ' + money(min) + '   Maximum ' + money(max));
    $('ipriceHelp').style.color = bad && total ? 'var(--red)' : '';
    $('itotal').textContent = money(total); $('ireceive').textContent = money(total - tax);
    $('ilistBtn').disabled = !!bad || !total || !S.st.canSell;
  }
  function setQty(n) {
    var it = S.inv[S.itemSlot]; if (!it) return;
    S.iqty = Math.max(1, Math.min(it.count, n || 1)); $('qty').value = S.iqty; itemPriceChanged(true);
  }
  $('qMinus').addEventListener('click', function () { setQty(S.iqty - 1); });
  $('qPlus').addEventListener('click', function () { setQty(S.iqty + 1); });
  $('qAll').addEventListener('click', function () { var it = S.inv[S.itemSlot]; if (it) setQty(it.count); });
  $('qty').addEventListener('input', function () { setQty(parseInt($('qty').value.replace(/[^0-9]/g, ''), 10) || 1); });
  $('iprice').addEventListener('input', function () { itemPriceChanged(false); });
  function setMode(m) {
    S.imode = m; $('modeAll').classList.toggle('on', m === 'all'); $('modeEach').classList.toggle('on', m === 'each'); itemPriceChanged(false);
  }
  $('modeAll').addEventListener('click', function () { setMode('all'); });
  $('modeEach').addEventListener('click', function () { setMode('each'); });
  $('ilistBtn').addEventListener('click', function () {
    var total = itemTotal(), it = S.inv[S.itemSlot]; if (S.itemSlot < 0 || !total || !it) return;
    var slot = S.itemSlot, qty = S.iqty, rate = S.st.itemTax !== undefined ? S.st.itemTax : S.st.taxRate;
    askConfirm({
      title: 'CONFIRM LISTING', yes: 'LIST IT', no: 'BACK',
      text: 'List ' + it.name + ' x' + qty + ' for ' + money(total) + '?',
      math: (qty > 1 ? money(Math.round(total / qty)) + ' each\n' : '') + 'Tax ' + Math.round(rate * 100) + '%: you receive ' + money(total - Math.floor(total * rate)) + ' when it sells\n' +
        'The items leave your inventory now and are held safely until they sell.' + cancelNote(),
      onYes: function () { $('ilistBtn').disabled = true; send({ action: 'sellitem', slot: slot, count: qty, price: total }); $('iprice').value = ''; }
    });
  });

  // ------------------------------------------------------------ controls
  $('tabMon').addEventListener('click', function () { setTab('mon'); });
  $('tabItem').addEventListener('click', function () { setTab('item'); });
  Array.prototype.forEach.call(document.querySelectorAll('#cats .catbtn'), function (b) {
    b.addEventListener('click', function () {
      S.cat = b.dataset.c; S.q.page = 0;
      Array.prototype.forEach.call(document.querySelectorAll('#cats .catbtn'), function (x) { x.classList.toggle('on', x === b); });
      request();
    });
  });
  $('modSel').addEventListener('change', function () { S.mod = $('modSel').value; S.q.page = 0; request(); });
  var searchTimer = null;
  $('search').addEventListener('input', function () {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(function () { S.q.text = $('search').value; S.q.page = 0; request(); }, 300);
  });
  Array.prototype.forEach.call(document.querySelectorAll('#filters .chk'), function (b) {
    b.addEventListener('click', function () {
      var f = b.dataset.f; S.q[f] = !S.q[f]; b.classList.toggle('on', S.q[f]); S.q.page = 0; request();
    });
  });

  $('prev').addEventListener('click', function () { if (S.q.page > 0) { S.q.page--; request(); } });
  $('next').addEventListener('click', function () { if (S.q.page < S.pages - 1) { S.q.page++; request(); } });
  $('navBrowse').addEventListener('click', function () { setView('browse'); });
  $('navSell').addEventListener('click', function () { setView('sell'); });
  $('navMine').addEventListener('click', function () { setView('mine'); });
  $('close').addEventListener('click', function () {
    if (window.webgui && typeof window.webgui.closeGui === 'function') window.webgui.closeGui();
  });

  // keep the countdowns and the list fresh without the player doing anything
  setInterval(function () {
    Array.prototype.forEach.call(document.querySelectorAll('.tl[data-exp]'), function (e) { e.textContent = left(Number(e.dataset.exp)); });
  }, 30000);
  setInterval(function () { if (S.view === 'browse' && $('overlay').hidden) request(); }, 20000);

  // ------------------------------------------------------------ start
  var m = /[?&]view=(\w+)/.exec(location.search), first = m ? m[1] : 'browse';
  if (/[?&]kind=item/.test(location.search)) S.tab = 'item';
  setView(first === 'sell' || first === 'mine' ? first : 'browse', false);
  send(S.tab === 'item' ? { action: 'ready', view: first, kind: 'item' } : { action: 'ready', view: first });
})();
