<script lang="ts">
  import Sidebar from './components/Sidebar.svelte'
  import Dashboard from './pages/Dashboard.svelte'
  import MapPage from './pages/Map.svelte'
  import Modules from './pages/Modules.svelte'
  import Settings from './pages/Settings.svelte'
  import Waypoints from './pages/Waypoints.svelte'
  import { app, load, nav } from './lib/store.svelte'
  import type { Page } from './lib/types'

  load()

  // #map, #waypoints, ... select a page. Used by the game tests to take screenshots of each page.
  const pages: Page[] = ['dashboard', 'map', 'waypoints', 'modules', 'settings']
  function pageFromHash() {
    const h = location.hash.slice(1) as Page
    if (pages.includes(h)) nav.page = h
  }
  pageFromHash()
  window.addEventListener('hashchange', pageFromHash)

  const accent = $derived(String(app.client.find((s) => s.id === 'accent')?.value ?? 'BLUE').toLowerCase())
  const reduced = $derived(app.client.find((s) => s.id === 'reducedMotion')?.value === true)
  $effect(() => { document.documentElement.dataset.accent = accent })
  $effect(() => { document.documentElement.dataset.motion = reduced ? 'reduced' : 'full' })
</script>

<div class="shell">
  <Sidebar />
  <main>
    {#if app.error}
      <div class="error" role="alert">
        <span>{app.error}</span>
        <button onclick={() => (app.error = '')} aria-label="Dismiss">✕</button>
      </div>
    {/if}
    {#if !app.ready}
      <div class="empty">{app.error ? 'Could not reach the game.' : 'Loading…'}</div>
    {:else if nav.page === 'dashboard'}
      <Dashboard />
    {:else if nav.page === 'map'}
      <MapPage />
    {:else if nav.page === 'waypoints'}
      <Waypoints />
    {:else if nav.page === 'modules'}
      <Modules />
    {:else}
      <Settings />
    {/if}
  </main>
</div>

<style>
  .shell { display: grid; grid-template-columns: 210px 1fr; height: 100%; }
  main { position: relative; overflow-y: auto; min-width: 0; min-height: 0; }
  .error {
    position: sticky; top: 0; z-index: 5; display: flex; justify-content: space-between; align-items: center; gap: 12px;
    padding: 9px 16px; background: rgba(255, 107, 107, 0.14); color: var(--danger); border-bottom: 1px solid rgba(255, 107, 107, 0.35);
  }
  .error button { color: inherit; padding: 0 4px; }
</style>
