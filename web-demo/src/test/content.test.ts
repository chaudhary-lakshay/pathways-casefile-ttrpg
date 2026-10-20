import { expect, test } from 'vitest'
import { cases, replay } from './parity'
import * as ContentLoader from '../rules/content/ContentLoader'

for (const c of cases('content')) {
  const name = c.fn.replace('ContentLoader.', '')
  replay('content', c.fn, () => ContentLoader.byName(name))
}

test('the TS loader has every accessor the Kotlin one has, and no more', () => {
  const kotlin = cases('content').map((c) => c.fn.replace('ContentLoader.', '')).sort()
  expect(ContentLoader.accessorNames().sort()).toEqual(kotlin)
})
