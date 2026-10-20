// BuilderScreen.kt, BuilderLedger.kt and BuilderRows.kt: the point ledger.
// Every row writes through the session, which bills and validates it.
import { useContext, useEffect, useState, type ReactNode } from 'react'
import {
  budgetPips, BUDGET_PIPS, carriedLine, charmableSkills, charmLine, FORM_LEDGER, isSupersededByPotion, ledgerStacks, pointsLabel, secondaryShortName,
  signedPoints, studyAdvice, type StudyAdvice,
} from '../../app/BuilderModel'
import type { BeyonderSession } from '../../app/session/BeyonderSession'
import * as EC from '../../app/session/EditedCharacter'
import type { SavedInstancedTrait } from '../../app/session/SessionStore'
import { disambiguated, skillKeyLabel } from '../../app/SheetText'
import * as Appearance from '../../rules/Appearance'
import * as AttributeCosts from '../../rules/AttributeCosts'
import type { AppearanceLevel, CataloguedWeapon, SecondaryCost, WealthLevel } from '../../rules/content/schema.gen'
import * as ContentLoader from '../../rules/content/ContentLoader'
import { canonical, type ValueMap } from '../../rules/kt'
import * as Languages from '../../rules/Languages'
import type { OwnedLanguage } from '../../rules/Languages'
import { attr as attrOf, ATTRS, BASE_ATTRIBUTE, MAX_SECONDARY_DELTA_MAGNITUDE, type Attr, type SkillKey, type Trait } from '../../rules/model'
import * as Derive from '../../rules/Derive'
import * as CharmedObject from '../../rules/CharmedObject'
import type { PremadeSheet } from '../../rules/Premades'
import * as Premades from '../../rules/Premades'
import * as PurchasableTraits from '../../rules/PurchasableTraits'
import type { InstantiableTrait } from '../../rules/PurchasableTraits'
import * as PurchaseCeilings from '../../rules/PurchaseCeilings'
import * as SecondaryCharacteristics from '../../rules/SecondaryCharacteristics'
import * as SkillDefaults from '../../rules/SkillDefaults'
import * as Study from '../../rules/Study'
import * as TraitGrants from '../../rules/TraitGrants'
import * as Wealth from '../../rules/Wealth'
import { useVersion } from '../hooks'
import { BoxedIconButton, BracketAction, Caps, CaseChip, ChipTag, DialogHost, InkAction, JoinedStepper, LedgerHeadRow } from '../kit'
import { AppearancePicker, CharmSkillPicker, InstancedTraitDialog, LanguagePicker, SkillPicker, TraitPicker, WealthPicker, WeaponPicker } from './BuilderPickers'
import './builder.css'

/** `ledgerStacks` read off the screen the app is drawn in, and the browser's text scale. */
function useStacks(): boolean {
  const host = useContext(DialogHost)
  const read = () => ledgerStacks(host?.clientWidth ?? window.innerWidth, parseFloat(getComputedStyle(document.documentElement).fontSize) / 16)
  const [stacks, setStacks] = useState(read)
  useEffect(() => {
    if (!host) return
    const observer = new ResizeObserver(() => setStacks(read()))
    observer.observe(host)
    return () => observer.disconnect()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [host])
  return stacks
}

export function BuilderScreen({ sheet, session, onBack }: { sheet: PremadeSheet; session: BeyonderSession; onBack: () => void }) {
  useVersion(session)
  const stacks = useStacks()
  const start = Premades.toCharacter(sheet)
  const character = session.characterFor(sheet)
  const derived = Derive.of(character)
  const played = EC.digestedWith(character, session.state)
  const report = derived.budget
  const playedAttributes = Derive.attributes(character, played)
  const [studyMethod, setStudyMethod] = useState<Study.StudyMethod>('TUTOR')
  const [openStudy, setOpenStudy] = useState<string[]>([])
  const [picker, setPicker] = useState<null | 'skill' | 'wealth' | 'looks' | 'trait' | 'charm' | 'weapon' | 'language'>(null)
  const [settling, setSettling] = useState<InstantiableTrait | null>(null)
  const close = () => setPicker(null)
  const edits = session.edits
  const charmable = charmableSkills(character, session.state)

  return (
    <div className="scaffold builder">
      <div className="row ledger-bar">
        <BoxedIconButton icon="arrowBack" label="Back to sheet" onClick={onBack} />
        <span className="grow" style={{ padding: '0 16px' }}><Caps text={FORM_LEDGER} className="labelMedium" /></span>
      </div>
      <div className="scroll">
        <div className="ledger-title">
          <h1 className="displaySmall">Spend points</h1>
          <p className="bodyMedium mono muted">{sheet.name}</p>
        </div>
        <div className="budget-sticky">
          <BudgetCard spent={report.spent} budget={report.budget} stacks={stacks} />
          {report.violations.map((it) => <p key={it} className="bodySmall err pad16">{it}</p>)}
        </div>
        <LedgerHeadRow heading="Attributes" />
        {ATTRS.map((a) => (
          <AttributeRow
            key={a}
            attr={a}
            level={attrOf(character.attributes, a)}
            played={attrOf(playedAttributes, a)}
            startingLevel={attrOf(start.attributes, a)}
            ceiling={PurchaseCeilings.attribute(a, edits.traits)}
            canLower={session.canSetAttribute(a, attrOf(character.attributes, a) - 1)}
            stacks={stacks}
            onChange={(d) => session.setAttribute(a, attrOf(character.attributes, a) + d)}
          />
        ))}
        <LedgerHeadRow heading="Secondary characteristics" />
        {ContentLoader.secondaryCosts().filter((it) => it.pointsPerStep !== null).map((row) => {
          const bought = edits.secondaries[row.key] ?? 0
          const boughtValue = SecondaryCharacteristics.valueLabelOf(derived.secondaries, row.key)!
          const value = (played !== null ? SecondaryCharacteristics.valueLabelOf(played.secondaries, row.key) : null) ?? boughtValue
          return (
            <SecondaryRow
              key={row.key}
              row={row}
              value={value}
              boughtValue={boughtValue}
              steps={bought}
              canLower={session.canSetSecondary(row.key, bought - 1)}
              stacks={stacks}
              onChange={(d) => session.setSecondary(row.key, bought + d)}
            />
          )
        })}
        <WealthRow level={EC.wealthShownFor(edits, character)} printed={Wealth.levelOf(start.traits)} onPick={() => setPicker('wealth')} />
        <AppearanceRow
          level={Appearance.levelOf(character.traits)}
          printed={Appearance.levelOf(start.traits)}
          granted={Appearance.played(TraitGrants.grantedTraits(session.state, session.boon))}
          onPick={() => setPicker('looks')}
        />
        <LanguagesRow owned={EC.ownedLanguages(edits, start.traits)} onAdd={() => setPicker('language')} onGiveBack={(name) => session.sellLanguage(name)} />
        <WeaponsRow
          carried={EC.carriedWeapons(edits)}
          skillLevels={played?.skillLevels ?? derived.skillLevels}
          mastery={session.state ? SkillDefaults.masteryFor(session.state.pathway, session.state.sequence) : null}
          dx={playedAttributes.dx}
          onAdd={() => setPicker('weapon')}
          onRemove={(name) => session.dropWeapon(name)}
        />
        <TraitsRow
          bought={edits.traits}
          instanced={EC.instancedRows(edits, EC.traitsBeforeInstanced(edits, start.traits))}
          onAdd={() => setPicker('trait')}
          onRemove={(name) => session.sellTrait(name)}
          canRemove={(name) => session.canSellTrait(name)}
          onRemoveInstanced={(i, drawn) => session.sellInstancedTrait(i, drawn)}
          charmLine={charmLine(edits.charmedSkill, charmable)}
          onChooseCharm={() => setPicker('charm')}
          gained={edits.gained}
          onGained={(name, on) => session.setGained(name, on)}
          quirks={edits.quirks}
          onRemoveQuirk={(name) => session.removeQuirk(name)}
        />
        <LedgerHeadRow heading="Skills" action={<BracketAction label="Add a skill" underlined onClick={() => setPicker('skill')} />} />
        {session.state !== null && <span className="before-potions labelLarge mono">Levels before potions</span>}
        <StudyToggle method={studyMethod} onPick={setStudyMethod} />
        {character.skills.map((purchased) => {
          const key = canonical(purchased.key)
          const bought = edits.skills.find((it) => it.name === purchased.key.name && it.specialization === purchased.key.specialization)
          return (
            <SkillRow
              key={key}
              label={disambiguated(purchased.key, character.skills.map((it) => it.key))}
              level={derived.skillLevels.get(purchased.key) ?? null}
              points={purchased.points - purchased.studied}
              studied={purchased.studied}
              expanded={openStudy.includes(key)}
              onToggle={() => setOpenStudy(openStudy.includes(key) ? openStudy.filter((it) => it !== key) : [...openStudy, key])}
              study={studyAdvice(purchased, character, derived, studyMethod, isSupersededByPotion(purchased.key, played))}
              onStudy={(n) => session.setStudied(purchased.key.name, purchased.key.specialization, Math.max(n, 0))}
              onStep={bought ? (up) => session.stepSkill(bought.name, bought.specialization, up) : null}
              stacks={stacks}
            />
          )
        })}
      </div>
      {picker === 'wealth' && (
        <WealthPicker chosen={EC.wealthLevel(edits)?.status ?? null} printed={Wealth.levelOf(start.traits)} onPick={(l) => { session.setWealth(l?.status ?? null); close() }} onDismiss={close} />
      )}
      {picker === 'looks' && (
        <AppearancePicker chosen={EC.appearanceLevel(edits)?.level ?? null} printed={Appearance.levelOf(start.traits)} onPick={(l) => { session.setAppearance(l?.level ?? null); close() }} onDismiss={close} />
      )}
      {picker === 'weapon' && <WeaponPicker carried={edits.weapons} onCarry={(name) => session.carryWeapon(name)} onDismiss={close} />}
      {picker === 'charm' && (
        <CharmSkillPicker
          skills={[...charmable].sort((a, b) => (skillKeyLabel(a) < skillKeyLabel(b) ? -1 : 1))}
          current={edits.charmedSkill}
          onPick={(k) => { session.setCharmedSkill(k); close() }}
          onDismiss={close}
        />
      )}
      {picker === 'trait' && (
        <TraitPicker
          held={session.traitsInPlay(sheet)}
          bought={edits.traits}
          refusalFor={(name) => session.buyRefusal(name)}
          onBuy={(t) => { if (session.buyRefusal(t.name) === null) session.buyTrait(t.name); close() }}
          onSettle={(row) => { setSettling(row); close() }}
          onDismiss={close}
        />
      )}
      {settling !== null && (
        <InstancedTraitDialog
          row={settling}
          onBuy={(p) => { session.buyInstancedTrait({ row: p.row, variant: p.variant, instance: p.instance, cost: p.cost }); setSettling(null) }}
          onDismiss={() => setSettling(null)}
        />
      )}
      {picker === 'language' && (
        <LanguagePicker
          owned={EC.ownedLanguages(edits, start.traits)}
          priced={(l) => EC.pricedWith(edits, l, start.traits)}
          onBuy={(l) => { session.buyLanguage(l); close() }}
          onDismiss={close}
        />
      )}
      {picker === 'skill' && (
        <SkillPicker
          owned={character.skills.map((it) => [it.key.name.toLowerCase(), it.key.specialization] as [string, string | null])}
          onPick={(d) => { session.buySkill(d.name, d.specialization); close() }}
          onDismiss={close}
        />
      )}
    </div>
  )
}

function BudgetCard({ spent, budget, stacks }: { spent: number; budget: number; stacks: boolean }) {
  const over = spent > budget
  const accent = over ? 'var(--error)' : 'var(--on-surface)'
  const spare = <span className="spare labelLarge mono" style={{ color: accent, borderColor: accent }}>{over ? `${spent - budget} over budget` : `${budget - spent} spare`}</span>
  const filled = budgetPips(spent, budget)
  return (
    <div className="budget-card" style={{ borderColor: over ? accent : undefined }}>
      <div className="row">
        <span className="headlineLarge mono" style={{ color: accent, fontWeight: 700 }}>{spent}</span>
        <span className="bodyLarge mono muted grow">&nbsp;of {budget} points</span>
        {!stacks && spare}
      </div>
      {stacks && <div style={{ paddingTop: 6 }}>{spare}</div>}
      <div className="budget-pips" aria-hidden="true">
        {Array.from({ length: BUDGET_PIPS }, (_, i) => <span key={i} style={i < filled ? { background: accent, borderColor: accent } : undefined} />)}
      </div>
    </div>
  )
}

function LedgerRow({ name, spoken, note, stacks, children }: { name: string; spoken?: string; note: string; stacks: boolean; children: ReactNode }) {
  return (
    <div className="ledger-row">
      <div className="row">
        <span className="ledger-name titleMedium mono" aria-label={spoken}>{name}</span>
        {stacks ? <span className="grow" /> : <span className="labelMedium mono muted grow" style={{ paddingRight: 8 }}>{note}</span>}
        {children}
      </div>
      {stacks && <p className="labelMedium mono muted" style={{ paddingTop: 6 }}>{note}</p>}
    </div>
  )
}

function AttributeRow({ attr, level, played, startingLevel, ceiling, canLower, stacks, onChange }: {
  attr: Attr; level: number; played: number; startingLevel: number; ceiling: number; canLower: boolean; stacks: boolean; onChange: (d: number) => void
}) {
  const spent = AttributeCosts.costOf(attr, level)
  const next = level < ceiling ? AttributeCosts.costOf(attr, level + 1) - spent : null
  const bought = EC.boughtNote(`${level}`, `${played}`)
  const note = `${spent} pts spent here` + (next !== null ? ` — next level costs ${next}` : '') + (level !== startingLevel ? ` — sheet prints ${startingLevel}` : '') + (bought !== null ? ` — ${bought}` : '')
  return (
    <LedgerRow name={attr} note={note} stacks={stacks}>
      <JoinedStepper
        value={`${played}`}
        onMinus={() => onChange(-1)}
        onPlus={() => onChange(1)}
        minusDisabled={!(level > BASE_ATTRIBUTE && canLower)}
        plusDisabled={!(level < ceiling)}
        valueColor={spent > 0 ? 'var(--primary)' : undefined}
        minusLabel={`Lower ${attr}`}
        plusLabel={`Raise ${attr}`}
      />
    </LedgerRow>
  )
}

function SecondaryRow({ row, value, boughtValue, steps, canLower, stacks, onChange }: {
  row: SecondaryCost; value: string; boughtValue: string; steps: number; canLower: boolean; stacks: boolean; onChange: (d: number) => void
}) {
  const bought = EC.boughtNote(boughtValue, value)
  const note = row.cost + (steps !== 0 ? ` — ${signedPoints(steps)} bought` : '') + (bought !== null ? ` — ${bought}` : '')
  return (
    <LedgerRow name={secondaryShortName(row.key, row.characteristic)} spoken={row.characteristic} note={note} stacks={stacks}>
      <JoinedStepper
        value={value}
        onMinus={() => onChange(-1)}
        onPlus={() => onChange(1)}
        minusDisabled={!(steps > -MAX_SECONDARY_DELTA_MAGNITUDE && canLower)}
        plusDisabled={!(steps < PurchaseCeilings.secondarySteps(row.key))}
        valueColor={steps !== 0 ? 'var(--primary)' : undefined}
        minusLabel={`Lower ${row.characteristic}`}
        plusLabel={`Raise ${row.characteristic}`}
      />
    </LedgerRow>
  )
}

function SectionHead({ heading, action, onAction, children }: { heading: string; action: string; onAction: () => void; children?: ReactNode }) {
  return (
    <>
      <LedgerHeadRow heading={heading} action={<BracketAction label={action} underlined onClick={onAction} />} />
      <div className="pad16" style={{ paddingTop: 10 }}>{children}</div>
    </>
  )
}

function WealthRow({ level, printed, onPick }: { level: WealthLevel | null; printed: WealthLevel | null; onPick: () => void }) {
  return (
    <SectionHead heading="Wealth" action="Change" onAction={onPick}>
      {level === null ? (
        <p className="bodyMedium">The sheet prints no wealth level</p>
      ) : (
        <>
          <p className="titleMedium">{level.status}</p>
          <div className="chips">
            <ChipTag text={pointsLabel(level.points)} ink />
            <ChipTag text={`${level.startingMoney} to start`} />
            {level.socialWeight !== 0 && <ChipTag text={`social weight ${signedPoints(level.socialWeight)}`} />}
            {printed !== null && printed.status !== level.status && <ChipTag text={`sheet prints ${printed.status}`} />}
          </div>
          <p className="bodySmall muted" style={{ paddingTop: 4 }}>{level.lifestyle}</p>
        </>
      )}
    </SectionHead>
  )
}

function AppearanceRow({ level, printed, granted, onPick }: { level: AppearanceLevel | null; printed: AppearanceLevel | null; granted: AppearanceLevel | null; onPick: () => void }) {
  const playedLevel = granted !== null ? Appearance.played([level, granted].filter((it) => it !== null).map(Appearance.traitFor)) : null
  return (
    <SectionHead heading="Appearance" action="Change" onAction={onPick}>
      {level === null ? (
        <p className="bodyMedium">Ordinary — the book prints no level for it</p>
      ) : (
        <>
          <p className="titleMedium">{level.level}</p>
          <div className="chips">
            <ChipTag text={pointsLabel(level.points)} ink />
            {printed !== null && printed.level !== level.level && <ChipTag text={`sheet prints ${printed.level}`} />}
          </div>
          <p className="bodySmall muted" style={{ paddingTop: 4 }}>{level.effect}</p>
        </>
      )}
      {granted !== null && playedLevel !== null && (
        <p className="bodySmall muted" style={{ paddingTop: 4 }}>the potion grants {granted.level}; {playedLevel.level} is played</p>
      )}
    </SectionHead>
  )
}

function HeldRow({ title, lines, action, onAction, disabled, children }: { title: string; lines: ReactNode[]; action: string; onAction: () => void; disabled?: boolean; children?: ReactNode }) {
  return (
    <div className="row held-row">
      <div className="grow">
        <p className="bodyLarge">{title}</p>
        {lines.map((it, i) => <div key={i} className="bodySmall">{it}</div>)}
        {children}
      </div>
      <InkAction label={action} onClick={onAction} disabled={disabled} />
    </div>
  )
}

function LanguagesRow({ owned, onAdd, onGiveBack }: { owned: OwnedLanguage[]; onAdd: () => void; onGiveBack: (name: string) => void }) {
  return (
    <>
      <SectionHead heading="Languages" action="Add or change" onAction={onAdd}>
        {owned.length === 0 && <p className="bodyMedium muted">None bought</p>}
      </SectionHead>
      {owned.map((it) => (
        <HeldRow key={it.purchase.language} title={`${Languages.ownedLabel(it)} — ${Languages.costLine(it)}`} lines={[]} action="Give back" onAction={() => onGiveBack(it.purchase.language)} />
      ))}
    </>
  )
}

function WeaponsRow({ carried, skillLevels, mastery, dx, onAdd, onRemove }: {
  carried: CataloguedWeapon[]; skillLevels: ValueMap<SkillKey, number>; mastery: SkillDefaults.WeaponMastery | null; dx: number; onAdd: () => void; onRemove: (name: string) => void
}) {
  return (
    <>
      <SectionHead heading="Weapons" action="Carry a weapon" onAction={onAdd}>
        {carried.length === 0 && <p className="bodyMedium muted">None carried — the defence screen has no armed parry to show.</p>}
      </SectionHead>
      {carried.map((w) => (
        <HeldRow key={w.name} title={w.name} lines={[carriedLine(w, skillLevels, mastery, dx)]} action="Put down" onAction={() => onRemove(w.name)} />
      ))}
    </>
  )
}

const GAINED_LABEL = 'Gained in play — 0 pts'
const GainedChip = ({ on, onChange }: { on: boolean; onChange: (v: boolean) => void }) => (
  <div style={{ paddingTop: 4 }}><CaseChip selected={on} onClick={() => onChange(!on)}>Gained in play</CaseChip></div>
)

function TraitsRow({ bought, instanced, onAdd, onRemove, canRemove, onRemoveInstanced, charmLine: charm, onChooseCharm, gained, onGained, quirks, onRemoveQuirk }: {
  bought: string[]
  instanced: [SavedInstancedTrait, Trait | null][]
  onAdd: () => void
  onRemove: (name: string) => void
  canRemove: (name: string) => boolean
  onRemoveInstanced: (index: number, drawn: SavedInstancedTrait) => void
  charmLine: string
  onChooseCharm: () => void
  gained: string[]
  onGained: (name: string, on: boolean) => void
  quirks: string[]
  onRemoveQuirk: (name: string) => void
}) {
  return (
    <>
      <SectionHead heading="Traits" action="Add a trait" onAction={onAdd}>
        {bought.length === 0 && instanced.length === 0 && quirks.length === 0 && (
          <p className="bodyMedium muted">None bought in play — the sheet's own are on the sheet screen.</p>
        )}
      </SectionHead>
      {instanced.map(([purchase, trait], index) => {
        const isAdvantage = PurchasableTraits.instantiableNamed(purchase.row)?.advantage === true
        const title = [trait?.name ?? purchase.row, trait?.name === purchase.row ? purchase.instance : null].filter((it) => it !== null).join(' — ')
        const price = trait === null
          ? 'Not applied — the book does not license this purchase, so it costs nothing.'
          : !isAdvantage && gained.includes(trait.name) ? GAINED_LABEL : pointsLabel(isAdvantage ? trait.cost : -trait.cost)
        return (
          <HeldRow key={`i${index}`} title={title} lines={[price]} action="Give back" onAction={() => onRemoveInstanced(index, purchase)}>
            {trait !== null && !isAdvantage && <GainedChip on={gained.includes(trait.name)} onChange={(v) => onGained(trait.name, v)} />}
          </HeldRow>
        )
      })}
      {bought.map((name) => {
        const row = PurchasableTraits.named(name)
        const removable = canRemove(name)
        const price = row === null
          ? 'The book no longer prints this trait, so it costs nothing.'
          : !row.advantage && gained.includes(name) ? GAINED_LABEL : pointsLabel(row.advantage ? row.cost : -row.cost)
        return (
          <HeldRow key={name} title={name} lines={[price]} action="Give back" onAction={() => onRemove(name)} disabled={!removable}>
            {row !== null && !row.advantage && <GainedChip on={gained.includes(name)} onChange={(v) => onGained(name, v)} />}
            {name === CharmedObject.ROW && (
              <div className="row" style={{ gap: 8 }}>
                <span className="bodySmall grow">{charm}</span>
                <InkAction label="Choose skill" onClick={onChooseCharm} />
              </div>
            )}
            {!removable && <p className="bodySmall err">Kept: giving it back would bring maximum CoR to the CoR total or below 1.</p>}
          </HeldRow>
        )
      })}
      {quirks.map((name) => <HeldRow key={`q${name}`} title={name} lines={[`Quirk · ${GAINED_LABEL}`]} action="Give back" onAction={() => onRemoveQuirk(name)} />)}
    </>
  )
}

function StudyToggle({ method, onPick }: { method: Study.StudyMethod; onPick: (m: Study.StudyMethod) => void }) {
  return (
    <div className="pad16" style={{ padding: '6px 16px' }}>
      <div style={{ paddingBottom: 6 }}><Caps text="Study by" /></div>
      <div className="study-toggle" role="radiogroup">
        {Study.STUDY_METHODS.map((entry) => (
          <button key={entry} role="radio" aria-checked={entry === method} className={entry === method ? 'picked' : ''} onClick={() => onPick(entry)}>
            <Caps text={Study.METHOD_LABEL[entry]} className="labelLarge mono semibold" color="currentColor" />
          </button>
        ))}
      </div>
    </div>
  )
}

function SkillRow({ label, level, points, studied, expanded, onToggle, study, onStudy, onStep, stacks }: {
  label: string
  level: number | null
  points: number
  studied: number
  expanded: boolean
  onToggle: () => void
  study: StudyAdvice
  onStudy: (n: number) => void
  onStep: ((up: boolean) => void) | null
  stacks: boolean
}) {
  const canOpen = study.reason !== null
  const paid = <p className="labelMedium mono muted">{pointsLabel(points).replace(/^\+/, '')}{studied > 0 ? ` + ${studied} studied` : ''}</p>
  const tag = <span className={`study-tag labelMedium mono${canOpen ? ' dashed-tag' : ''}`}>Study: {study.tag}</span>
  const stepper = (
    <span className="row study-stepper">
      <button className="text-button" disabled={studied <= 0} onClick={() => onStudy(studied - 1)} aria-label="Remove a study point">−</button>
      <span className="labelMedium">study CP</span>
      <button className="text-button" disabled={study.reason !== null} onClick={() => onStudy(studied + 1)} aria-label="Record a study point">+</button>
    </span>
  )
  const opens = (children: ReactNode) =>
    canOpen ? <button className="study-open" onClick={onToggle} aria-expanded={expanded}>{children}</button> : <div>{children}</div>
  return (
    <div className="skill-ledger-row">
      <div className="row" style={{ alignItems: 'flex-start' }}>
        <div className="grow" style={{ paddingRight: 8 }}>
          {opens(<><p className="bodyLarge">{label}</p>{!stacks && <>{paid}{tag}</>}</>)}
        </div>
        <div className="col" style={{ alignItems: 'flex-end' }}>
          {onStep !== null ? (
            <JoinedStepper value={level !== null ? String(level) : '–'} onMinus={() => onStep(false)} onPlus={() => onStep(true)} minusLabel="One rung down" plusLabel="One rung up" />
          ) : (
            level !== null && <span className="level-badge titleMedium mono">{level}</span>
          )}
          {!stacks && stepper}
        </div>
      </div>
      {stacks && (
        <>
          <div className="row" style={{ justifyContent: 'flex-end' }}>{stepper}</div>
          {opens(<>{paid}{tag}</>)}
        </>
      )}
      {expanded && study.reason !== null && <p className="bodyMedium muted" style={{ paddingTop: 6 }}>{study.reason}</p>}
    </div>
  )
}
