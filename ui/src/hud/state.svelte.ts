import { on } from '../lib/bridge'

export interface RegionCell { number: number; group?: string }
export interface RegionsPayload {
  grid: RegionCell[][]; cellSize: number; half: number
  /** Average ping in ms for each area you have been in, keyed "column,row". */
  pings: Record<string, number>
  /** Your ping right now. */
  ping?: number
  /** What the cells show: your ping there, or the server's region number. */
  labels?: 'ping' | 'region'
}
export interface FinderEntry {
  id: string; kind: string; x: number; z: number; minY: number; maxY: number
  sections: number; totalSections: number; confidence: number
}
export interface FinderPayload { entries: FinderEntry[] }
export type Tone = 'info' | 'alert' | 'base' | 'farm' | 'player'
export interface Toast { id: number; title: string; message: string; tone: Tone; ms: number; /** Bumped when the same pop-up arrives again, which restarts its countdown. */ nonce: number }
export interface Player { x: number; z: number }
/** What the player is aiming at (a chunk's dot or a stash's outline): the card shown next to the crosshair. */
export interface Inspect {
  title: string; percent: number; direction: string; distance: number
  minY: number; maxY: number; sections: number; totalSections: number
  evidence: { points: number; text: string }[]
  points: number; level: string
  /** What the chunk data actually contained, e.g. "sections below Y 0: 5 of 13". */
  note?: string
}
export interface Config { accent?: string; reducedMotion?: boolean }

/** Everything the game has told the HUD page. Panels show while their data is present and animate out when it goes. */
export const hud = $state({
  regions: null as RegionsPayload | null,
  finder: null as FinderPayload | null,
  player: null as Player | null,
  inspect: null as Inspect | null,
  config: {} as Config,
  toasts: [] as Toast[],
})

const MAX_TOASTS = 4
let nextId = 1

on<{ key: string; value: unknown }>('hud.set', ({ key, value }) => {
  if (key === 'regions') hud.regions = value as RegionsPayload | null
  else if (key === 'finder') hud.finder = value as FinderPayload | null
  else if (key === 'player') hud.player = value as Player | null
  else if (key === 'inspect') hud.inspect = value as Inspect | null
  else if (key === 'config') hud.config = (value ?? {}) as Config
})

const timers = new Map<number, ReturnType<typeof setTimeout>>()

on<{ title: string; message: string; tone?: Tone; ms?: number }>('hud.toast', (t) => {
  const ms = t.ms ?? 4500
  // The same pop-up again (an alert repeating) refreshes the one on screen instead of stacking a copy.
  const same = hud.toasts.find((x) => x.title === t.title && x.message === t.message)
  if (same) {
    same.nonce++
    same.ms = ms
    clearTimeout(timers.get(same.id))
    timers.set(same.id, setTimeout(() => dismiss(same.id), ms))
    return
  }
  const toast: Toast = { id: nextId++, title: t.title, message: t.message, tone: t.tone ?? 'info', ms, nonce: 0 }
  hud.toasts.push(toast)
  while (hud.toasts.length > MAX_TOASTS) dismiss(hud.toasts[0].id)
  timers.set(toast.id, setTimeout(() => dismiss(toast.id), ms))
})

export function dismiss(id: number) {
  clearTimeout(timers.get(id))
  timers.delete(id)
  const i = hud.toasts.findIndex((t) => t.id === id)
  if (i >= 0) hud.toasts.splice(i, 1)
}

// ---- helpers shared by the panels ------------------------------------------------------------

export const GROUP_COLORS: Record<string, string> = {
  'EU Central': '#a0c46c',
  'EU West': '#17a673',
  'NA East': '#62a8d6',
  'NA West': '#3b73b8',
  Asia: '#dcb55a',
  Oceania: '#e08a1e',
}
export const UNKNOWN_COLOR = '#2a2e5c'
export const groupColor = (g?: string) => (g ? GROUP_COLORS[g] ?? UNKNOWN_COLOR : UNKNOWN_COLOR)
export const LEGEND = ['EU Central', 'NA West', 'EU West', 'Asia', 'NA East', 'Oceania']

/** Green for a good connection, through yellow, to red for a bad one. */
export function pingHue(ms: number): number {
  const t = Math.min(1, Math.max(0, (ms - 20) / 180))
  return 140 - 140 * t
}
export const pingColor = (ms: number, light = 52) => `hsl(${pingHue(ms)} 62% ${light}%)`
export const PING_SCALE = [
  { label: 'under 50ms', ms: 30 },
  { label: 'under 100ms', ms: 75 },
  { label: 'under 200ms', ms: 150 },
  { label: '200ms+', ms: 220 },
]

/** Regions you have not visited get a muted colour of their own, so neighbours can be told apart. */
export const regionColor = (number: number) => `hsl(${(number * 47) % 360} 34% 27%)`

/** Same rule as the Kotlin side: north is toward -Z, east toward +X. */
const NAMES = ['E', 'SE', 'S', 'SW', 'W', 'NW', 'N', 'NE']
export function direction(dx: number, dz: number): string {
  if (dx === 0 && dz === 0) return 'here'
  const degrees = (Math.atan2(dz, dx) * 180) / Math.PI
  return NAMES[Math.round(((degrees + 360) % 360) / 45) % 8]
}
export const distance = (dx: number, dz: number) => Math.round(Math.hypot(dx, dz))
export const rangeLabel = (min: number, max: number) => `${Math.trunc(min / 1000)}k..${Math.trunc(max / 1000)}k`

/**
 * The colour of a chance, the same one the Base Finder list and the chunk outlines use: orange-red when it is unlikely,
 * yellow when it is kind of likely, green when it is likely and blue at 99%. (Kotlin has the same stops in ConfidenceColor.)
 */
const STOPS: [number, [number, number, number]][] = [
  [0, [255, 90, 54]],
  [30, [255, 152, 0]],
  [50, [255, 216, 59]],
  [75, [102, 187, 106]],
  [90, [102, 187, 106]],
  [96, [66, 165, 245]],
]
export function confidenceColor(percent: number): string {
  const p = Math.min(100, Math.max(0, percent))
  for (let i = 1; i < STOPS.length; i++) {
    const [p1, c1] = STOPS[i]
    if (p <= p1) {
      const [p0, c0] = STOPS[i - 1]
      const t = (p - p0) / (p1 - p0)
      const mix = (k: number) => Math.round(c0[k] + (c1[k] - c0[k]) * t)
      return `rgb(${mix(0)}, ${mix(1)}, ${mix(2)})`
    }
  }
  return 'rgb(66, 165, 245)'
}
