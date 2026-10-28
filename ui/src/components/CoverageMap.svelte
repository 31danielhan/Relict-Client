<script lang="ts">
  import { onMount } from 'svelte'
  import { invoke } from '../lib/bridge'
  import { nav, ui, world } from '../lib/store.svelte'
  import { tagColor, type CoverageView, type Waypoint } from '../lib/types'

  let { dimension, selectedId = null, onselect }: { dimension: string; selectedId?: string | null; onselect: (id: string | null) => void } = $props()

  let canvas: HTMLCanvasElement
  let wrap: HTMLDivElement
  let width = $state(600)
  let height = $state(400)

  // View: centre in chunk coordinates and zoom in pixels per chunk.
  let cx = $state(0)
  let cz = $state(0)
  let zoom = $state(4)
  let follow = $state(true)

  let view: CoverageView | null = null
  let bitmap: HTMLCanvasElement | null = null
  let requestId = 0
  let hover = $state<Waypoint | null>(null)

  const MIN_ZOOM = 0.15
  const MAX_ZOOM = 24
  const accent = () => getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#8777ff'

  const stepFor = (z: number) => {
    let step = 1
    while (step * z < 3 && step < 256) step *= 2
    return step
  }

  async function fetchView() {
    const id = ++requestId
    const step = stepFor(zoom)
    const left = cx - width / 2 / zoom
    const top = cz - height / 2 / zoom
    const x0 = Math.floor(left / step) * step
    const z0 = Math.floor(top / step) * step
    const w = Math.min(512, Math.ceil((cx + width / 2 / zoom - x0) / step) + 1)
    const h = Math.min(512, Math.ceil((cz + height / 2 / zoom - z0) / step) + 1)
    try {
      const v = await invoke<CoverageView>('coverage.view', { dimension, x0, z0, w, h, step })
      if (id !== requestId) return // a newer request superseded this one
      view = v
      bitmap = toBitmap(v)
      draw()
    } catch {
      /* module off or not in a world: the page shows a prompt instead */
    }
  }

  function toBitmap(v: CoverageView) {
    const bytes = Uint8Array.from(atob(v.data), (c) => c.charCodeAt(0))
    const off = document.createElement('canvas')
    off.width = v.w
    off.height = v.h
    const ctx = off.getContext('2d')!
    const img = ctx.createImageData(v.w, v.h)
    const color = accent()
    const r = parseInt(color.slice(1, 3), 16), g = parseInt(color.slice(3, 5), 16), b = parseInt(color.slice(5, 7), 16)
    for (let i = 0; i < bytes.length; i++) {
      img.data[i * 4] = r
      img.data[i * 4 + 1] = g
      img.data[i * 4 + 2] = b
      img.data[i * 4 + 3] = bytes[i] === 0 ? 0 : 70 + Math.round((bytes[i] / 255) * 150)
    }
    ctx.putImageData(img, 0, 0)
    return off
  }

  const toScreen = (chunkX: number, chunkZ: number) => [(chunkX - cx) * zoom + width / 2, (chunkZ - cz) * zoom + height / 2] as const

  function draw() {
    if (!canvas) return
    const dpr = 1 // the page is already zoomed to the GUI scale
    canvas.width = width * dpr
    canvas.height = height * dpr
    const ctx = canvas.getContext('2d')!
    ctx.fillStyle = '#05061c'
    ctx.fillRect(0, 0, width, height)

    // grid every 16 chunks (256 blocks), stronger every 64
    if (zoom >= 0.6) {
      const first = Math.floor((cx - width / 2 / zoom) / 16) * 16
      for (let gx = first; gx < cx + width / 2 / zoom; gx += 16) {
        const [sx] = toScreen(gx, 0)
        ctx.fillStyle = gx % 64 === 0 ? 'rgba(255,255,255,0.09)' : 'rgba(255,255,255,0.04)'
        ctx.fillRect(Math.round(sx), 0, 1, height)
      }
      const firstZ = Math.floor((cz - height / 2 / zoom) / 16) * 16
      for (let gz = firstZ; gz < cz + height / 2 / zoom; gz += 16) {
        const [, sy] = toScreen(0, gz)
        ctx.fillStyle = gz % 64 === 0 ? 'rgba(255,255,255,0.09)' : 'rgba(255,255,255,0.04)'
        ctx.fillRect(0, Math.round(sy), width, 1)
      }
    }

    if (view && bitmap) {
      const [sx, sy] = toScreen(view.x0, view.z0)
      ctx.imageSmoothingEnabled = false
      ctx.drawImage(bitmap, sx, sy, view.w * view.step * zoom, view.h * view.step * zoom)
    }

    // world origin
    const [ox, oy] = toScreen(0, 0)
    ctx.strokeStyle = 'rgba(255,255,255,0.35)'
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(ox - 7, oy); ctx.lineTo(ox + 7, oy); ctx.moveTo(ox, oy - 7); ctx.lineTo(ox, oy + 7)
    ctx.stroke()

    // waypoints
    for (const w of world.waypoints) {
      if (w.dimension !== dimension) continue
      const [px, py] = toScreen(w.x / 16, w.z / 16)
      if (px < -10 || py < -10 || px > width + 10 || py > height + 10) continue
      const selected = w.id === selectedId
      ctx.beginPath()
      ctx.arc(px, py, selected ? 7 : 5, 0, Math.PI * 2)
      ctx.fillStyle = tagColor(w.tags)
      ctx.fill()
      ctx.lineWidth = selected ? 2.5 : 1.5
      ctx.strokeStyle = selected ? '#fff' : 'rgba(0,0,0,0.6)'
      ctx.stroke()
    }

    // player
    const p = world.player
    if (p && p.dimension === dimension) {
      const [px, py] = toScreen(p.x / 16, p.z / 16)
      ctx.save()
      ctx.translate(px, py)
      ctx.rotate(((p.yaw + 180) * Math.PI) / 180) // yaw 180 faces north, which is up on the map
      ctx.beginPath()
      ctx.moveTo(0, -9); ctx.lineTo(6, 7); ctx.lineTo(0, 3); ctx.lineTo(-6, 7); ctx.closePath()
      ctx.fillStyle = '#fff'
      ctx.fill()
      ctx.strokeStyle = 'rgba(0,0,0,0.7)'
      ctx.lineWidth = 1.5
      ctx.stroke()
      ctx.restore()
    }

    // scale bar
    const blocks = [16, 64, 256, 1024, 4096, 16384, 65536].find((b) => (b / 16) * zoom >= 60) ?? 65536
    const len = (blocks / 16) * zoom
    ctx.fillStyle = 'rgba(255,255,255,0.8)'
    ctx.fillRect(14, height - 18, len, 2)
    ctx.fillRect(14, height - 22, 1, 10)
    ctx.fillRect(14 + len, height - 22, 1, 10)
    ctx.font = '11px sans-serif'
    ctx.fillText(`${blocks.toLocaleString()} blocks`, 14, height - 28)
  }

  // ---- interaction ----
  let dragging = false
  let moved = false
  let last = [0, 0]

  const pos = (e: MouseEvent) => {
    const r = canvas.getBoundingClientRect()
    return [e.clientX - r.left, e.clientY - r.top] as const
  }

  function waypointAt(sx: number, sy: number) {
    let best: Waypoint | null = null
    let bestD = 100
    for (const w of world.waypoints) {
      if (w.dimension !== dimension) continue
      const [px, py] = toScreen(w.x / 16, w.z / 16)
      const d = (px - sx) ** 2 + (py - sy) ** 2
      if (d < bestD) { best = w; bestD = d }
    }
    return best
  }

  function onDown(e: MouseEvent) {
    dragging = true
    moved = false
    last = [e.clientX, e.clientY]
  }

  function onMove(e: MouseEvent) {
    const [sx, sy] = pos(e)
    if (dragging) {
      const dx = e.clientX - last[0], dy = e.clientY - last[1]
      if (Math.abs(dx) + Math.abs(dy) > 0) moved = true
      cx -= dx / zoom
      cz -= dy / zoom
      last = [e.clientX, e.clientY]
      if (moved) follow = false
      draw()
      schedule()
    } else {
      const w = waypointAt(sx, sy)
      if (w?.id !== hover?.id) { hover = w; canvas.style.cursor = w ? 'pointer' : 'grab' }
    }
  }

  function onUp(e: MouseEvent) {
    if (dragging && !moved) {
      const [sx, sy] = pos(e)
      onselect(waypointAt(sx, sy)?.id ?? null)
    }
    dragging = false
  }

  function onWheel(e: WheelEvent) {
    e.preventDefault()
    const [sx, sy] = pos(e)
    const before = [cx + (sx - width / 2) / zoom, cz + (sy - height / 2) / zoom]
    zoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom * (e.deltaY < 0 ? 1.25 : 0.8)))
    // keep the point under the cursor fixed
    cx = before[0] - (sx - width / 2) / zoom
    cz = before[1] - (sy - height / 2) / zoom
    follow = false
    draw()
    schedule()
  }

  let timer: ReturnType<typeof setTimeout> | undefined
  function schedule() {
    clearTimeout(timer)
    timer = setTimeout(fetchView, 90)
  }

  export function centerOn(x: number, z: number) {
    cx = x / 16
    cz = z / 16
    follow = false
    schedule()
    draw()
  }

  export function followPlayer() {
    follow = true
    const p = world.player
    if (p && p.dimension === dimension) { cx = p.x / 16; cz = p.z / 16 }
    schedule()
    draw()
  }

  export const isFollowing = () => follow

  // Follow the player as they move.
  $effect(() => {
    const p = world.player
    if (follow && p && p.dimension === dimension) {
      cx = p.x / 16
      cz = p.z / 16
    }
    draw()
  })

  // Waypoint changes and the colour theme need a redraw; a new dimension needs new data.
  $effect(() => { world.waypoints; selectedId; draw() })
  $effect(() => { dimension; view = null; bitmap = null; schedule() })

  // A "show on map" request from the Waypoints page.
  $effect(() => {
    const f = nav.focus
    if (f) {
      if (f.dimension === dimension) centerOn(f.x, f.z)
      nav.focus = null
    }
  })

  onMount(() => {
    const ro = new ResizeObserver(() => {
      width = Math.max(100, Math.floor(wrap.clientWidth))
      height = Math.max(100, Math.floor(wrap.clientHeight))
      draw()
      schedule()
    })
    ro.observe(wrap)
    followPlayer()
    const refresh = setInterval(() => { if (ui.open) fetchView() }, 1500) // coverage grows while you fly
    return () => { ro.disconnect(); clearInterval(refresh); clearTimeout(timer) }
  })
</script>

<div class="wrap" bind:this={wrap}>
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <canvas
    bind:this={canvas}
    style="cursor: grab"
    onmousedown={onDown}
    onmousemove={onMove}
    onmouseup={onUp}
    onmouseleave={() => { dragging = false; hover = null }}
    onwheel={onWheel}
  ></canvas>
  {#if hover}
    <div class="tip">{hover.name}<span class="muted"> · {hover.x}, {hover.y}, {hover.z}</span></div>
  {/if}
  <div class="controls">
    <button onclick={() => { zoom = Math.min(MAX_ZOOM, zoom * 1.5); draw(); schedule() }} aria-label="Zoom in">+</button>
    <button onclick={() => { zoom = Math.max(MIN_ZOOM, zoom / 1.5); draw(); schedule() }} aria-label="Zoom out">−</button>
    <button class:active={follow} onclick={followPlayer} aria-label="Follow me" title="Follow me">◎</button>
  </div>
</div>

<style>
  .wrap { position: relative; width: 100%; height: 100%; min-height: 0; overflow: hidden; border-radius: var(--radius); border: 1px solid var(--border); background: #05061c; }
  canvas { display: block; width: 100%; height: 100%; }
  .tip {
    position: absolute; left: 12px; top: 12px; padding: 5px 10px; border-radius: 7px; pointer-events: none;
    background: rgba(8, 10, 40, 0.92); border: 1px solid var(--border); font-size: 12px;
  }
  .controls { position: absolute; right: 12px; top: 12px; display: flex; flex-direction: column; gap: 6px; }
  .controls button {
    width: 32px; height: 32px; border-radius: 8px; font-size: 16px; line-height: 1;
    background: rgba(8, 10, 40, 0.92); border: 1px solid var(--border); color: var(--text);
  }
  .controls button:hover { background: var(--surface-2); }
  .controls button.active { color: var(--accent); border-color: var(--accent); }
</style>
