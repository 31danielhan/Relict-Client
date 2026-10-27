<script lang="ts">
  import SettingControl from '../components/SettingControl.svelte'
  import { CLIENT } from '../lib/types'
  import { app } from '../lib/store.svelte'

  // The accent colour has its own page (Theme); everything else about the client itself is here.
  const general = $derived(app.client.filter((s) => s.id !== 'accent'))
</script>

<div class="page">
  <header>
    <h1><em>Client</em> Settings</h1>
    <p>Applies to the whole client. Module options are on each module's own row.</p>
  </header>

  <section>
    <h2>General</h2>
    <div class="card pad">
      {#each general as s (s.id)}
        <SettingControl module={CLIENT} setting={s} />
      {:else}
        <div class="note muted">Nothing to set here yet.</div>
      {/each}
    </div>
  </section>

  <section>
    <h2>About</h2>
    <div class="card note">
      Relict {app.info?.version} on Minecraft {app.info?.minecraft}.
      {#if app.info?.dev}<span class="badge yellow">Development build</span>{/if}
    </div>
  </section>
</div>

<style>
  section { display: flex; flex-direction: column; gap: 10px; }
  .pad { padding: 4px 18px; }
  .note { padding: 14px 18px; }
</style>
