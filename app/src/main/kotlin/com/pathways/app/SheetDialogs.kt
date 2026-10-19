package com.pathways.app

import com.pathways.rules.AttributeRoll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.BeyonderState
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.Modifier
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.rules.BurnKind
import com.pathways.rules.ClauseBonus
import com.pathways.rules.Conditions
import com.pathways.rules.Divination
import com.pathways.rules.EffortBurn
import com.pathways.rules.Equipment
import com.pathways.rules.FrightCheck
import com.pathways.rules.Languages
import com.pathways.rules.Pool
import com.pathways.rules.PoolStatus
import com.pathways.rules.Pools
import com.pathways.rules.PremadeSheet
import com.pathways.rules.RollKind
import com.pathways.rules.SelfControlCheck
import com.pathways.rules.Shock
import com.pathways.rules.SkillDefaults
import com.pathways.rules.SkillRoll
import com.pathways.rules.SpiritVision
import com.pathways.rules.SpiritualSenses
import com.pathways.rules.StunRecovery
import com.pathways.rules.TraitClauses
import com.pathways.rules.TraitAttributeRolls
import com.pathways.rules.TraitEffects

/**
 * Every dialog the sheet opens, drawn from the values [SheetScreen] derived.
 * State stays in the screen and is written back through the callbacks; a
 * value only these dialogs read is derived here, beside them. Reads of
 * [session]'s observable fields stay in this body, so a skipped call never
 * holds a stale balance (split step 7's design review).
 */
@Composable
internal fun SheetDialogs(
    open: Set<SheetDialog>,
    onClose: (SheetDialog) -> Unit,
    rolling: RollableSkill?,
    onRolling: (RollableSkill?) -> Unit,
    controlling: SelfControlCheck?,
    onControlling: (SelfControlCheck?) -> Unit,
    pickingUntrained: Boolean,
    onPickingUntrained: (Boolean) -> Unit,
    shocked: Boolean,
    onShocked: () -> Unit,
    /** Defend's round and Stunned, held in [SheetScene] so closing Defend keeps them. */
    roundState: MutableState<RoundState>,
    stunnedState: MutableState<Boolean>,
    sheet: PremadeSheet,
    session: BeyonderSession,
    drunk: BeyonderState?,
    vitals: Vitals,
    maxCor: Int,
    traits: List<Trait>,
    standing: Map<String, Int>,
    attributes: Attributes,
    skillLevels: Map<SkillKey, Int>,
    divination: Divination.SkillOrDefault,
    ritualLevel: Int?,
    spiritVisionForm: String?,
    hpNow: PoolStatus,
    fpNow: PoolStatus,
    shockOnAttribute: Int,
    hurtOnAttribute: Int,
    /** The held rows' declared "-N to all rolls" (6gs): the number and its words. */
    states: ClauseBonus,
    /**
     * [states] plus the states scoped to physical rolls (6gv): for the bare
     * rolls line 390 calls physical, which are the defences and the HT rolls.
     * No default, so a new dialog has to say which of the two it takes.
     */
    physicalStates: ClauseBonus,
    /** The same declaration as ledger modifiers, for a roll that is physical or is not. */
    stateModifiers: (physical: Boolean) -> List<Modifier>,
    /** [StunRecovery.targetFor] as played: the Damage dialog's and the Vitals row's one copy. */
    stunRecoveryTarget: Int,
) {
    // Every throw is logged under the dialog that rolled it (RollLog).
    val dice = LocalDice.current
    val divinationLevel = divination.level
    val divinationKey = Divination.keyFor(divination)
    // Derived once for both bare Will rolls. Two `remember`s over one input
    // is two chances to key them differently, which is the drift this file
    // records against every re-derived value (6ai).
    val willClauses = remember(traits) { TraitClauses.willClauses(traits) }
    // The Will clauses plus the ones naming concentration itself (6fa).
    val concentrationClauses = remember(traits) { TraitClauses.concentrationClauses(traits) }

    // Every printed skill this character does NOT have and may attempt at its
    // default (ch. 2 p. 10), off the same played character as the levels
    // above. Until 6et no screen could roll one.
    val untrained = remember(sheet, drunk, session.edits) {
        SkillDefaults.offeredTo(session.characterFor(sheet).copy(beyonder = drunk))
    }
    // A Twilight Giant's weapon line, read once for both combat dialogs.
    val mastery = drunk?.let { SkillDefaults.masteryFor(it.pathway, it.sequence) }

    // What the trackers are costing a roll of [key]. [cor] is a parameter
    // because RitualResolution applies the corruption ladder itself, and
    // passing it here as well would charge a corrupted caster twice.
    // [spiAboutToSpend] is SPI this roll will pay BEFORE it is rolled, which
    // ch. 6.5 pays in Step 2 and rolls in Step 3 (p. 55). Zero everywhere
    // else, where nothing is paid to make the roll at all.
    // The SPI pool as a roll will find it, [aboutToSpend] already paid; null
    // for a mortal, since Pools.spi refuses a maximum of zero.
    fun spiNow(aboutToSpend: Int = 0) =
        if (vitals.maxSpi > 0) Pools.spi(vitals.maxSpi - session.spiSpent - aboutToSpend, vitals.maxSpi) else null

    fun conditionsFor(
        key: SkillKey,
        cor: Int = session.cor,
        spiAboutToSpend: Int = 0,
        /**
         * Three callers collapse this list with `sumOf` into one number they
         * label "hurt or tired" or "circumstances". Shock is neither, and nor
         * is a declared trait state (6gs), so they take both as their own
         * arguments and name them; folding either into a bucket would move a
         * target under the wrong reason (Codex).
         */
        withStates: Boolean = true,
        /**
         * The declared states alone. The Attack dialog reads its tracker line
         * off the ledger by id, so it takes them among its own terms instead.
         */
        withDeclared: Boolean = withStates,
    ) = Conditions.forSkill(
        skill = key,
        hp = hpNow,
        fp = fpNow,
        spi = spiNow(spiAboutToSpend),
        cor = cor,
    ) + (if (withStates) Shock.modifiers(shocked, traits) else emptyList()) +
        if (withDeclared) stateModifiers(EffortBurn.isPhysical(key)) else emptyList()

    // The same number the ledger would have carried, for the three dialogs
    // that reduce it to an Int and print their own breakdown.
    val shockOnSkill = Shock.penaltyFor(RollKind.SKILL, shocked, traits)
    // Ether Body Damage's "-1 to all SPI-based rolls". The ledger carries it
    // for an SPI-controlled SKILL, but two rolls reach their target another
    // way -- a detection roll takes a bare attribute, and a divination builds
    // its target through Divination.targetFor -- so they are handed the number
    // here. Decided ONCE, for the same reason shock is: an adapter left
    // unwired looks like an oversight rather than a decision (Codex, 6bo).
    val spiTrait = TraitAttributeRolls.spiRollBonus(traits)

    // Ch. 2's burn, charged the moment it is bought. The book allows it after
    // the result is seen, so the pool moves when the player pushes -- printing
    // the rule and not charging it is how a tracker starts lying (6m).
    //
    // ONE lambda for both dialogs that offer the push. The attack dialog used
    // to charge only the physical half, so a mystical push there would have
    // moved the target for free; two copies of "which pool does this kind
    // spend" is the same-predicate-at-two-call-sites defect, and the cure is
    // to answer it once (a design review, on this slice's decomposition).
    val burn: (BurnKind, Int) -> Unit = { kind, points ->
        val pool = if (kind == BurnKind.PHYSICAL) Pool.FP else Pool.SPI
        val max = if (pool == Pool.FP) vitals.maxFp else vitals.maxSpi
        session.spend(pool, points, max, maxCor)
    }

    if (pickingUntrained) {
        UntrainedDialog(
            rows = untrained,
            weaponNote = drunk?.let { SkillDefaults.weaponNote(it.pathway, it.sequence) },
            onPick = { row ->
                onPickingUntrained(false)
                // The source rides in the label, so the roll says it is a
                // default and which one: "Stealth (untrained, DX-5)".
                onRolling(
                    RollableSkill(
                        "${row.key.label()} (untrained, ${row.source})",
                        row.level,
                        row.key,
                    ),
                )
            },
            onDismiss = { onPickingUntrained(false) },
        )
    }

    rolling?.let { skill ->
        RollDialog(
            label = skill.label,
            base = skill.base,
            // Null for every skill but two. Chapter 4 prints a result table
            // for Spiritual Intuition and Spiritual Perception and for
            // nothing else, so the rest keep Appendix A's general ladder.
            sense = SpiritualSenses.forSkill(skill.key),
            // Which pool may push this roll, decided by the skill's own
            // controlling attribute. Null for IQ, Will and Per, which the
            // book's two rows name neither of -- so no burn is offered
            // rather than fatigue being allowed on a Research roll.
            burnKind = EffortBurn.kindFor(skill.key)
                ?.takeIf { it != BurnKind.MYSTICAL || vitals.maxSpi > 0 },
            // The pool's BALANCE, not its maximum. The gate above excludes a
            // mortal, who never had SPI; this excludes a Beyonder who has
            // spent it, which the gate above cannot see.
            spiAvailable = vitals.maxSpi - session.spiSpent,
            conditionalBurnNote = EffortBurn.conditionalScope(skill.key),
            // Chapter 8's gear that names this exact skill, printed as the
            // book prints it. Not a chip: the save records weapons carried
            // and no other gear, so the screen cannot assert possession, and
            // two of these rows would stack into a bonus the book never
            // grants. Rules decides which rows the sentence names.
            equipment = Equipment.referenceFor(skill.key),
            // Offered only where the book's own cells apply: a social skill,
            // or the one skill a row names, on a character carrying a row
            // that names a circumstance. One rules list, which `modifiers`
            // also reads, so the chips cannot disagree with the dice (6ff).
            declarableClauses = TraitClauses.skillRollClauses(traits, skill.key),
            targetFor = { circumstances, declaredClauses ->
                // The trackers reach the dice here: a reeling, fatigued or
                // corrupted character no longer rolls at full skill. The GM's
                // circumstances are a separate source and simply join the list.
                SkillRoll.targetFor(
                    label = skill.label,
                    base = skill.base,
                    key = skill.key,
                    // Three sources now: what the trackers cost (Conditions),
                    // what the character IS (TraitEffects -- Alertness on a
                    // Per roll, Fit on an HT one), and what the GM says the
                    // situation is.
                    mods = conditionsFor(skill.key) +
                        TraitEffects.modifiers(traits, skill.key, declaredClauses) +
                        circumstances,
                )
            },
            roll = { SkillRoll.rollAgainst(it, dice.dice(it.label)) },
            onBurn = burn,
            onDismiss = { onRolling(null) },
        )
    }
    controlling?.let { check ->
        // Null only if the trait left the list under an open dialog, which
        // no path does today: then there is no roll to show.
        standing[check.trait]?.let { target ->
            SelfControlDialog(check = check, standing = target, clauses = willClauses, states = states) {
                onControlling(null)
            }
        }
    }

    if (SheetDialog.FRIGHT in open) {
        // The traits, not a number: this dialog was the one roll-building
        // boundary that discarded them, so five catalogue rows that modify a
        // Fright Check could not reach the roll they name (Codex, on the
        // design). [TraitEffects] decides which apply and by how much; the
        // screen only asks which circumstances hold.
        FrightDialog(
            will = vitals.will,
            // A Fright Check IS a bare Will roll, so the rows that say
            // "Will rolls" reach it as well as the ones that name the
            // Fright Check itself. Concatenated rather than merged:
            // `TraitClause.key` is "shown / printed" and the two lists
            // share no row, so no declaration can move two clauses.
            iq = attributes.iq,
            // Row 12's fright cell counts its duration down from HT while
            // rolling Will, so the screen needs an attribute no roll here
            // takes. [FrightCheck.durationLine] is what decides which.
            //
            // From `attributes`, which is `effectiveAttributes` -- the levels
            // AFTER any potion, the same source the Major Wound's HT roll
            // takes. `will` above comes off `vitals` and is post-potion too;
            // the two read from different objects because Will is a SECONDARY
            // and HT is bought.
            ht = attributes.ht,
            clauses = remember(traits) {
                TraitClauses.frightClauses(traits) + TraitClauses.willClauses(traits)
            },
            // The recovery roll is a bare Will roll a second later, so the
            // rows that name one reach it and the ones scoped to the Fright
            // Check do not — that check is over by then.
            willOnlyClauses = willClauses,
            recoveryBonus = { FrightCheck.recoveryBonus(it, traits) },
            shock = shockOnAttribute,
            hurt = hurtOnAttribute,
            states = states,
            onStunned = { stunnedState.value = it },
            stunned = stunnedState.value,
            onQuirk = session::addQuirk,
        ) { onClose(SheetDialog.FRIGHT) }
    }
    if (SheetDialog.CONCENTRATE in open) {
        ConcentrateDialog(vitals.will, concentrationClauses, shockOnAttribute, hurtOnAttribute, states) {
            onClose(SheetDialog.CONCENTRATE)
        }
    }
    AttributeRoll.Kind.entries.forEach { kind ->
        val dialog = rollDialogFor(kind)
        if (dialog in open) {
            AttributeRollDialog(
                kind = kind,
                level = if (kind == AttributeRoll.Kind.WILL) vitals.will else vitals.per,
                traits = traits,
                shock = shockOnAttribute,
                hurt = hurtOnAttribute,
                // Neither Will nor Per is a roll line 390 calls physical.
                states = states,
            ) { onClose(dialog) }
        }
    }
    if (SheetDialog.DAMAGE in open) {
        DamageDialog(
            // The same test that hides the SPI tracker: a mortal has no
            // spirituality to spend, so it is not offered as somewhere to
            // absorb a hit.
            hasSpirituality = vitals.maxSpi > 0,
            maxHp = vitals.maxHp,
            ht = attributes.ht,
            dice = dice,
            onApply = { pool, amount ->
                val max = when (pool) {
                    Pool.HP -> vitals.maxHp
                    Pool.FP -> vitals.maxFp
                    Pool.SPI -> vitals.maxSpi
                }
                session.spend(pool, amount, max, maxCor)
            },
            onShocked = onShocked,
            onStunned = { stunnedState.value = true },
            majorWoundHt = TraitAttributeRolls.majorWoundHt(traits),
            stunRecoveryHt = TraitAttributeRolls.stunRecoveryHt(traits),
            // The kind is named at the call site because the two states answer
            // differently for it: shock refuses this roll (the roll creates
            // it) and the pools do not (the character carried them in).
            hurt = Conditions.penaltyFor(RollKind.MAJOR_WOUND_HT, hpNow, fpNow),
            // Shock refuses the Major Wound roll (that roll makes the
            // shock) and reaches every other kind, so a character already
            // shocked from an earlier wound recovers at -4. The pools'
            // term is hurtOnAttribute: Conditions.penaltyFor answers every
            // kind but SKILL alike, so it is the `hurt` above.
            recoveryTarget = stunRecoveryTarget,
            // Both rolls here are HT rolls.
            states = physicalStates,
            onDismiss = { onClose(SheetDialog.DAMAGE) },
        )
    }
    if (SheetDialog.DEFEND in open) {
        // A plain live expression, deliberately NOT remembered: `fpLost` is
        // observable state and `vitals` is keyed on the sheet, the walk and the
        // edits, so a remembered halving would freeze at whatever the pool held
        // when one of THOSE last changed. The same reason `spiAvailable` below
        // is written out rather than cached.
        val fatigue = fpNow
        DefenseDialog(
            // p. 42: "At 1/3 FP or below ... Move and Dodge halved". The sheet
            // screen has printed that sentence since 6b; until now the dialog
            // one tap away offered the whole Dodge. Halved here, at the one
            // reader of the derived score -- the published-vs-computed row in
            // SheetComparison is an audit of the DERIVATION and stays whole.
            dodge = Pools.halvedForFatigue(vitals.dodge, fatigue),
            fatigued = Pools.isFatigued(fatigue),
            // p. 40 and p. 42's "-1 to all rolls", added to every row. The
            // halving above is the fatigue row's OTHER clause and lands on the
            // Dodge score first; this lands on top, where the stun's -4 and a
            // shield's DB already do.
            poolPenalty = Conditions.penaltyFor(RollKind.ACTIVE_DEFENSE, hpNow, fatigue),
            skillLevels = skillLevels,
            // p. 37's Parry (Unarmed) formula prints DX as an operand, and the
            // Acrobatic Dodge is rolled at the default this sheet composed.
            dx = attributes.dx,
            untrained = untrained,
            // What the player says they are CARRYING, resolved against
            // chapter 8. `Character.weapons` is the published sheet's own
            // weapon rows and no sheet prints one, so reading it here showed
            // an armed parry to nobody (see SavedEdits.weapons).
            weapons = session.edits.carriedWeapons(),
            // The same line the Attack dialog lifts a weapon with, so a
            // master's weapon parries at DX on both screens (6ey).
            mastery = mastery,
            dice = dice,
            // What the trackers cost, the first term of the sheet's own skill
            // rolls. An Acrobatic Dodge is a skill roll like any other, and
            // rolling it bare would have a reeling character roll Acrobatics at
            // two different targets depending on which screen asked (a design
            // review's finding). The dialog adds the trait term itself, from
            // `traits` below -- see acrobaticTarget.
            conditions = { conditionsFor(it) },
            // The traits themselves: Combat Reflexes both adds +1 to every
            // defence and cancels the surprise refusal, and one trait's two
            // consequences belong in one place (ActiveDefenses).
            traits = traits,
            shocked = shocked,
            // "dodging" is on line 390's own list, and a parry or block is
            // a DX skill's.
            states = physicalStates,
            onSpendFp = { session.spend(Pool.FP, it, vitals.maxFp, maxCor) },
            roundState = roundState,
            stunnedState = stunnedState,
        ) { onClose(SheetDialog.DEFEND) }
    }
    if (SheetDialog.ATTACK in open) {
        val carried = session.edits.carriedWeapons()
        AttackDialog(
            skillLevels = skillLevels,
            conditions = { conditionsFor(it, withDeclared = false) },
            st = attributes.st,
            dx = attributes.dx,
            dice = dice,
            // An All-Out maneuver's own FP is spent like any other: through
            // the tracker, so the next roll is made by a tireder character.
            // Distinct from [burn]: this is the maneuver's printed price, not
            // Ch. 2's push.
            onSpendFp = { session.spend(Pool.FP, it, vitals.maxFp, maxCor) },
            // A plain expression, deliberately NOT remembered. `spiSpent` is
            // observable state, so this recomputes on every recomposition and
            // the second attack of a Rapid Strike sees what the first one
            // spent. Captured in a `remember` it would go stale and let one
            // maneuver spend the same SPI twice (a design review's finding,
            // and invisible in a diff because the stale version looks right).
            spiAvailable = vitals.maxSpi - session.spiSpent,
            onBurn = burn,
            darknessReduction = TraitEffects.darknessReduction(traits),
            traits = traits,
            // The untrained skills this dialog can show an attack for, off
            // the same list the Skills tab offers -- one source, so the two
            // screens agree on a default wherever no weapon line applies.
            untrained = SkillDefaults.attacks(untrained, carried, skillLevels),
            // The list Defend parries with: an untrained row arms only these.
            carried = carried,
            mastery = mastery,
            toolNote = drunk?.let { SkillDefaults.toolNote(it.pathway, it.sequence) },
            // "attack rolls" lead line 390's list, whatever the skill.
            states = physicalStates,
            stateModifiers = stateModifiers(true),
            onDismiss = { onClose(SheetDialog.ATTACK) },
        )
    }
    if (SheetDialog.DIVINE in open) {
        DivinationDialog(
            skillLevel = divinationLevel,
            // Only the SPI-4 default is an SPI-based roll (Divination.keyFor),
            // and this dialog builds its target through Divination.targetFor
            // rather than the ledger, so it is handed the number.
            spiTrait = if (divinationKey.attr == ControllingAttribute.Spi) spiTrait else 0,
            sourceLine = Divination.sourceLine(divination),
            // Null for a mortal, who has no Sequence for Sec. 6 to compare.
            divinerSequence = drunk?.sequence,
            // Spiritual Attrition reaches the SPI default and not the owned
            // IQ skill; the key decides. The CoR ladder reaches neither: it
            // is scoped to ritual rolls. Asked per method cost, not computed
            // once: paying for the question can itself push the diviner
            // across a threshold, and the book charges before it rolls.
            conditionPenaltyAfterSpending = { cost ->
                conditionsFor(
                    key = divinationKey,
                    cor = 0,
                    spiAboutToSpend = cost,
                    withStates = false,
                ).sumOf { (it.effect as ModEffect.Flat).n }
            },
            shock = shockOnSkill,
            states = states,
            spiAvailable = vitals.maxSpi - session.spiSpent,
            dice = dice.dice("Divination"),
            // Through the tracker, so a question asked at 0 SPI buys the Soul
            // Debt the book charges for it.
            onSpendSpi = { session.spend(Pool.SPI, it, vitals.maxSpi, maxCor) },
            // Sec. 6's HP Sacrifice: 4+ HP forgives 2 of a target's protection.
            // Through the tracker, so a diviner who bleeds for an answer ends
            // the scene wounded.
            onSpendHp = { session.spend(Pool.HP, it, vitals.maxHp, maxCor) },
            onDismiss = { onClose(SheetDialog.DIVINE) },
        )
    }
    if (SheetDialog.DETECT in open) {
        // The other side of the divination dialog. Nothing is spent here: the
        // book calls this "a free action (once per divination attempt)" that
        // "does not cost SPI".
        DetectionDialog(
            shock = shockOnAttribute,
            hurt = hurtOnAttribute,
            states = states,
            perClauses = TraitClauses.perRollClauses(traits),
            // The SPI branch's mirror of alertness below, and equally unable
            // to be reached by SkillKey scoping.
            spiTrait = spiTrait,
            // Ch. 3's Alertness. This is a Per roll made outside SkillKey, so
            // TraitEffects.modifiers cannot reach it; passed explicitly rather
            // than left a silent gap (found in review).
            alertness = TraitAttributeRolls.alertnessBonus(traits),
            // The ATTRIBUTE, not what is left in the pool. The book rolls
            // "SPI or Per, whichever is higher" the way any attribute roll
            // goes; passing the remaining pool made a caster who had spent
            // SPI on a ritual worse at noticing, and in Soul Debt it went
            // NEGATIVE — found on the emulator, which printed "the higher of
            // SPI -2 and Per 10".
            spi = vitals.maxSpi,
            per = vitals.per,
            // Nothing is spent, so the pool as it stands: Attrition reaches
            // the roll an awareness row grants, an ability check.
            spiPool = spiNow(),
            dice = dice.dice("Detect"),
            onDismiss = { onClose(SheetDialog.DETECT) },
        )
    }
    if (SheetDialog.SPIRIT_VISION in open && spiritVisionForm != null) {
        SpiritVisionDialog(
            form = spiritVisionForm,
            // The ATTRIBUTE, not what is left in the pool -- the reading a
            // wounded Detection roll already needed. Per is the roll; SPI is
            // only what it costs.
            per = vitals.per,
            // "+1 per level to all Per rolls": this is one, as Detection is (6fe).
            alertness = TraitAttributeRolls.alertnessBonus(traits),
            // A sight by its own name: the GM says whether the eye's acuity
            // reaches it (6fh).
            visionClause = TraitClauses.visionClause(traits),
            spiAvailable = vitals.maxSpi - session.spiSpent,
            shock = shockOnAttribute,
            hurt = hurtOnAttribute,
            states = states,
            // Attrition is "-3 to all ... Beyonder ability checks", and the
            // look is rolled once the activation is paid, as a divination's
            // question is (ch. 6.5 Step 2, then Step 3).
            spi = spiNow(SpiritVision.activation()),
            dice = dice.dice("Spirit Vision"),
            // Through the tracker, so activating actually costs the point.
            onSpendSpi = { session.spend(Pool.SPI, it, vitals.maxSpi, maxCor) },
            onDismiss = { onClose(SheetDialog.SPIRIT_VISION) },
        )
    }
    if (SheetDialog.RITUAL in open && ritualLevel != null) {
        RitualDialog(
            skillLevel = ritualLevel,
            // Through the one reading of what a save owns, so the note
            // cannot name a language the budget does not bill.
            boughtLanguages = remember(sheet, session.edits) {
                session.edits.ownedLanguages(sheet.toCharacter().traits)
                    .filter { it.type == Languages.MYSTICAL }
                    .map { it.label }
            },
            grantedLanguages = remember(drunk) { Languages.grantedBy(drunk).map { it.label } },
            // cor = 0 here on purpose: RitualResolution applies the CoR
            // ladder itself, so including it would charge the caster twice
            // for the same rule.
            conditionPenalty = conditionsFor(
                key = SkillKey(
                    Conditions.RITUAL_SKILL,
                    null,
                    ControllingAttribute.Buyable(Attr.IQ),
                    Difficulty.VERY_HARD,
                ),
                cor = 0,
                withStates = false,
            ).sumOf { (it.effect as ModEffect.Flat).n },
            shock = shockOnSkill,
            states = states,
            cor = session.cor,
            spiAvailable = vitals.maxSpi - session.spiSpent,
            dice = dice.dice("Ritual"),
            onCorGained = { gained -> session.gainCor(gained, maxCor) },
            // Through the tracker, so a ritual fuelled past 0 SPI buys the
            // Soul Debt the book charges for it.
            onSpendSpi = { session.spend(Pool.SPI, it, vitals.maxSpi, maxCor) },
            // Ch. 7 IV.B: 2 HP buys 1 SPI of fuel. Through the tracker, so a
            // caster who bleeds for a ritual actually ends it wounded.
            onSpendHp = { session.spend(Pool.HP, it, vitals.maxHp, maxCor) },
            onDismiss = { onClose(SheetDialog.RITUAL) },
        )
    }
    // Ungated: without Ritualistic Magic the dialog says what is missing,
    // where an absent chip read as a feature that did not work.
    if (SheetDialog.BOON in open) {
        BoonDialog(
            skillLevel = ritualLevel,
            boughtLanguages = remember(sheet, session.edits) {
                session.edits.ownedLanguages(sheet.toCharacter().traits)
                    .filter { it.type == Languages.MYSTICAL }
                    .map { it.label }
            },
            grantedLanguages = remember(drunk) { Languages.grantedBy(drunk).map { it.label } },
            // cor = 0 for the same reason the ritual passes 0: Boons routes
            // through RitualResolution, which applies the ladder itself.
            conditionPenalty = conditionsFor(
                key = SkillKey(
                    Conditions.RITUAL_SKILL,
                    null,
                    ControllingAttribute.Buyable(Attr.IQ),
                    Difficulty.VERY_HARD,
                ),
                cor = 0,
                withStates = false,
            ).sumOf { (it.effect as ModEffect.Flat).n },
            shock = shockOnSkill,
            states = states,
            cor = session.cor,
            spiAvailable = vitals.maxSpi - session.spiSpent,
            dice = dice.dice("Boon"),
            onCorGained = { gained -> session.gainCor(gained, maxCor) },
            held = session.boon,
            nextBoon = session::nextBoon,
            lost = session.isLost,
            onSpendSpi = { session.spend(Pool.SPI, it, vitals.maxSpi, maxCor) },
            onSpendHp = { session.spend(Pool.HP, it, vitals.maxHp, maxCor) },
            // Asked again at the grant: the dialog settles as it leaves, and
            // the character may have been lost since the roll.
            onGranted = { if (session.nextBoon(it.pathway) == it) session.grantBoon(it) },
            onDismiss = { onClose(SheetDialog.BOON) },
        )
    }
}
