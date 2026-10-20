import { defineConfig } from '@playwright/test'

// Against the production build, served under the Pages base, as it ships.
export default defineConfig({
  testDir: 'e2e',
  use: { baseURL: 'http://localhost:4174/lotm-ttrpg/' },
  webServer: {
    command: 'npm run build && npx vite preview --port 4174 --strictPort',
    url: 'http://localhost:4174/lotm-ttrpg/',
    reuseExistingServer: false,
    timeout: 180_000,
  },
})
