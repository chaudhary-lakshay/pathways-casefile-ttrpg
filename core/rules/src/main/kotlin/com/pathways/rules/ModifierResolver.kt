package com.pathways.rules

import com.pathways.model.*

data class Context(
    val isNight: Boolean = false,
    val isDarkness: Boolean = false,
    val sequenceDelta: Int = 0,
    val turn: Int = 0,
)

data class Resolution(
    val value: Int,
    val forbidden: Boolean = false,
    val requiresCritical: Boolean = false,
    val applied: List<ModifierId> = emptyList(),
)

object ModifierResolver {

    fun resolve(base: Int, mods: List<Modifier>, target: ModTarget, ctx: Context): Resolution {
        // Only modifiers that are themselves active (right target, condition
        // holds) can supersede anything - an inactive or off-target
        // "superseder" must not delete the modifier it names.
        val activeForTarget = mods.filter { it.target == target && matches(it.condition, ctx) }
        val superseded = activeForTarget.mapNotNull { it.supersedes }.toSet()
        val live = activeForTarget.filter { it.id !in superseded }

        var value = base
        var forbidden = false
        var requiresCritical = false

        for (layer in Layer.entries) {
            val inLayer = live.filter { it.layer == layer }
            if (inLayer.isEmpty()) continue

            // Additive contributions, clamped per stack group.
            inLayer.filter { it.effect is ModEffect.Flat || it.effect is ModEffect.SetTo }
                .groupBy { it.group }
                .forEach { (group, ms) ->
                    val raw = ms.sumOf {
                        when (val e = it.effect) {
                            is ModEffect.Flat -> e.n
                            is ModEffect.SetTo -> e.n
                            else -> 0
                        }
                    }
                    value += if (group == null) raw else raw.coerceIn(-group.cap, group.cap)
                }

            inLayer.forEach { m ->
                when (val e = m.effect) {
                    is ModEffect.Scale -> value = Math.floorDiv(value * e.num, e.den)
                    is ModEffect.Decay -> {
                        val remaining = e.from + e.perTurn * ctx.turn
                        value += if (e.from < 0) minOf(remaining, 0) else maxOf(remaining, 0)
                    }
                    is ModEffect.Cancel -> {
                        val penalty = live.filter { it.source.kind == e.of }
                            .sumOf { (it.effect as? ModEffect.Flat)?.n?.coerceAtMost(0) ?: 0 }
                        value += minOf(e.upTo, -penalty)
                    }
                    ModEffect.Forbid -> forbidden = true
                    ModEffect.RequireCrit -> requiresCritical = true
                    else -> Unit
                }
            }
        }

        return Resolution(value, forbidden, requiresCritical, live.map { it.id })
    }

    private fun matches(c: Condition, ctx: Context): Boolean = when (c) {
        Condition.Always -> true
        Condition.AtNight -> ctx.isNight
        Condition.InDarkness -> ctx.isDarkness
        is Condition.AgainstLowerSequence -> ctx.sequenceDelta >= c.delta
        is Condition.All -> c.of.all { matches(it, ctx) }
        is Condition.Any -> c.of.any { matches(it, ctx) }
    }
}
