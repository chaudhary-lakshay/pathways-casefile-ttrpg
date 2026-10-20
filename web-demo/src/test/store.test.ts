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
    const store = new BrowserStore(m)
    store.load()
    store.save(new Map([['Beat Cop', SavedSession({ cor: 3 })]]))
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

  test('an unreadable save that cannot be kept aside is never overwritten', () => {
    const m = new Memory()
    m.setItem(BACKUP_KEY, 'first')
    m.setItem(KEY, 'second')
    const store = new BrowserStore(m)
    expect(store.load().unreadable).toBe(true)
    expect(store.save(new Map([['A', SavedSession()]]))).toBe('blocked')
    expect(m.getItem(KEY)).toBe('second')
  })

  test('discarding the backup moves the waiting unreadable save into it, and saving resumes', () => {
    const m = new Memory()
    m.setItem(BACKUP_KEY, 'first')
    m.setItem(KEY, 'second')
    const store = new BrowserStore(m)
    store.load()
    store.discardUnreadable()
    expect(store.unreadableText()).toBe('second')
    expect(store.save(new Map([['A', SavedSession()]]))).toBe('saved')
  })

  test('a backup the browser refuses to write blocks the save', () => {
    const m = new Memory()
    m.setItem(KEY, 'bad')
    const refusesBackup: StorageLike = {
      getItem: (k) => m.getItem(k),
      setItem: (k, v) => {
        if (k === BACKUP_KEY) throw new Error('QuotaExceededError')
        m.setItem(k, v)
      },
      removeItem: (k) => m.removeItem(k),
    }
    const store = new BrowserStore(refusesBackup)
    store.load()
    expect(store.save(new Map([['A', SavedSession()]]))).toBe('blocked')
    expect(m.getItem(KEY)).toBe('bad')
  })

  test('a read that throws is not taken for an empty store', () => {
    const m = new Memory()
    m.setItem(KEY, '{"Old":{}}')
    let readable = false
    const flaky: StorageLike = {
      getItem: (k) => {
        if (!readable) throw new Error('SecurityError')
        return m.getItem(k)
      },
      setItem: (k, v) => m.setItem(k, v),
      removeItem: (k) => m.removeItem(k),
    }
    const store = new BrowserStore(flaky)
    store.load()
    readable = true
    expect(store.save(new Map([['A', SavedSession()]]))).toBe('blocked')
    expect(m.getItem(KEY)).toBe('{"Old":{}}')
  })

  test("a tab holding an older snapshot does not overwrite another tab's save", () => {
    const m = new Memory()
    const first = new BrowserStore(m)
    const second = new BrowserStore(m)
    first.load()
    second.load()
    expect(second.save(new Map([['Newer', SavedSession({ cor: 2 })]]))).toBe('saved')
    expect(first.save(new Map([['Older', SavedSession()]]))).toBe('stale')
    expect(new BrowserStore(m).load().sessions.has('Newer')).toBe(true)
    expect(first.load().sessions.has('Newer')).toBe(true)
    expect(first.save(new Map([['Older', SavedSession()]]))).toBe('saved')
  })

  test('an import the browser refuses to write throws', () => {
    const m = new Memory()
    const store = new BrowserStore({
      getItem: (k) => m.getItem(k),
      setItem: () => {
        throw new Error('QuotaExceededError')
      },
      removeItem: () => {},
    })
    store.load()
    expect(() => store.importText(ANDROID_FILE())).toThrow()
  })

  test("importing Android's sessions.json replaces the saves", () => {
    const m = new Memory()
    const store = new BrowserStore(m)
    store.load()
    store.save(new Map([['Old', SavedSession()]]))
    const imported = store.importText(ANDROID_FILE())
    expect(imported.has('Old')).toBe(false)
    expect(imported.size).toBeGreaterThan(0)
    expect(m.getItem(KEY)).toBe(ANDROID_FILE())
  })

  test('a failed import touches nothing', () => {
    const m = new Memory()
    const store = new BrowserStore(m)
    store.load()
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
    expect(store.save(new Map([['A', SavedSession()]]))).not.toBe('saved')
  })
})
