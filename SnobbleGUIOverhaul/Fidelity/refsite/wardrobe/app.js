/* Wardrobe page. It only draws what the server sends and reports what the player clicked;
   ownership and every other rule is enforced again on the server. */
(function () {
  'use strict';
  var CH = 'snobblecosmetics';
  var $ = function (id) { return document.getElementById(id); };
  var RARITY = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY', 'MYTHIC'];
  var CATS = [['hats', 'Hats'], ['back', 'Back'], ['armor', 'Armor'], ['tools', 'Tool Skins'], ['pokemon', 'Pokemon Skins'], ['balloons', 'Balloons'], ['effects', 'Effects']];
  var ALWAYS = { hats: 1, back: 1, armor: 1, tools: 1, pokemon: 1, balloons: 1, effects: 1 };
  var EMPTY = {
    hats: 'No hats yet.', back: 'No back items yet.', armor: 'No armor sets yet.', tools: 'No tool skins yet.',
    pokemon: 'No Pokemon skins yet.', balloons: 'No balloons yet.', effects: 'No effects yet.', all: 'No cosmetics yet.'
  };
  var SLOT_ORDER = ['Helmet', 'Chestplate', 'Leggings', 'Boots', 'Back'];
  var SLOT_ABBR = { Helmet: 'HE', Chestplate: 'CH', Leggings: 'LE', Boots: 'BO', Back: 'BA' };

  var S = {
    items: [], cat: 'hats', q: '', f: { owned: false, worn: false }, sort: 'rarity', page: 0, per: 8,
    cobblemon: true, preview: 15, sel: null, party: null, partyMode: false, first: true, flash: null, scale: 1
  };

  // ------------------------------------------------------------ plumbing
  function send(o) {
    o.channel = CH;
    if (window.webgui && typeof window.webgui.postToGame === 'function') window.webgui.postToGame(o);
    else console.log('[wardrobe] no webgui bridge', o);
  }
  function norm(x) {
    if (x && x.detail !== undefined) x = x.detail;
    if (typeof x === 'string') { try { x = JSON.parse(x); } catch (e) { return null; } }
    if (x && typeof x === 'object' && x.payload && !x.view && !x.message && !x.members) {
      var p = x.payload; if (typeof p === 'string') { try { p = JSON.parse(p); } catch (e) { return null; } } x = p;
    }
    return x;
  }
  window.addEventListener('webgui:state', function (e) { var d = norm(e); if (d) onState(d); });
  window.addEventListener('webgui:result', function (e) { var d = norm(e); if (d) onResult(d); });
  window.addEventListener('webgui:party', function (e) { var d = norm(e); if (d) onParty(d); });

  function fit() {
    var s = Math.min(window.innerWidth / 1600, window.innerHeight / 900);
    S.scale = s;
    $('stage').style.transform = 'translate(' + (-800 * s) + 'px,' + (-450 * s) + 'px) scale(' + s + ')';
  }
  window.addEventListener('resize', fit); fit();

  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text !== undefined) e.textContent = text; return e; }
  function reduced() { return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches; }
  function closeGui() { if (window.webgui && typeof window.webgui.closeGui === 'function') window.webgui.closeGui(); }
  function pretty(s) { return String(s || '').replace(/_/g, ' ').replace(/\b\w/g, function (c) { return c.toUpperCase(); }); }

  /* Counts a number up or down to its new value. */
  function tickNumber(node, to, fmt) {
    var from = node._v === undefined ? to : node._v; node._v = to;
    if (node._r) cancelAnimationFrame(node._r);
    if (from === to || reduced()) { node.textContent = fmt(to); return; }
    var t0 = performance.now(), d = 500;
    (function step(t) {
      var k = Math.min(1, (t - t0) / d); k = 1 - Math.pow(1 - k, 3);
      node.textContent = fmt(Math.round(from + (to - from) * k));
      if (k < 1) node._r = requestAnimationFrame(step);
    })(t0);
  }

  // ------------------------------------------------------------ colours and names
  /* Dark name colours would vanish on the dark panel: lighten them to a readable level. */
  function lift(hex) {
    var n = parseInt(String(hex).slice(1), 16); if (isNaN(n)) return '#e4ecf1';
    var r = n >> 16 & 255, g = n >> 8 & 255, b = n & 255, lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
    if (lum >= 0.5) return hex;
    var k = (0.5 - lum) / (1 - lum) + 0.05;
    function m(c) { return Math.min(255, Math.round(c + (255 - c) * k)); }
    return 'rgb(' + m(r) + ',' + m(g) + ',' + m(b) + ')';
  }
  function nameNode(i, cls) {
    var n = el('div', cls + (i.c1 ? ' gname' : ''), i.name);
    if (cls === 'nm') { var L = i.name.length; n.style.fontSize = (L <= 18 ? 21 : L <= 20 ? 19 : L <= 23 ? 17 : L <= 26 ? 15 : 14) + 'px'; }
    if (i.c1) { n.style.setProperty('--c1', lift(i.c1)); n.style.setProperty('--c2', lift(i.c2 || i.c1)); }
    return n;
  }
  function isWorn(i) { return !!i.equipped || (i.wornBy || 0) > 0; }

  // ------------------------------------------------------------ pictures
  function art(i) {
    var img = new Image(); img.alt = ''; img.draggable = false;
    var fb = el('span', 'fallback loading', String(i.name || '?').replace(/[^A-Za-z0-9]/g, '').slice(0, 2).toUpperCase());
    img.onload = function () { if (fb.parentNode) fb.parentNode.removeChild(fb); };
    img.onerror = function () { img.style.display = 'none'; fb.classList.remove('loading'); };
    img.src = 'thumbs/' + i.id + '.png';
    return [img, fb];
  }

  // ------------------------------------------------------------ list
  function hay(i) { return (i.name + ' ' + (i.set || '') + ' ' + i.slot + ' ' + (i.species || '') + ' ' + (i.desc || []).join(' ') + ' ' + i.rarity).toLowerCase(); }
  function visible() {
    var q = S.q.trim().toLowerCase();
    var list = S.items.filter(function (i) {
      if (i.cat !== S.cat) return false;
      if (S.f.owned && !i.owned) return false;
      if (S.f.worn && !isWorn(i)) return false;
      return !q || hay(i).indexOf(q) >= 0;
    });
    list.sort(function (a, b) {
      if (S.sort === 'name') return a.name.localeCompare(b.name);
      if (S.sort === 'owned' && a.owned !== b.owned) return a.owned ? -1 : 1;
      return b.rank - a.rank || a.name.localeCompare(b.name);
    });
    return list;
  }
  function pages(n) { return Math.max(1, Math.ceil(n / S.per)); }

  function renderCats() {
    var box = $('tabs'); box.textContent = '';
    CATS.forEach(function (c) {
      var n = S.items.filter(function (i) { return i.cat === c[0]; }).length;
      var b = el('button', 'tab' + (S.cat === c[0] ? ' on' : ''), c[1].toUpperCase());
      b.onclick = function () { if (S.cat !== c[0]) setCat(c[0]); };
      box.appendChild(b);
    });
    var mine = S.items.filter(function (i) { return i.cat === S.cat; }), own = mine.filter(function (i) { return i.owned; }).length;
    var t = CATS.filter(function (x) { return x[0] === S.cat; })[0];
    $('collName').textContent = t ? t[1] : '';
    $('collText').textContent = own + ' / ' + mine.length + ' owned';
    $('collBar').firstChild.style.width = (mine.length ? Math.round(100 * own / mine.length) : 0) + '%';
  }

  function setCat(c) {
    S.cat = c; S.page = 0;
    render(true);
  }

  function sub(i) {
    if (i.set) return pretty(i.set) + ' set';
    if (i.cat === 'tools' && i.target) return 'Fits ' + i.target;
    if (i.cat === 'pokemon' && i.species) return pretty(i.species) + ' skin';
    return (i.desc && i.desc[0]) || i.slot;
  }
  function goLabel(i) {
    if (!i.owned) return i.wearable ? 'TRY ON' : 'LOCKED';
    if (i.wearable) return isWorn(i) ? 'TAKE OFF' : 'EQUIP';
    return i.cat === 'tools' ? 'APPLY' : 'CHOOSE';
  }

  function card(i, idx) {
    var b = el('div', 'card panel' + (i.rank >= 2 ? ' rare' : '') + (i.rank >= 4 ? ' epic' : '') + (i.owned ? '' : ' locked') + (isWorn(i) ? ' worn' : ''));
    b.setAttribute('data-r', i.rank); b.style.setProperty('--i', idx); b.style.setProperty('--f', (idx % 5) * -0.7 + 's');
    if (S.flash === i.id) { b.classList.add('flash'); S.flash = null; }
    var a = el('div', 'art'), pair = art(i);
    a.appendChild(pair[0]); a.appendChild(pair[1]);
    a.appendChild(el('span', 'tag dimt slot', i.slot.toUpperCase()));
    var st = el('span', 'tag state ' + (isWorn(i) ? '' : i.owned ? 'dimt' : 'lock'), isWorn(i) ? 'WORN' : i.owned ? 'OWNED' : 'LOCKED');
    a.appendChild(st);
    b.appendChild(a);
    b.appendChild(nameNode(i, 'nm'));
    b.appendChild(el('div', 'sub', sub(i)));
    var foot = el('div', 'foot');
    foot.appendChild(el('span', 'tag r' + i.rank, i.rarity));
    var go = el('button', 'go' + (isWorn(i) && i.wearable ? ' take' : ''), goLabel(i));
    if (!i.owned && !i.wearable) { go.disabled = true; go.style.opacity = '.4'; go.style.cursor = 'default'; }
    go.onclick = function (ev) { ev.stopPropagation(); quick(i); };
    foot.appendChild(go);
    b.appendChild(foot);
    if (i.rank >= 3) b.appendChild(el('i', 'shine'));
    b.onclick = function () { openDetail(i.id); };
    return b;
  }

  function render(play) {
    var list = visible(), np = pages(list.length);
    if (S.page >= np) S.page = np - 1;
    if (S.page < 0) S.page = 0;
    renderCats();
    var grid = $('grid'); grid.textContent = '';
    list.slice(S.page * S.per, S.page * S.per + S.per).forEach(function (i, idx) { grid.appendChild(card(i, idx)); });
    grid.classList.remove('play');
    if (play && !reduced()) { void grid.offsetWidth; grid.classList.add('play'); }
    var empty = list.length === 0;
    $('empty').hidden = !empty;
    if (empty) {
      var none = S.items.length === 0 || (S.items.every(function (i) { return i.cat !== S.cat; }));
      $('emptyTitle').hidden = none;
      $('emptyTitle').textContent = 'No matches';
      $('emptyText').textContent = none ? EMPTY[S.cat] : 'Try another search or turn a filter off.';
      $('emptyText').className = none ? '' : 'dim';
    }
    $('pageLabel').textContent = 'PAGE ' + (S.page + 1) + ' / ' + np;
    $('prev').disabled = S.page <= 0; $('next').disabled = S.page >= np - 1;
  }

  // ------------------------------------------------------------ actions
  function quick(i) {
    if (!i.owned) { if (i.wearable) send({ action: 'tryon', id: i.id }); return; }
    if (i.wearable) { send({ action: isWorn(i) ? 'unequip' : 'equip', id: i.id }); S.flash = i.id; return; }
    if (i.cat === 'tools') { send({ action: 'tool', id: i.id }); return; }
    openDetail(i.id, true);
  }

  // ------------------------------------------------------------ detail
  function find(id) { for (var k = 0; k < S.items.length; k++) if (S.items[k].id === id) return S.items[k]; return null; }

  function openDetail(id, party) {
    S.sel = id; S.partyMode = false; S.party = null;
    $('overlay').hidden = false;
    renderDetail(true);
    if (party) startParty();
  }
  function closeDetail() { S.sel = null; S.partyMode = false; $('overlay').hidden = true; }

  function startParty() {
    var i = find(S.sel); if (!i) return;
    S.partyMode = true; S.party = null;
    send({ action: 'party', id: i.id });
    renderDetail(false);
  }

  function renderDetail(anim) {
    var i = find(S.sel); if (!i) { closeDetail(); return; }
    var d = $('detail'); d.setAttribute('data-r', i.rank);
    var artBox = $('dArt'); artBox.classList.toggle('locked', !i.owned);
    if (anim || !artBox._id || artBox._id !== i.id) {
      artBox._id = i.id;
      var old = artBox.querySelectorAll('img, .fallback'); for (var k = 0; k < old.length; k++) old[k].parentNode.removeChild(old[k]);
      var pair = art(i); artBox.appendChild(pair[0]); artBox.appendChild(pair[1]);
    }
    $('dSlot').textContent = i.slot + (i.set ? '  /  ' + pretty(i.set) + ' set' : '');
    var nm = $('dName'); nm.textContent = i.name; nm.className = i.c1 ? 'gname' : '';
    if (i.c1) { nm.style.setProperty('--c1', lift(i.c1)); nm.style.setProperty('--c2', lift(i.c2 || i.c1)); }
    var tags = $('dTags'); tags.textContent = '';
    tags.appendChild(el('span', 'tag r' + i.rank, i.rarity));
    tags.appendChild(el('span', 'tag' + (isWorn(i) ? '' : i.owned ? ' dimt' : ' lock'), isWorn(i) ? 'WORN' : i.owned ? 'OWNED' : 'LOCKED'));
    if (i.glint) tags.appendChild(el('span', 'tag r5', 'RADIANT'));

    $('dBody').hidden = S.partyMode; $('dParty').hidden = !S.partyMode;
    var desc = $('dDesc'); desc.textContent = '';
    (i.desc || []).forEach(function (l) { desc.appendChild(el('div', '', l)); });
    var ex = $('dExtra'); ex.textContent = '';
    function line(a, b) { var d1 = el('div'); d1.appendChild(document.createTextNode(a + ' ')); d1.appendChild(el('b', '', b)); ex.appendChild(d1); }
    if (i.cat === 'tools' && i.target) line('Fits:', i.target);
    if (i.cat === 'pokemon') { line('For:', pretty(i.species)); if (i.wornBy > 0) line('Worn by:', i.wornBy + (i.wornBy === 1 ? ' Pokemon' : ' Pokemon')); }
    if (i.free) line('Unlocked:', 'for everyone');

    var setBox = $('dSet'), row = $('dSetRow'); row.textContent = '';
    var mates = i.set ? S.items.filter(function (x) { return x.set === i.set; }) : [];
    setBox.hidden = mates.length < 2;
    if (mates.length > 1) {
      mates.sort(function (a, b) { return SLOT_ORDER.indexOf(a.slot) - SLOT_ORDER.indexOf(b.slot); });
      var have = mates.filter(function (x) { return x.owned; }).length;
      setBox.querySelector('.h').textContent = pretty(i.set).toUpperCase() + ' SET  ' + have + '/' + mates.length;
      mates.forEach(function (x) {
        var c = el('button', 'sp' + (x.owned ? ' have' : '') + (x.id === i.id ? ' cur' : '') + (isWorn(x) ? ' worn' : ''), SLOT_ABBR[x.slot] || x.slot.slice(0, 2).toUpperCase());
        c.title = x.name; c.onclick = function () { openDetail(x.id); };
        row.appendChild(c);
      });
    }

    var status = $('dStatus'); status.textContent = '';
    var head = el('b', '', isWorn(i) ? 'Wearing this' : i.owned ? 'In your collection' : 'Locked');
    head.style.color = isWorn(i) ? 'var(--green)' : i.owned ? 'var(--acc)' : 'var(--red)';
    status.appendChild(head);
    status.appendChild(el('span', 'dim', i.owned ? (i.wearable ? 'Click to ' + (isWorn(i) ? 'take it off.' : 'put it on.') : i.cat === 'tools' ? 'Hold a matching item in your main hand.' : 'Choose which Pokemon gets it.')
      : i.wearable ? 'You can still try it on for ' + S.preview + ' seconds.' : 'Ask a staff member how to unlock it.'));

    var main = $('dMain'), tr = $('dTry');
    tr.hidden = !i.wearable; tr.textContent = 'TRY ON ' + S.preview + 'S'; tr.onclick = function () { send({ action: 'tryon', id: i.id }); };
    main.hidden = S.partyMode;
    main.className = 'btn ' + (isWorn(i) && i.wearable ? 'green' : 'solid');
    main.disabled = !i.owned;
    main.textContent = !i.owned ? 'LOCKED' : i.wearable ? (isWorn(i) ? 'TAKE OFF' : 'EQUIP') : i.cat === 'tools' ? 'APPLY TO HELD ITEM' : 'CHOOSE POKEMON';
    main.onclick = function () {
      if (i.wearable) { send({ action: isWorn(i) ? 'unequip' : 'equip', id: i.id }); S.flash = i.id; }
      else if (i.cat === 'tools') send({ action: 'tool', id: i.id });
      else startParty();
    };
    $('dBack').textContent = S.partyMode ? 'BACK' : 'CLOSE';
    $('dBack').onclick = function () { if (S.partyMode) { S.partyMode = false; renderDetail(false); } else closeDetail(); };
    if (S.partyMode) renderParty();
  }

  function onParty(d) {
    if (!d || d.id !== S.sel) return;
    S.party = d; if (S.partyMode) renderParty();
  }
  function renderParty() {
    var box = $('partyList'); box.textContent = '';
    if (!S.party) { box.appendChild(el('div', 'dim', 'Loading your party...')); return; }
    var id = S.sel;
    S.party.members.forEach(function (m) {
      if (m.empty) { box.appendChild(el('div', 'prow empty', 'Slot ' + (m.slot + 1) + ' - empty')); return; }
      var r = el('button', 'prow' + (m.fits ? '' : ' blocked') + (m.wearing ? ' wear' : ''));
      r.appendChild(el('span', 'pn', m.name));
      r.appendChild(el('span', 'pm', 'Lv. ' + m.level));
      r.appendChild(el('span', 'ps', !m.fits ? 'WRONG SPECIES' : m.wearing ? 'WEARING - REMOVE' : m.skin ? 'REPLACE SKIN' : 'APPLY'));
      if (m.fits) r.onclick = function () { send({ action: 'skin', id: id, slot: m.slot }); };
      box.appendChild(r);
    });
  }

  // ------------------------------------------------------------ server events
  function onState(d) {
    if (!d || !d.items) return;
    var sig = JSON.stringify(d.items);
    if (sig === S.sig) return;                       // nothing changed: keep the cards (and any click in progress)
    S.sig = sig;
    S.items = d.items; S.cobblemon = d.cobblemon !== false; S.preview = d.previewSeconds || 15;
    tickNumber($('owned'), d.owned, function (v) { return v + ' / ' + d.total; });
    tickNumber($('worn'), d.worn, String);
    var first = S.first; S.first = false;
    render(first);
    if (S.sel) renderDetail(false);
  }

  var toastTimer = 0;
  function toast(msg, bad) {
    var t = $('toast'); t.textContent = msg; t.className = bad ? 'bad' : ''; t.hidden = false;
    t.classList.remove('in'); void t.offsetWidth; t.classList.add('in');
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { t.hidden = true; }, 2600);
  }
  function burst(node) {
    if (reduced() || !node) return;
    var st = $('stage').getBoundingClientRect(), r = node.getBoundingClientRect();
    var b = el('div', 'burst');
    b.style.left = ((r.left + r.width / 2 - st.left) / S.scale) + 'px'; b.style.top = ((r.top + r.height / 2 - st.top) / S.scale) + 'px';
    for (var k = 0; k < 18; k++) {
      var s = document.createElement('i'), a = Math.PI * 2 * k / 18 + Math.random() * .4, d = 60 + Math.random() * 70;
      s.style.setProperty('--dx', Math.cos(a) * d + 'px'); s.style.setProperty('--dy', Math.sin(a) * d + 'px'); b.appendChild(s);
    }
    $('stage').appendChild(b); setTimeout(function () { if (b.parentNode) b.parentNode.removeChild(b); }, 950);
  }
  function onResult(d) {
    if (!d) return;
    if (d.message) toast(d.message, !d.ok);
    if (d.ok && (d.action === 'equip' || d.action === 'skin' || d.action === 'tool')) {
      burst(!$('overlay').hidden ? $('dArt') : document.querySelector('.card.flash .art'));
    }
    if (d.ok && d.close) setTimeout(closeGui, 500);
  }

  // ------------------------------------------------------------ wiring
  Array.prototype.forEach.call(document.querySelectorAll('.chk'), function (b) {
    b.onclick = function () { var f = b.getAttribute('data-f'); S.f[f] = !S.f[f]; b.classList.toggle('on', S.f[f]); S.page = 0; render(true); };
  });
  Array.prototype.forEach.call(document.querySelectorAll('#sorts .pill'), function (b) {
    b.onclick = function () {
      S.sort = b.getAttribute('data-s'); S.page = 0;
      Array.prototype.forEach.call(document.querySelectorAll('#sorts .pill'), function (x) { x.classList.toggle('on', x === b); });
      render(true);
    };
  });
  $('prev').onclick = function () { S.page--; render(true); };
  $('next').onclick = function () { S.page++; render(true); };
  $('offAll').onclick = function () { send({ action: 'unequipall' }); };
  $('close').onclick = closeGui;
  $('overlay').addEventListener('click', function (e) { if (e.target === $('overlay')) closeDetail(); });
  window.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') { if (!$('overlay').hidden) closeDetail(); else closeGui(); }
    else if ($('overlay').hidden) {
      if (e.key === 'ArrowRight' && !$('next').disabled) $('next').click();
      if (e.key === 'ArrowLeft' && !$('prev').disabled) $('prev').click();
    }
  });
  window.addEventListener('wheel', function (e) {
    if (!$('overlay').hidden) return;
    if (e.deltaY > 0 && !$('next').disabled) $('next').click();
    else if (e.deltaY < 0 && !$('prev').disabled) $('prev').click();
  }, { passive: true });

  (function start() {
    var c = null; try { c = new URLSearchParams(location.search).get('cat'); } catch (e) { /* old engine */ }
    if (c && CATS.some(function (x) { return x[0] === c; })) { S.cat = c; }
    send({ action: 'ready' });
  })();
  /* keep the lists fresh while the page stays open (party changes, permissions granted by staff) */
  setInterval(function () { if (!document.hidden) send({ action: 'refresh' }); }, 20000);
})();
