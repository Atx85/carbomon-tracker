'use strict';
const $ = id => document.getElementById(id);
let records = [], editing = null, validText = null, createId = null, busy = false;
function status(message, error = false) { $('status').textContent = message; $('status').classList.toggle('error', error); }
async function request(path, method = 'GET', data, headers = {}) {
 const response = await fetch('/api/v1/' + path, {method, headers: {'Authorization': 'Bearer ' + $('key').value.trim(), ...(data !== undefined ? {'Content-Type': 'application/json'} : {}), ...headers}, body: data !== undefined ? JSON.stringify(data) : undefined});
 const result = await response.json();
 if (!response.ok) throw new Error(result.error || 'Request failed');
 return result;
}
function invalidate() { validText = null; $('save').disabled = true; $('preview').hidden = true; }
function reset() { editing = null; createId = null; $('kind').disabled = false; $('editor-title').textContent = 'Add to the catalogue'; $('cancel').hidden = true; invalidate(); }
function showCatalog(c) { records = c.records.filter(r => !r.deleted); render(); }
async function refresh() { showCatalog(await request('catalog')); status('Connected. Catalogue is up to date.'); }
function render() {
 const q = $('filter').value.toLowerCase(); const list = $('catalogue'); list.replaceChildren();
 $('count').textContent = `${records.filter(r => r.kind === 'food').length} foods · ${records.filter(r => r.kind === 'recipe').length} recipes`;
 for (const r of records.filter(r => ((r.data.name || r.data.description) + ' ' + (r.data.brand || '')).toLowerCase().includes(q))) {
  const article = document.createElement('article'); const title = document.createElement('h3'); title.textContent = r.data.name || r.data.description;
  const detail = document.createElement('p'); detail.className = 'hint'; detail.textContent = r.kind === 'food' ? `${r.data.brand || 'Food'} · ${r.data.caloriesPer100g} kcal / 100 g` : `Recipe · ${r.data.ingredients.length} ingredients`;
  const buttons = document.createElement('div'); buttons.className = 'row';
  const edit = document.createElement('button'); edit.className = 'secondary'; edit.textContent = 'Edit JSON'; edit.onclick = () => { if (busy) return; reset(); editing = r; $('kind').value = r.kind; $('kind').disabled = true; $('schema').href = '/api/v1/schemas/' + r.kind; $('json').value = JSON.stringify(r.data, null, 2); $('editor-title').textContent = 'Edit ' + title.textContent; $('cancel').hidden = false; $('json').focus(); };
  const del = document.createElement('button'); del.className = 'danger'; del.textContent = 'Delete'; del.onclick = () => run(async () => { if (!confirm(`Delete “${title.textContent}” from the shared catalogue? This also removes it from connected apps on their next sync.`)) return; showCatalog(await request(r.kind + 's/' + encodeURIComponent(r.id), 'DELETE', undefined, {'If-Match': String(r.revision)})); if (editing?.id === r.id) reset(); status('Deleted. Devices will receive this change on their next sync.'); });
  buttons.append(edit, del); article.append(title, detail, buttons); list.append(article);
 }
}
async function run(fn) { if (busy) return; busy = true; try { await fn(); } catch (e) { status(e.message, true); } finally { busy = false; } }
$('connect').onclick = $('refresh').onclick = () => run(refresh);
$('filter').oninput = render; $('json').oninput = invalidate;
$('kind').onchange = () => { reset(); $('schema').href = '/api/v1/schemas/' + $('kind').value; };
$('cancel').onclick = () => { reset(); $('json').value = ''; };
$('example').onclick = () => run(async () => { const kind = $('kind').value; const data = await request('examples/' + kind); reset(); $('json').value = JSON.stringify([data], null, 2); status('Example list loaded. Replace its values before saving.'); });
$('prompt').onclick = () => run(async () => { const schema = await request('schemas/' + $('kind').value); $('prompt-label').hidden = $('ai-prompt').hidden = false; $('ai-prompt').value = 'Create a JSON list of ' + $('kind').value + 's. Each object must match this schema. Return only JSON, no Markdown. Use the nutrition labels or sources I provide. Do not invent nutrition or treat an unknown value as a measured zero. Tell me which values I need to supply if information is missing. All nutrition is per 100 grams. Recipe ingredients contain nutrition snapshots; foodId may be omitted. Include a measured finishedWeightGrams only if supplied.\n\n' + JSON.stringify(schema, null, 2) + '\n\nMy food / recipe and source values:\n[Describe here]'; $('ai-prompt').select(); });
$('validate').onclick = () => run(async () => {
 const submitted = $('json').value, parsed = JSON.parse(submitted);
 if (editing && Array.isArray(parsed)) throw new Error('Edit one entry at a time. Cancel editing to import a list.');
 const result = await request('validate/' + $('kind').value, 'POST', parsed);
 if ($('json').value !== submitted) return;
 validText = submitted; $('save').disabled = false;
 const preview = $('preview'); preview.replaceChildren();
 const entries = Array.isArray(result) ? result : [result];
 const summary = document.createElement('p'); summary.textContent = `${entries.length} ${$('kind').value}${entries.length === 1 ? '' : 's'} ready to save.`; preview.append(summary);
 for (const item of entries) {
  const title = document.createElement('strong'); title.textContent = item.name || item.description;
  const p = document.createElement('p'); p.textContent = item.ingredients ? item.ingredients.map(i => `${i.foodDescription}: ${i.gramsUsed} g`).join(' · ') + (item.finishedWeightGrams ? ` · Finished: ${item.finishedWeightGrams} g` : '') : `${item.caloriesPer100g} kcal · protein ${item.proteinPer100g} g · carbs ${item.carbsPer100g} g · fat ${item.fatPer100g} g per 100 g`;
  preview.append(title, p);
 }
 preview.hidden = false; status('JSON is valid. Review the values, then save. The entire list is saved together.');
});
$('save').onclick = () => run(async () => {
 if (validText !== $('json').value) { invalidate(); throw new Error('Validate the current JSON before saving.'); }
 const kind = $('kind').value, data = JSON.parse(validText);
 // Stable across retries, including a lost response. No secure-context APIs needed on LAN HTTP.
 if (!createId) { const bytes = crypto.getRandomValues(new Uint8Array(16)); createId = 'catalog-' + [...bytes].map(b => b.toString(16).padStart(2, '0')).join(''); }
 const result = editing ? await request(kind + 's/' + encodeURIComponent(editing.id), 'PUT', data, {'If-Match': String(editing.revision)}) : await request(kind + 's', 'POST', data, {'Idempotency-Key': createId});
 showCatalog(result); reset(); $('json').value = ''; status('Saved. Sync the app to receive this entry.');
});
