package com.pathways.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.ModEffect
import com.pathways.model.SkillKey
import com.pathways.rules.ClauseBonus
import com.pathways.rules.AttackManeuver
import com.pathways.rules.AttackPlan
import com.pathways.rules.Attacks
import com.pathways.rules.Damage
import com.pathways.rules.DamageRoll
import com.pathways.rules.DarknessCause
import com.pathways.rules.TraitClause
import com.pathways.rules.Ranged
import com.pathways.rules.SkillDefaults
import com.pathways.rules.SkillTarget
import com.pathways.rules.Weapons
import com.pathways.rules.Wounds
import com.pathways.rules.content.CataloguedWeapon
import com.pathways.rules.content.HitLocation
import com.pathways.rules.content.RangePenalty
import com.pathways.rules.content.UnarmedAttack
import com.pathways.rules.content.WeaponMode

@Composable
internal fun AttackOptions(
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
    /** A Charmed Object naming the skill in hand, or null (6hk). */
    charm: TraitClause?,
    charmLit: Boolean,
    onCharm: () -> Unit,
    fromTraits: Int,
    /** The held rows' declared "-N to all rolls" (6gs), on a line of its own. */
    states: ClauseBonus,
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
    standing: SkillTarget,
    fromTrackers: Int,
    onRoll: () -> Unit,
) {
    val maxDeceptive = Attacks.maxDeceptive(skillLevel)
    // The choices scroll and the target with Roll is pinned beneath them
    // ([AttackFooter]): with a distance band and nine conditions the panel is
    // taller than a phone, and Roll fell below the fold even on the tablet.
    // An AlertDialog does not scroll its own text slot. Found on device.
    Column {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                LevelBadge("$skillLevel", MaterialTheme.colorScheme.primary)
            }
            notes.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (attacks.isNotEmpty()) {
                SectionLabel("Strike")
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
                // Drawn empty with a weapon in hand, so picking one moves no
                // row under the finger (play session 5).
                Text(
                    // Shove prints "Special" and rolls nothing; saying so beats
                    // showing a damage line it does not have.
                    unarmed?.let { "${it.damage} — ${it.notes}" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    minLines = 1,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            ArmedWith(weapons, armed, wieldedAt, onArmed)
            // Chips WRAP rather than scroll sideways: a horizontally scrolling Row
            // inside an AlertDialog never received the drag on a 1080px phone, so
            // three of the five maneuvers -- Strong, Double and Charge -- could not
            // be selected at all. Found on device, not by a test.
            SectionLabel("Maneuver")
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
            // Two chips, the lit one saying where the target is. Both attack
            // options are melee-only, so switching to range drops them rather
            // than offering a choice the book does not allow.
            FlowRow {
                listOf(false to "In reach", true to "At range").forEach { (ranged, text) ->
                    FilterChip(
                        selected = plan.isRanged == ranged,
                        onClick = { if (plan.isRanged != ranged) onPlan(Attacks.atRange(plan, ranged)) },
                        label = { Text(text) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

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
                SectionLabel("Options")
                OptionStepper("Rapid Strike", plan.rapidStrikes, "${Attacks.RAPID_STRIKE_PENALTY} each") {
                    onPlan(plan.copy(rapidStrikes = (plan.rapidStrikes + it).coerceAtLeast(0)))
                }
                OptionStepper(
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
            if (charm != null) {
                SectionLabel(charm.shown)
                FilterChip(
                    selected = charmLit,
                    onClick = onCharm,
                    label = { Text(charm.printed) },
                )
            }
            // "Regular melee attacks only", and an attack that rolls damage for
            // the +2 to land on. Withheld rather than offered and refused: a chip
            // that takes the dialog down on Roll is not a choice (6t). The whole
            // scope lives in Attacks, because half of it -- the firearm, and
            // whether anything is being rolled at all -- is a fact about what is
            // held, which modifiers() never sees.
            SectionLabel("Extra effort")
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
        }
        val fp = Attacks.fpCost(plan)
        val total = Attacks.modifiers(plan, skillLevel, unarmed)
            .sumOf { (it.effect as ModEffect.Flat).n }
        val count = Attacks.attackCount(plan)
        val fromRange = rangedModifiers(plan, band, circumstances, armed.aimAcc, aimCancelled || aimRefusedBy != null, darknessCause, darknessReduction, darknessSeverity)
            .sumOf { (it.effect as ModEffect.Flat).n }
        // Every term the target is made of, in the order it was always
        // printed, handed to the footer that prints the target under them.
        val lines = mutableListOf<String>()
        lines += "Attack options: ${if (total == 0) "no modifier" else signed(total)}" +
            (if (count > 1) ", $count attacks" else "") +
            if (fp > 0) ", costs $fp FP" else ""
        val fromMelee = meleeModifiers(plan, circumstances)
            .sumOf { (it.effect as ModEffect.Flat).n }
        if (plan.isRanged) {
            lines += "Range and cover: ${if (fromRange == 0) "no modifier" else signed(fromRange)}"
        } else if (fromMelee != 0) {
            // Its own label, never "Range and cover": nothing about distance
            // is being priced here, and a total that does not match the line
            // above it is the breakdown defect this repo has shipped twice
            // (6ag, 6ai).
            lines += "Target: ${signed(fromMelee)}"
        }
        if (fromTraits != 0) {
            // Its own line, like "Target": a trait term the breakdown left
            // out would make the roll's total a number nothing above it adds
            // up to (the diff review's MEDIUM).
            lines += "Traits: ${signed(fromTraits)}"
        }
        states.line?.let { lines += "Declared on the sheet: $it" }
        location?.let {
            lines += "Called shot: ${it.target} ${signed(it.penalty)}, ${it.printedMultiplier} — ${it.effect}"
        }
        lines += (
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
                    "Damage on a hit: ${roll.render()}" + woundingNote(location, roll.type)
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
            }.orEmpty()
            )
        // The last term, and then what they all come to. This line used to
        // read "...are added when it is rolled", the only word of the target
        // this dialog gave before the dice.
        if (fromTrackers != 0) {
            lines += "Injury, fatigue and attrition: ${signed(fromTrackers)}"
        }
        AttackFooter(lines.filter { it.isNotEmpty() }, standing, count, onRoll)
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
    SectionLabel("Weapon")
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
                    // The book prints no untrained armed parry (Defend says
                    // why, [unskilledParryNote]), so this line prints none
                    // either. A firearm first: it parries at nothing, skill
                    // or not.
                    if (!Weapons.canParry(held.weapon)) {
                        "cannot parry"
                    } else {
                        wieldedAt(held.weapon)?.let { "parry ${Weapons.parryFor(held.weapon, it)}" }
                            ?: "untrained parry is the GM's call"
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
    SectionLabel("Target (${clauses.first().shown})")
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
 * What a hit of [type] is multiplied by at [location], as the damage line
 * says it. "there" only where a shot was called: with none, no chip is lit
 * and no line names a place, so the word pointed at nothing (6gi).
 */
internal fun woundingNote(location: HitLocation?, type: String): String {
    val times = Wounds.multiplierFor(location, type)
    if (times == 1.0) return ""
    return ", ${Wounds.render(times)}" + if (location == null) "" else " there"
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
    SectionLabel("Called shot")
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
internal data class OfferedSkill(
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
