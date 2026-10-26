<script lang="ts">
  import { deleteWaypoint, showOnMap, updateWaypoint } from '../lib/store.svelte'
  import { dimensionName, TAGS, tagColor, type Waypoint } from '../lib/types'

  let { waypoint, selected = false, onselect }: { waypoint: Waypoint; selected?: boolean; onselect?: () => void } = $props()

  let editing = $state(false)
  let confirming = $state(false)
  let draft = $state({ name: '', x: 0, y: 0, z: 0, note: '', tags: [] as string[] })
  let confirmTimer: ReturnType<typeof setTimeout> | undefined

  function startEdit() {
    const w = waypoint
    draft = { name: w.name, x: w.x, y: w.y, z: w.z, note: w.note, tags: [...w.tags] }
    editing = true
  }

  async function save() {
    await updateWaypoint(waypoint.id, { ...draft, x: Number(draft.x), y: Number(draft.y), z: Number(draft.z) })
    editing = false
  }

  function toggleTag(id: string) {
    draft.tags = draft.tags.includes(id) ? draft.tags.filter((t) => t !== id) : [...draft.tags, id]
  }

  function remove() {
    if (!confirming) {
      confirming = true
      confirmTimer = setTimeout(() => (confirming = false), 3000)
      return
    }
    clearTimeout(confirmTimer)
    deleteWaypoint(waypoint.id)
  }

  const when = (t: number) => new Date(t).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
</script>

<div class="row" class:selected>
  {#if !editing}
    <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
    <div class="main" onclick={onselect}>
      <span class="dot" style="background:{tagColor(waypoint.tags)}"></span>
      <div class="info">
        <div class="name">{waypoint.name}</div>
        <div class="muted meta">
          {waypoint.x}, {waypoint.y}, {waypoint.z} · {dimensionName(waypoint.dimension)} · {when(waypoint.createdAt)}
        </div>
        {#if waypoint.tags.length}
          <div class="tags">
            {#each waypoint.tags as t}
              <span class="chip small" style="--c:{TAGS.find((x) => x.id === t)?.color}">{TAGS.find((x) => x.id === t)?.label ?? t}</span>
            {/each}
          </div>
        {/if}
        {#if waypoint.note}<div class="note">{waypoint.note}</div>{/if}
      </div>
      <div class="actions">
        <button onclick={(e) => { e.stopPropagation(); showOnMap(waypoint) }}>Map</button>
        <button onclick={(e) => { e.stopPropagation(); startEdit() }}>Edit</button>
        <button class:danger={confirming} onclick={(e) => { e.stopPropagation(); remove() }}>{confirming ? 'Sure?' : 'Delete'}</button>
      </div>
    </div>
  {:else}
    <div class="form">
      <input class="in wide" placeholder="Name" maxlength="48" bind:value={draft.name} />
      <div class="coords">
        <label>X <input class="in" type="number" bind:value={draft.x} /></label>
        <label>Y <input class="in" type="number" bind:value={draft.y} /></label>
        <label>Z <input class="in" type="number" bind:value={draft.z} /></label>
      </div>
      <div class="tags">
        {#each TAGS as t}
          <button class="chip" class:on={draft.tags.includes(t.id)} style="--c:{t.color}" onclick={() => toggleTag(t.id)}>{t.label}</button>
        {/each}
      </div>
      <input class="in wide" placeholder="Note" maxlength="500" bind:value={draft.note} />
      <div class="actions">
        <button class="save" onclick={save}>Save</button>
        <button onclick={() => (editing = false)}>Cancel</button>
      </div>
    </div>
  {/if}
</div>

<style>
  .row { border-top: 1px solid var(--border); }
  .row:first-child { border-top: 0; }
  .row.selected { background: var(--accent-soft); }
  .main { display: flex; align-items: flex-start; gap: 12px; padding: 12px 18px; cursor: default; }
  .dot { flex: none; width: 10px; height: 10px; margin-top: 5px; border-radius: 50%; }
  .info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
  .name { font-weight: 600; }
  .meta { font-size: 12px; }
  .note { font-size: 13px; color: var(--text); opacity: 0.85; }
  .tags { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 2px; }
  .chip {
    padding: 3px 10px; border-radius: 999px; border: 1px solid color-mix(in srgb, var(--c) 45%, transparent);
    color: var(--c); background: transparent; font-size: 12px; transition: background var(--ease);
  }
  .chip.small { padding: 1px 8px; font-size: 11px; }
  .chip.on { background: color-mix(in srgb, var(--c) 22%, transparent); }
  .actions { display: flex; gap: 4px; flex: none; }
  .actions button { padding: 4px 10px; border-radius: 6px; color: var(--muted); }
  .actions button:hover { background: var(--surface-2); color: var(--text); }
  .actions .danger { color: var(--danger); background: rgba(255, 107, 107, 0.12); }
  .actions .save { background: var(--accent); color: #fff; font-weight: 600; }
  .form { display: flex; flex-direction: column; gap: 10px; padding: 14px 18px; }
  .coords { display: flex; gap: 10px; }
  .coords label { display: flex; align-items: center; gap: 6px; color: var(--muted); }
  .in { background: var(--bg); border: 1px solid var(--border); border-radius: 6px; padding: 6px 9px; outline: none; }
  .in:focus { border-color: var(--accent); }
  .in.wide { width: 100%; }
  .coords .in { width: 100px; }
</style>
