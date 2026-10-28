<script lang="ts">
  import { onMount } from 'svelte'
  import CoverageMap from '../components/CoverageMap.svelte'
  import WaypointRow from '../components/WaypointRow.svelte'
  import { clearCoverage, isEnabled, loadStats, loadWaypoints, nav, setEnabled, ui, world } from '../lib/store.svelte'
  import { dimensionName, TAGS } from '../lib/types'

  let dimension = $state('minecraft:overworld')
  let selectedId = $state<string | null>(null)
  let map = $state<ReturnType<typeof CoverageMap> | null>(null)
  let confirmClear = $state(false)
  let seededDimension = false
  let confirmTimer: ReturnType<typeof setTimeout> | undefined

  // Start on the dimension the player is in, once we know it.
  $effect(() => {
    if (!seededDimension && world.player) {
      dimension = world.player.dimension
      seededDimension = true
    }
  })
  // "Show on map" can ask for another dimension.
  $effect(() => { if (nav.focus) dimension = nav.focus.dimension })

  const dimensions = $derived(
    [...new Set(['minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end', ...world.stats.map((s) => s.dimension)])],
  )
  const stat = $derived(world.stats.find((s) => s.dimension === dimension))
  const selected = $derived(world.waypoints.find((w) => w.id === selectedId) ?? null)
  const coverageOn = $derived(isEnabled('coverage'))
  const waypointsOn = $derived(isEnabled('waypoints'))

  $effect(() => { coverageOn; loadStats() })
  $effect(() => { waypointsOn; loadWaypoints() })
  onMount(() => {
    const t = setInterval(() => { if (ui.open) loadStats() }, 3000)
    return () => clearInterval(t)
  })

  function clear() {
    if (!confirmClear) {
      confirmClear = true
      confirmTimer = setTimeout(() => (confirmClear = false), 3000)
      return
    }
    clearTimeout(confirmTimer)
    confirmClear = false
    clearCoverage(dimension)
  }

  const km2 = (v: number) => (v < 0.01 ? v.toFixed(4) : v < 10 ? v.toFixed(2) : v.toFixed(1))
</script>

<div class="layout">
  <div class="left">
    <header>
      <h1><em>World</em> Map</h1>
      <p class="muted">Drag to pan, scroll to zoom. The shaded area is ground you have already flown over.</p>
    </header>
    {#if !coverageOn}
      <div class="notice card">
        <span>Search Coverage is off, so nothing is being recorded.</span>
        <button class="primary" onclick={() => setEnabled('coverage', true)}>Turn on</button>
      </div>
    {/if}
    <div class="mapbox"><CoverageMap bind:this={map} {dimension} {selectedId} onselect={(id) => (selectedId = id)} /></div>
  </div>

  <aside>
    <section>
      <h2>Dimension</h2>
      <div class="seg" role="group" aria-label="Dimension">
        {#each dimensions as d}
          <button class:active={d === dimension} onclick={() => (dimension = d)}>{dimensionName(d)}</button>
        {/each}
      </div>
    </section>

    <section>
      <h2>Searched</h2>
      <div class="card stat">
        <div class="big">{stat ? km2(stat.areaKm2) : '0'}<span class="muted unit"> km²</span></div>
        <div class="muted small">{(stat?.chunks ?? 0).toLocaleString()} chunks in {dimensionName(dimension)}</div>
      </div>
      <button class="ghost" class:danger={confirmClear} disabled={!stat?.chunks} onclick={clear}>
        {confirmClear ? 'Really clear? Click again' : 'Clear this dimension'}
      </button>
    </section>

    {#if !world.player}
      <p class="muted small">Join a world or server to start recording and see yourself on the map.</p>
    {/if}

    {#if selected}
      <section>
        <h2>Selected</h2>
        <div class="card"><WaypointRow waypoint={selected} selected={false} /></div>
      </section>
    {/if}

    <section>
      <h2>Legend</h2>
      <div class="legend">
        {#each TAGS as t}<span><i style="background:{t.color}"></i>{t.label}</span>{/each}
      </div>
    </section>
  </aside>
</div>

<style>
  .layout { display: grid; grid-template-columns: 1fr 290px; gap: 14px; height: 100%; }
  .left { display: flex; flex-direction: column; gap: 14px; padding: 26px 0 16px 30px; min-width: 0; min-height: 0; }
  .left > header { display: flex; flex-direction: column; gap: 4px; }
  .mapbox { flex: 1; min-height: 0; }
  @media (max-width: 960px) {
    .layout { grid-template-columns: 1fr 230px; gap: 10px; }
    .left { padding: 20px 0 10px 20px; }
  }
  .notice { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 9px 14px; }
  .primary { padding: 5px 14px; border-radius: 9px; background: var(--accent); color: #fff; font-weight: 600; }
  aside { display: flex; flex-direction: column; gap: 20px; padding: 24px 20px; overflow-y: auto; border-radius: 18px; background: linear-gradient(180deg, rgba(14, 16, 56, 0.55), rgba(6, 8, 34, 0.55)); border: 1px solid var(--border); border-top-color: rgba(124, 130, 255, 0.22); }
  section { display: flex; flex-direction: column; gap: 9px; }
  .seg { display: flex; background: #05061c; border: 1px solid var(--border); border-radius: 11px; padding: 3px; }
  .seg button { flex: 1; padding: 5px 8px; border-radius: 8px; color: var(--muted); }
  .seg button:hover { color: var(--text); }
  .seg button.active { background: var(--accent-soft); color: var(--accent); }
  .stat { padding: 14px 16px; }
  .big { font-family: var(--serif); font-size: 32px; line-height: 1.1; }
  .unit { font-size: 15px; font-weight: 500; }
  .small { font-size: 12px; }
  .ghost { padding: 6px 10px; border-radius: 7px; border: 1px solid var(--border); color: var(--muted); }
  .ghost:hover:not(:disabled) { color: var(--text); background: var(--surface-2); }
  .ghost:disabled { opacity: 0.35; cursor: default; }
  .ghost.danger { color: var(--danger); border-color: var(--danger); background: rgba(255, 107, 107, 0.1); }
  .legend { display: flex; flex-direction: column; gap: 6px; font-size: 13px; }
  .legend span { display: flex; align-items: center; gap: 9px; color: var(--muted); }
  .legend i { width: 9px; height: 9px; border-radius: 50%; }
</style>
