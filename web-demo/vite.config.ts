/// <reference types="vitest/config" />
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const here = (p: string) => fileURLToPath(new URL(p, import.meta.url))

const SCHEMES = here('../app/src/main/kotlin/com/pathways/app/ThemeSchemes.kt')

/**
 * `virtual:case-file.css`: the app's one colour scheme as CSS variables, read
 * from the generated ThemeSchemes.kt at build time so the two cannot drift.
 */
function caseFile(): Plugin {
  const id = 'virtual:case-file.css'
  return {
    name: 'case-file-scheme',
    resolveId: (source) => (source === id ? `\0${id}` : null),
    load(resolved) {
      if (resolved !== `\0${id}`) return null
      this.addWatchFile(SCHEMES)
      const roles = [...readFileSync(SCHEMES, 'utf8').matchAll(/(\w+) = Color\(0xFF([0-9A-F]{6})\)/g)]
      if (roles.length < 30) throw new Error(`ThemeSchemes.kt gave ${roles.length} colour roles`)
      const kebab = (s: string) => s.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)
      return `:root{${roles.map(([, role, hex]) => `--${kebab(role)}:#${hex};`).join('')}}`
    },
  }
}

// The rule data, fonts and art are the Android app's own: imported from where
// the app ships them, never copied, so the two cannot drift.
export default defineConfig({
  // A GitHub Pages project site lives under /<repo>/.
  base: process.env.PAGES_BASE ?? '/pathways-casefile-ttrpg/',
  plugins: [react(), caseFile()],
  resolve: {
    alias: {
      '@content': here('../core/rules/src/main/resources/content'),
      '@fixtures': here('../fixtures'),
      '@res': here('../app/src/main/res'),
      '@icons': here('node_modules/@material-design-icons/svg/filled'),
    },
  },
  server: { fs: { allow: [here('..')] } },
  // ponytail: one chunk; the rule content is most of it and every screen needs it.
  build: { chunkSizeWarningLimit: 1024 },
  test: { include: ['src/**/*.test.ts'] },
})
