// The page talks to the installer's Kotlin side through window.backend (set by the JavaFX window).
// In a normal browser (npm run dev:installer) a small stand-in answers instead, so the layout can be worked on.

export interface Target {
  key: string
  kind: string
  launcher: string
  name: string
  label: string
  dir: string
  minecraft: string | null
  loader: string | null
  loaderVersion: string | null
  instance: boolean
  detail: string
  suggestedVersion?: string | null
  state: 'installed' | 'update' | 'none' | 'unsupported'
}

export interface Snapshot {
  installerVersion: string
  relictVersion: string
  autoUpdate: boolean
  updatesConfigured: boolean
  versions: string[]
  targets: Target[]
  selected?: string
}

export interface Status {
  note: string
  problem: boolean
  installed: boolean
  installedVersion: string | null
  needsUpdate: boolean
  blocked: boolean
  minecraft: string | null
  pickVersion: boolean
  pickLayout: boolean
}

export interface Banner { visible: boolean; title?: string; detail?: string; updateAction?: boolean; installerAction?: boolean }

declare global {
  interface Window {
    backend?: { invoke: (id: number, method: string, params: string) => void }
    relictBridge: { resolve: (id: number, reply: string) => void; emit: (event: string, payload: unknown) => void }
  }
}

const pending = new Map<number, { ok: (v: any) => void; fail: (e: Error) => void }>()
const listeners = new Map<string, Set<(payload: any) => void>>()
let nextId = 1

window.relictBridge = {
  resolve(id, reply) {
    const p = pending.get(id)
    pending.delete(id)
    if (!p) return
    const parsed = JSON.parse(reply)
    if ('error' in parsed) p.fail(new Error(parsed.error))
    else p.ok(parsed.ok)
  },
  emit(event, payload) {
    listeners.get(event)?.forEach((fn) => fn(payload))
  },
}

export function on<T>(event: string, fn: (payload: T) => void): () => void {
  if (!listeners.has(event)) listeners.set(event, new Set())
  listeners.get(event)!.add(fn)
  return () => listeners.get(event)?.delete(fn)
}

export function call<T>(method: string, params: Record<string, unknown> = {}): Promise<T> {
  if (!window.backend) return mockCall(method, params) as Promise<T>
  return new Promise<T>((ok, fail) => {
    const id = nextId++
    pending.set(id, { ok, fail })
    window.backend!.invoke(id, method, JSON.stringify(params))
  })
}

// ---- stand-in used outside the installer ---------------------------------------------------

const fire = (event: string, payload: unknown) => setTimeout(() => window.relictBridge.emit(event, payload), 0)
const wait = (ms: number) => new Promise((r) => setTimeout(r, ms))

const mockTargets: Target[] = [
  { key: 'VANILLA|/Users/me/Library/Application Support/minecraft|Minecraft Launcher', kind: 'VANILLA', launcher: 'Minecraft Launcher', name: 'Minecraft Launcher', label: 'Minecraft Launcher', dir: '~/Library/Application Support/minecraft', minecraft: null, loader: null, loaderVersion: null, instance: false, detail: '', suggestedVersion: '1.21.11', state: 'installed' },
  { key: 'PRISM|prism-1|1.21.10', kind: 'PRISM', launcher: 'Prism Launcher', name: '1.21.10', label: 'Prism Launcher · 1.21.10', dir: '~/Library/Application Support/PrismLauncher/instances/1.21.10/minecraft', minecraft: '1.21.10', loader: 'fabric', loaderVersion: '0.18.4', instance: true, detail: 'Minecraft 1.21.10 · Fabric 0.18.4', state: 'none' },
  { key: 'MODRINTH|mr-1|26.1', kind: 'MODRINTH', launcher: 'Modrinth App', name: 'Base hunting 26.1', label: 'Modrinth App · Base hunting 26.1', dir: '~/Library/Application Support/ModrinthApp/profiles/Base hunting 26.1', minecraft: '26.1.2', loader: 'fabric', loaderVersion: '0.19.1', instance: true, detail: 'Minecraft 26.1.2 · Fabric 0.19.1', state: 'update' },
  { key: 'PRISM|prism-2|1.20.1', kind: 'PRISM', launcher: 'Prism Launcher', name: 'Forge 1.20.1', label: 'Prism Launcher · Forge 1.20.1', dir: '~/Library/Application Support/PrismLauncher/instances/Forge 1.20.1/minecraft', minecraft: '1.20.1', loader: 'forge', loaderVersion: '47.2', instance: true, detail: 'Minecraft 1.20.1 · Forge 47.2', state: 'unsupported' },
]

async function mockCall(method: string, p: Record<string, any>): Promise<unknown> {
  await wait(40)
  switch (method) {
    case 'init':
    case 'rescan':
    case 'browse':
      return { installerVersion: '0.1.3', relictVersion: '0.1.3', autoUpdate: true, updatesConfigured: true, versions: ['26.3', '26.2', '26.1.2', '1.21.11', '1.21.10', '1.21.4'], targets: mockTargets, selected: p.selected ?? mockTargets[0].key }
    case 'status': {
      const t = mockTargets.find((x) => x.key === p.target)!
      const unsupported = t.state === 'unsupported'
      return {
        note: unsupported ? "Relict doesn't support Minecraft 1.20.1. It runs on 26.3, 26.2, 26.1.2, 1.21.11, 1.21.10, 1.21.4." : t.instance ? "Ready: Relict goes into this instance's mods folder." : 'Found the Minecraft launcher data.',
        problem: unsupported, installed: t.state === 'installed' || t.state === 'update', installedVersion: t.state === 'none' ? null : '0.1.0',
        needsUpdate: t.state === 'update', blocked: unsupported, minecraft: t.minecraft ?? p.version ?? '1.21.11', pickVersion: t.minecraft === null, pickLayout: t.kind === 'VANILLA',
      }
    }
    case 'install':
    case 'uninstall': {
      fire('busy', { value: true, title: method })
      for (const line of ['Checking Fabric Loader…', 'Downloading Fabric API…', 'Downloading Fabric Language Kotlin…', 'Writing Relict 0.1.1…', 'Done.']) { fire('log', { text: line }); await wait(450) }
      fire('busy', { value: false })
      return mockCall('status', p)
    }
    case 'updates.startup':
      await wait(500)
      fire('banner', { visible: true, title: 'A new Relict 0.2.0 and installer 0.2.0 is available', detail: 'You have Relict 0.1.1. Adds Donut ESP improvements.', updateAction: true, installerAction: true })
      return {}
    case 'updates.check':
      fire('banner', { visible: true, title: "You're up to date", detail: 'Relict 0.1.1 is the newest build.' })
      return {}
    default:
      return {}
  }
}
