/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'

const here = (p: string) => fileURLToPath(new URL(p, import.meta.url))

// The rule data is the Android app's own: imported from where the app ships
// it, never copied, so the two cannot drift.
export default defineConfig({
  // A GitHub Pages project site lives under /<repo>/.
  base: process.env.PAGES_BASE ?? '/lotm-ttrpg/',
  plugins: [react()],
  resolve: {
    alias: {
      '@content': here('../core/rules/src/main/resources/content'),
      '@fixtures': here('../fixtures'),
    },
  },
  server: { fs: { allow: [here('..')] } },
  test: { include: ['src/**/*.test.ts'] },
})
