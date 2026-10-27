import './fonts.css'
import '../theme.css'
import { mount } from 'svelte'
import InstallerApp from './InstallerApp.svelte'

const start = () => mount(InstallerApp, { target: document.getElementById('app')! })

// Inside the installer window the Kotlin side hands over window.backend once the page has loaded and then calls
// relictBoot. In a normal browser (npm run dev:installer) there is no such window, so the page starts straight away.
if (navigator.userAgent.includes('JavaFX')) (window as any).relictBoot = () => setTimeout(() => { try { start() } catch (e: any) { alert('boot failed: ' + String(e)) } }, 0)
else start()
