package parity

import com.pathways.model.Attr
import com.pathways.model.Attributes
import com.pathways.model.Difficulty
import com.pathways.model.SecondaryDelta
import com.pathways.rules.AttributeCosts
import com.pathways.rules.Derive
import com.pathways.rules.PointBudget
import com.pathways.rules.Premades
import com.pathways.rules.SecondaryCharacteristics
import com.pathways.rules.SheetComparison
import com.pathways.rules.SkillLadder

/** The 23 premades through the derivation, and the tables under it. */
fun Vectors.characterVectors() {
    for (sheet in Premades.all()) {
        val c = sheet.toCharacter()
        case("Premade.toCharacter", sheet.name) { c }
        case("Derive.of", sheet.name) { Derive.of(c) }
        case("SheetComparison.of", sheet.name) { SheetComparison.of(sheet) }
    }
    for (attr in Attr.entries) for (level in 8..31) {
        case("AttributeCosts.costOf", attr, level) { AttributeCosts.costOf(attr, level) }
    }
    for (d in Difficulty.entries) for (attribute in listOf(8, 10, 14)) {
        for (points in 0..70) {
            case("SkillLadder.levelFor", points, d, attribute) { SkillLadder.levelFor(points, d, attribute) }
        }
        case("SkillLadder.defaultFor", d, attribute) { SkillLadder.defaultFor(d, attribute) }
    }
    for (points in -1..70) case("SkillLadder.nextRungPoints", points) { SkillLadder.nextRungPoints(points) }
    for (bought in 0..40) for (studied in listOf(-1, 0, 1, 3, 8)) for (up in listOf(true, false)) {
        case("SkillLadder.stepped", bought, studied, up) { SkillLadder.stepped(bought, studied, up) }
    }
    val deltas = listOf(
        SecondaryDelta(),
        SecondaryDelta(hp = 2, fp = -1, will = 1, per = -2, basicSpeedQuarters = -3, basicMove = 1),
        SecondaryDelta(basicSpeedQuarters = -41, basicMove = -2),
        SecondaryDelta(hp = 100, fp = -100, basicSpeedQuarters = 7),
    )
    val attrs = listOf(Attributes(), Attributes(14, 12, 11, 13), Attributes(9, 9, 30, 9))
    for (a in attrs) for (d in deltas) {
        case("SecondaryCharacteristics.of", a, d) { SecondaryCharacteristics.of(a, d) }
        case("Secondaries.basicSpeed", a, d) { SecondaryCharacteristics.of(a, d).basicSpeed }
    }
    val keys = listOf("hp", "fp", "will", "per", "basicSpeed", "basicMove", "spi", "nope")
    for (d in deltas) {
        case("SecondaryCharacteristics.costOf", d) { SecondaryCharacteristics.costOf(d) }
        for (k in keys) {
            case("SecondaryCharacteristics.stepsOf", d, k) { SecondaryCharacteristics.stepsOf(d, k) }
            for (n in listOf(-150, -3, 0, 4, 150)) {
                case("SecondaryCharacteristics.withSteps", d, k, n) { SecondaryCharacteristics.withSteps(d, k, n) }
            }
            case("SecondaryCharacteristics.valueLabelOf", Attributes(14, 12, 11, 13), d, k) {
                SecondaryCharacteristics.valueLabelOf(SecondaryCharacteristics.of(Attributes(14, 12, 11, 13), d), k)
            }
        }
        val steps = mapOf("hp" to 3, "basicSpeed" to -2, "will" to 120, "spi" to 4)
        case("SecondaryCharacteristics.plusSteps", d, steps) { SecondaryCharacteristics.plusSteps(d, steps) }
    }
    for (sheet in Premades.all()) {
        val c = sheet.toCharacter()
        case("PointBudget.report", c.attributes, c.secondaryDelta, c.traits, c.skills) {
            PointBudget.report(c.attributes, c.secondaryDelta, c.traits, c.skills)
        }
    }
}
