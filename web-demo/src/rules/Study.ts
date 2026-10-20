import { eqIc, error, IllegalStateException, require } from './kt'
import * as ContentLoader from './content/ContentLoader'
import type { StudyCost, StudyPrice, StudyRule } from './content/schema.gen'
import type { Difficulty } from './model'
import * as SkillLadder from './SkillLadder'

export type StudyMethod = 'TUTOR' | 'SELF_STUDY'
export const STUDY_METHODS: StudyMethod[] = ['TUTOR', 'SELF_STUDY']
export const METHOD_LABEL: Record<StudyMethod, string> = { TUTOR: 'With a Tutor', SELF_STUDY: 'Self-Study' }

export interface StudyPlan {
  pointsNeeded: number
  toLevel: number
  hours: number
  amount: number
  unit: string
  perPoint: StudyPrice
}

export const printedPlan = (p: StudyPlan) => `${p.hours} hrs + ` + (p.unit === '£' ? `£${p.amount}` : `${p.amount}${p.unit}`)

export type StudyRefusal = 'POTION_GRANTED' | 'NOT_POSSIBLE' | 'AT_CAP'

export class StudyRefusedException extends IllegalStateException {
  constructor(readonly reason: string, readonly refusal: StudyRefusal) {
    super(reason)
    this.name = 'StudyRefusedException'
  }
}

/** Kotlin's `Result<StudyPlan>`. */
export type StudyResult = { ok: true; plan: StudyPlan } | { ok: false; error: StudyRefusedException }

export const CAP_ABOVE_ATTRIBUTE = 1

export const costs = (): StudyCost[] => ContentLoader.studyCosts()
export const rules = (): StudyRule[] => ContentLoader.studyRules()
export const capFor = (attributeLevel: number) => attributeLevel + CAP_ABOVE_ATTRIBUTE

const LABEL: Record<Difficulty, string> = { EASY: 'Easy', AVERAGE: 'Average', HARD: 'Hard', VERY_HARD: 'Very Hard' }

export const rowFor = (d: Difficulty): StudyCost =>
  costs().find((it) => eqIc(it.difficulty, LABEL[d])) ?? error(`the study table prints no row for ${LABEL[d]}`)

export const cellFor = (d: Difficulty, m: StudyMethod): StudyPrice => (m === 'TUTOR' ? rowFor(d).tutor : rowFor(d).selfStudy)

export const isPriced = (d: Difficulty, m: StudyMethod) => cellFor(d, m).hours !== null

export function costPerPoint(d: Difficulty, m: StudyMethod): StudyPrice {
  const cell = cellFor(d, m)
  require(cell.hours !== null, () => `the book prints '${cell.printed}' for ${LABEL[d]} by ${METHOD_LABEL[m]}; there is no cost to quote`)
  return cell
}

const refuse = (cause: StudyRefusal, reason: string): StudyResult => ({ ok: false, error: new StudyRefusedException(reason, cause) })

export function plan(points: number, d: Difficulty, attributeLevel: number, m: StudyMethod, isPotionGranted = false): StudyResult {
  require(points >= 0, () => `points spent cannot be negative; was ${points}`)
  if (isPotionGranted) return refuse('POTION_GRANTED', 'A potion granted this skill, and the book does not let it be studied.')
  const cell = cellFor(d, m)
  if (cell.hours === null) {
    return refuse('NOT_POSSIBLE', `The book prints "${cell.printed}" for a ${LABEL[d]} skill by ${METHOD_LABEL[m].toLowerCase()}.`)
  }
  const cap = capFor(attributeLevel)
  const current = points === 0 ? null : SkillLadder.levelFor(points, d, attributeLevel)
  if (current !== null && current >= cap) {
    return refuse('AT_CAP', `Study alone cannot pass ${cap}, and this skill is already ${current}.`)
  }
  const nextPoints = SkillLadder.nextRungPoints(points)
  const toLevel = SkillLadder.levelFor(nextPoints, d, attributeLevel)
  if (toLevel > cap) return refuse('AT_CAP', `The next rung is ${toLevel}, and study alone cannot pass ${cap}.`)
  const needed = nextPoints - points
  return {
    ok: true,
    plan: {
      pointsNeeded: needed,
      toLevel,
      hours: cell.hours * needed,
      amount: (cell.amount ?? 0) * needed,
      unit: cell.unit ?? 's',
      perPoint: cell,
    },
  }
}
