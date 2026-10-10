package com.pathways.app

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
import com.pathways.model.BeyonderState

/** The sheet's dialogs a hub tile opens; [SheetScreen] holds which are open. */
enum class SheetDialog {
    FRIGHT, CONCENTRATE, DETECT, ATTACK, DEFEND, DAMAGE, RITUAL, DIVINE, SPIRIT_VISION, BOON, ARTIFACTS,
}

/** The sheet's action hub: every tile, each gated as the book gates it. */
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
    ActionHub(modifier) {
        // The pathway was a full-width brass Button above this
        // grid, which made it a thirteenth action drawn to
        // different rules -- and cost a whole row of height to
        // say what a tile says. Arcane, because drinking is how
        // a mortal stops being one; ungated, because that is
        // exactly who needs it.
        HubAction(
            if (drunk == null) "Drink a potion" else "Pathway",
            Icons.Filled.Science,
            ActionKind.BEYONDER,
            onPathways,
        )
        HubAction("Fright", Icons.Filled.Psychology, ActionKind.ROLL) {
            onOpen(SheetDialog.FRIGHT)
        }
        // Ungated, like Fright and Detect: the maneuver is
        // "powers or skills requiring focus", and which of
        // them counts is the GM's call, not a skill list this
        // book never prints (6i).
        HubAction("Concentrate", Icons.Filled.CenterFocusStrong, ActionKind.ROLL) {
            onOpen(SheetDialog.CONCENTRATE)
        }
        // Not gated. Divination Arts is the DIVINER's skill;
        // being divined needs no skill at all, and the book's
        // detection roll falls back to Per, which a mortal
        // has. Who suspects is the GM's call (Detection).
        HubAction("Detect", Icons.Filled.Radar, ActionKind.ROLL) {
            onOpen(SheetDialog.DETECT)
        }
        HubAction("Attack", Icons.Filled.Bolt, ActionKind.COMBAT) {
            onOpen(SheetDialog.ATTACK)
        }
        HubAction("Defend", Icons.Filled.Shield, ActionKind.COMBAT) {
            onOpen(SheetDialog.DEFEND)
        }
        HubAction("Damage", Icons.Filled.HeartBroken, ActionKind.COMBAT) {
            onOpen(SheetDialog.DAMAGE)
        }
        // Each gated individually, which the grid allows and a
        // labelled group did not: an absent tile leaves no hole,
        // where an absent group left a heading over empty space.
        if (ritualLevel != null) {
            HubAction("Ritual", Icons.Filled.AutoAwesome, ActionKind.BEYONDER) {
                onOpen(SheetDialog.RITUAL)
            }
        }
        if (divinationLevel != null) {
            HubAction("Divine", Icons.Filled.Visibility, ActionKind.BEYONDER) {
                onOpen(SheetDialog.DIVINE)
            }
        }
        if (spiritVisionForm != null) {
            // Labelled with the pathway's OWN name for it: a
            // Hermit's block writes "Eyes of Mystery Prying"
            // and never "Spirit Vision", so a fixed label
            // would name something their sheet does not.
            HubAction(
                spiritVisionForm,
                Icons.Filled.RemoveRedEye,
                ActionKind.BEYONDER,
            ) { onOpen(SheetDialog.SPIRIT_VISION) }
        }
        if (ritualLevel != null) {
            // Same gate as the ritual: Chapter 13 rolls the
            // same skill, because a boon IS a ritual.
            HubAction("Boon", Icons.Filled.Church, ActionKind.BEYONDER) {
                onOpen(SheetDialog.BOON)
            }
        }
        HubAction("Spend points", Icons.Filled.Calculate, ActionKind.MANAGE) {
            onBuild()
        }
        // Not gated on anything. A Sealed Artifact is found,
        // not bought or awarded by Sequence: the grade table's
        // only gate is church rank, which this app does not
        // model. See SealedArtifacts.
        HubAction("Artifacts", Icons.Filled.Lock, ActionKind.MANAGE) {
            onOpen(SheetDialog.ARTIFACTS)
        }
    }
}
