package com.pathways.app

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.model.Weapon
import com.pathways.rules.ClauseBonus
import com.pathways.rules.ActiveDefenses
import com.pathways.rules.Facing
import com.pathways.rules.ParryBlock
import com.pathways.rules.content.CataloguedWeapon
import com.pathways.rules.RandomSource
import com.pathways.rules.Shields
import com.pathways.rules.SkillDefaults
import com.pathways.rules.SkillRoll
import com.pathways.rules.UnarmedStyle
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.Shield

/**
 * Active defenses for the character as they stand (Ch. 5, p. 36-37).
 *
 * Every number comes from [ParryBlock] or [com.pathways.rules.Derive] --
 * nothing is computed here. What the dialog adds is the pairing: which of the
 * character's own skills can defend, and what the book prints beside each
 * formula ("Cumulative -4 per additional parry", "Once per round").
 *
 * A shield's DB used to be the one figure the app could not know, shown as an
 * explicit "+ shield DB". The book prints four of them, so it is picked here
 * and added to ALL THREE defences -- "its DB adds to all active defenses
 * (Dodge, Parry, and Block)", not to Block alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DefenseDialog(
    dodge: Int,
    skillLevels: Map<SkillKey, Int>,
    /**
     * DX as played, after potions: the second operand of the Parry (Unarmed)
     * formula cell. Required, because a default here would be a parry.
     */
    dx: Int,
    /**
     * The skills this character may attempt untrained, as the sheet composed
     * them. Read for Acrobatics alone -- see [acrobaticsFor].
     */
    untrained: List<SkillDefaults.Untrained>,
    weapons: List<CataloguedWeapon> = emptyList(),
    /**
     * A Twilight Giant's weapon line, or null. Required: a carried weapon in
     * a master's hand parries at DX, and a silent default would drop that.
     */
    mastery: SkillDefaults.WeaponMastery?,
    /** The dice an Acrobatic Dodge is rolled with. */
    dice: RandomSource,
    /**
     * What the trackers cost a roll of a given skill.
     *
     * The same ledger the attack dialog takes, and for the same reason: an
     * Acrobatic Dodge is an ordinary skill roll, so rolling it against a bare
     * level would answer differently from the sheet screen for one character at
     * one moment -- the one-rule-two-sites class (6ag, 6ai, 6be). Passed as the
     * function rather than a resolved list because the modifiers are scoped per
     * skill ([com.pathways.rules.Conditions]).
     */
    // Qualified rather than imported: `Modifier` is Compose's here, and the
    // attack dialog's own ledger parameter is written the same way.
    conditions: (SkillKey) -> List<com.pathways.model.Modifier>,
    /**
     * The character's own traits.
     *
     * The TRAITS rather than the +1 they produce, because two different rules
     * read them here: Combat Reflexes adds to every defence AND cancels the
     * surprise refusal. Deriving the bonus at the call site and the refusal
     * here would put one trait's consequences in two places.
     */
    traits: List<Trait> = emptyList(),
    /**
     * Charges the FP a defence boost costs.
     *
     * A cost that is only printed is a tracker that lies -- the FP-to-HP rule
     * (6b) and the Soul Debt (6m) were both found that way. Required rather
     * than defaulted: no value is safe when it is wrong.
     */
    onSpendFp: (Int) -> Unit,
    /**
     * Whether the character stands at p. 42's "1/3 FP or below".
     *
     * The FACT, not the number: [dodge] arrives already halved from the one
     * place that owns the threshold ([com.pathways.rules.Pools]), and this
     * says so on screen. Re-deriving it here would put one rule at two sites,
     * which is how the sheet came to print "Move and Dodge halved" beside a
     * dialog offering the whole thing.
     */
    fatigued: Boolean = false,
    /**
     * The Major Wound's shock, latched by the damage dialog. Not a chip here:
     * it is the same one-turn fact every other screen reads, and a second
     * control for it could disagree with them (Codex, at design time).
     */
    shocked: Boolean = false,
    /**
     * What the HP and FP trackers cost every row, from
     * [com.pathways.rules.Conditions.penaltyFor].
     *
     * The NUMBER here, unlike [fatigued] beside it: the halving is a fact this
     * screen states in words, and this is a term the rows add. It arrives
     * already decided for the same reason [dodge] does -- one place owns what
     * a pool costs.
     */
    poolPenalty: Int,
    /**
     * The held rows' declared "-N to all rolls" (6gs), on every defence row
     * through [ActiveDefenses.everyDefence]. The Acrobatic Dodge is a skill
     * roll and takes it through [conditions] instead, never both.
     */
    states: ClauseBonus = ClauseBonus(0, null),
    onDismiss: () -> Unit,
) {
    // Everything p. 36 scopes to a round, held together so the boundary can
    // move all of it at once: the refusals the GM declares (none is computable
    // -- the app holds no turn, no initiative and no line of sight), the three
    // free actions, the parry count and the paid boost. See [RoundState].
    var round by remember { mutableStateOf(RoundState()) }
    // Stunned is a DECLARATION for the same reason, and this app is where the
    // state comes from: its own fright, major-wound and ritual-critical tables
    // all hand it out. It is not a DefenseRefusal -- it prices the defences
    // rather than withdrawing them.
    var stunned by remember { mutableStateOf(false) }
    val everyDefence = remember(traits, stunned, shocked, poolPenalty, states) {
        ActiveDefenses.everyDefence(traits, stunned, shocked, poolPenalty, states.total)
    }
    val refusal = ActiveDefenses.state(round.declared, traits)
    val forfeiting = remember { ActiveDefenses.forfeitingActions() }
    val printed = remember { ContentLoader.defenses() }
    // Only the bullets this app does NOT apply. The Acrobatic Dodge row is
    // quoted at the control that applies it, and ActiveDefenses decides which
    // half a bullet is in, so the two cannot disagree.
    val modifiers = remember { ActiveDefenses.unappliedModifiers() }
    val parries = remember(skillLevels, dx) { unarmedParries(skillLevels, dx) }
    val armed = remember(weapons, skillLevels, mastery, dx) { armedParries(weapons, skillLevels, mastery, dx) }
    val unskilled = remember(weapons, skillLevels, mastery, dx) { unskilledParriers(weapons, skillLevels, mastery, dx) }
    // The weapons whose parry the mastery changed: the rows a master gets
    // that a non-master with these skills would not, or would get lower.
    val lifted = remember(armed, weapons, skillLevels, dx) {
        (armed - armedParries(weapons, skillLevels, mastery = null, dx = dx).toSet()).map { it.first }
    }
    val shieldSkill = remember(skillLevels) {
        skillLevels.entries.firstOrNull { it.key.name == SHIELD_SKILL }?.value
    }
    // The character's own Acrobatics, or the default the sheet composed for
    // one without it. A data class and not the Map.Entry it is read from: an
    // entry is a live view of the map and compares unstably, which both keeps
    // the control from ever skipping recomposition and makes it useless as a
    // remember key -- and it is needed as one, immediately below.
    val acrobatics = remember(skillLevels, untrained) { acrobaticsFor(skillLevels, untrained) }
    // The roll as it came out, or null for "not taken this attack". Kept as the
    // whole outcome rather than the ±2 it produces, because the note under the
    // button reports the target the trackers left and the total rolled against
    // it -- a number on screen with no way to reconcile it is what 6be found.
    // Keyed on the skill it was rolled against. A roll survives every other
    // control on this screen on purpose -- the GM took it against this attack --
    // but it must not survive the LEVEL changing underneath it: the note reports
    // a target the row's ±2 would no longer match, and if the skill left the
    // sheet the roll would turn into one at its default while a bonus rolled at
    // the trained level stayed on Dodge (a review's finding, latent today
    // because the sheet is behind this dialog).
    // The roll lives in [RoundState] with the other two free actions, so the
    // guard that `remember(acrobatics)` used to give it is written out here.
    // It stays a COMPOSITION-time clear, exactly as the remember key was: a
    // LaunchedEffect fires after the pass commits, so the row would render one
    // frame carrying a roll made against a level that has already moved. And
    // keying the whole holder would discard the paid boost with it, which
    // `onSpendFp` has already charged for.
    val rolledAgainst = remember { mutableStateOf(acrobatics) }
    if (rolledAgainst.value != acrobatics) {
        rolledAgainst.value = acrobatics
        round = round.copy(acrobatic = null)
    }
    // Latched behind the round boundary now, with the other two free actions.
    // p. 36's "Each can be used once per round" governs all three, and 6bf/6co
    // refused to honour it on the grounds that clearing one while `priorParries`
    // and a paid boost stayed put would claim a round the screen could not keep.
    // Every one of those values is local to this composable, so one assignment
    // keeps the round for all of them -- see [RoundState].
    val acrobaticSwing = ActiveDefenses.acrobaticDodge(round.acrobatic?.effectiveOutcome)
    val shields = remember { Shields.all() }
    // Null is bare-handed, and it is the default: a shield must be Readied for
    // a turn before it does anything, so assuming one is carried would hand
    // out a bonus the character has not paid a turn for.
    var carried by remember { mutableStateOf<Shield?>(null) }
    // Where the attack comes from. A declaration for the same reason the three
    // refusals and the stun are: this app holds no map and no facing.
    // FRONT is the default -- the book's unpenalised case, named by printing
    // nothing for it.
    var facing by remember { mutableStateOf(Facing.FRONT) }
    val coverage = Shields.coverageAt(facing)
    // The shield the DB is read from, which is NOT the shield that is Readied:
    // at the rear the book withholds the bonus. Derived ONCE and passed to
    // every row, because the Block row builds its arrow by hand and a rule
    // applied at six sites out of seven is this codebase's oldest defect
    // (6ag, 6ai, 6be) -- a design review named this exact row.
    val shieldForDb = shieldForDb(carried, facing)
    // What is coming AT the character. A declaration for the same reason the
    // three refusals are: this dialog holds the defender's sheet and the
    // attacker is a party with no numbers here. It changes two of the Parry
    // (Unarmed) row's three rules -- the -3 for a weapon, and the refusal
    // against a shot -- both printed since 6g and applied by nothing.
    var incoming by remember { mutableStateOf(Incoming.BARE_HAND) }
    // Whether this is a shot, derived ONCE from the one control that says so.
    // Two free actions read it and they read it in opposite directions -- the
    // Dive! is "vs. ranged only" and the Retreat is "vs. 1 melee attack" --
    // which is one fact in two spellings. Spelling it twice is the defect
    // rather than the drift it eventually produces (6cn).
    val isRanged = incoming == Incoming.SHOT
    // Each rule answers with its own scopes already applied, and each answer is
    // reused below, including by the controls' own notes: re-testing `incoming`
    // there would be the same predicate at two sites.
    val diveBonus = ActiveDefenses.dive(round.dived, isRanged)
    // Three figures because the bullet prints three, against three named rows.
    val retreatDodge = ActiveDefenses.retreat(round.retreated, !isRanged, ActiveDefenses.Retreat.DODGE)
    val retreatParry = ActiveDefenses.retreat(round.retreated, !isRanged, ActiveDefenses.Retreat.PARRY)
    val retreatBlock = ActiveDefenses.retreat(round.retreated, !isRanged, ActiveDefenses.Retreat.BLOCK)
    // ORDINARY for every row, and that is not a flattening: chapter 8's nine
    // weapons are five firearms, a knife, a truncheon, a hand axe and a
    // broadsword, and none of them is a fencing weapon or a rapier. `Weapon`
    // carries no tier because the book prints no column for one, and adding
    // it here would invent data -- the call 6t already made about which
    // weapons can parry at all. The two other tiers stay modelled in
    // [ParryClass] for the GM's own smallsword, and the note under these rows
    // says on screen that they exist and that nothing here reaches them.
    val parryPenalty = ParryBlock.parryPenalty(round.priorParries)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Active defenses") },
        text = {
            LazyColumn {
                item {
                    // At the TOP, and unlike the parry stepper that is not a
                    // placement quibble: a refusal withdraws EVERY defence, so
                    // a control tucked under the parry rows would read as
                    // scoped to them.
                    // In the content and at the TOP, not in the button bar: it
                    // moves every value below it, which is this screen's own
                    // placement rule (StunPicker sits above the rows "because
                    // it moves all of them"), and a chip beside "Close" reads
                    // as Cancel while a mis-tap would drop a boost the GM has
                    // already paid FP for. It does NOT clear the stun -- that
                    // has its own clock and its own recovery roll.
                    NewRoundControl(round) { round = round.newRound() }
                    RefusalPicker(round.declared, forfeiting) { cause ->
                        round = round.copy(
                            declared = if (cause in round.declared) {
                                round.declared - cause
                            } else {
                                round.declared + cause
                            },
                        )
                    }
                    refusal.printed?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    // The book's narrower printing of the same rule, with its
                    // scope said out loud rather than quoted as if general.
                    refusal.narrower?.let {
                        DefenseNote("Page 37 prints it narrowly — \"$it\" — and names only the All-Out Attacks.")
                    }
                    // A cancelled cause is SAID, not silently dropped: a GM who
                    // ticks "surprised" and sees nothing change would fairly
                    // conclude the tick failed.
                    refusal.negated?.let {
                        DefenseNote("${refusal.negatedBy} — \"never freeze in surprise\", so the defences stand.")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
                if (!refusal.defends) return@LazyColumn
                item {
                    // Under the refusal chips and above every row, because it
                    // moves all of them -- and inside this block rather than
                    // the one above, so a withdrawn set of defences does not
                    // offer a -4 on nothing.
                    StunPicker(stunned) { stunned = !stunned }
                    // Beside the stun, because it is the other thing already
                    // inside every row below -- and inside this same block, so
                    // a withdrawn set of defences does not announce a penalty
                    // on nothing (6cs: a block inherits the branch it lands
                    // in). Announced rather than left silent: a target that
                    // moves without the screen saying why is how these numbers
                    // stopped reconciling in 6ag and 6ai.
                    if (poolPenalty != 0) {
                        DefenseNote(
                            "Hurt or tired: $poolPenalty to every defence below, " +
                                "from the HP and FP thresholds on the sheet (pp. 40 and 42).",
                        )
                    }
                    states.line?.let {
                        DefenseNote("Declared on the sheet: $it to every defence below.")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    // Above the rows it changes, unlike the parry stepper: it
                    // moves the unarmed parries AND withdraws them, so a
                    // control below them would be read after its effect.
                    IncomingPicker(incoming) { incoming = it }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    // Beside IncomingPicker because both describe the attack
                    // rather than the defender, and above the shield picker
                    // because it decides what that picker's note can say.
                    FacingPicker(facing) { facing = it }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    // ABOVE every row, because unlike its two neighbouring
                    // bullets this one moves all of them -- the placement rule
                    // StunPicker follows. BELOW IncomingPicker, because its
                    // number depends on that declaration and its refusal note
                    // points back up at it. Between the two, it sits with the
                    // defender's own kit rather than splitting the pair of
                    // pickers that describe the attack.
                    RetreatControl(taken = round.retreated, bonus = retreatDodge) {
                        round = round.copy(retreated = !round.retreated)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    ShieldPicker(shields, carried, coverage) { carried = if (it == carried) null else it }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    DefenseRow(
                        "Dodge",
                        // The acrobatic ±2 and the Dive!'s +3 join the bought
                        // +2 on this row and nowhere else: both bullets name
                        // Dodge and no other defence, so they stay out of
                        // `everyDefence`, which is reserved for rules printed
                        // plural. They compose rather than exclude each other
                        // — p. 36 says the free actions "can be taken alongside
                        // any active defense", and refusing to stack two of
                        // them while the same paragraph's "once per round" goes
                        // unenforced would author the narrower rule of the two.
                        // The Retreat's +3 joins them on the strength of a
                        // sentence printed in the acrobatic bullet's own cell:
                        // "Stacks with Retreat." -- corroboration for this row
                        // rather than the general line, and pinned by a test.
                        shielded(
                            dodge + boostOn("Dodge", round.boost) + acrobaticSwing + diveBonus + retreatDodge,
                            shieldForDb,
                            everyDefence,
                        ),
                    )
                    printed.firstOrNull { it.defense == "Dodge" }?.let { DefenseNote(it.notes) }
                    // Under the row it moved, like the Acrobatic Dodge's own
                    // bullet: a halved number with no reason beside it reads as
                    // a bug. The rounding is named because the book does not
                    // print one for this row.
                    if (fatigued) {
                        DefenseNote(
                            "At 1/3 FP or below: \"Move and Dodge halved\" (p. 42). " +
                                "Halved above, rounded up — the book prints no direction.",
                        )
                    }
                    // Directly under the only row it moves. The bullet itself
                    // is quoted here rather than left in the Modifiers list at
                    // the bottom, which now shows only what this app does NOT
                    // apply -- one sentence printed in both places, with
                    // neither marked, is 6bl's defect on this same screen.
                    val acrobaticStanding = acrobatics?.let { acrobaticTarget(it, conditions, traits) }
                    AcrobaticDodge(
                        skill = acrobatics,
                        standing = acrobaticStanding,
                        rolled = round.acrobatic,
                        onRoll = {
                            acrobaticStanding?.let { printed ->
                                round = round.copy(acrobatic = SkillRoll.rollAgainst(printed, dice))
                            }
                        },
                        onClear = { round = round.copy(acrobatic = null) },
                    )
                    // Beside the acrobatic roll, because the two bullets move
                    // the same row and a GM comparing them should not have to
                    // scroll between them.
                    DiveControl(taken = round.dived, bonus = diveBonus) {
                        round = round.copy(dived = !round.dived)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
                items(armed) { (name, value) ->
                    val label = "Parry ($name)"
                    DefenseRow(
                        label,
                        shielded(
                            parryScore(value, parryPenalty, boostOn(label, round.boost), retreatParry),
                            shieldForDb,
                            everyDefence,
                        ),
                    )
                }
                item {
                    if (armed.isNotEmpty()) {
                        printed.firstOrNull { it.defense == "Parry (Armed)" }?.let { DefenseNote(it.notes) }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                }
                // ParryBlock decides whether a shot can be parried, not this
                // screen: a dialog holding its own opinion about a printed
                // refusal is the two-copies failure (6ag, 6ai, 6be), and a
                // predicate nothing calls is decoration (6ai).
                val standing = parries.filter {
                    incoming != Incoming.SHOT || ParryBlock.canParryRanged(it.second)
                }
                items(standing) { (name, style, value) ->
                    // "Brawling parries weapons at -3", and Boxing alone is
                    // exempt. Composed with the cumulative penalty rather than
                    // replacing it: a second parry, of a weapon, is both.
                    val vsWeapon =
                        if (incoming == Incoming.WEAPON) ParryBlock.versusWeaponPenalty(style) else 0
                    val label = "Parry ($name)"
                    DefenseRow(
                        label,
                        shielded(
                            parryScore(
                                value,
                                parryPenalty,
                                boostOn(label, round.boost),
                                retreatParry,
                                vsWeapon,
                            ),
                            shieldForDb,
                            everyDefence,
                        ),
                    )
                }
                item {
                    // The DX row's two readings, said beside it. Until 6ev
                    // a note here called a sheet with no unarmed skill "a
                    // gap" in defaults, under a cell that prints DX.
                    //
                    // Behind a tap with the mastery's below (the redesign): both
                    // are this app's readings of a formula cell, never a target,
                    // a cost or a refusal, and the rows they explain stay open.
                    if (standing.any { it.second == UnarmedStyle.BARE_HANDS }) {
                        val formula = printed
                            .firstOrNull { it.defense.startsWith("Parry (Unarmed)") }
                            ?.formula
                        RuleNote("Why the bare-hand parry is DX itself") {
                            DefenseNote(
                                "Parry ($BARE_HANDS) is the formula cell's other operand" +
                                    (formula?.let { ", \"$it\"" } ?: "") +
                                    ": DX itself, not a default, so nothing is taken off " +
                                    "and Versatile adds nothing. Against a weapon it takes " +
                                    "Brawling's −3. That is a reading: the cell names " +
                                    "Brawling and exempts Boxing alone.",
                            )
                        }
                    }
                    // What is still not offered, named rather than absent.
                    if (unskilled.isNotEmpty()) {
                        DefenseNote(
                            "No parry offered with ${unskilled.joinToString(", ")}: no " +
                                "skill on this sheet wields it, and the Parry (Armed) " +
                                "row prints Weapon Skill ÷ 2 + 3 with no second operand. " +
                                "Whether a default parries is a gap here and not a ruling.",
                        )
                    }
                    // Said wherever the mastery changed a number, because
                    // the book's own examples are attacks and this is the
                    // reading that carries the line to a parry (6ey).
                    if (mastery != null && lifted.isNotEmpty()) {
                        RuleNote("Why a master's weapon parries at DX") {
                            DefenseNote(
                                "${lifted.joinToString(", ")}: Weapon Skill no less than DX " +
                                    "before the halving, from ${mastery.quoted}'s \"treated as " +
                                    "known at effective DX\", read as the Parry (Armed) " +
                                    "row's operand. A reading: the book's examples are attacks.",
                            )
                        }
                    }
                    // A refusal, not a penalty, so the rows are withdrawn and
                    // the reason is SAID -- a GM who picks "a shot" and sees
                    // the parries simply vanish would think the app lost them.
                    if (standing.isEmpty() && parries.isNotEmpty()) {
                        DefenseNote("No unarmed parry against a shot: \"Cannot parry ranged attacks.\"")
                    }
                    printed.firstOrNull { it.defense.startsWith("Parry (Unarmed)") }
                        ?.let { DefenseNote(it.notes) }
                    // The stepper sits UNDER the parry rows, because it moves
                    // them and a control above them would read as applying to
                    // Dodge too -- which takes no such penalty ("May be used
                    // repeatedly").
                    // Withdrawn against a shot along with the rows it moves:
                    // an armed parry is not covered by that sentence, so it
                    // survives, and a stepper over nothing would be a control
                    // that does nothing.
                    if (armed.isNotEmpty() || standing.isNotEmpty()) {
                        ParriesThisRound(round.priorParries, parryPenalty) {
                            round = round.copy(priorParries = it)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    if (shieldSkill != null) {
                        // shieldForDb, NOT carried: this row builds its arrow
                        // by hand rather than through shielded(), so gating
                        // only there would leave the DB standing on exactly
                        // the row the change is for (a design review's find).
                        val held = shieldForDb
                        val bare = ParryBlock.blockFor(shieldSkill, shieldDb = 0)
                        val boosted = boostOn("Block", round.boost)
                        if (held == null) {
                            DefenseRow("Block", shielded(bare + boosted + retreatBlock, null, everyDefence))
                            // Two ways to have no DB, and they are different
                            // facts: nothing Readied, or something Readied
                            // that the book withholds from behind.
                            DefenseNote(
                                if (carried == null) {
                                    "Pick a shield above; Block needs one Readied."
                                } else {
                                    "No DB from behind: \"${Shields.PRINTED_ARC}\"."
                                },
                            )
                        } else {
                            // Shields.blockFor already folds the DB in, so the
                            // arrow is built by hand here rather than through
                            // shielded() -- but the trait bonus is added to
                            // one bare figure and carried across, so the two
                            // ends of the arrow cannot drift apart.
                            val own = bare + everyDefence + boosted + retreatBlock
                            val melee =
                                Shields.blockFor(held, shieldSkill) + everyDefence + boosted + retreatBlock
                            DefenseRow("Block", "$own → $melee")
                            // The ranged figure is shown beside it rather than
                            // replacing it: a light shield is "-1 to Block vs.
                            // ranged" and one number cannot say both. It takes
                            // no Retreat: that bullet is "vs. 1 melee attack",
                            // so a figure for a shot must not carry it.
                            val shot =
                                Shields.blockFor(held, shieldSkill, isRanged = true) + everyDefence + boosted
                            // Asked of the SHIELD's own penalty rather than by
                            // comparing the two totals. This note exists to
                            // disclose the -1 a light shield takes against a
                            // shot, and `shot != melee` stopped being that
                            // question the moment a melee-only bonus could
                            // separate them -- a disclaimer whose trigger has
                            // drifted from its stated reason is 6bl's defect
                            // on this same screen.
                            val differsVsShot = Shields.blockPenalty(held, isRanged = true) !=
                                Shields.blockPenalty(held, isRanged = false)
                            if (differsVsShot) DefenseNote("Against ranged attacks: $shot")
                        }
                        printed.firstOrNull { it.defense == "Block" }?.let { DefenseNote(it.notes) }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                    carried?.let {
                        DefenseNote("${it.type}: DB +${it.db}, Cover DR ${it.coverDr}, HP ${it.hp} — ${it.notes}")
                    }
                    RuleNote("Shield rules") { Shields.rules().forEach { DefenseNote(it.rule) } }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    // UNDER the rows, because it names one of them: a picker
                    // above would have to be read before the labels it offers
                    // exist on screen. The rows it can name are exactly the
                    // ones rendered above -- withdrawing a parry against a
                    // shot withdraws it from here too, so no payment can buy
                    // a defence the book already refused.
                    // Block needs a shield READIED, which is what the row
                    // itself says two lines up -- so offering to buy +2 on it
                    // bare-handed contradicts our own screen (Codex). Having
                    // the SKILL is not having the shield.
                    // ...and no Shield skill means no Block row at all, so
                    // both halves gate it, exactly as the row itself does.
                    val payable = defenceLabels(
                        dodge = true,
                        armed,
                        standing,
                        block = shieldSkill != null && carried != null,
                    )
                    if (round.boost != null && round.boost?.second !in payable) {
                        DefenseNote(
                            "The defence you paid for is not available against this attack, " +
                                "so the +${ActiveDefenses.BOOST_BONUS} lands on nothing. " +
                                "The FP stays spent.",
                        )
                    }
                    BoostPicker(
                        rows = payable,
                        chosen = round.boost,
                        onPick = { next ->
                            // Charged ONLY on the transition from nothing to
                            // something. Swapping one paid chip for another
                            // charged twice while showing one +2 (Codex), and
                            // the alternatives are both wrong: refunding the
                            // first invents a rule the book does not print,
                            // and keeping both would apply two bonuses the GM
                            // sees as one. The picker clears first instead --
                            // one payment, one +2, no refunds.
                            if (round.boost == null && next != null) {
                                onSpendFp(ActiveDefenses.fpCostOf(next.first))
                            }
                            round = round.copy(boost = next)
                        },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    SectionLabel("Modifiers")
                }
                items(modifiers) { DefenseNote(it) }
            }
        },
    )
}
