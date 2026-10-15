package com.pathways.app

/**
 * One run of a potion's ability text, laid out: the name the book gives it,
 * the sentence that leads in, and a list where the book strings three or
 * more clauses together with semicolons.
 *
 * ponytail: split at display time, not at ingestion. Nothing reads these
 * pieces but the screen, and [rejoined] gives back the stored string byte for
 * byte, which PathwayTextTest checks over every block. Move it into
 * tools/extract_pathways.py if a rule ever needs one ability by name.
 */
internal data class AbilityParagraph(val heading: String?, val lead: String, val items: List<String>) {
    val text: String
        get() = (if (heading == null) "" else "$heading: ") + body

    /** Everything under the heading, as one run. */
    val body: String
        get() = lead + (if (items.isEmpty()) "" else (if (lead.isEmpty()) "" else " ") + items.joinToString("; "))
}

/**
 * Every heading, at one level: some name an ability ("Spirit Vision"),
 * some an outcome inside one ("Failure", "Success by 3"). [abilities] puts
 * the second kind under the first.
 *
 * A heading opens the text or follows the end of a sentence, starts with a
 * word (so the page reference "p. B420:" is none), and every word after is
 * capitalised, a number, a parenthesis or a joining word. "While active:"
 * fails on "active", which is what keeps it inside its paragraph.
 */
internal fun abilityParagraphs(text: String): List<AbilityParagraph> {
    val starts = HEADING.findAll(text).map { it.range.first }.toList()
    val bounds = (listOf(0) + starts.filter { it > 0 }).distinct()
    return bounds.mapIndexed { i, start ->
        val end = if (i + 1 < bounds.size) bounds[i + 1] - 1 else text.length
        paragraph(text.substring(start, end))
    }
}

/** One power, and the outcomes and aspects the book prints under it. */
internal data class Ability(val head: AbilityParagraph, val parts: List<AbilityParagraph>)

/**
 * [abilityParagraphs], with each outcome of a roll (Success, Critical
 * Failure) and each aspect of a power (Passive, Duration) put under the
 * power it follows. Drawn at one level they read as abilities called
 * "Failure" (the owner, 2026-10-13). The grouping rests on no text opening
 * with one, which PathwayTextTest pins.
 *
 * Only a heading that is one of those words and nothing else is a part:
 * "Passive Luck" is a power. The book's "Success by 3+:" and "Quick
 * Contest:" are not matched, and stay where the parser left them.
 */
internal fun abilities(text: String): List<Ability> {
    val grouped = mutableListOf<Ability>()
    for (paragraph in abilityParagraphs(text)) {
        val last = grouped.lastOrNull()
        if (last != null && paragraph.heading?.let(PART::matches) == true) {
            grouped[grouped.lastIndex] = last.copy(parts = last.parts + paragraph)
        } else {
            grouped += Ability(paragraph, emptyList())
        }
    }
    return grouped
}

/** "Decryption (IQ / 1 SPI)" is the name and what it costs: the closing bracket is the tag. */
internal fun titleOf(heading: String): Pair<String, String?> {
    val tagged = TAGGED.matchEntire(heading) ?: return heading to null
    return tagged.groupValues[1] to tagged.groupValues[2]
}

/**
 * The fixed SPI a bracket ([titleOf]'s tag) prices a power at, or null where
 * it prints none or one the GM must choose ("X SPI", "1+ SPI", "1–2 SPI").
 * The first price is the activation's: "2 SPI + 1 SPI/turn" is 2, then
 * whatever upkeep the GM runs on the tracker.
 */
internal fun spiPriceOf(tag: String?): Int? =
    tag?.let { SPI_PRICE.find(it)?.groupValues?.get(1)?.toInt() }

private val SPI_PRICE = Regex("""(?<![\d–\-+])(\d+) SPI\b""")

/**
 * The lead-in, then one line per bullet. The PDF's bullet glyph is stored
 * as U+007F, which draws as an empty box. The last bullet runs into the
 * prose after it: the PDF kept no line break to cut at.
 */
internal fun bullets(text: String): List<String> = text.split(BULLET).map { it.trim() }.filter { it.isNotEmpty() }

/** A bullet's own name, where it opens with one, and the rest of it. */
internal fun named(item: String): Pair<String?, String> {
    val name = HEADING.matchAt(item, 0) ?: return null to item
    return name.groupValues[1] to item.substring(name.range.last + 1)
}

/** A skill row's note as a line under the name. */
internal fun noteOf(note: String): String = note.removePrefix("— ")

internal const val BULLET = '\u007f'

private val PART = Regex("""(?:Critical )?(?:Success|Failure)(?: by \d+)?|Passive|Duration|Range|Examples?|Note""")

private val TAGGED = Regex("""(.+?)\s*\(([^()]*)\)""")

/** The stored string, put back together: the test that nothing was lost. */
internal fun rejoined(paragraphs: List<AbilityParagraph>): String = paragraphs.joinToString(" ") { it.text }

/**
 * Where the numbers a player acts on sit: costs in SPI, HP, FP or CoR,
 * distances and durations, dice, and signed modifiers.
 */
internal fun keyTerms(text: String): List<IntRange> = TERM.findAll(text).map { it.range }.toList()

private fun paragraph(run: String): AbilityParagraph {
    val named = HEADING.matchAt(run, 0)
    val heading = named?.groupValues?.get(1)
    val body = if (named == null) run else run.substring(named.range.last + 1)
    val clauses = topLevel(body, "; ").let { cuts ->
        (listOf(0) + cuts.map { it + 2 }).zip(cuts + body.length) { a, b -> body.substring(a, b) }
    }
    if (clauses.size < LIST_MIN) return AbilityParagraph(heading, body, emptyList())
    // The first clause carries the lead-in sentences; the list starts after
    // the last sentence end inside it, outside brackets (the diff review).
    val first = clauses.first()
    val cut = topLevel(first, ". ").lastOrNull() ?: -1
    val lead = if (cut < 0) "" else first.substring(0, cut + 1)
    val opening = if (cut < 0) first else first.substring(cut + 2)
    return AbilityParagraph(heading, lead, listOf(opening) + clauses.drop(1))
}

/**
 * Where [separator] occurs in [text] outside brackets and quotation marks.
 * A semicolon or a full stop inside one belongs to whatever it holds, such
 * as Paper Weaponry's damage table or a three-part honorific.
 */
private fun topLevel(text: String, separator: String): List<Int> {
    val cuts = mutableListOf<Int>()
    var depth = 0
    var quoted = false
    text.forEachIndexed { i, c ->
        when (c) {
            '(', '[' -> depth++
            ')', ']' -> depth = maxOf(0, depth - 1)
            '"' -> quoted = !quoted
        }
        if (depth == 0 && !quoted && text.startsWith(separator, i)) cuts += i
    }
    return cuts
}

private const val LIST_MIN = 3

private const val NAME_WORD = """[A-Z][a-z][\w'’!\-]*"""
private const val WORD = """(?:[A-Z][\w'’!\-]*|\([^)]*\)|\d+%?)"""
private const val JOIN = """(?:of|the|and|to|a|an|in|on|for|with|from|by|&|or|as|at|vs\.)"""
private const val SEP = """(?:\s+—\s+|\s*/\s*|\s+)"""

private val HEADING = Regex(
    """(?:^|(?<=[.!?)\]] ))($NAME_WORD(?:$SEP(?:$WORD|$JOIN)){0,8}): """,
)

private val TERM = Regex(
    """\b\d+d\d*(?:[+\-−]\d+)?\b""" +
        """|\b\d+(?:[–-]\d+)?\s+(?:SPI|HP|FP|CoR)\b""" +
        """|\b\d+(?:[–-]\d+)?[\s-]?(?:meters?|m|km|seconds?|minutes?|hours?|turns?|days?)\b""" +
        """|(?<![\w+\-−])[+\-−]\d+%?""",
)
