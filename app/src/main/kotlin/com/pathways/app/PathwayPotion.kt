package com.pathways.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pathways.rules.DigestedCharacter
import com.pathways.rules.PotionDigestion
import com.pathways.rules.content.SequenceBlock

/**
 * What one potion grants, as cards a player scans: the stat gains as pills,
 * the skills with their level, then the book's own text for advantages,
 * drawbacks and abilities. Every word of that text is the book's; only the
 * layout is this screen's ([abilityParagraphs]).
 */
internal fun LazyListScope.potionContent(
    block: SequenceBlock,
    digested: DigestedCharacter,
    /** Under the drawbacks: the choice a block like Hanged Man 9's asks for (6he), or null. */
    drawbackPicker: (@Composable () -> Unit)?,
    /** A held potion's powers can be paid for here ([AbilitySpend]); null for one browsed. */
    spend: AbilitySpend? = null,
) {
    item {
        PotionCard("Gains") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (block.stats.isEmpty()) Text("No stat gains", style = MaterialTheme.typography.bodyMedium)
                // Stamped in red, as frame 10 draws "SPI +9": a potion's gain
                // is a Beyonder fact.
                block.stats.forEach {
                    Stamp(
                        "${it.attr} ${formatDelta(it.delta)}",
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.typography.titleSmall.copy(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold),
                    )
                }
                if (digested.grants.spi != 0) ChipTag("SPI after digestion ${digested.effectiveSpi}")
            }
            block.stats.filter { it.note.isNotEmpty() }.forEach {
                Text("${it.attr} ${it.note}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            if (block.sequence < PotionDigestion.STARTING_SEQUENCE) {
                Text(
                    "Levels and totals count every potion from Sequence " +
                        "${PotionDigestion.STARTING_SEQUENCE} down to ${block.sequence}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    if (block.skills.isNotEmpty() || block.unresolvedSkills.isNotEmpty()) {
        item {
            PotionCard("Skills") {
                block.skills.forEachIndexed { i, grant ->
                    if (i > 0) DashedRule()
                    // The full identity, not the base name: Death grants Hidden
                    // Lore (Spirits) at Seq 8 and (Spirit World) at Seq 6.
                    val base = grant.name.substringBefore(" (")
                    val spec = if (" (" in grant.name) grant.name.substringAfter(" (").removeSuffix(")") else null
                    val level = digested.grants.skillLevels.entries.firstOrNull {
                        it.key.name == base && it.key.specialization == spec && it.key.difficulty == grant.difficulty
                    }?.value
                    SkillLine(
                        grant.name,
                        noteOf(grant.note),
                        "${grant.attr.code}/${grant.difficulty.code}",
                        level?.toString() ?: "+${grant.bonus}",
                    )
                }
                // Printed, and naming no skill the book lists (6gk): shown as
                // printed, with nothing computed from it.
                block.unresolvedSkills.forEachIndexed { i, printed ->
                    if (i > 0 || block.skills.isNotEmpty()) DashedRule()
                    SkillLine(
                        printed.substringBefore("/"),
                        "names no skill the book lists; the GM assigns it",
                        "",
                        printed.substringAfterLast(" "),
                    )
                }
            }
        }
    }
    if (block.advantages.isNotEmpty()) item { PotionCard("Advantages") { BookText(block.advantages) } }
    if (block.drawbacks.isNotEmpty()) {
        item {
            PotionCard("Drawbacks") {
                // Hanged Man 9 names its three under one cell: a label each.
                // Never a list: Chained 7's semicolons sit inside two rows.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    abilityParagraphs(block.drawbacks).forEach { BookText(it.body, it.heading) }
                }
                drawbackPicker?.invoke()
            }
        }
    }
    item { Abilities("Abilities", block.ability, spend) }
    if (block.reinforcedAbilities.isNotEmpty()) {
        item { Abilities("Reinforced abilities", block.reinforcedAbilities, spend) }
    }
}

/**
 * One chip per drawback the block offers, the chosen one selected. A row
 * the character bought is disabled and says so: sell it in the builder
 * first. The catalogue's "(pathway)" prints as the block does.
 */
@Composable
internal fun DrawbackPicker(
    choices: List<String>,
    chosen: String?,
    pathway: String,
    canChoose: (String) -> Boolean,
    onChoose: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        choices.forEach { name ->
            val selected = name == chosen
            val enabled = selected || canChoose(name)
            val label = name.replace("(pathway)", "($pathway)")
            CaseChip(
                selected = selected,
                enabled = enabled,
                onClick = { onChoose(name) },
                label = { Text(if (enabled) label else "$label · bought") },
            )
        }
    }
}

/** The potion's sections, each a [LedgerCard] (frame 10). */
@Composable
private fun PotionCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) = LedgerCard(title, action, content)

/** A granted skill: name (and the book's note), "IQ/VH" in mono, the level boxed (frame 10). */
@Composable
private fun SkillLine(name: String, note: String, attr: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (note.isNotEmpty()) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            attr,
            Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        LevelBox(value)
    }
}

/**
 * One row per power: its name, the bracket the book prints after it, and a
 * chevron. The text is closed until the row is tapped (the owner rejects
 * text walls), except in a card short enough to read at a glance.
 */
/**
 * Paying a power's fixed SPI price ([spiPriceOf]) from the SPI the
 * character has left, [left] as drawn: play session 2 moved Authority's
 * "1 SPI" on the tracker by hand. [onSpend] takes the price and the [left]
 * the button was drawn with, so a second tap of one picture is dropped.
 */
internal class AbilitySpend(val left: Int, val onSpend: (price: Int, drawnLeft: Int) -> Unit)

@Composable
internal fun Abilities(title: String, text: String, spend: AbilitySpend? = null) {
    val all = remember(text) { abilities(text) }
    val named = all.indices.filter { all[it].head.heading != null }
    var open by rememberSaveable(text) { mutableStateOf(if (named.size <= OPEN_AT_MOST) named else emptyList()) }
    val allOpen = open.containsAll(named)
    PotionCard(
        title,
        action = if (named.size <= OPEN_AT_MOST) {
            null
        } else {
            {
                BracketAction(if (allOpen) "Close all" else "Open all", { open = if (allOpen) emptyList() else named })
            }
        },
    ) {
        // One ledger, a dashed rule between powers (frame 10).
        Column {
            all.forEachIndexed { i, ability ->
                if (i > 0) DashedRule()
                AbilityRow(ability, expanded = i in open, spend = spend, onToggle = { open = if (i in open) open - i else open + i })
            }
        }
    }
}

/** A card with this many powers or fewer starts open. */
private const val OPEN_AT_MOST = 2

@Composable
private fun AbilityRow(ability: Ability, expanded: Boolean, spend: AbilitySpend?, onToggle: () -> Unit) {
    val heading = ability.head.heading
    // Text that opens with no name has nothing to tap: it is drawn as it is.
    if (heading == null) {
        AbilityText(ability)
        return
    }
    val (name, tag) = titleOf(heading)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Hide text" else "Show text", role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        // At the row's end beside the chevron, as frame 10 draws "1 SPI".
        if (tag != null) Box(Modifier.padding(horizontal = 8.dp).widthIn(max = 160.dp)) { Tag(tag) }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (expanded) {
        AbilityText(ability)
        val price = spiPriceOf(tag)
        if (spend != null && price != null) {
            // Personal SPI "cannot be reduced below 0" (ch. 7 Power Source A).
            InkAction(
                if (spend.left >= price) "Spend $price SPI" else "Spend $price SPI: ${spend.left} left",
                onClick = { spend.onSpend(price, spend.left) },
                enabled = spend.left >= price,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

/** The heading's bracket, in the book's words: a cost, "Passive", a resistance. */
@Composable
private fun Tag(text: String) {
    // The stamp red: a power's cost or kind is a Beyonder fact, the colour
    // the pool strip gives SPI. Outlined, not tinted: red on a red wash fell
    // under AA through the paper grain (3.9:1, Codex).
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** The power's own text, then each outcome and aspect under its label. */
@Composable
private fun AbilityText(ability: Ability) {
    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Paragraph(ability.head, label = null)
        ability.parts.forEach { Paragraph(it, label = it.heading) }
    }
}

@Composable
private fun Paragraph(paragraph: AbilityParagraph, label: String?) {
    val pieces = bullets(paragraph.lead)
    // A lead that opens on a bullet has no lead-in of its own.
    val leadIn = pieces.firstOrNull().takeUnless { paragraph.lead.trimStart().startsWith(BULLET) }
    val listed = pieces.drop(if (leadIn == null) 0 else 1) + paragraph.items.flatMap(::bullets)
    Column(Modifier.padding(start = if (label == null) 0.dp else 12.dp)) {
        if (label != null || leadIn != null) BookText(leadIn.orEmpty(), label)
        listed.forEach { item ->
            Row(Modifier.padding(top = 4.dp, start = 4.dp)) {
                Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                val (name, rest) = named(item)
                BookText(rest, name)
            }
        }
    }
}

/** The book's words at reading size, with the numbers a player acts on picked out. */
@Composable
private fun BookText(text: String, label: String? = null) {
    val body = highlighted(text, MaterialTheme.colorScheme.tertiary)
    val labelled = if (label == null) {
        body
    } else {
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(label) }
            append("  ")
            append(body)
        }
    }
    Text(labelled, style = MaterialTheme.typography.bodyMedium)
}

private fun highlighted(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (range in keyTerms(text)) {
        append(text.substring(at, range.first))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(text.substring(range)) }
        at = range.last + 1
    }
    append(text.substring(at))
}

/** "+2", "-1", "+0.25" — the book's own notation, without a trailing ".0". */
internal fun formatDelta(delta: Double): String {
    val sign = if (delta < 0) "" else "+"
    return if (delta == delta.toInt().toDouble()) "$sign${delta.toInt()}" else "$sign$delta"
}
