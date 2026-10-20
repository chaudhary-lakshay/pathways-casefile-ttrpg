import { expect, test } from 'vitest'
import { canonical, ValueMap } from '../rules/kt'

/** One case the Kotlin engine answered: `web-demo/parity` writes these. */
export interface Case {
  fn: string
  args: unknown[]
  out?: unknown
  throws?: string
}

const files = import.meta.glob<Case[]>('./vectors/*.json', { eager: true, import: 'default' })

export function cases(file: string): Case[] {
  const found = files[`./vectors/${file}.json`]
  if (!found) throw new Error(`no vector file ${file}.json in ${Object.keys(files)}; run npm run parity`)
  return found
}

/** A TS value in the shape `parity/Dump.kt` writes a Kotlin one. */
export function canon(v: unknown): unknown {
  if (v === undefined) throw new Error('undefined has no Kotlin counterpart; use null')
  if (v === null || typeof v !== 'object') return v
  if (v instanceof ValueMap) return Object.fromEntries([...v.entries()].map(([k, x]) => [keyOf(k), canon(x)]))
  if (v instanceof Map) return Object.fromEntries([...v.entries()].map(([k, x]) => [keyOf(k), canon(x)]))
  if (Array.isArray(v)) return v.map(canon)
  return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, canon(x)]))
}

function keyOf(k: unknown): string {
  if (typeof k === 'string') return k
  if (typeof k === 'number' || typeof k === 'boolean') return String(k)
  return canonical(canon(k))
}

/**
 * Every case of [fn] in [file], replayed through [run]: the value must match
 * the Kotlin dump, and a Kotlin throw must be a throw of the same class.
 */
export function replay(file: string, fn: string, run: (...args: any[]) => unknown) {
  const all = cases(file).filter((c) => c.fn === fn)
  test(`${fn}: ${all.length} Kotlin cases`, () => {
    expect(all.length).toBeGreaterThan(0)
    for (const c of all) {
      const label = `${fn}(${JSON.stringify(c.args)})`
      if (c.throws) {
        let threw: unknown = null
        try {
          run(...c.args)
        } catch (e) {
          threw = e
        }
        expect(threw, `${label} should throw ${c.throws}`).toBeInstanceOf(Error)
        expect((threw as Error).name, label).toBe(c.throws)
      } else {
        expect(canon(run(...c.args)), label).toEqual(c.out)
      }
    }
  })
}
