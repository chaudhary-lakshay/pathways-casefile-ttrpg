import { canonical, coerceIn } from './kt'
import { LAYERS, sourceKind, type Condition, type ModEffect, type ModTarget, type Modifier, type SourceKind, type StackGroup } from './model'

export interface Context {
  isNight: boolean
  isDarkness: boolean
  sequenceDelta: number
  turn: number
}

export const Context = (p: Partial<Context> = {}): Context => ({
  isNight: false, isDarkness: false, sequenceDelta: 0, turn: 0, ...p,
})

export interface Contribution {
  ids: string[]
  delta: number
}

export interface Resolution {
  value: number
  forbidden: boolean
  requiresCritical: boolean
  applied: Contribution[]
}

// Kotlin Long arithmetic: BigInt wrapped to 64 bits, and `.toInt()` to 32.
const long = (n: bigint) => BigInt.asIntN(64, n)
const toInt = (n: bigint) => Number(BigInt.asIntN(32, n))
function floorDivL(a: bigint, b: bigint): bigint {
  const q = a / b
  return (a % b !== 0n && (a < 0n) !== (b < 0n)) ? q - 1n : q
}
function gcd(a: bigint, b: bigint): bigint {
  let x = a < 0n ? -a : a
  let y = b < 0n ? -b : b
  while (y !== 0n) {
    const t = y
    y = x % y
    x = t
  }
  return x === 0n ? 1n : x
}

/** Kotlin `groupBy`: keys in first-seen order, equal by value. */
function groupBy<T, K>(xs: T[], key: (x: T) => K): [K, T[]][] {
  const out = new Map<string, [K, T[]]>()
  for (const x of xs) {
    const k = key(x)
    const id = canonical(k)
    const slot = out.get(id)
    if (slot) slot[1].push(x)
    else out.set(id, [k, [x]])
  }
  return [...out.values()]
}

/** Kotlin `maxBy`: the first of equal maxima. */
function maxBy<T>(xs: T[], f: (x: T) => number): T {
  let best = xs[0]
  for (const x of xs) if (f(x) > f(best)) best = x
  return best
}

const setToN = (m: Modifier) => (m.effect as Extract<ModEffect, { type: 'SetTo' }>).n
const flatN = (m: Modifier) => (m.effect as Extract<ModEffect, { type: 'Flat' }>).n

function runLayers(
  base: number,
  live: Modifier[],
  ctx: Context,
  cancelBudget: Map<SourceKind, number> | null,
  penaltyLedger: Map<SourceKind, number>,
  contributions: Contribution[] | null = null,
): [number, boolean, boolean] {
  let value = base
  let forbidden = false
  let requiresCritical = false
  let chainActive = false
  let chainBase = 0
  let chainNum = 1n
  let chainDen = 1n
  let chainFloor = 0
  const record = (kind: SourceKind, delta: number) => {
    if (delta < 0) penaltyLedger.set(kind, (penaltyLedger.get(kind) ?? 0) + delta)
  }
  const contribute = (ids: string[], delta: number) => {
    if (delta !== 0) contributions?.push({ ids, delta })
  }
  const capped = (raw: number, group: StackGroup | null) => (group === null ? raw : coerceIn(raw, -group.cap, group.cap))
  const commitChain = () => {
    if (!chainActive) return
    value = chainFloor
    chainActive = false
    chainNum = 1n
    chainDen = 1n
  }
  const applyScale = (m: Modifier, e: Extract<ModEffect, { type: 'Scale' }>) => {
    if (m.group === null && !e.roundUp) {
      if (!chainActive) {
        chainActive = true
        chainBase = value
        chainFloor = value
      }
      let n = long(chainNum * BigInt(e.num))
      let d = long(chainDen * BigInt(e.den))
      const g = gcd(n, d)
      if (g !== 0n) {
        n /= g
        d /= g
      }
      chainNum = n
      chainDen = d
      const newFloor = toInt(floorDivL(long(BigInt(chainBase) * chainNum), chainDen))
      contribute([m.id], newFloor - chainFloor)
      chainFloor = newFloor
    } else {
      commitChain()
      const before = value
      const raw = long(BigInt(before) * BigInt(e.num))
      const scaled = toInt(e.roundUp ? -floorDivL(-raw, BigInt(e.den)) : floorDivL(raw, BigInt(e.den)))
      const applied = capped(scaled - before, m.group)
      value = before + applied
      contribute([m.id], applied)
    }
  }
  for (const layer of LAYERS) {
    const inLayer = live.filter((it) => it.layer === layer)
    if (inLayer.length === 0) continue
    const flatSetTo = inLayer.filter((it) => it.effect.type === 'Flat' || it.effect.type === 'SetTo')
    if (flatSetTo.length > 0) {
      commitChain()
      for (const [group, ms] of groupBy(flatSetTo, (it) => it.group)) {
        const setTos = ms.filter((it) => it.effect.type === 'SetTo')
        const flats = ms.filter((it) => it.effect.type === 'Flat')
        if (group !== null && setTos.length > 0) {
          const winner = maxBy(setTos, setToN)
          const applied = capped(setToN(winner), group)
          value += applied
          record(sourceKind(winner.source), applied)
          contribute([winner.id], applied)
        } else {
          if (flats.length > 0) {
            const raw = flats.reduce((s, it) => s + flatN(it), 0)
            const applied = capped(raw, group)
            value += applied
            contribute(flats.map((it) => it.id), applied)
            if (raw !== 0) {
              let prefixRaw = 0n
              let prefixApplied = 0n
              for (const [kind, kms] of groupBy(flats, (it) => sourceKind(it.source))) {
                prefixRaw += BigInt(kms.reduce((s, it) => s + flatN(it), 0))
                const next = floorDivL(long(BigInt(applied) * prefixRaw), BigInt(raw))
                record(kind, toInt(next - prefixApplied))
                prefixApplied = next
              }
            }
          }
          if (setTos.length > 0) {
            const winner = maxBy(setTos, setToN)
            const applied = capped(setToN(winner), group)
            value += applied
            record(sourceKind(winner.source), applied)
            contribute([winner.id], applied)
          }
        }
      }
    }
    for (const m of inLayer) {
      const e = m.effect
      switch (e.type) {
        case 'Flat':
        case 'SetTo':
          break // handled above
        case 'Scale':
          applyScale(m, e)
          break
        case 'Decay': {
          commitChain()
          const remaining = e.from + e.perTurn * ctx.turn
          const raw = e.from < 0 ? Math.min(remaining, 0) : Math.max(remaining, 0)
          const applied = capped(raw, m.group)
          value += applied
          record(sourceKind(m.source), applied)
          contribute([m.id], applied)
          break
        }
        case 'Cancel': {
          commitChain()
          if (cancelBudget !== null) {
            const available = -(cancelBudget.get(e.of) ?? 0)
            const offset = capped(Math.min(e.upTo, available), m.group)
            value += offset
            cancelBudget.set(e.of, (cancelBudget.get(e.of) ?? 0) + offset)
            contribute([m.id], offset)
          }
          break
        }
        case 'Forbid':
          forbidden = true
          break
        case 'RequireCrit':
          requiresCritical = true
          break
      }
    }
  }
  commitChain()
  return [value, forbidden, requiresCritical]
}

export function resolve(base: number, mods: Modifier[], target: ModTarget, ctx: Context): Resolution {
  const t = canonical(target)
  const activeForTarget = mods.filter((it) => canonical(it.target) === t && !expired(it, ctx) && matches(it.condition, ctx))
  const superseded = new Set(activeForTarget.map((it) => it.supersedes).filter((s): s is string => s !== null))
  const live = activeForTarget.filter((it) => !superseded.has(it.id))
  const penaltyLedger = new Map<SourceKind, number>()
  runLayers(base, live, ctx, null, penaltyLedger)
  const contributions: Contribution[] = []
  const [value, forbidden, requiresCritical] = runLayers(base, live, ctx, new Map(penaltyLedger), new Map(), contributions)
  return { value, forbidden, requiresCritical, applied: contributions }
}

function expired(m: Modifier, ctx: Context): boolean {
  if (m.startTurn === null) return false
  const elapsed = ctx.turn - m.startTurn
  const d = m.duration
  switch (d.type) {
    case 'Permanent':
    case 'Scene':
      return false
    case 'Turns':
      return elapsed >= d.n
    case 'Minutes':
      return elapsed >= d.n * 60
  }
}

function matches(c: Condition, ctx: Context): boolean {
  switch (c.type) {
    case 'Always': return true
    case 'AtNight': return ctx.isNight
    case 'InDarkness': return ctx.isDarkness
    case 'AgainstLowerSequence': return ctx.sequenceDelta >= c.delta
    case 'All': return c.of.every((it) => matches(it, ctx))
    case 'Any': return c.of.some((it) => matches(it, ctx))
  }
}
