package com.pathways.rules

import com.pathways.model.BeyonderState
import com.pathways.model.BoonState
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The catalogue rows a boon block prints (6hn): granted as a potion's are,
 * through the same merge. 6hm left them as prose the GM applies.
 */
class BoonTraitsTest {

    private fun held(boon: BoonState?, state: BeyonderState? = null, bought: List<Trait> = emptyList()) =
        TraitGrants.withGrantedTraits(bought, state, boon)

    /**
     * Both ways, by `TraitGrantsTest`'s own method: every catalogue name a
     * boon block prints whole, longest first, is granted or refused.
     */
    @Test
    fun `every catalogue row a boon prints is granted or refused`() {
        val catalogue = (ContentLoader.advantages() + ContentLoader.disadvantages()).associateBy { it.name }
        val names = catalogue.keys.sortedByDescending { it.length }
        val printed = mutableSetOf<Pair<Pair<String, Int>, String>>()
        for (block in NonStandardPathways.all()) {
            for (text in listOf(block.advantages, block.drawbacks)) {
                val taken = mutableListOf<IntRange>()
                for (name in names) {
                    val pattern = Regex("(?<![A-Za-z])${Regex.escape(name)}(?![A-Za-z])")
                    for (m in pattern.findAll(text)) {
                        if (taken.any { m.range.first in it }) continue
                        taken += m.range
                        printed += (NonStandardPathways.patronOf(block.pathway) to block.sequence) to name
                    }
                }
            }
        }
        val granted = TraitGrants.boonTraitTable()
            .flatMap { (key, rows) -> rows.map { key to TraitNames.catalogueName(it.name) } }
            .toSet()
        val refused = TraitGrants.refusedBoonGrants().keys
        assertEquals(printed, granted + refused)
        assertTrue((granted intersect refused).isEmpty())
        assertTrue(granted.isNotEmpty())
        assertTrue(TraitGrants.refusedBoonGrants().values.all { it.isNotBlank() })
        // Every granted name is a catalogue row, so nothing here is invented.
        assertTrue(granted.all { it.second in catalogue }, granted.toString())
    }

    @Test
    fun `a boon's row is held free from the rung that prints it`() {
        // Patriarch 8, the Sex Addict: "Fit +1 to all HT rolls".
        assertTrue(held(BoonState("Patriarch", 9)).none { it.name == "Fit" })
        assertEquals(listOf(Trait("Fit", 0, TraitKind.ADVANTAGE)), held(BoonState("Patriarch", 8)).filter { it.name == "Fit" })
        // ...and reaches the roll the bought row reaches.
        assertEquals(1, NaturalRecovery.bonusFor(held(BoonState("Patriarch", 8))))
        assertEquals(0, NaturalRecovery.bonusFor(held(BoonState("Patriarch", 9))))
    }

    @Test
    fun `a boon's Charisma adds to a potion's and to a bought level`() {
        // The Broker's "Charisma +2", by 6fm's rule: levels add, capped at 4.
        val broker = BoonState("Chaos Mist", 9)
        fun charisma(traits: List<Trait>) = TraitNames.levelOf(traits, TraitEffects.CHARISMA)
        assertEquals(2, charisma(held(broker)))
        assertEquals(3, charisma(held(broker, BeyonderState("Error", 8))))
        assertEquals(4, charisma(held(broker, BeyonderState("Demoness", 6))))
        assertEquals(3, charisma(held(broker, bought = listOf(Trait("Charisma 1", 5, TraitKind.ADVANTAGE)))))
    }

    @Test
    fun `a row held from both a potion and a boon is held once`() {
        // Demoness 9 and Sublunary Eye 8 both print Acute Vision +2.
        val both = held(BoonState("Sublunary Eye", 8), BeyonderState("Demoness", 9))
        assertEquals(1, both.count { it.name == "Acute Vision" })
    }
}
