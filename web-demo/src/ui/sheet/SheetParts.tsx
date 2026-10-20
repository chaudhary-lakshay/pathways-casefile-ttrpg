// SheetHeader.kt (SheetTitle, pills), SheetBackdrop.kt + PathwayArt.kt,
// PoolStrip.kt, SheetActions.kt + ActionGroup.kt, SheetTabBar.kt,
// TraitRows.kt SheetSection and VitalsTable.kt TableHead.
import { useState, type ReactNode } from 'react'
import { type HeaderFact } from '../../app/Gallery'
import type { SheetTab } from '../../app/RollLog'
import { poolLine, poolTags, spelledPool, twoDigits } from '../../app/SheetText'
import type { BeyonderState } from '../../rules/model'
import type { PoolStatus } from '../../rules/Pools'
import type { Pool } from '../../rules/Pools'
import * as SpiritVision from '../../rules/SpiritVision'
import { Icon, type IconName } from '../icons'
import { BracketAction, Caps, Stamp } from '../kit'

const ART = import.meta.glob<string>('@res/drawable/pathway_*.webp', { eager: true, import: 'default', query: '?url' })

/** PathwayArt.kt: the 22 pathways with art, by file name. */
export function pathwayArt(pathway: string): string | null {
  const file = `pathway_${pathway.toLowerCase().replace(/ /g, '_')}.webp`
  const key = Object.keys(ART).find((k) => k.endsWith(`/${file}`))
  return key ? ART[key] : null
}

export function SheetBackdrop({ pathway }: { pathway: string | null }) {
  const art = pathway ? pathwayArt(pathway) : null
  if (!art) return null
  return <img className="sheet-backdrop" src={art} alt="" aria-hidden="true" />
}

export function SheetTitle({ name, facts }: { name: string; facts: HeaderFact[] }) {
  return (
    <div className="sheet-title">
      <h1 className="headlineLarge">{name}</h1>
      {facts.length > 0 && (
        <div className="pills">
          {facts.map((f, i) => <HeaderPill key={i} fact={f} />)}
        </div>
      )}
    </div>
  )
}

function HeaderPill({ fact }: { fact: HeaderFact }) {
  switch (fact.ink) {
    case 'STAMP':
      return <Stamp text={fact.text} color="var(--secondary)" small />
    case 'LOST':
      return <span className="pill lost"><Caps text={fact.text} className="labelSmall semibold" color="var(--on-error)" /></span>
    case 'INK':
      return <span className="pill ink"><Caps text={fact.text} className="labelSmall semibold" color="var(--on-surface)" /></span>
    case 'MUTE':
      return <span className="pill mute"><Caps text={fact.text} className="labelSmall semibold" /></span>
  }
}

export function PoolStrip({ pools, onSpend, stacked }: { pools: PoolStatus[]; onSpend: (pool: Pool, n: number) => void; stacked: boolean }) {
  const [opened, setOpened] = useState<Pool | null>(null)
  const toggle = (p: Pool) => setOpened(opened === p ? null : p)
  const open = pools.find((it) => it.pool === opened && poolTags(it).length > 0)
  return (
    <div className="pool-strip">
      {stacked ? (
        pools.map((s, i) => (
          <div key={s.pool}>
            {i > 0 && <hr className="rule" />}
            <div className="pool-row">
              <div className="row" style={{ alignItems: 'flex-start' }}>
                <TagsTap status={s} onTags={() => toggle(s.pool)} className="grow col" style={{ gap: 6 }}>
                  <PoolLabel name={spelledPool(s.pool)} status={s} />
                  <PoolCount status={s} className="headlineMedium" />
                </TagsTap>
                <PoolStepper status={s} onSpend={(n) => onSpend(s.pool, n)} width={100} />
              </div>
              <PoolBar status={s} largest={18} gap={3} round={s.pool === 'SPI'} />
            </div>
          </div>
        ))
      ) : (
        <div className="pool-cells">
          {pools.map((s) => (
            <div className="pool-cell" key={s.pool}>
              <TagsTap status={s} onTags={() => toggle(s.pool)} className="col" style={{ gap: 4, padding: '0 4px' }}>
                <PoolLabel name={s.pool} status={s} />
                <PoolCount status={s} className="titleLarge" />
                <PoolBar status={s} largest={8} gap={2} round={false} segments />
              </TagsTap>
              <PoolStepper status={s} onSpend={(n) => onSpend(s.pool, n)} />
            </div>
          ))}
        </div>
      )}
      {open && <p className="bodySmall muted" style={{ padding: '0 12px 10px' }}>{poolLine(open)}</p>}
    </div>
  )
}

function TagsTap({ status, onTags, children, className, style }: { status: PoolStatus; onTags: () => void; children: ReactNode; className: string; style?: React.CSSProperties }) {
  if (poolTags(status).length === 0) return <div className={className} style={style}>{children}</div>
  return (
    <button className={`${className} tags-tap`} style={style} onClick={onTags} aria-label="Show the rule">
      {children}
    </button>
  )
}

function PoolLabel({ name, status }: { name: string; status: PoolStatus }) {
  const tags = poolTags(status)
  return (
    <span className="row" style={{ minWidth: 0 }}>
      <Caps text={name} color={status.pool === 'SPI' ? 'var(--secondary)' : 'var(--on-surface-variant)'} />
      {tags.length > 0 && <span className="labelSmall err ellipsis" style={{ paddingLeft: 8 }}>{tags.join(' · ')}</span>}
    </span>
  )
}

function PoolCount({ status, className }: { status: PoolStatus; className: string }) {
  return (
    <span className={`${className} mono semibold pool-count`} style={{ color: poolTags(status).length ? 'var(--error)' : 'var(--on-surface)' }} aria-label={`${status.current} of ${status.max}`}>
      {twoDigits(status.current)}
      <span className="pool-max">/{twoDigits(status.max)}</span>
    </span>
  )
}

function PoolStepper({ status, onSpend, width }: { status: PoolStatus; onSpend: (n: number) => void; width?: number }) {
  const canRegain = status.current < status.max
  return (
    <span className="pool-stepper" style={{ width }}>
      <button onClick={() => onSpend(1)} aria-label={`Lose 1 ${status.pool}`}><Icon name="remove" size={20} /></button>
      <button className={canRegain ? 'filled' : ''} disabled={!canRegain} onClick={() => onSpend(-1)} aria-label={`Regain 1 ${status.pool}`}>
        <Icon name="add" size={20} />
      </button>
    </span>
  )
}

/** PoolStrip.kt PoolBar: pips while they fit, else a bar; segments in a cell. */
export function PoolBar({ status, largest, gap, round, segments = false }: { status: PoolStatus; largest: number; gap: number; round: boolean; segments?: boolean }) {
  const max = status.max
  if (max <= 0) return <div style={{ height: largest }} />
  const left = Math.min(Math.max(status.current, 0), max)
  const color = status.pool === 'SPI' ? 'var(--secondary)' : 'var(--primary)'
  const pips = Array.from({ length: max }, (_, i) => i < left)
  if (segments) {
    return (
      <div className="pool-bar segments" style={{ height: largest, gap }} aria-hidden="true">
        {pips.map((full, i) => <span key={i} style={full ? { background: color } : { border: '1px solid var(--outline-variant)' }} />)}
      </div>
    )
  }
  return (
    <div className="pool-bar pips" style={{ height: largest, gap, ['--pip' as string]: `${largest}px` }} aria-hidden="true">
      {pips.map((full, i) => (
        <span
          key={i}
          className={`${round ? 'round' : ''}${full ? '' : ' spent'}`}
          style={full ? { background: color } : { border: '1px solid var(--outline-variant)' }}
        />
      ))}
    </div>
  )
}

export function PoolLine({ pools }: { pools: PoolStatus[] }) {
  return (
    <div className="pool-line">
      {pools.map((s) => {
        const spi = s.pool === 'SPI'
        const color = spi ? 'var(--secondary)' : 'var(--on-surface)'
        return (
          <div key={s.pool} className="grow col" style={{ gap: 4 }}>
            <span className="labelMedium" style={{ color: poolTags(s).length ? 'var(--error)' : color }}>
              {s.pool} <b>{twoDigits(s.current)}</b>
              <span style={{ color: spi ? color : 'var(--on-surface-variant)' }}>/{twoDigits(s.max)}</span>
            </span>
            <PoolBar status={s} largest={6} gap={2} round={false} segments />
          </div>
        )
      })}
    </div>
  )
}

export type SheetDialog =
  | 'FRIGHT' | 'CONCENTRATE' | 'DETECT' | 'ATTACK' | 'DEFEND' | 'DAMAGE' | 'RITUAL' | 'DIVINE' | 'SPIRIT_VISION' | 'BOON'
  | 'WILL_ROLL' | 'PER_ROLL' | 'INTUITION_ROLL' | 'PERCEPTION_ROLL' | 'REST' | 'CONTRACT'

function PrimaryAction({ label, icon, harm, onClick }: { label: string; icon: IconName; harm?: boolean; onClick: () => void }) {
  return (
    <button className="primary-action" onClick={onClick}>
      <Icon name={icon} className={harm ? 'err' : ''} />
      <Caps text={label} className="labelLarge" color="var(--on-surface)" />
    </button>
  )
}

export function SheetActions({ drunk, ritualLevel, hasSpiritVision, sensesOffered, onPathways, onBuild, onOpen }: {
  drunk: BeyonderState | null
  ritualLevel: number | null
  hasSpiritVision: boolean
  sensesOffered: boolean
  onPathways: () => void
  onBuild: () => void
  onOpen: (d: SheetDialog) => void
}) {
  const chip = (label: string, d: SheetDialog, mystical = false) => (
    <BracketAction key={label} label={label} onClick={() => onOpen(d)} color={mystical ? 'var(--secondary)' : 'var(--on-surface)'} />
  )
  return (
    <div className="sheet-actions">
      <div className="action-grid">
        <PrimaryAction label="Attack" icon="bolt" onClick={() => onOpen('ATTACK')} />
        <PrimaryAction label="Defend" icon="shield" onClick={() => onOpen('DEFEND')} />
        <PrimaryAction label="Damage" icon="heartBroken" harm onClick={() => onOpen('DAMAGE')} />
        <PrimaryAction label={drunk === null ? 'Drink a potion' : 'Pathway'} icon="science" onClick={onPathways} />
      </div>
      <div className="action-chips">
        {ritualLevel !== null && chip('Ritual', 'RITUAL', true)}
        {ritualLevel !== null && drunk !== null && chip('Contract', 'CONTRACT', true)}
        {chip('Divine', 'DIVINE', true)}
        {hasSpiritVision && chip(SpiritVision.TRAIT, 'SPIRIT_VISION', true)}
        {sensesOffered && chip('Intuition', 'INTUITION_ROLL', true)}
        {sensesOffered && chip('Perception', 'PERCEPTION_ROLL', true)}
        {chip('Boon', 'BOON', true)}
        {chip('Fright', 'FRIGHT')}
        {chip('Concentrate', 'CONCENTRATE')}
        {chip('Detect', 'DETECT')}
        {chip('Rest', 'REST')}
        <BracketAction label="Spend points" underlined onClick={onBuild} />
      </div>
    </div>
  )
}

const TABS: [SheetTab, string, IconName][] = [['VITALS', 'Vitals', 'monitorHeart'], ['SKILLS', 'Skills', 'casino'], ['TRAITS', 'Traits', 'fingerprint']]

export function SheetTabBar({ tab, onTab }: { tab: SheetTab; onTab: (t: SheetTab) => void }) {
  return (
    <div className="sheet-tab-bar">
      <hr className="rule thick" />
      <div className="tabs" role="tablist">
        {TABS.map(([option, label, icon]) => {
          const open = option === tab
          return (
            <button key={option} role="tab" aria-selected={open} className={`sheet-tab${open ? ' open' : ''}`} onClick={() => onTab(option)}>
              <Icon name={icon} size={20} />
              <Caps text={label} className={`labelLarge mono${open ? ' semibold' : ''}`} color="currentColor" />
            </button>
          )
        })}
      </div>
    </div>
  )
}

export function SheetSection({ title, count }: { title: string; count?: number | null }) {
  return (
    <div className="sheet-section">
      <h2>
        <Caps
          text={`§ ${title}${count !== undefined && count !== null ? ` · ${count}` : ''}`}
          spoken={`${title}${count !== undefined && count !== null ? `, ${count}` : ''}`}
          className="labelSmall semibold"
          color="var(--on-surface)"
        />
      </h2>
      <hr className="rule ink" />
    </div>
  )
}

export function TableHead({ heads, widths }: { heads: string[]; widths?: number[] }) {
  const w = widths ?? heads.slice(1).map(() => 72)
  return (
    <div className="table-head-row">
      <div className="row" style={{ padding: '8px 0' }}>
        <span className="grow"><Caps text={heads[0]} /></span>
        {heads.slice(1).map((h, i) => <span key={h} style={{ width: w[i], textAlign: 'right' }}><Caps text={h} /></span>)}
      </div>
      <hr className="rule" />
    </div>
  )
}

export function RollGlyph({ size = 28 }: { size?: number }) {
  return (
    <span className="roll-glyph" style={{ width: size, height: size }}>
      <Icon name="casino" size={16} />
    </span>
  )
}

export function LevelBox({ number }: { number: string }) {
  return <span className="level-box titleMedium mono">{number}</span>
}
