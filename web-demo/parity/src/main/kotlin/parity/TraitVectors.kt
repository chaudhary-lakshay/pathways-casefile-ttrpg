package parity

import com.pathways.model.BeyonderState
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.Appearance
import com.pathways.rules.CharmedObject
import com.pathways.rules.LanguagePurchase
import com.pathways.rules.Languages
import com.pathways.rules.Premades
import com.pathways.rules.SelfControl
import com.pathways.rules.TraitAttributeRolls
import com.pathways.rules.TraitClauses
import com.pathways.rules.TraitEffects
import com.pathways.rules.TraitNames
import com.pathways.rules.TraitReport
import com.pathways.rules.Wealth
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.TraitDefinition

/** A catalogue row as a held trait: the printed price, as a magnitude. */
private fun TraitDefinition.held(kind: TraitKind) =
    Trait(name, Math.abs(cost ?: costMin ?: costMax ?: 0), kind)

/**
 * The trait lists every trait vector is asked of, by label: a premade's
 * (`premade:<name>`), the whole catalogue at once, and a hand-picked set of
 * levelled and variant spellings. The TS side rebuilds each from its label.
 */
internal fun traitLists(): List<Pair<String, List<Trait>>> {
    val catalogue = ContentLoader.advantages().map { it.held(TraitKind.ADVANTAGE) } +
        ContentLoader.disadvantages().map { it.held(TraitKind.DISADVANTAGE) }
    return Premades.all().map { "premade:${it.name}" to it.toCharacter().traits } +
        listOf("catalogue" to catalogue, "variants" to VARIANTS)
}

internal val VARIANTS = listOf(
    Trait("Alertness 2", 10, TraitKind.ADVANTAGE),
    Trait("Charisma 3", 15, TraitKind.ADVANTAGE),
    Trait("Hard to Kill 3", 6, TraitKind.ADVANTAGE),
    Trait("Acute Vision 2", 4, TraitKind.ADVANTAGE),
    Trait("Acute Hearing 1", 2, TraitKind.ADVANTAGE),
    Trait("Fit", 5, TraitKind.ADVANTAGE),
    Trait("Combat Reflexes", 15, TraitKind.ADVANTAGE),
    Trait("Luck", 15, TraitKind.ADVANTAGE),
    Trait("Versatile", 5, TraitKind.ADVANTAGE),
    Trait("Charmed Object (Stealth)", 5, TraitKind.ADVANTAGE),
    Trait("Language (Balam (Dutan), Accented)", 4, TraitKind.ADVANTAGE),
    Trait("Shyness (Mild)", 5, TraitKind.DISADVANTAGE),
    Trait("Light Sleeper", 5, TraitKind.DISADVANTAGE),
    Trait("Reckless", 10, TraitKind.DISADVANTAGE),
    Trait("Unfit", 5, TraitKind.DISADVANTAGE),
    Trait("Wealth (Poor)", 15, TraitKind.DISADVANTAGE),
    Trait("Secret (Beyonder)", 10, TraitKind.DISADVANTAGE),
    Trait("Talks to Himself", 1, TraitKind.QUIRK),
)

/** Every skill a vector asks about: the catalogue's, each as its key. */
internal fun catalogueSkills(): List<SkillKey> =
    ContentLoader.skills().map { SkillKey(it.name, it.specialization, it.attr, it.difficulty) }

fun Vectors.traitVectors() {
    val lists = traitLists()
    val names = (lists.flatMap { (_, t) -> t.map { it.name } } +
        listOf("Shyness (Mild)", "Charisma 3", "Language (Balam (Dutan), Accented)", "", "Nonsense 4", "Fit 2"))
        .distinct()
    for (n in names) {
        case("TraitNames.catalogueName", n) { TraitNames.catalogueName(n) }
        case("TraitNames.displayName", n) { TraitNames.displayName(n) }
        case("CharmedObject.skillOf", n) { CharmedObject.skillOf(n) }
    }
    case("TraitEffects.known") { TraitEffects.known().sorted() }
    case("TraitEffects.unappliedNames") { TraitEffects.unappliedNames().sorted() }
    case("TraitEffects.partlyAppliedNames") { TraitEffects.partlyAppliedNames().sorted() }
    case("SelfControl.borrowedRolls") { SelfControl.borrowedRolls() }
    val skills = catalogueSkills()
    val lookups = listOf("Alertness", "Charisma", "Hard to Kill", "Acute Vision", "Fit", "Luck", "Shyness")
    for ((label, traits) in lists) {
        for (n in lookups) case("TraitNames.levelOf", label, n) { TraitNames.levelOf(traits, n) }
        for (kind in TraitKind.entries) case("TraitReport.held", label, kind) { TraitReport.held(traits, kind) }
        case("TraitReport.applied", label) {
            TraitKind.entries.flatMap { TraitReport.held(traits, it) }.map { TraitReport.applied(it) }
        }
        case("TraitReport.reported", label) { TraitReport.reported(traits) }
        case("TraitReport.corruptionNotes", label) { TraitReport.corruptionNotes(traits) }
        case("SelfControl.checksFor", label) { SelfControl.checksFor(traits) }
        case("TraitClauses.frightClauses", label) { TraitClauses.frightClauses(traits) }
        case("TraitClauses.willClauses", label) { TraitClauses.willClauses(traits) }
        case("TraitClauses.concentrationClauses", label) { TraitClauses.concentrationClauses(traits) }
        case("TraitClauses.attackClauses", label) { TraitClauses.attackClauses(traits) }
        case("TraitClauses.refusesAim", label) { TraitClauses.refusesAim(traits) }
        case("TraitClauses.socialClauses", label) { TraitClauses.socialClauses(traits) }
        case("TraitClauses.perRollClauses", label) { TraitClauses.perRollClauses(traits) }
        case("TraitClauses.visionClause", label) { TraitClauses.visionClause(traits) }
        case("TraitClauses.allRollsClauses", label) { TraitClauses.allRollsClauses(traits) }
        case("TraitClauses.declaredStateClauses", label) { TraitClauses.declaredStateClauses(traits) }
        val declaredAll = TraitClauses.declaredStateClauses(traits).map { it.key }.toSet()
        for (declared in listOf(emptySet(), declaredAll)) {
            case("TraitClauses.declaredStates", label, declared) { TraitClauses.declaredStates(traits, declared) }
            for (physical in listOf(true, false)) {
                case("TraitClauses.declaredModifiers", label, declared, physical) {
                    TraitClauses.declaredModifiers(traits, declared, physical)
                }
            }
            case("TraitClauses.clauseBonus.fright", label, declared) {
                TraitClauses.clauseBonus(TraitClauses.frightClauses(traits), declared +
                    TraitClauses.frightClauses(traits).map { it.key })
            }
        }
        for (c in TraitClauses.attackClauses(traits).map { it.key } + listOf(null, "nope")) {
            case("TraitClauses.attackModifiers", label, c) { TraitClauses.attackModifiers(traits, c) }
        }
        case("TraitEffects.defaultBonus", label) { TraitEffects.defaultBonus(traits) }
        case("TraitEffects.darknessReduction", label) { TraitEffects.darknessReduction(traits) }
        case("TraitEffects.defenseBonus", label) { TraitEffects.defenseBonus(traits) }
        case("TraitEffects.healsFast", label) { TraitEffects.healsFast(traits) }
        case("TraitEffects.rollsActingMethod", label) { TraitEffects.rollsActingMethod(traits) }
        for (state in listOf(null, BeyonderState("Fool", 9, 0), BeyonderState("Wheel of Fortune", 7, 0))) {
            case("TraitEffects.rerollSource", label, state) { TraitEffects.rerollSource(traits, state) }
        }
        case("TraitAttributeRolls.all", label) {
            listOf(
                TraitAttributeRolls.alertnessBonus(traits), TraitAttributeRolls.spiRollBonus(traits),
                TraitAttributeRolls.knockdownBonus(traits), TraitAttributeRolls.deathRollBonus(traits),
                TraitAttributeRolls.consciousnessRollBonus(traits), TraitAttributeRolls.htRollBonus(traits),
                TraitAttributeRolls.stunRecoveryBonus(traits),
            )
        }
        case("TraitAttributeRolls.majorWoundHt", label) { TraitAttributeRolls.majorWoundHt(traits) }
        case("TraitAttributeRolls.stunRecoveryHt", label) { TraitAttributeRolls.stunRecoveryHt(traits) }
        case("Wealth.levelOf", label) { Wealth.levelOf(traits) }
        case("Appearance.levelOf", label) { Appearance.levelOf(traits) }
        case("Appearance.played", label) { Appearance.played(traits) }
        case("Appearance.without", label) { Appearance.without(traits) }
        for (w in Wealth.all()) case("Wealth.applied", label, w.status) { Wealth.applied(traits, w) }
        for (a in Appearance.all()) case("Appearance.applied", label, a.level) { Appearance.applied(traits, a) }
        val asked = if (label.startsWith("premade:")) skills.filterIndexed { i, _ -> i % 4 == 0 } else skills
        for (skill in asked) {
            val clauses = TraitClauses.skillRollClauses(traits, skill)
            if (clauses.isNotEmpty() || label != "catalogue") {
                case("TraitClauses.skillRollClauses", label, skill) { clauses }
            }
            case("TraitEffects.modifiers", label, skill, emptySet<String>()) { TraitEffects.modifiers(traits, skill) }
            val keys = clauses.map { it.key }.toSet()
            if (keys.isNotEmpty()) {
                case("TraitEffects.modifiers", label, skill, keys) { TraitEffects.modifiers(traits, skill, keys) }
            }
            case("CharmedObject.clauses", label, skill) { CharmedObject.clauses(traits, skill) }
        }
    }
    for (w in Wealth.all()) case("Wealth.traitFor", w.status) { Wealth.traitFor(w) }
    for (a in Appearance.all()) case("Appearance.traitFor", a.level) { Appearance.traitFor(a) }
    languageVectors()
}

private fun Vectors.languageVectors() {
    val loen = LanguagePurchase("Loen", "Native", Languages.BOTH_FORMS)
    val purchases = listOf(
        loen,
        LanguagePurchase("Intis", "Accented", Languages.SPOKEN_ONLY),
        LanguagePurchase("Feysac", "Broken", Languages.WRITTEN_ONLY),
        LanguagePurchase("Ancient Hermes", "Native", Languages.BOTH_FORMS),
        LanguagePurchase("Ancient Feysac", "Accented", Languages.BOTH_FORMS),
        LanguagePurchase("Highland", "Native", Languages.BOTH_FORMS),
    )
    val helpers = listOf(
        emptyList(),
        listOf(Trait(Languages.MULTILINGUAL, 5, TraitKind.ADVANTAGE)),
        listOf(Trait(Languages.TALENT, 10, TraitKind.ADVANTAGE)),
        listOf(Trait(Languages.DEAD_LANGUAGE_FLUENCY, 5, TraitKind.ADVANTAGE)),
        listOf(
            Trait(Languages.MULTILINGUAL, 5, TraitKind.ADVANTAGE),
            Trait(Languages.TALENT, 10, TraitKind.ADVANTAGE),
            Trait(Languages.DEAD_LANGUAGE_FLUENCY, 5, TraitKind.ADVANTAGE),
        ),
    )
    for (held in helpers) for (n in 0..purchases.size) {
        val bought = purchases.take(n)
        case("Languages.owned", bought, held) { Languages.owned(bought, held) }
        case("Languages.traits", bought, held) { Languages.traits(bought, held) }
    }
    case("Languages.owned", listOf(LanguagePurchase("Nope", "Native", Languages.BOTH_FORMS)), emptyList<Trait>()) {
        Languages.owned(listOf(LanguagePurchase("Nope", "Native", Languages.BOTH_FORMS)), emptyList())
    }
    for (p in purchases) case("LanguagePurchase.label", p) { p.label }
    for (type in listOf("Common", "Mystical", "Other")) {
        case("Languages.proficiencies", type) { Languages.proficiencies(type) }
        for (prof in listOf("Native", "Accented", "Broken", "Fluent")) {
            case("Languages.ritualModifier", type, prof) {
                Languages.ritualModifier(Languages.proficiency(type, prof))
            }
        }
    }
    case("Languages.ritualModifier", null, null) { Languages.ritualModifier(null) }
    case("Languages.forms") { Languages.forms() }
    case("Languages.notes") { Languages.notes() }
    for (state in listOf(null, BeyonderState("Fool", 9, 0), BeyonderState("Door", 8, 0))) {
        case("Languages.grantedBy", state) { Languages.grantedBy(state) }
    }
}
