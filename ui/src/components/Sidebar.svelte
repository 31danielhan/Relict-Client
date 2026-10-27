<script lang="ts">
  import Logo from './Logo.svelte'
  import { app, nav } from '../lib/store.svelte'
  import { CATEGORY_ORDER, categoryOf } from '../lib/types'
  import type { Page } from '../lib/types'

  interface Item { id: Page; label: string; icon: string; category?: string; count?: number }

  const ICONS = {
    donut: 'M12 3a9 9 0 100 18 9 9 0 000-18zM12 9.2a2.8 2.8 0 100 5.6 2.8 2.8 0 000-5.6z',
    search: 'M11 4a7 7 0 100 14 7 7 0 000-14zM20 20l-4-4',
    map: 'M9 4L3 6v14l6-2 6 2 6-2V4l-6 2zM9 4v14M15 6v14',
    pin: 'M12 21s-7-6.2-7-11a7 7 0 0114 0c0 4.8-7 11-7 11zM12 12.5a2.5 2.5 0 100-5 2.5 2.5 0 000 5z',
    grid: 'M3 3h7v9H3zM14 3h7v5h-7zM14 12h7v9h-7zM3 16h7v5H3z',
    sliders: 'M4 6h10M18 6h2M4 12h2M10 12h10M4 18h12M20 18h0M14 4v4M8 10v4M16 16v4',
    palette: 'M12 3a9 9 0 100 18c1.4 0 2-1 1.5-2.2-.5-1.2.2-2.3 1.5-2.3H17a4 4 0 004-4c0-5-4-9.5-9-9.5zM7.5 11h.01M10 7.5h.01M14.5 7.5h.01',
    keys: 'M3 7h18v10H3zM7 11h.01M11 11h.01M15 11h.01M7.5 14h9',
    cube: 'M12 2l9 5v10l-9 5-9-5V7zM12 12l9-5M12 12v10M12 12L3 7',
  }
  const categoryIcon = (c: string) => (c === 'Donut' ? ICONS.donut : c === 'Search' ? ICONS.search : ICONS.cube)

  const categories = $derived(
    CATEGORY_ORDER.map((c) => ({ name: c, count: app.modules.filter((m) => categoryOf(m.id) === c).length })).filter((c) => c.count > 0),
  )
  const world: Item[] = [
    { id: 'map', label: 'Map', icon: ICONS.map },
    { id: 'waypoints', label: 'Waypoints', icon: ICONS.pin },
  ]
  const general: Item[] = [
    { id: 'overview', label: 'Overview', icon: ICONS.grid },
    { id: 'settings', label: 'Settings', icon: ICONS.sliders },
    { id: 'theme', label: 'Theme', icon: ICONS.palette },
    { id: 'keybinds', label: 'Keybinds', icon: ICONS.keys },
  ]
</script>

{#snippet icon(d: string)}
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path {d} /></svg>
{/snippet}

<nav>
  <div class="brand">
    <Logo size={34} />
    <div>
      <div class="name">Relict <em>Client</em></div>
      <div class="release">{app.info ? `ALPHA RELEASE ${app.info.version}` : 'ALPHA RELEASE'}</div>
    </div>
  </div>

  <div class="scroll">
    {#if categories.length}
      <h2>Modules</h2>
      <ul>
        {#each categories as c}
          <li>
            <button class:active={nav.page === 'modules' && nav.category === c.name} onclick={() => { nav.category = c.name; nav.page = 'modules' }}>
              {@render icon(categoryIcon(c.name))}<span>{c.name}</span><small>{c.count}</small>
            </button>
          </li>
        {/each}
      </ul>
    {/if}

    <h2>World</h2>
    <ul>
      {#each world as item}
        <li><button class:active={nav.page === item.id} onclick={() => (nav.page = item.id)}>{@render icon(item.icon)}<span>{item.label}</span></button></li>
      {/each}
    </ul>

    <h2>General</h2>
    <ul>
      {#each general as item}
        <li><button class:active={nav.page === item.id} onclick={() => (nav.page = item.id)}>{@render icon(item.icon)}<span>{item.label}</span></button></li>
      {/each}
    </ul>
  </div>
</nav>

<style>
  nav {
    display: flex; flex-direction: column; min-height: 0; padding: 20px 14px 16px;
    border-radius: 18px; border: 1px solid var(--border); border-top-color: rgba(124, 130, 255, 0.22);
    background: linear-gradient(180deg, rgba(14, 16, 56, 0.55), rgba(6, 8, 34, 0.55));
  }
  .brand { display: flex; align-items: center; gap: 12px; padding: 0 8px 18px; }
  .name { font-family: var(--serif); font-size: 21px; line-height: 1.1; }
  .name em { font-style: italic; color: var(--accent-text); }
  .release { margin-top: 3px; font-size: 10.5px; letter-spacing: 0.06em; color: var(--faint); }
  .scroll { flex: 1; min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: 6px; padding-right: 2px; }
  h2 { margin: 14px 10px 4px; }
  h2:first-child { margin-top: 4px; }
  ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 2px; }
  li button {
    display: flex; align-items: center; gap: 12px; width: 100%; padding: 10px 12px; border-radius: 12px; text-align: left;
    color: #aeb1d2; border: 1px solid transparent; transition: background var(--ease), color var(--ease);
  }
  li button svg { flex: none; color: #8a8eb8; }
  li button span { flex: 1; }
  li button small { font-size: 12px; color: var(--faint); }
  li button:hover { background: rgba(255, 255, 255, 0.04); color: var(--text); }
  li button.active {
    color: #fff; border-color: rgba(135, 119, 255, 0.28);
    background: linear-gradient(90deg, color-mix(in srgb, var(--accent) 38%, transparent), color-mix(in srgb, var(--accent) 12%, transparent));
  }
  li button.active svg { color: var(--accent-text); }
  li button.active small { color: #c9c7f0; }
</style>
