package com.pathways.app

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.model.Weapon
import com.pathways.rules.ActiveDefenses
import com.pathways.rules.DefenseBoost
import com.pathways.rules.DefenseRefusal
import com.pathways.rules.Facing
import com.pathways.rules.ParryBlock
import com.pathways.rules.RandomSource
import com.pathways.rules.ShieldCoverage
import com.pathways.rules.Shields
import com.pathways.rules.SkillRoll
import com.pathways.rules.SkillRollOutcome
import com.pathways.rules.UnarmedStyle
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.Shield

/**
 * The skills the book gives a parry formula of their own (Ch. 5, p. 39).
 *
 * Brawling and Boxing parry at skill/2 + 3 like a weapon; Wrestling is
 * (skill - 3)/2 + 3, with the -3 INSIDE the halving, which is why the three
 * cannot share one branch.
 */
private val UNARMED = mapOf(
    "Brawling" to UnarmedStyle.BRAWLING,
    "Boxing" to UnarmedStyle.BOXING,
    "Wrestling" to UnarmedStyle.WRESTLING,
)

/** The shield skill Block is rolled off (Ch. 5, p. 37). */
private const val SHIELD_SKILL = "Shield"

/**
 * A defence with the shield's DB shown as the conditional it is: "7 → 10".
 *
 * The bonus applies only "against attacks from the front or shield side". This
 * KDoc used to add "and this app does not model facing -- the book prints no
 * arc to test against", which was false: the arc is printed in the modifier
 * list this same dialog renders. Facing is now declared and
 * [Shields.coverageAt] decides -- [shield] arrives null when the DB is
 * withheld, so no caller adds it by hand.
 *
 * Both numbers are still shown wherever it is not withheld, because at the
 * SIDE the book settles nothing: baking the total into one number would let a
 * GM roll a shielded Dodge against a backstab (Codex), and hiding it would
 * withhold a bonus the book may well grant.
 */
private fun shielded(base: Int, shield: Shield?, everyDefence: Int = 0): String {
    // What lands on EVERY row is unconditional -- Combat Reflexes' "+1 to all
    // active defenses" and the stun's "-4 to Active Defense" -- so it is
    // folded into the base, where the shield's conditional DB cannot be. Every
    // row goes through this one function for exactly that reason: two places
    // adding the same figure is how these numbers stop reconciling (6ai).
    val own = base + everyDefence
    return if (shield == null) "$own" else "$own → ${Shields.withBonus(own, shield)}"
}

/**
 * Which shield is Readied, if any.
 *
 * Chips WRAP rather than scroll sideways: a horizontally scrolling Row inside
 * an AlertDialog never receives the drag on a 1080px phone, which cost the
 * attack dialog three of its five maneuvers before it was found on device.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShieldPicker(
    shields: List<Shield>,
    carried: Shield?,
    coverage: ShieldCoverage,
    onPick: (Shield) -> Unit,
) {
    Text("Shield Readied", style = MaterialTheme.typography.titleSmall)
    // One note per coverage state, because the old single note told the GM to
    // hand-check the rear -- which the app now does -- while saying nothing
    // about the side, which it still cannot decide. A disclaimer that outlives
    // the thing it disclaims is the defect 6bl found on this very screen.
    if (carried != null) {
        Note(
            when (coverage) {
                ShieldCoverage.COUNTS ->
                    "Arrow shows the DB applied: the book grants it from the front."
                // Deliberately NOT the COUNTS lead-in. Both states began
                // "Arrow shows the DB applied", and a GM who stops after the
                // first sentence reads the app as having decided a side the
                // book leaves open (a review's finding). The verb carries the
                // difference, in the clause a skimmer actually reaches.
                ShieldCoverage.GM_DECIDES ->
                    "Arrow OFFERS the DB — it is not applied. From the side it " +
                        "counts only if that is your shield side, and the book " +
                        "names no hand, so the call is yours."
                ShieldCoverage.WITHHELD ->
                    "No DB from behind: \"${Shields.PRINTED_ARC}\". The rows below " +
                        "are the unshielded numbers."
            },
        )
    }
    FlowRow {
        shields.forEach { option ->
            FilterChip(
                selected = option == carried,
                onClick = { onPick(option) },
                // The parenthesised examples are what makes the row long;
                // the word before them is what the GM says out loud.
                label = { Text("${option.type.substringBefore(" ")} +${option.db}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * The round boundary p. 36 names, offered only once there is something to end.
 *
 * Hidden while nothing is spent, because a control that clears nothing invites
 * the tap that later clears something -- and what it clears includes a boost
 * the GM has already paid FP for, which no refund rule in this book undoes.
 */
@Composable
private fun NewRoundControl(round: RoundState, onNewRound: () -> Unit) {
    if (!round.isSpent()) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Free actions, parries and any paid +2 are spent for this round.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(end = 8.dp),
        )
        TextButton(onClick = onNewRound) { Text("New round") }
    }
}

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
    weapons: List<Weapon> = emptyList(),
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
     * The Major Wound's shock, latched by the damage dialog. Not a chip here:
     * it is the same one-turn fact every other screen reads, and a second
     * control for it could disagree with them (Codex, at design time).
     */
    shocked: Boolean = false,
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
    val everyDefence = remember(traits, stunned, shocked) {
        ActiveDefenses.everyDefence(traits, stunned, shocked)
    }
    val refusal = ActiveDefenses.state(round.declared, traits)
    val forfeiting = remember { ActiveDefenses.forfeitingActions() }
    val printed = remember { ContentLoader.defenses() }
    // Only the bullets this app does NOT apply. The Acrobatic Dodge row is
    // quoted at the control that applies it, and ActiveDefenses decides which
    // half a bullet is in, so the two cannot disagree.
    val modifiers = remember { ActiveDefenses.unappliedModifiers() }
    // The STYLE travels with the row, because two of the three rules on the
    // Parry (Unarmed) line are per-style: Boxing alone ignores the weapon
    // penalty, and Wrestling's own -3 is already inside the number.
    val parries = remember(skillLevels) {
        skillLevels.entries
            .mapNotNull { (key, level) ->
                UNARMED[key.name]?.let { style ->
                    Triple(key.name, style, ParryBlock.unarmedParry(style, level))
                }
            }
            .sortedBy { it.first }
    }
    val armed = remember(weapons, skillLevels) { armedParries(weapons, skillLevels) }
    val shieldSkill = remember(skillLevels) {
        skillLevels.entries.firstOrNull { it.key.name == SHIELD_SKILL }?.value
    }
    // The character's own Acrobatics, key and level together: the key is what
    // the modifier ledger is asked about, and the level is what is rolled
    // against. Null is a sheet without the skill, which is NOT offered an
    // untrained roll -- see AcrobaticDodge.
    val acrobatics = remember(skillLevels) {
        skillLevels.entries
            .firstOrNull { it.key.name == ActiveDefenses.ACROBATIC_SKILL }
            // A Pair, not the Map.Entry: an entry is a live view of the map and
            // compares unstably, which both keeps the control from ever
            // skipping recomposition and makes it useless as a remember key --
            // and it is needed as one, immediately below.
            ?.let { it.key to it.value }
    }
    // The roll as it came out, or null for "not taken this attack". Kept as the
    // whole outcome rather than the ±2 it produces, because the note under the
    // button reports the target the trackers left and the total rolled against
    // it -- a number on screen with no way to reconcile it is what 6be found.
    // Keyed on the skill it was rolled against. A roll survives every other
    // control on this screen on purpose -- the GM took it against this attack --
    // but it must not survive the LEVEL changing underneath it: the note reports
    // a target the row's ±2 would no longer match, and if the skill left the
    // sheet the control would vanish while its bonus stayed on Dodge (a review's
    // finding, latent today because the sheet is behind this dialog).
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
                        Note("Page 37 prints it narrowly — \"$it\" — and names only the All-Out Attacks.")
                    }
                    // A cancelled cause is SAID, not silently dropped: a GM who
                    // ticks "surprised" and sees nothing change would fairly
                    // conclude the tick failed.
                    refusal.negated?.let {
                        Note("${refusal.negatedBy} — \"never freeze in surprise\", so the defences stand.")
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
                    printed.firstOrNull { it.defense == "Dodge" }?.let { Note(it.notes) }
                    // Directly under the only row it moves. The bullet itself
                    // is quoted here rather than left in the Modifiers list at
                    // the bottom, which now shows only what this app does NOT
                    // apply -- one sentence printed in both places, with
                    // neither marked, is 6bl's defect on this same screen.
                    AcrobaticDodge(
                        skill = acrobatics,
                        rolled = round.acrobatic,
                        onRoll = {
                            acrobatics?.let { (key, level) ->
                                round = round.copy(
                                    acrobatic = SkillRoll.at(
                                        label = key.name,
                                        base = level,
                                        key = key,
                                        source = dice,
                                        mods = conditions(key),
                                    ),
                                )
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
                            value + parryPenalty + boostOn(label, round.boost) + retreatParry,
                            shieldForDb,
                            everyDefence,
                        ),
                    )
                }
                item {
                    if (armed.isNotEmpty()) {
                        printed.firstOrNull { it.defense == "Parry (Armed)" }?.let { Note(it.notes) }
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
                            value + parryPenalty + vsWeapon + boostOn(label, round.boost) + retreatParry,
                            shieldForDb,
                            everyDefence,
                        ),
                    )
                }
                item {
                    if (parries.isEmpty() && armed.isEmpty()) {
                        Note("No unarmed combat skill on this sheet; an untrained parry is the GM's call.")
                    }
                    // A refusal, not a penalty, so the rows are withdrawn and
                    // the reason is SAID -- a GM who picks "a shot" and sees
                    // the parries simply vanish would think the app lost them.
                    if (standing.isEmpty() && parries.isNotEmpty()) {
                        Note("No unarmed parry against a shot: \"Cannot parry ranged attacks.\"")
                    }
                    printed.firstOrNull { it.defense.startsWith("Parry (Unarmed)") }
                        ?.let { Note(it.notes) }
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
                            Note(
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
                            if (differsVsShot) Note("Against ranged attacks: $shot")
                        }
                        printed.firstOrNull { it.defense == "Block" }?.let { Note(it.notes) }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                    carried?.let {
                        Note("${it.type}: DB +${it.db}, Cover DR ${it.coverDr}, HP ${it.hp} — ${it.notes}")
                    }
                    Shields.rules().forEach { Note(it.rule) }
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
                        Note(
                            "The defence you paid for is not available against this attack, " +
                                "so the +2 lands on nothing. The FP stays spent.",
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

                    Text(
                        "Modifiers",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(modifiers) { Note(it) }
            }
        },
    )
}

/**
 * The values a round boundary clears, held together because the boundary has
 * to move all of them at once.
 *
 * p. 36 scopes two rules to a round over this screen: "Each type may be used
 * multiple times per round, but Parry accumulates a -4 cumulative penalty after
 * the first" (line 2454), and "Free Actions: Retreat, Acrobatic Dodge, and
 * Dive! ... Each can be used once per round unless stated otherwise" (2456-
 * 2457). The paid boost is priced "until next turn" by its own maneuver row,
 * and the three refusals each print a scope of one turn or less -- surprise is
 * the "first turn", the All-Out Attack is the turn it was taken, and an unaware
 * shot is one attack.
 *
 * **6bf and 6co both refused this boundary, and the reason was about REACH.**
 * Latching one control while `priorParries` and a paid boost stayed put would
 * claim a round the screen could not keep for the rest. Reach is what changed:
 * every value the paragraph governs is local to one composable, so a single
 * assignment honours it for all of them -- with no global combat state, which
 * is what 6bf actually refused.
 *
 * **Membership is the rule: a field here is a value a new round CLEARS.**
 * `stunned` is deliberately not one -- the book gives it its own clock,
 * "Stunned for 1d6 seconds (roll HT each turn to recover early)" (2826) -- and
 * the shield carried, the facing and the incoming attack are not per-round
 * facts at all, which is a second reason rather than the same one.
 */
internal data class RoundState(
    /**
     * What the GM says is true of this character right now: none of the three
     * is computable, because the app holds no turn, no initiative and no line
     * of sight. See ActiveDefenses.
     */
    val declared: Set<DefenseRefusal> = emptySet(),
    /**
     * The Acrobatic Dodge as it came out, or null for "not taken". The whole
     * outcome rather than the ±2 it produces, because the note under the button
     * reports the target the trackers left and the total rolled against it --
     * a number on screen with no way to reconcile it is what 6be found.
     */
    val acrobatic: SkillRollOutcome? = null,
    /** Whether the character threw themselves flat: the second free action. */
    val dived: Boolean = false,
    /** Whether the character gave ground: the third. */
    val retreated: Boolean = false,
    /**
     * Parries already made THIS ROUND. The book's cumulative penalty was
     * committed in defenses.json's own notes cell from 6g onward, shown to the
     * GM, and applied by nothing -- the class 6h, 6m and 6ai each found. Zero
     * is the honest default: a round starts with the free parry.
     */
    val priorParries: Int = 0,
    /**
     * Which boost was paid for, and WHICH ROW it was spent on. Both printed
     * sentences say "+2 to ONE defence", so the row is part of the choice --
     * folding it into every row would buy Dodge, each Parry and Block for one
     * payment. Null is the ordinary case: nothing bought. Both price it "until
     * next turn", which is why the boundary clears it.
     */
    val boost: Pair<DefenseBoost, String>? = null,
) {
    /** A fresh round: every value the two sentences govern, back to default. */
    fun newRound(): RoundState = RoundState()

    /**
     * Whether anything the boundary would clear has actually been set.
     *
     * Compared against a fresh instance rather than field by field, so a value
     * added to this class is covered the day it is added -- the same reason
     * [newRound] is not six assignments.
     */
    fun isSpent(): Boolean = this != RoundState()
}

/**
 * The shield whose DB the rows may add, which is not always the one Readied.
 *
 * One function rather than the predicate written at each site: the Dodge and
 * Parry rows go through `shielded()` and the Block row builds its arrow by
 * hand, so the same test written twice is how this codebase's numbers stopped
 * reconciling three times (6ag, 6ai, 6be). A design review named the Block row
 * specifically -- gating only `shielded()` would have left the bonus standing
 * on the row the change exists for.
 *
 * Null means "add no DB", which the caller must not read as "no shield
 * Readied": at the rear both are null and only [carried] tells them apart.
 */
internal fun shieldForDb(carried: Shield?, facing: Facing): Shield? =
    carried?.takeIf { Shields.coverageAt(facing) != ShieldCoverage.WITHHELD }

/**
 * What a paid boost adds to the row named [label], and nothing to the rest.
 *
 * Both printed sentences say "+2 to ONE defence", so the bonus is keyed on
 * the row the GM named rather than folded into [shielded] with Combat
 * Reflexes -- which really does say "all active defenses" (6al).
 */
internal fun boostOn(label: String, boost: Pair<DefenseBoost, String>?): Int =
    if (boost?.second == label) ActiveDefenses.BOOST_BONUS else 0

/**
 * The rows a boost may be spent on: exactly the ones rendered above it.
 *
 * A parry withdrawn against a shot is not offered here either, so no payment
 * can buy a defence the book has already refused.
 */
internal fun defenceLabels(
    dodge: Boolean,
    armed: List<Pair<String, Int>>,
    unarmed: List<Triple<String, UnarmedStyle, Int>>,
    block: Boolean,
): List<String> = buildList {
    if (dodge) add("Dodge")
    armed.forEach { add("Parry (${it.first})") }
    unarmed.forEach { add("Parry (${it.first})") }
    if (block) add("Block")
}

/**
 * The Acrobatic Dodge: a roll this app can actually make (Ch. 5, p. 37).
 *
 * "[Free Action] [Acrobatic Dodge] Roll vs. Acrobatics: success gives +2 Dodge;
 * failure gives -2 Dodge." Extracted since 6g and applied by nothing -- the
 * only one of the five Defense Modifiers bullets with a die behind it.
 *
 * **Rolled through [SkillRoll], not [com.pathways.rules.SuccessRoll].** Every
 * other skill roll in this app carries what the trackers cost
 * ([com.pathways.rules.Conditions]); a bare 3d6 against the printed level would
 * have a reeling character roll Acrobatics at one number here and another on
 * the sheet screen.
 *
 * **No untrained roll, and the reason is not the one it first looked like.**
 * The book prints the untrained default as a RANGE -- `common_modifiers.json`'s
 * "Using untrained skill (default): Attribute -4 to -6", stored with no
 * number -- and settling it needs `SkillLadder.defaultFor`, which has had no
 * production caller since M0 and cannot express the per-skill overrides p. 39
 * prints for two of the three skills it can be checked against (6aw). That is a
 * policy for 158 skills, not a branch for this one, so the roll is offered to
 * the sheets that carry the skill and the rest is said out loud.
 *
 * **Re-rollable on purpose.** See the state's own comment: the "once per round"
 * limit lives in a paragraph governing all three free actions, which this app
 * neither extracts nor enforces for the other two.
 */
@Composable
private fun AcrobaticDodge(
    skill: Pair<SkillKey, Int>?,
    rolled: SkillRollOutcome?,
    onRoll: () -> Unit,
    onClear: () -> Unit,
) {
    Note(ActiveDefenses.printedAcrobaticDodge())
    if (skill == null) {
        Note("No ${ActiveDefenses.ACROBATIC_SKILL} on this sheet; an untrained roll is the GM's call.")
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onRoll) {
            Text(if (rolled == null) "Roll vs. ${ActiveDefenses.ACROBATIC_SKILL}" else "Roll again")
        }
        // The way back, which a latched roll needs as much as a paid boost does
        // (6u): the GM who rolled for the wrong attack has to be able to say so.
        if (rolled != null) {
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
    rolled?.let { outcome ->
        val roll = outcome.roll
        if (roll == null) {
            // A forbidden roll is not rolled at all, and the Dodge row above
            // is unmoved -- ActiveDefenses.acrobaticDodge answers 0 for it.
            Note("Something forbids this roll, so the Dodge row above is unchanged.")
            return@let
        }
        // Both targets, whenever they differ: the printed level and what the
        // trackers left of it. One number the GM cannot reconcile against the
        // sheet is the defect 6be found on this screen's neighbour.
        val target = if (outcome.target == outcome.baseTarget) {
            "${outcome.baseTarget}"
        } else {
            "${outcome.baseTarget} → ${outcome.target}"
        }
        // Non-null wherever the roll is: `effectiveOutcome` is null exactly when
        // nothing was rolled, which the branch above has already answered.
        val effective = outcome.effectiveOutcome ?: return@let
        val swing = ActiveDefenses.acrobaticDodge(effective)
        Note(
            "${ActiveDefenses.ACROBATIC_SKILL} $target: rolled ${roll.total} — " +
                "${label(effective, outcome.degree)}, " +
                "${if (swing > 0) "+" else ""}$swing on Dodge above.",
        )
    }
}

/**
 * The Dive!: the second Defense Modifiers bullet with a number this app can
 * reach (Ch. 5, p. 37).
 *
 * "[Free Action] [Dive!] +3 Dodge vs. ranged only. Spend 1 turn getting back up
 * (Change Posture)." Extracted since 6g and applied by nothing until now.
 *
 * A chip rather than a roll, unlike its neighbour: this bullet asks for no die,
 * only for the GM to say the character went flat. Both of its scopes are
 * decided in [ActiveDefenses.dive]; what arrives here is [bonus], the answer.
 *
 * **[bonus] is read rather than [taken] re-tested against the attack.** A zero
 * while the chip is on means the rule refused it, which is exactly the sentence
 * the note needs — and asking `incoming` a second time here is how one rule
 * becomes two disagreeing copies (6ag, 6ai, 6be).
 *
 * The turn spent getting back up is SAID and not enforced: this app holds no
 * clock (6bf), the same split [StunPicker] takes on recovering from a stun.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiveControl(taken: Boolean, bonus: Int, onToggle: () -> Unit) {
    Note(ActiveDefenses.printedDive())
    FlowRow {
        FilterChip(
            selected = taken,
            onClick = { onToggle() },
            label = { Text("Dived") },
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    if (!taken) return
    // A chip that is on while the row above it has not moved would read as a
    // tick that failed -- the reason a cancelled refusal is SAID rather than
    // silently dropped two controls up.
    if (bonus == 0) {
        Note(
            "Not against this attack: the bullet is \"vs. ranged only\", so the " +
                "+${ActiveDefenses.DIVE_BONUS} lands on nothing. Pick \"A shot\" above.",
        )
    }
    Note(
        "Getting back up costs a turn (Change Posture) — the GM's to spend; " +
            "this app holds no clock to run it against.",
    )
}

/**
 * The Retreat: the third Defense Modifiers bullet with a number, and the first
 * that moves more than the Dodge row (Ch. 5, p. 37).
 *
 * "[Free Action] [Retreat] Step back 1 hex: +3 Dodge / +1 Block / +1 Parry vs.
 * 1 melee attack per round." Extracted since 6g and applied by nothing until
 * now.
 *
 * A chip rather than a roll, like [DiveControl]: the bullet asks for no die,
 * only for the GM to say the character gave ground. Its scope is decided in
 * [ActiveDefenses.retreat], and what arrives here is [bonus] -- the answer on
 * the Dodge row, which answers for all three, because one melee test allows or
 * refuses every one of them.
 *
 * **[bonus] is read rather than the attack re-tested here**, for the reason
 * [DiveControl] states: a zero while the chip is on is exactly the sentence the
 * note needs, and asking `incoming` a second time is how one rule becomes two
 * disagreeing copies (6ag, 6ai, 6be).
 *
 * The "1 melee attack per round" limit is SAID and not enforced, the split
 * [DiveControl] takes on the turn spent standing up: this screen keeps no
 * round, and the parry stepper below is the GM's for the same reason.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RetreatControl(taken: Boolean, bonus: Int, onToggle: () -> Unit) {
    Note(ActiveDefenses.printedRetreat())
    FlowRow {
        FilterChip(
            selected = taken,
            onClick = { onToggle() },
            // Named from the bullet's own verb, not from the rule's effect: the
            // GM ticks what the character DID, and every other chip on this
            // screen is worded that way.
            label = { Text("Stepped back") },
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    if (!taken) return
    // A chip that is on while no row has moved would read as a tick that failed
    // -- the reason a cancelled refusal is SAID rather than silently dropped.
    if (bonus == 0) {
        Note(
            "Not against this attack: the bullet is \"vs. 1 melee attack\", so " +
                "nothing lands. Pick a bare hand or a weapon above.",
        )
        return
    }
    Note("One melee attack this round — the GM's to count; this screen keeps no round.")
}

/**
 * Which +2 was paid for, and on which row.
 *
 * Two chips rather than one, because the book prints two rules at different
 * prices with different drawbacks, and a GM has to say which they paid. The
 * ROW is part of the same choice for the same reason the option is: "+2 to
 * one defence" is meaningless without naming the one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoostPicker(
    rows: List<String>,
    chosen: Pair<DefenseBoost, String>?,
    onPick: (Pair<DefenseBoost, String>?) -> Unit,
) {
    // Nothing to buy when no defence stands -- but a boost already paid for
    // still needs its clear button, so an empty row list is not an early exit
    // when one is held.
    if (rows.isEmpty() && chosen == null) return
    Text("Buy +2 on one defence", style = MaterialTheme.typography.titleSmall)
    DefenseBoost.entries.forEach { boost ->
        val fp = ActiveDefenses.fpCostOf(boost)
        Text(
            "${boost.printed} — $fp FP: ${ActiveDefenses.printedBoost(boost)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        FlowRow {
            rows.forEach { row ->
                val isChosen = chosen == boost to row
                FilterChip(
                    selected = isChosen,
                    // Tapping the chosen chip clears it, which is the only way
                    // back -- a choice with no way out is the defect the
                    // wealth picker was fixed for (6u).
                    onClick = { onPick(if (isChosen) null else boost to row) },
                    // While one is paid for, the others are DISABLED rather
                    // than swapping: swapping charged twice and showed one +2
                    // (Codex), and nothing here can refund the first payment,
                    // because the book spends the FP on the declaration.
                    // Clearing first is the honest way to change your mind.
                    enabled = chosen == null || isChosen,
                    label = { Text(row) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
    chosen?.let { (boost, row) ->
        Note("Paid ${ActiveDefenses.fpCostOf(boost)} FP: +2 on $row this turn.")
        // Always reachable, even when the paid row is not on screen: declaring
        // a shot withdraws an unarmed parry, and with only the chips to clear
        // with, a boost bought on that row left every control disabled
        // (Codex). The FP stays spent -- clearing is a correction, not a
        // refund, and the label says so.
        TextButton(onClick = { onPick(null) }) {
            Text("Clear the +2 (the FP stays spent)")
        }
    }
}

/**
 * What is coming at the defender.
 *
 * Three values because the Parry (Unarmed) row draws exactly these lines:
 * a bare hand is the plain formula, a weapon costs -3 unless you box, and a
 * shot cannot be parried at all. A declaration rather than something derived,
 * because this dialog holds one sheet and the attacker is not on it — the
 * same reason the three refusals are chips (6bg).
 */
private enum class Incoming(val label: String) {
    BARE_HAND("A bare hand"),
    WEAPON("A weapon"),
    SHOT("A shot"),
}

/**
 * Where the attack is coming from.
 *
 * A declaration, like [Incoming] and the three refusals: this app holds no map
 * and no facing, and the GM does (6bf).
 *
 * **The -2 and -4 the book prints beside these positions are NOT applied**, and
 * [com.pathways.rules.ActiveDefenses.everyDefence] carries the reason. What
 * this control reaches is the shield's DB, whose own sentence is conditional on
 * the same arc and says "all active defenses (Dodge, Parry, and Block)" in as
 * many words. The rows below therefore change on REAR only because the shield
 * bonus does.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FacingPicker(chosen: Facing, onPick: (Facing) -> Unit) {
    Text("Where the attack comes from", style = MaterialTheme.typography.titleSmall)
    FlowRow {
        Facing.entries.forEach { option ->
            FilterChip(
                selected = option == chosen,
                onClick = { onPick(option) },
                label = { Text(FACING_LABELS.getValue(option)) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    // The penalty is SHOWN and not applied, so the GM can still use it. Saying
    // which half the screen did is the honest version of a rule that is partly
    // outside the model -- the same split ActiveDefenses takes on surprise.
    if (chosen != Facing.FRONT) {
        Note(
            "The book also prints a defence penalty for this position. It is " +
                "listed under Modifiers below and is NOT in the numbers above: " +
                "it prints \"to defense\" in the singular, and this app only " +
                "takes a penalty to every row when a second printing says so.",
        )
    }
}

/** What the GM says out loud, per printed position. */
private val FACING_LABELS = mapOf(
    Facing.FRONT to "Front",
    Facing.SIDE to "Side",
    Facing.REAR to "Rear",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncomingPicker(chosen: Incoming, onPick: (Incoming) -> Unit) {
    Text("The attack coming in", style = MaterialTheme.typography.titleSmall)
    FlowRow {
        Incoming.entries.forEach { option ->
            FilterChip(
                selected = option == chosen,
                onClick = { onPick(option) },
                label = { Text(option.label) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * Whether the character is stunned, and the book's own cell for what it costs.
 *
 * The cell is quoted WHOLE. Its second clause is a rest that recovers FP,
 * which needs a clock this app does not hold (6bf) -- cutting the sentence at
 * render time to hide it would be a parser nobody tests (6f), and a GM reading
 * the row can see for themselves which half the screen applied.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StunPicker(stunned: Boolean, onToggle: () -> Unit) {
    Text("Stunned", style = MaterialTheme.typography.titleSmall)
    FlowRow {
        FilterChip(
            selected = stunned,
            onClick = { onToggle() },
            label = { Text("Stunned") },
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    if (stunned) {
        Note("Do Nothing (p. 37): \"${ActiveDefenses.printedStun()}\"")
        Note(
            "Every defence below already includes it. Recovering is the GM's " +
                "roll — this app holds no clock to run the duration against.",
        )
    }
}

/** What the GM declares about this character's ability to defend at all. */
private val REFUSAL_LABELS = mapOf(
    DefenseRefusal.SURPRISE to "Surprised",
    // Not "All-out last turn": the book forfeits the defence for a Charge and
    // for Feverish Defense too, and a label naming one table would make the
    // other two look like a different rule.
    DefenseRefusal.ALL_OUT_ATTACK to "Forfeited last turn",
    DefenseRefusal.UNAWARE_RANGED to "Never saw the shot",
)

/**
 * The three declarations that withdraw every active defence.
 *
 * Offered as chips rather than enforced from state: the app holds no turn, so
 * it cannot know that a Charge happened last turn any more than it can know a
 * shot came from concealment. The GM does.
 *
 * [forfeiting] names everything whose own row forfeits the defence, read from
 * the book's tables rather than restated -- five, where p. 37's parenthetical
 * names three.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RefusalPicker(
    declared: Set<DefenseRefusal>,
    forfeiting: List<String>,
    onToggle: (DefenseRefusal) -> Unit,
) {
    Text("Can this character defend?", style = MaterialTheme.typography.titleSmall)
    FlowRow {
        REFUSAL_LABELS.forEach { (cause, label) ->
            FilterChip(
                selected = cause in declared,
                onClick = { onToggle(cause) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
    if (DefenseRefusal.ALL_OUT_ATTACK in declared) {
        Note("Forfeits the next turn's defence: ${forfeiting.joinToString(", ")}.")
    }
}

/**
 * How many parries this character has already made this round.
 *
 * A stepper rather than a counter the app increments, because nothing here
 * knows a round has passed -- the same reason a Fright Check and a Major
 * Wound's HT roll are buttons the GM presses (6c, 6n). The penalty is shown
 * beside it so the number on the rows above can be reconciled.
 */
@Composable
private fun ParriesThisRound(prior: Int, penalty: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Parries already this round", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onChange((prior - 1).coerceAtLeast(0)) }) { Text("−1") }
        Text("$prior", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { onChange(prior + 1) }) { Text("+1") }
    }
    Note(
        if (penalty == 0) {
            "The first parry of a round is free."
        } else {
            // The minus sign is normalised: `penalty` is an Int and prints
            // ASCII "-", while the two tiers beside it are typed. One line
            // with two different dashes reads as two different rules.
            "Parry rows above include ${"$penalty".replace('-', '−')}. " +
                "Chapter 8 prints no fencing weapon or rapier, whose tiers are −2 and −1."
        },
    )
}

/**
 * Each ready weapon's parry, paired with the level of the skill that wields it.
 *
 * A weapon whose skill the character does not have is skipped: the book's
 * formula is `Weapon Skill / 2 + 3`, and there is no skill to halve. So is one
 * that cannot parry at all -- [Weapon.parryMod] is null where the sheet prints
 * a dash, which is not the same as parrying at +0, and [ParryBlock.parryFor]
 * returns null for it.
 *
 * No premade carries a weapon today, so nothing here is on screen yet; it is
 * one branch rather than a silent omission the first time one does.
 */
internal fun armedParries(
    weapons: List<Weapon>,
    skillLevels: Map<SkillKey, Int>,
): List<Pair<String, Int>> = weapons.mapNotNull { weapon ->
    val level = skillLevels[weapon.skill] ?: return@mapNotNull null
    ParryBlock.parryFor(weapon, level)?.let { weapon.name to it }
}

@Composable
private fun DefenseRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
}
