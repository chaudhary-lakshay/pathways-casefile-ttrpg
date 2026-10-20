package parity

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.DiceExpr
import com.pathways.model.Outcome
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.DamageRoll
import com.pathways.rules.Damage
import com.pathways.rules.Derive
import com.pathways.rules.Divination
import com.pathways.rules.FrightCheck
import com.pathways.rules.LanguagePurchase
import com.pathways.rules.Languages
import com.pathways.rules.Pools
import com.pathways.rules.PotionDigestion
import com.pathways.rules.PowerSources
import com.pathways.rules.Premades
import com.pathways.rules.RitualMode
import com.pathways.rules.RitualPacing
import com.pathways.rules.SelfControl
import com.pathways.rules.SelfControlCheck
import com.pathways.rules.SheetComparison
import com.pathways.rules.SkillDefaults
import com.pathways.rules.SpiritVision
import com.pathways.rules.TraitClauses
import com.pathways.rules.Wounds
import com.pathways.rules.content.ContentLoader

/** The functions the UI calls that the other files reach only through a caller. */
fun Vectors.gapVectors() {
    damage()
    selfControl()
    content()
    perPremade()
    rituals()
    for (target in listOf(5, 11, 15)) for (crit in listOf(false, true)) for (f in THROWS) {
        case("Divination.outcome", target, crit, f) {
            scripted(f) { Divination.rollAgainst(target, it, crit).let { o -> listOf(o.isCritical, o.effectiveOutcome) } }
        }
    }
    for (will in listOf(8, 12)) for (mod in listOf(-3, 0)) for (f in THROWS.filter { it.sum() > 9 }) for (g in THROWS.take(4)) {
        case("FrightCheck.against", will, mod, f + g) {
            scripted(f + g) { FrightCheck.against(will, mod, it).let { o -> listOf(o, o.passed) } }
        }
    }
    for (fp in -12..12) for (max in listOf(1, 10, 12)) case("Pools.isFatigued", fp, max) { Pools.isFatigued(Pools.fp(fp, max)) }
    val declaredAll = TraitClauses.allRollsClauseRows().map { it.key }.toSet()
    for (d in listOf(emptySet(), declaredAll)) {
        case("TraitClauses.allRollsModifiers", d) { TraitClauses.allRollsModifiers(TraitClauses.allRollsClauseRows(), d) }
    }
}

private fun Vectors.damage() {
    val rolls = listOf(DamageRoll(DiceExpr(1, 6, -4), "cr"), DamageRoll(DiceExpr(2, 6, 1), "cut"), DamageRoll(DiceExpr(3, 6, 0), "pi+"))
    for ((i, d) in rolls.withIndex()) {
        case("Damage.renderRoll", i) { d.render() }
        for (f in listOf(listOf(1, 1, 1), listOf(6, 6, 6), listOf(2, 5, 3))) {
            for (o in Outcome.entries) case("Damage.roll", i, f, o) {
                scripted(f) { Damage.roll(d, it).let { r -> listOf(r, Damage.onAttack(r, o)) } }
            }
        }
    }
}

private fun Vectors.selfControl() {
    val specs = ContentLoader.disadvantages().mapNotNull { it.selfControl }.distinct()
    for ((i, s) in specs.withIndex()) for (mod in listOf(-2, 0, 3)) {
        case("SelfControl.targetFor", i, 11, 9, mod) { SelfControl.targetFor(s, 11, 9, mod) }
        for (f in THROWS) case("SelfControl.attempt", i, mod, f) {
            scripted(f) {
                SelfControl.attempt(SelfControlCheck("T", s, "p"), 11, 9, it, mod).let { o -> listOf(o, o.held) }
            }
        }
    }
}

private fun Vectors.content() {
    case("SpiritVision.astralColors") { SpiritVision.astralColors() }
    case("SpiritVision.etherBody") { SpiritVision.etherBody() }
    case("SpiritVision.etherBodyNote") { SpiritVision.etherBodyNote() }
    case("SpiritVision.pathwayDifferences") { SpiritVision.pathwayDifferences() }
    case("SpiritVision.activation") { SpiritVision.activation() }
    case("Wounds.locations") { Wounds.locations() }
    case("Wounds.damageTypes") { Wounds.damageTypes() }
    case("Wounds.majorWounds") { Wounds.majorWounds() }
    for (c in PowerSources.complexities()) for (p in RitualPacing.entries) {
        case("PowerSources.pacingModifier", c.complexity, p) { PowerSources.pacingModifier(c, p) }
    }
    for (n in -1..12) {
        case("PowerSources.participantBonus", n) { PowerSources.participantBonus(n) }
        case("PowerSources.sacrificeBonus", n) { PowerSources.sacrificeBonus(n) }
    }
    for (n in listOf(null) + (-1..12)) case("PowerSources.invocationBonus", n) { PowerSources.invocationBonus(n) }
    for (r in PowerSources.revelations()) for (s in -1..4) for (b in listOf(false, true)) {
        case("PowerSources.revelationPenalty", r.entity, s, b) {
            PowerSources.revelationPenalty(PowerSources.revelationNamed(r.entity), s, b)
        }
    }
    case("PowerSources.revelationNamed", "nope") { PowerSources.revelationNamed("nope") }
    val purchases = listOf(
        LanguagePurchase("Loen", "Native", Languages.BOTH_FORMS),
        LanguagePurchase("Intis", "Accented", Languages.SPOKEN_ONLY),
        LanguagePurchase("Feysac", "Broken", Languages.WRITTEN_ONLY),
        LanguagePurchase("Ancient Hermes", "Native", Languages.BOTH_FORMS),
    )
    val helpers = listOf(emptyList(), listOf(Trait(Languages.MULTILINGUAL, 5, TraitKind.ADVANTAGE)), listOf(Trait(Languages.TALENT, 10, TraitKind.ADVANTAGE)))
    for (held in helpers) {
        case("Languages.labels", purchases, held) {
            Languages.owned(purchases, held).map { (p, o) -> listOf(p.label, o?.isFree, o?.costLine, o?.label) }
        }
    }
}

private fun Vectors.perPremade() {
    for (sheet in Premades.all()) {
        val c = sheet.toCharacter()
        for (s in sheet.skills) {
            case("Premades.levels", sheet.name, s.name) { listOf(sheet.attributeLevelFor(s), sheet.levelFor(s)) }
        }
        val d = Derive.of(c)
        case("SheetComparison.secondaryRows", sheet.name) { SheetComparison.secondaryRows(d.secondaries, sheet.published) }
        case("Derive.attributes", sheet.name) { Derive.attributes(c, null) }
        val attrs = listOf(ControllingAttribute.Buyable(Attr.ST), ControllingAttribute.Buyable(Attr.IQ), ControllingAttribute.Will, ControllingAttribute.Per, ControllingAttribute.Spi)
        for (a in attrs) case("Derive.controllingLevel", sheet.name, a) { Derive.controllingLevel(a, c.attributes, d.secondaries, c.spi) }
        val level: (ControllingAttribute) -> Int = { Derive.controllingLevel(it, c.attributes, d.secondaries, c.spi) }
        for (b in listOf(0, 1)) {
            case("SkillDefaults.offered", sheet.name, b) { SkillDefaults.offered(d.skillLevels, level, b, c.traits) }
            for (k in ContentLoader.skills().take(80)) {
                val key = com.pathways.model.SkillKey(k.name, k.specialization, k.attr, k.difficulty)
                case("SkillDefaults.unbought", sheet.name, key, b) {
                    listOf(SkillDefaults.untrainedLevel(key, level, b), SkillDefaults.unboughtLevel(key, level, b, c.traits))
                }
            }
        }
        for (block in ContentLoader.pathways().filter { it.sequence == 9 }) {
            case("PotionDigestion.digestBlock", sheet.name, block.pathway) { PotionDigestion.digest(c, block) }
        }
        case("SpiritVision.isHeldBy", sheet.name) { SpiritVision.isHeldBy(c.traits) }
    }
    case("SpiritVision.isHeldBy", "+Spirit Vision") { SpiritVision.isHeldBy(listOf(Trait(SpiritVision.TRAIT, 0, TraitKind.ADVANTAGE))) }
}

private fun Vectors.rituals() {
    for (m in RitualMode.entries) for ((i, p) in (listOf(null) + m.preparations()).withIndex()) {
        for (chosen in listOf(null, -6, -2, 0, 3, 8)) {
            case("RitualMode.preparationValueFor", m, i - 1, chosen) { RitualMode.valueFor(p, chosen) }
        }
    }
    for ((i, l) in (listOf(null) + RitualMode.links()).withIndex()) for (chosen in listOf(null, -6, -2, 0, 3, 8)) {
        case("RitualMode.linkValueFor", i - 1, chosen) { RitualMode.valueFor(l, chosen) }
    }
}
