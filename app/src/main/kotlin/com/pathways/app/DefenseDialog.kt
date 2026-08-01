package com.pathways.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.SkillKey
import com.pathways.model.Weapon
import com.pathways.rules.ParryBlock
import com.pathways.rules.UnarmedStyle
import com.pathways.rules.content.ContentLoader

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
 * Active defenses for the character as they stand (Ch. 5, p. 36-37).
 *
 * Every number comes from [ParryBlock] or [com.pathways.rules.Derive] --
 * nothing is computed here. What the dialog adds is the pairing: which of the
 * character's own skills can defend, and what the book prints beside each
 * formula ("Cumulative -4 per additional parry", "Once per round").
 *
 * A shield's DB is the one figure the app cannot know, and it is shown as an
 * explicit "+ shield DB" rather than assumed to be zero.
 */
@Composable
fun DefenseDialog(
    dodge: Int,
    skillLevels: Map<SkillKey, Int>,
    weapons: List<Weapon> = emptyList(),
    onDismiss: () -> Unit,
) {
    val printed = remember { ContentLoader.defenses() }
    val modifiers = remember { ContentLoader.defenseModifiers() }
    val parries = remember(skillLevels) {
        skillLevels.entries
            .mapNotNull { (key, level) ->
                UNARMED[key.name]?.let { style -> key.name to ParryBlock.unarmedParry(style, level) }
            }
            .sortedBy { it.first }
    }
    val armed = remember(weapons, skillLevels) { armedParries(weapons, skillLevels) }
    val shield = remember(skillLevels) {
        skillLevels.entries.firstOrNull { it.key.name == SHIELD_SKILL }?.value
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Active defenses") },
        text = {
            LazyColumn {
                item {
                    DefenseRow("Dodge", dodge.toString())
                    printed.firstOrNull { it.defense == "Dodge" }?.let { Note(it.notes) }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
                items(armed) { (name, value) -> DefenseRow("Parry ($name)", value.toString()) }
                item {
                    if (armed.isNotEmpty()) {
                        printed.firstOrNull { it.defense == "Parry (Armed)" }?.let { Note(it.notes) }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                }
                items(parries) { (name, value) -> DefenseRow("Parry ($name)", value.toString()) }
                item {
                    if (parries.isEmpty() && armed.isEmpty()) {
                        Note("No unarmed combat skill on this sheet; an untrained parry is the GM's call.")
                    }
                    printed.firstOrNull { it.defense.startsWith("Parry (Unarmed)") }
                        ?.let { Note(it.notes) }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    if (shield != null) {
                        DefenseRow("Block", "${ParryBlock.blockFor(shield, shieldDb = 0)} + shield DB")
                        printed.firstOrNull { it.defense == "Block" }?.let { Note(it.notes) }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }

                    Text(
                        "Modifiers",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(modifiers) { Note(it.modifier) }
            }
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
