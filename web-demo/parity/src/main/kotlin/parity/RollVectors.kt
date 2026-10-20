package parity

import com.pathways.model.Attr
import com.pathways.model.Condition
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.Duration
import com.pathways.model.Layer
import com.pathways.model.ModEffect
import com.pathways.model.ModTarget
import com.pathways.model.Modifier
import com.pathways.model.ModifierId
import com.pathways.model.ModifierSource
import com.pathways.model.SkillKey
import com.pathways.model.SourceKind
import com.pathways.model.StackGroup
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.ActiveDefenses
import com.pathways.rules.AttackManeuver
import com.pathways.rules.AttackPlan
import com.pathways.rules.Attacks
import com.pathways.rules.AttributeRoll
import com.pathways.rules.BurnKind
import com.pathways.rules.CommonModifiers
import com.pathways.rules.Conditions
import com.pathways.rules.Context
import com.pathways.rules.DarknessCause
import com.pathways.rules.DefenseBoost
import com.pathways.rules.DefenseRefusal
import com.pathways.rules.DegreeOfSuccess
import com.pathways.rules.EffortBurn
import com.pathways.rules.Equipment
import com.pathways.rules.FrightCheck
import com.pathways.rules.FrightKind
import com.pathways.rules.ModifierResolver
import com.pathways.rules.NaturalRecovery
import com.pathways.rules.Pools
import com.pathways.rules.Premades
import com.pathways.rules.Ranged
import com.pathways.rules.Rest
import com.pathways.rules.RollKind
import com.pathways.rules.SkillRoll
import com.pathways.rules.SuccessDegree
import com.pathways.rules.SuccessRoll
import com.pathways.rules.VitalCheck
import com.pathways.rules.VitalRolls
import com.pathways.rules.content.ContentLoader
import kotlin.random.Random

private val KEY_A = SkillKey("Stealth", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)
private val KEY_B = SkillKey("Ritualistic Magic", null, ControllingAttribute.Spi, Difficulty.HARD)

/** A modifier list drawn from every effect, layer, group and condition. */
private fun randomMods(r: Random, n: Int): List<Modifier> {
    val sources = listOf(
        ModifierSource.Potion("Fool", 9), ModifierSource.FromTrait("Fit", true),
        ModifierSource.FromTrait("Unfit", false), ModifierSource.Equipment("Lamp"),
        ModifierSource.Situational("Dark"), ModifierSource.StateThreshold("Reeling"),
    )
    val targets = listOf(ModTarget.Skill(KEY_A), ModTarget.Skill(KEY_A), ModTarget.AllRolls, ModTarget.Skill(KEY_B), ModTarget.Attribute(Attr.ST))
    val groups = listOf(null, null, null, StackGroup("g1", 2), StackGroup("g2", 0), StackGroup("g3", 5))
    val conditions = listOf(
        Condition.Always, Condition.Always, Condition.AtNight, Condition.InDarkness, Condition.AgainstLowerSequence(1),
        Condition.All(listOf(Condition.AtNight, Condition.InDarkness)), Condition.Any(listOf(Condition.AtNight, Condition.InDarkness)),
    )
    return List(n) { i ->
        val effect = when (r.nextInt(12)) {
            0, 1, 2, 3 -> ModEffect.Flat(r.nextInt(-6, 6))
            4, 5 -> ModEffect.Scale(r.nextInt(-2, 4), listOf(1, 2, 3, -2)[r.nextInt(4)], r.nextBoolean())
            6 -> ModEffect.SetTo(r.nextInt(-4, 4))
            7, 8 -> ModEffect.Cancel(r.nextInt(0, 5), SourceKind.entries[r.nextInt(SourceKind.entries.size)])
            9 -> ModEffect.Decay(r.nextInt(-5, 5), r.nextInt(-2, 3))
            10 -> ModEffect.Forbid
            else -> ModEffect.RequireCrit
        }
        val started = r.nextInt(4) == 0
        Modifier(
            id = ModifierId("m$i"),
            source = sources[r.nextInt(sources.size)],
            target = targets[r.nextInt(targets.size)],
            effect = effect,
            layer = Layer.entries[r.nextInt(Layer.entries.size)],
            group = groups[r.nextInt(groups.size)],
            supersedes = if (i > 0 && r.nextInt(6) == 0) ModifierId("m${r.nextInt(i)}") else null,
            duration = if (started) listOf(Duration.Turns(2), Duration.Minutes(1), Duration.Scene)[r.nextInt(3)] else Duration.Permanent,
            startTurn = if (started) r.nextInt(0, 3) else null,
            condition = conditions[r.nextInt(conditions.size)],
        )
    }
}

fun Vectors.rollVectors2() {
    val r = Random(6)
    val contexts = listOf(Context(), Context(isNight = true, turn = 2), Context(isDarkness = true, sequenceDelta = 2, turn = 70))
    for (i in 0 until 400) {
        val mods = randomMods(r, 1 + r.nextInt(8))
        val base = r.nextInt(-3, 18)
        val ctx = contexts[i % contexts.size]
        case("ModifierResolver.resolve", base, mods, ModTarget.Skill(KEY_A), ctx) {
            ModifierResolver.resolve(base, mods, ModTarget.Skill(KEY_A), ctx)
        }
        case("SkillRoll.targetFor", "Stealth", base, KEY_A, mods, ctx) { SkillRoll.targetFor("Stealth", base, KEY_A, mods, ctx) }
    }
    // Pinned shapes: a Scale chain, a Scale after a Cancel, a Cancel with no penalty to cancel.
    val pinned = listOf(
        listOf(scaled("a", 1, 2), scaled("b", 3, 2)),
        listOf(flat("p", -4, ModifierSource.Situational("x")), cancel("c", 6, SourceKind.SITUATIONAL), scaled("s", 1, 2)),
        listOf(cancel("c", 3, SourceKind.POTION), flat("p", -2, ModifierSource.Potion("Fool", 9))),
        listOf(flat("a", 3, ModifierSource.FromTrait("Fit", true)), flat("b", -5, ModifierSource.Situational("x")), cancel("c", 2, SourceKind.SITUATIONAL)),
    )
    for ((i, mods) in pinned.withIndex()) for (base in listOf(-4, 0, 7, 13)) {
        case("ModifierResolver.resolve", base, mods, ModTarget.Skill(KEY_A), Context()) {
            ModifierResolver.resolve(base, mods, ModTarget.Skill(KEY_A), Context())
        }
        case("SkillRoll.targetFor", "pin$i", base, KEY_A, mods, Context()) { SkillRoll.targetFor("pin$i", base, KEY_A, mods, Context()) }
    }
    for (target in listOf(-2, 6, 11, 16)) for (f in THROWS) {
        val t = SkillRoll.targetFor("x", target, KEY_A, listOf(ModifierResolverMods.crit, ModifierResolverMods.forbid).take(Math.floorMod(target, 3)))
        case("SkillRoll.rollAgainst", target, f) {
            scripted(f) { src -> SkillRoll.rollAgainst(t, src).let { listOf(it, it.effectiveOutcome, it.degree, it.lines()) } }
        }
    }
    for (margin in -12..12) {
        for (o in listOf(null) + com.pathways.model.Outcome.entries) case("DegreeOfSuccess.of", o, margin) { DegreeOfSuccess.of(o, margin) }
    }
    for (d in SuccessDegree.entries) case("DegreeOfSuccess.marginFor", d) { DegreeOfSuccess.marginFor(d) }
    checkVectors()
    combatOptionVectors()
}

private object ModifierResolverMods {
    val crit = Modifier(ModifierId("crit"), ModifierSource.Situational("crit"), ModTarget.AllRolls, ModEffect.RequireCrit, Layer.SITUATIONAL)
    val forbid = Modifier(ModifierId("no"), ModifierSource.Situational("no"), ModTarget.AllRolls, ModEffect.Forbid, Layer.SITUATIONAL)
}

private fun scaled(id: String, num: Int, den: Int) =
    Modifier(ModifierId(id), ModifierSource.Situational(id), ModTarget.Skill(KEY_A), ModEffect.Scale(num, den), Layer.SITUATIONAL)
private fun flat(id: String, n: Int, s: ModifierSource) =
    Modifier(ModifierId(id), s, ModTarget.Skill(KEY_A), ModEffect.Flat(n), Layer.EQUIPMENT)
private fun cancel(id: String, upTo: Int, of: SourceKind) =
    Modifier(ModifierId(id), ModifierSource.FromTrait("Shield", true), ModTarget.Skill(KEY_A), ModEffect.Cancel(upTo, of), Layer.COUNTERMEASURE)

private fun Vectors.checkVectors() {
    val hpGrid = listOf(10 to 10, 3 to 10, 0 to 10, -4 to 10, -12 to 10, -25 to 10, -51 to 10)
    val fpGrid = listOf(10 to 10, 3 to 10, 0 to 10, -9 to 10)
    val spiGrid = listOf(12 to 12, 3 to 12, 0 to 12)
    for ((h, hm) in hpGrid) for ((f, fm) in fpGrid) {
        val hp = Pools.hp(h, hm)
        val fp = Pools.fp(f, fm)
        for ((s, sm) in spiGrid) for (key in listOf(KEY_A, KEY_B)) for (cor in listOf(0, 5, 12)) {
            case("Conditions.forSkill", key, h, hm, f, fm, s, sm, cor) { Conditions.forSkill(key, hp, fp, Pools.spi(s, sm), cor) }
        }
        for (k in RollKind.entries) case("Conditions.penaltyFor", k, h, hm, f, fm) { Conditions.penaltyFor(k, hp, fp) }
        for (c in VitalCheck.entries) {
            case("VitalRolls.printed", c, h, hm) { VitalRolls.printed(c, hp) }
            case("VitalRolls.rowFor", c, h, hm) { VitalRolls.rowFor(c, hp) }
        }
        case("VitalRolls.due", h, hm) { VitalRolls.due(hp) }
        case("NaturalRecovery.isOffered", h, hm) { NaturalRecovery.isOffered(hp) }
    }
    val traitSets = listOf(
        emptyList(),
        listOf(Trait("Fit", 5, TraitKind.ADVANTAGE), Trait("Hard to Kill 2", 4, TraitKind.ADVANTAGE)),
        listOf(Trait("Fast Healer", 5, TraitKind.ADVANTAGE), Trait("Alertness 2", 10, TraitKind.ADVANTAGE), Trait("Combat Reflexes", 15, TraitKind.ADVANTAGE)),
        listOf(Trait("Unfit", 5, TraitKind.DISADVANTAGE), Trait("Hard to Subdue 1", 2, TraitKind.ADVANTAGE), Trait("Acute Vision 2", 4, TraitKind.ADVANTAGE)),
    )
    for (traits in traitSets) {
        for (c in VitalCheck.entries) {
            case("VitalRolls.bonusFor", c, traits) { VitalRolls.bonusFor(c, traits) }
            case("VitalRolls.targetFor", c, 11, traits, -1, -2, 1) { VitalRolls.targetFor(c, 11, traits, -1, -2, 1) }
        }
        for (s in listOf(true, false)) case("NaturalRecovery.dayLine", s, traits) { NaturalRecovery.dayLine(s, traits) }
        case("NaturalRecovery.targetFor", 12, traits, -1, 2, 1) { NaturalRecovery.targetFor(12, traits, -1, 2, 1) }
        for (k in AttributeRoll.Kind.entries) {
            case("AttributeRoll.clauses", k, traits) { AttributeRoll.clauses(k, traits) }
            for ((s, sm) in spiGrid) {
                case("AttributeRoll.targetFor", k, 11, traits, -2, -1, 1, s, sm) {
                    AttributeRoll.targetFor(k, 11, traits, -2, -1, 1, Pools.spi(s, sm))
                }
                case("AttributeRoll.partsFor", k, 11, traits, -2, -1, s, sm) {
                    AttributeRoll.partsFor(k, 11, traits, -2, -1, Pools.spi(s, sm))
                }
            }
        }
        for (stunned in listOf(true, false)) for (shocked in listOf(true, false)) {
            case("ActiveDefenses.everyDefence", traits, stunned, shocked, -1, 2) {
                ActiveDefenses.everyDefence(traits, stunned, shocked, -1, 2)
            }
        }
        val declaredSets = listOf(emptySet(), setOf(DefenseRefusal.SURPRISE), DefenseRefusal.entries.toSet(), setOf(DefenseRefusal.PINNED, DefenseRefusal.ALL_OUT_ATTACK))
        for (d in declaredSets) case("ActiveDefenses.state", d, traits) { ActiveDefenses.state(d, traits) }
        for (k in BurnKind.entries) case("EffortBurn.whenAllowed", k, traits) { EffortBurn.whenAllowed(k, traits) }
    }
    case("NaturalRecovery.rows") { NaturalRecovery.rows() }
    case("ActiveDefenses.forfeitingActions") { ActiveDefenses.forfeitingActions() }
    for (b in DefenseBoost.entries) {
        case("ActiveDefenses.fpCostOf", b) { ActiveDefenses.fpCostOf(b) }
        case("ActiveDefenses.printedBoost", b) { ActiveDefenses.printedBoost(b) }
    }
    case("ActiveDefenses.printed") {
        listOf(ActiveDefenses.printedAcrobaticDodge(), ActiveDefenses.printedDive(), ActiveDefenses.printedRetreat(), ActiveDefenses.printedStun())
    }
    case("ActiveDefenses.unappliedModifiers") { ActiveDefenses.unappliedModifiers() }
    for (o in listOf(null) + com.pathways.model.Outcome.entries) case("ActiveDefenses.acrobaticDodge", o) { ActiveDefenses.acrobaticDodge(o) }
    for (a in listOf(true, false)) for (b in listOf(true, false)) {
        case("ActiveDefenses.dive", a, b) { ActiveDefenses.dive(a, b) }
        for (row in ActiveDefenses.Retreat.entries) case("ActiveDefenses.retreat", a, b, row) { ActiveDefenses.retreat(a, b, row) }
    }
    for (c in listOf("SURPRISE", "PINNED", "nope")) case("ActiveDefenses.printedFor", c) { ActiveDefenses.printedFor(c) }
    for (k in BurnKind.entries) {
        case("EffortBurn.pool", k) { EffortBurn.pool(k) }
        for (p in listOf(-1, 0, 1, 3)) {
            case("EffortBurn.bonusFor", k, p) { EffortBurn.bonusFor(k, p) }
            case("EffortBurn.modifier", k, p) { EffortBurn.modifier(k, p) }
        }
    }
    for (sk in catalogueSkills()) {
        case("EffortBurn.kindFor", sk) { EffortBurn.kindFor(sk) }
        val eq = Equipment.referenceFor(sk)
        if (eq.isNotEmpty()) case("Equipment.referenceFor", sk) { eq }
    }
    for ((fp, spi) in listOf(0 to 0, 2 to 0, 0 to 3, 1 to 1)) case("EffortBurn.requireOnePool", fp, spi) { EffortBurn.requireOnePool(fp, spi); true }
    for (rest in Rest.rests()) for (cog in listOf(true, false)) {
        case("Rest.recovery", rest.name, cog, 11) { Rest.recovery(rest, cog, 11) }
    }
    case("Rest.named", "Nap of a year") { Rest.named("Nap of a year") }
    for (row in CommonModifiers.all()) {
        for (chosen in listOf(null, -10, -4, -1, 0, 1, 3, 8)) {
            case("CommonModifiers.valueFor", row.situation, chosen) { CommonModifiers.valueFor(row, chosen) }
        }
    }
    val offered = CommonModifiers.offered()
    case("CommonModifiers.modifiers", offered.map { it.situation }, emptyMap<String, Int>()) { CommonModifiers.modifiers(offered) }
    case("CommonModifiers.modifiers", listOf(offered[0].situation), mapOf("nope" to 1)) {
        CommonModifiers.modifiers(listOf(offered[0]), mapOf("nope" to 1))
    }
    for (v in listOf(-3, 0, 2)) case("CommonModifiers.gmRuling", v) { CommonModifiers.gmRuling(v) }
    frightVectors()
}

private fun Vectors.frightVectors() {
    for (will in listOf(8, 12)) for (mod in listOf(0, -3)) for (f in THROWS) for (g in listOf(THROWS[0], THROWS[8], THROWS[15])) {
        case("FrightCheck.rollAgainst", will + mod, f + g) { scripted(f + g) { FrightCheck.rollAgainst(will + mod, it) } }
    }
    for (roll in 2..40) case("FrightCheck.effectFor", roll) { FrightCheck.effectFor(roll) }
    val traits = listOf(Trait("Combat Reflexes", 15, TraitKind.ADVANTAGE))
    for ((i, e) in ContentLoader.frightEffects().withIndex()) for (k in FrightKind.entries) {
        case("FrightCheck.text", i, k) { FrightCheck.text(e, k) }
        case("FrightCheck.grants", i, k) { listOf(FrightCheck.grantsQuirk(e, k), FrightCheck.grantsDisadvantage(e, k)) }
        case("FrightCheck.recoveryFor", i, k) { FrightCheck.recoveryFor(e, k) }
        FrightCheck.recoveryFor(e, k)?.let { rec ->
            case("FrightCheck.recovery", i, k) {
                listOf(FrightCheck.recoveryBonus(rec, traits), FrightCheck.levelFor(rec, 11, 13), FrightCheck.takesWillClauses(rec))
            }
        }
        for (rr in listOf(null, SuccessRoll.evaluate(8, 10), SuccessRoll.evaluate(14, 10))) {
            case("FrightCheck.leavesStunned", i, k, rr) { FrightCheck.leavesStunned(e, k, rr) }
        }
        FrightCheck.durationFor(e, k)?.let { d ->
            case("FrightCheck.durationLine", i, k) { listOf(FrightCheck.secondsFor(d, 12, 11, 13), FrightCheck.durationLine(d, 12, 11, 13)) }
        }
    }
    case("FrightCheck.triggers") { FrightCheck.triggers() }
}

private fun Vectors.combatOptionVectors() {
    val unarmed = listOf(null) + ContentLoader.unarmedAttacks()
    val plans = listOf(
        AttackPlan(),
        AttackPlan(maneuver = AttackManeuver.DETERMINED, isRanged = true),
        AttackPlan(rapidStrikes = 1, deceptive = 2),
        AttackPlan(maneuver = AttackManeuver.CHARGE, heroicCharge = true, giantStep = true),
        AttackPlan(strongBlow = true, pinOnGrappledProne = true),
        AttackPlan(maneuver = AttackManeuver.DOUBLE, rapidStrikes = 2),
        AttackPlan(maneuver = AttackManeuver.STRONG, strongBlow = true),
        AttackPlan(isRanged = true, deceptive = 1),
        AttackPlan(rapidStrikes = -1),
    )
    for (plan in plans) {
        case("Attacks.attackCount", plan) { Attacks.attackCount(plan) }
        case("Attacks.fpCost", plan) { Attacks.fpCost(plan) }
        for (b in listOf(-1, 0, 2)) case("Attacks.damageBonus", plan, b) { Attacks.damageBonus(plan, b) }
        for (lv in listOf(3, 9, 14)) {
            case("Attacks.atLevel", plan, lv) { Attacks.atLevel(plan, lv) }
            for ((i, u) in unarmed.withIndex()) case("Attacks.modifiers", plan, lv, i - 1) { Attacks.modifiers(plan, lv, u) }
        }
        for (rng in listOf(true, false)) case("Attacks.atRange", plan, rng) { Attacks.atRange(plan, rng) }
        for ((i, u) in unarmed.withIndex()) case("Attacks.withAttack", plan, i - 1) { Attacks.withAttack(plan, u) }
        for (m in AttackManeuver.entries) case("Attacks.withManeuver", plan, m) { Attacks.withManeuver(plan, m) }
        for (d in listOf(true, false)) for (f in listOf(true, false)) case("Attacks.offersStrongBlow", plan, d, f) { Attacks.offersStrongBlow(plan, d, f) }
    }
    for (lv in -2..20) case("Attacks.maxDeceptive", lv) { Attacks.maxDeceptive(lv) }
    for (m in listOf(0, 1, 2, 3, 5, 6, 10, 20, 30, 50, 100, 200, 300)) case("Ranged.bandFor", m) { Ranged.bandFor(m) }
    case("Ranged.bandFor", -1) { Ranged.bandFor(-1) }
    val conds = Ranged.conditions()
    for (c in conds) {
        case("Ranged.alternativesTo", c.condition) { Ranged.alternativesTo(c.condition).toList() }
        for (acc in listOf(null, 2)) for (cause in listOf(null) + DarknessCause.entries) for (red in listOf(0, 2)) {
            val gm = c.worstCase?.let { mapOf(c.condition to it) } ?: emptyMap()
            case("Ranged.modifiers", 1, listOf(c.condition), gm, acc, false, cause, red) {
                Ranged.modifiers(Ranged.bands()[1], listOf(c), gm, acc, false, cause, red)
            }
        }
    }
    case("Ranged.modifiers", null, conds.filter { it.weaponAcc }.map { it.condition }, emptyMap<String, Int>(), 2, false, null, 0) {
        Ranged.modifiers(null, conds.filter { it.weaponAcc }, emptyMap(), 2, false, null, 0)
    }
    case("Ranged.modifiers", null, conds.filter { it.weaponAcc }.take(1).map { it.condition }, emptyMap<String, Int>(), 2, true, null, 0) {
        Ranged.modifiers(null, conds.filter { it.weaponAcc }.take(1), emptyMap(), 2, true, null, 0)
    }
    for (c in listOf(true, false)) case("Ranged.offeredConditions", c) { Ranged.offeredConditions(c).map { it.condition } }
    case("Ranged.meleeModifiers", Ranged.meleeConditions().map { it.condition }) { Ranged.meleeModifiers(Ranged.meleeConditions()) }
    case("Ranged.printedAim") { Ranged.printedAim() }
}
