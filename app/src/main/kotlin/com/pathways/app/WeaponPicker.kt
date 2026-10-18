package com.pathways.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.SkillKey
import com.pathways.rules.SkillDefaults
import com.pathways.rules.Weapons
import com.pathways.rules.content.CataloguedWeapon

/**
 * The weapons carried in play, with what each one parries at.
 *
 * Possession, not training. A character may carry a weapon no skill of theirs
 * arms -- a borrowed broadsword is still in hand -- and the row says so
 * instead of hiding it, because a saved weapon that renders nowhere is
 * indistinguishable from a save that lost it.
 *
 * Nothing here is billed: chapter 8 prices weapons in money and this app
 * tracks none. The row sits in the builder all the same, because that is
 * where everything a player changes about a character already lives.
 */
@Composable
internal fun WeaponsRow(
    carried: List<CataloguedWeapon>,
    skillLevels: Map<SkillKey, Int>,
    mastery: SkillDefaults.WeaponMastery?,
    dx: Int,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column {
        SectionHead("Weapons", "Carry a weapon", onAdd) {
            if (carried.isEmpty()) {
                Text(
                    "None carried — the defence screen has no armed parry to show.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        carried.forEach { weapon ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(weapon.name, style = MaterialTheme.typography.bodyLarge)
                    Text(carriedLine(weapon, skillLevels, mastery, dx), style = MaterialTheme.typography.bodySmall)
                }
                InkAction("Put down", onClick = { onRemove(weapon.name) }, Modifier.padding(start = 8.dp))
            }
        }
    }
}

/**
 * What this weapon is worth to the character holding it.
 *
 * Both numbers come from the rules rather than from arithmetic here: the
 * level is [SkillDefaults.wieldedAt]'s -- the highest skill that arms it,
 * no less than DX in a master's hand, which is what Defend parries at -- and the parry is
 * chapter 5's `skill / 2 + 3`. The three cases are three different facts --
 * no skill, no parry, and a number -- and a screen that printed one line for
 * all three would be saying the character can parry when they cannot.
 */
internal fun carriedLine(
    weapon: CataloguedWeapon,
    skillLevels: Map<SkillKey, Int>,
    mastery: SkillDefaults.WeaponMastery?,
    dx: Int,
): String {
    val level = SkillDefaults.wieldedAt(weapon, skillLevels, mastery, dx)
        ?: return "${weapon.skill} — not a skill this character has, so no parry"
    val parry = Weapons.parryFor(weapon, level)
        ?: return "${weapon.skill} $level — cannot parry"
    return "${weapon.skill} $level — parry $parry"
}

/**
 * Chapter 8's nine weapons (p. 80).
 *
 * The WHOLE catalogue, not the rows the character's skills can wield: a GM
 * may hand anyone a weapon, and offering only the trained ones would make
 * possession require training, which neither the book nor this app says
 * (the design review's finding). A weapon carried untrained simply prints no
 * parry.
 */
@Composable
internal fun WeaponPicker(
    carried: List<String>,
    onCarry: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val offered = remember { Weapons.all() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { CloseButton(onDismiss) },
        title = { FormTitle(null) { Text("Carry a weapon") } },
        text = {
            LazyColumn {
                items(offered) { weapon ->
                    val held = carried.any { it.equals(weapon.name, ignoreCase = true) }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !held) { onCarry(weapon.name) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(weapon.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${weapon.skill} · reach/range ${weapon.range} · ${weapon.cost}" +
                                if (held) " · carried" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
    )
}
