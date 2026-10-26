<script lang="ts">
  let { checked, label, onchange }: { checked: boolean; label: string; onchange: (value: boolean) => void } = $props()
</script>

<button class="toggle" class:on={checked} role="switch" aria-checked={checked} aria-label={label} onclick={(e) => { e.stopPropagation(); onchange(!checked) }}>
  <span class="knob"></span>
</button>

<style>
  .toggle {
    position: relative; flex: none; width: 38px; height: 22px; border-radius: 999px;
    background: var(--surface-2); border: 1px solid var(--border); transition: background var(--ease), border-color var(--ease);
  }
  .toggle.on { background: var(--accent); border-color: var(--accent); }
  .knob {
    position: absolute; top: 2px; left: 2px; width: 16px; height: 16px; border-radius: 50%;
    background: #fff; transition: transform var(--ease);
  }
  .toggle.on .knob { transform: translateX(16px); }
  .toggle:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
</style>
