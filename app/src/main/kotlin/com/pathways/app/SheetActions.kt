package com.pathways.app

import com.pathways.rules.AttributeRoll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pathways.model.BeyonderState

/** The sheet's dialogs a hub tile opens; [SheetScreen] holds which are open. */
enum class SheetDialog {
    FRIGHT, CONCENTRATE, DETECT, ATTACK, DEFEND, DAMAGE, RITUAL, DIVINE, SPIRIT_VISION, BOON, ARTIFACTS,
    /** The bare attribute rolls, opened from their Vitals rows (play session 4). */
    WILL_ROLL, PER_ROLL,
}

/** The dialog a Vitals row opens. */
internal fun rollDialogFor(kind: AttributeRoll.Kind): SheetDialog = when (kind) {
    AttributeRoll.Kind.WILL -> SheetDialog.WILL_ROLL
    AttributeRoll.Kind.PER -> SheetDialog.PER_ROLL
}

/**
 * The sheet's actions, each gated as the book gates it: four big buttons for
 * a turn (the combat three in the order a turn runs, and the pathway), then
 * chips for everything else. See [PrimaryAction].
 */
@Composable
internal fun SheetActions(
    drunk: BeyonderState?,
    ritualLevel: Int?,
    divinationLevel: Int?,
    spiritVisionForm: String?,
    onPathways: () -> Unit,
    onBuild: () -> Unit,
    onOpen: (SheetDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val each = Modifier.weight(1f)
            PrimaryAction("Attack", Icons.Filled.Bolt, each) {
                onOpen(SheetDialog.ATTACK)
            }
            PrimaryAction("Defend", Icons.Filled.Shield, each) {
                onOpen(SheetDialog.DEFEND)
            }
            PrimaryAction("Damage", Icons.Filled.HeartBroken, each) {
                onOpen(SheetDialog.DAMAGE)
            }
            // Ungated, because a mortal is exactly who needs it: drinking is
            // how one stops being mortal.
            PrimaryAction(
                if (drunk == null) "Drink a potion" else "Pathway",
                Icons.Filled.Science,
                each,
                onPathways,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Each Beyonder chip gated on its own: an absent one leaves no
            // hole, where an absent labelled group left a heading over
            // nothing.
            if (ritualLevel != null) {
                ActionChip("Ritual", Icons.Filled.AutoAwesome) {
                    onOpen(SheetDialog.RITUAL)
                }
            }
            if (divinationLevel != null) {
                ActionChip("Divine", Icons.Filled.Visibility) {
                    onOpen(SheetDialog.DIVINE)
                }
            }
            if (spiritVisionForm != null) {
                // The pathway's OWN name for it: a Hermit's block writes
                // "Eyes of Mystery Prying" and never "Spirit Vision".
                ActionChip(spiritVisionForm, Icons.Filled.RemoveRedEye) {
                    onOpen(SheetDialog.SPIRIT_VISION)
                }
            }
            // Ungated: an absent chip read as a feature that did not work.
            // The dialog refuses the roll, and says why, without Ritualistic
            // Magic or the 4 SPI of fuel -- so a mortal at SPI 0 still cannot
            // pray, and whether blood may fuel it is the owner's to rule.
            ActionChip("Boon", Icons.Filled.Church) {
                onOpen(SheetDialog.BOON)
            }
            // Fright, Concentrate and Detect are ungated: which focus counts,
            // and who suspects, is the GM's call (6i, Detection); the
            // detection roll falls back to Per, which a mortal has.
            ActionChip("Fright", Icons.Filled.Psychology) {
                onOpen(SheetDialog.FRIGHT)
            }
            ActionChip("Concentrate", Icons.Filled.CenterFocusStrong) {
                onOpen(SheetDialog.CONCENTRATE)
            }
            ActionChip("Detect", Icons.Filled.Radar) {
                onOpen(SheetDialog.DETECT)
            }
            ActionChip("Spend points", Icons.Filled.Calculate, onBuild)
            // Not gated: a Sealed Artifact is found, not bought or awarded by
            // Sequence, and church rank is not modelled. See SealedArtifacts.
            ActionChip("Artifacts", Icons.Filled.Lock) {
                onOpen(SheetDialog.ARTIFACTS)
            }
        }
    }
}
