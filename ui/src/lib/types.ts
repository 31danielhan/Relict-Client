export type SettingType = 'bool' | 'int' | 'double' | 'string' | 'enum'

export interface SettingDto {
  id: string
  name: string
  type: SettingType
  description: string
  value: boolean | number | string
  default: boolean | number | string
  min?: number
  max?: number
  maxLength?: number
  options?: string[]
}

export interface ModuleDto {
  id: string
  name: string
  description: string
  tier: 'green' | 'yellow'
  enabled: boolean
  /** Why the module can't work in this game version, if it can't. */
  unavailable?: string
  settings: SettingDto[]
}

export interface Info {
  version: string
  minecraft: string
  mcef: string
  dev: boolean
  uiKey: string
}

/** Module id used for the client's own settings. */
export const CLIENT = 'client'

export type Page = 'overview' | 'map' | 'waypoints' | 'modules' | 'settings' | 'theme' | 'keybinds'

/** Which sidebar group each module is listed under. Modules not named here fall under Other. */
export const CATEGORY_OF: Record<string, string> = {
  donut_regions: 'Donut',
  base_finder: 'Donut',
  live_players: 'Donut',
  base_esp: 'Donut',
  block_esp: 'Donut',
  chunk_esp: 'Donut',
  notifier: 'Donut',
  coverage: 'Search',
  waypoints: 'Search',
}
/** Order of the sidebar groups. */
export const CATEGORY_ORDER = ['Donut', 'Search', 'Other']
export const categoryOf = (id: string) => CATEGORY_OF[id] ?? 'Other'

export interface Waypoint {
  id: string
  name: string
  x: number
  y: number
  z: number
  dimension: string
  tags: string[]
  note: string
  createdAt: number
}

export interface PlayerPos {
  x: number
  y: number
  z: number
  yaw: number
  dimension: string
}

export interface DimStats {
  dimension: string
  chunks: number
  areaKm2: number
}

export interface CoverageView {
  x0: number
  z0: number
  w: number
  h: number
  step: number
  /** base64, one byte per cell: how much of the cell has been searched (0-255) */
  data: string
}

export const TAGS = [
  { id: 'base', label: 'Suspected base', color: '#ff6b6b' },
  { id: 'farm', label: 'Farm', color: '#3ecf8e' },
  { id: 'lava', label: 'Lava', color: '#ff8a3d' },
  { id: 'sign', label: 'Sign', color: '#f5b73d' },
  { id: 'other', label: 'Other', color: '#8b92a7' },
] as const

export const tagColor = (tags: string[]) => TAGS.find((t) => t.id === tags[0])?.color ?? '#8b92a7'

export const DIMENSIONS: Record<string, string> = {
  'minecraft:overworld': 'Overworld',
  'minecraft:the_nether': 'Nether',
  'minecraft:the_end': 'End',
}
export const dimensionName = (id: string) => DIMENSIONS[id] ?? id.replace(/^minecraft:/, '')
