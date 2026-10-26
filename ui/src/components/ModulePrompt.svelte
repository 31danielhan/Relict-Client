<script lang="ts">
  import { app, setEnabled } from '../lib/store.svelte'

  let { id, what }: { id: string; what: string } = $props()
  const module = $derived(app.modules.find((m) => m.id === id))
</script>

<div class="prompt card">
  <div class="title">{module?.name ?? id} is off</div>
  <p class="muted">{what}</p>
  <button class="primary" onclick={() => setEnabled(id, true)}>Turn on</button>
</div>

<style>
  .prompt { display: flex; flex-direction: column; align-items: flex-start; gap: 8px; padding: 18px 20px; max-width: 420px; }
  .title { font-weight: 600; font-size: 15px; }
  .primary { margin-top: 4px; padding: 7px 16px; border-radius: 8px; background: var(--accent); color: #fff; font-weight: 600; }
  .primary:hover { filter: brightness(1.1); }
</style>
