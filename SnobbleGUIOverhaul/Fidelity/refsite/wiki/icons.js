/* Original 16x16 pixel icons ("trainer badges"): coloured art with a dark outline, drawn on a dark tile.
   window.wikiIcon(name) -> CSS url() for a mod/page icon; window.wikiGroupIcon(id) -> the same for a category. */
(function () {
  'use strict';
  var OUT = [8, 24, 29];
  var RED = '#e0364a', RED2 = '#ff7a85', RED3 = '#9c1428', GLD = '#f2c230', GLD2 = '#ffe27a', GLD3 = '#b98a12', BLU = '#2e86c1', BLU2 = '#7fc8f0',
    GRN = '#2fbf5a', GRN2 = '#7be89b', GRN3 = '#1d8a3f', SLV = '#9fb0b8', SLV2 = '#d5dee2', SLV3 = '#5d6b73', BRN = '#8a5a2b', BRN2 = '#b8793a',
    PAP = '#f4ecd0', PAP2 = '#d9c58f', INK = '#1d2b30', WHT = '#ffffff', TEAL = '#2eb8d6', TEAL2 = '#1f8fb0';
  var PAL = { r: RED, o: RED2, R: RED3, y: GLD, Y: GLD2, d: GLD3, b: BLU, B: BLU2, g: GRN, G: GRN2, h: GRN3, s: SLV, S: SLV2, z: SLV3, n: BRN, N: BRN2, p: PAP, P: PAP2, k: INK, w: WHT };

  /* each drawing gets P(x,y,c) R(x,y,w,h,c) C(cx,cy,r,c) L(x0,y0,x1,y1,c) G(rows) */
  var D = {
    cap: function (P, R, C) { for (var y = 3; y <= 10; y++) for (var x = 1; x <= 14; x++) { var dx = x - 7.5, dy = y - 10; if (dx * dx + dy * dy <= 38) P(x, y, y < 6 ? RED2 : RED); } R(2, 9, 12, 2, '#b5122a'); R(8, 10, 7, 3, RED); R(8, 12, 7, 1, '#8f1424'); R(6, 4, 4, 4, WHT); R(7, 5, 2, 2, RED); },
    badge: function (P, R) { for (var y = 1; y <= 14; y++) for (var x = 1; x <= 14; x++) if (Math.abs(x - 7.5) + Math.abs(y - 7.5) <= 7) P(x, y, GLD); for (y = 3; y <= 12; y++) for (x = 3; x <= 12; x++) if (Math.abs(x - 7.5) + Math.abs(y - 7.5) <= 4.6) P(x, y, GLD2); R(7, 5, 2, 6, '#c8202f'); R(5, 7, 6, 2, '#c8202f'); P(7, 7, WHT); P(4, 4, '#fff6c8'); P(5, 3, '#fff6c8'); },
    medal: function (P, R, C) { R(4, 8, 3, 6, BLU); R(9, 8, 3, 6, '#c8202f'); P(5, 14, BLU); P(10, 14, '#c8202f'); C(8, 6, 5, GLD); C(8, 6, 3, GLD2); R(7, 5, 2, 2, WHT); R(7, 6, 2, 1, '#d9a21b'); },
    coin: function (P, R, C) { C(8, 8, 6, GLD); C(8, 8, 4, '#d9a21b'); C(8, 8, 3, '#ffd54a'); R(7, 5, 2, 6, '#b98a12'); P(5, 4, '#fff6c8'); P(4, 5, '#fff6c8'); P(6, 3, '#fff6c8'); },
    ball: function (P, R, C) { C(8, 8, 6, WHT); for (var y = 2; y <= 7; y++) for (var x = 1; x <= 14; x++) if ((x - 7.5) * (x - 7.5) + (y - 8) * (y - 8) <= 38) P(x, y, RED); R(2, 7, 12, 2, INK); C(8, 8, 2, WHT); P(8, 8, '#cfd8dc'); P(4, 4, RED2); P(5, 3, RED2); },
    sparkle: function (P, R) { R(7, 1, 2, 14, '#c9b3ff'); R(1, 7, 14, 2, '#c9b3ff'); for (var i = 0; i < 5; i++) { P(7 - i, 7 - i, '#a98bff'); P(8 + i, 7 - i, '#a98bff'); P(7 - i, 8 + i, '#a98bff'); P(8 + i, 8 + i, '#a98bff'); } R(6, 6, 4, 4, WHT); P(2, 2, '#ffd54a'); P(13, 2, '#ffd54a'); P(2, 13, '#7fe3f2'); P(13, 13, '#ffd54a'); },
    tree: function (P, R, C) { R(7, 10, 3, 5, BRN); R(7, 10, 1, 5, '#a8733a'); C(8, 5, 4, '#3fbf5a'); C(4, 8, 3, '#2fa24b'); C(11, 8, 3, '#2fa24b'); C(7, 4, 2, '#6fe08a'); },
    book: function (P, R) { R(2, 2, 12, 12, '#1d6f7a'); R(2, 2, 3, 12, '#c8202f'); R(5, 2, 9, 1, '#2aa0ad'); R(13, 3, 1, 10, PAP); R(4, 13, 10, 1, PAP); R(7, 5, 4, 4, GLD); P(8, 6, INK); P(9, 7, INK); },
    star: function (P, R, C, L, G) { G(['................', '.......yy.......', '.......yY.......', '......yYYy......', '......yYYy......', '.yyyyyyyYYyyyyy.', '..yyyyYYYYyyyy..', '...yyyYYYYyyy...', '....yyYYYYyy....', '....yyYYYYyy....', '..yyyYy..yYyyy..', '..yyy......yyy..', '..yy........yy..']); },
    scroll: function (P, R) { R(3, 3, 10, 10, PAP); R(2, 2, 12, 2, PAP2); R(2, 12, 12, 2, PAP2); R(5, 6, 6, 1, BRN); R(5, 8, 6, 1, BRN); R(5, 10, 4, 1, BRN); P(2, 2, '#fff6c8'); },
    tower: function (P, R) { R(3, 6, 10, 9, SLV); R(3, 3, 3, 3, SLV); R(7, 3, 2, 3, SLV); R(10, 3, 3, 3, SLV); R(3, 6, 2, 9, SLV2); R(7, 10, 3, 5, SLV3); R(7, 7, 2, 2, INK); R(11, 8, 1, 2, SLV3); },
    gem: function (P, R) { R(5, 3, 6, 2, '#7fe3f2'); R(3, 5, 10, 3, TEAL); for (var i = 0; i < 6; i++) R(3 + i, 8 + i, 10 - 2 * i, 1, TEAL2); R(5, 4, 2, 1, WHT); R(4, 6, 2, 1, WHT); R(8, 5, 4, 1, '#7fe3f2'); },
    sword: function (P, R, C, L) { for (var i = 0; i < 9; i++) { P(5 + i, 10 - i, SLV2); P(6 + i, 10 - i, SLV); P(5 + i, 11 - i, SLV); } L(2, 8, 7, 13, GLD); L(3, 8, 7, 12, GLD3); L(3, 12, 1, 14, BRN); P(1, 14, GLD); P(12, 3, WHT); },
    swords: function (P, R, C, L) { L(3, 2, 13, 12, SLV2); L(4, 2, 13, 11, SLV); L(12, 2, 2, 12, SLV2); L(11, 2, 2, 11, SLV); L(2, 11, 6, 15, GLD); L(13, 11, 9, 15, GLD); L(3, 13, 1, 14, BRN); L(12, 13, 14, 14, BRN); P(2, 2, WHT); P(13, 2, WHT); },
    shield: function (P, R) { for (var y = 2; y <= 14; y++) { var t = y <= 8 ? 0 : y - 8; R(3 + t, y, 10 - 2 * t, 1, BLU); } R(3, 2, 10, 1, BLU2); R(3, 3, 1, 5, BLU2); R(7, 4, 2, 8, GLD); R(5, 6, 6, 2, GLD); },
    egg: function (P, R) { var w = [4, 8, 10, 12, 12, 12, 12, 10, 8]; for (var i = 0; i < w.length; i++) R(8 - w[i] / 2, 3 + i, w[i], 1, PAP); R(4, 6, 2, 3, WHT); R(8, 8, 3, 2, BLU2); R(6, 10, 2, 2, BLU2); R(10, 6, 2, 1, BLU2); R(5, 12, 6, 1, PAP2); },
    heart: function (P, R, C, L, G) { G(['................', '..rrrr....rrrr..', '.rooorr..rrrrrr.', '.roorrrrrrrrrrr.', '.rorrrrrrrrrrrr.', '.rrrrrrrrrrrrRR.', '..rrrrrrrrrrRR..', '...rrrrrrrrRR...', '....rrrrrrRR....', '.....rrrrRR.....', '......rrRR......', '.......RR.......']); },
    key: function (P, R, C) { C(4, 8, 3, GLD); C(4, 8, 1, '#4a3a0a'); R(7, 7, 7, 2, GLD); R(7, 7, 7, 1, GLD2); R(11, 9, 2, 3, GLD); R(13, 9, 1, 2, GLD); },
    flag: function (P, R) { R(3, 2, 2, 13, SLV); R(3, 2, 1, 13, SLV2); R(5, 3, 9, 6, RED); R(5, 3, 9, 2, RED2); R(12, 7, 2, 2, RED3); P(13, 6, RED3); R(3, 14, 4, 1, SLV3); },
    gear: function (P, R, C) { C(8, 8, 5, SLV); R(7, 1, 2, 3, SLV); R(7, 12, 2, 3, SLV); R(1, 7, 3, 2, SLV); R(12, 7, 3, 2, SLV); R(3, 3, 2, 2, SLV); R(11, 3, 2, 2, SLV); R(3, 11, 2, 2, SLV); R(11, 11, 2, 2, SLV); C(8, 8, 3, SLV3); C(8, 8, 1, INK); P(5, 5, SLV2); P(6, 4, SLV2); },
    chat: function (P, R) { R(2, 3, 12, 8, WHT); R(3, 2, 10, 1, WHT); R(3, 11, 10, 1, WHT); R(4, 12, 3, 2, WHT); R(4, 6, 2, 2, BLU); R(7, 6, 2, 2, BLU); R(10, 6, 2, 2, BLU); R(2, 10, 12, 1, SLV2); },
    bag: function (P, R, C) { C(8, 10, 5, BRN2); R(6, 3, 4, 4, BRN); R(5, 6, 6, 1, GLD); R(5, 3, 1, 3, BRN2); R(10, 3, 1, 3, BRN2); R(4, 9, 2, 3, '#d99a5a'); P(8, 10, GLD); },
    crown: function (P, R) { R(2, 8, 12, 5, GLD); R(2, 4, 2, 4, GLD); R(7, 3, 2, 5, GLD); R(12, 4, 2, 4, GLD); R(2, 12, 12, 1, GLD3); R(2, 8, 12, 1, GLD2); P(4, 10, RED); P(8, 10, BLU); P(11, 10, RED); P(7, 3, WHT); },
    clock: function (P, R, C) { C(8, 8, 6, SLV); C(8, 8, 5, WHT); R(8, 4, 1, 5, INK); R(8, 8, 4, 1, INK); P(8, 3, RED); P(8, 12, SLV3); P(3, 8, SLV3); P(12, 8, SLV3); },
    bolt: function (P, R, C, L, G) { G(['................', '.........yyy....', '........yYyy....', '.......yYyy.....', '......yYyy......', '.....yYyyyyyy...', '....yYYYYYyy....', '.......yYyy.....', '......yYyy......', '.....yYyy.......', '....yYy.........', '....yy..........', '...y............']); },
    skull: function (P, R) { R(3, 2, 10, 8, PAP); R(2, 4, 12, 4, PAP); R(5, 10, 6, 3, PAP); R(5, 6, 2, 2, INK); R(9, 6, 2, 2, INK); P(8, 9, INK); P(7, 10, PAP2); P(9, 10, PAP2); R(6, 12, 1, 1, INK); R(9, 12, 1, 1, INK); R(4, 3, 3, 1, WHT); },
    mic: function (P, R) { R(6, 2, 4, 7, SLV); R(7, 1, 2, 1, SLV); R(6, 3, 4, 1, SLV2); R(6, 5, 4, 1, SLV2); R(4, 8, 1, 3, SLV3); R(11, 8, 1, 3, SLV3); R(5, 11, 6, 1, SLV3); R(7, 12, 2, 2, SLV3); R(5, 14, 6, 1, SLV); R(7, 2, 1, 6, WHT); },
    hammer: function (P, R) { R(7, 6, 2, 9, BRN); R(7, 6, 1, 9, BRN2); R(3, 2, 10, 5, SLV); R(3, 2, 10, 1, SLV2); R(3, 6, 10, 1, SLV3); },
    card: function (P, R) { R(3, 1, 10, 14, GLD); R(4, 2, 8, 12, BLU); R(5, 3, 6, 6, BLU2); R(6, 4, 2, 2, WHT); R(5, 10, 6, 1, WHT); R(5, 12, 4, 1, WHT); },
    ticket: function (P, R) { R(1, 4, 14, 8, GLD); P(1, 7, '#08181d'); P(1, 8, '#08181d'); P(14, 7, '#08181d'); P(14, 8, '#08181d'); for (var y = 5; y <= 10; y += 2) P(10, y, GLD3); R(3, 6, 5, 1, GLD3); R(3, 8, 3, 1, GLD3); R(1, 4, 14, 1, GLD2); },
    house: function (P, R) { for (var i = 0; i < 6; i++) R(8 - 1 - i, 2 + i, 2 + 2 * i, 1, RED); R(3, 8, 10, 6, PAP); R(2, 7, 12, 1, RED3); R(7, 10, 3, 4, BRN); R(4, 9, 2, 2, BLU2); R(11, 9, 1, 2, BLU2); },
    trophy: function (P, R) { R(4, 2, 8, 6, GLD); R(5, 8, 6, 1, GLD); R(2, 3, 2, 3, GLD); R(12, 3, 2, 3, GLD); R(7, 9, 2, 2, GLD3); R(5, 11, 6, 3, GLD3); R(5, 3, 1, 4, '#fff6c8'); R(4, 2, 8, 1, GLD2); },
    map: function (P, R) { R(2, 3, 12, 10, PAP); R(6, 3, 1, 10, PAP2); R(10, 3, 1, 10, PAP2); P(4, 10, RED); P(3, 9, RED); P(5, 9, RED); P(4, 8, RED); R(8, 6, 1, 1, BLU); P(8, 8, GRN3); P(12, 5, GRN); },
    cart: function (P, R, C, L) { L(1, 2, 3, 2, SLV); R(3, 3, 1, 2, SLV); R(4, 5, 10, 5, SLV); R(4, 5, 10, 1, SLV2); R(6, 2, 3, 3, RED); R(10, 3, 2, 2, GLD); R(5, 10, 8, 1, SLV3); C(5, 13, 1, INK); C(12, 13, 1, INK); },
    lock: function (P, R) { R(3, 7, 10, 8, GLD); R(3, 7, 10, 1, GLD2); R(5, 2, 2, 6, SLV); R(9, 2, 2, 6, SLV); R(5, 2, 6, 2, SLV2); R(7, 10, 2, 2, '#4a3a0a'); R(8, 12, 1, 2, '#4a3a0a'); },
    pencil: function (P, R) { for (var i = 0; i < 9; i++) { P(4 + i, 11 - i, GLD); P(5 + i, 11 - i, GLD); P(4 + i, 10 - i, GLD2); } P(3, 12, PAP); P(4, 12, PAP); P(3, 13, INK); P(2, 13, PAP2); P(2, 14, INK); P(13, 2, RED); P(12, 1, RED); P(13, 1, RED2); },
    wave: function (P, R) { for (var x = 0; x < 16; x++) { var y = 4 + Math.round(2 * Math.sin(x * .8)); R(x, y, 1, 2, BLU2); R(x, y + 2, 1, 2, BLU); R(x, y + 5, 1, 2, TEAL2); R(x, y + 7, 1, 2, BLU); } },
    ribbon: function (P, R, C) { R(5, 9, 3, 6, RED); R(9, 9, 3, 6, '#b5122a'); P(6, 14, '#08181d'); P(10, 14, '#08181d'); C(8, 6, 5, RED); C(8, 6, 3, RED2); C(8, 6, 1, GLD); P(5, 3, '#fff6c8'); }
  };
  var GROUP = { start: 'cap', challenges: 'badge', progress: 'medal', economy: 'coin', pokemon: 'ball', style: 'sparkle', world: 'tree', ref: 'book' };
  var cache = {};
  function url(name) {
    if (cache[name] !== undefined) return cache[name];
    var fn = D[name]; if (!fn) return (cache[name] = '');
    var cv = document.createElement('canvas'); cv.width = cv.height = 16;
    var x = cv.getContext('2d');
    var P = function (a, b, c) { x.fillStyle = c; x.fillRect(a, b, 1, 1); };
    var R = function (a, b, w, h, c) { x.fillStyle = c; x.fillRect(a, b, w, h); };
    var C = function (cx, cy, r, c) { for (var yy = -r; yy <= r; yy++) for (var xx = -r; xx <= r; xx++) if (xx * xx + yy * yy <= r * r + r * .4) P(cx + xx, cy + yy, c); };
    var L = function (x0, y0, x1, y1, c) { var n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) || 1; for (var i = 0; i <= n; i++) P(Math.round(x0 + (x1 - x0) * i / n), Math.round(y0 + (y1 - y0) * i / n), c); };
    var G = function (rows) { for (var yy = 0; yy < rows.length; yy++) for (var xx = 0; xx < 16; xx++) { var c = PAL[rows[yy].charAt(xx)]; if (c) P(xx, yy + 1, c); } };
    fn(P, R, C, L, G);
    var d = x.getImageData(0, 0, 16, 16), o = new Uint8ClampedArray(d.data);
    var A = function (a, b) { return a < 0 || b < 0 || a > 15 || b > 15 ? 0 : d.data[(b * 16 + a) * 4 + 3]; };
    for (var yy = 0; yy < 16; yy++) for (var xx = 0; xx < 16; xx++) if (!A(xx, yy) && (A(xx - 1, yy) || A(xx + 1, yy) || A(xx, yy - 1) || A(xx, yy + 1))) { var i = (yy * 16 + xx) * 4; o[i] = OUT[0]; o[i + 1] = OUT[1]; o[i + 2] = OUT[2]; o[i + 3] = 255; }
    x.putImageData(new ImageData(o, 16, 16), 0, 0);
    return (cache[name] = 'url(' + cv.toDataURL() + ')');
  }
  window.wikiIcon = url;
  window.wikiGroupIcon = function (id) { return GROUP[id] ? url(GROUP[id]) : ''; };
  /* names an author can use for a mod or page (the editor's icon list) */
  window.WIKI_ICON_NAMES = Object.keys(D).filter(function (n) { return n !== 'cap' && n !== 'badge' && n !== 'medal'; });
})();
