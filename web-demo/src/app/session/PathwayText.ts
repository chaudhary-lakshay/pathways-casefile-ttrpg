// app/.../PathwayText.kt: a potion block's prose cut into its named abilities.
import { WS } from '../../rules/kt'

export interface AbilityParagraph {
  heading: string | null
  lead: string
  items: string[]
}

export const paragraphBody = (p: AbilityParagraph) =>
  p.lead + (p.items.length === 0 ? '' : (p.lead === '' ? '' : ' ') + p.items.join('; '))
export const paragraphText = (p: AbilityParagraph) => (p.heading === null ? '' : `${p.heading}: `) + paragraphBody(p)

export interface Ability {
  head: AbilityParagraph
  parts: AbilityParagraph[]
}

const NAME_WORD = "[A-Z][a-z][\\w'’!\\-]*"
const WORD = "(?:[A-Z][\\w'’!\\-]*|\\([^)]*\\)|\\d+%?)"
const JOIN = '(?:of|the|and|to|a|an|in|on|for|with|from|by|&|or|as|at|vs\\.)'
const SEP = `(?:${WS}+—${WS}+|${WS}*/${WS}*|${WS}+)`
const HEADING_SOURCE = `(?:^|(?<=[.!?)\\]] ))(${NAME_WORD}(?:${SEP}(?:${WORD}|${JOIN})){0,8}): `
const HEADING = () => new RegExp(HEADING_SOURCE, 'g')
const PART = new RegExp('^(?:(?:Critical )?(?:Success|Failure)(?: by \\d+)?|Passive|Duration|Range|Examples?|Note)$')
const TAGGED = new RegExp(`^(.+?)${WS}*\\(([^()]*)\\)$`)
const SPI_PRICE = /(?<![\d–\-+])(\d+) SPI\b/
const TERM = () => new RegExp(
  '\\b\\d+d\\d*(?:[+\\-−]\\d+)?\\b' +
    `|\\b\\d+(?:[–-]\\d+)?${WS}+(?:SPI|HP|FP|CoR)\\b` +
    `|\\b\\d+(?:[–-]\\d+)?(?:${WS}|-)?(?:meters?|m|km|seconds?|minutes?|hours?|turns?|days?)\\b` +
    '|(?<![\\w+\\-−])[+\\-−]\\d+%?',
  'g',
)

/** Kotlin's `Regex.matchAt(text, 0)`. */
function headingAt0(text: string): RegExpExecArray | null {
  const r = new RegExp(HEADING_SOURCE, 'y')
  r.lastIndex = 0
  return r.exec(text)
}

export function abilityParagraphs(text: string): AbilityParagraph[] {
  const starts = [...text.matchAll(HEADING())].map((m) => m.index!)
  const bounds = [...new Set([0, ...starts.filter((it) => it > 0)])]
  return bounds.map((start, i) => {
    const end = i + 1 < bounds.length ? bounds[i + 1] - 1 : text.length
    return paragraph(text.substring(start, end))
  })
}

export function abilities(text: string): Ability[] {
  const grouped: Ability[] = []
  for (const p of abilityParagraphs(text)) {
    const last = grouped[grouped.length - 1]
    if (last && p.heading !== null && PART.test(p.heading)) grouped[grouped.length - 1] = { ...last, parts: [...last.parts, p] }
    else grouped.push({ head: p, parts: [] })
  }
  return grouped
}

export function titleOf(heading: string): [string, string | null] {
  const tagged = TAGGED.exec(heading)
  return tagged ? [tagged[1], tagged[2]] : [heading, null]
}

export const spiPriceOf = (tag: string | null): number | null => {
  const m = tag === null ? null : SPI_PRICE.exec(tag)
  return m ? Number(m[1]) : null
}

export const BULLET = '\u007f'

export const bullets = (text: string) => text.split(BULLET).map((it) => it.trim()).filter((it) => it !== '')

export function named(item: string): [string | null, string] {
  const name = headingAt0(item)
  return name ? [name[1], item.substring(name[0].length)] : [null, item]
}

export const noteOf = (note: string) => (note.startsWith('— ') ? note.slice(2) : note)

export const rejoined = (paragraphs: AbilityParagraph[]) => paragraphs.map(paragraphText).join(' ')

/** Each key term's range, as Kotlin's IntRange: first and last index. */
export const keyTerms = (text: string): { first: number; last: number }[] =>
  [...text.matchAll(TERM())].map((m) => ({ first: m.index!, last: m.index! + m[0].length - 1 }))

const LIST_MIN = 3

function paragraph(run: string): AbilityParagraph {
  const name = headingAt0(run)
  const heading = name ? name[1] : null
  const body = name ? run.substring(name[0].length) : run
  const cuts = topLevel(body, '; ')
  const starts = [0, ...cuts.map((it) => it + 2)]
  const ends = [...cuts, body.length]
  const clauses = starts.map((a, i) => body.substring(a, ends[i]))
  if (clauses.length < LIST_MIN) return { heading, lead: body, items: [] }
  const firstClause = clauses[0]
  const sentences = topLevel(firstClause, '. ')
  const cut = sentences.length ? sentences[sentences.length - 1] : -1
  const lead = cut < 0 ? '' : firstClause.substring(0, cut + 1)
  const opening = cut < 0 ? firstClause : firstClause.substring(cut + 2)
  return { heading, lead, items: [opening, ...clauses.slice(1)] }
}

function topLevel(text: string, separator: string): number[] {
  const cuts: number[] = []
  let depth = 0
  let quoted = false
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    if (c === '(' || c === '[') depth++
    else if (c === ')' || c === ']') depth = Math.max(0, depth - 1)
    else if (c === '"') quoted = !quoted
    if (depth === 0 && !quoted && text.startsWith(separator, i)) cuts.push(i)
  }
  return cuts
}
