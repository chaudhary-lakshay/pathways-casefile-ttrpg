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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.SkillKey
import com.pathways.model.Weapon
import com.pathways.rules.ParryBlock
import com.pathways.rules.Shields
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
 * The bonus applies only "against attacks from the front or shield side", and
 * this app does not model facing -- the book prints no arc to test against.
 * Baking the total into one number would let a GM roll a shielded Dodge
 * against a backstab (Codex), so BOTH numbers are shown and the arrow is
 * explained once above. Cheaper than a facing selector, and it cannot be read
 * as an unconditional total.
 */
private fun shielded(base: Int, shield: Shield?, reflexes: Int = 0): String {
    // Combat Reflexes is unconditional -- "+1 to all active defenses" -- so it
    // is folded into the base, where the shield's conditional DB cannot be.
    // Every row goes through this one function for exactly that reason: two
    // places adding the same +1 is how these numbers stop reconciling (6ai).
    val own = base + reflexes
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
private fun ShieldPicker(shields: List<Shield>, carried: Shield?, onPick: (Shield) -> Unit) {
    Text("Shield Readied", style = MaterialTheme.typography.titleSmall)
    if (carried != null) {
        Note(
            "Arrow shows the DB applied. It only counts against attacks from " +
                "the front or shield side — from behind, use the first number.",
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
    /** Ch. 3's Combat Reflexes, +1 to every active defence. See TraitEffects. */
    reflexes: Int = 0,
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
    val shieldSkill = remember(skillLevels) {
        skillLevels.entries.firstOrNull { it.key.name == SHIELD_SKILL }?.value
    }
    val shields = remember { Shields.all() }
    // Null is bare-handed, and it is the default: a shield must be Readied for
    // a turn before it does anything, so assuming one is carried would hand
    // out a bonus the character has not paid a turn for.
    var carried by remember { mutableStateOf<Shield?>(null) }
    // Parries already made THIS ROUND. The book's cumulative penalty was
    // committed in defenses.json's own notes cell from 6g onward, shown to
    // the GM, and applied by nothing -- the class 6h, 6m and 6ai each found.
    // Zero is the honest default: a round starts with the free parry.
    var priorParries by remember { mutableIntStateOf(0) }
    val parryPenalty = ParryBlock.parryPenalty(priorParries)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Active defenses") },
        text = {
            LazyColumn {
                item {
                    ShieldPicker(shields, carried) { carried = if (it == carried) null else it }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    DefenseRow("Dodge", shielded(dodge, carried, reflexes))
                    printed.firstOrNull { it.defense == "Dodge" }?.let { Note(it.notes) }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
                items(armed) { (name, value) ->
                    DefenseRow("Parry ($name)", shielded(value + parryPenalty, carried, reflexes))
                }
                item {
                    if (armed.isNotEmpty()) {
                        printed.firstOrNull { it.defense == "Parry (Armed)" }?.let { Note(it.notes) }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                }
                items(parries) { (name, value) ->
                    DefenseRow("Parry ($name)", shielded(value + parryPenalty, carried, reflexes))
                }
                item {
                    if (parries.isEmpty() && armed.isEmpty()) {
                        Note("No unarmed combat skill on this sheet; an untrained parry is the GM's call.")
                    }
                    printed.firstOrNull { it.defense.startsWith("Parry (Unarmed)") }
                        ?.let { Note(it.notes) }
                    // The stepper sits UNDER the parry rows, because it moves
                    // them and a control above them would read as applying to
                    // Dodge too -- which takes no such penalty ("May be used
                    // repeatedly").
                    if (parries.isNotEmpty() || armed.isNotEmpty()) {
                        ParriesThisRound(priorParries, parryPenalty) { priorParries = it }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    if (shieldSkill != null) {
                        val held = carried
                        val bare = ParryBlock.blockFor(shieldSkill, shieldDb = 0)
                        if (held == null) {
                            DefenseRow("Block", shielded(bare, null, reflexes))
                            Note("Pick a shield above; Block needs one Readied.")
                        } else {
                            // Shields.blockFor already folds the DB in, so the
                            // arrow is built by hand here rather than through
                            // shielded() -- but the trait bonus is added to
                            // one bare figure and carried across, so the two
                            // ends of the arrow cannot drift apart.
                            val own = bare + reflexes
                            val melee = Shields.blockFor(held, shieldSkill) + reflexes
                            DefenseRow("Block", "$own → $melee")
                            // The ranged figure is shown beside it rather than
                            // replacing it: a light shield is "-1 to Block vs.
                            // ranged" and one number cannot say both.
                            val shot = Shields.blockFor(held, shieldSkill, isRanged = true) + reflexes
                            if (shot != melee) Note("Against ranged attacks: $shot")
                        }
                        printed.firstOrNull { it.defense == "Block" }?.let { Note(it.notes) }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                    carried?.let {
                        Note("${it.type}: DB +${it.db}, Cover DR ${it.coverDr}, HP ${it.hp} — ${it.notes}")
                    }
                    Shields.rules().forEach { Note(it.rule) }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

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
            "Parry rows above include $penalty. " +
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
