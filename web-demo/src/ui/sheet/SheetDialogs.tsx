// SheetDialogs.kt: which dialog a sheet has open, and what each is handed.
import type { CharacterDice, SheetScene } from '../../app/RollLog'
import type { BeyonderSession } from '../../app/session/BeyonderSession'
import { conditionsFor, ritualConditionPenalty, shockOnSkill, skillRollTarget, spiNow, stateModifiers, type RollableSkill, type SheetModel } from '../../app/SheetModel'
import { skillKeyLabel } from '../../app/SheetText'
import * as AttributeRoll from '../../rules/AttributeRoll'
import * as EffortBurn from '../../rules/EffortBurn'
import * as Equipment from '../../rules/Equipment'
import type { Pool } from '../../rules/Pools'
import type { PremadeSheet } from '../../rules/Premades'
import type { SelfControlCheck } from '../../rules/SelfControl'
import * as SkillDefaults from '../../rules/SkillDefaults'
import * as SkillRoll from '../../rules/SkillRoll'
import * as TraitClauses from '../../rules/TraitClauses'
import { ConcentrateDialog, RestDialog, SpiritVisionDialog } from '../dialogs/CalmDialogs'
import { ContractDialog } from '../dialogs/ContractDialog'
import { FrightDialog } from '../dialogs/FrightDialog'
import { DetectionDialog } from '../dialogs/DetectionDialog'
import { DamageDialog } from '../dialogs/DamageDialog'
import { DivinationDialog } from '../dialogs/DivinationDialog'
import { RitualDialog } from '../dialogs/RitualDialog'
import { BoonDialog } from '../dialogs/BoonDialog'
import { AttackDialog } from '../dialogs/AttackDialog'
import { carriedWeapons } from '../../app/session/EditedCharacter'
import * as TraitEffects from '../../rules/TraitEffects'
import { ownedLanguages } from '../../app/session/EditedCharacter'
import * as Languages from '../../rules/Languages'
import * as Premades from '../../rules/Premades'
import * as Divination from '../../rules/Divination'
import * as Conditions from '../../rules/Conditions'
import * as TraitAttributeRolls from '../../rules/TraitAttributeRolls'
import * as FrightCheck from '../../rules/FrightCheck'
import { useCell } from '../hooks'
import '../dialogs/dialogs.css'
import * as SpiritContract from '../../rules/SpiritContract'
import { burnOffer, RollDialog } from '../dialogs/RollDialog'
import { AttributeRollDialog, SelfControlDialog, UntrainedDialog } from '../dialogs/SmallRolls'
import type { SheetDialog } from './SheetParts'

const ROLL_DIALOGS: [AttributeRoll.Kind, SheetDialog][] = [
  ['WILL', 'WILL_ROLL'], ['PER', 'PER_ROLL'], ['INTUITION', 'INTUITION_ROLL'], ['PERCEPTION', 'PERCEPTION_ROLL'],
]
export const rollDialogFor = (kind: AttributeRoll.Kind) => ROLL_DIALOGS.find(([k]) => k === kind)![1]

export function SheetDialogs({ m, sheet, session, scene, dice, open, onClose, rolling, onRolling, controlling, onControlling, pickingUntrained, onPickingUntrained }: {
  m: SheetModel
  sheet: PremadeSheet
  session: BeyonderSession
  scene: SheetScene
  dice: CharacterDice
  open: Set<SheetDialog>
  onClose: (d: SheetDialog) => void
  rolling: RollableSkill | null
  onRolling: (s: RollableSkill | null) => void
  controlling: SelfControlCheck | null
  onControlling: (c: SelfControlCheck | null) => void
  pickingUntrained: boolean
  onPickingUntrained: (v: boolean) => void
}) {
  const [stunned, setStunned] = useCell(scene.stunned)
  const [warmed, setWarmed] = useCell(scene.warmed)
  const [round] = useCell(scene.round)
  const { vitals, traits, drunk } = m
  const untrainedRows = () => SkillDefaults.offeredTo({ ...session.characterFor(sheet), beyonder: drunk })
  const mastery = drunk !== null ? SkillDefaults.masteryFor(drunk.pathway, drunk.sequence) : null
  const boughtMystical = () =>
    ownedLanguages(session.edits, Premades.toCharacter(sheet).traits).filter((it) => it.type === Languages.MYSTICAL).map(Languages.ownedLabel)
  const maxOf = (pool: Pool) => (pool === 'HP' ? vitals.maxHp : pool === 'FP' ? vitals.maxFp : vitals.maxSpi)
  const burn = (kind: EffortBurn.BurnKind, points: number) => {
    const pool: Pool = kind === 'PHYSICAL' ? 'FP' : 'SPI'
    session.spend(pool, points, maxOf(pool), m.maxCor)
  }
  const willClauses = TraitClauses.willClauses(traits)
  const standingTarget = controlling !== null ? m.standing.get(controlling.trait) : undefined
  return (
    <>
      {pickingUntrained && (
        <UntrainedDialog
          rows={untrainedRows()}
          weaponNote={drunk !== null ? SkillDefaults.weaponNote(drunk.pathway, drunk.sequence) : null}
          onPick={(row) => {
            onPickingUntrained(false)
            onRolling({ label: `${skillKeyLabel(row.key)} (untrained, ${row.source})`, base: row.level, key: row.key })
          }}
          onDismiss={() => onPickingUntrained(false)}
        />
      )}
      {rolling !== null && (
        <RollDialog
          label={rolling.label}
          base={rolling.base}
          burn={burnOffer(((k) => (k !== 'MYSTICAL' || vitals.maxSpi > 0 ? k : null))(EffortBurn.kindFor(rolling.key)), traits)}
          equipment={Equipment.referenceFor(rolling.key)}
          declarableClauses={TraitClauses.skillRollClauses(traits, rolling.key)}
          targetFor={(circumstances, declared) => skillRollTarget(m, session, rolling, circumstances, declared)}
          roll={(t) => SkillRoll.rollAgainst(t, dice(t.label))}
          onBurn={burn}
          onDismiss={() => onRolling(null)}
        />
      )}
      {controlling !== null && standingTarget !== undefined && (
        <SelfControlDialog check={controlling} standing={standingTarget} clauses={willClauses} states={m.states} dice={dice} onDismiss={() => onControlling(null)} />
      )}
      {open.has('FRIGHT') && (
        <FrightDialog
          will={vitals.will}
          iq={m.attributes.iq}
          ht={m.attributes.ht}
          clauses={[...TraitClauses.frightClauses(traits), ...willClauses]}
          willOnlyClauses={willClauses}
          recoveryBonus={(r) => FrightCheck.recoveryBonus(r, traits)}
          shock={m.shockOnAttribute}
          hurt={m.hurtOnAttribute}
          states={m.states}
          onStunned={setStunned}
          stunned={stunned}
          onQuirk={(name) => session.addQuirk(name)}
          dice={dice}
          onDismiss={() => onClose('FRIGHT')}
        />
      )}
      {open.has('DAMAGE') && (
        <DamageDialog
          hasSpirituality={vitals.maxSpi > 0}
          maxHp={vitals.maxHp}
          ht={m.attributes.ht}
          dice={dice}
          onApply={(pool, n) => session.spend(pool, n, maxOf(pool), m.maxCor)}
          onSpiritHit={(points, cor) => {
            if (vitals.maxSpi > 0) session.spend('SPI', points, vitals.maxSpi, m.maxCor)
            scene.spiritPending.set(scene.spiritPending.value + points)
            if (cor > 0) session.gainCor(cor, m.maxCor)
          }}
          onShocked={() => scene.shocked.set(true)}
          onStunned={() => setStunned(true)}
          majorWoundHt={TraitAttributeRolls.majorWoundHt(traits)}
          stunRecoveryHt={TraitAttributeRolls.stunRecoveryHt(traits)}
          hurt={Conditions.penaltyFor('MAJOR_WOUND_HT', m.hpNow, m.fpNow)}
          recoveryTarget={m.stunRecoveryTarget}
          states={m.physicalStates}
          onDismiss={() => onClose('DAMAGE')}
        />
      )}
      {open.has('RITUAL') && m.ritualLevel !== null && (
        <RitualDialog
          skillLevel={m.ritualLevel}
          boughtLanguages={boughtMystical()}
          grantedLanguages={Languages.grantedBy(drunk).map(Languages.purchaseLabel)}
          conditionPenalty={ritualConditionPenalty(m, session)}
          shock={shockOnSkill(m)}
          states={m.states}
          cor={session.cor}
          dice={dice('Ritual')}
          onCorGained={(n) => session.gainCor(n, m.maxCor)}
          onSpendSpi={(n) => session.spend('SPI', n, vitals.maxSpi, m.maxCor)}
          onSpendHp={(n) => session.spend('HP', n, vitals.maxHp, m.maxCor)}
          onDismiss={() => onClose('RITUAL')}
        />
      )}
      {open.has('ATTACK') && (() => {
        const carried = carriedWeapons(session.edits)
        return (
          <AttackDialog
            skillLevels={m.skillLevels}
            conditions={(k) => conditionsFor(m, session, k, { withDeclared: false })}
            st={m.attributes.st}
            dx={m.attributes.dx}
            dice={dice}
            onSpendFp={(n) => session.spend('FP', n, vitals.maxFp, m.maxCor)}
            onBurn={burn}
            darknessReduction={TraitEffects.darknessReduction(traits)}
            traits={traits}
            untrained={SkillDefaults.attacks(untrainedRows(), carried, m.skillLevels)}
            carried={carried}
            mastery={mastery}
            toolNote={drunk !== null ? SkillDefaults.toolNote(drunk.pathway, drunk.sequence) : null}
            states={m.physicalStates}
            stateModifiers={stateModifiers(m, true)}
            warmed={warmed}
            onWarmed={setWarmed}
            spiritPending={scene.spiritPending.value}
            pinned={round.declared.has('PINNED')}
            onDismiss={() => onClose('ATTACK')}
          />
        )
      })()}
      {open.has('BOON') && (
        <BoonDialog
          skillLevel={m.ritualLevel}
          boughtLanguages={boughtMystical()}
          grantedLanguages={Languages.grantedBy(drunk).map(Languages.purchaseLabel)}
          conditionPenalty={ritualConditionPenalty(m, session)}
          shock={shockOnSkill(m)}
          states={m.states}
          cor={session.cor}
          dice={dice('Boon')}
          onCorGained={(n) => session.gainCor(n, m.maxCor)}
          held={session.boon}
          nextBoon={(patron) => session.nextBoon(patron)}
          lost={session.isLost}
          onSpendSpi={(n) => session.spend('SPI', n, vitals.maxSpi, m.maxCor)}
          onSpendHp={(n) => session.spend('HP', n, vitals.maxHp, m.maxCor)}
          onGranted={(b) => {
            const next = session.nextBoon(b.pathway)
            if (next !== null && next.pathway === b.pathway && next.sequence === b.sequence) session.grantBoon(b)
          }}
          onDismiss={() => onClose('BOON')}
        />
      )}
      {open.has('DIVINE') && (
        <DivinationDialog
          skillLevel={m.divination.level}
          spiTrait={Divination.keyFor(m.divination).attr.type === 'Spi' ? TraitAttributeRolls.spiRollBonus(traits) : 0}
          sourceLine={Divination.sourceLine(m.divination)}
          divinerSequence={drunk?.sequence ?? null}
          conditionPenaltyAfterSpending={(cost) =>
            conditionsFor(m, session, Divination.keyFor(m.divination), { cor: 0, spiAboutToSpend: cost, withStates: false })
              .reduce((sum, it) => sum + (it.effect.type === 'Flat' ? it.effect.n : 0), 0)
          }
          shock={shockOnSkill(m)}
          states={m.states}
          dice={dice('Divination')}
          onSpendSpi={(n) => session.spend('SPI', n, vitals.maxSpi, m.maxCor)}
          onSpendHp={(n) => session.spend('HP', n, vitals.maxHp, m.maxCor)}
          onDismiss={() => onClose('DIVINE')}
        />
      )}
      {open.has('DETECT') && (
        <DetectionDialog
          shock={m.shockOnAttribute}
          hurt={m.hurtOnAttribute}
          states={m.states}
          perClauses={TraitClauses.perRollClauses(traits)}
          spiTrait={TraitAttributeRolls.spiRollBonus(traits)}
          alertness={TraitAttributeRolls.alertnessBonus(traits)}
          spi={vitals.maxSpi}
          per={vitals.per}
          spiPool={spiNow(m, session)}
          dice={dice('Detect')}
          onDismiss={() => onClose('DETECT')}
        />
      )}
      {open.has('REST') && (
        <RestDialog maxFp={vitals.maxFp} drawn={[session.fpLost, session.spiSpent]} onRest={(r, drawn) => session.rest(r, drawn)} onDismiss={() => onClose('REST')} />
      )}
      {open.has('CONCENTRATE') && (
        <ConcentrateDialog
          will={vitals.will}
          clauses={TraitClauses.concentrationClauses(traits)}
          shock={m.shockOnAttribute}
          hurt={m.hurtOnAttribute}
          states={m.states}
          dice={dice}
          onDismiss={() => onClose('CONCENTRATE')}
        />
      )}
      {open.has('SPIRIT_VISION') && m.hasSpiritVision && (
        <SpiritVisionDialog
          spiAvailable={vitals.maxSpi - session.spiSpent}
          onSpendSpi={(n) => session.usePower(n, vitals.maxSpi, m.maxCor)}
          onDismiss={() => onClose('SPIRIT_VISION')}
        />
      )}
      {open.has('CONTRACT') && m.ritualLevel !== null && drunk !== null && (
        <ContractDialog
          skillLevel={m.ritualLevel}
          conditionPenalty={ritualConditionPenalty(m, session)}
          shock={shockOnSkill(m)}
          states={m.states}
          cor={session.cor}
          sequence={drunk.sequence}
          maxContracts={SpiritContract.maxContracts(drunk)}
          dice={dice('Contract')}
          onSpendSpi={(n) => session.spend('SPI', n, vitals.maxSpi, m.maxCor)}
          onDismiss={() => onClose('CONTRACT')}
        />
      )}
      {ROLL_DIALOGS.map(([kind, dialog]) =>
        open.has(dialog) && (
          <AttributeRollDialog
            key={dialog}
            kind={kind}
            level={kind === 'WILL' ? vitals.will : kind === 'PER' ? vitals.per : vitals.maxSpi - session.spiSpent}
            spi={spiNow(m, session)}
            traits={traits}
            shock={m.shockOnAttribute}
            hurt={m.hurtOnAttribute}
            states={m.states}
            dice={dice}
            onDismiss={() => onClose(dialog)}
          />
        ),
      )}
    </>
  )
}
