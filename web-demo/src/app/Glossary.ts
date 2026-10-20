// GlossaryScreen.kt `glossaryGroups`: the shown terms, in runs by first letter.
import type { GlossaryEntry } from '../rules/content/schema.gen'
import { containsIc, trim } from '../rules/kt'

export interface GlossaryGroup {
  letter: string
  entries: GlossaryEntry[]
}

export function glossaryGroups(entries: GlossaryEntry[], query: string): GlossaryGroup[] {
  const groups: GlossaryGroup[] = []
  for (const entry of entries.filter((it) => containsIc(it.term, trim(query)))) {
    const letter = entry.term.charAt(0).toUpperCase()
    const last = groups[groups.length - 1]
    if (last?.letter === letter) last.entries = [...last.entries, entry]
    else groups.push({ letter, entries: [entry] })
  }
  return groups
}
