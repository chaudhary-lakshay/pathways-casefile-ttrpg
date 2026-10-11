package com.pathways.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
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
import com.pathways.model.ModEffect
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.rules.AttackPlan
import com.pathways.rules.Attacks
import com.pathways.rules.BurnKind
import com.pathways.rules.EffortBurn
import com.pathways.rules.Damage
import com.pathways.rules.DarknessCause
import com.pathways.rules.TraitClauses
import com.pathways.rules.TraitEffects
import com.pathways.rules.RandomSource
import com.pathways.rules.SkillDefaults
import com.pathways.rules.SkillRoll
import com.pathways.rules.SkillRollOutcome
import com.pathways.rules.SkillTarget
import com.pathways.rules.Weapons
import com.pathways.rules.Wounds
import com.pathways.rules.content.HitLocation
import com.pathways.rules.content.RangePenalty
import com.pathways.rules.content.UnarmedAttack

/** The one skill whose table pays a damage bonus for skill above DX (p. 38). */
private const val BOXING = "Boxing"

/**
 * One attack: pick the skill, then the maneuver and its options (Ch. 5).
 *
 * The skill comes FIRST because Deceptive Attack's ceiling is half of it --
 * offering a stepper before knowing the skill would mean either capping
 * against the best skill on the sheet or trimming the choice afterwards, and
 * [Attacks] refuses a trimmed plan rather than silently obeying it.
 *
 * The OWNED skills offered are the ones the book files under COMBAT SKILLS
 * ([SkillDefaults.ownedAttacks]): nineteen rows, Fast-Draw and Shield among
 * them, and no skill outside it prints a damage line or arms a weapon. Until
 * the redesign every owned skill was offered, Research and Writing included,
 * on the reading that which skill may attack is the GM's call; a skill
 * outside the banner a GM rules can strike is now a Skills-tab roll.
 * Chapter 8's weapon table names seven skills, and [Weapons.forSkill] arms
 * those.
 *
 * **Untrained skills are offered too, where this dialog can show an attack
 * for one** ([SkillDefaults.attacks]): a fist, or a weapon off the table.
 * That is this screen's boundary. Any other skill is rolled untrained from
 * the Skills tab, where a to-hit number is all there is.
 */
@Composable
fun AttackDialog(
    skillLevels: Map<SkillKey, Int>,
    conditions: (SkillKey) -> List<com.pathways.model.Modifier>,
    st: Int,
    dx: Int,
    dice: RandomSource,
    onSpendFp: (Int) -> Unit,
    /**
     * What is left of the character's SPI, for Ch. 2's mystical push.
     *
     * This was a hardcoded 0 from the day [BurnRow] gained the parameter, and
     * no slice ever argued for it. `EffortBurn.affordable` reads it, so a 0
     * drove that row's early return and told a Beyonder at full SPI they had
     * "no spirituality left" -- on a screen that had just printed the rule
     * granting the push. That was reachable while every owned skill was
     * offered here. No COMBAT SKILLS row is SPI-based, so no attack pushes
     * with SPI today; the balance is still passed, live, so the row stays
     * true if one ever does.
     *
     * Must be passed as a LIVE expression by the caller, never a remembered
     * one: a maneuver can make two attacks and they share this one pool.
     */
    spiAvailable: Int,
    /**
     * Ch. 2's push, charged through the tracker.
     *
     * Takes the KIND rather than being a second FP-shaped callback: the
     * kind-to-pool question is answered once, in the caller both dialogs
     * share. The old handler charged the physical half and silently dropped
     * the mystical one, which would have moved a target for free.
     */
    onBurn: (BurnKind, Int) -> Unit,
    /**
     * Night Eyes' "reduce all darkness penalties by 2", or 0 without it.
     *
     * Passed in rather than derived here, and it decides only whether the
     * cause chips are OFFERED -- the book's darkness row itself stays
     * available to every shooter, or an ordinary one could not take a penalty
     * the book charges them (a design review's blocking finding).
     */
    darknessReduction: Int = 0,
    /**
     * The skills this character does NOT own and can still attack with here,
     * already filtered by [SkillDefaults.attacks]. Listed after the owned
     * ones and rolled through the same path.
     */
    untrained: List<SkillDefaults.Untrained> = emptyList(),
    /** Warrior's or Weapon Master's weapon line, where the chain holds one. */
    mastery: SkillDefaults.WeaponMastery? = null,
    /** The Harvest Priest's farming-tool line, as the sentence to print. */
    toolNote: String? = null,
    /** The sheet's traits, for the trait term and Reluctant Killer's clauses. */
    traits: List<Trait> = emptyList(),
    onDismiss: () -> Unit,
) {
    val skills = remember(skillLevels, untrained) {
        SkillDefaults.ownedAttacks(skillLevels).entries
            .sortedBy { it.key.name }
            .map { OfferedSkill(it.key, it.value) } +
            untrained.map { OfferedSkill(it.key, it.level, it) }
    }
    var picked by remember { mutableStateOf<OfferedSkill?>(null) }
    var plan by remember { mutableStateOf(AttackPlan()) }
    // Null for every skill with no attack table -- Wrestling included, which
    // the book gives "None (grapple only)".
    var unarmed by remember { mutableStateOf<UnarmedAttack?>(null) }
    // A weapon off chapter 8's table, and which of its attacks is being made:
    // the Combat Knife and the Broadsword each print two. Mutually exclusive
    // with [unarmed] -- a Brawling character punches OR swings the truncheon,
    // and showing both damage lines at once would claim one turn does both.
    var armed by remember { mutableStateOf<Armed?>(null) }
    // Distance and circumstances, kept out of AttackPlan: they describe the
    // TARGET and the scene, not the attacker's choice of maneuver. The
    // distance is a shot's alone; the circumstances are read on BOTH sides of
    // the toggle now, because one row of that table -- the prone target --
    // prints a number for reach as well ([Ranged.meleeConditions]).
    var band by remember { mutableStateOf<RangePenalty?>(null) }
    var circumstances by remember { mutableStateOf<Set<String>>(emptySet()) }
    // "Canceled by any Active Defense or injury" -- a declaration, because this
    // app holds no turn to remember an aim across and the GM does. See
    // [Ranged.offeredConditions].
    var aimCancelled by remember { mutableStateOf(false) }
    // Reluctant Killer's to-hit clause, declared by the GM: the TARGET, so it
    // lives beside the circumstances and survives the reach/range toggle.
    // One key, because the -2 is the same clause with the face hidden.
    var killerTarget by remember { mutableStateOf<String?>(null) }
    // "cannot Aim", unconditional: the Aim rows are withdrawn for a holder
    // whatever is declared, and kept apart from "Defended or was hurt".
    val killerClauses = TraitClauses.attackClauses(traits)
    val aimRefused = TraitClauses.refusesAim(traits)
    // What is behind "Darkness or heavy fog", which only Night Eyes can care
    // about. It starts on DARKNESS because that is the trait's general clause
    // ("reduce ALL darkness penalties by 2") and the row's own first-named
    // cause; fog and dim light are the departures the GM declares. Starting
    // unset would take the trait away from its owner unless they thought to
    // say what the weather is, which is the printed-and-not-applied defect in
    // miniature.
    var darknessCause by remember { mutableStateOf(DarknessCause.DARKNESS) }
    // "Darkness or heavy fog: -3 to -9 depending on severity". The screen
    // applied -3 and the other six were unreachable, although `Ranged` has
    // taken a `gmValue` for this row -- and validated it against the printed
    // ends -- since the row was extracted. Null is "the GM has not said",
    // which is the row's own first figure.
    var darknessSeverity by remember { mutableStateOf<Int?>(null) }
    // Null is the Torso: the book's own default, and the row it prints at +0.
    // A called shot survives the reach/range toggle -- unlike distance and
    // cover, "aiming at the skull" means the same thing with a fist or a gun.
    var location by remember { mutableStateOf<HitLocation?>(null) }
    // A list, not one roll: All-Out Double and Rapid Strike each make more
    // than one attack, and rolling once for them silently drops the rest
    // (Codex). Resolving the whole maneuver here is also what keeps its FP
    // charged once -- rolling the second attack as a fresh maneuver would
    // charge the 2 FP twice.
    var outcomes by remember { mutableStateOf<List<SkillRollOutcome>>(emptyList()) }
    // Points pushed onto each roll, keyed by its index in [outcomes]. Keyed
    // rather than counted once because "Max +5 per roll" is per ROLL, and a
    // Rapid Strike is two -- the per-call-vs-per-roll slip 6ak already made
    // once, here in the one place the distinction is visible on screen.
    var burned by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }

    val chosen = picked
    // What the chosen skill is rolled at NOW. Its own level, owned or
    // default -- unless a weapon is in a Warrior's or Weapon Master's hand,
    // which is wielded at no less than DX. So the level depends on the hand,
    // and it is derived here, once, so the header, the stepper's ceiling, the
    // damage and the roll cannot read four different numbers.
    fun levelWith(weaponInHand: Boolean): Int {
        val own = chosen?.level ?: 0
        return SkillDefaults.mastered(own, weaponInHand, mastery, dx) ?: own
    }
    val level = levelWith(armed != null)
    // What the traits add, composed ONCE: printed above Roll and rolled by
    // the handler drawn beside it, so the two cannot disagree (6eg).
    val traitMods = chosen?.let { traitTerms(traits, it.key, killerTarget) }.orEmpty()
    val isMastered = chosen != null && level != chosen.level
    val label = chosen?.let {
        it.key.label() + when {
            isMastered -> " (${mastery?.source})"
            it.untrained != null -> " (untrained, ${it.untrained.source})"
            else -> ""
        }
    }.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (outcomes.isNotEmpty() || chosen != null) {
                TextButton(
                    onClick = {
                        // Everything the turn chose, including where the
                        // target was standing: inheriting last turn's distance
                        // or cover would modify the next roll invisibly
                        // (Codex).
                        outcomes = emptyList()
                        // The pushes belong to the rolls they were spent on.
                        burned = emptyMap()
                        picked = null
                        plan = AttackPlan()
                        unarmed = null
                        armed = null
                        band = null
                        circumstances = emptySet()
                        // A cancelled aim belongs to the turn that cancelled
                        // it, like the distance and the cover above.
                        aimCancelled = false
                        // And who the target was.
                        killerTarget = null
                        // So does what the scene was lit by.
                        darknessCause = DarknessCause.DARKNESS
                        // And how bad it was. The same GM declares it about
                        // the same row, so leaving it would let last turn's
                        // pitch dark price this turn's dusk at -9.
                        darknessSeverity = null
                        location = null
                    },
                ) { Text("Another turn") }
            }
        },
        title = { Text(if (chosen == null) "Attack with which skill?" else "Attack") },
        text = {
            when {
                outcomes.isNotEmpty() -> LazyColumn {
                    itemsIndexed(outcomes) { index, outcome ->
                        AttackResult(outcome)
                        // Ch. 2's FP push, whose scope names attack rolls
                        // outright: "any roll based on ST, DX, or HT (attack
                        // rolls, dodging, climbing, lifting, etc.)". It was
                        // wired only into the generic skill dialog, so the
                        // one roll the rule names first could not use it.
                        //
                        // PER OUTCOME, because the cap is "Max +5 per roll"
                        // and All-Out Double and Rapid Strike each make two:
                        // one shared budget would let a fighter push one
                        // attack with the other's allowance. `burned` is
                        // therefore keyed by index and reset with the roll.
                        // One skill per maneuver, so every outcome here shares
                        // one kind: "you cannot spend both FP and SPI on the
                        // same roll" cannot be violated from this dialog, and
                        // EffortBurn.requireOnePool is deliberately not called.
                        // Said out loud because a future multi-skill combo
                        // would break that silently (a design review).
                        val kind = chosen?.key?.let(EffortBurn::kindFor)
                        if (kind != null && EffortBurn.canPush(outcome)) {
                            // Every count the chips below are offered against.
                            val drawn = burned
                            BurnRow(kind, drawn[index] ?: 0, spiAvailable) { points ->
                                // The skill dialog's two guards, which this
                                // row had neither of. A second tap of the
                                // same picture pushed the `outcome` this item
                                // was drawn with, so +1 twice charged 2 FP for
                                // +1, and +3 twice threw past the cap. The
                                // whole map, because a push on the other
                                // attack moves the pool this one is drawn
                                // against.
                                if (burned != drawn) return@BurnRow
                                val live = outcomes.getOrNull(index) ?: return@BurnRow
                                val already = burned[index] ?: 0
                                val next = EffortBurn.pushed(live, kind, points, already, spiAvailable)
                                    ?: return@BurnRow
                                outcomes = outcomes.toMutableList().also { it[index] = next }
                                burned = burned + (index to already + points)
                                onBurn(kind, points)
                            }
                        }
                    }
                }
                chosen == null -> LazyColumn {
                    items(skills) { entry ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    picked = entry
                                    // The book's first listed attack is the
                                    // ordinary one (Punch, Jab); an unlisted
                                    // skill simply has no damage to show.
                                    unarmed = Damage.attacksFor(entry.key.name).firstOrNull()
                                    // Only when the skill has no unarmed table
                                    // of its own: Brawling has both, and the
                                    // book's own first answer there is a fist.
                                    armed = if (unarmed == null) {
                                        Weapons.forSkill(entry.key).firstOrNull()?.let(::Armed)
                                    } else {
                                        null
                                    }
                                    // A Guns skill opens on a firearm, so the
                                    // attack starts AT RANGE. Left in reach it
                                    // would take All-Out Determined's melee +4
                                    // instead of its ranged +1 and hide the
                                    // distance and Aim rows entirely (Codex).
                                    plan = AttackPlan(isRanged = armed.isFired)
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.key.label())
                                entry.untrained?.let {
                                    Text(
                                        "untrained, ${it.source}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text("${entry.level}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                else -> {
                    // The roll as the picks stand, composed while the options are
                    // drawn so it can be printed above Roll, and handed to Roll
                    // as it was printed. Rolled at the level the exertion started
                    // from, then charged: a character is not yet tired by an
                    // effort they are in the middle of making. The cost still
                    // lands, so the NEXT turn feels it.
                    //
                    // The three option calls below already ran at draw time, for
                    // the lines AttackOptions prints; the trackers, the traits
                    // and the called shot refuse nothing.
                    val standing = attackStanding(
                        label = label,
                        level = level,
                        key = chosen.key,
                        trackers = conditions(chosen.key),
                        picked = Attacks.modifiers(plan, level, unarmed) +
                            rangedModifiers(plan, band, circumstances, armed.aimAcc, aimCancelled || aimRefused, darknessCause, darknessReduction, darknessSeverity) +
                            meleeModifiers(plan, circumstances) +
                            traitMods +
                            listOfNotNull(location?.let(Wounds::modifier)),
                    )
                    val attacks = Attacks.attackCount(plan)
                    val fp = Attacks.fpCost(plan)
                    AttackOptions(
                        label = label,
                        skillLevel = level,
                        // Said under the header with a weapon in hand: what of a
                        // weapon line is still not applied here. The Warrior's
                        // repeated-use clause is about any weapon they hold, and a
                        // Pugilist's ranged one only; the Harvest Priest's is
                        // about a skill they lack.
                        notes = armed?.let { held ->
                            listOfNotNull(
                                mastery?.unappliedFor(Weapons.isRanged(held.weapon)),
                                toolNote.takeIf { chosen.untrained != null },
                            )
                        }.orEmpty(),
                        // The level Defend parries the same weapon at, never the
                        // chosen skill's: the best skill that arms it, floored at
                        // DX in a master's hand. Two skills can arm one knife, and
                        // the chosen one printed 8 here beside Defend's 9 (6ey's
                        // design review).
                        wieldedAt = { SkillDefaults.wieldedAt(it, skillLevels, mastery, dx) },
                        plan = plan,
                        unarmed = unarmed,
                        // Two unrelated bonuses reach damage: All-Out Attack
                        // (Strong)'s +2 and Boxing's for skill above DX. Attacks
                        // composes them so neither can be dropped here. Boxing's
                        // is a property of the bare fist, so a weapon takes only
                        // the maneuver's.
                        damage = armed?.let { Weapons.damageFor(it.weapon, it.mode, st, Attacks.damageBonus(plan, 0)) }
                            ?: unarmed?.let {
                                val fromSkill = if (it.skill == BOXING) Damage.boxingBonus(dx, level) else 0
                                Damage.forAttack(it, st, Attacks.damageBonus(plan, fromSkill))
                            },
                        attacks = Damage.attacksFor(chosen.key.name),
                        armed = armed,
                        weapons = Weapons.forSkill(chosen.key),
                        band = band,
                        circumstances = circumstances,
                        aimCancelled = aimCancelled,
                        aimRefusedBy = killerClauses.firstOrNull()?.shown?.takeIf { aimRefused },
                        killerClauses = killerClauses,
                        killerTarget = killerTarget,
                        onKillerTarget = { killerTarget = if (it == killerTarget) null else it },
                        fromTraits = traitMods.sumOf { (it.effect as ModEffect.Flat).n },
                        darknessCause = darknessCause,
                        darknessReduction = darknessReduction,
                        darknessSeverity = darknessSeverity,
                        onDarknessCause = { darknessCause = it },
                        onDarknessSeverity = { darknessSeverity = it },
                        location = location,
                        onAttack = {
                            unarmed = it
                            armed = null
                            // Shove rolls no damage, so a Strong Blow ticked on
                            // the fist cannot come along -- Attacks refuses the
                            // pair rather than charging 1 FP for nothing (Codex).
                            // A fist puts the weapon down, so a master's level
                            // drops to the default and the plan goes with it.
                            plan = Attacks.atLevel(Attacks.withAttack(plan, it), levelWith(false))
                            circumstances = circumstances - AIM_ROWS
                        },
                        onArmed = { held ->
                            armed = held
                            // Putting the weapon down goes back to the skill's own
                            // first attack, not to nothing: for Brawling that is
                            // the fist, and leaving both null showed no damage
                            // line at all until Punch was tapped by hand (Codex).
                            unarmed = if (held == null) {
                                Damage.attacksFor(chosen.key.name).firstOrNull()
                            } else {
                                null
                            }
                            // A firearm is fired at range and a sword is not
                            // thrown; the toggle still overrides either way.
                            // And to the level the new hand is rolled at, which
                            // for an untrained master differs with and without.
                            plan = Attacks.atLevel(
                                Attacks.atRange(plan, held.isFired),
                                levelWith(held != null),
                            )
                            // Aim is worth the WEAPON's Acc, and distance belongs
                            // to a shot. Carrying either into reach asks
                            // Ranged.modifiers for a number nothing can supply --
                            // the same shape as the distance and cover that
                            // "Another turn" used to leave behind.
                            //
                            // Now OVER-BROAD by one row, and recorded rather than
                            // narrowed: picking up a sword while already in reach
                            // also drops a ticked prone chip, which the reason
                            // above does not cover -- the plan was melee and stays
                            // melee. Narrowing it would put meleeConditions' own
                            // predicate at a second call site, which is the third
                            // copy the maneuver chip's comment warns about. The
                            // cost is a lost declaration the player can see go.
                            if (!held.isFired) {
                                band = null
                                circumstances = emptySet()
                            }
                        },
                        onBand = { band = it },
                        // Tapping the chosen location again goes back to the torso
                        // rather than leaving the player stuck with a -9 eye shot.
                        onLocation = { location = if (it == location) null else it },
                        onAimCancelled = {
                            aimCancelled = !aimCancelled
                            // The rows go with the declaration. Left selected they
                            // would still be summed on Roll -- and Ranged.modifiers
                            // now refuses that pairing, so the stale chip would
                            // throw rather than merely mislead (6k, 6y, 6af).
                            if (aimCancelled) circumstances = circumstances - AIM_ROWS
                        },
                        onCircumstance = { circumstances = toggled(circumstances, it) },
                        onPlan = {
                            // Range and circumstances describe a shot; going back
                            // into reach drops them rather than applying a
                            // distance penalty to a punch.
                            if (!it.isRanged) {
                                band = null
                                circumstances = emptySet()
                            }
                            plan = it
                        },
                        standing = standing.target,
                        fromTrackers = standing.fromTrackers,
                        onRoll = onRoll@{
                            // The guard every roll button here carries (6p), and
                            // this one did not: a second tap of the same picture
                            // rolled again and charged the plan's FP again.
                            if (outcomes.isNotEmpty()) return@onRoll
                            // A fresh roll starts with a fresh +5 budget per roll.
                            burned = emptyMap()
                            // The target printed above the button, the count and
                            // the cost as they were drawn. Nothing the plan is
                            // made of is read again here (6eg).
                            outcomes = List(attacks) { SkillRoll.rollAgainst(standing.target, dice) }
                            fp.takeIf { it > 0 }?.let(onSpendFp)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun AttackResult(outcome: SkillRollOutcome) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(outcome.label, style = MaterialTheme.typography.titleSmall)
        Text(
            "3d6: ${outcome.roll?.total} vs ${outcome.target}",
            style = MaterialTheme.typography.titleLarge,
        )
        if (outcome.target != outcome.baseTarget) {
            Text(
                "base ${outcome.baseTarget}, modified to ${outcome.target}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            outcome.effectiveOutcome?.name?.lowercase()?.replace('_', ' ') ?: "no roll",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

internal fun signed(n: Int) = if (n >= 0) "+$n" else "$n"

/** "Guns (Pistol)" where the book specialises, "Brawling" where it does not. */
internal fun SkillKey.label(): String =
    if (specialization == null) name else "$name ($specialization)"

/**
 * What the character's traits add to an attack: the general term every
 * other roll on the sheet takes, and the to-hit clause the GM declared.
 *
 * The Attack roll was the one roll that took no trait term (6ev). 6ew's
 * census found [TraitEffects.modifiers] empty for every COMBAT skill, and
 * since the redesign those are the only owned skills offered here, so the
 * general term adds nothing today and Reluctant Killer's clause is what this
 * carries. Kept whole so a trait that names a combat skill reaches the attack
 * the way it reaches the Skills tab. Composed once per composition, printed
 * and rolled.
 */
internal fun traitTerms(
    traits: List<Trait>,
    key: SkillKey,
    killerTarget: String?,
): List<com.pathways.model.Modifier> =
    TraitEffects.modifiers(traits, key) + TraitClauses.attackModifiers(traits, killerTarget)

/**
 * The attack roll as the picks stand: the target printed above Roll and
 * rolled by it, and what of that target the trackers took.
 */
internal class AttackStanding(val target: SkillTarget, val fromTrackers: Int)

/**
 * [trackers] is what the character walked in with (reeling, fatigue,
 * attrition, shock) and [picked] everything chosen in the dialog, which its
 * own lines already print. The trackers' line is read off the ledger by id,
 * never taken as the remainder: a remainder would absorb a term the screen
 * forgot to print and still add up (6gd's design review).
 */
internal fun attackStanding(
    label: String,
    level: Int,
    key: SkillKey,
    trackers: List<com.pathways.model.Modifier>,
    picked: List<com.pathways.model.Modifier>,
): AttackStanding {
    val target = SkillRoll.targetFor(label, level, key, trackers + picked)
    val ids = trackers.map { it.id }.toSet()
    return AttackStanding(
        target,
        target.applied.filter { applied -> applied.ids.any { it in ids } }.sumOf { it.delta },
    )
}
