// Stand-in for the game, used only when the UI runs in a normal browser (npm run dev).
import type { CoverageView, Info, ModuleDto, SettingDto, Waypoint } from './types'
import { CLIENT } from './types'

const info: Info = { version: '0.1.0-dev', minecraft: '1.21.11', mcef: 'mock', dev: true, uiKey: 'Right Shift' }

const modules: ModuleDto[] = [
  {
    id: 'waypoints', name: 'Waypoints', tier: 'green', enabled: true,
    description: 'Save and tag the places you find.',
    settings: [{ id: 'defaultName', name: 'Default name', type: 'string', description: '', value: 'Find', default: 'Find', maxLength: 24 }],
  },
  {
    id: 'coverage', name: 'Search Coverage', tier: 'green', enabled: true,
    description: 'Records the areas you have already searched.',
    settings: [
      { id: 'radius', name: 'Search radius (chunks)', type: 'int', description: 'How far around you counts as searched.', value: 8, default: 8, min: 1, max: 32 },
      { id: 'trail', name: 'Show trail', type: 'bool', description: '', value: true, default: true },
    ],
  },
  {
    id: 'anomaly', name: 'Visible Anomaly Marker', tier: 'yellow', enabled: false,
    description: 'Suggests logging unusual blocks you can see. Needs staff approval before use.',
    settings: [{ id: 'mode', name: 'Mode', type: 'enum', description: '', value: 'SUGGEST', default: 'SUGGEST', options: ['SUGGEST', 'AUTO'] }],
  },
]

const client: SettingDto[] = [
  { id: 'accent', name: 'Accent colour', type: 'enum', description: '', value: 'BLUE', default: 'BLUE', options: ['BLUE', 'GREEN', 'PURPLE', 'ORANGE'] },
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
