package com.pathways.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.pathways.rules.RandomSource
import com.pathways.rules.SkillRoll
import com.pathways.rules.SkillRollOutcome

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
    dice: RandomSource,
    onSpendFp: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val skills = remember(skillLevels) { skillLevels.entries.sortedBy { it.key.name } }
    var picked by remember { mutableStateOf<Map.Entry<SkillKey, Int>?>(null) }
    var plan by remember { mutableStateOf(AttackPlan()) }
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
                        outcomes = emptyList()
                        picked = null
                        plan = AttackPlan()
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
                                .clickable { picked = entry }
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
                    onPlan = { plan = it },
                    onRoll = {
                        // Rolled at the level the exertion started from, then
                        // charged: a character is not yet tired by an effort
                        // they are in the middle of making. The cost still
                        // lands, so the NEXT turn feels it.
                        val mods = conditions(chosen.key) + Attacks.modifiers(plan, chosen.value)
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
    onPlan: (AttackPlan) -> Unit,
    onRoll: () -> Unit,
) {
    val maxDeceptive = Attacks.maxDeceptive(skillLevel)
    Column {
        Text("$label $skillLevel", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
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

        if (!plan.isRanged) {
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
        val total = Attacks.modifiers(plan, skillLevel).sumOf { (it.effect as ModEffect.Flat).n }
        val attacks = Attacks.attackCount(plan)
        Text(
            "Attack options: ${if (total == 0) "no modifier" else signed(total)}" +
                (if (attacks > 1) ", $attacks attacks" else "") +
                if (fp > 0) ", costs $fp FP" else "",
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
