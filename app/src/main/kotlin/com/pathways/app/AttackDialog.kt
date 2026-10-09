package com.pathways.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import com.pathways.model.ModEffect
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.rules.AttackManeuver
import com.pathways.rules.AttackPlan
import com.pathways.rules.Attacks
import com.pathways.rules.BurnKind
import com.pathways.rules.EffortBurn
import com.pathways.rules.Damage
import com.pathways.rules.DamageRoll
import com.pathways.rules.DarknessCause
import com.pathways.rules.TraitClause
import com.pathways.rules.TraitEffects
import com.pathways.rules.RandomSource
import com.pathways.rules.Ranged
import com.pathways.rules.SkillDefaults
import com.pathways.rules.SkillRoll
import com.pathways.rules.SkillRollOutcome
import com.pathways.rules.Weapons
import com.pathways.rules.Wounds
import com.pathways.rules.content.CataloguedWeapon
import com.pathways.rules.content.HitLocation
import com.pathways.rules.content.RangePenalty
import com.pathways.rules.content.UnarmedAttack
import com.pathways.rules.content.WeaponMode

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
 * Every OWNED skill is offered, not a curated combat list. The book does
 * print a COMBAT SKILLS banner -- nineteen rows, Fast-Draw and Shield among
 * them -- but a banner is where a skill is filed, not a rule about which
 * skill may make an attack, so which of a character's skills can is the GM's
 * call. (This KDoc said "this book tags no skill as a combat skill" until
 * 6eu, which line 1668 of the book contradicts.) Chapter 8's weapon table
 * names seven skills, and [Weapons.forSkill] arms those.
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
     * granting the push. Every skill is offered here (see the KDoc above), and
     * `pathways.json` grants Ritualistic Magic at Sequence 9 of six pathways,
     * so the false sentence was reachable on a character's first potion.
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
        skillLevels.entries.sortedBy { it.key.name }.map { OfferedSkill(it.key, it.value) } +
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
    val killerClauses = TraitEffects.attackClauses(traits)
    val aimRefused = TraitEffects.refusesAim(traits)
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
                            BurnRow(kind, burned[index] ?: 0, spiAvailable) { points ->
                                val already = burned[index] ?: 0
                                outcomes = outcomes.toMutableList().also {
                                    it[index] = EffortBurn.applied(outcome, kind, points, already)
                                }
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
                else -> AttackOptions(
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
                    onCircumstance = { name ->
                        circumstances = when {
                            name in circumstances -> circumstances - name
                            // Aiming is one act held for one length of time.
                            // Ranged.modifiers refuses both rows together, so
                            // picking the second must REPLACE the first rather
                            // than build a plan that throws on Roll.
                            name in AIM_ROWS -> circumstances - AIM_ROWS + name
                            else -> circumstances + name
                        }
                    },
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
                    onRoll = {
                        // Rolled at the level the exertion started from, then
                        // charged: a character is not yet tired by an effort
                        // they are in the middle of making. The cost still
                        // lands, so the NEXT turn feels it.
                        val mods = conditions(chosen.key) +
                            Attacks.modifiers(plan, level, unarmed) +
                            rangedModifiers(plan, band, circumstances, armed.aimAcc, aimCancelled || aimRefused, darknessCause, darknessReduction, darknessSeverity) +
                            meleeModifiers(plan, circumstances) +
                            traitMods +
                            listOfNotNull(location?.let(Wounds::modifier))
                        // A fresh roll starts with a fresh +5 budget per roll.
                        burned = emptyMap()
                        outcomes = List(Attacks.attackCount(plan)) {
                            SkillRoll.at(
                                label = label,
                                base = level,
                                key = chosen.key,
                                source = dice,
                                mods = mods,
                            )
                        }
                        Attacks.fpCost(plan).takeIf { it > 0 }?.let(onSpendFp)
                    },
                )
            }
        },
    )
}

@Composable
private fun AttackOptions(
    label: String,
    skillLevel: Int,
    notes: List<String>,
    wieldedAt: (CataloguedWeapon) -> Int?,
    plan: AttackPlan,
    unarmed: UnarmedAttack?,
    damage: DamageRoll?,
    attacks: List<UnarmedAttack>,
    armed: Armed?,
    weapons: List<CataloguedWeapon>,
    band: RangePenalty?,
    circumstances: Set<String>,
    aimCancelled: Boolean,
    aimRefusedBy: String?,
    killerClauses: List<TraitClause>,
    killerTarget: String?,
    onKillerTarget: (String) -> Unit,
    fromTraits: Int,
    darknessCause: DarknessCause,
    darknessReduction: Int,
    darknessSeverity: Int?,
    onDarknessCause: (DarknessCause) -> Unit,
    onDarknessSeverity: (Int) -> Unit,
    location: HitLocation?,
    onAttack: (UnarmedAttack) -> Unit,
    onArmed: (Armed?) -> Unit,
    onBand: (RangePenalty) -> Unit,
    onCircumstance: (String) -> Unit,
    onAimCancelled: () -> Unit,
    onLocation: (HitLocation) -> Unit,
    onPlan: (AttackPlan) -> Unit,
    onRoll: () -> Unit,
) {
    val maxDeceptive = Attacks.maxDeceptive(skillLevel)
    // Scrolls: with a distance band and nine conditions to choose from, the
    // panel is taller than a phone and the Roll button fell off the bottom.
    // An AlertDialog does not scroll its own text slot. Found on device.
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("$label $skillLevel", style = MaterialTheme.typography.titleMedium)
        notes.forEach {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (attacks.isNotEmpty()) {
            FlowRow {
                attacks.forEach { option ->
                    FilterChip(
                        selected = unarmed?.attack == option.attack,
                        onClick = { onAttack(option) },
                        label = { Text(option.attack) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            unarmed?.let {
                Text(
                    // Shove prints "Special" and rolls nothing; saying so beats
                    // showing a damage line it does not have.
                    "${it.damage} — ${it.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        ArmedWith(weapons, armed, wieldedAt, onArmed)
        // Chips WRAP rather than scroll sideways: a horizontally scrolling Row
        // inside an AlertDialog never received the drag on a 1080px phone, so
        // three of the five maneuvers -- Strong, Double and Charge -- could not
        // be selected at all. Found on device, not by a test.
        FlowRow {
            AttackManeuver.entries.forEach { option ->
                FilterChip(
                    selected = plan.maneuver == option,
                    // Changing maneuver drops the options the old one allowed:
                    // Heroic Charge outside a charge is refused by Attacks, and
                    // carrying it over silently would throw on Roll. Through
                    // Attacks rather than rebuilt here, because deciding what
                    // survives a transition is a rule and this was its third
                    // copy -- the one that dropped an unscoped option.
                    onClick = { onPlan(Attacks.withManeuver(plan, option)) },
                    label = { Text(option.printed) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        FilterChip(
            selected = plan.isRanged,
            // Both attack options are melee-only, so switching to range drops
            // them rather than offering a choice the book does not allow.
            onClick = { onPlan(Attacks.atRange(plan, !plan.isRanged)) },
            label = { Text(if (plan.isRanged) "At range" else "In reach") },
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (plan.isRanged) {
            RangedOptions(
                band,
                circumstances,
                armed,
                aimCancelled,
                aimRefusedBy,
                darknessCause,
                darknessReduction,
                darknessSeverity,
                onBand,
                onCircumstance,
                onAimCancelled,
                onDarknessCause,
                onDarknessSeverity,
            )
        } else {
            Stepper("Rapid Strike", plan.rapidStrikes, "${Attacks.RAPID_STRIKE_PENALTY} each") {
                onPlan(plan.copy(rapidStrikes = (plan.rapidStrikes + it).coerceAtLeast(0)))
            }
            Stepper(
                "Deceptive",
                plan.deceptive,
                if (maxDeceptive == 0) "skill too low" else "-2 each, max $maxDeceptive",
            ) {
                onPlan(plan.copy(deceptive = (plan.deceptive + it).coerceIn(0, maxDeceptive)))
            }
            // The one row of the ranged table the book also prices in reach.
            // Which rows those are is [Ranged.meleeConditions]' call, not this
            // screen's: a dialog holding its own list is the second copy of a
            // scope (6ag, 6ai, 6be). Same chip label as the ranged side, whole
            // printed clause included, so both numbers are on screen wherever
            // the plan is and the breakdown always adds up.
            FlowRow {
                Ranged.meleeConditions().forEach { option ->
                    FilterChip(
                        selected = option.condition in circumstances,
                        onClick = { onCircumstance(option.condition) },
                        label = { Text("${option.condition} ${option.printed}") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
        CalledShot(location, onLocation)
        KillerTarget(killerClauses, killerTarget, onKillerTarget)
        // "Regular melee attacks only", and an attack that rolls damage for
        // the +2 to land on. Withheld rather than offered and refused: a chip
        // that takes the dialog down on Roll is not a choice (6t). The whole
        // scope lives in Attacks, because half of it -- the firearm, and
        // whether anything is being rolled at all -- is a fact about what is
        // held, which modifiers() never sees.
        if (Attacks.offersStrongBlow(plan, damage != null, armed?.isFired == true)) {
            FilterChip(
                selected = plan.strongBlow,
                onClick = { onPlan(plan.copy(strongBlow = !plan.strongBlow)) },
                label = {
                    Text(
                        "${Attacks.STRONG_BLOW} (${Attacks.effortFp(Attacks.STRONG_BLOW)} FP, " +
                            "+${Attacks.STRONG_BLOW_DAMAGE} damage)",
                    )
                },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        if (plan.maneuver == AttackManeuver.CHARGE) {
            FilterChip(
                selected = plan.heroicCharge,
                onClick = { onPlan(plan.copy(heroicCharge = !plan.heroicCharge)) },
                label = {
                    Text("${Attacks.HEROIC_CHARGE} (${Attacks.effortFp(Attacks.HEROIC_CHARGE)} FP)")
                },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        // Offered on every maneuver and at any reach, because its cell prints
        // no scope where both neighbours print one. The step itself is the
        // GM's -- this app holds no map -- so what the chip delivers is the FP
        // charge, which is the half of the row this app does model.
        FilterChip(
            selected = plan.giantStep,
            onClick = { onPlan(plan.copy(giantStep = !plan.giantStep)) },
            label = {
                Text("${Attacks.GIANT_STEP} (${Attacks.effortFp(Attacks.GIANT_STEP)} FP)")
            },
            modifier = Modifier.padding(vertical = 4.dp),
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        val fp = Attacks.fpCost(plan)
        val total = Attacks.modifiers(plan, skillLevel, unarmed)
            .sumOf { (it.effect as ModEffect.Flat).n }
        val count = Attacks.attackCount(plan)
        val fromRange = rangedModifiers(plan, band, circumstances, armed.aimAcc, aimCancelled || aimRefusedBy != null, darknessCause, darknessReduction, darknessSeverity)
            .sumOf { (it.effect as ModEffect.Flat).n }
        Text(
            "Attack options: ${if (total == 0) "no modifier" else signed(total)}" +
                (if (count > 1) ", $count attacks" else "") +
                if (fp > 0) ", costs $fp FP" else "",
            style = MaterialTheme.typography.bodySmall,
        )
        val fromMelee = meleeModifiers(plan, circumstances)
            .sumOf { (it.effect as ModEffect.Flat).n }
        if (plan.isRanged) {
            Text(
                "Range and cover: ${if (fromRange == 0) "no modifier" else signed(fromRange)}",
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (fromMelee != 0) {
            // Its own label, never "Range and cover": nothing about distance
            // is being priced here, and a total that does not match the line
            // above it is the breakdown defect this repo has shipped twice
            // (6ag, 6ai).
            Text(
                "Target: ${signed(fromMelee)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (fromTraits != 0) {
            // Its own line, like "Target": a trait term the breakdown left
            // out would make the roll's total a number nothing above it adds
            // up to (the diff review's MEDIUM).
            Text("Traits: ${signed(fromTraits)}", style = MaterialTheme.typography.bodySmall)
        }
        location?.let {
            Text(
                "Called shot: ${it.target} ${signed(it.penalty)}, ${it.printedMultiplier} — ${it.effect}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            // The multiplier is SHOWN, never applied: damage here is an
            // expression, not a rolled total, so there is no product to round
            // and this book prints no rounding rule to round it by.
            damage?.let { roll ->
                // The guard stays, and the message no longer names a chapter.
                // It used to read "chapter 5 prints no wounding multiplier for
                // pi+" — true of chapter 5 and false of the BOOK, which prints
                // it in Appendix A. Nothing in the committed content reaches
                // this branch today; it is here so an unlabelled type in future
                // content cannot crash the dialog.
                if (!Wounds.knowsType(roll.type)) {
                    "Damage on a hit: ${roll.render()} — this book prints no " +
                        "wounding multiplier for \"${roll.type}\""
                } else {
                    val times = Wounds.multiplierFor(location, roll.type)
                    "Damage on a hit: ${roll.render()}" +
                        if (times == 1.0) "" else ", ${Wounds.render(times)} there"
                }
            } ?: unarmed?.let { row ->
                // The grappling rows print an Opposed Roll column the two
                // damage tables do not have, and it is the whole resolution:
                // this screen rolls the grappler's half and the target's is
                // the GM's. Named here or the column would have no reader.
                //
                // Those rows do NOT also get "rolls no damage": their own cell
                // two lines above already reads "None (grapple only)". Shove's
                // reads "Special", which does not say it, so Shove keeps the
                // sentence.
                row.opposedRoll?.let { "The target rolls $it." }
                    ?: "Rolls no damage; see the note above."
            }.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Injury, fatigue and attrition are added when it is rolled.",
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onRoll) { Text("Roll") }
    }
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

/**
 * What the character is holding (Ch. 8, p. 80).
 *
 * Shown for any skill the weapon table names -- Knife, Brawling, Axe/Mace,
 * Broadsword and the three Guns specializations. Tapping the selected weapon
 * again puts it down, which is how a Brawling character gets back to a fist
 * after picking up the truncheon.
 *
 * A second chip row appears only for the two weapons that print two attacks:
 * the Combat Knife and the Broadsword can cut or thrust, and the mode changes
 * both the damage and the wounding multiplier a called shot would apply.
 */
@Composable
private fun ArmedWith(
    weapons: List<CataloguedWeapon>,
    armed: Armed?,
    wieldedAt: (CataloguedWeapon) -> Int?,
    onArmed: (Armed?) -> Unit,
) {
    if (weapons.isEmpty()) return
    Text(
        "Weapon",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow {
        weapons.forEach { option ->
            FilterChip(
                selected = armed?.weapon?.name == option.name,
                onClick = {
                    onArmed(if (armed?.weapon?.name == option.name) null else Armed(option))
                },
                label = { Text(option.name) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    armed?.let { held ->
        if (held.weapon.damage.size > 1) {
            FlowRow {
                held.weapon.damage.forEach { mode ->
                    FilterChip(
                        selected = held.mode == mode,
                        onClick = { onArmed(Armed(held.weapon, mode)) },
                        label = { Text(mode.printed) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
        Text(
            // Parry is the number ParryBlock has had a formula for since M0
            // with no weapon to apply it to. A firearm gets "cannot parry"
            // rather than a 0: parrying at nothing is not parrying badly.
            "Acc ${held.weapon.acc} · reach/range ${held.weapon.range} · " +
                (
                    // Whether a default parries is not settled, and Defend
                    // offers none with a weapon (its bare-hand row is DX, a
                    // printed operand), so this line must not print one either.
                    // A firearm first: it parries at nothing, skill or not.
                    if (!Weapons.canParry(held.weapon)) {
                        "cannot parry"
                    } else {
                        wieldedAt(held.weapon)?.let { "parry ${Weapons.parryFor(held.weapon, it)}" }
                            ?: "no parry offered without the skill"
                    }
                    ) +
                " · ${held.weapon.cost}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        if (held.weapon.notes.isNotBlank()) {
            Text(held.weapon.notes, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Distance and the scene, for a shot (Ch. 5, p. 39-40).
 *
 * The two Aim rows are selectable only with a weapon in hand: their modifier
 * is that weapon's Acc, and the cell says so ("see weapon table"). Without one
 * there is no number, and [Ranged.modifiers] refuses rather than applying
 * zero, so offering the chip would build a plan that throws on Roll.
 */
@Composable
private fun RangedOptions(
    band: RangePenalty?,
    circumstances: Set<String>,
    armed: Armed?,
    aimCancelled: Boolean,
    aimRefusedBy: String?,
    darknessCause: DarknessCause,
    darknessReduction: Int,
    darknessSeverity: Int?,
    onBand: (RangePenalty) -> Unit,
    onCircumstance: (String) -> Unit,
    onAimCancelled: () -> Unit,
    onDarknessCause: (DarknessCause) -> Unit,
    onDarknessSeverity: (Int) -> Unit,
) {
    val bands = remember { Ranged.bands() }
    // What the book still allows, decided in rules: a screen filtering its own
    // list is a second copy of the refusal (6ag, 6ai, 6be).
    val conditions = remember(aimCancelled, aimRefusedBy) {
        Ranged.offeredConditions(aimCancelled || aimRefusedBy != null)
    }
    Text("Distance", style = MaterialTheme.typography.titleSmall)
    FlowRow {
        bands.forEach { option ->
            FilterChip(
                selected = band?.printed == option.printed,
                onClick = { onBand(option) },
                label = { Text("${option.printed.substringBefore(" meters")} ${signed(option.penalty)}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    Text(
        "Conditions",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow {
        conditions.filterNot { it.weaponAcc }.forEach { option ->
            FilterChip(
                selected = option.condition in circumstances,
                onClick = { onCircumstance(option.condition) },
                label = { Text("${option.condition} ${option.printed}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
    // Offered only to a character the trait can help, and only once the row
    // it modifies is actually selected: nobody else has anything to declare,
    // and the row itself is never withdrawn from them.
    if (DARKNESS_ROW in circumstances) {
        val row = Ranged.conditions().single { it.condition == DARKNESS_ROW }
        Text(
            "How bad is it?",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        FlowRow {
            endsBetween(row.modifier!!, row.worstCase).forEach { value ->
                FilterChip(
                    // Unset shows the row's own figure selected, because that
                    // is the one the roll is using.
                    selected = (darknessSeverity ?: row.modifier) == value,
                    onClick = { onDarknessSeverity(value) },
                    label = { Text(signed(value)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
    // Offered only to a character the trait can help, and only once the row
    // it modifies is actually selected.
    if (darknessReduction > 0 && DARKNESS_ROW in circumstances) {
        Text(
            "What is it?",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        FlowRow {
            DarknessCause.entries.forEach { option ->
                FilterChip(
                    selected = option == darknessCause,
                    onClick = { onDarknessCause(option) },
                    label = { Text(causeLabel(option)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        Text(
            "${TraitEffects.NIGHT_EYES}: \"${TraitEffects.printedNightEyes()}\"",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (aimRefusedBy != null) {
        // Withdrawn with the cell's own words, like a cancelled aim (6bj),
        // and no chip: there is no aim to cancel.
        Text(
            "$aimRefusedBy: \"${TraitEffects.AIM_REFUSAL}\"",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    Text("Since taking aim", style = MaterialTheme.typography.titleSmall)
    FlowRow {
        FilterChip(
            selected = aimCancelled,
            onClick = { onAimCancelled() },
            label = { Text("Defended or was hurt") },
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    if (aimCancelled) {
        // Withdrawn with the reason SAID: rows that simply vanish read as a
        // screen that lost them (6bj), and the aim cost a turn to take.
        Text(
            "Aim (Ranged): \"${Ranged.printedAim()}\"",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    val acc = armed.aimAcc
    conditions.filter { it.weaponAcc }.forEach { option ->
        if (acc == null) {
            Text(
                // A FIRED weapon, not merely a held one: the toggle above is
                // the player's, so a knife can be carried into a ranged plan,
                // and the Hand Axe prints Acc 1 -- enough to aim a hatchet for
                // +3 over three turns if the chip were offered (Codex).
                "${option.condition}: ${option.printed} — choose a firearm above.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            FilterChip(
                selected = option.condition in circumstances,
                // One aim, one length of time: Ranged.modifiers refuses both
                // rows together, because each pays the weapon's Acc and two
                // would break the second row's own "max Acc +2 total".
                onClick = { onCircumstance(option.condition) },
                label = { Text("${option.condition} ${signed(acc + option.accBonus)}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * Reluctant Killer's to-hit clauses, offered to a holder alone. The GM says
 * whether the target is a recognizable person met with deadly force; tapping
 * the chosen clause again takes the declaration back.
 */
@Composable
private fun KillerTarget(
    clauses: List<TraitClause>,
    declared: String?,
    onDeclare: (String) -> Unit,
) {
    if (clauses.isEmpty()) return
    Text(
        "Target (${clauses.first().shown})",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow {
        clauses.forEach { clause ->
            FilterChip(
                selected = clause.key == declared,
                onClick = { onDeclare(clause.key) },
                label = { Text(clause.printed) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * Calling a body part (Ch. 5, p. 41, Optional).
 *
 * The chip carries only the to-hit cost. Two of the nine multipliers are not
 * one number -- the Neck prints "Cr x1.5 / Cut x2 / Imp x2" -- so the printed
 * cell goes in the summary line below, where it has room to be quoted whole
 * rather than trimmed to whichever number came first.
 *
 * The Torso is offered like any other row: it is the book's own +0 default, and
 * leaving it out would make "no called shot" a state with no chip to return to.
 */
@Composable
private fun CalledShot(location: HitLocation?, onLocation: (HitLocation) -> Unit) {
    val locations = remember { Wounds.locations() }
    Text(
        "Called shot",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow {
        locations.forEach { option ->
            FilterChip(
                selected = option == location,
                onClick = { onLocation(option) },
                label = { Text("${option.target} ${signed(option.penalty)}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * The range and condition modifiers for [plan], or none for a melee attack.
 *
 * Darkness starts at the gentle end the book prints. Its cell reads "-3 to
 * -9 depending on severity", and the harsher end is the GM's call rather than
 * the app's to assume -- [Ranged.modifiers] takes an override for exactly
 * that, and the "How bad is it?" row passes one (6dz). This comment went on
 * saying "the screen does not yet offer one" for five slices after it did.
 */
/**
 * The condition modifiers for a MELEE [plan], or none for a shot.
 *
 * The mirror of [rangedModifiers]' opening guard, and the whole safety
 * property of the pair. Both are called from one expression against ONE
 * `plan`, so exactly one of them can speak -- and the row they share prints
 * two numbers, "-2 ranged (but -4 in melee)". Without this line the two-tap
 * path (tick the chip in reach, tap "At range") would sum them to -6, which
 * the book prints nowhere: the live double count of 6bx's class.
 *
 * `internal` so [AttackDialogTest] can assert that, the way it already reaches
 * [isFired]. A content-only test would add a second reader of the melee value
 * and leave this slice's actual finding -- no production caller -- standing.
 */
internal fun meleeModifiers(
    plan: AttackPlan,
    circumstances: Set<String>,
): List<com.pathways.model.Modifier> {
    if (plan.isRanged) return emptyList()
    return Ranged.meleeModifiers(Ranged.meleeConditions().filter { it.condition in circumstances })
}

internal fun rangedModifiers(
    plan: AttackPlan,
    band: RangePenalty?,
    circumstances: Set<String>,
    weaponAcc: Int?,
    aimCancelled: Boolean,
    darknessCause: DarknessCause,
    darknessReduction: Int,
    darknessSeverity: Int?,
): List<com.pathways.model.Modifier> {
    if (!plan.isRanged) return emptyList()
    // The chosen band is passed whole, never re-looked-up by its lower bound:
    // "100+" begins at 100 and bandFor(100) is the -5 row (Codex).
    return Ranged.modifiers(
        band = band,
        conditions = Ranged.conditions().filter { it.condition in circumstances },
        // Keyed by the row's own condition, and only when the GM has said --
        // an empty map is what the engine already treats as "take the row's
        // figure", so there is one authority on that default and it is not
        // this screen.
        gmValue = darknessSeverity?.let { mapOf(DARKNESS_ROW to it) } ?: emptyMap(),
        weaponAcc = weaponAcc,
        aimCancelled = aimCancelled,
        darknessCause = darknessCause,
        darknessReduction = darknessReduction,
    )
}

/**
 * The two rows whose modifier is the weapon's Acc.
 *
 * Read off the content rather than spelled out here, so a re-extraction that
 * renames a row cannot leave this holding a string nothing matches -- which
 * would silently stop clearing an aim the character can no longer take.
 */
private val AIM_ROWS: Set<String> by lazy {
    Ranged.conditions().filter { it.weaponAcc }.map { it.condition }.toSet()
}

/**
 * The one row a [DarknessCause] can change, found by the range it prints
 * rather than by its name -- a re-extraction that reworded the condition would
 * otherwise leave this holding a string nothing matches, silently withdrawing
 * the chips from the character who owns the trait.
 */
private val DARKNESS_ROW: String by lazy {
    Ranged.conditions().single { it.worstCase != null }.condition
}

/** The GM's three answers, in the trait's own words. */
private fun causeLabel(cause: DarknessCause) = when (cause) {
    DarknessCause.HEAVY_FOG -> "Heavy fog"
    DarknessCause.DARKNESS -> "Darkness"
    DarknessCause.DIM_LIGHT -> "Dim gaslight or moonlight"
}

/**
 * A weapon off the catalogue and which of its attacks is being made.
 *
 * [mode] defaults to the first the book prints, which is the ordinary one --
 * the Combat Knife's cut before its thrust -- matching how an unarmed skill
 * defaults to its first listed attack.
 */
/**
 * One row of the skill list: a skill the sheet owns at [level], or one it
 * does not, at its default, with [untrained] saying where the number is from.
 */
private data class OfferedSkill(
    val key: SkillKey,
    val level: Int,
    val untrained: SkillDefaults.Untrained? = null,
)

internal data class Armed(val weapon: CataloguedWeapon, val mode: WeaponMode) {
    constructor(weapon: CataloguedWeapon) : this(weapon, weapon.damage.first())
}

/**
 * Whether what is being attacked with is fired at a distance.
 *
 * Null -- an empty hand -- is false: this book prints no thrown weapon and no
 * unarmed attack that reaches past arm's length.
 */
internal val Armed?.isFired: Boolean get() = this != null && Weapons.isRanged(weapon)

/**
 * The Acc to aim with, or null when nothing in hand is fired.
 *
 * Not simply the held weapon's Acc (Codex): the "At range" toggle stays the
 * player's, so a knife can be carried into a ranged plan, and the Hand Axe
 * prints **Acc 1**. Gating on the weapon merely being present let a hatchet
 * be aimed for +1, or +3 over three turns -- a modifier the book gives only
 * to something fired.
 */
internal val Armed?.aimAcc: Int? get() = if (isFired) this?.weapon?.acc else null

@Composable
private fun Stepper(label: String, value: Int, note: String, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label $value", Modifier.weight(1f))
        Text(note, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onStep(-1) }) { Text("−") }
        TextButton(onClick = { onStep(1) }) { Text("+") }
    }
}

private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"

/** "Guns (Pistol)" where the book specialises, "Brawling" where it does not. */
internal fun SkillKey.label(): String =
    if (specialization == null) name else "$name ($specialization)"

/**
 * What the character's traits add to an attack: the general term every
 * other roll on the sheet takes, and the to-hit clause the GM declared.
 *
 * The Attack roll was the one roll that took no trait term (6ev). A census
 * found [TraitEffects.modifiers] empty for every COMBAT skill, and that was
 * the wrong corpus: this dialog offers every owned skill, so Perfect
 * Balance's +2 reaches an Acrobatics attack here as it does on the Skills tab
 * (the diff review). Composed once per composition, printed and rolled.
 */
internal fun traitTerms(
    traits: List<Trait>,
    key: SkillKey,
    killerTarget: String?,
): List<com.pathways.model.Modifier> =
    TraitEffects.modifiers(traits, key) + TraitEffects.attackModifiers(traits, killerTarget)
