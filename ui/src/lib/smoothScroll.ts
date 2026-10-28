/**
 * Makes the mouse wheel glide instead of jumping. Chromium inside the game receives the wheel as a few big steps, so
 * each step becomes a target position that the list eases toward every frame. Svelte action: use:smoothScroll.
 */
export function smoothScroll(node: HTMLElement) {
  let target = node.scrollTop
  let frame = 0

  function step() {
    const distance = target - node.scrollTop
    if (Math.abs(distance) < 0.5) {
      node.scrollTop = target
      frame = 0
      return
    }
    node.scrollTop += distance * 0.2
    frame = requestAnimationFrame(step)
  }

  function onWheel(e: WheelEvent) {
    // Leave sideways scrolls, zoom gestures and anything inside a field that scrolls by itself alone.
    if (e.ctrlKey || Math.abs(e.deltaX) > Math.abs(e.deltaY)) return
    const inner = (e.target as HTMLElement | null)?.closest('textarea, .log')
    if (inner && inner !== node && inner.scrollHeight > inner.clientHeight) return
    const max = node.scrollHeight - node.clientHeight
    if (max <= 0) return
    e.preventDefault()
    if (!frame) target = node.scrollTop // pick up from wherever the list is if something else moved it
    const lines = e.deltaMode === 1 ? 40 : 1
    target = Math.min(max, Math.max(0, target + e.deltaY * lines))
    if (!frame) frame = requestAnimationFrame(step)
  }

  node.addEventListener('wheel', onWheel, { passive: false })
  return {
    destroy() {
      node.removeEventListener('wheel', onWheel)
      if (frame) cancelAnimationFrame(frame)
    },
  }
}
