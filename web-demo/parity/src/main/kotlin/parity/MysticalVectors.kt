package parity

import com.pathways.model.Attr
import com.pathways.model.BeyonderState
import com.pathways.model.BoonState
import com.pathways.model.Difficulty
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.AntiDivination
import com.pathways.rules.AntiDivinationTechniques
import com.pathways.rules.Boons
import com.pathways.rules.ContractPenalty
import com.pathways.rules.Detection
import com.pathways.rules.Incantations
import com.pathways.rules.InstancedPurchase
import com.pathways.rules.Languages
import com.pathways.rules.PotionConsumption
import com.pathways.rules.PowerSourceDeclaration
import com.pathways.rules.PowerSources
import com.pathways.rules.Pools
import com.pathways.rules.Premades
import com.pathways.rules.PurchasableTraits
import com.pathways.rules.PurchaseCeilings
import com.pathways.rules.Revelation
import com.pathways.rules.RitualMode
import com.pathways.rules.RitualPacing
import com.pathways.rules.RitualResolution
import com.pathways.rules.SealedArtifacts
import com.pathways.rules.SpiritContract
import com.pathways.rules.SpiritualSenses
import com.pathways.rules.Study
import com.pathways.rules.StudyMethod
import com.pathways.rules.StudyRefusedException
import com.pathways.rules.SuccessRoll
import com.pathways.rules.TraitClauses
import com.pathways.rules.TraitGrants
import com.pathways.rules.content.ContentLoader

fun Vectors.mysticalVectors() {
    antiDivinationVectors()
    ritualVectors()
    detectionVectors()
    characterRuleVectors()
}

private fun Vectors.antiDivinationVectors() {
    case("SealedArtifacts.grades") { SealedArtifacts.grades() }
    case("SpiritualSenses.all") { SpiritualSenses.all() }
    for (b in listOf(true, false)) for (s in -1..2) case("SpiritualSenses.isOffered", b, s) { SpiritualSenses.isOffered(b, s) }
    case("Incantations.standard") { Incantations.standard() }
    val techs = AntiDivinationTechniques.all()
    for (t in techs) {
        case("AntiDivinationTechniques.named", t.id.lowercase()) { AntiDivinationTechniques.named(t.id.lowercase()) }
        case("AntiDivinationTechniques.named", t.name.uppercase()) { AntiDivinationTechniques.named(t.name.uppercase()) }
    }
    case("AntiDivinationTechniques.named", "nope") { AntiDivinationTechniques.named("nope") }
    val picks = listOf(listOf(0), listOf(0, 0, 1), techs.indices.toList(), emptyList())
    for (p in picks) {
        val chosen = p.map { techs[it] }
        case("AntiDivinationTechniques.totals", p) {
            listOf(
                AntiDivinationTechniques.penaltyTotal(chosen), AntiDivinationTechniques.detectionBonus(chosen),
                AntiDivinationTechniques.spiFor(chosen),
            )
        }
    }
    case("AntiDivinationTechniques.onDetectionRoll") { AntiDivinationTechniques.onDetectionRoll().map { it.id } }
    for (p in listOf(0, -1, -3, -8, 2)) for (up in listOf(true, false)) {
        case("AntiDivination.halvedMagnitude", p, up) { AntiDivination.halvedMagnitude(p, up) }
    }
    for (seq in -1..10) case("AntiDivination.rowFor", seq) { AntiDivination.rowFor(seq) }
    for (target in 0..9) for (diviner in listOf(null, 0, target - 2, target, 9)) for (tech in listOf(0, -3, 1)) {
        for (link in listOf(false, true)) for (hp in listOf(0, 5, 20, -1)) {
            case("AntiDivination.against", target, diviner, tech, link, hp) {
                AntiDivination.against(target, diviner, tech, link, hp).let { listOf(it, AntiDivination.modifiers(it)) }
            }
        }
    }
    case("AntiDivination.counterNamed", "nope") { AntiDivination.counterNamed("nope") }
}

private fun Vectors.ritualVectors() {
    for (mode in RitualMode.entries) {
        val preps = mode.preparations()
        for (q in preps.map { it.quality } + listOf("nope")) case("RitualMode.preparationNamed", mode, q) { mode.preparationNamed(q.uppercase()) }
        for (pi in listOf(null) + preps.indices) for (li in listOf(null) + RitualMode.links().indices) {
            val prep = pi?.let { preps[it] }
            val link = li?.let { RitualMode.links()[it] }
            for (flourish in listOf(false, true)) for (pv in listOf(null, -6, -1, 0, 2)) {
                case("RitualMode.modifierTotal", mode, pi, li, flourish, pv, null) {
                    mode.modifierTotal(prep, link, flourish, pv, null)
                }
            }
            for (lv in listOf(-6, -2, 0)) case("RitualMode.modifierTotal", mode, pi, li, false, null, lv) {
                mode.modifierTotal(prep, link, false, null, lv)
            }
        }
    }
    case("RitualMode.modifierTotal.foreign") {
        RitualMode.QUICK.modifierTotal(RitualMode.FULL.preparations().last())
    }
    case("RitualMode.linkNamed", "nope") { RitualMode.linkNamed("nope") }
    for (w in RitualResolution.weights()) {
        for (chosen in listOf(w.spi - 1, w.spi, w.spi + 2, 100)) for (burned in listOf(-1, 0, 3)) {
            case("RitualResolution.spiFor", w.weight, chosen, burned) { RitualResolution.spiFor(w, chosen, burned) }
        }
        case("PowerSources.complexityFor", w.weight) { PowerSources.complexityFor(w) }
    }
    val decls = listOf(
        PowerSourceDeclaration(),
        PowerSourceDeclaration(pacing = RitualPacing.RUSHED, assistants = 5, sacrifice = -10, invocation = 3),
        PowerSourceDeclaration(pacing = RitualPacing.EXTENDED, suspended = true, sacrifice = 40),
        PowerSourceDeclaration(sacrifice = 41),
        PowerSourceDeclaration(invocation = 6),
        PowerSourceDeclaration(assistants = -1),
    ) + PowerSources.revelations().flatMap { r ->
        listOf(Revelation(r, 0), Revelation(r, 2), Revelation(r, 0, blessedByDeity = true)).map { PowerSourceDeclaration(revelation = it) }
    }
    val complexities = listOf(null) + PowerSources.complexities()
    for ((di, d) in decls.withIndex()) for (c in complexities) {
        case("PowerSources.modifierTotal", c?.complexity, di) { PowerSources.modifierTotal(c, d) }
    }
    for ((cost, hp) in listOf(0 to 0, 4 to 3, 4 to 8, 4 to 10, -1 to 0, 3 to -1)) case("PowerSources.spiOwed", cost, hp) { PowerSources.spiOwed(cost, hp) }
    for (s in listOf(-1, 0, 4)) case("PowerSources.hpFor", s) { PowerSources.hpFor(s) }
    val weights = RitualResolution.weights()
    val profs = listOf(null) + ContentLoader.languageProficiencies().filter { it.type == Languages.MYSTICAL }
    for ((wi, w) in weights.withIndex()) for (cor in listOf(0, 8)) for (pi in profs.indices) for (burned in listOf(0, 2)) {
        case("RitualResolution.targetFor", 12, wi, cor, pi, burned) {
            RitualResolution.targetFor(12, w, cor, language = profs[pi], burned = burned)
        }
    }
    for (target in listOf(4, 10, 15)) for (f in THROWS) for (g in listOf(THROWS[0], THROWS[9], THROWS[15])) {
        case("RitualResolution.rollAgainst", target, f + g) { scripted(f + g) { RitualResolution.rollAgainst(target, it) } }
    }
    for (m in -3..12) case("RitualResolution.successFor", m) { RitualResolution.successFor(m) }
    for (m in 0..12) case("RitualResolution.failureFor", m) { RitualResolution.failureFor(m) }
    for (r in 2..19) case("RitualResolution.criticalFor", r) { RitualResolution.criticalFor(r) }
    for (b in listOf(-1, 0, 5)) case("RitualResolution.burnBonus", b) { RitualResolution.burnBonus(b) }
    // Boons
    for (s in -1..10) case("Boons.penaltyForSequence", s) { Boons.penaltyForSequence(s) }
    case("Boons.weight") { Boons.weight() }
    case("Boons.groups") { Boons.groups() }
    case("Boons.spiFor") { Boons.spiFor() }
    val mods = Boons.modifiers()
    for (i in mods.indices) {
        val row = mods[i]
        val gms = listOf(emptyMap(), mapOf("${row.group} / ${row.label}" to (row.best ?: row.modifier)), mapOf("${row.group} / ${row.label}" to 99))
        for (gm in gms) case("Boons.modifierTotal", listOf(i), gm) { Boons.modifierTotal(listOf(row), gm) }
    }
    case("Boons.modifierTotal", listOf(0, 0), emptyMap<String, Int>()) { Boons.modifierTotal(listOf(mods[0], mods[0])) }
    case("Boons.modifierTotal", listOf(0), mapOf("x" to 1)) { Boons.modifierTotal(listOf(mods[0]), mapOf("x" to 1)) }
    for (seq in listOf(9, 7, 4)) for (cor in listOf(0, 6)) case("Boons.targetFor", 13, cor, seq) { Boons.targetFor(13, cor, seq) }
    // Spirit contract
    for (state in listOf(null, BeyonderState("Hermit", 6, 0), BeyonderState("Hermit", 7, 0), BeyonderState("Fool", 6, 0))) {
        case("SpiritContract.maxContracts", state) { SpiritContract.maxContracts(state) }
    }
    case("SpiritContract.spi") { SpiritContract.spi() }
    for (held in -1..4) for (f in listOf(listOf(1, 2, 3, 4, 5), listOf(6, 6, 6, 6, 6))) {
        case("SpiritContract.penaltyFor", 7, held, 3, f) { scripted(f) { SpiritContract.penaltyFor(7, held, 3, it) } }
    }
    for (b in listOf(-1, 0, 2)) case("SpiritContract.targetFor", 14, 6, b) {
        val p = ContractPenalty(7, listOf(3, 4))
        listOf(SpiritContract.targetFor(14, 6, p, b), p.total)
    }
    for (r in results()) case("SpiritContract.outcomeFor", r) { SpiritContract.outcomeFor(r) }
}

private fun Vectors.detectionVectors() {
    for (spi in listOf(-1, 0, 8, 12)) for (per in listOf(0, 1, 10, 12)) for (free in listOf(null, "spi", "PER", "Will")) {
        for (alert in listOf(-1, 0, 2)) {
            case("Detection.attributeFor", spi, per, free, alert, -2, 1) { Detection.attributeFor(spi, per, free, alert, -2, 1) }
        }
        case("Detection.attributeLine", spi, per, free, 2, -2, 1, "Light Sleeper +1") {
            Detection.attributeLine(spi, per, free, 2, -2, com.pathways.rules.ClauseBonus(1, "Light Sleeper +1"))
        }
        case("Detection.isPerRoll", spi, per, free) { Detection.isPerRoll(spi, per, free) }
    }
    for (p in Detection.awareness().map { it.pathway } + listOf("Nope")) case("Detection.awarenessFor", p.lowercase()) { Detection.awarenessFor(p.lowercase()) }
    for (r in results()) case("Detection.outcomeFor", r) { Detection.outcomeFor(r) }
    val sit = Detection.modifiers()
    val items = Detection.items()
    val techs = AntiDivinationTechniques.all()
    for (si in sit.indices) for (ii in listOf(null) + items.indices) {
        val held = ii?.let { listOf(items[it]) }.orEmpty()
        case("Detection.modifierTotal", listOf(si), listOf(0), listOfNotNull(ii), 2) {
            Detection.modifierTotal(listOf(sit[si]), listOf(techs[0]), held, 2)
        }
        case("Detection.partsFor", listOf(si), listOf(0), listOfNotNull(ii), 2) {
            Detection.partsFor(
                10, 12, listOf(sit[si]), listOf(techs[0]), held, 2, "Seer", null, 1, -1, -2, 0,
                com.pathways.rules.ClauseBonus(-1, "Reckless -1"), com.pathways.rules.ClauseBonus(0, null), -3,
            )
        }
    }
    for ((s, sm) in listOf(12 to 12, 3 to 12, 0 to 12)) for (aw in listOf(null, 0)) {
        case("Detection.attritionFor", s, sm, aw) { Detection.attritionFor(Pools.spi(s, sm), aw?.let { Detection.awareness()[it] }) }
    }
    for (target in listOf(6, 12)) for (f in THROWS) case("Detection.rollAgainst", target, f) {
        scripted(f) { Detection.rollAgainst(target, it, listOf("x")) }
    }
    case("Detection.targetFor", 10, 12) {
        Detection.targetFor(10, 12, 2, null, 1, -1, -2, 0, com.pathways.rules.ClauseBonus(-1, "x"), com.pathways.rules.ClauseBonus(1, "y"), -3)
    }
}

private fun Vectors.characterRuleVectors() {
    for (d in listOf(-1, 0, 10, 30, 55, 80, 99, 100, 101)) for (cor in listOf(-1, 0, 3)) {
        case("PotionConsumption.targetFor", d, cor, 1) { PotionConsumption.targetFor(d, cor, modifier = 1) }
    }
    for (d in listOf(0, 50, 100)) for (f in THROWS) {
        val dice = listOf(f, f + listOf(4), f + listOf(6, 6))
        for (fs in dice) case("PotionConsumption.attempt", d, 2, fs) {
            scripted(fs) { PotionConsumption.attempt(BeyonderState("Fool", 9, d), 2, it) }
        }
    }
    case("PotionConsumption.earlyAdvancementDice") { PotionConsumption.earlyAdvancementDice() }
    // Trait grants
    case("TraitGrants.tables") {
        listOf(
            TraitGrants.grantedTraitTable(), TraitGrants.refusedGrants(), TraitGrants.drawbackChoiceTable(),
            TraitGrants.boonTraitTable(), TraitGrants.refusedBoonGrants(),
        )
    }
    val blocks = ContentLoader.pathways()
    val boons = listOf(null) + com.pathways.rules.NonStandardPathways.all().map { BoonState(it.pathway, it.sequence) }
    for (b in blocks) for (dw in listOf(null, "Paranoia", "Nope")) {
        val state = BeyonderState(b.pathway, b.sequence, 0, dw)
        case("TraitGrants.grantedTraits", state, null) { TraitGrants.grantedTraits(state) }
        if (dw == null) case("TraitGrants.drawbackChoices", state) { TraitGrants.drawbackChoices(state) }
    }
    for (boon in boons) case("TraitGrants.grantedTraits", null, boon) { TraitGrants.grantedTraits(null, boon) }
    for (sheet in Premades.all().take(8)) {
        val traits = sheet.toCharacter().traits
        for (state in listOf(null, BeyonderState("Demoness", 6, 0), BeyonderState("Red Priest", 6, 0), BeyonderState("Sun", 7, 0))) {
            case("TraitGrants.withGrantedTraits", sheet.name, state, BoonState("Chaos Mist", 9)) {
                TraitGrants.withGrantedTraits(traits, state, BoonState("Chaos Mist", 9))
            }
        }
    }
    val bought = listOf(Trait("Charisma 1", 5, TraitKind.ADVANTAGE), Trait("Acute Vision 2", 4, TraitKind.ADVANTAGE))
    for (state in listOf(BeyonderState("Demoness", 6, 0), BeyonderState("Visionary", 9, 0))) {
        val merged = TraitGrants.withGrantedTraits(bought, state)
        case("TraitGrants.withGrantedTraits.bought", state) { merged }
        for (m in merged) case("TraitGrants.grantedLevelsIn", m, bought) { TraitGrants.grantedLevelsIn(m, bought) }
    }
    // Purchasable traits
    case("PurchasableTraits.offered") { PurchasableTraits.offered() }
    case("PurchasableTraits.unoffered") { PurchasableTraits.unoffered() }
    case("PurchasableTraits.instantiable") { PurchasableTraits.instantiable() }
    case("PurchasableTraits.blessingNames") { PurchasableTraits.blessingNames().toList() }
    val held = Premades.all().first().toCharacter().traits + Trait("Fit", 5, TraitKind.ADVANTAGE)
    for (t in PurchasableTraits.offered()) {
        case("PurchasableTraits.canBuy", t.name, held) { listOf(PurchasableTraits.canBuy(t, held), PurchasableTraits.missingPrerequisite(t, held)) }
    }
    val purchases = PurchasableTraits.instantiable().flatMap { row ->
        listOf(
            InstancedPurchase(row.name, instance = "Rats", cost = row.costMin),
            InstancedPurchase(row.name, instance = " ", cost = row.costMin),
            InstancedPurchase(row.name, instance = "Rats", cost = row.costMax + 1),
        ) + row.variants.map { InstancedPurchase(row.name, variant = it.first.uppercase(), cost = it.second) } +
            row.variants.take(1).map { InstancedPurchase(row.name, variant = it.first, cost = it.second + 1) }
    } + listOf(InstancedPurchase("Nope", instance = "x", cost = 1))
    for (p in purchases) case("PurchasableTraits.instanced", p) { PurchasableTraits.instanced(p) }
    case("PurchasableTraits.resolved", purchases + purchases.take(3), held) { PurchasableTraits.resolved(purchases + purchases.take(3), held) }
    // Ceilings
    val helds = listOf(emptyList(), listOf("God of Combat"))
    for (a in Attr.entries) for (h in helds) case("PurchaseCeilings.attribute", a, h) { PurchaseCeilings.attribute(a, h) }
    for (k in listOf("hp", "fp", "will", "per", "basicSpeed", "basicMove", "spi")) case("PurchaseCeilings.secondarySteps", k) { PurchaseCeilings.secondarySteps(k) }
    // Study
    for (d in Difficulty.entries) for (m in StudyMethod.entries) {
        case("Study.cellFor", d, m) { listOf(Study.cellFor(d, m), Study.isPriced(d, m)) }
        case("Study.costPerPoint", d, m) { Study.costPerPoint(d, m) }
        for (points in listOf(-1, 0, 1, 2, 4, 8, 12, 24)) for (attr in listOf(10, 13)) for (granted in listOf(false, true)) {
            case("Study.plan", points, d, attr, m, granted) {
                val r = Study.plan(points, d, attr, m, granted)
                listOf(r.getOrNull(), (r.exceptionOrNull() as StudyRefusedException?)?.refusal)
            }
        }
    }
}
