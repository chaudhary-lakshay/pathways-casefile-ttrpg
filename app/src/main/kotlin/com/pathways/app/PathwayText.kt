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
        get() = heading.orEmpty().let { if (heading == null) "" else "$it: " } + lead +
            (if (items.isEmpty()) "" else (if (lead.isEmpty()) "" else " ") + items.joinToString("; "))
}

/**
 * Every heading is drawn at one level. Some name an ability ("Spirit
 * Vision"), some an outcome inside one ("Failure", "Success by 3"), and the
 * book does not say which is which, so the screen does not either.
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
    val clauses = topLevelClauses(body)
    if (clauses.size < LIST_MIN) return AbilityParagraph(heading, body, emptyList())
    // The first clause carries the lead-in sentences; the list starts after
    // the last sentence end inside it.
    val first = clauses.first()
    val cut = first.lastIndexOf(". ")
    val lead = if (cut < 0) "" else first.substring(0, cut + 1)
    val opening = if (cut < 0) first else first.substring(cut + 2)
    return AbilityParagraph(heading, lead, listOf(opening) + clauses.drop(1))
}

/**
 * [body] cut at each "; " outside brackets. A semicolon inside one belongs
 * to whatever the bracket lists, such as Paper Weaponry's damage table.
 */
private fun topLevelClauses(body: String): List<String> {
    val cuts = mutableListOf<Int>()
    var depth = 0
    body.forEachIndexed { i, c ->
        when (c) {
            '(', '[' -> depth++
            ')', ']' -> depth = maxOf(0, depth - 1)
            ';' -> if (depth == 0 && body.startsWith("; ", i)) cuts += i
        }
    }
    val starts = listOf(0) + cuts.map { it + 2 }
    val ends = cuts + body.length
    return starts.zip(ends) { a, b -> body.substring(a, b) }
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
