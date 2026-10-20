import { describe, expect, test } from 'vitest'
import { hashOf, parentOf, parseRoute, type Route } from '../ui/route'

describe('hash routes', () => {
  test('every route survives a round trip, names with spaces and slashes included', () => {
    const routes: Route[] = [
      { kind: 'gallery' },
      { kind: 'glossary' },
      { kind: 'credits' },
      { kind: 'character', name: 'Beat Cop', screen: 'sheet' },
      { kind: 'character', name: 'A/B ½ Nº', screen: 'pathway' },
      { kind: 'character', name: 'Mira', screen: 'builder' },
    ]
    for (const r of routes) expect(parseRoute(hashOf(r))).toEqual(r)
  })

  test('an unknown or broken hash is the character list', () => {
    expect(parseRoute('')).toEqual({ kind: 'gallery' })
    expect(parseRoute('#/c/%E0%A4%A')).toEqual({ kind: 'gallery' })
    expect(parseRoute('#/nowhere')).toEqual({ kind: 'gallery' })
  })

  test('back goes pathway -> sheet -> list, as BackHandler does', () => {
    expect(parentOf({ kind: 'character', name: 'A', screen: 'pathway' })).toEqual({ kind: 'character', name: 'A', screen: 'sheet' })
    expect(parentOf({ kind: 'character', name: 'A', screen: 'sheet' })).toEqual({ kind: 'gallery' })
  })
})
