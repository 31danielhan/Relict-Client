<script lang="ts">
  import { onMount } from 'svelte'
  import ModulePrompt from '../components/ModulePrompt.svelte'
  import WaypointRow from '../components/WaypointRow.svelte'
  import { addWaypointHere, isEnabled, loadWaypoints, world } from '../lib/store.svelte'
  import { TAGS } from '../lib/types'

  let filter = $state<string | null>(null)
  let query = $state('')

  const shown = $derived(
    world.waypoints
      .filter((w) => (filter ? w.tags.includes(filter) : true))
      .filter((w) => w.name.toLowerCase().includes(query.trim().toLowerCase()))
      .toSorted((a, b) => b.createdAt - a.createdAt),
  )

  const enabled = $derived(isEnabled('waypoints'))
  $effect(() => { enabled; loadWaypoints() })
  onMount(loadWaypoints)
</script>

<div class="page">
  <header>
    <h1><em>Saved</em> Waypoints</h1>
    <p class="muted">The places you've found. Bind a key under Options → Controls → Relict to drop one where you stand.</p>
  </header>

  {#if !enabled}
    <ModulePrompt id="waypoints" what="Turn it on to save and tag the places you find." />
  {:else}
    <div class="bar">
      <button class="primary" onclick={() => addWaypointHere()} disabled={!!world.waypointsNote}>Add at my position</button>
      <input class="search" placeholder="Search" bind:value={query} />
      <div class="filters">
        <button class="f" class:on={filter === null} onclick={() => (filter = null)}>All</button>
        {#each TAGS as t}
          <button class="f" class:on={filter === t.id} style="--c:{t.color}" onclick={() => (filter = filter === t.id ? null : t.id)}>{t.label}</button>
        {/each}
      </div>
    </div>

    <div class="card">
      {#if world.waypointsNote}
        <div class="empty">{world.waypointsNote}</div>
      {:else}
        {#each shown as w (w.id)}
          <WaypointRow waypoint={w} />
        {:else}
          <div class="empty">{world.waypoints.length ? 'No waypoints match.' : 'No waypoints yet. Add one at your position, or bind a key to drop them as you fly.'}</div>
        {/each}
      {/if}
    </div>
  {/if}
</div>

<style>
  .bar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
  .primary { padding: 8px 16px; border-radius: 10px; background: var(--accent); color: #fff; font-weight: 600; box-shadow: 0 6px 18px -8px var(--accent-glow); }
  .primary:hover:not(:disabled) { filter: brightness(1.1); }
  .primary:disabled { opacity: 0.4; cursor: default; }
  .search { background: #05061c; border: 1px solid var(--border); border-radius: 10px; padding: 7px 11px; width: 190px; outline: none; }
  .search:focus { border-color: var(--accent); }
  .filters { display: flex; flex-wrap: wrap; gap: 6px; }
  .f { padding: 4px 12px; border-radius: 999px; border: 1px solid var(--border); color: var(--muted); --c: var(--accent); }
  .f:hover { color: var(--text); }
  .f.on { color: var(--c); border-color: var(--c); background: color-mix(in srgb, var(--c) 16%, transparent); }
</style>
