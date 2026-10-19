package com.pathways.app

import com.pathways.rules.AttributeRoll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.BeyonderState
import com.pathways.rules.SpiritVision

/** The sheet's dialogs a hub tile opens; [SheetScreen] holds which are open. */
enum class SheetDialog {
    FRIGHT, CONCENTRATE, DETECT, ATTACK, DEFEND, DAMAGE, RITUAL, DIVINE, SPIRIT_VISION, BOON,
    /** The bare attribute rolls, opened from their Vitals rows (play session 4). */
    WILL_ROLL, PER_ROLL,
    /** Ch. 4's universal senses, rolled against current SPI. */
    INTUITION_ROLL, PERCEPTION_ROLL,
    /** Ch. 7's Short or Long Rest (v6.9i). */
    REST,
    /** Ch. 9 VI's Spirit World Creature Contract (v6.9i). */
    CONTRACT,
}

/** The dialog a Vitals row opens. */
internal fun rollDialogFor(kind: AttributeRoll.Kind): SheetDialog = when (kind) {
    AttributeRoll.Kind.WILL -> SheetDialog.WILL_ROLL
    AttributeRoll.Kind.PER -> SheetDialog.PER_ROLL
    AttributeRoll.Kind.INTUITION -> SheetDialog.INTUITION_ROLL
    AttributeRoll.Kind.PERCEPTION -> SheetDialog.PERCEPTION_ROLL
}

/**
 * The sheet's actions, each gated as the book gates it: four big buttons for
 * a turn (the combat three in the order a turn runs, and the pathway), then
 * bracketed words for everything else. See [PrimaryAction].
 */
@Composable
internal fun SheetActions(
    drunk: BeyonderState?,
    ritualLevel: Int?,
    hasSpiritVision: Boolean,
    sensesOffered: Boolean,
    onPathways: () -> Unit,
    onBuild: () -> Unit,
    onOpen: (SheetDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionGrid {
            val each = Modifier.weight(1f)
            PrimaryAction("Attack", Icons.Filled.Bolt, each) {
                onOpen(SheetDialog.ATTACK)
            }
            PrimaryAction("Defend", Icons.Filled.Shield, each) {
                onOpen(SheetDialog.DEFEND)
            }
            PrimaryAction("Damage", Icons.Filled.HeartBroken, each, harm = true) {
                onOpen(SheetDialog.DAMAGE)
            }
            // Ungated, because a mortal is exactly who needs it: drinking is
            // how one stops being mortal.
            PrimaryAction(
                if (drunk == null) "Drink a potion" else "Pathway",
                Icons.Filled.Science,
                each,
                onClick = onPathways,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Each Beyonder action gated on its own: an absent one leaves no
            // hole, where an absent labelled group left a heading over
            // nothing.
            if (ritualLevel != null) {
                ActionChip("Ritual", mystical = true) {
                    onOpen(SheetDialog.RITUAL)
                }
            }
            // A Beyonder's alone: the penalty is the Sequence, which a mortal has none of.
            if (ritualLevel != null && drunk != null) {
                ActionChip("Contract", mystical = true) {
                    onOpen(SheetDialog.CONTRACT)
                }
            }
            // Ungated: Divination Arts defaults to SPI-4 (ch. 8 I).
            ActionChip("Divine", mystical = true) {
                onOpen(SheetDialog.DIVINE)
            }
            if (hasSpiritVision) {
                ActionChip(SpiritVision.TRAIT, mystical = true) {
                    onOpen(SheetDialog.SPIRIT_VISION)
                }
            }
            if (sensesOffered) {
                ActionChip("Intuition", mystical = true) { onOpen(SheetDialog.INTUITION_ROLL) }
                ActionChip("Perception", mystical = true) { onOpen(SheetDialog.PERCEPTION_ROLL) }
            }
            // Ungated: an absent chip read as a feature that did not work.
            // The dialog refuses the roll, and says why, without Ritualistic
            // Magic or the 4 SPI of fuel -- so a mortal at SPI 0 still cannot
            // pray, and whether blood may fuel it is the owner's to rule.
            ActionChip("Boon", mystical = true) {
                onOpen(SheetDialog.BOON)
            }
            // Fright, Concentrate and Detect are ungated: which focus counts,
            // and who suspects, is the GM's call (6i, Detection); the
            // detection roll falls back to Per, which a mortal has.
            ActionChip("Fright") {
                onOpen(SheetDialog.FRIGHT)
            }
            ActionChip("Concentrate") {
                onOpen(SheetDialog.CONCENTRATE)
            }
            ActionChip("Detect") {
                onOpen(SheetDialog.DETECT)
            }
            // Ungated: whether a rest is possible is the GM's (ch. 7).
            ActionChip("Rest") {
                onOpen(SheetDialog.REST)
            }
            // Underlined, as frames 02 and 04 draw these two.
            ActionChip("Spend points", underlined = true, onClick = onBuild)
        }
    }
}
