<script lang="ts">
  import { cubicOut } from 'svelte/easing'
  import { flip } from 'svelte/animate'
  import { fade, fly } from 'svelte/transition'
  import { LEGEND, confidenceColor, direction, distance, groupColor, hud, pingColor, rangeLabel } from './state.svelte'

  /** One page, two jobs: the panels across the top, or the notifications in the bottom right corner. */
  let { mode }: { mode: 'panels' | 'toasts' } = $props()

  const COLS = 'ABCDEFGHI'
  const CELL_W = 22
  const CELL_H = 20
  const GAP = 3

  // The region the player stands in, worked out here from their position so the marker follows them every tick.
  const cell = $derived.by(() => {
    const r = hud.regions
    const p = hud.player
    if (!r || !p) return null
    const column = Math.floor((p.x + r.half) / r.cellSize)
    const row = Math.floor((p.z + r.half) / r.cellSize)
    if (column < 0 || row < 0 || column >= r.grid.length || row >= r.grid.length) return null
    const info = r.grid[row][column]
    return {
      column, row, number: info.number, group: info.group, label: `${COLS[column]}${row + 1}`,
      rangeX: rangeLabel(-r.half + column * r.cellSize, -r.half + (column + 1) * r.cellSize),
      rangeZ: rangeLabel(-r.half + row * r.cellSize, -r.half + (row + 1) * r.cellSize),
    }
  })

  const finderRows = $derived.by(() => {
    const f = hud.finder
    const p = hud.player
    if (!f) return { shown: [], more: 0 }
    const rows = f.entries.map((e) => {
      const dx = p ? e.x - p.x : 0
      const dz = p ? e.z - p.z : 0
      return { ...e, dir: direction(dx, dz), dist: distance(dx, dz) }
    })
    rows.sort((a, b) => a.dist - b.dist)
    return { shown: rows.slice(0, 5), more: Math.max(0, rows.length - 5) }
  })

  const accent = $derived(String(hud.config.accent ?? 'PURPLE').toLowerCase())
  const reduced = $derived(hud.config.reducedMotion === true)
  $effect(() => { document.documentElement.dataset.accent = accent })
  $effect(() => { document.documentElement.dataset.motion = reduced ? 'reduced' : 'full' })

  // With reduced motion on, panels and toasts appear without moving.
  const dur = (ms: number) => (reduced ? 0 : ms)
</script>

<div class="layer">
  {#if mode === 'panels' && hud.finder}
    <section class="panel finder" in:fly={{ x: -28, duration: dur(320), easing: cubicOut }} out:fade={{ duration: dur(180) }}>
      <header>
        <h2 class="accent">Base finder</h2>
        <span class="count">{hud.finder.entries.length} nearby</span>
      </header>
      {#each finderRows.shown as e, i (e.id)}
        <div class="item" class:first={i === 0} animate:flip={{ duration: dur(260) }} in:fly={{ x: -16, duration: dur(260) }} out:fade={{ duration: dur(160) }}>
          <div class="line">
            <span class="rank">{i + 1}</span>
            <b>{e.kind}</b>
            <span class="conf" style:color={confidenceColor(e.confidence)}>{e.confidence}%</span>
          </div>
          <div class="sub">{e.dir} {e.dist}m · Y {e.minY}..{e.maxY} · {e.sections}/{e.totalSections} sections</div>
          <div class="meter"><i style:width="{e.confidence}%" style:background={confidenceColor(e.confidence)}></i></div>
        </div>
      {:else}
        <div class="none">Nothing flagged yet</div>
      {/each}
      {#if finderRows.more > 0}<div class="more">+{finderRows.more} more nearby</div>{/if}
    </section>
  {/if}

  {#if mode === 'panels' && hud.regions}
    {@const r = hud.regions}
    <section class="panel regions" in:fly={{ x: 28, duration: dur(320), easing: cubicOut }} out:fade={{ duration: dur(180) }}>
      <header>
        <h2>Donut regions</h2>
        {#if r.labels === 'region'}
          <span class="num">{cell?.number ?? '–'}</span>
        {:else}
          <span class="num" style:color={r.ping ? pingColor(r.ping, 62) : 'var(--muted)'}>{r.ping ?? '–'}<small>ms</small></span>
        {/if}
      </header>

      <div class="map">
        <div class="rowlabels">{#each r.grid as _, i}<span style:height="{CELL_H}px">{i + 1}</span>{/each}</div>
        <div class="grid" style:grid-template-columns="repeat({r.grid.length}, {CELL_W}px)" style:gap="{GAP}px">
          {#each r.grid as row, rowIndex}
            {#each row as c, colIndex}
              {@const ms = r.pings[`${colIndex},${rowIndex}`]}
              {@const here = cell?.column === colIndex && cell?.row === rowIndex}
              {@const shown = here && r.ping ? r.ping : ms}
              <div class="cell" class:here style:background={here ? undefined : groupColor(c.group)} style:height="{CELL_H}px" title="Region {c.number}">
                {#if r.labels === 'region'}{c.number}{:else}{shown ?? '–'}{/if}
              </div>
            {/each}
          {/each}
          <i
            class="cursor"
            class:hidden={!cell}
            style:width="{CELL_W}px"
            style:height="{CELL_H}px"
            style:transform="translate({(cell?.column ?? 0) * (CELL_W + GAP)}px, {(cell?.row ?? 0) * (CELL_H + GAP)}px)"
          ></i>
        </div>
        <div class="collabels" style:grid-template-columns="repeat({r.grid.length}, {CELL_W}px)" style:gap="{GAP}px">
          {#each COLS.slice(0, r.grid.length) as l}<span>{l}</span>{/each}
        </div>
      </div>

      <div class="legend">
        {#each LEGEND as g}<span><i style:background={groupColor(g)}></i>{g}</span>{/each}
      </div>

      {#if hud.player}
        <div class="foot">
          <span class="xz">X: {Math.floor(hud.player.x)}&nbsp; Z: {Math.floor(hud.player.z)}</span>
          {#if cell?.group}<span class="grp" style:color={groupColor(cell.group)}>{cell.group}</span>{/if}
        </div>
        {#if cell}<div class="range">Region {cell.number} · {cell.rangeX} {cell.rangeZ}</div>{/if}
      {/if}
    </section>
  {/if}

  {#if mode === 'panels' && hud.inspect}
    {@const i = hud.inspect}
    <aside class="inspect" in:fly={{ x: 10, duration: dur(160), easing: cubicOut }} out:fade={{ duration: dur(110) }}>
      <header>
        <b style:color={confidenceColor(i.percent)}>{i.title}</b>
        <span class="pct" style:color={confidenceColor(i.percent)} style:background="color-mix(in srgb, {confidenceColor(i.percent)} 16%, transparent)">{i.percent}%</span>
      </header>
      <div class="where"><span>{i.direction} {i.distance}m</span><span>Y {i.minY}..{i.maxY} · {i.sections}/{i.totalSections} sections</span></div>
      <ul>
        {#each i.evidence as e}<li><em>+{e.points}</em>{e.text}</li>{/each}
      </ul>
      {#if i.note}<div class="note">{i.note}</div>{/if}
      <footer>{i.points} points · confidence {i.level}</footer>
    </aside>
  {/if}

  {#if mode === 'toasts'}
    <div class="toasts">
      {#each hud.toasts as t (t.id)}
        <div class="toast {t.tone}" animate:flip={{ duration: dur(260) }} in:fly={{ x: 46, duration: dur(340), easing: cubicOut }} out:fly={{ x: 40, duration: dur(240) }}>
          <span class="icon">
            {#if t.tone === 'alert' || t.tone === 'player'}
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l10 18H2zM12 10v5M12 18h.01" /></svg>
            {:else if t.tone === 'farm'}
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21V9M12 9c0-3 2-5 6-5 0 4-2 6-6 5zM12 13c0-2.5-1.8-4-5-4 0 3.4 1.8 5 5 4z" /></svg>
            {:else}
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2l9 5v10l-9 5-9-5V7zM12 12l9-5M12 12v10M12 12L3 7" /></svg>
            {/if}
          </span>
          <div class="body"><b>{t.title}</b><span>{t.message}</span></div>
          {#key t.nonce}<i class="life" style:animation-duration="{t.ms}ms"></i>{/key}
        </div>
      {/each}
    </div>
  {/if}
</div>

<style>
  /* Scoped on purpose: component styles end up in one stylesheet shared with the GUI window, which must stay clickable. */
  .layer { position: fixed; inset: 0; overflow: hidden; pointer-events: none; user-select: none; }

  .panel {
    position: absolute; top: 10px; padding: 12px 13px 11px; border-radius: 20px;
    /* A little clear tint: you can still see the world through it. */
    background: linear-gradient(180deg, rgba(16, 19, 66, 0.58), rgba(7, 9, 36, 0.62));
    border: 1px solid rgba(124, 130, 255, 0.2); border-top-color: rgba(150, 156, 255, 0.34);
    box-shadow: 0 14px 34px -12px rgba(0, 0, 0, 0.5);
    will-change: transform, opacity;
  }
  .panel header { display: flex; align-items: baseline; justify-content: space-between; gap: 14px; margin-bottom: 8px; }
  .panel h2 { font-size: 9.5px; letter-spacing: 0.14em; }
  .panel h2.accent { color: var(--accent-text); }
  .count { font-size: 10px; color: var(--muted); }

  /* region map */
  .regions { right: 10px; }
  .num { font-family: var(--serif); font-size: 22px; line-height: 1; transition: color 300ms ease; }
  .num small { margin-left: 2px; font-family: var(--sans); font-size: 9px; font-weight: 600; opacity: 0.75; }
  .map { display: grid; grid-template-columns: 10px auto; grid-template-rows: auto auto; column-gap: 5px; row-gap: 3px; }
  .rowlabels { display: flex; flex-direction: column; gap: 3px; font-size: 8.5px; color: var(--muted); }
  .rowlabels span { display: grid; place-items: center; }
  .grid { position: relative; display: grid; }
  .cell {
    display: grid; place-items: center; border-radius: 7px; font-size: 9px; font-weight: 700; color: rgba(255, 255, 255, 0.96);
    text-shadow: 0 1px 1px rgba(0, 0, 0, 0.35); transition: background 400ms ease, color 300ms ease, box-shadow 300ms ease;
  }
  /* The area you are in turns bright, whatever its own colour was. */
  .cell.here { background: #e8f4ff; color: #0b0e34; text-shadow: none; box-shadow: 0 0 14px 1px rgba(160, 215, 255, 0.55); }
  .cursor {
    position: absolute; left: 0; top: 0; border-radius: 7px; box-sizing: border-box; pointer-events: none;
    border: 2px solid #fff; box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.35), 0 0 12px 2px rgba(255, 255, 255, 0.55);
    transition: transform 280ms cubic-bezier(0.2, 0.9, 0.25, 1), opacity 180ms ease;
    animation: pulse 2.2s ease-in-out infinite;
  }
  .cursor.hidden { opacity: 0; }
  @keyframes pulse { 50% { box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.35), 0 0 6px 1px rgba(255, 255, 255, 0.3); } }
  .collabels { grid-column: 2; display: grid; font-size: 8.5px; color: var(--muted); text-align: center; }
  .legend { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 10px; margin: 10px 0 8px; padding-top: 9px; border-top: 1px solid rgba(124, 130, 255, 0.12); font-size: 9.5px; color: var(--muted); }
  .legend span { display: flex; align-items: center; gap: 5px; }
  .legend i { width: 7px; height: 7px; border-radius: 50%; }
  .foot { display: flex; justify-content: space-between; gap: 10px; padding-top: 8px; border-top: 1px solid rgba(124, 130, 255, 0.12); font-size: 10.5px; font-weight: 600; }
  .grp { font-weight: 700; }
  .grp.muted { color: var(--muted); font-weight: 600; }
  .range { margin-top: 2px; font-size: 9px; color: var(--muted); }

  /* base finder */
  .finder { left: 10px; width: 196px; }
  .item { padding: 6px 0 5px; border-top: 1px solid rgba(124, 130, 255, 0.1); }
  .item:first-of-type { border-top: 0; padding-top: 0; }
  .line { display: flex; align-items: baseline; gap: 6px; font-size: 11.5px; }
  .rank { font-size: 9px; color: var(--faint); width: 8px; }
  .line b { flex: 1; font-weight: 700; }
  .conf { font-size: 10px; font-weight: 700; color: var(--accent-text); }
  .sub { margin: 1px 0 4px 14px; font-size: 8.5px; color: var(--muted); }
  .meter { height: 3px; margin-left: 14px; border-radius: 3px; background: rgba(255, 255, 255, 0.07); overflow: hidden; }
  .meter i { display: block; height: 100%; border-radius: 2px; background: var(--accent); box-shadow: 0 0 8px var(--accent-glow); transition: width 400ms ease; }
  .none, .more { font-size: 9.5px; color: var(--muted); padding-top: 2px; }

  .item.first { position: relative; }
  .item.first::before { content: ''; position: absolute; left: -13px; top: 2px; bottom: 4px; width: 2px; border-radius: 2px; background: var(--accent); box-shadow: 0 0 8px var(--accent-glow); }
  .finder .conf { font-variant-numeric: tabular-nums; }

  /* the card for what the crosshair is on */
  .inspect {
    position: absolute; left: calc(50% + 30px); top: calc(50% - 46px); width: 270px; padding: 12px 14px 10px; border-radius: 18px;
    background: linear-gradient(180deg, rgba(16, 19, 66, 0.82), rgba(7, 9, 36, 0.86));
    border: 1px solid rgba(124, 130, 255, 0.24); border-top-color: rgba(150, 156, 255, 0.38);
    box-shadow: 0 14px 34px -10px rgba(0, 0, 0, 0.6);
  }
  .inspect header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
  .inspect header b { font-size: 13px; font-weight: 700; }
  .inspect .pct { padding: 2px 9px; border-radius: 999px; font-size: 11px; font-weight: 700; }
  .where { display: flex; justify-content: space-between; gap: 8px; font-size: 10px; color: var(--muted); margin-bottom: 7px; }
  .inspect ul { list-style: none; margin: 0 0 7px; padding: 0; display: flex; flex-direction: column; gap: 3px; }
  .inspect li { font-size: 10.5px; color: #c9cbe6; padding-left: 12px; position: relative; }
  .inspect li::before { content: ''; position: absolute; left: 2px; top: 6px; width: 3px; height: 3px; border-radius: 50%; background: var(--accent); }
  .inspect li em { font-style: normal; font-weight: 700; color: var(--accent-text); margin-right: 5px; }
  .inspect .note { font-size: 9.5px; color: var(--muted); margin-bottom: 6px; }
  .inspect footer { padding-top: 7px; border-top: 1px solid rgba(124, 130, 255, 0.12); font-size: 9.5px; color: var(--muted); }

  /* notifications, bottom right, stacking upwards */
  .toasts { position: absolute; right: 12px; bottom: 12px; display: flex; flex-direction: column-reverse; align-items: flex-end; gap: 8px; width: 320px; }
  .toast {
    position: relative; display: flex; align-items: center; gap: 11px; width: 100%; padding: 11px 15px 13px 12px; border-radius: 18px; overflow: hidden;
    background: linear-gradient(180deg, rgba(16, 19, 68, 0.9), rgba(7, 9, 38, 0.92));
    border: 1px solid rgba(124, 130, 255, 0.24); border-top-color: rgba(150, 156, 255, 0.38);
    box-shadow: 0 14px 34px -10px rgba(0, 0, 0, 0.7);
    --tone: var(--accent);
  }
  .toast.alert { --tone: #f5b73d; }
  .toast.player { --tone: #b06cff; }
  .toast.base { --tone: #42a5f5; }
  .toast.farm { --tone: #66bb6a; }
  .icon { flex: none; display: grid; place-items: center; width: 30px; height: 30px; border-radius: 10px; color: var(--tone); background: color-mix(in srgb, var(--tone) 16%, transparent); }
  .body { display: flex; flex-direction: column; min-width: 0; }
  .body b { font-size: 12.5px; font-weight: 700; }
  .body span { font-size: 11px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .life { position: absolute; left: 0; bottom: 0; height: 2px; width: 100%; transform-origin: left; background: var(--tone); opacity: 0.85; animation: life linear forwards; }
  @keyframes life { from { transform: scaleX(1); } to { transform: scaleX(0); } }
</style>
