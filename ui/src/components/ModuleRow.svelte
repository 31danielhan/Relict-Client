<script lang="ts">
  import SettingControl from './SettingControl.svelte'
  import Toggle from './Toggle.svelte'
  import { slide } from 'svelte/transition'
  import { cubicOut } from 'svelte/easing'
  import { setEnabled } from '../lib/store.svelte'
  import type { ModuleDto } from '../lib/types'

  let { module, grid = false }: { module: ModuleDto; grid?: boolean } = $props()
  let open = $state(false)
</script>

<div class="row" class:on={module.enabled} class:open class:grid>
  <div class="head" role="button" tabindex="0" onclick={() => (open = !open)} onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), (open = !open))} aria-expanded={open}>
    <div class="text">
      <div class="name">{module.name}</div>
      <div class="desc">{module.description}</div>
      {#if module.unavailable}<div class="note">{module.unavailable}</div>{/if}
    </div>
    {#if module.tier === 'yellow'}<span class="tier" title="Needs staff approval before use on a server">Needs approval</span>{/if}
    <svg class="chev" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 6l6 6-6 6" /></svg>
    <Toggle checked={module.enabled} label="Enable {module.name}" onchange={(v) => setEnabled(module.id, v)} />
  </div>

  {#if open}
    <div class="settings" transition:slide={{ duration: 260, easing: cubicOut }}>
      {#each module.settings as s (s.id)}
        <SettingControl module={module.id} setting={s} />
      {:else}
        <div class="muted none">This module has no settings.</div>
      {/each}
    </div>
  {/if}
</div>

<style>
  .row {
    position: relative; border-radius: 20px; overflow: hidden;
    background: linear-gradient(180deg, rgba(15, 18, 62, 0.6), rgba(9, 11, 42, 0.6));
    border: 1px solid var(--border); border-top-color: rgba(124, 130, 255, 0.24);
    transition: background var(--ease), border-color var(--ease), transform 180ms ease, box-shadow 220ms ease;
  }
  .row:hover { border-color: rgba(124, 130, 255, 0.32); transform: translateY(-1px); box-shadow: 0 10px 26px -14px rgba(0, 0, 0, 0.7); }
  /* A module that is switched on gets a glowing bar down its left edge and a faint tint. */
  .row.on { background: linear-gradient(180deg, rgba(26, 28, 90, 0.7), rgba(15, 17, 64, 0.7)); }
  .row.on::before {
    content: ''; position: absolute; left: 0; top: 14px; bottom: 14px; width: 3px; border-radius: 0 3px 3px 0;
    background: var(--accent); box-shadow: 0 0 14px 1px var(--accent-glow);
  }
  .head { display: flex; align-items: center; gap: 14px; padding: 15px 18px 15px 22px; cursor: pointer; }
  .text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
  .name { font-weight: 700; font-size: 15px; }
  .desc { font-size: 12.5px; color: var(--muted); }
  .note { font-size: 12px; color: var(--yellow); }
  .tier {
    flex: none; font-size: 10.5px; font-weight: 600; letter-spacing: 0.02em; color: var(--yellow);
    background: rgba(245, 183, 61, 0.1); border: 1px solid rgba(245, 183, 61, 0.22); border-radius: 999px; padding: 2px 9px;
  }
  .chev { flex: none; color: var(--muted); transition: transform var(--ease), color var(--ease); }
  .head:hover .chev { color: var(--text); }
  .open .chev { transform: rotate(90deg); }
  .settings { padding: 2px 22px 10px; border-top: 1px solid var(--border-soft); background: rgba(3, 4, 24, 0.35); }
  .none { padding: 12px 0; }

  .grid .head { flex-wrap: wrap; align-items: flex-start; }
  .grid .text { flex-basis: 100%; }
  .grid .chev { margin-left: auto; }
</style>
