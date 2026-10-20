// MainActivity.kt SheetScreen (lines 201-347): the top bar, the title, the
// pools, the action hub and one tab, with a pool line pinned once the pools
// scroll off; SheetDialogs.kt draws whatever is open.
import { useRef, useState, type UIEvent } from 'react'
import { fileLine, headerFacts } from '../../app/Gallery'
import type { CharacterDice, RollLog, SheetScene } from '../../app/RollLog'
import type { BeyonderSession } from '../../app/session/BeyonderSession'
import { boonPill, sheetModel, type RollableSkill } from '../../app/SheetModel'
import * as Encumbrance from '../../rules/Encumbrance'
import type { Pool } from '../../rules/Pools'
import type { PremadeSheet } from '../../rules/Premades'
import type { SelfControlCheck } from '../../rules/SelfControl'
import { useCell, useVersion } from '../hooks'
import { BoxedIconButton, Caps, Stamp } from '../kit'
import { rollDialogFor, SheetDialogs } from './SheetDialogs'
import { SheetMenu } from './SheetMenu'
import { PoolLine, PoolStrip, SheetActions, SheetBackdrop, SheetSection, SheetTabBar, SheetTitle, type SheetDialog } from './SheetParts'
import { SkillsTab, TraitsTab, VitalsTab } from './SheetTabs'
import './sheet.css'

export function SheetScreen({ sheet, session, scene, file, dice, rollLog, onPathways, onBuild, onBack, onReset, onDelete }: {
  sheet: PremadeSheet
  session: BeyonderSession
  scene: SheetScene
  file: number | null
  dice: CharacterDice
  rollLog: RollLog
  onPathways: () => void
  onBuild: () => void
  onBack: () => void
  onReset: () => void
  onDelete: (() => void) | null
}) {
  useVersion(session)
  const [tab, setTab] = useCell(scene.tab)
  const [shocked, setShocked] = useCell(scene.shocked)
  const [declaredStates, setDeclaredStates] = useCell(scene.declaredStates)
  const [spiritPending, setSpiritPending] = useCell(scene.spiritPending)
  const [stunned, setStunned] = useCell(scene.stunned)
  const [carried, setCarried] = useCell(scene.carried)
  const [open, setOpen] = useState<Set<SheetDialog>>(new Set())
  const [rolling, setRolling] = useState<RollableSkill | null>(null)
  const [controlling, setControlling] = useState<SelfControlCheck | null>(null)
  const [pickingUntrained, setPickingUntrained] = useState(false)
  const [amendedOnly, setAmendedOnly] = useState(false)
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [scrolled, setScrolled] = useState({ name: false, pools: false })
  const titleRef = useRef<HTMLDivElement>(null)
  const poolsRef = useRef<HTMLDivElement>(null)

  const m = sheetModel(sheet, session, { shocked, declaredStates, spiritPending }, onDelete !== null)
  const { drunk, vitals, attributes, traits } = m
  const maxOf = (pool: Pool) => (pool === 'HP' ? vitals.maxHp : pool === 'FP' ? vitals.maxFp : vitals.maxSpi)
  const opened = (d: SheetDialog) => setOpen(new Set(open).add(d))
  const closed = (d: SheetDialog) => {
    const next = new Set(open)
    next.delete(d)
    setOpen(next)
  }
  // firstVisibleItemIndex > 0 and > 1: the title, then the pools, scrolled off.
  const onScroll = (e: UIEvent<HTMLDivElement>) => {
    const top = e.currentTarget.scrollTop
    const title = titleRef.current?.offsetHeight ?? 0
    const name = top >= title
    const pools = top >= title + (poolsRef.current?.offsetHeight ?? 0)
    if (name !== scrolled.name || pools !== scrolled.pools) setScrolled({ name, pools })
  }

  return (
    <div className="scaffold sheet">
      <SheetBackdrop pathway={drunk?.pathway ?? null} />
      <div className="top-bar">
        <BoxedIconButton icon="arrowBack" label="Back to characters" onClick={onBack} />
        <div className="title">
          {scrolled.name ? (
            <span className="headlineSmall">{sheet.name}</span>
          ) : (
            <div style={{ textAlign: 'center' }} aria-hidden="true"><Caps text={fileLine(file, drunk !== null)} /></div>
          )}
        </div>
        {!scrolled.name ? (
          <SheetMenu name={sheet.name} rollLog={rollLog} onReset={onReset} onDelete={onDelete} />
        ) : drunk !== null ? (
          <span style={{ paddingRight: 12 }}><Stamp text={`Seq ${drunk.sequence}`} color="var(--secondary)" /></span>
        ) : (
          <span style={{ width: 48 }} />
        )}
      </div>
      <div className="sheet-body">
        <div className="scroll" onScroll={onScroll}>
          <div ref={titleRef}>
            <SheetTitle name={sheet.name} facts={headerFacts(drunk, session.boon ? boonPill(session.boon) : null, session.cor, session.isLost, session.corrupted)} />
          </div>
          <div ref={poolsRef} className="pad16" style={{ paddingTop: 8, paddingBottom: 8 }}>
            <PoolStrip pools={m.pools} onSpend={(pool, n) => session.spend(pool, n, maxOf(pool), m.maxCor)} stacked={tab === 'VITALS'} />
          </div>
          {tab === 'VITALS' && <SheetSection title="Actions" />}
          <div className="pad16" style={{ paddingTop: 4, paddingBottom: 12 }}>
            <SheetActions
              drunk={drunk}
              ritualLevel={m.ritualLevel}
              hasSpiritVision={m.hasSpiritVision}
              sensesOffered={m.sensesOffered}
              onPathways={onPathways}
              onBuild={onBuild}
              onOpen={opened}
            />
          </div>
          {tab === 'VITALS' && (
            <VitalsTab
              m={m}
              isOwn={m.isOwn}
              dice={dice}
              amendedOnly={amendedOnly}
              onAmendedOnly={setAmendedOnly}
              onClearShock={() => setShocked(false)}
              stunned={stunned}
              onClearStun={() => setStunned(false)}
              spiritPending={spiritPending}
              onClearSpirit={() => setSpiritPending(0)}
              onDeclareStates={setDeclaredStates}
              onRollAttribute={(k) => opened(rollDialogFor(k))}
              carried={carried}
              basicLift={Encumbrance.basicLift(attributes.st, traits)}
              onCarried={setCarried}
            />
          )}
          {tab === 'TRAITS' && (
            <TraitsTab
              m={m}
              expanded={expanded}
              onToggle={(key) => {
                const next = new Set(expanded)
                if (next.has(key)) next.delete(key)
                else next.add(key)
                setExpanded(next)
              }}
              onControl={setControlling}
            />
          )}
          {tab === 'SKILLS' && (
            <SkillsTab
              sheet={sheet}
              rows={m.rows}
              skillLevels={m.skillLevels}
              edits={session.edits}
              onRoll={setRolling}
              onPickUntrained={() => setPickingUntrained(true)}
              amendedOnly={amendedOnly}
              onAmendedOnly={setAmendedOnly}
            />
          )}
        </div>
        {scrolled.pools && (
          <div className="pinned-pools">
            <div className="pad16" style={{ paddingTop: 8, paddingBottom: 8 }}><PoolLine pools={m.pools} /></div>
            <hr className="rule thick" />
          </div>
        )}
      </div>
      <SheetTabBar tab={tab} onTab={setTab} />
      <SheetDialogs
        m={m}
        sheet={sheet}
        session={session}
        scene={scene}
        dice={dice}
        open={open}
        onClose={closed}
        rolling={rolling}
        onRolling={setRolling}
        controlling={controlling}
        onControlling={setControlling}
        pickingUntrained={pickingUntrained}
        onPickingUntrained={setPickingUntrained}
      />
    </div>
  )
}
