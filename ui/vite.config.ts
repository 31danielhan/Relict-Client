import { defineConfig, type Plugin } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

// The page is loaded over file:// inside the game. Chromium refuses ES-module scripts from file://,
// so the bundle is emitted as one classic script and the module/crossorigin attributes are dropped.
function classicScript(): Plugin {
  return {
    name: 'relict-classic-script',
    enforce: 'post',
    transformIndexHtml(html) {
      return html
        .replace(/<script type="module" crossorigin/g, '<script defer')
        .replace(/<link rel="stylesheet" crossorigin/g, '<link rel="stylesheet"')
    },
  }
}

export default defineConfig({
  base: './',
  plugins: [svelte(), classicScript()],
  build: {
    outDir: '../build/ui',
    emptyOutDir: true,
    cssCodeSplit: false,
    rollupOptions: {
      output: {
        format: 'iife',
      },
    },
  },
})
