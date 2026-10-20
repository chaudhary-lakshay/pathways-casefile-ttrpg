package parity

import com.pathways.app.abilities
import com.pathways.app.abilityParagraphs
import com.pathways.app.bullets
import com.pathways.app.keyTerms
import com.pathways.app.named
import com.pathways.app.noteOf
import com.pathways.app.pickInside
import com.pathways.app.playedLine
import com.pathways.app.rejoined
import com.pathways.app.spiPriceOf
import com.pathways.app.standingTargets
import com.pathways.app.titleOf
import com.pathways.app.valuesOf
import com.pathways.app.vitalLines
import com.pathways.model.SecondaryDelta
import com.pathways.rules.Derive
import com.pathways.rules.NonStandardPathways
import com.pathways.rules.Premades
import com.pathways.rules.SecondaryCharacteristics
import com.pathways.rules.SelfControl
import com.pathways.rules.SheetComparison
import com.pathways.rules.content.ContentLoader

/** PathwayText, SheetLines and ModifierRange: the app's own text and number helpers. */
fun Vectors.appTextVectors() {
    val blocks = ContentLoader.pathways() + NonStandardPathways.all()
    for ((i, b) in blocks.withIndex()) {
        for ((field, text) in listOf("ability" to b.ability, "reinforcedAbilities" to b.reinforcedAbilities, "drawbacks" to b.drawbacks, "advantages" to b.advantages)) {
            if (text.isEmpty()) continue
            case("PathwayText.cut", i, field) {
                val paragraphs = abilityParagraphs(text)
                listOf(
                    paragraphs.map { listOf(it, it.text, it.body, bullets(it.lead), it.items.map(::named)) },
                    abilities(text).map { a -> listOf(a.head.heading?.let(::titleOf), a.head.heading?.let { h -> spiPriceOf(titleOf(h).second) }, a.parts.size) },
                    keyTerms(text),
                    rejoined(paragraphs) == text,
                )
            }
        }
        for ((j, g) in b.skills.withIndex()) case("PathwayText.noteOf", i, j) { noteOf(g.note) }
    }
    for (tag in listOf(null, "1 SPI", "2-3 SPI", "+2 SPI", "10 SPI, 1 turn", "Passive", "1d6 SPI")) {
        case("PathwayText.spiPriceOf", tag) { spiPriceOf(tag) }
    }
    for (sheet in Premades.all()) {
        val rows = SheetComparison.of(sheet)
        val c = sheet.toCharacter()
        val played = SecondaryCharacteristics.of(c.attributes, SecondaryDelta(hp = 2, will = -1))
        for (compared in listOf(true, false)) {
            case("SheetLines.vitalLines", sheet.name, compared) { vitalLines(sheet, rows, played, compared) }
        }
        for (row in rows) case("SheetLines.playedLine", sheet.name, row.label) { listOf(playedLine(row, row.computed), playedLine(row, "99")) }
        val d = Derive.of(c).secondaries
        val checks = SelfControl.checksFor(c.traits)
        for (state in listOf(-2, 0, 3)) {
            case("SheetLines.standingTargets", sheet.name, state) { standingTargets(checks, d.will, c.attributes.iq, state).toList() }
        }
    }
    for (from in -6..6) for (to in listOf(null, -8, -1, 0, 2, 6)) for (open in listOf(false, true)) {
        case("ModifierRange.valuesOf", from, to, open) { valuesOf(from, to, open) }
    }
    case("ModifierRange.pickInside", "a", "a", 3) { pickInside("a", "a" to 3) }
    case("ModifierRange.pickInside", "a", "b", 3) { pickInside("a", "b" to 3) }
    case("ModifierRange.pickInside", null, "b", 3) { pickInside<String>(null, "b" to 3) }
    case("ModifierRange.pickInside", "a", null, null) { pickInside<String>("a", null) }
}
