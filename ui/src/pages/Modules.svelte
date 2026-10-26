<script lang="ts">
  import ModuleCard from '../components/ModuleCard.svelte'
  import SettingControl from '../components/SettingControl.svelte'
  import Toggle from '../components/Toggle.svelte'
  import { app, setEnabled } from '../lib/store.svelte'

  let selectedId = $state<string | null>(null)
  const selected = $derived(app.modules.find((m) => m.id === selectedId) ?? null)
</script>

<div class="layout" class:open={selected}>
  <div class="page">
    <header>
      <h1>Modules</h1>
      <p class="muted">Turn tools on or off and tune how they behave. Each module can also get its own key in Controls.</p>
    </header>
    <div class="grid">
      {#each app.modules as m (m.id)}
        <ModuleCard module={m} selected={m.id === selectedId} onselect={() => (selectedId = m.id === selectedId ? null : m.id)} />
      {/each}
    </div>
  </div>

  {#if selected}
    <aside>
      <div class="head">
        <div>
          <h1>{selected.name}</h1>
          <span class="badge {selected.tier}">{selected.tier === 'green' ? 'Safe' : 'Needs approval'}</span>
        </div>
        <Toggle checked={selected.enabled} label="Enable {selected.name}" onchange={(v) => setEnabled(selected.id, v)} />
      </div>
      <p class="muted">{selected.description}</p>
      {#if selected.unavailable}<p class="unavailable">{selected.unavailable}</p>{/if}
      <h2>Settings</h2>
      <div class="settings">
        {#each selected.settings as s (s.id)}
          <SettingControl module={selected.id} setting={s} />
        {:else}
          <div class="muted">This module has no settings.</div>
        {/each}
      </div>
    </aside>
  {/if}
</div>

<style>
  .unavailable { font-size: 13px; color: var(--yellow); margin: 0; }
  .layout { display: grid; grid-template-columns: 1fr; height: 100%; }
  .layout.open { grid-template-columns: 1fr 400px; }
  .layout > .page { overflow-y: auto; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 14px; }
  aside { display: flex; flex-direction: column; gap: 14px; padding: 28px 24px; overflow-y: auto; background: var(--surface); border-left: 1px solid var(--border); }
  .head { display: flex; justify-content: space-between; align-items: flex-start; gap: 14px; }
  .head h1 { margin-bottom: 6px; }
</style>
