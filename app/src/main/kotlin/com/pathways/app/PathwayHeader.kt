package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pathways.rules.content.SequenceBlock

// The pathway screen's head as frames 10-11 draw it: a boxed back button and
// a typed kicker, the potion beside its sigil, the four rungs as one ruled
// row, the digestion bar, and the Drink button in ink.

/** Back, boxed, and the character and pathway as a typed kicker (frame 10). */
@Composable
internal fun PathwayBar(name: String, pathway: String?, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxedIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back to sheet", onBack)
        Caps(
            pathway?.let { "$name · $it Pathway" } ?: "$name · Choose a pathway",
            Modifier.padding(start = 16.dp).weight(1f),
            style = MaterialTheme.typography.labelMedium.tabular(),
        )
    }
}

/** The potion read: its sigil boxed, its name in Fraunces, its rung in mono. */
@Composable
internal fun PotionHeading(block: SequenceBlock) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val art = pathwayArt(block.pathway)
        if (art != null) {
            Image(
                painter = painterResource(art),
                contentDescription = "${block.pathway} Pathway",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(96.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .padding(8.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 20.dp)) {
            // One line, shrunk to fit: a name that wrapped ("Unwinged Angel
            // (Coldblooded)", seen on the SM-T570) pushed the rungs down
            // under the finger that had just tapped one.
            val display = MaterialTheme.typography.displaySmall
            BasicText(
                block.name,
                Modifier.semantics { heading() },
                style = display.copy(color = MaterialTheme.colorScheme.onSurface),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = display.fontSize),
            )
            Text(
                "Sequence ${block.sequence} · ${block.pathway} Pathway",
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The four rungs as one ruled row (frame 10): the ink shows through a 1 dp
 * gap between the cells, as the action hub's grid does.
 */
@Composable
internal fun SequenceRow(content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.outline,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.height(IntrinsicSize.Min).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            content = content,
        )
    }
}

/**
 * One rung: "SEQ 9" over its potion, filled in ink when it is the block
 * being read, and ticked when the character has drunk it.
 */
@Composable
internal fun SequenceTile(
    sequence: Int,
    potion: String,
    selected: Boolean,
    drunk: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ink = if (selected) scheme.onPrimary else scheme.onSurface
    Column(
        modifier
            .fillMaxHeight()
            .background(if (selected) scheme.primary else scheme.surfaceContainerLow)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Caps(
                "Seq $sequence",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = if (selected) ink else scheme.onSurfaceVariant,
            )
            if (drunk) {
                Icon(Icons.Filled.Check, contentDescription = "drunk", tint = ink, modifier = Modifier.padding(start = 4.dp).size(14.dp))
            }
        }
        val body = MaterialTheme.typography.bodyLarge
        BasicText(
            potion,
            Modifier.padding(top = 2.dp),
            style = body.copy(color = ink, textAlign = TextAlign.Center),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            autoSize = WholeWords(min = 10.sp, max = body.fontSize),
        )
    }
}

/**
 * The digestion of the potion drunk, as ten cells (frame 10): one per whole
 * 10% ([digestionCells]). Read as a progress bar, "Digestion 40%".
 */
@Composable
internal fun DigestionBar(digestion: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clearAndSetSemantics {
                contentDescription = "Digestion $digestion%"
                progressBarRangeInfo = ProgressBarRangeInfo(digestion / 100f, 0f..1f)
            },
    ) {
        Row {
            Text("Digestion", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge.tabular())
            Text("$digestion%", style = MaterialTheme.typography.labelLarge.tabular().copy(fontWeight = FontWeight.Bold))
        }
        val filled = digestionCells(digestion)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(10) { i ->
                val on = i < filled
                Box(
                    Modifier
                        .weight(1f)
                        .height(12.dp)
                        .background(if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                        .border(1.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

/** The one button this screen is for: filled ink, the flask, typed capitals. */
@Composable
internal fun DrinkButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, shape = ButtonShape, modifier = modifier.heightIn(min = 56.dp)) {
        Icon(Icons.Filled.Science, contentDescription = null, Modifier.size(18.dp))
        Caps(
            label,
            Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = LocalContentColor.current,
        )
    }
}

/** POTION | DIGESTION & CORRUPTION, the open one filled in ink (frames 10-11). */
@Composable
internal fun PathwayTabs(tab: Int, onTab: (Int) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    // Both cells the row's height: at 390 dp and font 1.3 the second label
    // wraps, and the open cell's ink stopped short of it.
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(2.dp, MaterialTheme.colorScheme.outline, shape).selectableGroup(),
    ) {
        listOf("Potion", "Digestion & corruption").forEachIndexed { i, label ->
            Segment(label, icon = null, open = tab == i, Modifier.weight(1f).fillMaxHeight()) { onTab(i) }
        }
    }
}

/**
 * One pathway in the chooser: its sigil, then its name. No frame draws the
 * chooser; it takes the ruled card the rest of the screen uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PathwayTile(name: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val art = pathwayArt(name)
            if (art != null) {
                Image(
                    painter = painterResource(art),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(64.dp),
                )
            }
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Whether a line of [text], each ending (exclusive) at one of [lineEnds],
 * ends between two letters: a word broken across lines.
 */
internal fun breaksAWord(text: String, lineEnds: List<Int>): Boolean =
    lineEnds.dropLast(1).any { end -> end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter() }

/**
 * Shrinks a text from [max] towards [min] until it fits without breaking a
 * word: the stock step-based size counts only overflow, and "Magicia/n"
 * fits two lines.
 */
private class WholeWords(private val min: TextUnit, private val max: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var size = max.value
        while (size > min.value) {
            val layout = performLayout(constraints, text, size.sp)
            val ends = (0 until layout.lineCount).map { layout.getLineEnd(it) }
            if (!layout.hasVisualOverflow && !breaksAWord(text.text, ends)) return size.sp
            size -= 1f
        }
        return min
    }

    override fun equals(other: Any?) = other is WholeWords && other.min == min && other.max == max

    override fun hashCode() = 31 * min.hashCode() + max.hashCode()
}
