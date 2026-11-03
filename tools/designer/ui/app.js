'use strict';

/* Anplomer designer front end. No framework, no build step, no external
 * requests: everything talks to the local JSON API described in README.md. */

const $ = (id) => document.getElementById(id);

const state = {
  root: '',
  entities: [],
  stats: {},
  art: {},
  svgs: [],
  errors: [],
  selected: 'player',
  tab: 'player',
  anim: null,          // /api/anim payload for the selected entity
  animDirty: false,    // edited since the last save?
  pollTimer: null,
};

const TABS = [
  { id: 'player', kinds: ['player'], label: 'Player' },
  { id: 'mobs', kinds: ['mob'], label: 'Mobs' },
  { id: 'weapons', kinds: ['weapon'], label: 'Weapons' },
];

/* ---- helpers ---------------------------------------------------------- */

async function api(path, opts) {
  const res = await fetch(path, Object.assign({ headers: {} }, opts));
  let body = null;
  try { body = await res.json(); } catch (e) { body = null; }
  return { status: res.status, body };
}

function post(path, payload) {
  return api(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

function esc(text) {
  return String(text ?? '').replace(/[&<>"']/g,
    (c) => ({ "&": '&amp;', "<": '&lt;', ">": '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/* ---- animation editor -------------------------------------------------- */

async function loadAnim(ent) {
  const block = $('animBlock');
  if (!ent.asset) {
    state.anim = null;
    block.classList.add('hidden');
    return;
  }
  const { status, body } = await api('/api/anim?entity=' + encodeURIComponent(ent.id));
  if (status !== 200 || !body.ok) {
    state.anim = null;
    block.classList.add('hidden');
    return;
  }
  state.anim = body;
  clearDirty();
  renderAnim();
}

function clearDirty() {
  state.animDirty = false;
  $('animDirty').classList.add('hidden');
  $('animSaveBtn').classList.remove('dirty');
}

function markDirty(row) {
  state.animDirty = true;
  if (row) row.dataset.dirty = '1';
  $('animDirty').classList.remove('hidden');
  $('animSaveBtn').classList.add('dirty');
}

function renderAnim() {
  const a = state.anim;
  const block = $('animBlock');
  if (!a) { block.classList.add('hidden'); return; }
  block.classList.remove('hidden');
  const channelOptions = (sel) => a.channels.map((ch) =>
    `<option value="${esc(ch)}" ${ch === sel ? 'selected' : ''}>${esc(ch)}</option>`).join('');
  const ampOf = (m) => m.amplitude == null ? (a.defaults[m.channel] || 0) : m.amplitude;
  // The by-layer-name default lives in the row tooltip: it is useful while
  // deciding, but as a line per part it drowned the numbers you edit.
  const nameHint = (by) => 'layer name says: ' + by.channel + ' @ ' + by.phase
      + '\u00b0, amplitude '
      + (by.amplitude == null ? 'default' : by.amplitude + '\u00b0');
  $('animRows').innerHTML = a.parts.map((p, i) =>
    `<div class="anim-row" data-idx="${i}"
          data-dirty="${state.animDirty ? 1 : 0}"
          title="${esc(p.name)} \u2014 ${esc(nameHint(p.byName))}">
       <span class="anim-name">${esc(p.name)}</span>
       <select data-fld="channel">${channelOptions(p.motion.channel)}</select>
       <input type="number" data-fld="phase" step="15" value="${esc(p.motion.phase)}"
              title="where in the cycle, in degrees">
       <input type="number" data-fld="amplitude" step="1" min="0" max="360"
              value="${esc(ampOf(p.motion))}"
              title="how far it swings, in degrees">
     </div>`).join('');
  document.querySelectorAll('#animRows .anim-row').forEach((row) => {
    const part = a.parts[Number(row.dataset.idx)];
    const setDirty = () => markDirty(row);
    row.querySelector('select[data-fld="channel"]')
        .addEventListener('change', (ev) => {
          part.motion.channel = ev.target.value;
          part.motion.amplitude = a.defaults[ev.target.value] || 0;
          row.querySelector('input[data-fld="amplitude"]').value =
              a.defaults[ev.target.value] || 0;
          setDirty();
        });
    row.querySelector('input[data-fld="phase"]')
        .addEventListener('input', (ev) => {
          part.motion.phase = Number(ev.target.value); setDirty();
        });
    row.querySelector('input[data-fld="amplitude"]')
        .addEventListener('input', (ev) => {
          part.motion.amplitude = Number(ev.target.value); setDirty();
        });
    // hover = "which vector is this?": dim the rest, glow this part
    row.addEventListener('mouseenter', () => highlightPart(part.name));
    row.addEventListener('mouseleave', restorePreview);
  });
}

/* ---- preview: fit / zoom / pan -----------------------------------------
 * The transform is kept in JS (scale + translate) instead of leaning on CSS
 * because zooming about the cursor needs the pointer's position in artwork
 * coordinates, and re-fitting on resize needs the natural size to hand.
 * The image is at natural size; `scale` is displayed px per artwork px and
 * `base` is the scale that fits the viewport, so the readout is relative to
 * fit (100% = fits). */
const view = { key: '', nw: 0, nh: 0, scale: 1, base: 1, tx: 0, ty: 0 };

function previewBaseSrc() {
  return $('svgPath').value.trim();
}

function previewUrl(src, part) {
  let url = '/api/svg?path=' + encodeURIComponent(src);
  if (part) url += '&highlight=' + encodeURIComponent(part);
  return url;
}

function previewVisible() {
  return !$('svgPreview').classList.contains('hidden');
}

function applyView() {
  const img = $('svgPreview');
  img.style.width = view.nw + 'px';
  img.style.height = view.nh + 'px';
  img.style.transform = 'translate(' + view.tx + 'px,' + view.ty + 'px) scale('
      + view.scale + ')';
  const pct = view.base > 0 ? Math.round(view.scale / view.base * 100) : 100;
  $('zoomLabel').textContent = pct + '%';
}

function centerView() {
  const vp = $('previewViewport');
  view.tx = (vp.clientWidth - view.nw * view.scale) / 2;
  view.ty = (vp.clientHeight - view.nh * view.scale) / 2;
}

function fitView() {
  const vp = $('previewViewport');
  if (!view.nw || !view.nh || !vp.clientWidth) return;
  const pad = 16;
  const k = Math.min((vp.clientWidth - pad) / view.nw,
                     (vp.clientHeight - pad) / view.nh);
  view.base = k > 0 ? k : 1;
  view.scale = view.base;
  centerView();
  applyView();
}

function zoomBy(factor, cx, cy) {
  const vp = $('previewViewport');
  if (!previewVisible()) return;
  if (cx == null) { cx = vp.clientWidth / 2; cy = vp.clientHeight / 2; }
  const next = Math.max(view.base * 0.1,
                        Math.min(view.base * 40, view.scale * factor));
  const k = next / view.scale;
  // keep the point under the cursor (or the centre) pinned
  view.tx = cx - (cx - view.tx) * k;
  view.ty = cy - (cy - view.ty) * k;
  view.scale = next;
  applyView();
}

function onPreviewLoad() {
  const img = $('svgPreview');
  const src = previewBaseSrc();
  if (view.key === src) return;      // a highlight swap: keep the view
  view.key = src;
  view.nw = img.naturalWidth || 300;
  view.nh = img.naturalHeight || 150;
  fitView();
}

/** Initialise the view if the artwork finished loading before we listened. */
function ensureViewReady() {
  const img = $('svgPreview');
  const src = previewBaseSrc();
  if (!src.toLowerCase().endsWith('.svg')) return;
  if (img.complete && img.naturalWidth) onPreviewLoad();
}

function highlightPart(name) {
  if (!previewVisible()) return;
  const src = previewBaseSrc();
  if (!src.toLowerCase().endsWith('.svg')) return;
  const img = $('svgPreview');
  const next = previewUrl(src, name);
  if (img.getAttribute('src') !== next) img.src = next;
}

function restorePreview() {
  if (!previewVisible()) return;
  const src = previewBaseSrc();
  if (!src.toLowerCase().endsWith('.svg')) return;
  const img = $('svgPreview');
  const next = previewUrl(src);
  if (img.getAttribute('src') !== next) img.src = next;
}

/**
 * One-click starting points. They only fill the rows in; nothing is written
 * until Save, so a preset is always safe to try.
 */
function applyPreset(kind) {
  const a = state.anim;
  if (!a || !a.parts.length) return;
  const n = a.parts.length;
  a.parts.forEach((p, i) => {
    if (kind === 'auto') {
      p.motion = {
        channel: p.byName.channel,
        phase: p.byName.phase,
        amplitude: p.byName.amplitude == null
            ? (a.defaults[p.byName.channel] || 0) : p.byName.amplitude,
      };
    } else if (kind === 'wave') {
      p.motion = { channel: 'limb', phase: Math.round(i * 360 / n),
                   amplitude: a.defaults.limb || 26 };
    } else if (kind === 'sway') {
      p.motion = { channel: 'flutter', phase: Math.round(i * 360 / n),
                   amplitude: a.defaults.flutter || 7 };
    } else {
      p.motion = { channel: 'none', phase: 0, amplitude: 0 };
    }
  });
  state.animDirty = true;
  renderAnim();
  markDirty(null);
  toast('preset applied \u2014 press Save');
}

async function saveAnim(clear) {
  if (!state.anim) return;
  const ent = entityById(state.selected);
  if (!ent) return;
  const mapping = {};
  if (!clear) {
    for (const p of state.anim.parts) {
      mapping[p.name] = {
        channel: p.motion.channel,
        phase: Number(p.motion.phase) || 0,
        amplitude: Number(p.motion.amplitude) || 0,
      };
    }
  }
  const { status, body } = await post('/api/anim', {
    entity: ent.id, anim: clear ? {} : mapping });
  if (status === 200 && body.ok) {
    showFieldErrors([], '#animErrors');
    clearDirty();
    toast(clear ? 'animation: back to layer names' : 'animation saved');
    await loadState();
    renderAll();
  } else {
    showFieldErrors((body && body.errors) || [{ field: '', message: 'save failed' }],
      '#animErrors');
    toast('animation NOT saved', true);
  }
}

let toastTimer = null;
function toast(message, bad) {
  const el = $('toast');
  el.textContent = message;
  el.classList.toggle('bad', !!bad);
  el.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add('hidden'), 2400);
}

function entityById(id) {
  return state.entities.find((e) => e.id === id) || null;
}

/* ---- rendering --------------------------------------------------------- */

function renderAll() {
  $('rootLabel').textContent = state.root;
  renderTabs();
  renderEntityList();
  renderInspector();
  renderSvgSelect();
}

function renderTabs() {
  const counts = {};
  for (const t of TABS) {
    counts[t.id] = state.entities.filter((e) => t.kinds.includes(e.kind)).length;
  }
  $('entityTabs').innerHTML = TABS.map((t) =>
    `<button type="button" class="${t.id === state.tab ? 'sel' : ''}"
        data-tab="${t.id}">${t.label}
        <span class="tab-count">${counts[t.id]}</span></button>`).join('');
  document.querySelectorAll('#entityTabs button').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.tab = btn.dataset.tab;
      const kinds = TABS.find((t) => t.id === state.tab).kinds;
      if (!kinds.includes(entityById(state.selected)?.kind)) {
        const first = state.entities.find((e) => kinds.includes(e.kind));
        if (first) state.selected = first.id;
      }
      renderAll();
    });
  });
}

function renderEntityList() {
  const kinds = TABS.find((t) => t.id === state.tab).kinds;
  const rows = state.entities
    .filter((e) => kinds.includes(e.kind))
    .map((e) =>
    `<button type="button" class="row ${e.id === state.selected ? 'sel' : ''}"
        data-entity="${esc(e.id)}">
       <span class="row-name">${esc(e.name || e.id)}</span>
       <span class="row-kind">${esc(e.kind)}</span>
     </button>`);
  $('entityRows').innerHTML = rows.join('');
  document.querySelectorAll('#entityRows .row[data-entity]').forEach((btn) => {
    btn.addEventListener('click', () => {
      state.selected = btn.dataset.entity;
      renderAll();
    });
  });
}

const PLAYER_FIELDS = [
  { key: 'maxHp', label: 'max HP', type: 'number', min: 1, max: 9999, step: 1 },
  { key: 'attack', label: 'attack', type: 'number', min: 0, max: 9999, step: 1 },
  { key: 'hurtInvuln', label: 'hurt invuln (s)', type: 'number', min: 0, max: 5, step: 0.1 },
];

const MOB_FIELDS = [
  { key: 'maxHp', label: 'max HP', type: 'number', min: 1, max: 9999, step: 1 },
  { key: 'attack', label: 'attack', type: 'number', min: 0, max: 9999, step: 1 },
  { key: 'speed', label: 'speed', type: 'number', min: 0, max: 2000, step: 1 },
  { key: 'width', label: 'width', type: 'number', min: 4, max: 512, step: 1 },
  { key: 'height', label: 'height', type: 'number', min: 4, max: 512, step: 1 },
];

const WEAPON_FIELDS = [
  { key: 'damage', label: 'damage', type: 'number', min: 0, max: 9999, step: 1 },
  { key: 'cooldown', label: 'cooldown (s)', type: 'number', min: 0, max: 10, step: 0.05 },
];

function fieldPath(entity, key) {
  if (entity.kind === 'player') return `player.${key}`;
  if (entity.kind === 'weapon') return `weapons[${entity.index}].${key}`;
  return `mobs[${entity.index}].${key}`;
}

function renderInspector() {
  const ent = entityById(state.selected);
  if (!ent) { $('inspectorTitle').textContent = '—'; return; }
  $('inspectorTitle').textContent = ent.kind === 'player' ? 'Player'
    : ent.kind === 'weapon' ? `Weapon: ${ent.name || ent.id}`
    : `Mob: ${ent.name || ent.id}`;

  const rows = [];
  if (ent.kind === 'mob' || ent.kind === 'weapon') {
    const list = ent.kind === 'mob' ? 'mobs' : 'weapons';
    rows.push(`<label class="field">id<input type="text" value="${esc(ent.id)}" readonly></label>`);
    rows.push(`<label class="field">name<input type="text" data-list="${list}"
        data-idx="${ent.index}" value="${esc(ent.name || '')}"></label>`);
  }
  const fields = ent.kind === 'player' ? PLAYER_FIELDS
    : ent.kind === 'weapon' ? WEAPON_FIELDS : MOB_FIELDS;
  for (const f of fields) {
    const path = fieldPath(ent, f.key);
    const value = ent.stats[f.key];
    rows.push(`<label class="field">${f.label}
        <input type="${f.type}" data-path="${esc(path)}" data-type="${f.type}"
          min="${f.min}" max="${f.max}" step="${f.step}" value="${esc(value)}">
        <span class="field-error" data-error-for="${esc(path)}"></span>
      </label>`);
  }
  $('statsForm').innerHTML = rows.join('');

  $('statsForm').querySelectorAll('input[data-path]').forEach((input) => {
    input.addEventListener('input', () => {
      const path = input.dataset.path;
      const v = input.dataset.type === 'number' ? Number(input.value) : input.value;
      let obj = state.stats;
      const parts = path.split('.');
      const m = parts[0].match(/^(mobs|weapons)\[(\d+)\]$/);
      if (m) {
        const arr = state.stats[m[1]];
        obj = arr ? arr[Number(m[2])] : null;
      } else {
        obj = state.stats[parts[0]];
      }
      if (obj) obj[parts[1]] = v;
    });
  });
  $('statsForm').querySelectorAll('input[data-list]').forEach((input) => {
    input.addEventListener('input', () => {
      const arr = state.stats[input.dataset.list];
      const row = arr ? arr[Number(input.dataset.idx)] : null;
      if (row) row.name = input.value;
    });
  });

  renderArtBlock(ent);
}

function renderArtBlock(ent) {
  const isWeapon = ent.kind === 'weapon';
  document.querySelector('.art-block').classList.remove('hidden');
  document.querySelector('.preview-pane').classList.remove('hidden');
  if (isWeapon) {
    // A weapon icon is a static glyph in-game (secondary disc, HUD chip,
    // loadout rows), so there is no per-part animation to author for it.
    state.anim = null;
    $('animBlock').classList.add('hidden');
  } else {
    loadAnim(ent);
  }
  const asset = ent.asset;
  $('artCurrent').innerHTML = asset
    ? `bound: <code>${esc(asset.src)}</code>
       <span class="muted">(${esc(asset.class)}, fit ${esc(asset.fit)},
       ${esc(asset.anchor)}, precision ${esc(asset.precision)})</span>`
    : 'no SVG bound';

  // In-game status: this is what answers "I bound an SVG and nothing changed".
  $('artStatus').innerHTML = 'in-game: ' + esc(ent.inGame || '')
    + (asset ? ' &mdash; rebuild the APK to see it' : '');

  if (document.activeElement !== $('svgPath')) {
    $('svgPath').value = asset ? asset.src : '';
  }
  $('fitInput').value = asset ? asset.fit
    : ent.kind === 'player' ? '26x36'
    : isWeapon ? '32x32'
    : `${Math.round(ent.stats.width)}x${Math.round(ent.stats.height)}`;
  // The game anchors the player and walkers at their feet, hovering mobs at
  // their centre: prefill whatever the renderer will actually draw at.
  $('anchorSelect').value = asset ? asset.anchor : (ent.suggestedAnchor || 'bottom-center');
  $('precisionInput').value = asset ? asset.precision : 2;
  $('classInput').value = asset ? asset.class
    : 'Art' + ent.id.charAt(0).toUpperCase() + ent.id.slice(1);
  updatePreview();
}

function updatePreview() {
  const img = $('svgPreview');
  const empty = $('previewEmpty');
  const src = previewBaseSrc();
  if (!src.toLowerCase().endsWith('.svg')) {
    view.key = '';
    img.removeAttribute('src');
    img.classList.add('hidden');
    empty.classList.remove('hidden');
    empty.textContent = 'no SVG selected \u2014 pick or type a path below';
    $('zoomLabel').textContent = '100%';
    return;
  }
  empty.classList.add('hidden');
  img.classList.remove('hidden');
  if (view.key !== src) img.src = previewUrl(src);
}

function renderSvgSelect() {
  const sel = $('svgSelect');
  const current = $('svgPath').value;
  sel.innerHTML = '<option value="">— pick a file —</option>' +
    state.svgs.map((s) =>
      `<option value="${esc(s)}" ${s === current ? 'selected' : ''}>${esc(s)}</option>`).join('');
}

/* ---- actions ----------------------------------------------------------- */

function showFieldErrors(errors, formSel) {
  document.querySelectorAll('.field-error').forEach((el) => { el.textContent = ''; });
  const where = document.querySelector(formSel);
  if (where) where.innerHTML = '';
  (errors || []).forEach((err) => {
    const span = document.querySelector(`[data-error-for="${CSS.escape(err.field)}"]`);
    if (span) {
      span.textContent = err.message;
    } else {
      const box = document.querySelector(formSel);
      if (box) box.innerHTML += `<div>${esc(err.field)}: ${esc(err.message)}</div>`;
    }
  });
}

async function saveStats() {
  const body = { player: state.stats.player, mobs: state.stats.mobs,
    weapons: state.stats.weapons || [] };
  const { status, body: resp } = await post('/api/stats', body);
  if (status === 200 && resp.ok) {
    showFieldErrors([]);
    toast('stats saved');
    await loadState();
    renderAll();
  } else {
    showFieldErrors((resp && resp.errors) || [{ field: '', message: 'save failed' }], '#artErrors');
    toast('stats NOT saved', true);
  }
}

async function bindAsset(remove) {
  const ent = entityById(state.selected);
  if (!ent) return;
  const payload = remove
    ? { entity: ent.id, src: null }
    : {
        entity: ent.id,
        src: $('svgPath').value.trim(),
        class: $('classInput').value.trim(),
        fit: $('fitInput').value.trim(),
        anchor: $('anchorSelect').value,
        precision: Number($('precisionInput').value),
      };
  const { status, body: resp } = await post('/api/asset', payload);
  if (status === 200 && resp.ok) {
    toast(remove ? 'SVG removed' : 'SVG bound');
    await loadState();
    renderAll();
  } else {
    showFieldErrors((resp && resp.errors) || [{ field: '', message: 'bind failed' }], '#artErrors');
    toast(remove ? 'remove failed' : 'bind failed', true);
  }
}

/* ---- build ------------------------------------------------------------- */

function renderBuild(b) {
  const fill = $('progressFill');
  const pct = Math.round((b.progress || 0) * 100);
  fill.style.width = pct + '%';
  $('progressPct').textContent = pct + '%';
  fill.classList.toggle('running', b.state === 'running');
  $('buildBtn').disabled = b.state === 'running';

  let status;
  if (b.state === 'running') {
    const step = (b.steps || []).find((s) => s.state === 'running');
    status = step && step.name === 'art' ? 'converting artwork\u2026' : 'converting stats\u2026';
  } else if (b.state === 'done') {
    const secs = b.finishedAt && b.startedAt ? (b.finishedAt - b.startedAt).toFixed(1) : '?';
    status = `done in ${secs}s`;
  } else if (b.state === 'failed') {
    status = `failed: ${b.message || 'unknown error'}`;
  } else {
    status = 'idle';
  }
  $('buildStatus').textContent = status;

  const log = (b.steps || []).map((s) =>
    `== ${s.name} (${s.state}) ==\n${s.log || ''}`).join('\n');
  const logEl = $('buildLog');
  const atBottom = logEl.scrollHeight - logEl.scrollTop - logEl.clientHeight < 8;
  logEl.textContent = log || 'no build yet';
  if (atBottom) logEl.scrollTop = logEl.scrollHeight;
}

async function pollBuild() {
  const { body: b } = await api('/api/build');
  renderBuild(b);
  if (b.state === 'running') {
    state.pollTimer = setTimeout(pollBuild, 250);
  } else {
    state.pollTimer = null;
    if (b.state === 'done') toast('Java code regenerated');
    if (b.state === 'failed') toast('build failed', true);
  }
}

async function startBuild() {
  const { status, body: resp } = await post('/api/build', {});
  if (status === 409) { toast('a build is already running', true); return; }
  if (status !== 200 || !resp.ok) { toast('could not start build', true); return; }
  clearTimeout(state.pollTimer);
  state.pollTimer = setTimeout(pollBuild, 250);
  pollBuild();
}

/* ---- boot -------------------------------------------------------------- */

async function loadState() {
  const { body } = await api('/api/state');
  if (!body || !body.ok) { toast('could not load project state', true); return; }
  state.root = body.root;
  state.entities = body.entities || [];
  state.stats = body.stats || {};
  state.art = body.art || {};
  state.svgs = body.svgs || [];
  state.errors = body.errors || [];
  state.entities.forEach((e) => {
    if (e.kind === 'mob') {
      e.index = state.stats.mobs ? state.stats.mobs.findIndex((m) => m.id === e.id) : -1;
    } else if (e.kind === 'weapon') {
      e.index = state.stats.weapons
        ? state.stats.weapons.findIndex((w) => w.id === e.id) : -1;
    }
  });
  if (!entityById(state.selected)) state.selected = 'player';
  if (state.errors.length) {
    toast(state.errors.map((e) => `${e.field}: ${e.message}`).join('; '), true);
  }
}

async function boot() {
  await loadState();
  renderAll();
  const { body: b } = await api('/api/build');
  if (b) renderBuild(b);
  if (b && b.state === 'running') {
    state.pollTimer = setTimeout(pollBuild, 250);
  }

  $('saveStatsBtn').addEventListener('click', saveStats);
  $('bindBtn').addEventListener('click', () => bindAsset(false));
  $('removeBtn').addEventListener('click', () => bindAsset(true));
  $('buildBtn').addEventListener('click', startBuild);
  // ---- preview: wheel zoom (about the cursor), drag to pan ----
  const vp = $('previewViewport');
  $('svgPreview').addEventListener('load', onPreviewLoad);
  $('zoomInBtn').addEventListener('click', () => zoomBy(1.25));
  $('zoomOutBtn').addEventListener('click', () => zoomBy(1 / 1.25));
  $('zoomFitBtn').addEventListener('click', fitView);
  vp.addEventListener('dblclick', fitView);
  vp.addEventListener('wheel', (ev) => {
    if (!previewVisible()) return;
    ev.preventDefault();          // zoom, do not scroll the panel
    const r = vp.getBoundingClientRect();
    zoomBy(ev.deltaY < 0 ? 1.15 : 1 / 1.15,
           ev.clientX - r.left, ev.clientY - r.top);
  }, { passive: false });
  let dragFrom = null;
  vp.addEventListener('pointerdown', (ev) => {
    if (!previewVisible()) return;
    dragFrom = { x: ev.clientX, y: ev.clientY };
    vp.classList.add('dragging');
    if (vp.setPointerCapture) vp.setPointerCapture(ev.pointerId);
  });
  vp.addEventListener('pointermove', (ev) => {
    if (!dragFrom) return;
    view.tx += ev.clientX - dragFrom.x;
    view.ty += ev.clientY - dragFrom.y;
    dragFrom = { x: ev.clientX, y: ev.clientY };
    applyView();
  });
  const endDrag = (ev) => {
    if (!dragFrom) return;
    dragFrom = null;
    vp.classList.remove('dragging');
    try {
      if (vp.releasePointerCapture) vp.releasePointerCapture(ev.pointerId);
    } catch (e) { /* pointer already gone */ }
  };
  vp.addEventListener('pointerup', endDrag);
  vp.addEventListener('pointercancel', endDrag);
  window.addEventListener('resize', () => {
    // re-fit only while the user is still at fit, so a zoom is never lost
    if (previewVisible() && Math.abs(view.scale - view.base) < 1e-6) fitView();
  });

  document.querySelectorAll('.anim-presets button').forEach((btn) => {
    btn.addEventListener('click', () => applyPreset(btn.dataset.preset));
  });

  ensureViewReady();   // the artwork may have loaded before we listened

  $('animSaveBtn').addEventListener('click', () => saveAnim(false));
  $('animClearBtn').addEventListener('click', () => saveAnim(true));
  $('svgSelect').addEventListener('change', () => {
    if ($('svgSelect').value) {
      $('svgPath').value = $('svgSelect').value;
      updatePreview();
    }
  });
  $('svgPath').addEventListener('input', () => { renderSvgSelect(); updatePreview(); });
  $('svgPreview').addEventListener('error', () => {
    $('svgPreview').classList.add('hidden');
    $('previewEmpty').textContent = 'preview failed \u2014 file not found?';
    $('previewEmpty').classList.remove('hidden');
  });
}

boot();
