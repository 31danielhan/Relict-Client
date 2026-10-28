<script lang="ts">
  import { cubicOut } from 'svelte/easing'
  import { fade, fly } from 'svelte/transition'
  import Sidebar from './components/Sidebar.svelte'
  import { smoothScroll } from './lib/smoothScroll'
  import Keybinds from './pages/Keybinds.svelte'
  import MapPage from './pages/Map.svelte'
  import Modules from './pages/Modules.svelte'
  import Overview from './pages/Overview.svelte'
  import Settings from './pages/Settings.svelte'
  import Theme from './pages/Theme.svelte'
  import Waypoints from './pages/Waypoints.svelte'
  import { app, load, nav, ui } from './lib/store.svelte'
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

<div class="stage" class:open={ui.open}>
  <div class="window">
    <Sidebar />
    <main use:smoothScroll>
      {#if app.error}
        <div class="error" role="alert">
          <span>{app.error}</span>
          <button onclick={() => (app.error = '')} aria-label="Dismiss">✕</button>
        </div>
      {/if}
      {#if !app.ready}
        <div class="empty">{app.error ? 'Could not reach the game.' : 'Loading…'}</div>
      {:else}
        {#key `${nav.page}:${nav.category}`}
          <div class="view" in:fly={{ y: 14, duration: reduced ? 0 : 280, delay: reduced ? 0 : 60, easing: cubicOut }} out:fade={{ duration: reduced ? 0 : 90 }}>
            {#if nav.page === 'overview'}
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
          </div>
        {/key}
      {/if}
    </main>
  </div>
</div>

<style>
  /* The window floats in the middle of the screen over the game; the world stays visible around it. */
  /* In the game the page is exactly the window (980 x 640); in a browser during development the window is centred in the tab. */
  .stage { height: 100%; display: grid; place-items: center; }
  /* The window eases in when it opens and back out when it closes; while closed it is invisible and costs nothing. */
  .window {
    opacity: 0; transform: translateY(10px) scale(0.965); transform-origin: 50% 55%;
    transition: opacity 190ms ease, transform 300ms cubic-bezier(0.2, 0.9, 0.25, 1);
  }
  .stage.open .window { opacity: 1; transform: none; }
  .stage:not(.open) .window { transition-duration: 150ms; transform: translateY(6px) scale(0.98); pointer-events: none; }
  .window {
    display: grid; grid-template-columns: 250px minmax(0, 1fr); gap: 14px; padding: 14px;
    width: min(980px, 100vw); height: min(640px, 100vh);
    border-radius: 30px; border: 1px solid rgba(124, 130, 255, 0.16);
    background:
      radial-gradient(900px 500px at 8% 100%, rgba(80, 70, 220, 0.16), transparent 60%),
      linear-gradient(150deg, var(--window-from) 0%, var(--window-to) 60%);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
  }
  main { position: relative; overflow-y: auto; min-width: 0; min-height: 0; border-radius: 22px; }
  /* Pages replace each other in the same spot while the old one fades out. */
  .view { min-height: 100%; }
  main > :global(.view + .view) { position: absolute; inset: 0; }
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
