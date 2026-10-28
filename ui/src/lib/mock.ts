// Stand-in for the game, used only when the UI runs in a normal browser (npm run dev).
import type { CoverageView, Info, ModuleDto, SettingDto, Waypoint } from './types'
import { CLIENT } from './types'

const info: Info = { version: '0.1.3-dev', minecraft: '1.21.11', mcef: 'mock', dev: true, uiKey: 'Right Shift' }

const intS = (id: string, name: string, value: number, min: number, max: number, description = ''): SettingDto =>
  ({ id, name, type: 'int', description, value, default: value, min, max })
const boolS = (id: string, name: string, value: boolean, description = ''): SettingDto =>
  ({ id, name, type: 'bool', description, value, default: value })

const modules: ModuleDto[] = [
  {
    id: 'donut_regions', name: 'Donut Regions', tier: 'green', enabled: true,
    description: "Shows the server's region map and which region you are in.",
    settings: [],
  },
  {
    id: 'base_finder', name: 'Base Finder', tier: 'green', enabled: true,
    description: 'Marks loaded chunks with signs of player activity (stashes, machines, dug shafts) and lists the nearest.',
    settings: [intS('radius', 'Scan radius (chunks)', 12, 2, 32, 'How far around you to look.'), intS('minScore', 'Minimum score', 20, 0, 300, 'Chunks scoring less than this are not listed.')],
  },
  {
    id: 'live_players', name: 'Live Player Search', tier: 'green', enabled: false,
    description: 'Lists players the game has loaded near you, and chunks where blocks changed lately (a player may be there).',
    settings: [intS('radius', 'Scan radius (chunks)', 12, 2, 32)],
  },
  {
    id: 'notifier', name: 'Notifier', tier: 'green', enabled: true,
    description: 'Pops up a notification when it finds a stash, farm or base, or when a player may be nearby.',
    settings: [boolS('bases', 'Stashes and bases', true), boolS('farms', 'Farms', true), boolS('players', 'Players', true), intS('minConfidence', 'Minimum chance (%)', 70, 10, 99)],
  },
  {
    id: 'base_esp', name: 'Stash/Farm ESP', tier: 'green', enabled: true,
    description: 'Outlines stashes, machines and farms in white, through walls. Aim at the dot in the middle for the details.',
    settings: [intS('radius', 'Radius (chunks)', 8, 1, 16, 'How far around you to highlight.'), intS('maxY', 'Highlight below Y', 0, -64, 320, 'Only blocks below this height are highlighted.')],
  },
  {
    id: 'block_esp', name: 'Block ESP', tier: 'green', enabled: false,
    description: 'Highlights the blocks you pick (for example chests or spawners), through walls.',
    settings: [{ id: 'blocks', name: 'Blocks', type: 'string', description: 'Comma-separated block ids.', value: 'minecraft:chest, minecraft:ender_chest, minecraft:spawner', default: 'minecraft:chest, minecraft:ender_chest, minecraft:spawner', maxLength: 400 }],
  },
  {
    id: 'chunk_esp', name: 'Chunk ESP', tier: 'green', enabled: false,
    description: 'Outlines the chunks the Base Finder flags, through walls.',
    settings: [intS('radius', 'Scan radius (chunks)', 12, 2, 32), intS('minScore', 'Minimum score', 20, 0, 300)],
  },
  {
    id: 'coverage', name: 'Search Coverage', tier: 'green', enabled: true,
    description: 'Records the ground you have already flown over so you never search the same area twice.',
    settings: [intS('maxRadius', 'Max radius (chunks)', 32, 2, 32, 'Never count chunks further than this from you.'), boolS('trail', 'Show trail', true)],
  },
  {
    id: 'waypoints', name: 'Waypoints', tier: 'green', enabled: true,
    description: 'Save and tag the places you find. Bind a key in Controls to drop one where you stand.',
    settings: [{ id: 'defaultName', name: 'Default name', type: 'string', description: '', value: 'Find', default: 'Find', maxLength: 24 }],
  },
]

const client: SettingDto[] = [
  { id: 'accent', name: 'Accent colour', type: 'enum', description: '', value: 'PURPLE', default: 'PURPLE', options: ['PURPLE', 'BLUE', 'GREEN', 'ORANGE'] },
  { id: 'reducedMotion', name: 'Reduce motion', type: 'bool', description: 'Turns off UI animations.', value: false, default: false },
]

const waypoints: Waypoint[] = [
  { id: 'w1', name: 'Find 1', x: 1200, y: 64, z: -800, dimension: 'minecraft:overworld', tags: ['base'], note: 'Torches on the surface', createdAt: Date.now() - 86400000 },
  { id: 'w2', name: 'Find 2', x: -2300, y: 70, z: 1500, dimension: 'minecraft:overworld', tags: ['farm'], note: '', createdAt: Date.now() - 3600000 },
]

// A fake flight path so the map has something to draw.
function fakeCoverage(p: Record<string, any>): CoverageView {
  const { x0, z0, w, h, step } = p
  const bytes = new Uint8Array(w * h)
  for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) {
    const cx = x0 + i * step, cz = z0 + j * step
    const onPath = Math.abs(cz - (cx * 0.4 - 300)) < 14 && cx > -400 && cx < 900
    if (onPath) bytes[j * w + i] = 255
  }
  return { x0, z0, w, h, step, data: btoa(String.fromCharCode(...bytes)) }
}

const clone = <T,>(v: T): T => JSON.parse(JSON.stringify(v))
const delay = <T,>(v: T) => new Promise<T>((r) => setTimeout(() => r(clone(v)), 30))

function findSetting(moduleId: string, id: string): SettingDto {
  const list = moduleId === CLIENT ? client : modules.find((m) => m.id === moduleId)?.settings
  const s = list?.find((x) => x.id === id)
  if (!s) throw new Error(`Unknown setting ${moduleId}.${id}`)
  return s
}

export function mockInvoke(method: string, p: Record<string, any>): Promise<unknown> {
  try {
    switch (method) {
      case 'app.info': return delay(info)
      case 'modules.list': return delay(modules)
      case 'client.settings': return delay(client)
      case 'coverage.view': return delay(fakeCoverage(p))
      case 'coverage.stats': return delay([{ dimension: 'minecraft:overworld', chunks: 5230, areaKm2: 1.34 }])
      case 'coverage.clear': return delay({})
      case 'waypoints.list': return delay(waypoints)
      case 'waypoints.addHere':
      case 'waypoints.add': {
        const w: Waypoint = { id: crypto.randomUUID(), name: p.name ?? `Find ${waypoints.length + 1}`, x: p.x ?? 0, y: p.y ?? 64, z: p.z ?? 0, dimension: p.dimension ?? 'minecraft:overworld', tags: p.tags ?? ['base'], note: p.note ?? '', createdAt: Date.now() }
        waypoints.push(w)
        return delay(w)
      }
      case 'waypoints.update': {
        const w = waypoints.find((x) => x.id === p.id)
        if (!w) throw new Error('Unknown waypoint')
        Object.assign(w, p)
        return delay(w)
      }
      case 'waypoints.delete': {
        const i = waypoints.findIndex((x) => x.id === p.id)
        if (i >= 0) waypoints.splice(i, 1)
        return delay({})
      }
      case 'modules.setEnabled': {
        const m = modules.find((x) => x.id === p.id)
        if (!m) throw new Error(`Unknown module ${p.id}`)
        m.enabled = !!p.enabled
        return delay(m)
      }
      case 'settings.set': {
        const s = findSetting(p.module, p.id)
        let v = p.value
        if (s.type === 'int' || s.type === 'double') v = Math.min(s.max!, Math.max(s.min!, Number(v)))
        s.value = v
        return delay(s)
      }
      case 'settings.reset': {
        const s = findSetting(p.module, p.id)
        s.value = s.default
        return delay(s)
      }
    }
    throw new Error(`Unknown method ${method}`)
  } catch (e) {
    return Promise.reject(e)
  }
}
