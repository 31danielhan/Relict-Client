<script lang="ts">
  import Toggle from './Toggle.svelte'
  import { setEnabled } from '../lib/store.svelte'
  import type { ModuleDto } from '../lib/types'

  let { module, selected = false, onselect }: { module: ModuleDto; selected?: boolean; onselect: () => void } = $props()
</script>

<div class="card module" class:selected role="button" tabindex="0" onclick={onselect} onkeydown={(e) => e.key === 'Enter' && onselect()}>
  <div class="top">
    <span class="title">{module.name}</span>
    <Toggle checked={module.enabled} label="Enable {module.name}" onchange={(v) => setEnabled(module.id, v)} />
  </div>
  <p class="muted">{module.description}</p>
  <span class="badge {module.tier}">{module.tier === 'green' ? 'Safe' : 'Needs approval'}</span>
  {#if module.unavailable}<p class="note">{module.unavailable}</p>{/if}
</div>

<style>
  .module { display: flex; flex-direction: column; gap: 8px; padding: 14px 16px; cursor: pointer; transition: border-color var(--ease), background var(--ease); }
  .module:hover { background: var(--surface-2); }
  .module.selected { border-color: var(--accent); }
  .top { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
  .title { font-weight: 600; }
  p { font-size: 13px; flex: 1; }
  .badge { align-self: flex-start; }
  .note { flex: none; color: var(--yellow); }
</style>
