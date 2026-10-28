// Stand-in for the game when the HUD page runs in a normal browser (npm run dev, then open /#hud).
import { inGame } from '../lib/bridge'

const emit = (name: string, payload: unknown) => window.relict.emit(name, payload)

export function startMock() {
  if (inGame) return
  const NUMBERS = [
    [82, 100, 101, 102, 103, 104, 105, 106, 91], [83, 44, 75, 42, 41, 40, 39, 38, 92], [84, 45, 14, 13, 12, 11, 10, 37, 93],
    [85, 46, 74, 3, 2, 1, 25, 36, 94], [86, 47, 72, 71, 5, 4, 24, 35, 95], [87, 51, 17, 9, 8, 7, 23, 34, 96],
    [88, 54, 18, 61, 62, 21, 22, 33, 97], [89, 26, 27, 28, 29, 30, 59, 32, 98], [90, 107, 108, 109, 110, 111, 112, 113, 99],
  ]
  const GROUPS = [
    'OC NW NW NW NE NE NE NE NE', 'OC NW NW NW NE NE NE NE NE', 'OC NW NW NW NE NE NE NE NE', 'OC OC OC NW NE NE NE NE NE',
    'AS AS AS AS NE NE NE NE NE', 'AS EW EW EC EC EC EC EC NE', 'AS EW EW EC EC EC EC EC EC', 'EC EW EC EC EC EC EC EC EC', 'EC EW EW EW EW EW EW EW EC',
  ]
  const NAMES: Record<string, string> = { EC: 'EU Central', EW: 'EU West', NW: 'NA West', NE: 'NA East', AS: 'Asia', OC: 'Oceania' }
  const grid = NUMBERS.map((row, r) => row.map((number, c) => ({ number, group: NAMES[GROUPS[r].split(' ')[c]] })))
  emit('hud.set', { key: 'config', value: { accent: 'PURPLE' } })
  const pings: Record<string, number> = {}
  for (let r = 0; r < 9; r++) for (let c = 0; c < 9; c++) if ((r * 3 + c * 5) % 4 !== 0) pings[`${c},${r}`] = 25 + ((r * 37 + c * 53) % 210)
  emit('hud.set', { key: 'regions', value: { grid, cellSize: 50000, half: 225000, pings, ping: 58, labels: 'ping' } })
  emit('hud.set', { key: 'finder', value: { entries: [
    { id: '62,62', kind: 'Stash', x: 1000, z: 1000, minY: -64, maxY: -49, sections: 1, totalSections: 1, confidence: 91 },
    { id: '70,50', kind: 'Farm', x: 1130, z: 810, minY: 30, maxY: 60, sections: 3, totalSections: 4, confidence: 64 },
    { id: '40,40', kind: 'Excavated', x: 650, z: 650, minY: -40, maxY: -20, sections: 2, totalSections: 5, confidence: 38 },
  ] } })
  emit('hud.set', { key: 'inspect', value: {
    title: 'Unknown', percent: 52, direction: 'N', distance: 19, minY: -55, maxY: 8, sections: 13, totalSections: 13,
    evidence: [{ points: 6, text: '1 chunk next door is flagged too' }, { points: 8, text: 'blocks changed since your last scan' }, { points: 10, text: '5 kinds of player-made blocks' }, { points: 8, text: 'obsidian 11' }],
    points: 42, level: 'MEDIUM', note: 'sections received below Y 0: 5 of 8',
  } })
  let t = 0
  setInterval(() => {
    t += 0.05
    emit('hud.set', { key: 'player', value: { x: 100000 + Math.cos(t) * 90000, z: 80000 + Math.sin(t) * 90000 } })
  }, 50)
  const toasts = [
    { title: 'Possible player', message: '-135800 177672 · blocks changed here', tone: 'player' },
    { title: 'Base found', message: 'Stash 91% · 120m NW', tone: 'base' },
    { title: 'Farm found', message: 'Kelp farm 74% · 60m S', tone: 'farm' },
    { title: 'Relict', message: 'The browser is still starting.', tone: 'info' },
  ]
  let i = 0
  setInterval(() => emit('hud.toast', toasts[i++ % toasts.length]), 2500)
}
