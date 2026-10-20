// Regenerates src/test/vectors from the Kotlin engine (web-demo/parity), with
// the repo's own Gradle wrapper. Needs a JDK 17.
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew'
const r = spawnSync(gradlew, ['-p', 'web-demo/parity', 'run', '--console=plain', '-q'], {
  cwd: repo,
  stdio: 'inherit',
  shell: process.platform === 'win32',
})
process.exit(r.status ?? 1)
