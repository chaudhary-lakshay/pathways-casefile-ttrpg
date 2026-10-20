// Kotlin's semantics where JavaScript's differ. The engine was written
// against these, so the port uses them by name instead of the JS operators.

function exception(name: string) {
  const E = class extends Error {
    constructor(message?: string) {
      super(message)
      this.name = name
    }
  }
  Object.defineProperty(E, 'name', { value: name })
  return E
}

export const IllegalArgumentException = exception('IllegalArgumentException')
export const IllegalStateException = exception('IllegalStateException')
export const NoSuchElementException = exception('NoSuchElementException')
export const ArithmeticException = exception('ArithmeticException')
export const NullPointerException = exception('NullPointerException')

/** Kotlin's `!!`. */
export function nn<T>(v: T | null | undefined): T {
  if (v === null || v === undefined) throw new NullPointerException()
  return v
}

/** Kotlin's `require`: a bad argument. */
export function require(ok: boolean, message: () => string = () => 'Failed requirement.'): asserts ok {
  if (!ok) throw new IllegalArgumentException(message())
}

/** Kotlin's `check`: a bad state. */
export function check(ok: boolean, message: () => string = () => 'Check failed.'): asserts ok {
  if (!ok) throw new IllegalStateException(message())
}

/** Kotlin's `error`. */
export function error(message: string): never {
  throw new IllegalStateException(message)
}

/** Kotlin's `Map.getValue`. */
export function getValue<K, V>(m: Map<K, V> | ValueMap<K, V>, k: K): V {
  if (!m.has(k)) throw new NoSuchElementException(`Key ${String(k)} is missing in the map.`)
  return m.get(k) as V
}

/** Kotlin's `first { }`. */
export function first<T>(xs: readonly T[], p: (x: T) => boolean = () => true): T {
  for (const x of xs) if (p(x)) return x
  throw new NoSuchElementException('Collection contains no element matching the predicate.')
}

/** Kotlin's `single()`. */
export function single<T>(xs: readonly T[]): T {
  if (xs.length === 0) throw new NoSuchElementException('List is empty.')
  require(xs.length === 1, () => 'List has more than one element.')
  return xs[0]
}

const INT_MIN = -2147483648
const INT_MAX = 2147483647

function int(n: number): number {
  if (n < INT_MIN || n > INT_MAX) throw new ArithmeticException('integer overflow')
  return n
}

/** `Math.floorDiv`: rounds toward negative infinity (CLAUDE.md 4.3). */
export function floorDiv(a: number, b: number): number {
  if (b === 0) throw new ArithmeticException('/ by zero')
  return Math.floor(a / b)
}

/** Kotlin's Int `/`: truncates toward zero. */
export function intDiv(a: number, b: number): number {
  if (b === 0) throw new ArithmeticException('/ by zero')
  return Math.trunc(a / b)
}

/** Kotlin's Int `%`: takes the dividend's sign, as JS `%` does. */
export function rem(a: number, b: number): number {
  if (b === 0) throw new ArithmeticException('/ by zero')
  return a % b
}

export function multiplyExact(a: number, b: number): number {
  return int(a * b)
}

export function addExact(a: number, b: number): number {
  return int(a + b)
}

export function coerceIn(n: number, lo: number, hi: number): number {
  require(lo <= hi, () => `Cannot coerce value to an empty range: maximum ${hi} is less than minimum ${lo}.`)
  return Math.min(Math.max(n, lo), hi)
}

export function sumOf<T>(xs: readonly T[], f: (x: T) => number): number {
  let s = 0
  for (const x of xs) s += f(x)
  return s
}

/** `String.format(Locale.ROOT, "%.2f", x)`, for the quarter-step values it formats. */
export function format2(x: number): string {
  return x.toFixed(2)
}

/**
 * Kotlin's `Double.toString()` for the values the engine prints: a whole
 * double keeps its ".0".
 */
export function doubleString(x: number): string {
  return Number.isInteger(x) ? `${x}.0` : String(x)
}

/** A memoised value: a table that reads another module is built on first use, never at import. */
export function lazy<T>(make: () => T): () => T {
  let made = false
  let value: T
  return () => {
    if (!made) {
      value = make()
      made = true
    }
    return value
  }
}

/** Kotlin's `String.toIntOrNull()`: an optional sign and digits, within Int. */
export function toIntOrNull(s: string): number | null {
  if (!/^[+-]?[0-9]+$/.test(s)) return null
  const n = Number(s)
  return n < INT_MIN || n > INT_MAX ? null : n
}

export const substringAfterLast = (s: string, d: string, missing = s) => {
  const i = s.lastIndexOf(d)
  return i < 0 ? missing : s.slice(i + d.length)
}
export const substringBeforeLast = (s: string, d: string, missing = s) => {
  const i = s.lastIndexOf(d)
  return i < 0 ? missing : s.slice(0, i)
}
export const substringBefore = (s: string, d: string, missing = s) => {
  const i = s.indexOf(d)
  return i < 0 ? missing : s.slice(0, i)
}
export const substringAfter = (s: string, d: string, missing = s) => {
  const i = s.indexOf(d)
  return i < 0 ? missing : s.slice(i + d.length)
}
export const removePrefix = (s: string, p: string) => (s.startsWith(p) ? s.slice(p.length) : s)
export const removeSuffix = (s: string, p: string) => (s.endsWith(p) ? s.slice(0, s.length - p.length) : s)

/** `equals(other, ignoreCase = true)`. */
export const eqIc = (a: string | null | undefined, b: string | null | undefined) =>
  a != null && b != null && a.length === b.length && a.toLowerCase() === b.toLowerCase()

export const startsWithIc = (s: string, p: string) => s.length >= p.length && eqIc(s.slice(0, p.length), p)
export const endsWithIc = (s: string, p: string) => s.length >= p.length && eqIc(s.slice(s.length - p.length), p)
export const containsIc = (s: string, p: string) => s.toLowerCase().includes(p.toLowerCase())

/** Java's `\s`: ASCII whitespace only, where a JS `\s` is Unicode's. */
export const WS = '[ \\t\\n\\x0B\\f\\r]'

export const isBlank = (s: string) => s.trim() === ''

/** `trimEnd(c)` for the characters given. */
export function trimEndChars(s: string, chars: string): string {
  let end = s.length
  while (end > 0 && chars.includes(s[end - 1])) end--
  return s.slice(0, end)
}

/** `distinctBy`: the first of each key, in order. */
export function distinctBy<T>(xs: readonly T[], key: (x: T) => unknown): T[] {
  const seen = new Set<string>()
  const out: T[] = []
  for (const x of xs) {
    const k = canonical(key(x))
    if (!seen.has(k)) {
      seen.add(k)
      out.push(x)
    }
  }
  return out
}

/** `maxByOrNull`: the FIRST of the greatest, as Kotlin returns it. */
export function maxByOrNull<T>(xs: readonly T[], f: (x: T) => number): T | null {
  let best: T | null = null
  let bestV = -Infinity
  for (const x of xs) {
    const v = f(x)
    if (best === null || v > bestV) {
      best = x
      bestV = v
    }
  }
  return best
}

/** `minByOrNull`: the FIRST of the least. */
export function minByOrNull<T>(xs: readonly T[], f: (x: T) => number): T | null {
  let best: T | null = null
  let bestV = Infinity
  for (const x of xs) {
    const v = f(x)
    if (best === null || v < bestV) {
      best = x
      bestV = v
    }
  }
  return best
}

/** `associateBy`: the LAST of each key wins, at the first one's position. */
export function associateBy<T, K>(xs: readonly T[], key: (x: T) => K): Map<K, T> {
  const m = new Map<K, T>()
  for (const x of xs) m.set(key(x), x)
  return m
}

/** Kotlin's `sortedBy`/`sortedByDescending`: stable, by a number or string key. */
export function sortedBy<T>(xs: readonly T[], key: (x: T) => number | string, descending = false): T[] {
  return [...xs].sort((a, b) => {
    const ka = key(a)
    const kb = key(b)
    const c = ka < kb ? -1 : ka > kb ? 1 : 0
    return descending ? -c : c
  })
}

/** Compact JSON with sorted keys: the same string `parity/Dump.kt` writes. */
export function canonical(v: unknown): string {
  if (v === null || typeof v !== 'object') return JSON.stringify(v)
  if (Array.isArray(v)) return `[${v.map(canonical).join(',')}]`
  const keys = Object.keys(v).sort()
  return `{${keys.map((k) => `${JSON.stringify(k)}:${canonical((v as Record<string, unknown>)[k])}`).join(',')}}`
}

/**
 * A Kotlin map keyed by a data class: two keys equal by value are one key.
 * Insertion order is kept, as Kotlin's LinkedHashMap keeps it.
 */
export class ValueMap<K, V> {
  private readonly inner = new Map<string, [K, V]>()

  constructor(entries: Iterable<readonly [K, V]> = []) {
    for (const [k, v] of entries) this.set(k, v)
  }

  get size() {
    return this.inner.size
  }
  has(k: K) {
    return this.inner.has(canonical(k))
  }
  get(k: K): V | undefined {
    return this.inner.get(canonical(k))?.[1]
  }
  set(k: K, v: V) {
    this.inner.set(canonical(k), [k, v])
    return this
  }
  *entries(): IterableIterator<[K, V]> {
    for (const e of this.inner.values()) yield e
  }
  keys(): K[] {
    return [...this.inner.values()].map((e) => e[0])
  }
  values(): V[] {
    return [...this.inner.values()].map((e) => e[1])
  }
}
