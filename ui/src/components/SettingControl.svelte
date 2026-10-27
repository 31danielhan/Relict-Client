<script lang="ts">
  import Toggle from './Toggle.svelte'
  import { resetSetting, setSetting } from '../lib/store.svelte'
  import type { SettingDto } from '../lib/types'

  let { module, setting }: { module: string; setting: SettingDto } = $props()

  // Editable copy so sliders and text fields feel live; committed to the game on release / blur / Enter.
  // It follows the game's value again whenever that changes.
  let draft = $derived(setting.value)

  const label = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ')
  const step = $derived(setting.type === 'int' ? 1 : Math.max(((setting.max ?? 1) - (setting.min ?? 0)) / 100, 0.01))
  const changed = $derived(setting.value !== setting.default)
  const commit = (value: unknown) => setSetting(module, setting.id, value)
</script>

<div class="row">
  <div class="text">
    <div class="name">{setting.name}</div>
    {#if setting.description}<div class="muted desc">{setting.description}</div>{/if}
  </div>

  <div class="control">
    {#if setting.type === 'bool'}
      <Toggle checked={setting.value as boolean} label={setting.name} onchange={commit} />
    {:else if setting.type === 'int' || setting.type === 'double'}
      <input type="range" min={setting.min} max={setting.max} {step} bind:value={draft} onchange={() => commit(Number(draft))} />
      <input class="num" type="number" min={setting.min} max={setting.max} {step} bind:value={draft} onchange={() => commit(Number(draft))} />
    {:else if setting.type === 'string'}
      <input class="text-in" type="text" maxlength={setting.maxLength} bind:value={draft} onchange={() => commit(String(draft))} />
    {:else if setting.type === 'enum'}
      <div class="seg" role="group" aria-label={setting.name}>
        {#each setting.options ?? [] as option}
          <button class:active={setting.value === option} aria-pressed={setting.value === option} onclick={() => commit(option)}>{label(option)}</button>
        {/each}
      </div>
    {/if}
    <button class="reset" disabled={!changed} title="Reset to default" aria-label="Reset {setting.name}" onclick={() => resetSetting(module, setting.id)}>↺</button>
  </div>
</div>

<style>
  .row { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 13px 0; border-top: 1px solid var(--border-soft); }
  .row:first-child { border-top: 0; }
  .name { font-weight: 550; }
  .desc { font-size: 12px; }
  .control { display: flex; align-items: center; gap: 10px; flex: none; }

  input[type='range'] { width: 150px; accent-color: var(--accent); }
  .num, .text-in {
    background: #05061c; border: 1px solid var(--border); border-radius: 9px; padding: 6px 10px; outline: none;
  }
  .num { width: 70px; }
  .text-in { width: 170px; }
  .num:focus, .text-in:focus { border-color: var(--accent); }

  .seg { display: flex; background: #05061c; border: 1px solid var(--border); border-radius: 11px; padding: 3px; }
  .seg button { padding: 4px 12px; border-radius: 8px; color: var(--muted); transition: background var(--ease), color var(--ease); }
  .seg button:hover { color: var(--text); }
  .seg button.active { background: var(--accent-soft); color: var(--accent); }

  .reset { width: 24px; height: 24px; border-radius: 6px; color: var(--muted); }
  .reset:hover:not(:disabled) { background: var(--surface-2); color: var(--text); }
  .reset:disabled { opacity: 0.25; cursor: default; }
</style>
