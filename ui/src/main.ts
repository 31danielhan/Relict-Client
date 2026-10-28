import { mount } from 'svelte'
import './app.css'
import App from './App.svelte'
import HudApp from './hud/HudApp.svelte'
import { startMock } from './hud/mock'
import { invoke } from './lib/bridge'

// The game shows this same bundle twice: as the window you open with the key, and (at #hud) as the always-on
// layer that draws the HUDs and pop-ups over the world.
const hudMode = location.hash === '#hud' || location.hash === '#toasts'
const target = document.getElementById('app')!
const app = hudMode ? mount(HudApp, { target, props: { mode: location.hash === '#toasts' ? 'toasts' : 'panels' } }) : mount(App, { target })
if (hudMode) startMock()

// Tell the game when a text field has focus, so Right Shift (which closes the window) can still make capital letters there.
if (!hudMode) {
  const isField = (e: EventTarget | null) => e instanceof HTMLElement && e.matches('input:not([type=range]):not([type=checkbox]), textarea, [contenteditable]')
  const report = (editing: boolean) => invoke('ui.editing', { editing }).catch(() => {})
  document.addEventListener('focusin', (e) => { if (isField(e.target)) report(true) })
  document.addEventListener('focusout', (e) => { if (isField(e.target)) report(false) })
}

export default app
