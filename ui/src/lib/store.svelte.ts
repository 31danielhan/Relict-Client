import { invoke, on } from './bridge'
import type { DimStats, Info, ModuleDto, Page, PlayerPos, SettingDto, Waypoint } from './types'
import { CLIENT } from './types'

export const app = $state({
  ready: false,
  error: '',
  info: null as Info | null,
  modules: [] as ModuleDto[],
  client: [] as SettingDto[],
})

function fail(e: unknown) {
  app.error = e instanceof Error ? e.message : String(e)
}

export async function load() {
  try {
    const [info, modules, client] = await Promise.all([
      invoke<Info>('app.info'),
      invoke<ModuleDto[]>('modules.list'),
      invoke<SettingDto[]>('client.settings'),
    ])
    app.info = info
    app.modules = modules
    app.client = client
    app.error = ''
    app.ready = true
  } catch (e) {
    fail(e)
  }
}

function replaceModule(m: ModuleDto) {
  const i = app.modules.findIndex((x) => x.id === m.id)
  if (i >= 0) app.modules[i] = m
}

function replaceSetting(moduleId: string, s: SettingDto) {
  const list = moduleId === CLIENT ? app.client : app.modules.find((m) => m.id === moduleId)?.settings
  const i = list?.findIndex((x) => x.id === s.id) ?? -1
  if (list && i >= 0) list[i] = s
}

export async function setEnabled(id: string, enabled: boolean) {
  try {
    replaceModule(await invoke<ModuleDto>('modules.setEnabled', { id, enabled }))
  } catch (e) {
    fail(e)
  }
}

export async function setSetting(module: string, id: string, value: unknown) {
  try {
    replaceSetting(module, await invoke<SettingDto>('settings.set', { module, id, value }))
  } catch (e) {
    fail(e)
    await load() // resync with the game's actual values
  }
}

export async function resetSetting(module: string, id: string) {
  try {
    replaceSetting(module, await invoke<SettingDto>('settings.reset', { module, id }))
  } catch (e) {
    fail(e)
  }
}

// Changes made in the game (e.g. a keybind toggling a module) arrive as events.
on<ModuleDto>('module.changed', replaceModule)


// ---- navigation ----------------------------------------------------------------------------

export const nav = $state({
  page: 'overview' as Page,
  /** Which group the Modules page is showing (Donut, Search...). */
  category: 'Donut',
  /** Set to ask the map to centre on a spot (e.g. "Show on map" from a waypoint). */
  focus: null as { x: number; z: number; dimension: string } | null,
})

export function showOnMap(w: { x: number; z: number; dimension: string }) {
  nav.focus = { x: w.x, z: w.z, dimension: w.dimension }
  nav.page = 'map'
}

// ---- world data: player, waypoints, coverage -------------------------------------------------

export const world = $state({
  player: null as PlayerPos | null,
  waypoints: [] as Waypoint[],
  stats: [] as DimStats[],
  /** Why waypoint data is unavailable right now (module off, not in a world), shown inline instead of as an error. */
  waypointsNote: '',
})

export const isEnabled = (id: string) => app.modules.find((m) => m.id === id)?.enabled === true

export async function loadWaypoints() {
  if (!isEnabled('waypoints')) {
    world.waypoints = []
    world.waypointsNote = 'Turn on the Waypoints module to use this.'
    return
  }
  try {
    world.waypoints = await invoke<Waypoint[]>('waypoints.list')
    world.waypointsNote = ''
  } catch (e) {
    world.waypoints = []
    world.waypointsNote = e instanceof Error ? e.message : String(e)
  }
}

async function waypointCall<T>(method: string, params: Record<string, unknown> = {}): Promise<T | undefined> {
  try {
    return await invoke<T>(method, params)
  } catch (e) {
    fail(e)
  }
}

export const addWaypointHere = (name?: string) => waypointCall<Waypoint>('waypoints.addHere', name ? { name } : {})
export const addWaypoint = (w: Partial<Waypoint>) => waypointCall<Waypoint>('waypoints.add', w)
export const updateWaypoint = (id: string, patch: Partial<Waypoint>) => waypointCall<Waypoint>('waypoints.update', { id, ...patch })
export const deleteWaypoint = (id: string) => waypointCall('waypoints.delete', { id })

export async function loadStats() {
  if (!isEnabled('coverage')) {
    world.stats = []
    return
  }
  try {
    world.stats = await invoke<DimStats[]>('coverage.stats')
  } catch {
    world.stats = []
  }
}

export async function clearCoverage(dimension: string) {
  try {
    await invoke('coverage.clear', { dimension })
    await loadStats()
  } catch (e) {
    fail(e)
  }
}

on<PlayerPos>('player.position', (p) => { world.player = p })
on<Waypoint[]>('waypoints.changed', (list) => { world.waypoints = list; world.waypointsNote = '' })
