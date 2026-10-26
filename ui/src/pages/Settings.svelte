<script lang="ts">
  import SettingControl from '../components/SettingControl.svelte'
  import { CLIENT } from '../lib/types'
  import { app } from '../lib/store.svelte'
</script>

<div class="page">
  <header>
    <h1>Settings</h1>
    <p class="muted">Applies to the whole client. Module options live on the Modules page.</p>
  </header>

  <section>
    <h2>Appearance</h2>
    <div class="card pad">
      {#each app.client as s (s.id)}
        <SettingControl module={CLIENT} setting={s} />
      {/each}
    </div>
  </section>

  <section>
    <h2>Keys</h2>
    <div class="card pad note">
      Open this window with <kbd>{app.info?.uiKey ?? '…'}</kbd>. Change it, and add keys for individual modules, under
      Options → Controls → Relict.
    </div>
  </section>

  <section>
    <h2>About</h2>
    <div class="card pad note">
      Relict {app.info?.version} on Minecraft {app.info?.minecraft}.
      {#if app.info?.dev}<span class="badge yellow">Development build</span>{/if}
    </div>
  </section>
</div>

<style>
  section { display: flex; flex-direction: column; gap: 10px; }
  .pad { padding: 4px 18px; }
  .note { padding: 14px 18px; }
  kbd { background: var(--surface-2); border: 1px solid var(--border); border-radius: 5px; padding: 1px 7px; font: inherit; font-weight: 600; }
</style>
