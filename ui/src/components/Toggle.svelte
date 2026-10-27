<script lang="ts">
  let { checked, label, onchange }: { checked: boolean; label: string; onchange: (value: boolean) => void } = $props()
</script>

<button class="toggle" class:on={checked} role="switch" aria-checked={checked} aria-label={label} onclick={(e) => { e.stopPropagation(); onchange(!checked) }}>
  <span class="knob"></span>
</button>

<style>
  .toggle {
    position: relative; flex: none; width: 46px; height: 25px; border-radius: 999px;
    background: #0d0f2c; border: 1px solid rgba(124, 130, 255, 0.14);
    transition: background var(--ease), border-color var(--ease), box-shadow var(--ease);
  }
  .toggle.on {
    background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 70%, #fff));
    border-color: transparent; box-shadow: 0 4px 16px -4px var(--accent-glow);
  }
  .knob {
    position: absolute; top: 3px; left: 3px; width: 17px; height: 17px; border-radius: 50%;
    background: #fff; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.4); transition: transform 160ms cubic-bezier(0.3, 0.7, 0.4, 1.2);
  }
  .toggle.on .knob { transform: translateX(21px); }
  .toggle:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
</style>
