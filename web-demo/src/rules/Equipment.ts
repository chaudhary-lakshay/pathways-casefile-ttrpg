import * as ContentLoader from './content/ContentLoader'
import type { EquipmentItem } from './content/schema.gen'
import type { SkillKey } from './model'

export const UNMODELLED = new Map([
  ['Armour',
    'the three rows price DR, and nothing in this app models DR — ' +
    "Damage's own KDoc puts it on the target's side of the hit, " +
    'outside what this engine computes'],
  ['Currency',
    'both the Loen three-tier system and the Feysac decimal one, with ' +
    'their exchange rate, convert a quantity nothing here holds: a ' +
    "wealth level's starting money is a printed STRING and no " +
    'arithmetic anywhere touches money'],
  ['Legal Licenses & Permits',
    'a licence is bought with points OR with pounds, which is a ' +
    'character-creation question, and this table reaches no roll'],
  ['Hired Allies & Services',
    'a rented NPC is a different subject from an owned item, and its ' +
    "column prints the ally's own skill LEVELS rather than a bonus to " +
    "the hirer's"],
])

export const all = (): EquipmentItem[] => ContentLoader.equipment()

export function forSkill(key: SkillKey): EquipmentItem[] {
  const instanceOfTemplate = key.specialization !== null && isTemplate(key.name)
  return all().filter((item) =>
    item.skills.some((it) =>
      it.name === key.name && (it.specialization === key.specialization || (it.specialization === null && instanceOfTemplate)),
    ),
  )
}

const isTemplate = (name: string) =>
  ContentLoader.skills().some((it) => it.name === name && it.specialization === null && it.specializable)

export const referenceFor = (key: SkillKey) => forSkill(key).map((it) => `${it.name} (${it.cost}) — ${it.notes}`)
