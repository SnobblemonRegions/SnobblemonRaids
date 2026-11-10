'use strict';
// The raid shop page. A view only: it shows what the server last said, and a click sends an id.
// The server re-reads everything else, so nothing here is trusted.
const CHANNEL = 'cobbleraids.shop';
const $ = RC.$;
const grid = $('grid');
const tip = RC.tooltip();
const icons = new Map();   // icon key -> data URL, delivered by webgui:shop_icons
let page = null;
let deadline = 0;          // performance.now() at which the rotating page restocks; 0 = this page does not
let lastRefresh = 0;       // when we last asked for a fresh page because the clock ran out

const send = (m) => RC.send(CHANNEL, m);
const art = (key) => { const u = icons.get(key); return u ? 'url(' + u + ')' : 'none'; };

function render() {
  if (!page) return;
  // A boss waiting to be saved is drawn as a wide cell of the Pokemon grid, even before any boss is saved.
  const pokemon = !!page.offer || (page.entries.length > 0 && page.entries.every((e) => e.pokemon));
  grid.className = pokemon ? 'pokemon' : 'items';
  document.querySelector('.stage').classList.toggle('single', page.pageCount <= 1);
  $('crumb').textContent = '/ ' + page.heading;
  $('balance').textContent = RC.num(page.balance) + ' RP';
  $('where').textContent = 'PAGE ' + (page.pageIndex + 1) + ' OF ' + page.pageCount;

  grid.textContent = '';
  if (page.offer) grid.appendChild(offerCell(page.offer));
  if (page.entries.length === 0 && !page.offer) {
    const e = RC.el('div', 'empty', 'Nothing for sale right now.');
    e.style.gridColumn = '1 / -1';
    e.style.padding = '8cqw 0';
    grid.appendChild(e);
  }
  for (const entry of page.entries) grid.appendChild(cell(entry, pokemon));
}

// Counts down locally from the server's "time left", so a wrong client clock cannot skew it and the
// server is not polled. At zero it asks for the page again, at most every few seconds: the server
// rolls the new stock at the boundary, but a request that lands a moment early would just get the
// old stock back and must not turn into a tight loop.
function tick() {
  const chip = $('timer');
  if (!deadline) { chip.hidden = true; return; }
  const left = Math.max(0, deadline - performance.now());
  chip.hidden = false;
  if (left > 0) {
    const s = Math.ceil(left / 1000);
    const hms = [Math.floor(s / 3600), Math.floor(s / 60) % 60, s % 60]
      .map((n, i) => (i === 0 ? String(n) : String(n).padStart(2, '0'))).join(':');
    chip.textContent = 'NEW STOCK IN ' + hms;
  } else {
    chip.textContent = 'RESTOCKING…';
    if (page && performance.now() - lastRefresh > 3000) {
      lastRefresh = performance.now();
      send({ action: 'turn', page: page.pageIndex });
    }
  }
}
setInterval(tick, 1000);

function cell(entry, pokemon) {
  const affordable = page.balance >= entry.cost && !entry.soldOut;
  const el = RC.el('div', 'panel cell' + (affordable ? '' : ' dim') + (entry.soldOut ? ' soldout' : '') + (entry.confirm ? ' confirm' : ''));
  el.dataset.icon = entry.icon || '';
  const image = RC.el('div', 'art');
  image.style.backgroundImage = art(entry.icon);
  el.appendChild(image);

  // The server asks for a second click before it spends anything; the cell says so until it gets one.
  const price = entry.soldOut ? 'SOLD OUT' : entry.confirm ? 'CONFIRM?' : RC.num(entry.cost) + ' RP';
  if (pokemon) {
    const body = RC.el('div', 'body');
    const name = RC.el('div', 'name' + (entry.shiny ? ' shiny' : ''), (entry.shiny ? '★ ' : '') + RC.title(entry.species));
    body.append(name, RC.el('div', 'meta', 'Level ' + entry.level));
    if (entry.personal) body.appendChild(RC.el('div', 'meta', 'IV ' + entry.ivPercent + '%  EV ' + entry.evPercent + '%'));
    if (entry.personal && entry.leavesInMillis >= 0) body.appendChild(RC.el('div', 'meta leaves', 'Leaves in ' + leavesIn(entry.leavesInMillis)));
    // The price and the reroll button share a row, so a long price can never run under the button.
    const priceRow = RC.el('div', 'pricerow');
    priceRow.appendChild(RC.el('div', 'price', price));
    if (entry.personal && entry.canReroll) priceRow.appendChild(rerollButton(entry));
    if (entry.personal && entry.canReplace) priceRow.appendChild(replaceButton(entry));
    body.appendChild(priceRow);
    el.appendChild(body);
  } else {
    if (entry.count > 1) el.appendChild(RC.el('span', 'count', '×' + entry.count));
    el.appendChild(RC.el('span', 'price', price));
  }
  if (entry.limit > 0 && !entry.soldOut) el.appendChild(RC.el('span', 'tag', Math.max(0, entry.remaining) + ' left'));

  el.addEventListener('click', () => send({ action: 'buy', page: page.pageIndex, id: entry.id }));
  tip.bind(el, () => tooltipFor(entry));
  return el;
}

// A saved boss stays a set number of days. Shown as days and hours, or hours and minutes on its last day.
function leavesIn(millis) {
  const minutes = Math.max(1, Math.ceil(millis / 60000));
  const d = Math.floor(minutes / 1440), h = Math.floor(minutes / 60) % 24, m = minutes % 60;
  return d > 0 ? d + 'd ' + h + 'h' : h > 0 ? h + 'h ' + m + 'm' : m + 'm';
}

// The boss the player just beat, waiting for a yes or no. Shown on top of Your Bosses.
function offerCell(offer) {
  const el = RC.el('div', 'panel cell offer');
  el.dataset.icon = offer.icon || '';
  const image = RC.el('div', 'art');
  image.style.backgroundImage = art(offer.icon);
  el.appendChild(image);
  const body = RC.el('div', 'body');
  body.appendChild(RC.el('div', 'meta kicker', 'NEW BOSS TO KEEP  -  DECIDE WITHIN ' + leavesIn(offer.expiresInMillis).toUpperCase()));
  body.appendChild(RC.el('div', 'name' + (offer.shiny ? ' shiny' : ''), (offer.shiny ? '★ ' : '') + RC.title(offer.species)));
  body.appendChild(RC.el('div', 'meta', 'Level ' + offer.level + '   IV ' + offer.ivPercent + '%   EV ' + offer.evPercent + '%'));
  body.appendChild(RC.el('div', 'meta hint', offer.full
    ? 'Your shop is full. Press SWAP on the boss you will give up.'
    : 'You have a free slot (' + page.slotsUsed + '/' + page.maxSlots + ' used).'));
  el.appendChild(body);
  const actions = RC.el('div', 'actions');
  if (!offer.full) {
    const keep = RC.el('button', 'keepbtn', 'KEEP IT');
    keep.addEventListener('click', () => send({ action: 'keep', page: page.pageIndex }));
    actions.appendChild(keep);
  }
  const no = RC.el('button', 'declinebtn', 'NO THANKS');
  no.addEventListener('click', () => send({ action: 'decline', page: page.pageIndex }));
  actions.appendChild(no);
  el.appendChild(actions);
  return el;
}

function replaceButton(entry) {
  const r = RC.el('button', 'reroll replace' + (entry.confirmReplace ? ' confirm' : ''), entry.confirmReplace ? 'SURE?' : 'SWAP');
  r.addEventListener('click', (ev) => {
    ev.stopPropagation();
    send({ action: 'keep', page: page.pageIndex, replace: entry.replaceId });
  });
  return r;
}

function rerollButton(entry) {
  const r = RC.el('button', 'reroll' + (entry.confirmReroll ? ' confirm' : ''), entry.confirmReroll ? 'SURE?' : 'REROLL');
  r.addEventListener('click', (ev) => {
    ev.stopPropagation();
    send({ action: 'buy', page: page.pageIndex, id: 'reroll:' + entry.id.substring('personal:'.length) });
  });
  return r;
}

function tooltipFor(entry) {
  const out = [];
  if (entry.pokemon) {
    out.push([(entry.shiny ? 'Shiny ' : '') + RC.title(entry.species), '']);
    out.push(['Level ' + entry.level, 'mu']);
  } else {
    out.push([entry.name, '']);
    out.push(['×' + entry.count, 'mu']);
  }
  if (entry.personal) {
    out.push(['IV ' + entry.ivPercent + '%   EV ' + entry.evPercent + '%', 'hi']);
    if (entry.canReroll) out.push(['Reroll (risky): ' + entry.rerollCost + ' RP', 'hi']);
    if (entry.leavesInMillis >= 0) out.push(['Leaves your shop in ' + leavesIn(entry.leavesInMillis), entry.leavesInMillis < 86400000 ? 'bad' : 'mu']);
  }
  if (entry.confirm) out.push(['Click again to confirm', 'hi']);
  if (entry.canReplace) out.push([entry.confirmReplace ? 'Click SWAP again: this boss is given up for good' : 'Swap this boss for the new one', 'hi']);
  if (entry.confirmReroll) out.push(['Click REROLL again to confirm', 'hi']);
  if (entry.soldOut) {
    out.push([entry.limit === 1 ? 'Already purchased' : 'All ' + entry.limit + ' bought', 'ok']);
    out.push([page.rotatesInMillis > 0 ? 'Back with the next restock' : 'Resets daily', 'mu']);
  } else {
    const short = entry.cost - page.balance;
    out.push([RC.num(entry.cost) + ' RP', short > 0 ? 'bad' : 'hi']);
    if (short > 0) out.push([RC.num(short) + ' RP short', 'bad']);
    if (entry.limit > 0) out.push([entry.remaining + ' of ' + entry.limit + ' left', 'mu']);
  }
  return out;
}

function turn(direction) {
  if (!page || page.pageCount <= 1) return;
  send({ action: 'turn', page: (page.pageIndex + direction + page.pageCount) % page.pageCount });
}
$('prev').addEventListener('click', () => turn(-1));
$('next').addEventListener('click', () => turn(1));
$('close').addEventListener('click', RC.close);

addEventListener('webgui:shop_icons', (e) => {
  for (const [key, url] of Object.entries(e.detail || {})) icons.set(key, url);
  grid.querySelectorAll('.cell').forEach((el) => {
    if (!el.dataset.icon) return;
    const url = icons.get(el.dataset.icon);
    if (url) el.querySelector('.art').style.backgroundImage = 'url(' + url + ')';
  });
});
addEventListener('webgui:shop_page', (e) => {
  page = e.detail;
  deadline = page.rotatesInMillis > 0 ? performance.now() + page.rotatesInMillis : 0;
  tip.hide();
  render();
  tick();
});

// The server answers with the first page; asking after load means it cannot arrive before we listen.
send({ action: 'ready' });
