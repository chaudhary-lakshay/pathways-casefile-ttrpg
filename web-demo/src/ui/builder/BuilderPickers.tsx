// BuilderPickers.kt, TraitPicker.kt, LanguagePicker.kt, WeaponPicker.kt and
// InstancedTraitDialog.kt: the builder's dialogs.
import { useId, useState, type ReactNode } from 'react'
import { definitionLabel, isCharmed, matchesSearch, pointsLabel, signedPoints, skillMatches } from '../../app/BuilderModel'
import type { SavedCharmedSkill, SavedLanguage } from '../../app/session/SessionStore'
import { skillKeyLabel } from '../../app/SheetText'
import * as Appearance from '../../rules/Appearance'
import type { AppearanceLevel, SkillDefinition, WealthLevel } from '../../rules/content/schema.gen'
import * as ContentLoader from '../../rules/content/ContentLoader'
import { trim } from '../../rules/kt'
import * as Languages from '../../rules/Languages'
import type { OwnedLanguage } from '../../rules/Languages'
import { attrCode, DIFFICULTY, type SkillKey, type Trait } from '../../rules/model'
import * as PurchasableTraits from '../../rules/PurchasableTraits'
import type { InstancedPurchase, InstantiableTrait, PurchasableTrait } from '../../rules/PurchasableTraits'
import * as Wealth from '../../rules/Wealth'
import * as Weapons from '../../rules/Weapons'
import { Icon } from '../icons'
import { Caps, CaseChip, Chips, CloseButton, Dialog, FormTitle, InkAction, InkButton, SteadyBody } from '../kit'

function PickRow({ title, lines, onClick, disabled }: { title: string; lines: string[]; onClick: () => void; disabled?: boolean }) {
  return (
    <>
      <button className="pick-row" onClick={onClick} disabled={disabled}>
        <span className="bodyLarge" style={{ color: disabled ? 'var(--on-surface-variant)' : 'var(--on-surface)' }}>{title}</span>
        {lines.map((it, i) => <span key={i} className="bodySmall">{it}</span>)}
      </button>
      <hr className="rule" />
    </>
  )
}

function SearchField({ query, onQuery }: { query: string; onQuery: (q: string) => void }) {
  const id = useId()
  return (
    <>
      <label htmlFor={id}><Caps text="Search" /></label>
      <input id={id} className="text-field bodyLarge" value={query} autoComplete="off" onChange={(e) => onQuery(e.target.value)} />
    </>
  )
}

export function WealthPicker({ chosen, printed, onPick, onDismiss }: { chosen: string | null; printed: WealthLevel | null; onPick: (l: WealthLevel | null) => void; onDismiss: () => void }) {
  return (
    <Dialog onDismiss={onDismiss} confirm={<CloseButton onClick={onDismiss} />} title={<FormTitle form={null}>Economic status</FormTitle>}>
      <PickRow title={`As printed${chosen === null ? ' ✓' : ''}`} lines={[printed ? `The sheet prints ${printed.status}` : 'The sheet prints no wealth level']} onClick={() => onPick(null)} />
      {Wealth.all().map((level) => (
        <PickRow
          key={level.status}
          title={level.status + (level.status === chosen ? ' ✓' : '')}
          lines={[`${pointsLabel(level.points)} · ${level.startingMoney} · social weight ${signedPoints(level.socialWeight)}`, level.lifestyle]}
          onClick={() => onPick(level)}
        />
      ))}
    </Dialog>
  )
}

export function AppearancePicker({ chosen, printed, onPick, onDismiss }: { chosen: string | null; printed: AppearanceLevel | null; onPick: (l: AppearanceLevel | null) => void; onDismiss: () => void }) {
  return (
    <Dialog onDismiss={onDismiss} confirm={<CloseButton onClick={onDismiss} />} title={<FormTitle form={null}>Appearance</FormTitle>}>
      <PickRow title={`As printed${chosen === null ? ' ✓' : ''}`} lines={[printed ? `The sheet prints ${printed.level}` : 'The sheet prints no appearance level — ordinary looks']} onClick={() => onPick(null)} />
      {Appearance.all().map((level) => (
        <PickRow key={level.level} title={level.level + (level.level === chosen ? ' ✓' : '')} lines={[pointsLabel(level.points), level.effect]} onClick={() => onPick(level)} />
      ))}
    </Dialog>
  )
}

export function SkillPicker({ owned, onPick, onDismiss }: { owned: [string, string | null][]; onPick: (d: SkillDefinition) => void; onDismiss: () => void }) {
  const [query, setQuery] = useState('')
  const skills = ContentLoader.skills()
    .filter((it) => !owned.some(([name, spec]) => name === it.name.toLowerCase() && spec === it.specialization))
    .sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0))
  return (
    <Dialog onDismiss={onDismiss} confirm={<CloseButton onClick={onDismiss} />} title={<FormTitle form={null}>Add a skill</FormTitle>}>
      <SearchField query={query} onQuery={setQuery} />
      <div className="scroll" style={{ height: 420 }}>
        {skills.filter((it) => skillMatches(it, query)).map((d) => (
          <PickRow
            key={definitionLabel(d)}
            title={definitionLabel(d)}
            lines={[`${attrCode(d.attr)}/${DIFFICULTY[d.difficulty].code}${d.specializable ? ' - needs a specialization' : ''}`]}
            onClick={() => onPick(d)}
          />
        ))}
      </div>
    </Dialog>
  )
}

export function CharmSkillPicker({ skills, current, onPick, onDismiss }: { skills: SkillKey[]; current: SavedCharmedSkill | null; onPick: (k: SkillKey) => void; onDismiss: () => void }) {
  return (
    <Dialog onDismiss={onDismiss} confirm={<CloseButton onClick={onDismiss} />} title={<FormTitle form={null}>Charmed Object's skill</FormTitle>}>
      {skills.length === 0 && <p>This character has no skill to charm yet.</p>}
      {skills.map((key) => (
        <button key={skillKeyLabel(key)} className="text-button bodyLarge" onClick={() => onPick(key)}>
          {isCharmed(current, key) ? `${skillKeyLabel(key)} — charmed now` : skillKeyLabel(key)}
        </button>
      ))}
    </Dialog>
  )
}

export function WeaponPicker({ carried, onCarry, onDismiss }: { carried: string[]; onCarry: (name: string) => void; onDismiss: () => void }) {
  return (
    <Dialog onDismiss={onDismiss} confirm={<CloseButton onClick={onDismiss} />} title={<FormTitle form={null}>Carry a weapon</FormTitle>}>
      {Weapons.all().map((w) => {
        const held = carried.some((it) => it.toLowerCase() === w.name.toLowerCase())
        return (
          <PickRow key={w.name} title={w.name} disabled={held} lines={[`${w.skill} · reach/range ${w.range} · ${w.cost}${held ? ' · carried' : ''}`]} onClick={() => onCarry(w.name)} />
        )
      })}
    </Dialog>
  )
}

const ALREADY_HELD = 'already held'
const signedCost = (advantage: boolean, cost: number) => (advantage ? cost : -cost)

function PickerRow({ name, blocked, price, notes, expanded, onToggle, action, onAction }: {
  name: string
  blocked: string | null
  price: string
  notes: string
  expanded: boolean
  onToggle: () => void
  action: string
  onAction: () => void
}) {
  return (
    <>
      <div style={{ padding: '8px 0' }}>
        <button className="row picker-head" onClick={onToggle} aria-expanded={expanded}>
          <span className="bodyLarge grow" style={{ color: blocked !== null ? 'var(--on-surface-variant)' : 'var(--on-surface)' }}>
            {name}{blocked !== null ? ` — ${blocked}` : ''}
          </span>
          <span className="labelLarge muted" style={{ padding: '0 6px' }}>{price}</span>
          <Icon name={expanded ? 'expandLess' : 'expandMore'} />
        </button>
        {expanded && (
          <>
            <p className="bodyMedium" style={{ paddingTop: 4, color: 'var(--on-surface)' }}>{notes}</p>
            {blocked === null && <div style={{ paddingTop: 6 }}><InkAction label={action} onClick={onAction} /></div>}
          </>
        )}
      </div>
      <hr className="rule" />
    </>
  )
}

function PickerSection({ heading, subtitle }: { heading: string; subtitle: string }) {
  return (
    <>
      <p className="titleSmall" style={{ paddingTop: 8, color: 'var(--on-surface)' }}>{heading}</p>
      <p className="bodySmall">{subtitle}</p>
      <hr className="rule" />
    </>
  )
}

export function TraitPicker({ held, bought, refusalFor, onBuy, onSettle, onDismiss }: {
  held: Trait[]
  bought: string[]
  refusalFor: (name: string) => string | null
  onBuy: (t: PurchasableTrait) => void
  onSettle: (t: InstantiableTrait) => void
  onDismiss: () => void
}) {
  const [open, setOpen] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const offered = PurchasableTraits.offered()
  const blessings = PurchasableTraits.blessingNames()
  const sections: [string, string, PurchasableTrait[]][] = [
    ['Advantages', 'Cost points.', offered.filter((it) => it.advantage && !blessings.has(it.name))],
    ['The Seven Orthodox Gods Blessings', PurchasableTraits.blessingRequirement(), offered.filter((it) => blessings.has(it.name))],
    ['Drawbacks', 'Refund points, up to 40.', offered.filter((it) => !it.advantage && !blessings.has(it.name))],
  ]
  const toggle = (key: string) => setOpen(open === key ? null : key)
  const instances = PurchasableTraits.instantiable().filter((it) => matchesSearch(it.name, query))
  return (
    <Dialog onDismiss={onDismiss} confirm={<CloseButton onClick={onDismiss} />} title={<FormTitle form={null}>Buy a trait</FormTitle>}>
      <SteadyBody>
        <SearchField query={query} onQuery={setQuery} />
        <div className="scroll">
          {sections.map(([heading, subtitle, all]) => {
            const rows = all.filter((it) => matchesSearch(it.name, query))
            if (rows.length === 0) return null
            return (
              <div key={heading}>
                <PickerSection heading={heading} subtitle={subtitle} />
                {rows.map((row) => {
                  const blocked = !PurchasableTraits.canBuy(row, held) || bought.includes(row.name) ? ALREADY_HELD : refusalFor(row.name)
                  const key = `${heading}/${row.name}`
                  const price = pointsLabel(signedCost(row.advantage, row.cost))
                  return <PickerRow key={key} name={row.name} blocked={blocked} price={price} notes={row.notes} expanded={open === key} onToggle={() => toggle(key)} action={`Buy · ${price}`} onAction={() => onBuy(row)} />
                })}
              </div>
            )
          })}
          {instances.length > 0 && (
            <PickerSection heading="Priced by the instance" subtitle="The book prints a range, or asks you to name the instance. Say which, and the price follows." />
          )}
          {instances.map((row) => {
            const key = `instance/${row.name}`
            const price = row.variants.length > 0
              ? row.variants.map(([name, points]) => `${name} ${signedCost(row.advantage, points)}`).join(' / ')
              : `${row.advantage ? row.costMin : -row.costMax} to ${row.advantage ? row.costMax : -row.costMin} pts`
            return (
              <PickerRow
                key={key}
                name={row.name}
                blocked={PurchasableTraits.canInstantiate(row, held) ? null : ALREADY_HELD}
                price={price}
                notes={row.notes}
                expanded={open === key}
                onToggle={() => toggle(key)}
                action="Choose…"
                onAction={() => onSettle(row)}
              />
            )
          })}
          {PurchasableTraits.unoffered().map((group) => (
            <div key={group.reason}>
              <p className="titleSmall" style={{ paddingTop: 8, color: 'var(--on-surface)' }}>Not offered — {group.reason}</p>
              <p className="bodySmall">{group.names.join(', ')}</p>
            </div>
          ))}
        </div>
      </SteadyBody>
    </Dialog>
  )
}

export function InstancedTraitDialog({ row, onBuy, onDismiss }: { row: InstantiableTrait; onBuy: (p: InstancedPurchase) => void; onDismiss: () => void }) {
  const enumerated = row.variants.length > 0
  const [variant, setVariant] = useState<string | null>(row.variants[0]?.[0] ?? null)
  const [instance, setInstance] = useState('')
  const [cost, setCost] = useState(row.costMin)
  const id = useId()
  const purchase = PurchasableTraits.InstancedPurchase({
    row: row.name,
    variant: enumerated ? variant : null,
    instance: enumerated ? null : trim(instance) === '' ? null : trim(instance),
    cost: enumerated ? row.variants.find(([name]) => name === variant)?.[1] ?? row.costMin : cost,
  })
  const licensed = PurchasableTraits.instanced(purchase)
  return (
    <Dialog
      onDismiss={onDismiss}
      confirm={<InkButton label="Buy" onClick={() => onBuy(purchase)} disabled={licensed === null} />}
      dismiss={<CloseButton onClick={onDismiss} label="Cancel" />}
      title={<FormTitle form={null}>{licensed?.name ?? row.name}</FormTitle>}
    >
      <p className="bodySmall">{row.notes}</p>
      {enumerated ? (
        <>
          <p className="bodySmall" style={{ paddingTop: 8 }}>The book prices each of these itself.</p>
          <Chips>
            {row.variants.map(([name, points]) => (
              <CaseChip key={name} selected={variant === name} onClick={() => setVariant(name)}>{name} — {pointsLabel(signedCost(row.advantage, points))}</CaseChip>
            ))}
          </Chips>
        </>
      ) : (
        <>
          <label htmlFor={id} style={{ display: 'block', paddingTop: 8 }}>
            <Caps text={PurchasableTraits.needsInstance(row) ? 'Name it — the book asks you to' : 'What is it? The book prices this row by the instance'} />
          </label>
          <input id={id} className="text-field bodyLarge" value={instance} autoComplete="off" onChange={(e) => setInstance(e.target.value)} />
          <div className="row" style={{ paddingTop: 8 }}>
            <button className="text-button titleLarge" disabled={cost <= row.costMin} onClick={() => setCost(cost - 1)} aria-label="Lower the price">−</button>
            <span className="bodyLarge grow" style={{ textAlign: 'center', color: 'var(--on-surface)' }}>{pointsLabel(signedCost(row.advantage, cost))}</span>
            <button className="text-button titleLarge" disabled={cost >= row.costMax} onClick={() => setCost(cost + 1)} aria-label="Raise the price">+</button>
          </div>
          <p className="bodySmall">The book prints {row.costMin} to {row.costMax} for this row.</p>
        </>
      )}
      <p className="bodySmall err" style={{ paddingTop: 8, minHeight: 48 }}>
        {licensed !== null ? '' : enumerated ? 'Name which one the character has.' : 'The book prices this row by what it actually is, so name the instance before buying it.'}
      </p>
    </Dialog>
  )
}

export function LanguagePicker({ owned, priced, onBuy, onDismiss }: {
  owned: OwnedLanguage[]
  priced: (l: SavedLanguage) => OwnedLanguage | null
  onBuy: (l: SavedLanguage) => void
  onDismiss: () => void
}) {
  const [name, setName] = useState<string | null>(null)
  const [proficiency, setProficiency] = useState(Languages.FLUENT)
  const [form, setForm] = useState(Languages.BOTH_FORMS)
  const candidate: SavedLanguage | null = name === null ? null : { language: name, proficiency, form }
  const price = candidate !== null ? priced(candidate) : null
  const isOwned = owned.some((it) => it.purchase.language === name)
  const entry = Languages.all().find((it) => it.name === name) ?? null
  const group = (title: string, languages: { name: string }[]): ReactNode => (
    <>
      <p className="titleSmall" style={{ paddingTop: 8, color: 'var(--on-surface)' }}>{title}</p>
      <Chips>
        {languages.map((language) => (
          <CaseChip
            key={language.name}
            selected={name === language.name}
            onClick={() => {
              setName(language.name)
              const had = owned.find((it) => it.purchase.language === language.name)
              if (had) {
                setProficiency(had.purchase.proficiency)
                setForm(had.purchase.form)
              }
            }}
          >
            {language.name}
          </CaseChip>
        ))}
      </Chips>
    </>
  )
  const rows = entry !== null ? Languages.proficiencies(entry.type) : []
  return (
    <Dialog
      onDismiss={onDismiss}
      confirm={<InkButton label={isOwned ? 'Change' : 'Buy'} onClick={() => candidate && onBuy(candidate)} disabled={price === null} />}
      dismiss={<CloseButton onClick={onDismiss} />}
      title={<FormTitle form={null}>Languages</FormTitle>}
    >
      <SteadyBody>
        <div className="scroll">
          {group('Common', Languages.common())}
          {group('Mystical', Languages.mystical())}
          {entry === null ? (
            <p className="bodySmall" style={{ paddingTop: 8 }}>Pick a language. The book prints two price tables, and which one applies is the language's.</p>
          ) : (
            <>
              <p className="bodySmall">{entry.notes}</p>
              <p className="titleSmall" style={{ paddingTop: 8, color: 'var(--on-surface)' }}>Proficiency</p>
              <Chips>
                {rows.map((row) => (
                  <CaseChip key={row.proficiency} selected={proficiency === row.proficiency} onClick={() => setProficiency(row.proficiency)}>{row.proficiency} {row.points}</CaseChip>
                ))}
              </Chips>
              {rows.filter((it) => it.proficiency === proficiency).map((it) => <p key={it.proficiency} className="bodySmall">{it.effect}</p>)}
              <p className="titleSmall" style={{ paddingTop: 8, color: 'var(--on-surface)' }}>Form</p>
              <Chips>
                {Languages.forms().map((option) => <CaseChip key={option} selected={form === option} onClick={() => setForm(option)}>{option}</CaseChip>)}
              </Chips>
              <p className="titleMedium" style={{ paddingTop: 8, color: 'var(--on-surface)' }}>
                {price !== null ? `${Languages.ownedLabel(price)} — ${Languages.costLine(price)}` : 'The book prints no such row.'}
              </p>
              <p className="bodySmall">
                The price above is what the budget bills. The penalties to language-dependent skill rolls are the GM's: the book tags no skill as language-dependent.
              </p>
            </>
          )}
        </div>
      </SteadyBody>
    </Dialog>
  )
}
