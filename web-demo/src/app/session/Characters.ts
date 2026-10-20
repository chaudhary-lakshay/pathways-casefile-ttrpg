// app/.../Characters.kt: the player's own characters, each a save naming its premade.
import { eqIc, isBlank, trim } from '../../rules/kt'
import type { PremadeSheet } from '../../rules/Premades'
import * as PurchasableTraits from '../../rules/PurchasableTraits'
import { InstancedPurchase } from '../../rules/PurchasableTraits'
import { minusAll } from './EditedCharacter'
import { SavedSession, type SavedEdits } from './SessionStore'

export const BLANK_SHEET = 'LOTM Blank Character Sheet'

export function ownSheets(premades: PremadeSheet[], saved: Map<string, SavedSession>): PremadeSheet[] {
  const byName = new Map(premades.map((it) => [it.name, it]))
  return [...saved].flatMap(([name, entry]) => {
    if (byName.has(name) || entry.basedOn === null) return []
    const base = byName.get(entry.basedOn)
    return base ? [{ ...base, name }] : []
  })
}

export const takenNames = (sheets: PremadeSheet[], saved: Map<string, SavedSession>): Set<string> =>
  new Set([...sheets.map((it) => it.name), ...saved.keys()])

export function newCharacterRefusal(name: string, taken: Set<string>): string | null {
  if (isBlank(name)) return 'Name the character.'
  if ([...taken].some((it) => eqIc(it, trim(name)))) return 'That name is taken.'
  return null
}

export const isTemplate = (sheet: PremadeSheet) => sheet.name === BLANK_SHEET

/** `SavedSession.cleared()`: back to the sheet, keeping only what was built. */
export const cleared = (s: SavedSession): SavedSession =>
  s.basedOn === null ? SavedSession() : SavedSession({ edits: built(s.edits), basedOn: s.basedOn })

function built(e: SavedEdits): SavedEdits {
  const names = PurchasableTraits.resolved(e.instanced.map((it) => InstancedPurchase(it))).map(([, t]) => t?.name ?? null)
  return {
    ...e,
    traits: minusAll(e.traits, e.gained),
    instanced: e.instanced.filter((_, at) => !e.gained.includes(names[at] as string)),
    gained: [],
    studied: [],
    quirks: [],
    boon: null,
  }
}
