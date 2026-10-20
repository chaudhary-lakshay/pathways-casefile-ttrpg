// GalleryRows.kt / PremadeGallery.kt / SheetHeader.kt: what the character list
// and a sheet's header print, as pure functions. Components only render these.
import { containsIc, substringBefore, trim } from '../rules/kt'
import type { Attributes, BeyonderState } from '../rules/model'
import type { PremadeSheet } from '../rules/Premades'
import * as Premades from '../rules/Premades'
import * as TraitGrants from '../rules/TraitGrants'
import { isTemplate, newCharacterRefusal } from './session/Characters'
import { applyTo, attributesWith, liveSkillLevels, repairedEdits } from './session/EditedCharacter'
import type { SavedSession } from './session/SessionStore'

export type Roster = 'ALL' | 'MORTAL' | 'BEYONDER'
export const ROSTERS: [Roster, string][] = [['ALL', 'All'], ['MORTAL', 'Mortal'], ['BEYONDER', 'Beyonders']]

export const walkOf = (sheet: PremadeSheet, saved: Map<string, SavedSession>): BeyonderState | null =>
  saved.get(sheet.name)?.beyonder ?? null

export interface PlayedRow {
  attributes: Attributes
  skills: number
  chips: string[]
}

export function playedRow(sheet: PremadeSheet, saved: SavedSession | null): PlayedRow {
  const walk = saved?.beyonder ?? null
  const base = Premades.toCharacter(sheet)
  const bought = saved ? applyTo(repairedEdits(saved, sheet), base) : base
  return {
    attributes: attributesWith(bought, walk),
    skills: liveSkillLevels({ ...bought, beyonder: walk }).size,
    chips: chipTraits(TraitGrants.withGrantedTraits(bought.traits, walk, bought.boon).map((it) => it.name)),
  }
}

/** A save that does not digest shows the sheet's row (Android logs a warning). */
export function rowFor(sheet: PremadeSheet, saved: SavedSession | null): PlayedRow {
  try {
    return playedRow(sheet, saved)
  } catch (failure) {
    console.warn(`${sheet.name}'s save does not digest; its row is the sheet's`, failure)
    return playedRow(sheet, null)
  }
}

export function rosterOf(roster: Roster, people: PremadeSheet[], saved: Map<string, SavedSession>): PremadeSheet[] {
  switch (roster) {
    case 'ALL':
      return people
    case 'MORTAL':
      return people.filter((it) => walkOf(it, saved) === null)
    case 'BEYONDER':
      return people.filter((it) => walkOf(it, saved) !== null)
  }
}

export const chipTraits = (names: string[]): string[] =>
  names
    .map((it) => trim(substringBefore(substringBefore(substringBefore(it, ' - '), ' — '), ' −')))
    .filter((it) => it.length >= 1 && it.length <= 32)
    .slice(0, 2)

export function galleryNote(row: PlayedRow, spoken: boolean): string {
  const chips = row.chips.join(', ')
  return [...(chips !== '' ? [chips] : []), `${row.skills} ${spoken ? 'skills' : 'sk.'}`].join(' · ')
}

export const walkStamp = (walk: BeyonderState | null) => (walk ? `${walk.pathway} ${walk.sequence}` : 'Mortal')
export const isStrong = (value: number) => value >= 10

export const nextFileNumber = (sheets: PremadeSheet[]) => sheets.filter((it) => !isTemplate(it)).length + 1

export function fileNumber(sheet: PremadeSheet, sheets: PremadeSheet[]): number | null {
  const at = sheets.filter((it) => !isTemplate(it)).findIndex((it) => it.name === sheet.name)
  return at >= 0 ? at + 1 : null
}
export const fileDigits = (n: number) => String(n).padStart(2, '0')
export const fileLabel = (n: number | null) => (n === null ? null : `File Nº ${fileDigits(n)}`)
export const fileLine = (n: number | null, isBeyonder: boolean) =>
  [fileLabel(n), isBeyonder ? 'Beyonder' : 'Mortal'].filter((it) => it !== null).join(' · ')

export type PillInk = 'LOST' | 'STAMP' | 'INK' | 'MUTE'
export interface HeaderFact {
  text: string
  ink: PillInk
}

export function headerFacts(drunk: BeyonderState | null, boon: string | null, cor: number, lost: boolean, corrupted: boolean): HeaderFact[] {
  if (drunk === null && boon === null) return []
  const facts: HeaderFact[] = [
    ...(lost ? [{ text: 'Lost to the Rampager', ink: 'LOST' as const }] : corrupted ? [{ text: 'Lost to corruption', ink: 'LOST' as const }] : []),
    ...(drunk ? [{ text: `${drunk.pathway} · Seq ${drunk.sequence}`, ink: 'STAMP' as const }, { text: `Digested ${drunk.digestion}%`, ink: 'INK' as const }] : []),
    ...(boon !== null ? [{ text: boon, ink: 'INK' as const }] : []),
    { text: `CoR ${cor}`, ink: 'MUTE' },
  ]
  return lost || corrupted ? facts.map((it) => ({ ...it, ink: 'LOST' })) : facts
}

// NewCharacterDialog.kt

const ATTRS = ['ST', 'DX', 'IQ', 'HT'] as const

export function blankHint(blank: PremadeSheet): string {
  const levels = ATTRS.map((it) => [it, blank.attributes.get(it)!] as const)
  const distinct = [...new Set(levels.map(([, l]) => l))]
  return distinct.length === 1
    ? `Blank sheet starts every attribute at ${distinct[0]}.`
    : `Blank sheet starts at ${levels.map(([a, l]) => `${a} ${l}`).join(', ')}.`
}

export const templateChoices = (bases: PremadeSheet[], query: string) =>
  bases.filter((it) => !isTemplate(it) && containsIc(it.name, trim(query)))

export const createRefusal = (name: string, taken: Set<string>, base: PremadeSheet | null): string | null =>
  newCharacterRefusal(name, taken) ?? (base === null ? 'Pick a template.' : null)
