import { describe, expect, test } from 'vitest'
import { Saves } from '../app/Saves'
import { BrowserStore, KEY, type StorageLike } from '../app/session/BrowserStore'
import { SavedSession } from '../app/session/SessionStore'

const memory = (): StorageLike & { items: Map<string, string> } => {
  const items = new Map<string, string>()
  return { items, getItem: (k) => items.get(k) ?? null, setItem: (k, v) => void items.set(k, v), removeItem: (k) => void items.delete(k) }
}

// MainActivity.kt's App(): one `saved` map, stored whole after every change.
describe('the saves the app holds', () => {
  test('a change is stored and survives a new load', () => {
    const m = memory()
    const saves = new Saves(new BrowserStore(m))
    saves.put('Beat Cop', SavedSession({ cor: 2 }))
    expect(new Saves(new BrowserStore(m)).saved.get('Beat Cop')?.cor).toBe(2)
  })

  test("another tab's write is adopted, and the stale change is reported, not written", () => {
    const m = memory()
    const mine = new Saves(new BrowserStore(m))
    const theirs = new Saves(new BrowserStore(m))
    theirs.put('Theirs', SavedSession())
    const generation = mine.generation
    mine.put('Mine', SavedSession())
    expect(mine.saved.has('Theirs')).toBe(true)
    expect(mine.saved.has('Mine')).toBe(false)
    expect(mine.generation).toBeGreaterThan(generation)
    expect(mine.notice).toMatch(/another tab/i)
    expect(m.items.get(KEY)).toContain('Theirs')
  })

  test('a quiet write that meets a stale store reloads after the render, not during it', async () => {
    const m = memory()
    const mine = new Saves(new BrowserStore(m))
    new Saves(new BrowserStore(m)).put('Theirs', SavedSession())
    let heard = 0
    mine.subscribe(() => heard++)
    const generation = mine.generation
    mine.put('Mine', SavedSession(), true)
    expect(heard).toBe(0)
    expect(mine.generation).toBe(generation)
    await Promise.resolve()
    expect(mine.generation).toBeGreaterThan(generation)
    expect(mine.saved.has('Theirs')).toBe(true)
    expect(mine.notice).toMatch(/another tab/i)
  })

  test('storage that cannot be read says so, not that a save is waiting', () => {
    const m = memory()
    const saves = new Saves(new BrowserStore({ ...m, getItem: () => { throw new Error('SecurityError') } }))
    saves.put('A', SavedSession())
    expect(saves.notice).toMatch(/cannot read/i)
  })

  test('an unreadable save is reported and kept aside; saving goes on', () => {
    const m = memory()
    m.setItem(KEY, 'not json')
    const saves = new Saves(new BrowserStore(m))
    expect(saves.unreadable).toBe('not json')
    saves.put('A', SavedSession())
    expect(saves.notice).toBeNull()
    expect(saves.unreadable).toBe('not json')
    expect(m.items.get(KEY)).toContain('"A"')
  })

  test('a second unreadable save while the slot is full is not overwritten', () => {
    const m = memory()
    m.setItem(KEY, 'first')
    new Saves(new BrowserStore(m))
    m.setItem(KEY, 'second')
    const saves = new Saves(new BrowserStore(m))
    saves.put('A', SavedSession())
    expect(saves.notice).toMatch(/not saved/i)
    expect(m.items.get(KEY)).toBe('second')
  })
})
