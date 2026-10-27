<script lang="ts">
  import { app, setSetting } from '../lib/store.svelte'
  import { CLIENT } from '../lib/types'

  const SWATCHES: Record<string, string> = { PURPLE: '#8777ff', BLUE: '#5b8cff', GREEN: '#2fbf7f', ORANGE: '#ff8a3d' }
  const accent = $derived(app.client.find((s) => s.id === 'accent'))
  const label = (s: string) => s.charAt(0) + s.slice(1).toLowerCase()
</script>

<div class="page">
  <header>
    <h1><em>Client</em> Theme</h1>
    <p>Pick the colour used for switches, highlights and the glow on enabled modules.</p>
  </header>

  <section>
    <h2>Accent colour</h2>
    <div class="swatches">
      {#each accent?.options ?? [] as option}
        <button class="card swatch" class:active={accent?.value === option} aria-pressed={accent?.value === option} onclick={() => setSetting(CLIENT, 'accent', option)}>
          <i style="background:{SWATCHES[option] ?? '#8777ff'}; box-shadow: 0 0 18px {SWATCHES[option] ?? '#8777ff'}88"></i>
          <span>{label(option)}</span>
        </button>
      {/each}
    </div>
  </section>
</div>

<style>
  section { display: flex; flex-direction: column; gap: 12px; }
  .swatches { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 12px; }
  .swatch { display: flex; align-items: center; gap: 12px; padding: 14px 16px; transition: border-color var(--ease), background var(--ease); }
  .swatch:hover { border-color: rgba(124, 130, 255, 0.4); }
  .swatch.active { border-color: var(--accent); background: var(--accent-soft); }
  .swatch i { width: 20px; height: 20px; border-radius: 50%; }
  .swatch span { font-weight: 600; }
</style>
