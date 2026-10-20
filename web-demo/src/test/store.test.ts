import { describe, expect, test } from 'vitest'
import { cases } from './parity'
import { BrowserStore, BACKUP_KEY, KEY, type StorageLike } from '../app/session/BrowserStore'
import { SavedSession } from '../app/session/SessionStore'

class Memory implements StorageLike {
  readonly items = new Map<string, string>()
  getItem(k: string) {
    return this.items.get(k) ?? null
  }
  setItem(k: string, v: string) {
    this.items.set(k, v)
  }
  removeItem(k: string) {
    this.items.delete(k)
  }
}

const ANDROID_FILE = () => cases('session').find((c) => c.fn === 'SessionStore.roundTrip' && (c.out as string).length > 1000)!.out as string

describe('saves in the browser', () => {
  test('keys are namespaced, since every Pages project on the account shares one origin', () => {
    expect(KEY.startsWith('pathways/')).toBe(true)
    expect(BACKUP_KEY.startsWith('pathways/')).toBe(true)
  })

  test('what is saved loads back after a reload', () => {
    const m = new Memory()
    new BrowserStore(m).save(new Map([['Beat Cop', SavedSession({ cor: 3 })]]))
    const again = new BrowserStore(m).load()
    expect(again.sessions.get('Beat Cop')?.cor).toBe(3)
    expect(again.unreadable).toBe(false)
  })

  test('an unreadable save is kept aside before anything overwrites it', () => {
    const m = new Memory()
    m.setItem(KEY, '{"Beat Cop":{"cor":3.5}}')
    const store = new BrowserStore(m)
    const loaded = store.load()
    expect(loaded.sessions.size).toBe(0)
    expect(loaded.unreadable).toBe(true)
    expect(m.getItem(BACKUP_KEY)).toBe('{"Beat Cop":{"cor":3.5}}')
    store.save(new Map([['Apothecary', SavedSession()]]))
    expect(store.unreadableText()).toBe('{"Beat Cop":{"cor":3.5}}')
  })

  test('a second bad load does not replace the first backup', () => {
    const m = new Memory()
    m.setItem(KEY, 'first')
    new BrowserStore(m).load()
    m.setItem(KEY, 'second')
    new BrowserStore(m).load()
    expect(m.getItem(BACKUP_KEY)).toBe('first')
  })

  test("importing Android's sessions.json replaces the saves", () => {
    const m = new Memory()
    const store = new BrowserStore(m)
    store.save(new Map([['Old', SavedSession()]]))
    const imported = store.importText(ANDROID_FILE())
    expect(imported.has('Old')).toBe(false)
    expect(imported.size).toBeGreaterThan(0)
    expect(m.getItem(KEY)).toBe(ANDROID_FILE())
  })

  test('a failed import touches nothing', () => {
    const m = new Memory()
    const store = new BrowserStore(m)
    store.save(new Map([['Old', SavedSession({ cor: 1 })]]))
    const before = m.getItem(KEY)
    expect(() => store.importText('{"A":{"cor":null}}')).toThrow()
    expect(m.getItem(KEY)).toBe(before)
  })

  test('export writes the text Android reads', () => {
    const m = new Memory()
    const store = new BrowserStore(m)
    store.importText(ANDROID_FILE())
    expect(store.exportText()).toBe(ANDROID_FILE())
  })

  test('storage that throws on access leaves the app running on memory', () => {
    const broken: StorageLike = {
      getItem: () => {
        throw new Error('SecurityError')
      },
      setItem: () => {
        throw new Error('QuotaExceededError')
      },
      removeItem: () => {},
    }
    const store = new BrowserStore(broken)
    expect(store.load().sessions.size).toBe(0)
    expect(store.save(new Map([['A', SavedSession()]]))).toBe(false)
  })
})
