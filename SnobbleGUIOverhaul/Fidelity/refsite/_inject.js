/* Reference-render injector (loaded before every original page's own scripts).
   Stubs the WebGUI bridge, paints the same game backdrop the native screenshots have, then replays a case:
   recorded server events (webgui:<event>) and the harness's clicks / typing, at the recorded spacing. */
(function () {
  'use strict';
  var posted = [];
  window.__posted = posted;
  window.webgui = {
    assetsBase: '/', isHud: false,          // what the real WebGUI bridge defines in-game (pages use them to tell game from browser)
    postToGame: function (o) { posted.push(o); },
    on: function (n, f) { window.addEventListener('webgui:' + n, function (e) { f(e.detail); }); },
    closeGui: function () { posted.push({ _close: true }); }
  };
  function backdrop() {
    var h = document.documentElement;
    h.style.background = '#000 url(/_bg.png) center / cover no-repeat';
    h.style.minHeight = '100%';
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', backdrop); else backdrop();

  var q = new URLSearchParams(location.search);
  var c = q.get('refcase');
  if (!c) return;
  var steps;
  try {
    var x = new XMLHttpRequest();
    x.open('GET', '/_cases/' + c + '.json', false);
    x.send();
    steps = JSON.parse(x.responseText);
  } catch (e) { console.log('[ref] no case ' + c); return; }

  function visible(el) {
    var r = el.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) return false;
    var s = getComputedStyle(el);
    return s.visibility !== 'hidden' && s.display !== 'none' && s.opacity !== '0';
  }
  function norm(t) { return String(t || '').replace(/\s+/g, ' ').trim().toLowerCase(); }
  function findByText(label) {
    var want = norm(label), best = null, bestLen = 1e9;
    var all = document.querySelectorAll('button, a, [data-act], [role=button], [onclick], .btn, .tab, .chip, div, span, li, label');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (!visible(el)) continue;
      var t = norm(el.textContent);
      if (t !== want && !(t.indexOf(want) === 0 && t.length <= want.length + 3)) continue;
      var len = el.innerHTML.length;
      if (len < bestLen) { best = el; bestLen = len; }
    }
    if (!best) return null;
    var p = best;
    while (p && p !== document.body) {
      if (p.tagName === 'BUTTON' || p.tagName === 'A' || (p.getAttribute && (p.getAttribute('data-act') || p.getAttribute('role') === 'button' || p.onclick))) return p;
      p = p.parentElement;
    }
    return best;
  }
  function clickEl(el) {
    var r = el.getBoundingClientRect(), cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    var o = { bubbles: true, cancelable: true, clientX: cx, clientY: cy, button: 0, view: window };
    try { el.dispatchEvent(new PointerEvent('pointerdown', o)); } catch (e) {}
    el.dispatchEvent(new MouseEvent('mousedown', o));
    try { el.dispatchEvent(new PointerEvent('pointerup', o)); } catch (e) {}
    el.dispatchEvent(new MouseEvent('mouseup', o));
    el.dispatchEvent(new MouseEvent('click', o));
  }
  function typeInto(text) {
    var el = document.activeElement;
    if (!el || !(el.tagName === 'INPUT' || el.tagName === 'TEXTAREA')) {
      var c = document.querySelectorAll('textarea, input[type=text], input:not([type])');
      for (var i = 0; i < c.length; i++) if (visible(c[i])) { el = c[i]; break; }
    }
    if (!el) return;
    el.focus();
    el.value = (el.value || '') + text;
    el.dispatchEvent(new Event('input', { bubbles: true }));
  }
  function run(s) {
    try {
      if (s.event) {
        window.dispatchEvent(new CustomEvent('webgui:' + s.event, { detail: s.detail }));
      } else if (s.click) {
        var el = findByText(s.click);
        if (el) clickEl(el); else console.log('[ref] no element for "' + s.click + '"');
      } else if (s.typetext) {
        typeInto(s.typetext);
      } else if (s.clickxy) {
        var hitEl = document.elementFromPoint(s.clickxy[0], s.clickxy[1]);
        if (hitEl) clickEl(hitEl); else console.log('[ref] nothing at ' + s.clickxy);
      } else if (s.key) {
        var GL = { '32': [' ', 'Space', 32], '256': ['Escape', 'Escape', 27], '262': ['ArrowRight', 'ArrowRight', 39], '263': ['ArrowLeft', 'ArrowLeft', 37],
                   '257': ['Enter', 'Enter', 13] };
        var m = GL[s.key] || [s.key, s.key, +s.key];
        var k = m[0];
        var o = { key: k, code: m[1], keyCode: m[2], which: m[2], bubbles: true };
        document.dispatchEvent(new KeyboardEvent('keydown', o));
        document.dispatchEvent(new KeyboardEvent('keyup', o));
      } else if (s.scroll) {
        var sc = document.querySelector(s.scroll) || document.scrollingElement;
        if (sc) sc.scrollTop += s.by || 300;
      }
    } catch (e) { console.log('[ref] step failed ' + e); }
  }
  var t = 0;
  steps.forEach(function (s) { t += s.dt || 0; setTimeout(function () { run(s); }, t); });
})();
