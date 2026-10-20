import { describe, expect, test } from 'vitest'
import { canon, cases, replay } from './parity'
import * as Languages from '../rules/Languages'
import { scripted } from './dice'
import * as ContentLoader from '../rules/content/ContentLoader'
import { BeyonderState } from '../rules/model'
import * as Premades from '../rules/Premades'
import type { PremadeSheet } from '../rules/Premades'
import * as Rest from '../rules/Rest'
import { BeyonderSession } from '../app/session/BeyonderSession'
import * as EC from '../app/session/EditedCharacter'
import { cleared, newCharacterRefusal, ownSheets, takenNames } from '../app/session/Characters'
import { decodeSessions, encodeSessions, SavedSession, SerializationException, type SavedSession as Saved } from '../app/session/SessionStore'

const snapshot = (s: BeyonderSession) => SavedSession({ ...s.saved(), basedOn: null })

function derived(s: BeyonderSession, sheet: PremadeSheet) {
  const c = s.characterFor(sheet)
  const printed = Premades.toCharacter(sheet).traits
  return [
    c,
    s.traitsInPlay(sheet),
    EC.grantedSkills(sheet, s.edits, EC.liveSkillLevels(c)),
    EC.carriedWeapons(s.edits),
    EC.ownedLanguages(s.edits, printed),
    EC.pricedWith(s.edits, { language: 'Intis', proficiency: 'Accented', form: Languages.SPOKEN_ONLY }, printed),
    EC.wealthShownFor(s.edits, c),
    EC.attributesWith(c, s.state),
    EC.secondariesWith(c, s.state),
    EC.shortfallOn(s.edits, sheet, s.state),
    EC.corCeilingOn(s.edits, sheet, s.state),
  ]
}

const unit = (_: unknown) => null

function run(s: BeyonderSession, sheet: PremadeSheet, op: any[]): unknown {
  const a = op.slice(1)
  const maxCorFor = (state: BeyonderState | null) => EC.corCeilingOn(s.edits, sheet, state)
  switch (op[0]) {
    case 'setAttribute': return unit(s.setAttribute(a[0], a[1]))
    case 'canSetAttribute': return s.canSetAttribute(a[0], a[1])
    case 'setSecondary': return unit(s.setSecondary(a[0], a[1]))
    case 'canSetSecondary': return s.canSetSecondary(a[0], a[1])
    case 'carryWeapon': return unit(s.carryWeapon(a[0]))
    case 'dropWeapon': return unit(s.dropWeapon(a[0]))
    case 'setWealth': return unit(s.setWealth(a[0]))
    case 'setAppearance': return unit(s.setAppearance(a[0]))
    case 'buyTrait': return unit(s.buyTrait(a[0]))
    case 'buyRefusal': return s.buyRefusal(a[0])
    case 'sellTrait': return unit(s.sellTrait(a[0]))
    case 'canSellTrait': return s.canSellTrait(a[0])
    case 'addQuirk': return unit(s.addQuirk(a[0]))
    case 'removeQuirk': return unit(s.removeQuirk(a[0]))
    case 'setGained': return unit(s.setGained(a[0], a[1]))
    case 'setCharmedSkill': return unit(s.setCharmedSkill(a[0]))
    case 'buyInstancedTrait': return unit(s.buyInstancedTrait(a[0]))
    case 'sellInstancedTrait': return unit(s.sellInstancedTrait(a[0], a[1]))
    case 'buyLanguage': return unit(s.buyLanguage(a[0]))
    case 'sellLanguage': return unit(s.sellLanguage(a[0]))
    case 'setStudied': return unit(s.setStudied(a[0], a[1], a[2]))
    case 'buySkill': return unit(s.buySkill(a[0], a[1]))
    case 'stepSkill': return unit(s.stepSkill(a[0], a[1], a[2]))
    case 'spendOnSkill': return unit(s.spendOnSkill(a[0], a[1], a[2]))
    case 'grantBoon': return unit(s.grantBoon(a[0]))
    case 'chooseDrawback': {
      const can = s.canChooseDrawback(sheet, a[0])
      return [can, unit(s.chooseDrawback(sheet, a[0]))]
    }
    case 'usePower': return unit(s.usePower(a[0], a[1], a[2]))
    case 'spend': return unit(s.spend(a[0], a[1], a[2], a[3]))
    case 'rest': {
      const recovery = Rest.recovery(Rest.named(a[0]), a[1], a[2])
      return s.rest(recovery, a[3] ? [s.fpLost + 1, s.spiSpent] : [s.fpLost, s.spiSpent])
    }
    case 'gainCor': return unit(s.gainCor(a[0], a[1]))
    case 'digest': return unit(s.digest(a[0]))
    case 'act': return unit(s.act(ContentLoader.actingMethod()[a[0]], (s.state?.digestion ?? 0) + (a[1] ? 1 : 0)))
    case 'drink': {
      const next = BeyonderState({ pathway: a[0].pathway, sequence: a[0].sequence })
      return scripted(a[1], (d) => s.drink(next, d, a[2], maxCorFor))
    }
    case 'rerollPcr': return scripted(a[0], (d) => s.rerollPcr(d, a[1], maxCorFor))
  }
  throw new Error(`unknown op ${op[0]}`)
}

function script(sheetName: string, start: Saved, scriptOps: any[]): unknown[] {
  const sheet = Premades.byName(sheetName)
  const emitted: Saved[] = []
  const session = new BeyonderSession(SavedSession(start), sheet, (it) => emitted.push(it))
  const steps: unknown[] = [['init', [...emitted], snapshot(session)]]
  for (const op of scriptOps) {
    emitted.length = 0
    let result: unknown
    try {
      result = { out: run(session, sheet, op) }
    } catch (e) {
      result = { throws: (e as Error).name }
    }
    steps.push([op, result, [...emitted], snapshot(session)])
  }
  steps.push(['derived', derived(session, sheet)])
  return steps
}

describe('the session, step by step', () => {
  // Compared a step at a time, so a divergence names its op, not a 6 MB diff.
  for (const c of cases('session').filter((it) => it.fn === 'session.script')) {
    const [name, seed, start] = c.args as [string, number, Saved]
    test(`${name} #${seed}`, () => {
      const kotlin = c.out as any[]
      const ours = script(name, start, kotlin.slice(1, -1).map((s) => s[0])) as any[]
      for (let i = 0; i < kotlin.length; i++) {
        expect(canon(ours[i]), `step ${i}: ${JSON.stringify(kotlin[i][0])}`).toEqual(kotlin[i])
      }
    })
  }
})

describe('own characters', () => {
  const sheets = Premades.all()
  const toMap = (o: [string, Saved][]) => new Map(o.map(([k, v]) => [k, SavedSession(v)]))
  replay('session', 'ownSheets', (saved) => ownSheets(sheets, toMap(saved)))
  replay('session', 'takenNames', (saved) => takenNames(sheets, toMap(saved)))
  replay('session', 'newCharacterRefusal', (name, saved) => newCharacterRefusal(name, takenNames(sheets, toMap(saved))))
  replay('session', 'cleared', (name, saved) => cleared(toMap(saved).get(name)!))
})


describe('the save codec', () => {
  test('decodes what Kotlin decodes, and refuses what it refuses', () => {
    for (const c of cases('session').filter((it) => it.fn === 'SessionStore.decode')) {
      const text = c.args[0] as string
      let ours: unknown
      try {
        ours = { out: canon([...decodeSessions(text)]) }
      } catch (e) {
        expect(e, text).toBeInstanceOf(SerializationException)
        ours = { throws: true }
      }
      expect(ours, text).toEqual(c.throws ? { throws: true } : { out: c.out })
    }
  })
  test('writes back the text the Android store writes', () => {
    for (const c of cases('session').filter((it) => it.fn === 'SessionStore.roundTrip')) {
      const text = c.args[0] as string
      let loaded = new Map<string, Saved>()
      try {
        loaded = decodeSessions(text)
      } catch {
        // SessionStore.load: an unreadable file reads as no saves.
      }
      expect(encodeSessions(loaded), text).toBe(c.out)
    }
  })
})
