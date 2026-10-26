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
  pollTimer: null,
};

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
  renderEntityList();
  renderInspector();
  renderSvgSelect();
}

function renderEntityList() {
  const rows = state.entities.map((e) =>
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
  return entity.kind === 'player' ? `player.${key}` : `mobs[${entity.index}].${key}`;
}

function renderInspector() {
  const ent = entityById(state.selected);
  if (!ent) { $('inspectorTitle').textContent = '—'; return; }
  $('inspectorTitle').textContent = ent.kind === 'player' ? 'Player'
    : ent.kind === 'weapon' ? `Weapon: ${ent.name || ent.id}`
    : `Mob: ${ent.name || ent.id}`;

  const rows = [];
  if (ent.kind === 'mob') {
    rows.push(`<label class="field">id<input type="text" value="${esc(ent.id)}" readonly></label>`);
    rows.push(`<label class="field">name<input type="text" data-mob-name="${ent.index}"
        value="${esc(ent.name || '')}"></label>`);
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
      const m = parts[0].match(/^mobs\[(\d+)\]$/);
      if (m) { obj = state.stats.mobs[Number(m[1])]; } else { obj = state.stats[parts[0]]; }
      if (obj) obj[parts[1]] = v;
    });
  });
  $('statsForm').querySelectorAll('input[data-mob-name]').forEach((input) => {
    input.addEventListener('input', () => {
      const mob = state.stats.mobs[Number(input.dataset.mobName)];
      if (mob) mob.name = input.value;
    });
  });

  renderArtBlock(ent);
}

function renderArtBlock(ent) {
  if (ent.kind === 'weapon') {
    document.querySelector('.art-block').classList.add('hidden');
    return;
  }
  document.querySelector('.art-block').classList.remove('hidden');
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
    : (ent.kind === 'player' ? '26x36' : `${Math.round(ent.stats.width)}x${Math.round(ent.stats.height)}`);
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
  const src = $('svgPath').value.trim();
  if (src.toLowerCase().endsWith('.svg')) {
    img.src = '/api/svg?path=' + encodeURIComponent(src);
    img.classList.remove('hidden');
    empty.classList.add('hidden');
  } else {
    img.removeAttribute('src');
    img.classList.add('hidden');
    empty.classList.remove('hidden');
  }
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
