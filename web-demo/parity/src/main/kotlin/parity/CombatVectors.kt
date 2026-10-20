package parity

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Outcome
import com.pathways.model.RollResult
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.Concentration
import com.pathways.rules.Corruption
import com.pathways.rules.Damage
import com.pathways.rules.Derive
import com.pathways.rules.Divination
import com.pathways.rules.Encumbrance
import com.pathways.rules.Facing
import com.pathways.rules.ParryBlock
import com.pathways.rules.ParryClass
import com.pathways.rules.Pool
import com.pathways.rules.Pools
import com.pathways.rules.Premades
import com.pathways.rules.RandomSource
import com.pathways.rules.RollKind
import com.pathways.rules.ScoreFloors
import com.pathways.rules.SecondaryCharacteristics
import com.pathways.rules.Shields
import com.pathways.rules.Shock
import com.pathways.rules.SkillDefaults
import com.pathways.rules.StunRecovery
import com.pathways.rules.SuccessRoll
import com.pathways.rules.UnarmedStyle
import com.pathways.rules.Weapons
import com.pathways.rules.Wounds
import com.pathways.rules.content.ContentLoader

/** What a scripted roll returned, and every throw it reported. */
internal fun scripted(faces: List<Int>, call: (RandomSource) -> Any?): Map<String, Any?> {
    val dice = ScriptedDice(faces)
    val out = call(dice)
    return mapOf("out" to out, "thrown" to dice.thrown)
}

/** Every 3d6 total from 3 to 18 as one scripted throw. */
internal val THROWS: List<List<Int>> = (3..18).map { t ->
    val a = minOf(6, t - 2)
    val b = minOf(6, t - a - 1)
    listOf(a, b, t - a - b)
}

internal fun results(): List<RollResult> = (3..18).flatMap { total ->
    listOf(4, 9, 12, 16, 20).map { SuccessRoll.evaluate(total, it) }
}

fun Vectors.combatVectors() {
    poolVectors()
    woundVectors()
    weaponVectors()
    rollVectors()
}

private fun Vectors.poolVectors() {
    for (max in listOf(1, 3, 10, 13)) for (current in -6 * max..max + 2) {
        case("Pools.hp", current, max) { Pools.hp(current, max) }
        case("Pools.fp", current, max) { Pools.fp(current, max) }
        case("Pools.spi", current, max) { Pools.spi(current, max) }
        case("Pools.halvedForFatigue", current, max) { Pools.halvedForFatigue(11, Pools.fp(current, max)) }
    }
    for (p in Pool.entries) case("Pools.thresholds", p) { Pools.thresholds(p) }
    for (will in listOf(5, 10, 14)) for (traits in listOf(emptyList(), listOf(Trait("Spiritual Fortitude", 5, TraitKind.ADVANTAGE)))) {
        val max = Corruption.maxFor(will, traits)
        case("Corruption.maxFor", will, traits) { max }
        for (cor in listOf(-2, 0, 3, max - 1, max, max + 4)) {
            case("Corruption.clamp", cor, max) { Corruption.clamp(cor, max) }
            case("Corruption.isLostAt", cor, max) { Corruption.isLostAt(cor, max) }
            for (d in listOf(-3, 0, 1, 5)) case("Corruption.isLostAfter", cor, d, max) { Corruption.isLostAfter(cor, d, max) }
        }
    }
    for (cor in -1..25) {
        case("Corruption.ritualPenalty", cor) { Corruption.ritualPenalty(cor) }
        case("Corruption.ritualThresholdsReached", cor) { Corruption.ritualThresholdsReached(cor) }
    }
    for (spi in -2..12) {
        case("Corruption.drainedUse", spi) { Corruption.drainedUse(spi) }
        for (price in listOf(0, 1, 3, 8)) case("Corruption.soulDebt", spi, price) { Corruption.soulDebt(spi, price) }
    }
    case("Corruption.gained") { Corruption.gained() }
    for (sheet in Premades.all()) {
        val c = sheet.toCharacter()
        case("Corruption.maxForCharacter", sheet.name) { Corruption.maxFor(c) }
        case("ScoreFloors.shortfall", sheet.name) {
            ScoreFloors.shortfall(SecondaryCharacteristics.of(c.attributes, c.secondaryDelta), c.traits)
        }
        case("Encumbrance.basicLift", sheet.name) { Encumbrance.basicLift(c.attributes.st, c.traits) }
    }
    for (c in listOf(-5, 0, 1, 3)) case("ScoreFloors.holdsCeiling", c) { ScoreFloors.holdsCeiling(c) }
    for (lift in listOf(20.0, 26.45, 39.2)) for (carried in listOf(0.0, 20.0, 40.0, 60.0, 80.0, 120.0, 200.0, 400.0)) {
        case("Encumbrance.levelFor", carried, lift) { Encumbrance.levelFor(carried, lift) }
    }
}

private fun Vectors.woundVectors() {
    val types = Wounds.damageTypes().map { it.type } + Wounds.knownAbbreviations() + listOf("zap")
    for (t in types) {
        case("Wounds.knowsType", t) { Wounds.knowsType(t) }
        case("Wounds.typeMultiplier", t) { Wounds.typeMultiplier(t) }
    }
    val locations = listOf(null) + Wounds.locations().map { it.target }
    for (loc in locations) {
        val l = loc?.let { Wounds.locationNamed(it) }
        if (l != null) {
            case("Wounds.modifier", loc) { Wounds.modifier(l) }
            for (maxHp in listOf(5, 10, 13)) for (dmg in listOf(0, 1, 3, 5, 7, 11, 20)) {
                case("Wounds.effectsAt", loc, dmg, maxHp) { Wounds.effectsAt(l, dmg, maxHp) }
            }
            for (o in l.thresholds.map { it.outcome } + listOf("nope")) for (maxHp in listOf(5, 13)) {
                case("Wounds.thresholdFor", loc, o, maxHp) { Wounds.thresholdFor(l, o, maxHp) }
            }
        }
        for (t in types) {
            case("Wounds.multiplierFor", loc, t) { Wounds.multiplierFor(l, t) }
            for (p in listOf(-1, 0, 1, 3, 7)) {
                case("Wounds.injuryFor", p, loc, t) { Wounds.injuryFor(p, l, t) }
                for (pool in Pool.entries) {
                    case("Wounds.injuryTo", pool, p, loc, t) { Wounds.injuryTo(pool, p, l, t) }
                }
            }
        }
        for (pool in Pool.entries) case("Wounds.injuryTo", pool, 5, loc, null) {
            Wounds.injuryTo(pool, 5, l, null)
        }
    }
    case("Wounds.locationNamed", "Nowhere") { Wounds.locationNamed("Nowhere") }
    for (p in listOf(-1, 0, 1, 4)) case("Wounds.spiritNextRoll", p) { Wounds.spiritNextRoll(p) }
    for (hp in listOf(1, 2, 5, 10, 13, 20)) {
        case("Wounds.majorWoundThreshold", hp) { Wounds.majorWoundThreshold(hp) }
        for (d in 0..12) case("Wounds.isMajorWound", d, hp) { Wounds.isMajorWound(d, hp) }
    }
    for (r in results()) case("Wounds.majorWoundFor", r) { Wounds.majorWoundFor(r) }
    for (m in listOf(0.5, 1.0, 1.5, 2.0, 3.0, 0.25, 1.25)) case("Wounds.render", m) { Wounds.render(m) }
    case("Wounds.cripplingRule") { Wounds.cripplingRule() }
    for (row in Wounds.majorWounds()) {
        case("StunRecovery.stunsOn", row) { StunRecovery.stunsOn(row) }
        case("Shock.causedBy", row) { Shock.causedBy(row) }
        for (r in listOf(null) + results().filterIndexed { i, _ -> i % 7 == 0 }) {
            case("StunRecovery.leavesStunned", row, r) { StunRecovery.leavesStunned(row, r) }
        }
    }
    for (r in results()) case("StunRecovery.stillStunned", r) { StunRecovery.stillStunned(r) }
    case("StunRecovery.printed") { StunRecovery.printed() }
    case("Shock.printed") { Shock.printed() }
    val traitSets = listOf(
        emptyList(),
        listOf(Trait("Combat Reflexes", 15, TraitKind.ADVANTAGE)),
        listOf(Trait("High Pain Threshold", 10, TraitKind.ADVANTAGE)),
        listOf(Trait("Hard to Subdue 2", 4, TraitKind.ADVANTAGE), Trait("Fit", 5, TraitKind.ADVANTAGE)),
    )
    for (traits in traitSets) {
        case("StunRecovery.bonusFor", traits) { StunRecovery.bonusFor(traits) }
        case("StunRecovery.targetFor", 11, traits, -2, -3, -1) { StunRecovery.targetFor(11, traits, -2, -3, -1) }
        case("Shock.ignoredBy", traits) { Shock.ignoredBy(traits) }
        for (shocked in listOf(true, false)) {
            case("Shock.modifiers", shocked, traits) { Shock.modifiers(shocked, traits) }
            for (k in RollKind.entries) case("Shock.penaltyFor", k, shocked, traits) { Shock.penaltyFor(k, shocked, traits) }
        }
    }
    for (k in RollKind.entries) case("Shock.appliesTo", k) { Shock.appliesTo(k) }
}

private fun Vectors.weaponVectors() {
    for (st in 1..30) {
        case("Damage.thrust", st) { Damage.thrust(st) }
        case("Damage.swing", st) { Damage.swing(st) }
    }
    val unarmed = listOf("Brawling", "Boxing", "Wrestling", "Karate", "DX", "Shield")
    for (s in unarmed) for (untrained in listOf(false, true)) for (bonus in listOf(0, 1)) {
        case("Damage.attacksFor", s, untrained, bonus) { Damage.attacksFor(s, untrained, bonus) }
        case("Shields.bashesFor", s, untrained, bonus) { Shields.bashesFor(s, untrained, bonus) }
    }
    for (a in ContentLoader.unarmedAttacks()) for (st in listOf(5, 10, 14)) for (bonus in listOf(0, 2)) {
        case("Damage.forAttack", a, st, bonus) { Damage.forAttack(a, st, bonus) }
    }
    for (dx in listOf(8, 10, 14)) for (lv in listOf(8, 10, 12, 14, 18, 22)) {
        case("Damage.boxingBonus", dx, lv) { Damage.boxingBonus(dx, lv) }
    }
    for (w in Weapons.all()) {
        case("Weapons.isRanged", w.name) { Weapons.isRanged(w) }
        case("Weapons.canParry", w.name) { Weapons.canParry(w) }
        for (lv in listOf(6, 11, 14)) case("Weapons.parryFor", w.name, lv) { Weapons.parryFor(w, lv) }
        for ((i, mode) in w.damage.withIndex()) for (st in listOf(6, 10, 15)) {
            case("Weapons.damageFor", w.name, i, st, 1) { Weapons.damageFor(w, mode, st, 1) }
        }
        for (mode in w.damage) case("Weapons.hasWoundingMultiplier", mode) { Weapons.hasWoundingMultiplier(mode) }
    }
    case("Weapons.named", "Nope") { Weapons.named("Nope") }
    for (sk in catalogueSkills()) {
        val found = Weapons.forSkill(sk)
        if (found.isNotEmpty()) case("Weapons.forSkill", sk) { found.map { it.name } }
    }
    for (sheet in Premades.all()) {
        val c = sheet.toCharacter()
        val levels = Derive.of(c).skillLevels
        case("SkillDefaults.offeredTo", sheet.name) { SkillDefaults.offeredTo(c) }
        case("SkillDefaults.ownedAttacks", sheet.name) { SkillDefaults.ownedAttacks(levels) }
        for (w in Weapons.all()) {
            case("Weapons.levelFor", sheet.name, w.name) { Weapons.levelFor(w, levels) }
            for (m in listOf(null) + SkillDefaults.WeaponMastery.entries) for (warm in listOf(false, true)) {
                case("SkillDefaults.wieldedAt", sheet.name, w.name, m, warm) {
                    SkillDefaults.wieldedAt(w, levels, m, c.attributes.dx, warm)
                }
            }
        }
        val untrained = SkillDefaults.offeredTo(c)
        case("SkillDefaults.attacks", sheet.name) { SkillDefaults.attacks(untrained, Weapons.all(), levels) }
    }
    for (m in listOf(null) + SkillDefaults.WeaponMastery.entries) for (inHand in listOf(true, false)) {
        for (ranged in listOf(true, false)) for (warm in listOf(true, false)) for (lv in listOf(8, 12, 15)) {
            case("SkillDefaults.mastered", lv, inHand, m, 12, ranged, warm) {
                SkillDefaults.mastered(lv, inHand, m, 12, ranged, warm)
            }
        }
    }
    for (p in ContentLoader.pathways().map { it.pathway }.distinct()) for (s in 0..9) {
        case("SkillDefaults.masteryFor", p, s) { SkillDefaults.masteryFor(p, s) }
        case("SkillDefaults.weaponNote", p, s) { SkillDefaults.weaponNote(p, s) }
        case("SkillDefaults.toolNote", p, s) { SkillDefaults.toolNote(p, s) }
        case("SkillDefaults.weaponLinesFor", p, s) { SkillDefaults.weaponLinesFor(p, s) }
    }
    for (shield in Shields.all()) for (ranged in listOf(true, false)) {
        case("Shields.blockPenalty", shield.type, ranged) { Shields.blockPenalty(shield, ranged) }
        for (lv in listOf(8, 12, 15)) case("Shields.blockFor", shield.type, lv, ranged) { Shields.blockFor(shield, lv, ranged) }
        case("Shields.withBonus", 9, shield.type) { Shields.withBonus(9, shield) }
    }
    case("Shields.withBonus", 9, null) { Shields.withBonus(9, null) }
    for (f in Facing.entries) case("Shields.coverageAt", f) { Shields.coverageAt(f) }
    case("Shields.rules") { Shields.rules() }
    for (style in UnarmedStyle.entries) {
        case("ParryBlock.versusWeaponPenalty", style) { ParryBlock.versusWeaponPenalty(style) }
        case("ParryBlock.canParryRanged", style) { ParryBlock.canParryRanged(style) }
        for (lv in listOf(-3, 7, 10, 13)) case("ParryBlock.unarmedParry", style, lv) { ParryBlock.unarmedParry(style, lv) }
    }
    for (dx in listOf(8, 12)) for (b in listOf(null, 6, 10, 14)) {
        case("ParryBlock.bareHandParry", dx, b) { ParryBlock.bareHandParry(dx, b) }
    }
    for (lv in listOf(-3, 9, 12)) for (db in listOf(0, 1, 3)) case("ParryBlock.blockFor", lv, db) { ParryBlock.blockFor(lv, db) }
    for (cls in ParryClass.entries) for (n in 0..4) case("ParryBlock.parryPenalty", n, cls) { ParryBlock.parryPenalty(n, cls) }
}

private fun Vectors.rollVectors() {
    for (target in -2..22) {
        case("SuccessRoll.printed", target) { SuccessRoll.printed(target) }
        for (f in THROWS) case("SuccessRoll.against", target, f) { scripted(f) { SuccessRoll.against(target, it) } }
    }
    for (will in listOf(9, 12)) for (shock in listOf(0, -2)) for (hurt in listOf(0, -1)) for (ft in listOf(0, 2)) {
        case("Concentration.targetFor", will, shock, hurt, ft) { Concentration.targetFor(will, shock, hurt, ft) }
    }
    for (target in listOf(6, 11)) for (dist in listOf(true, false)) for (f in THROWS) {
        case("Concentration.rollAgainst", target, dist, f) { scripted(f) { Concentration.rollAgainst(target, dist, it) } }
    }
    case("Concentration.printed") { Concentration.printed() }
    for (own in listOf(null, 9, 14)) for (spi in listOf(0, 6, 12)) for (b in listOf(0, 1)) {
        val chosen = Divination.divinationLevelFor(own, spi, b)
        case("Divination.divinationLevelFor", own, spi, b) { chosen }
        case("Divination.sourceLine", own, spi, b) { Divination.sourceLine(chosen) }
        case("Divination.keyFor", own, spi, b) { Divination.keyFor(chosen) }
    }
    for (d in Divination.difficulties()) for (skill in listOf(8, 12, 16)) for (extras in listOf(0, -3)) {
        for (burned in listOf(0, 2)) {
            case("Divination.targetFor", skill, d.band, extras, burned) { Divination.targetFor(skill, d, extras, burned) }
        }
    }
    for (r in results()) for (demoted in listOf(false, true)) {
        case("Divination.clarityFor", r, demoted) { Divination.clarityFor(r, demoted) }
    }
    for (target in listOf(5, 11, 15)) for (crit in listOf(false, true)) for (f in THROWS) {
        case("Divination.rollAgainst", target, crit, f) { scripted(f) { Divination.rollAgainst(target, it, crit) } }
    }
    val rows = Divination.modifiers()
    for (i in rows.indices) {
        val gm = rows[i].let { r -> if (r.modifier == null) mapOf(r.source to -2) else emptyMap() }
        for (repeats in 0..2) case("Divination.modifierTotal", listOf(i), gm, repeats) {
            Divination.modifierTotal(listOf(rows[i]), gm, repeats)
        }
    }
    case("Divination.modifierTotal", rows.indices.toList(), emptyMap<String, Int>(), 0) {
        Divination.modifierTotal(rows, emptyMap(), 0)
    }
    for (m in Divination.methods()) for (burned in listOf(0, 1, 3)) {
        case("Divination.spiFor", m.method, null, burned) { Divination.spiFor(m, burned = burned) }
        case("Divination.spiFor", m.method, m.spi + 1, burned) { Divination.spiFor(m, m.spi + 1, burned) }
    }
}
