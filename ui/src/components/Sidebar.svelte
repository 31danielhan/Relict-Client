<script lang="ts">
  import Logo from './Logo.svelte'
  import { app, nav } from '../lib/store.svelte'
  import type { Page } from '../lib/types'

  const items: { id: Page; label: string; icon: string }[] = [
    { id: 'dashboard', label: 'Dashboard', icon: 'M3 3h7v9H3zM14 3h7v5h-7zM14 12h7v9h-7zM3 16h7v5H3z' },
    { id: 'map', label: 'Map', icon: 'M9 4L3 6v14l6-2 6 2 6-2V4l-6 2zM9 4v14M15 6v14' },
    { id: 'waypoints', label: 'Waypoints', icon: 'M12 21s-7-6.2-7-11a7 7 0 0114 0c0 4.8-7 11-7 11zM12 12.5a2.5 2.5 0 100-5 2.5 2.5 0 000 5z' },
    { id: 'modules', label: 'Modules', icon: 'M12 2l9 5v10l-9 5-9-5V7zM12 12l9-5M12 12v10M12 12L3 7' },
    { id: 'settings', label: 'Settings', icon: 'M4 6h10M18 6h2M4 12h2M10 12h10M4 18h12M20 18h0M14 4v4M8 10v4M16 16v4' },
  ]
</script>

<nav>
  <div class="brand"><Logo size={26} />Relict</div>
  <ul>
    {#each items as item}
      <li>
        <button class:active={nav.page === item.id} onclick={() => (nav.page = item.id)}>
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d={item.icon} /></svg>
          {item.label}
        </button>
      </li>
    {/each}
  </ul>
  <div class="foot muted">{app.info ? `v${app.info.version} · MC ${app.info.minecraft}` : ''}</div>
</nav>

<style>
  nav { display: flex; flex-direction: column; gap: 22px; padding: 22px 14px; background: var(--surface); border-right: 1px solid var(--border); }
  .brand { display: flex; align-items: center; gap: 10px; padding: 0 10px; font-size: 18px; font-weight: 700; letter-spacing: -0.01em; }
  ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 2px; }
  li button {
    display: flex; align-items: center; gap: 11px; width: 100%; padding: 9px 10px; border-radius: 8px;
    color: var(--muted); transition: background var(--ease), color var(--ease);
  }
  li button:hover { background: var(--surface-2); color: var(--text); }
  li button.active { background: var(--accent-soft); color: var(--accent); }
  .foot { margin-top: auto; padding: 0 10px; font-size: 12px; }
</style>
