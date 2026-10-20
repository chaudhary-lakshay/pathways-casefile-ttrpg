// app/.../BeyonderSession.kt, one to one: a mutable session whose every
// change is saved through onChange. React reads it with useSyncExternalStore
// (subscribe + version), where Compose read its mutableStateOf fields.
import { canonical, require } from '../../rules/kt'
import type { ActingQuality } from '../../rules/content/schema.gen'
import { Beyonder, SecondaryDelta, type Attr, type BeyonderState, type BoonState, type Character, type Pool, type SkillKey, type Trait } from '../../rules/model'
import * as Corruption from '../../rules/Corruption'
import * as NonStandardPathways from '../../rules/NonStandardPathways'
import * as PotionConsumption from '../../rules/PotionConsumption'
import type { PcrOutcome } from '../../rules/PotionConsumption'
import type { PremadeSheet } from '../../rules/Premades'
import * as Premades from '../../rules/Premades'
import * as PurchasableTraits from '../../rules/PurchasableTraits'
import { InstancedPurchase } from '../../rules/PurchasableTraits'
import type { RandomSource } from '../../rules/RandomSource'
import type { RestRecovery } from '../../rules/Rest'
import * as ScoreFloors from '../../rules/ScoreFloors'
import * as SkillLadder from '../../rules/SkillLadder'
import * as TraitGrants from '../../rules/TraitGrants'
import * as TraitNames from '../../rules/TraitNames'
import * as EC from './EditedCharacter'
import { minusAll, minusFirst } from './EditedCharacter'
import {
  endedPlay, instancedKey, SavedEdits, SavedSession, SavedSkill,
  type SavedInstancedTrait, type SavedLanguage,
} from './SessionStore'

const same = (a: unknown, b: unknown) => canonical(a) === canonical(b)
const describe = (s: BeyonderState | null) =>
  s === null ? 'null' : `BeyonderState(pathway=${s.pathway}, sequence=${s.sequence}, digestion=${s.digestion}, drawback=${s.drawback})`

export class BeyonderSession {
  state: BeyonderState | null
  lost: boolean
  pendingPcr: BeyonderState | null
  cor: number
  corrupted: boolean
  hpLost: number
  fpLost: number
  spiSpent: number
  edits: SavedEdits
  private readonly basedOn: string | null
  private readonly listeners = new Set<() => void>()
  /** Bumped on every saved change: what useSyncExternalStore compares. */
  version = 0

  constructor(
    saved: SavedSession = SavedSession(),
    private readonly sheet: PremadeSheet | null = null,
    private readonly onChange: (s: SavedSession) => void = () => {},
  ) {
    this.state = saved.beyonder
    this.lost = saved.lost
    this.pendingPcr = saved.pendingPcr
    this.cor = saved.cor
    this.corrupted = saved.corrupted
    this.hpLost = saved.hpLost
    this.fpLost = saved.fpLost
    this.spiSpent = saved.spiSpent
    this.edits = saved.edits
    this.basedOn = saved.basedOn
    if (sheet !== null) {
      const repaired = EC.repairedEdits(saved, sheet)
      if (!this.isLost && EC.corCeilingOn(repaired, sheet, this.state) <= this.cor) this.corrupted = true
      if (!same(repaired, this.edits) || this.corrupted !== saved.corrupted) {
        this.edits = repaired
        this.persist()
      }
    }
  }

  get isLost() {
    return endedPlay(this.lost, this.corrupted)
  }

  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  getVersion = () => this.version

  saved(): SavedSession {
    return SavedSession({
      beyonder: this.state, lost: this.lost, cor: this.cor, corrupted: this.corrupted,
      hpLost: this.hpLost, fpLost: this.fpLost, spiSpent: this.spiSpent, edits: this.edits,
      pendingPcr: this.pendingPcr, basedOn: this.basedOn,
    })
  }

  private persist() {
    this.onChange(this.saved())
    this.version++
    for (const l of this.listeners) l()
  }

  rerollPcr(dice: RandomSource, modifier: number, maxCorAfter: (s: BeyonderState) => number): PcrOutcome {
    const next = this.pendingPcr
    const current = this.state
    require(this.lost && !this.corrupted && next !== null && current !== null, () => 'no failed PCR to reroll')
    const ceilingAfter = maxCorAfter(next)
    require(ScoreFloors.holdsCeiling(ceilingAfter), () => `${describe(next)} would leave a CoR ceiling of ${ceilingAfter}`)
    const outcome = PotionConsumption.attempt(current, this.cor, dice, undefined, modifier)
    this.pendingPcr = null
    if (PotionConsumption.integrated(outcome)) {
      this.lost = false
      this.state = next
      this.gainCor(outcome.corGained, ceilingAfter)
    }
    this.persist()
    return outcome
  }

  private allows(next: SavedEdits): boolean {
    return EC.keepsPrerequisites(next, this.sheet, this.state) &&
      (this.sheet === null || (same(EC.shortfallOn(next, this.sheet, this.state), SecondaryDelta()) && this.keepsBelowCeiling(next)))
  }

  private keepsBelowCeiling(next: SavedEdits): boolean {
    return this.isLost || this.sheet === null || EC.corCeilingOn(next, this.sheet, this.state) > this.cor
  }

  private edit(next: SavedEdits) {
    if (!this.allows(next)) return
    this.edits = next
    this.persist()
  }

  setAttribute(attr: Attr, level: number) {
    this.edit(this.withAttribute(attr, level))
  }
  canSetAttribute(attr: Attr, level: number): boolean {
    return this.allows(this.withAttribute(attr, level))
  }
  private withAttribute(attr: Attr, level: number) {
    return EC.withinCeilings(EC.withAttribute(this.edits, attr, level))
  }

  carryWeapon(name: string) {
    if (this.edits.weapons.some((it) => it.toLowerCase() === name.toLowerCase())) return
    this.edits = { ...this.edits, weapons: [...this.edits.weapons, name] }
    this.persist()
  }

  dropWeapon(name: string) {
    const kept = this.edits.weapons.filter((it) => it.toLowerCase() !== name.toLowerCase())
    if (kept.length === this.edits.weapons.length) return
    this.edits = { ...this.edits, weapons: kept }
    this.persist()
  }

  setSecondary(key: string, steps: number) {
    this.edit(this.withSecondary(key, steps))
  }
  canSetSecondary(key: string, steps: number): boolean {
    return this.allows(this.withSecondary(key, steps))
  }
  private withSecondary(key: string, steps: number): SavedEdits {
    const held = EC.withinCeilings({ ...this.edits, secondaries: { ...this.edits.secondaries, [key]: steps } })
    if (steps !== 0) return held
    const { [key]: _, ...rest } = held.secondaries
    return { ...held, secondaries: rest }
  }

  setWealth(status: string | null) {
    this.edits = { ...this.edits, wealth: status }
    this.persist()
  }

  setAppearance(level: string | null) {
    this.edits = { ...this.edits, appearance: level }
    this.persist()
  }

  buyTrait(name: string) {
    if (this.edits.traits.includes(name) || this.buyRefusal(name) !== null) return
    this.edits = { ...this.edits, traits: [...this.edits.traits, name] }
    this.persist()
  }

  buyRefusal(name: string): string | null {
    const row = PurchasableTraits.named(name)
    if (row !== null && PurchasableTraits.PREREQUISITES().has(row.name)) {
      const missing = PurchasableTraits.missingPrerequisite(row, EC.traitsInPlay(this.edits, this.sheet, this.state))
      if (missing !== null) return `needs ${missing}`
    }
    const next = { ...this.edits, traits: [...this.edits.traits, name] }
    const held = EC.withinCeilings(next)
    const over = ([['ST', next.st, held.st], ['DX', next.dx, held.dx], ['HT', next.ht, held.ht]] as const)
      .find(([, a, b]) => a !== b)
    if (!over) return null
    return `bought ${over[0]} ${over[1]} is above the ${over[2]} a holder may buy; lower it first`
  }

  sellTrait(name: string) {
    if (!this.edits.traits.includes(name)) return
    this.edit({ ...this.edits, traits: minusFirst(this.edits.traits, name), gained: minusFirst(this.edits.gained, name) })
  }

  addQuirk(name: string) {
    const quirk = name.trim()
    if (quirk === '' || this.edits.quirks.includes(quirk)) return
    this.edits = { ...this.edits, quirks: [...this.edits.quirks, quirk] }
    this.persist()
  }

  removeQuirk(name: string) {
    if (!this.edits.quirks.includes(name)) return
    this.edits = { ...this.edits, quirks: minusFirst(this.edits.quirks, name) }
    this.persist()
  }

  setGained(name: string, gained: boolean) {
    const bought = this.edits.traits.includes(name) ||
      PurchasableTraits.resolved(this.edits.instanced.map((it) => InstancedPurchase(it))).some(([, t]) => t?.name === name)
    if (gained && !bought) return
    const next = gained ? [...minusFirst(this.edits.gained, name), name] : minusFirst(this.edits.gained, name)
    if (same(next, this.edits.gained)) return
    this.edits = { ...this.edits, gained: next }
    this.persist()
  }

  setCharmedSkill(skill: SkillKey) {
    this.edits = { ...this.edits, charmedSkill: { name: skill.name, specialization: skill.specialization } }
    this.persist()
  }

  canSellTrait(name: string): boolean {
    return this.edits.traits.includes(name) && this.allows({ ...this.edits, traits: minusFirst(this.edits.traits, name) })
  }

  buyInstancedTrait(purchase: SavedInstancedTrait) {
    if (this.edits.instanced.some((it) => instancedKey(it) === instancedKey(purchase))) return
    this.edits = { ...this.edits, instanced: [...this.edits.instanced, purchase] }
    this.persist()
  }

  buyLanguage(language: SavedLanguage) {
    this.edits = EC.withLanguage(this.edits, language)
    this.persist()
  }

  sellLanguage(name: string) {
    const kept = this.edits.languages.filter((it) => it.language.toLowerCase() !== name.toLowerCase())
    if (kept.length === this.edits.languages.length) return
    this.edits = { ...this.edits, languages: kept }
    this.persist()
  }

  sellInstancedTrait(index: number, drawn: SavedInstancedTrait) {
    const at = this.edits.instanced[index]
    if (at === undefined || !same(at, drawn)) return
    const name = PurchasableTraits.resolved([InstancedPurchase(drawn)])[0][1]?.name ?? null
    this.edits = {
      ...this.edits,
      instanced: this.edits.instanced.filter((_, i) => i !== index),
      gained: name === null ? this.edits.gained : minusAll(this.edits.gained, [name]),
    }
    this.persist()
  }

  setStudied(name: string, specialization: string | null, points: number) {
    require(points >= 0, () => `study points cannot be negative; was ${points}`)
    const others = this.edits.studied.filter((it) => !(it.name === name && it.specialization === specialization))
    const next = points === 0 ? others : [...others, SavedSkill(name, specialization, points)]
    if (same(next, this.edits.studied)) return
    this.edits = { ...this.edits, studied: next }
    this.persist()
  }

  buySkill(name: string, specialization: string | null = null) {
    if (this.edits.skills.some((it) => it.name === name && it.specialization === specialization)) return
    this.edits = { ...this.edits, skills: [...this.edits.skills, SavedSkill(name, specialization, 1)] }
    this.persist()
  }

  stepSkill(name: string, specialization: string | null, up: boolean) {
    const bought = this.edits.skills.find((it) => it.name === name && it.specialization === specialization)
    if (!bought) return
    const studied = this.edits.studied.find((it) =>
      it.name.toLowerCase() === name.toLowerCase() && it.specialization === specialization)?.points ?? 0
    this.spendOnSkill(name, specialization, SkillLadder.stepped(bought.points, studied, up) - bought.points)
  }

  spendOnSkill(name: string, specialization: string | null, delta: number) {
    const updated = this.edits.skills.flatMap((skill) => {
      if (skill.name !== name || skill.specialization !== specialization) return [skill]
      const points = skill.points + delta
      return points > 0 ? [{ ...skill, points }] : []
    })
    const gone = !updated.some((it) => it.name === name && it.specialization === specialization)
    this.edits = {
      ...this.edits,
      skills: updated,
      studied: gone
        ? this.edits.studied.filter((it) => !(it.name.toLowerCase() === name.toLowerCase() && it.specialization === specialization))
        : this.edits.studied,
    }
    this.persist()
  }

  get boon(): BoonState | null {
    return this.edits.boon
  }

  nextBoon(patron: string): BoonState | null {
    return this.isLost ? null : NonStandardPathways.nextRung(this.edits.boon, patron)
  }

  grantBoon(next: BoonState) {
    require(same(next, this.nextBoon(next.pathway)), () => `${JSON.stringify(next)} is not the next boon after ${JSON.stringify(this.edits.boon)}`)
    this.edits = { ...this.edits, boon: next }
    this.persist()
  }

  characterFor(sheet: PremadeSheet): Character {
    return EC.applyTo(this.edits, Premades.toCharacter(sheet))
  }

  traitsInPlay(sheet: PremadeSheet): Trait[] {
    return TraitGrants.withGrantedTraits(this.characterFor(sheet).traits, this.state, this.edits.boon)
  }

  canChooseDrawback(sheet: PremadeSheet, name: string): boolean {
    const walked = this.state
    if (walked === null) return false
    return !this.isLost && TraitGrants.drawbackChoices(walked).includes(name) &&
      !this.characterFor(sheet).traits.some((it) => TraitNames.catalogueName(it.name).toLowerCase() === name.toLowerCase())
  }

  chooseDrawback(sheet: PremadeSheet, name: string) {
    require(this.canChooseDrawback(sheet, name), () => `${name} is not a drawback this character may choose`)
    this.state = { ...this.state!, drawback: name }
    this.persist()
  }

  usePower(price: number, maxSpi: number, maxCor: number) {
    const drained = Corruption.drainedUse(maxSpi - this.spiSpent)
    this.spend('SPI', price, maxSpi, maxCor)
    if (drained > 0) this.gainCor(drained, maxCor)
  }

  spend(pool: Pool, delta: number, max: number, maxCor: number) {
    switch (pool) {
      case 'HP':
        this.hpLost = Math.max(this.hpLost + delta, 0)
        break
      case 'FP': {
        const chargedBefore = Math.max(this.fpLost - max, 0)
        this.fpLost = Math.max(this.fpLost + delta, 0)
        const chargedAfter = Math.max(this.fpLost - max, 0)
        this.hpLost = Math.max(this.hpLost + chargedAfter - chargedBefore, 0)
        break
      }
      case 'SPI': {
        const debt = Corruption.soulDebt(max - this.spiSpent, delta)
        this.spiSpent = Math.max(this.spiSpent + delta, 0)
        if (debt > 0) this.gainCor(debt, maxCor)
        break
      }
    }
    this.persist()
  }

  rest(recovery: RestRecovery, drawn: [number, number]): boolean {
    if (this.isLost || drawn[0] !== this.fpLost || drawn[1] !== this.spiSpent) return false
    this.fpLost = recovery.allFp ? 0 : Math.max(this.fpLost - recovery.fp, 0)
    this.spiSpent = Math.max(this.spiSpent - recovery.spi, 0)
    this.persist()
    return true
  }

  gainCor(delta: number, max: number) {
    if (this.isLost) return
    if (Corruption.isLostAfter(this.cor, delta, max)) this.corrupted = true
    this.cor = Corruption.clamp(this.cor + delta, max)
    this.persist()
  }

  digest(delta: number) {
    if (this.isLost) return
    this.state = this.state === null ? null : Beyonder.digested(this.state, delta)
    this.persist()
  }

  act(quality: ActingQuality, drawnDigestion: number) {
    if (this.state?.digestion !== drawnDigestion) return
    if (quality.minGain !== null && !quality.losesProgress) this.digest(quality.minGain)
  }

  stillShows(printed: BeyonderState | null, printedCor: number): boolean {
    return !this.isLost && same(this.state, printed) && this.cor === printedCor
  }

  onSamePotion(printed: BeyonderState | null): boolean {
    return !this.isLost && this.state?.pathway === printed?.pathway && this.state?.sequence === printed?.sequence
  }

  drink(next: BeyonderState, dice: RandomSource, modifier: number, maxCorAfter: (s: BeyonderState) => number): PcrOutcome | null {
    const current = this.state
    require(!this.isLost, () => 'a lost character drinks nothing further')
    require(Beyonder.canDrink(current, next), () => `${describe(next)} is not the next potion after ${describe(current)}`)
    const ceilingAfter = maxCorAfter(next)
    require(ScoreFloors.holdsCeiling(ceilingAfter), () => `${describe(next)} would leave a CoR ceiling of ${ceilingAfter}`)
    if (current === null) {
      this.state = next
      this.gainCor(0, ceilingAfter)
      this.persist()
      return null
    }
    const outcome = PotionConsumption.attempt(current, this.cor, dice, undefined, modifier)
    if (PotionConsumption.integrated(outcome)) {
      this.state = next
      this.gainCor(outcome.corGained, ceilingAfter)
    } else {
      this.lost = true
      this.pendingPcr = next
    }
    this.persist()
    return outcome
  }
}
