// app/.../SessionStore.kt: the save shapes, and the decoder that reads them
// the way the Android app's Json { ignoreUnknownKeys; encodeDefaults } does.
import { require } from '../../rules/kt'
import { BeyonderState, type BoonState } from '../../rules/model'
import { InstancedPurchase, purchaseKey } from '../../rules/PurchasableTraits'
import { parse, type Node } from './json'

export interface SavedSkill {
  name: string
  specialization: string | null
  points: number
}

export function SavedSkill(name: string, specialization: string | null, points: number): SavedSkill {
  require(points > 0, () => `a purchased skill needs at least 1 point; '${name}' had ${points}`)
  return { name, specialization, points }
}

export interface SavedCharmedSkill {
  name: string
  specialization: string | null
}

export interface SavedLanguage {
  language: string
  proficiency: string
  form: string
}

export interface SavedInstancedTrait {
  row: string
  variant: string | null
  instance: string | null
  cost: number
}

export const instancedKey = (t: SavedInstancedTrait) => purchaseKey(InstancedPurchase(t))

export interface SavedEdits {
  st: number | null
  dx: number | null
  iq: number | null
  ht: number | null
  skills: SavedSkill[]
  wealth: string | null
  appearance: string | null
  traits: string[]
  instanced: SavedInstancedTrait[]
  secondaries: Record<string, number>
  weapons: string[]
  languages: SavedLanguage[]
  charmedSkill: SavedCharmedSkill | null
  boon: BoonState | null
  gained: string[]
  studied: SavedSkill[]
  quirks: string[]
}

/** Every field in Kotlin's declaration order, so an encoded save reads as Android's does. */
export function SavedEdits(p: Partial<SavedEdits> = {}): SavedEdits {
  return {
    st: p.st ?? null,
    dx: p.dx ?? null,
    iq: p.iq ?? null,
    ht: p.ht ?? null,
    skills: p.skills ?? [],
    wealth: p.wealth ?? null,
    appearance: p.appearance ?? null,
    traits: p.traits ?? [],
    instanced: p.instanced ?? [],
    secondaries: p.secondaries ?? {},
    weapons: p.weapons ?? [],
    languages: p.languages ?? [],
    charmedSkill: p.charmedSkill ?? null,
    boon: p.boon ?? null,
    gained: p.gained ?? [],
    studied: p.studied ?? [],
    quirks: p.quirks ?? [],
  }
}

/** Kotlin's `isEmpty`, which does not read `charmedSkill` or `gained`. */
export const isEmpty = (e: SavedEdits) =>
  e.st === null && e.dx === null && e.iq === null && e.ht === null &&
  e.skills.length === 0 && e.wealth === null && e.appearance === null &&
  e.traits.length === 0 && e.instanced.length === 0 && Object.keys(e.secondaries).length === 0 &&
  e.weapons.length === 0 && e.languages.length === 0 && e.boon === null && e.studied.length === 0 &&
  e.quirks.length === 0

export interface SavedSession {
  beyonder: BeyonderState | null
  lost: boolean
  cor: number
  corrupted: boolean
  hpLost: number
  fpLost: number
  spiSpent: number
  edits: SavedEdits
  pendingPcr: BeyonderState | null
  basedOn: string | null
}

export function SavedSession(p: Partial<SavedSession> = {}): SavedSession {
  return {
    beyonder: p.beyonder ?? null,
    lost: p.lost ?? false,
    cor: p.cor ?? 0,
    corrupted: p.corrupted ?? false,
    hpLost: p.hpLost ?? 0,
    fpLost: p.fpLost ?? 0,
    spiSpent: p.spiSpent ?? 0,
    edits: p.edits ?? SavedEdits(),
    pendingPcr: p.pendingPcr ?? null,
    basedOn: p.basedOn ?? null,
  }
}

export const endedPlay = (lost: boolean, corrupted: boolean) => lost || corrupted
export const sessionIsLost = (s: SavedSession) => endedPlay(s.lost, s.corrupted)

/** What kotlinx.serialization throws on a save it cannot read. */
export class SerializationException extends Error {
  override name = 'SerializationException'
}

const fail = (path: string, what: string): never => {
  throw new SerializationException(`${path}: ${what}`)
}

type Read<T> = (v: Node, path: string) => T

/** An object's fields: a repeated key keeps its first place and its last value, as a LinkedHashMap does. */
function object(v: Node, path: string): Map<string, Node> {
  if (v.t !== 'obj') fail(path, 'expected an object')
  return new Map((v as { v: [string, Node][] }).v)
}

const nullable = <T>(read: Read<T>): Read<T | null> => (v, path) => (v.t === 'null' ? null : read(v, path))

const str: Read<string> = (v, path) => (v.t === 'str' ? v.v : fail(path, 'expected a string'))
/** kotlinx reads a quoted "true" as a Boolean too. */
const bool: Read<boolean> = (v, path) => {
  if (v.t === 'bool') return v.v
  if (v.t === 'str' && (v.v === 'true' || v.v === 'false')) return v.v === 'true'
  return fail(path, 'expected a boolean')
}
/** kotlinx's Int: an integer literal, quoted or not, exponent allowed, no fraction. */
const int: Read<number> = (v, path) => {
  const text = v.t === 'num' ? v.s : v.t === 'str' ? v.v : fail(path, 'expected an Int')
  const m = /^(-?\d+)(?:[eE]([+-]?\d+))?$/.exec(text)
  if (!m) return fail(path, `'${text}' is not an Int`)
  let n = BigInt(m[1])
  const e = Number(m[2] ?? '0')
  if (e >= 0) n *= 10n ** BigInt(e)
  else {
    const d = 10n ** BigInt(-e)
    if (n % d !== 0n) return fail(path, `'${text}' is not an Int`)
    n /= d
  }
  if (n < -2147483648n || n > 2147483647n) return fail(path, `'${text}' is out of Int range`)
  return Number(n)
}
const list = <T>(read: Read<T>): Read<T[]> => (v, path) =>
  v.t === 'arr' ? v.v.map((x, i) => read(x, `${path}[${i}]`)) : fail(path, 'expected a list')
const map = <T>(read: Read<T>): Read<Record<string, T>> => (v, path) =>
  Object.fromEntries([...object(v, path)].map(([k, x]) => [k, read(x, `${path}.${k}`)]))

/**
 * A field: required when there is no default, as kotlinx's MissingFieldException
 * says, and a JSON null where the type is not nullable is a decode failure.
 */
function field<T>(o: Map<string, Node>, key: string, path: string, read: Read<T>, fallback?: () => T): T {
  const v = o.get(key)
  if (v === undefined) return fallback ? fallback() : fail(path, `field '${key}' is required`)
  return read(v, `${path}.${key}`)
}

const wrapInit = <T>(path: string, make: () => T): T => {
  try {
    return make()
  } catch (e) {
    // The data class's own `require` fails the whole decode, as on Android.
    throw new SerializationException(`${path}: ${(e as Error).message}`)
  }
}

const beyonderState: Read<BeyonderState> = (v, path) => {
  const o = object(v, path)
  const pathway = field(o, 'pathway', path, str)
  const sequence = field(o, 'sequence', path, int)
  const digestion = field(o, 'digestion', path, int, () => 0)
  const drawback = field(o, 'drawback', path, nullable(str), () => null)
  return wrapInit(path, () => BeyonderState({ pathway, sequence, digestion, drawback }))
}

const boonState: Read<BoonState> = (v, path) => {
  const o = object(v, path)
  return { pathway: field(o, 'pathway', path, str), sequence: field(o, 'sequence', path, int) }
}

const savedSkill: Read<SavedSkill> = (v, path) => {
  const o = object(v, path)
  const name = field(o, 'name', path, str)
  const specialization = field(o, 'specialization', path, nullable(str), () => null)
  const points = field(o, 'points', path, int)
  return wrapInit(path, () => SavedSkill(name, specialization, points))
}

const charmed: Read<SavedCharmedSkill> = (v, path) => {
  const o = object(v, path)
  return { name: field(o, 'name', path, str), specialization: field(o, 'specialization', path, nullable(str), () => null) }
}

const language: Read<SavedLanguage> = (v, path) => {
  const o = object(v, path)
  return {
    language: field(o, 'language', path, str),
    proficiency: field(o, 'proficiency', path, str),
    form: field(o, 'form', path, str),
  }
}

const instanced: Read<SavedInstancedTrait> = (v, path) => {
  const o = object(v, path)
  return {
    row: field(o, 'row', path, str),
    variant: field(o, 'variant', path, nullable(str), () => null),
    instance: field(o, 'instance', path, nullable(str), () => null),
    cost: field(o, 'cost', path, int),
  }
}

const edits: Read<SavedEdits> = (v, path) => {
  const o = object(v, path)
  const f = <T>(key: keyof SavedEdits, read: Read<T>, fallback: () => T) => field(o, key, path, read, fallback)
  return SavedEdits({
    st: f('st', nullable(int), () => null),
    dx: f('dx', nullable(int), () => null),
    iq: f('iq', nullable(int), () => null),
    ht: f('ht', nullable(int), () => null),
    skills: f('skills', list(savedSkill), () => []),
    wealth: f('wealth', nullable(str), () => null),
    appearance: f('appearance', nullable(str), () => null),
    traits: f('traits', list(str), () => []),
    instanced: f('instanced', list(instanced), () => []),
    secondaries: f('secondaries', map(int), () => ({})),
    weapons: f('weapons', list(str), () => []),
    languages: f('languages', list(language), () => []),
    charmedSkill: f('charmedSkill', nullable(charmed), () => null),
    boon: f('boon', nullable(boonState), () => null),
    gained: f('gained', list(str), () => []),
    studied: f('studied', list(savedSkill), () => []),
    quirks: f('quirks', list(str), () => []),
  })
}

const session: Read<SavedSession> = (v, path) => {
  const o = object(v, path)
  const f = <T>(key: keyof SavedSession, read: Read<T>, fallback: () => T) => field(o, key, path, read, fallback)
  return SavedSession({
    beyonder: f('beyonder', nullable(beyonderState), () => null),
    lost: f('lost', bool, () => false),
    cor: f('cor', int, () => 0),
    corrupted: f('corrupted', bool, () => false),
    hpLost: f('hpLost', int, () => 0),
    fpLost: f('fpLost', int, () => 0),
    spiSpent: f('spiSpent', int, () => 0),
    edits: f('edits', edits, () => SavedEdits()),
    pendingPcr: f('pendingPcr', nullable(beyonderState), () => null),
    basedOn: f('basedOn', nullable(str), () => null),
  })
}

/**
 * `json.decodeFromString<Map<String, SavedSession>>(text)`: every entry or
 * none. Throws [SerializationException]; the caller decides what to keep.
 */
export function decodeSessions(text: string): Map<string, SavedSession> {
  let parsed: Node
  try {
    parsed = parse(text)
  } catch (e) {
    throw new SerializationException(`not JSON: ${(e as Error).message}`)
  }
  return new Map([...object(parsed, '$')].map(([k, v]) => [k, session(v, `$.${k}`)]))
}

/**
 * `json.encodeToString(sessions)`: defaults encoded, declaration order. The
 * outer map is written by hand: an object would list a character named "12" first.
 */
export const encodeSessions = (sessions: Map<string, SavedSession>) =>
  `{${[...sessions].map(([k, v]) => `${JSON.stringify(k)}:${JSON.stringify(v)}`).join(',')}}`
