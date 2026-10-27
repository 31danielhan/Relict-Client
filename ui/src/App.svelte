<script lang="ts">
  import Sidebar from './components/Sidebar.svelte'
  import Keybinds from './pages/Keybinds.svelte'
  import MapPage from './pages/Map.svelte'
  import Modules from './pages/Modules.svelte'
  import Overview from './pages/Overview.svelte'
  import Settings from './pages/Settings.svelte'
  import Theme from './pages/Theme.svelte'
  import Waypoints from './pages/Waypoints.svelte'
  import { app, load, nav } from './lib/store.svelte'
  import type { Page } from './lib/types'

  load()

  // #map, #waypoints, ... select a page. Used by the game tests to take screenshots of each page.
  // (#dashboard is the old name of the overview.)
  const pages: Page[] = ['overview', 'map', 'waypoints', 'modules', 'settings', 'theme', 'keybinds']
  function pageFromHash() {
    const h = location.hash.slice(1)
    if (h === 'dashboard') nav.page = 'overview'
    else if (pages.includes(h as Page)) nav.page = h as Page
    else if (h.startsWith('modules:')) { nav.page = 'modules'; nav.category = decodeURIComponent(h.slice('modules:'.length)) }
  }
  pageFromHash()
  window.addEventListener('hashchange', pageFromHash)

  const accent = $derived(String(app.client.find((s) => s.id === 'accent')?.value ?? 'PURPLE').toLowerCase())
  const reduced = $derived(app.client.find((s) => s.id === 'reducedMotion')?.value === true)
  $effect(() => { document.documentElement.dataset.accent = accent })
  $effect(() => { document.documentElement.dataset.motion = reduced ? 'reduced' : 'full' })
</script>

<div class="stage">
  <div class="window">
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
      {:else if nav.page === 'overview'}
        <Overview />
      {:else if nav.page === 'map'}
        <MapPage />
      {:else if nav.page === 'waypoints'}
        <Waypoints />
      {:else if nav.page === 'modules'}
        <Modules />
      {:else if nav.page === 'theme'}
        <Theme />
      {:else if nav.page === 'keybinds'}
        <Keybinds />
      {:else}
        <Settings />
      {/if}
    </main>
  </div>
</div>

<style>
  /* The window floats in the middle of the screen over the game; the world stays visible around it. */
  .stage { height: 100%; display: grid; place-items: center; padding: 20px; }
  .window {
    display: grid; grid-template-columns: 250px minmax(0, 1fr); gap: 14px; padding: 14px;
    width: min(1120px, calc(100vw - 40px)); height: min(740px, calc(100vh - 40px));
    border-radius: 26px; border: 1px solid rgba(124, 130, 255, 0.16);
    background:
      radial-gradient(900px 500px at 8% 100%, rgba(80, 70, 220, 0.16), transparent 60%),
      linear-gradient(150deg, var(--window-from) 0%, var(--window-to) 60%);
    box-shadow: 0 30px 80px -20px rgba(0, 0, 0, 0.75), inset 0 1px 0 rgba(255, 255, 255, 0.04);
  }
  main { position: relative; overflow-y: auto; min-width: 0; min-height: 0; border-radius: 18px; }
  .error {
    position: sticky; top: 0; z-index: 5; display: flex; justify-content: space-between; align-items: center; gap: 12px;
    padding: 9px 16px; border-radius: 12px; background: rgba(255, 107, 107, 0.14); color: var(--danger); border: 1px solid rgba(255, 107, 107, 0.35);
  }
  .error button { color: inherit; padding: 0 4px; }

  /* A small game window (low resolution or a big GUI scale) gets narrower side panels. */
  @media (max-width: 960px) {
    .window { grid-template-columns: 200px minmax(0, 1fr); gap: 10px; padding: 10px; }
    :global(.page) { padding: 20px 20px 24px; }
  }
</style>
