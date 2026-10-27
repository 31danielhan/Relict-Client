<script lang="ts">
  import { app } from '../lib/store.svelte'
</script>

<div class="page">
  <header>
    <h1><em>Client</em> Keybinds</h1>
    <p>Keys are set in Minecraft's own Controls screen, so they work with everything else you have bound.</p>
  </header>

  <section>
    <h2>Open this window</h2>
    <div class="card note">
      <kbd>{app.info?.uiKey ?? '…'}</kbd>
      <span class="muted">Esc closes it again.</span>
    </div>
  </section>

  <section>
    <h2>Change a key</h2>
    <div class="card note">
      Go to Options → Controls → Key Binds and look under <b>Relict</b>. Each module that has a hotkey, such as dropping a waypoint, is listed there.
    </div>
  </section>
</div>

<style>
  section { display: flex; flex-direction: column; gap: 10px; }
  .note { padding: 16px 18px; display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
  kbd {
    background: rgba(255, 255, 255, 0.06); border: 1px solid var(--border); border-bottom-width: 2px; border-radius: 8px;
    padding: 3px 11px; font: inherit; font-weight: 700;
  }
</style>
