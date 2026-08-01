package com.pathways.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.pathways.rules.AttackManeuver
import com.pathways.rules.AttackPlan
import com.pathways.rules.Attacks
import com.pathways.rules.Damage
import com.pathways.rules.DamageRoll
import com.pathways.rules.RandomSource
import com.pathways.rules.Ranged
import com.pathways.rules.SkillRoll
import com.pathways.rules.SkillRollOutcome
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
 * Every skill is offered, not a curated combat list: this book prints no
 * weapon table and tags no skill as a combat skill, so which of them can
 * attack is the GM's call, and inventing the list here would be inventing
 * content.
 */
@Composable
fun AttackDialog(
    skillLevels: Map<SkillKey, Int>,
    conditions: (SkillKey) -> List<com.pathways.model.Modifier>,
    st: Int,
    dx: Int,
    dice: RandomSource,
    onSpendFp: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val skills = remember(skillLevels) { skillLevels.entries.sortedBy { it.key.name } }
    var picked by remember { mutableStateOf<Map.Entry<SkillKey, Int>?>(null) }
    var plan by remember { mutableStateOf(AttackPlan()) }
    // Null for every skill with no attack table -- Wrestling included, which
    // the book gives "None (grapple only)".
    var unarmed by remember { mutableStateOf<UnarmedAttack?>(null) }
    // Distance and circumstances, kept out of AttackPlan: they describe the
    // TARGET and the scene, not the attacker's choice of maneuver, and only a
    // ranged attack reads them.
    var band by remember { mutableStateOf<RangePenalty?>(null) }
    var circumstances by remember { mutableStateOf<Set<String>>(emptySet()) }
    // A list, not one roll: All-Out Double and Rapid Strike each make more
    // than one attack, and rolling once for them silently drops the rest
    // (Codex). Resolving the whole maneuver here is also what keeps its FP
    // charged once -- rolling the second attack as a fresh maneuver would
    // charge the 2 FP twice.
    var outcomes by remember { mutableStateOf<List<SkillRollOutcome>>(emptyList()) }

    val chosen = picked
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
                        picked = null
                        plan = AttackPlan()
                        unarmed = null
                        band = null
                        circumstances = emptySet()
                    },
                ) { Text("Another turn") }
            }
        },
        title = { Text(if (chosen == null) "Attack with which skill?" else "Attack") },
        text = {
            when {
                outcomes.isNotEmpty() -> LazyColumn {
                    items(outcomes) { AttackResult(it) }
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
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(entry.key.label(), Modifier.weight(1f))
                            Text("${entry.value}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                else -> AttackOptions(
                    label = chosen.key.label(),
                    skillLevel = chosen.value,
                    plan = plan,
                    unarmed = unarmed,
                    // Two unrelated bonuses reach damage: All-Out Attack
                    // (Strong)'s +2 and Boxing's for skill above DX. Attacks
                    // composes them so neither can be dropped here.
                    damage = unarmed?.let {
                        val fromSkill = if (it.skill == BOXING) Damage.boxingBonus(dx, chosen.value) else 0
                        Damage.forAttack(it, st, Attacks.damageBonus(plan, fromSkill))
                    },
                    attacks = Damage.attacksFor(chosen.key.name),
                    band = band,
                    circumstances = circumstances,
                    onAttack = { unarmed = it },
                    onBand = { band = it },
                    onCircumstance = { name ->
                        circumstances = if (name in circumstances) {
                            circumstances - name
                        } else {
                            circumstances + name
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
                            Attacks.modifiers(plan, chosen.value, unarmed) +
                            rangedModifiers(plan, band, circumstances)
                        outcomes = List(Attacks.attackCount(plan)) {
                            SkillRoll.at(
                                label = chosen.key.label(),
                                base = chosen.value,
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
    plan: AttackPlan,
    unarmed: UnarmedAttack?,
    damage: DamageRoll?,
    attacks: List<UnarmedAttack>,
    band: RangePenalty?,
    circumstances: Set<String>,
    onAttack: (UnarmedAttack) -> Unit,
    onBand: (RangePenalty) -> Unit,
    onCircumstance: (String) -> Unit,
    onPlan: (AttackPlan) -> Unit,
    onRoll: () -> Unit,
) {
    val maxDeceptive = Attacks.maxDeceptive(skillLevel)
    // Scrolls: with a distance band and nine conditions to choose from, the
    // panel is taller than a phone and the Roll button fell off the bottom.
    // An AlertDialog does not scroll its own text slot. Found on device.
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("$label $skillLevel", style = MaterialTheme.typography.titleMedium)
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
                    // carrying it over silently would throw on Roll.
                    onClick = { onPlan(AttackPlan(maneuver = option, isRanged = plan.isRanged)) },
                    label = { Text(option.printed) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        FilterChip(
            selected = plan.isRanged,
            // Both attack options are melee-only, so switching to range drops
            // them rather than offering a choice the book does not allow.
            onClick = { onPlan(AttackPlan(maneuver = plan.maneuver, isRanged = !plan.isRanged)) },
            label = { Text(if (plan.isRanged) "At range" else "In reach") },
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (plan.isRanged) {
            RangedOptions(band, circumstances, onBand, onCircumstance)
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
        }
        if (plan.maneuver == AttackManeuver.CHARGE) {
            FilterChip(
                selected = plan.heroicCharge,
                onClick = { onPlan(plan.copy(heroicCharge = !plan.heroicCharge)) },
                label = { Text("Heroic Charge (2 FP)") },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        val fp = Attacks.fpCost(plan)
        val total = Attacks.modifiers(plan, skillLevel, unarmed)
            .sumOf { (it.effect as ModEffect.Flat).n }
        val count = Attacks.attackCount(plan)
        val fromRange = rangedModifiers(plan, band, circumstances)
            .sumOf { (it.effect as ModEffect.Flat).n }
        Text(
            "Attack options: ${if (total == 0) "no modifier" else signed(total)}" +
                (if (count > 1) ", $count attacks" else "") +
                if (fp > 0) ", costs $fp FP" else "",
            style = MaterialTheme.typography.bodySmall,
        )
        if (plan.isRanged) {
            Text(
                "Range and cover: ${if (fromRange == 0) "no modifier" else signed(fromRange)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            damage?.let { "Damage on a hit: ${it.render()}" }
                ?: if (unarmed != null) "Rolls no damage; see the note above." else "",
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
 * Distance and the scene, for a shot (Ch. 5, p. 39-40).
 *
 * The two Aim rows are shown but not selectable: their modifier is the
 * weapon's Acc, and this book prints no weapon table, so there is no number to
 * apply. Saying so beats hiding a rule the GM may still want to use by hand.
 */
@Composable
private fun RangedOptions(
    band: RangePenalty?,
    circumstances: Set<String>,
    onBand: (RangePenalty) -> Unit,
    onCircumstance: (String) -> Unit,
) {
    val bands = remember { Ranged.bands() }
    val conditions = remember { Ranged.conditions() }
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
    conditions.filter { it.weaponAcc }.forEach {
        Text(
            "${it.condition}: ${it.printed} — no weapon in this book prints one.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * The range and condition modifiers for [plan], or none for a melee attack.
 *
 * Darkness is applied at the gentle end the book prints. Its cell reads "-3 to
 * -9 depending on severity", and the harsher end is the GM's call to make out
 * loud rather than the app's to assume -- [Ranged.modifiers] takes an override
 * for exactly that, and the screen does not yet offer one.
 * ponytail: a stepper here when a GM asks for it.
 */
private fun rangedModifiers(
    plan: AttackPlan,
    band: RangePenalty?,
    circumstances: Set<String>,
): List<com.pathways.model.Modifier> {
    if (!plan.isRanged) return emptyList()
    // The chosen band is passed whole, never re-looked-up by its lower bound:
    // "100+" begins at 100 and bandFor(100) is the -5 row (Codex).
    return Ranged.modifiers(
        band = band,
        conditions = Ranged.conditions().filter { it.condition in circumstances },
    )
}

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
