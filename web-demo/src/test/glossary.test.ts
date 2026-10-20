import { describe, expect, test } from 'vitest'
import { glossaryGroups } from '../app/Glossary'
import * as ContentLoader from '../rules/content/ContentLoader'

// GlossaryScreen.kt `glossaryGroups`.
describe('the glossary groups', () => {
  const entries = [
    { term: 'Acting', definition: 'a' },
    { term: 'Aura', definition: 'b' },
    { term: 'beyonder', definition: 'c' },
    { term: 'Ritual', definition: 'd' },
  ]

  test('runs of one first letter group under it, upper-cased', () => {
    expect(glossaryGroups(entries, '').map((g) => [g.letter, g.entries.length])).toEqual([['A', 2], ['B', 1], ['R', 1]])
  })

  test('the trimmed query filters terms, ignoring case', () => {
    expect(glossaryGroups(entries, ' AU ').map((g) => g.entries.map((e) => e.term))).toEqual([['Aura']])
    expect(glossaryGroups(entries, 'zzz')).toEqual([])
  })

  test('the book glossary loads into groups', () => {
    const all = ContentLoader.glossary()
    expect(glossaryGroups(all, '').flatMap((g) => g.entries)).toHaveLength(all.length)
  })
})
