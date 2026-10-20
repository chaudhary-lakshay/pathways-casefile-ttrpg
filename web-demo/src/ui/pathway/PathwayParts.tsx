// PathwayHeader.kt, PathwayPotion.kt and PathwayTrackers.kt: the pathway
// screen's pieces. The screen itself is PathwayScreen.tsx.
import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { corLadder, digestionCells, formatDelta, pcrLine, spendLabel } from '../../app/PathwayModel'
import {
  abilities, abilityParagraphs, BULLET, bullets, keyTerms, named, noteOf, paragraphBody, spiPriceOf, titleOf,
  type Ability, type AbilityParagraph,
} from '../../app/session/PathwayText'
import type { BeyonderSession } from '../../app/session/BeyonderSession'
import type { ActingQuality, SequenceBlock } from '../../rules/content/schema.gen'
import * as ContentLoader from '../../rules/content/ContentLoader'
import * as Corruption from '../../rules/Corruption'
import { substringAfter, substringBefore, substringAfterLast } from '../../rules/kt'
import { attrCode, DIFFICULTY, SkillKey, type BeyonderState, type Trait } from '../../rules/model'
import * as PotionDigestion from '../../rules/PotionDigestion'
import type { DigestedCharacter } from '../../rules/PotionDigestion'
import * as TraitEffects from '../../rules/TraitEffects'
import * as TraitReport from '../../rules/TraitReport'
import { Icon } from '../icons'
import { BoxedIconButton, BracketAction, Caps, CaseChip, Chips, ChipTag, InkAction, LedgerCard, OptionStepper, RuleNote, SectionLabel, Stamp, StepperCard } from '../kit'
import { LevelBox, pathwayArt } from '../sheet/SheetParts'

export function PathwayBar({ name, pathway, onBack }: { name: string; pathway: string | null; onBack: () => void }) {
  return (
    <div className="row pathway-bar">
      <BoxedIconButton icon="arrowBack" label="Back to sheet" onClick={onBack} />
      <span className="grow" style={{ paddingLeft: 16 }}>
        <Caps text={pathway !== null ? `${name} · ${pathway} Pathway` : `${name} · Choose a pathway`} className="labelMedium" />
      </span>
    </div>
  )
}

export function PotionHeading({ block }: { block: SequenceBlock }) {
  const art = pathwayArt(block.pathway)
  return (
    <div className="row potion-heading">
      {art && <img className="potion-art" src={art} alt={`${block.pathway} Pathway`} />}
      <div className="grow" style={{ paddingLeft: 20 }}>
        <FitText className="displaySmall" min={16} lines={1} as="h1">{block.name}</FitText>
        <p className="labelLarge mono muted">Sequence {block.sequence} · {block.pathway} Pathway</p>
      </div>
    </div>
  )
}

/**
 * `TextAutoSize` and WholeWords: the largest size, stepping down 1 px, at which
 * the text fits its lines with no word cut. A browser never breaks inside a
 * word, so a cut word shows as overflow.
 */
function FitText({ children, className, min, lines, as: Tag = 'span' }: { children: string; className: string; min: number; lines: number; as?: 'span' | 'h1' }) {
  const ref = useRef<HTMLElement>(null)
  const [size, setSize] = useState<number | null>(null)
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    el.style.fontSize = ''
    let px = parseFloat(getComputedStyle(el).fontSize)
    const fits = () => el.scrollWidth <= el.clientWidth && el.scrollHeight <= Math.ceil(parseFloat(getComputedStyle(el).lineHeight) * lines) + 1
    while (px > min && !fits()) {
      px -= 1
      el.style.fontSize = `${px}px`
      el.style.lineHeight = '1.2'
    }
    setSize(px)
  }, [children, min, lines])
  return (
    <Tag ref={ref as never} className={`${className} fit-text`} style={size !== null ? { fontSize: size, lineHeight: 1.2, WebkitLineClamp: lines } : { WebkitLineClamp: lines }}>
      {children}
    </Tag>
  )
}

export function SequenceTile({ sequence, potion, selected, drunk, onClick }: { sequence: number; potion: string; selected: boolean; drunk: boolean; onClick: () => void }) {
  return (
    <button role="tab" aria-selected={selected} className={`sequence-tile${selected ? ' selected' : ''}`} onClick={onClick}>
      <span className="row" style={{ justifyContent: 'center' }}>
        <Caps text={`Seq ${sequence}`} color={selected ? 'var(--on-primary)' : 'var(--on-surface-variant)'} />
        {drunk && <span style={{ paddingLeft: 4 }}><Icon name="check" size={14} label="drunk" /></span>}
      </span>
      <FitText className="bodyLarge" min={10} lines={2}>{potion}</FitText>
    </button>
  )
}

export function DigestionBar({ digestion }: { digestion: number }) {
  const filled = digestionCells(digestion)
  return (
    <div className="digestion-bar" role="progressbar" aria-label={`Digestion ${digestion}%`} aria-valuenow={digestion} aria-valuemin={0} aria-valuemax={100}>
      <div className="row labelLarge mono">
        <span className="grow">Digestion</span>
        <b>{digestion}%</b>
      </div>
      <div className="cells">
        {Array.from({ length: 10 }, (_, i) => <span key={i} className={i < filled ? 'on' : ''} />)}
      </div>
    </div>
  )
}

export function DrinkButton({ label, enabled, onClick }: { label: string; enabled: boolean; onClick: () => void }) {
  return (
    <button className="ink-button drink-button" disabled={!enabled} onClick={onClick}>
      <Icon name="science" size={18} />
      <span className="labelLarge mono caps">{label}</span>
    </button>
  )
}

export function PathwayTabs({ tab, onTab }: { tab: number; onTab: (t: number) => void }) {
  return (
    <div className="pathway-tabs" role="radiogroup">
      {['Potion', 'Digestion & corruption'].map((label, i) => (
        <button key={label} role="radio" aria-checked={tab === i} className={`segment${tab === i ? ' open' : ''}`} onClick={() => onTab(i)}>
          <Caps text={label} className="labelLarge mono" color="currentColor" />
        </button>
      ))}
    </div>
  )
}

export function PathwayTile({ name, onClick }: { name: string; onClick: () => void }) {
  const art = pathwayArt(name)
  return (
    <button className="pathway-tile" onClick={onClick}>
      {art && <img src={art} alt="" width={64} height={64} />}
      <span className="labelLarge">{name}</span>
    </button>
  )
}

export const DashedRule = () => <hr className="dashed-rule" />

export function Note({ text }: { text: string }) {
  return <p className="bodySmall" style={{ padding: 16 }}>{text}</p>
}

// ---- Potion ----

export interface AbilitySpend {
  left: number
  onSpend: (price: number, drawnLeft: number) => void
}

export function PotionContent({ block, digested, drawbackPicker, spend }: {
  block: SequenceBlock
  digested: DigestedCharacter
  drawbackPicker: ReactNode | null
  spend: AbilitySpend | null
}) {
  return (
    <>
      <LedgerCard title="Gains">
        <div className="chips" style={{ gap: 8 }}>
          {block.stats.length === 0 && <span className="bodyMedium">No stat gains</span>}
          {block.stats.map((it) => <Stamp key={it.attr} text={`${it.attr} ${formatDelta(it.delta)}`} color="var(--secondary)" />)}
          {digested.grants.spi !== 0 && <ChipTag text={`SPI after digestion ${digested.effectiveSpi}`} />}
        </div>
        {block.stats.filter((it) => it.note !== '').map((it) => <p key={it.attr} className="bodySmall" style={{ paddingTop: 6 }}>{it.attr} {it.note}</p>)}
        {block.sequence < PotionDigestion.STARTING_SEQUENCE && (
          <p className="bodySmall muted" style={{ paddingTop: 8 }}>
            Levels and totals count every potion from Sequence {PotionDigestion.STARTING_SEQUENCE} down to {block.sequence}.
          </p>
        )}
      </LedgerCard>
      {(block.skills.length > 0 || block.unresolvedSkills.length > 0) && (
        <LedgerCard title="Skills">
          {block.skills.map((grant, i) => {
            const spec = grant.name.includes(' (') ? substringAfter(grant.name, ' (').replace(/\)$/, '') : null
            const granted = SkillKey(substringBefore(grant.name, ' ('), spec, grant.attr, grant.difficulty)
            const level = [...digested.grants.skillLevels.entries()].find(([k]) => PotionDigestion.isSameSkill(k, granted))?.[1]
            return (
              <div key={grant.name}>
                {i > 0 && <DashedRule />}
                <SkillLine name={grant.name} note={noteOf(grant.note)} attr={`${attrCode(grant.attr)}/${DIFFICULTY[grant.difficulty].code}`} value={level !== undefined ? String(level) : `+${grant.bonus}`} />
              </div>
            )
          })}
          {block.unresolvedSkills.map((printed, i) => (
            <div key={printed}>
              {(i > 0 || block.skills.length > 0) && <DashedRule />}
              <SkillLine name={substringBefore(printed, '/')} note="names no skill the book lists; the GM assigns it" attr="" value={substringAfterLast(printed, ' ')} />
            </div>
          ))}
        </LedgerCard>
      )}
      {block.advantages !== '' && <LedgerCard title="Advantages"><BookText text={block.advantages} /></LedgerCard>}
      {block.drawbacks !== '' && (
        <LedgerCard title="Drawbacks">
          <div className="col" style={{ gap: 8 }}>
            {abilityParagraphs(block.drawbacks).map((it, i) => <BookText key={i} text={paragraphBody(it)} label={it.heading} />)}
          </div>
          {drawbackPicker}
        </LedgerCard>
      )}
      <Abilities title="Abilities" text={block.ability} spend={spend} />
      {block.reinforcedAbilities !== '' && <Abilities title="Reinforced abilities" text={block.reinforcedAbilities} spend={spend} />}
    </>
  )
}

export function DrawbackPicker({ choices, chosen, pathway, canChoose, onChoose }: {
  choices: string[]
  chosen: string | null
  pathway: string
  canChoose: (name: string) => boolean
  onChoose: (name: string) => void
}) {
  return (
    <div style={{ paddingTop: 8 }}>
      <Chips>
        {choices.map((name) => {
          const selected = name === chosen
          const enabled = selected || canChoose(name)
          const label = name.replace('(pathway)', `(${pathway})`)
          return (
            <CaseChip key={name} selected={selected} disabled={!enabled} onClick={() => onChoose(name)}>
              {enabled ? label : `${label} · bought`}
            </CaseChip>
          )
        })}
      </Chips>
    </div>
  )
}

function SkillLine({ name, note, attr, value }: { name: string; note: string; attr: string; value: string }) {
  return (
    <div className="row" style={{ padding: '10px 0' }}>
      <div className="grow">
        <p className="bodyLarge">{name}</p>
        {note !== '' && <p className="bodySmall muted">{note}</p>}
      </div>
      <span className="labelSmall muted" style={{ padding: '0 12px', whiteSpace: 'nowrap' }}>{attr}</span>
      <LevelBox number={value} />
    </div>
  )
}

const OPEN_AT_MOST = 2

export function Abilities({ title, text, spend = null }: { title: string; text: string; spend?: AbilitySpend | null }) {
  const all = abilities(text)
  const namedOnes = all.flatMap((it, i) => (it.head.heading !== null ? [i] : []))
  const [open, setOpen] = useState<number[]>(() => (namedOnes.length <= OPEN_AT_MOST ? namedOnes : []))
  const allOpen = namedOnes.every((i) => open.includes(i))
  return (
    <LedgerCard
      title={title}
      action={namedOnes.length > OPEN_AT_MOST ? <BracketAction label={allOpen ? 'Close all' : 'Open all'} onClick={() => setOpen(allOpen ? [] : namedOnes)} /> : undefined}
    >
      {all.map((ability, i) => (
        <div key={i}>
          {i > 0 && <DashedRule />}
          <AbilityRow ability={ability} expanded={open.includes(i)} spend={spend} onToggle={() => setOpen(open.includes(i) ? open.filter((it) => it !== i) : [...open, i])} />
        </div>
      ))}
    </LedgerCard>
  )
}

function AbilityRow({ ability, expanded, spend, onToggle }: { ability: Ability; expanded: boolean; spend: AbilitySpend | null; onToggle: () => void }) {
  const heading = ability.head.heading
  if (heading === null) return <AbilityText ability={ability} />
  const [name, tag] = titleOf(heading)
  const price = spiPriceOf(tag)
  return (
    <>
      <button className="ability-row" onClick={onToggle} aria-expanded={expanded}>
        <span className="titleMedium grow">{name}</span>
        {tag !== null && <span className="ability-tag labelMedium mono">{tag}</span>}
        <Icon name={expanded ? 'expandLess' : 'expandMore'} />
      </button>
      {expanded && (
        <>
          <AbilityText ability={ability} />
          {spend !== null && price !== null && (
            <div style={{ paddingBottom: 12 }}>
              <InkAction label={spendLabel(price, spend.left)} onClick={() => spend.onSpend(price, spend.left)} />
            </div>
          )}
        </>
      )}
    </>
  )
}

function AbilityText({ ability }: { ability: Ability }) {
  return (
    <div className="col" style={{ paddingBottom: 12, gap: 8 }}>
      <Paragraph paragraph={ability.head} label={null} />
      {ability.parts.map((it, i) => <Paragraph key={i} paragraph={it} label={it.heading} />)}
    </div>
  )
}

function Paragraph({ paragraph, label }: { paragraph: AbilityParagraph; label: string | null }) {
  const pieces = bullets(paragraph.lead)
  const leadIn = paragraph.lead.trimStart().startsWith(BULLET) ? null : pieces[0] ?? null
  const listed = [...pieces.slice(leadIn === null ? 0 : 1), ...paragraph.items.flatMap(bullets)]
  return (
    <div style={{ paddingLeft: label === null ? 0 : 12 }}>
      {(label !== null || leadIn !== null) && <BookText text={leadIn ?? ''} label={label} />}
      {listed.map((item, i) => {
        const [itemName, rest] = named(item)
        return (
          <div key={i} className="row" style={{ alignItems: 'flex-start', padding: '4px 0 0 4px' }}>
            <span className="bodyMedium" style={{ color: 'var(--primary)' }}>•&nbsp;&nbsp;</span>
            <BookText text={rest} label={itemName} />
          </div>
        )
      })}
    </div>
  )
}

function BookText({ text, label = null }: { text: string; label?: string | null }) {
  const out: ReactNode[] = []
  let at = 0
  for (const r of keyTerms(text)) {
    out.push(text.slice(at, r.first))
    out.push(<span key={r.first} className="key-term">{text.slice(r.first, r.last + 1)}</span>)
    at = r.last + 1
  }
  out.push(text.slice(at))
  return (
    <p className="bodyMedium book-text">
      {label !== null && <><b>{label}</b>{'  '}</>}
      {out}
    </p>
  )
}

// ---- Trackers ----

export function DigestionTracker({ state, cor, traits, title, onDigest, onAct, onActingMethod, onStartingRoll, pcrModifier }: {
  state: BeyonderState
  cor: number
  traits: Trait[]
  title: string
  onDigest: (delta: number) => void
  onAct: (q: ActingQuality, drawnDigestion: number) => void
  onActingMethod: (drawnDigestion: number) => void
  onStartingRoll: (drawnDigestion: number) => void
  pcrModifier: number
}) {
  const drawn = state.digestion
  return (
    <StepperCard title={title} minus="-5%" plus="+5%" value={`${state.digestion}%`} onMinus={() => onDigest(-5)} onPlus={() => onDigest(5)}>
      <p className="bodySmall mono muted" style={{ minHeight: 40 }}>next potion rolls {pcrLine(state, cor, pcrModifier)}</p>
      <SectionLabel text="Acting" />
      <div className="chips" style={{ gap: 8 }}>
        {ContentLoader.actingMethod().map((q) =>
          q.losesProgress ? (
            <ChipTag key={q.quality} text={`${q.quality} 0 or lose`} />
          ) : (
            <button key={q.quality} className="outlined-button labelLarge mono" onClick={() => onAct(q, drawn)}>
              {q.quality} +{q.minGain}-{q.maxGain}%
            </button>
          ),
        )}
      </div>
      <OptionStepper label="Digestion" value={state.digestion} shown={`${state.digestion}%`} note="Earned CP: 1 point buys 1%" onStep={onDigest} />
      <InkAction label="Starting Beyonder: roll 4d20%" onClick={() => onStartingRoll(drawn)} disabled={state.digestion !== 0} />
      {TraitEffects.rollsActingMethod(traits) && (
        <div style={{ paddingTop: 8 }}>
          <InkAction label={`${TraitEffects.ACTING_METHOD}: roll 3d6%`} onClick={() => onActingMethod(drawn)} />
        </div>
      )}
    </StepperCard>
  )
}

export function CorruptionTracker({ session, drunk, max, traits }: { session: BeyonderSession; drunk: BeyonderState; max: number; traits: Trait[] }) {
  const step = (delta: number) => { if (session.onSamePotion(drunk)) session.gainCor(delta, max) }
  const cor = session.cor
  const atRisk = session.corrupted || cor >= max - 1
  return (
    <StepperCard title="Corruption" minus="−1" plus="+1" value={`${cor} / ${max}`} onMinus={() => step(-1)} onPlus={() => step(1)} disabled={session.isLost} valueColor={atRisk ? 'var(--error)' : 'var(--on-surface)'}>
      <p className="bodySmall mono" style={{ color: session.corrupted ? 'var(--error)' : 'var(--on-surface-variant)' }}>
        {session.corrupted
          ? 'At maximum CoR the character is permanently lost. The GM takes full control.'
          : `maximum CoR is ${max}; reaching it removes the character from play`}
      </p>
      <SectionLabel text="From ritual work" />
      {corLadder(cor).map(([threshold, reached]) => (
        <div key={threshold.cor}>
          <div className="row" style={{ alignItems: 'flex-start', padding: '8px 0' }}>
            <span className="labelLarge mono semibold" style={{ width: 72, flex: 'none', color: reached ? 'var(--error)' : 'var(--on-surface-variant)' }}>{threshold.cor} CoR</span>
            <span className="bodySmall mono" style={{ color: reached ? 'var(--on-surface)' : 'var(--on-surface-variant)' }}>{threshold.effect}</span>
          </div>
          <DashedRule />
        </div>
      ))}
      <div className="dashed-box">
        <RuleNote title="What raises corruption">
          {Corruption.gained().map((it) => (
            <div key={it.action} className="row bodyMedium" style={{ padding: '2px 0', alignItems: 'flex-start' }}>
              <span className="grow">{it.action}</span>
              <span style={{ color: 'var(--tertiary)' }}>{it.gain}</span>
            </div>
          ))}
        </RuleNote>
      </div>
      {TraitReport.corruptionNotes(traits).map((it) => (
        <p key={it.trait} className="bodySmall" style={{ paddingTop: 4 }}>{it.trait}: {it.printed} Not halved here: {it.reason}.</p>
      ))}
    </StepperCard>
  )
}
