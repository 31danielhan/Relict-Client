<script lang="ts">
  import Toggle from '../components/Toggle.svelte'
  import { app, setEnabled } from '../lib/store.svelte'

  const active = $derived(app.modules.filter((m) => m.enabled))
</script>

<div class="page">
  <header>
    <h1>Dashboard</h1>
    <p class="muted">Press {app.info?.uiKey ?? 'the UI key'} to open this window. Esc closes it.</p>
  </header>

  <section class="stats">
    <div class="card stat"><h2>Active modules</h2><div class="value">{active.length}<span class="muted"> / {app.modules.length}</span></div></div>
    <div class="card stat"><h2>Minecraft</h2><div class="value">{app.info?.minecraft}</div></div>
    <div class="card stat"><h2>Relict</h2><div class="value">{app.info?.version}</div></div>
  </section>

  <section class="list">
    <h2>Enabled modules</h2>
    <div class="card">
      {#each active as m (m.id)}
        <div class="item">
          <div><div class="name">{m.name}</div><div class="muted small">{m.description}</div></div>
          <Toggle checked={m.enabled} label="Enable {m.name}" onchange={(v) => setEnabled(m.id, v)} />
        </div>
      {:else}
        <div class="empty">Nothing is enabled yet. Turn modules on from the Modules page.</div>
      {/each}
    </div>
  </section>
</div>

<style>
  .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 14px; }
  .stat { padding: 16px 18px; display: flex; flex-direction: column; gap: 6px; }
  .value { font-size: 26px; font-weight: 650; letter-spacing: -0.02em; }
  .value .muted { font-size: 16px; font-weight: 500; }
  .list { display: flex; flex-direction: column; gap: 10px; }
  .item { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 18px; border-top: 1px solid var(--border); }
  .item:first-child { border-top: 0; }
  .name { font-weight: 600; }
  .small { font-size: 12px; }
</style>
