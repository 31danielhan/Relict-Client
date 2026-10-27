import { defineConfig, type Plugin } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

// The installer shows this page in a JavaFX WebView through loadContent, so it has to be one self-contained file:
// the script and the stylesheet (with the fonts inside it) are written into the HTML.
function singleFile(): Plugin {
  return {
    name: 'relict-single-file',
    enforce: 'post',
    generateBundle(_options, bundle) {
      const html = Object.values(bundle).find((f) => f.type === 'asset' && f.fileName.endsWith('.html'))
      if (!html || html.type !== 'asset') return
      let page = String(html.source)
      for (const [name, file] of Object.entries(bundle)) {
        if (file.type === 'chunk' && name.endsWith('.js')) {
          page = page.replace(/<script type="module"[^>]*><\/script>/, '')
          page = page.replace('</body>', () => `<script>${file.code.replace(/<\/script/gi, '<\\/script')}</script></body>`)
          delete bundle[name]
        } else if (file.type === 'asset' && name.endsWith('.css')) {
          page = page.replace(/<link rel="stylesheet"[^>]*>/, () => `<style>${String(file.source)}</style>`)
          delete bundle[name]
        }
      }
      html.source = page
      html.fileName = 'index.html'
    },
  }
}

export default defineConfig({
  plugins: [svelte(), singleFile()],
  build: {
    outDir: '../installer/build/generated/web/web',
    emptyOutDir: true,
    minify: process.env.RELICT_DEBUG ? false : 'oxc',
    assetsInlineLimit: 100_000_000,
    cssCodeSplit: false,
    rollupOptions: {
      input: 'installer.html',
      output: { format: 'iife', inlineDynamicImports: true },
    },
  },
})
