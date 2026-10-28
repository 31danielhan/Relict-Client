<script lang="ts">
  import { fly } from 'svelte/transition'
  import { cubicOut } from 'svelte/easing'
  import ModuleRow from '../components/ModuleRow.svelte'
  import { app, nav } from '../lib/store.svelte'
  import { categoryOf } from '../lib/types'

  type Filter = 'all' | 'enabled' | 'approval'
  const FILTERS: { id: Filter; label: string }[] = [
    { id: 'all', label: 'All' },
    { id: 'enabled', label: 'Enabled' },
    { id: 'approval', label: 'Needs approval' },
  ]

  let query = $state('')
  let filter = $state<Filter>('all')
  let grid = $state(false)

  const inCategory = $derived(app.modules.filter((m) => categoryOf(m.id) === nav.category))
  const searching = $derived(query.trim().length > 0)
  // While searching, every module is searched, not just the ones in this group.
  const pool = $derived(searching ? app.modules : inCategory)
  const shown = $derived(
    pool.filter((m) => {
      if (filter === 'enabled' && !m.enabled) return false
      if (filter === 'approval' && m.tier !== 'yellow') return false
      const q = query.trim().toLowerCase()
      return !q || m.name.toLowerCase().includes(q) || m.description.toLowerCase().includes(q)
    }),
  )
  const enabledCount = $derived(inCategory.filter((m) => m.enabled).length)
</script>

<div class="page">
  <header class="top">
    <div>
      {#if searching}
        <h1><em>Search</em> results</h1>
        <p>{shown.length} {shown.length === 1 ? 'module' : 'modules'} found</p>
      {:else}
        <h1><em>{nav.category}</em> Modules</h1>
        <p>{inCategory.length} {inCategory.length === 1 ? 'module' : 'modules'} · {enabledCount} enabled</p>
      {/if}
    </div>
    <label class="search">
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M11 4a7 7 0 100 14 7 7 0 000-14zM20 20l-4-4" /></svg>
      <input type="text" placeholder="Search modules" bind:value={query} aria-label="Search modules" />
    </label>
  </header>

  <div class="bar">
    <div class="chips" role="group" aria-label="Filter modules">
      {#each FILTERS as f}
        <button class="chip" class:active={filter === f.id} aria-pressed={filter === f.id} onclick={() => (filter = f.id)}>{f.label}</button>
      {/each}
    </div>
    <div class="views" role="group" aria-label="Layout">
      <button class:active={!grid} aria-label="List view" aria-pressed={!grid} onclick={() => (grid = false)}>
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
      </button>
      <button class:active={grid} aria-label="Grid view" aria-pressed={grid} onclick={() => (grid = true)}>
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z" /></svg>
      </button>
    </div>
  </div>

  <div class="list" class:grid>
    {#each shown as m, i (m.id)}
      <div class="slot" in:fly={{ y: 12, duration: 300, delay: Math.min(i, 8) * 35, easing: cubicOut }}>
        <ModuleRow module={m} {grid} />
      </div>
    {:else}
      <div class="empty">No modules match.</div>
    {/each}
  </div>
</div>

<style>
  .top { flex-direction: row !important; align-items: flex-start; justify-content: space-between; gap: 20px; }
  .top p { margin-top: 6px; color: var(--muted); }
  .search {
    display: flex; align-items: center; gap: 9px; width: 270px; max-width: 45%; padding: 9px 14px; border-radius: 12px;
    background: #05061c; border: 1px solid var(--border); color: var(--muted); transition: border-color var(--ease);
  }
  .search:focus-within { border-color: var(--accent); }
  .search input { flex: 1; min-width: 0; background: none; border: 0; outline: none; color: var(--text); }
  .search input::placeholder { color: var(--muted); }

  .bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
  .chips { display: flex; gap: 8px; flex-wrap: wrap; }
  .chip {
    padding: 7px 16px; border-radius: 999px; color: #b2b5d6; background: rgba(255, 255, 255, 0.04);
    border: 1px solid var(--border); transition: background var(--ease), color var(--ease), border-color var(--ease);
  }
  .chip:hover { color: var(--text); }
  .chip.active { color: #fff; background: var(--accent-soft); border-color: color-mix(in srgb, var(--accent) 45%, transparent); }
  .views { display: flex; padding: 3px; gap: 2px; border-radius: 11px; background: #05061c; border: 1px solid var(--border); }
  .views button { display: grid; place-items: center; width: 32px; height: 28px; border-radius: 8px; color: var(--muted); transition: background var(--ease), color var(--ease); }
  .views button:hover { color: var(--text); }
  .views button.active { background: #fff; color: #0b0e34; }

  .list { display: flex; flex-direction: column; gap: 12px; }
  .slot { min-width: 0; }
  .list.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(290px, 1fr)); align-items: start; }
</style>
