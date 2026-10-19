package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What a walked potion chain grants ([TraitGrants]). */
class TraitGrantsTest {
    private fun advantage(name: String) = Trait(name, 5, TraitKind.ADVANTAGE)

    private val COMPULSION = "Compulsion (pathway)"

    /**
     * Hanged Man 9 prints "Paranoia, Hallucinations, or Compulsion (Hanged
     * Man; -10 pts) Choose one of these three drawbacks" (6he). The three are
     * the catalogue's rows at the block's price: Paranoia and Hallucinations
     * cost 10, and Compulsion (pathway) prints 5-15.
     */
    @Test
    fun `Hanged Man 9's three drawbacks are the block's own, at its price`() {
        val block = ContentLoader.pathways().single { it.pathway == "Hanged Man" && it.sequence == 9 }
        assertTrue("Choose one of these three drawbacks" in block.drawbacks)
        for (printed in listOf("Paranoia", "Hallucinations", "Compulsion (Hanged Man; -10 pts)")) {
            assertTrue(printed in block.drawbacks, printed)
        }
        val choices = listOf("Paranoia", "Hallucinations", COMPULSION)
        assertEquals(mapOf(("Hanged Man" to 9) to choices), TraitGrants.drawbackChoiceTable())
        val catalogue = ContentLoader.disadvantages().associateBy { it.name }
        for (name in choices) {
            val row = catalogue.getValue(name)
            assertTrue(row.cost == 10 || (row.costMin!! <= 10 && 10 <= row.costMax!!), name)
        }
    }

    @Test
    fun `a chosen drawback is granted free down the chain, and nothing else is`() {
        val eight = com.pathways.model.BeyonderState("Hanged Man", 8, drawback = "Paranoia")
        assertEquals(
            listOf(Trait("Paranoia", 0, TraitKind.DISADVANTAGE)),
            TraitGrants.grantedTraits(eight).filter { it.kind == TraitKind.DISADVANTAGE },
        )
        assertEquals(listOf("Paranoia", "Hallucinations", COMPULSION), TraitGrants.drawbackChoices(eight))
        // Unchosen, none of the three is held.
        val nine = com.pathways.model.BeyonderState("Hanged Man", 9)
        assertTrue(TraitGrants.grantedTraits(nine).none { it.kind == TraitKind.DISADVANTAGE })
        // A name outside the three, from a hand-edited save, grants nothing.
        val stray = nine.copy(drawback = "Bloodlust")
        assertTrue(TraitGrants.grantedTraits(stray).none { it.name == "Bloodlust" })
        // Another pathway has no choice to make.
        assertTrue(TraitGrants.drawbackChoices(com.pathways.model.BeyonderState("Fool", 9)).isEmpty())
    }

    /**
     * The potion blocks that grant a sense (6fi), against every block's own
     * prose in both directions. The names come from the PROSE -- any
     * capitalised row ending in a sense word and followed by "+N" -- never
     * from the table, which is how the first census missed Incisive Vision
     * and Acute Smell (the design review). The whole name is captured, so
     * "Greater Acute Vision" is never also read as an Acute Vision.
     */
    @Test
    fun `the granted-sense table is every block that prints one`() {
        val row = Regex("((?:[A-Z][a-z]+ (?:& )?)*(?:Vision|Hearing|Smell|Touch)) \\+\\d")
        val printed = ContentLoader.pathways().mapNotNull { block ->
            val found = row.findAll(block.advantages).map { it.groupValues[1] }.toSet()
            if (found.isEmpty()) null else (block.pathway to block.sequence) to found
        }.toMap()
        assertEquals(8, printed.size, printed.toString())
        // The senses in the one granted-row table (6fk folded 6fi's into it).
        val senseName = Regex(".*(Vision|Hearing|Smell|Touch)")
        val table = TraitGrants.grantedTraitTable()
            .mapValues { (_, rows) -> rows.map { it.name }.filter { senseName.matches(it) }.toSet() }
            .filterValues { it.isNotEmpty() }
        assertEquals(printed, table)
        // Every row no catalogue prints says what its OWN block prints
        // (the diff review's LOW: Greater Acute Vision had no such check).
        val search = SkillKey("Search", null, ControllingAttribute.Per, Difficulty.AVERAGE)
        val catalogued = ContentLoader.advantages().map { it.name }.toSet()
        for ((key, names) in table) {
            val (pathway, sequence) = key
            val block = ContentLoader.pathways().single { it.pathway == pathway && it.sequence == sequence }
            val held = TraitGrants.withGrantedTraits(emptyList(), com.pathways.model.BeyonderState(pathway, sequence))
            val clauses = TraitClauses.skillRollClauses(held, search)
            for (name in names.filter { it !in catalogued }) {
                val printed = clauses.single { it.trait == name }.printed
                assertTrue("$name $printed" in block.advantages, "$key: $name $printed")
            }
        }
    }

    /**
     * Every catalogue row a walkable block prints (6fk) is granted or refused
     * with a reason, both ways. Names are matched whole and longest first, so
     * "Healer" is never found inside "Fast Healer". "Greater Acute Vision"
     * is its own row: the lookbehind names that one word, because a general
     * "preceded by a capitalised word" would also hide a real row that
     * follows a previous row's last word (the diff review's LOW).
     */
    @Test
    fun `every catalogue row a potion prints is granted or refused`() {
        val catalogue = (ContentLoader.advantages() + ContentLoader.disadvantages()).associateBy { it.name }
        val names = catalogue.keys.sortedByDescending { it.length }
        val printed = mutableSetOf<Pair<Pair<String, Int>, String>>()
        for (block in ContentLoader.pathways()) {
            for (text in listOf(block.advantages, block.drawbacks)) {
                val taken = mutableListOf<IntRange>()
                for (name in names) {
                    val pattern = Regex("(?<![A-Za-z])(?<!Greater )${Regex.escape(name)}(?![A-Za-z])")
                    for (m in pattern.findAll(text)) {
                        if (taken.any { m.range.first in it }) continue
                        taken += m.range
                        printed += (block.pathway to block.sequence) to name
                    }
                }
            }
        }
        val granted = TraitGrants.grantedTraitTable()
            .flatMap { (key, rows) ->
                rows.map { TraitNames.catalogueName(it.name) }.filter { it in catalogue }.map { key to it }
            }
            .toSet()
        val refused = TraitGrants.refusedGrants().keys
        // A chosen drawback is granted when chosen (6he). The block prints
        // the third as "Compulsion (Hanged Man; -10 pts)", which names no
        // row whole, so this census cannot see it; the choice test pins it.
        val chosen = TraitGrants.drawbackChoiceTable()
            .flatMap { (key, names) -> names.map { key to it } }
            .toSet() - (("Hanged Man" to 9) to COMPULSION)
        assertEquals(printed, granted + refused + chosen)
        assertTrue((granted intersect refused).isEmpty(), (granted intersect refused).toString())
        // Every granted catalogue row is unlevelled but Charisma, whose levels
        // the merge adds (6fm): an unlevelled row is kept once by name, which
        // would let a bought level hide a granted one.
        for ((_, name) in granted) {
            assertTrue(catalogue.getValue(name).perLevel != true || name == TraitEffects.CHARISMA, name)
        }
        // A refusal carries a reason.
        assertTrue(TraitGrants.refusedGrants().values.all { it.isNotBlank() })
    }

    /**
     * What the granted rows reach (6fk): Hermit 8's Combat Reflexes reaches
     * every defence, Sun 9's Fit the HT rolls, Fool 8's Perfect Balance its
     * skills, Abyss 8's Compulsive Behavior its self-control roll. A row the
     * sheet already holds is not added twice.
     */
    @Test
    fun `a potion's catalogue rows reach the rolls that read them`() {
        fun walk(pathway: String, seq: Int, held: List<Trait> = emptyList()) =
            TraitGrants.withGrantedTraits(held, com.pathways.model.BeyonderState(pathway, seq))
        assertEquals(0, TraitEffects.defenseBonus(walk("Hermit", 9)))
        assertEquals(1, TraitEffects.defenseBonus(walk("Hermit", 8)))
        assertEquals(1, TraitAttributeRolls.htRollBonus(walk("Sun", 9)))
        val acrobatics = SkillKey("Acrobatics", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.HARD)
        assertEquals(ModEffect.Flat(2), TraitEffects.modifiers(walk("Fool", 8), acrobatics).single().effect)
        assertTrue(TraitEffects.modifiers(walk("Fool", 9), acrobatics).isEmpty())
        // Named, not counted: Abyss 8 prints other drawbacks, and a bare
        // non-empty check passed with this one removed (mutation, 6fk).
        assertTrue(
            SelfControl.checksFor(walk("Abyss", 8)).any { it.trait.startsWith("Compulsive Behavior") },
            SelfControl.checksFor(walk("Abyss", 8)).toString(),
        )

        val callous = Trait("Callous", 5, TraitKind.DISADVANTAGE)
        val abyss = walk("Abyss", 8, listOf(callous))
        assertEquals(1, abyss.count { it.name == "Callous" })
        assertEquals(callous, abyss.first { it.name == "Callous" })
        // The merge is the bought list plus the grants it does not already hold.
        val grants = TraitGrants.grantedTraits(com.pathways.model.BeyonderState("Abyss", 8))
        assertEquals(listOf(callous) + grants.filter { it.name != "Callous" }, abyss)
        assertTrue(grants.all { it.cost == 0 })
        assertTrue(TraitGrants.grantedTraits(null).isEmpty())
    }

    /**
     * Charisma is a Stat/Advantage gain like "DX +1", which "goes up by 1
     * permanently" (5446), so a walked chain ADDS its blocks' levels (6fm):
     * Demoness 8's +2, 7's +1 and 6's +1 make 4, the catalogue's own ceiling.
     * A bought level and a granted one are one row at their sum, keeping the
     * bought cost, and the skill half stays capped at four levels.
     */
    @Test
    fun `a potion's Charisma adds to the chain and to a bought level`() {
        fun walk(pathway: String, seq: Int, held: List<Trait> = emptyList()) =
            TraitGrants.withGrantedTraits(held, com.pathways.model.BeyonderState(pathway, seq))
        val leadership = SkillKey("Leadership", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE)
        fun bonus(traits: List<Trait>) =
            TraitEffects.modifiers(traits, leadership).sumOf { (it.effect as ModEffect.Flat).n }
        assertEquals(0, bonus(walk("Demoness", 9)))
        assertEquals(2, bonus(walk("Demoness", 8)))
        assertEquals(3, bonus(walk("Demoness", 7)))
        assertEquals(4, bonus(walk("Demoness", 6)))
        assertEquals(1, bonus(walk("Error", 8)))
        assertEquals(listOf("Charisma 4"), walk("Demoness", 6).map { it.name }.filter { it.startsWith("Charisma") })

        val bought = Trait("Charisma 2", 10, TraitKind.ADVANTAGE)
        val teller = walk("Demoness", 8, listOf(bought))
        assertEquals(listOf(Trait("Charisma 4", 10, TraitKind.ADVANTAGE)), teller.filter { it.name.startsWith("Charisma") })
        assertEquals(4, bonus(teller))
        // The Traits tab says which levels the potion ADDED to a bought row.
        fun charisma(traits: List<Trait>) = traits.first { it.name.startsWith("Charisma") }
        assertEquals(2, TraitGrants.grantedLevelsIn(charisma(teller), listOf(bought)))
        assertEquals(null, TraitGrants.grantedLevelsIn(charisma(walk("Demoness", 8)), emptyList()))
        assertEquals(null, TraitGrants.grantedLevelsIn(bought, listOf(bought)))
        // A bare bought "Charisma" is level one, so Demoness 8 adds two.
        val bare = Trait("Charisma", 5, TraitKind.ADVANTAGE)
        assertEquals(Trait("Charisma 3", 5, TraitKind.ADVANTAGE), charisma(walk("Demoness", 8, listOf(bare))))
        // Bought 3 under a granted 2 is capped at 4: the potion added one, not two.
        val three = Trait("Charisma 3", 15, TraitKind.ADVANTAGE)
        assertEquals(1, TraitGrants.grantedLevelsIn(charisma(walk("Demoness", 8, listOf(three))), listOf(three)))
        // Bought 2 and granted 4 hold six levels; the cell's ceiling is four.
        assertEquals(4, bonus(walk("Demoness", 6, listOf(bought))))
        // ...and the row says four too, not a level past the cell's ceiling.
        val four = Trait("Charisma 4", 20, TraitKind.ADVANTAGE)
        assertEquals(listOf(four), walk("Demoness", 8, listOf(four)).filter { it.name.startsWith("Charisma") })
        // A number that is not a level stays as printed: Abyss 8's SC 6.
        assertTrue(walk("Abyss", 8).any { it.name == "Compulsive Behavior (Indulge Evil Desires) SC 6" })
    }

    /**
     * Demoness 7 and 6 grant a face (6fn). The chain holds one, Sequence 6's;
     * a bought face as good keeps the grant out, and a worse one stays in the
     * list because the budget bills it, while the better face is played.
     */
    @Test
    fun `a potion's face is held once, and the better face is played`() {
        fun walk(pathway: String, seq: Int, held: List<Trait> = emptyList()) =
            TraitGrants.withGrantedTraits(held, com.pathways.model.BeyonderState(pathway, seq))
        fun faces(traits: List<Trait>) = traits.map { it.name }.filter { n -> Appearance.all().any { it.level == n } }
        val beautiful = "Handsome / Beautiful"
        val very = "Very Handsome / Very Beautiful"
        assertEquals(emptyList<String>(), faces(walk("Demoness", 8)))
        assertEquals(listOf(beautiful), faces(walk("Demoness", 7)))
        assertEquals(listOf(very), faces(walk("Demoness", 6)))

        val boughtVery = Appearance.traitFor(Appearance.named(very))
        assertEquals(listOf(boughtVery), walk("Demoness", 7, listOf(boughtVery)).filter { it.name == very || it.name == beautiful })
        val ugly = Appearance.traitFor(Appearance.named("Ugly"))
        val played = walk("Demoness", 7, listOf(ugly))
        assertEquals(listOf("Ugly", beautiful), faces(played))
        assertEquals(beautiful, Appearance.played(played)!!.level)
    }

    @Test
    fun `the face table is every block that prints a face`() {
        // Each level's own words, longest first, so "Very Beautiful" is never
        // also read as "Beautiful".
        val words = Appearance.all().flatMap { level -> level.level.split(" / ").map { it to level.level } }
            .sortedByDescending { it.first.length }
        val printed = ContentLoader.pathways().mapNotNull { block ->
            val text = block.advantages + " " + block.drawbacks
            val taken = mutableListOf<IntRange>()
            val found = mutableSetOf<String>()
            for ((word, level) in words) {
                for (m in Regex("(?<![A-Za-z])${Regex.escape(word)}(?![A-Za-z])").findAll(text)) {
                    if (taken.any { m.range.first in it }) continue
                    taken += m.range
                    found += level
                }
            }
            if (found.isEmpty()) null else (block.pathway to block.sequence) to found
        }.toMap()
        val table = TraitGrants.grantedTraitTable()
            .mapValues { (_, rows) -> rows.map { it.name }.filter { n -> Appearance.all().any { it.level == n } }.toSet() }
            .filterValues { it.isNotEmpty() }
        assertEquals(printed, table)
        assertEquals(setOf("Demoness" to 7, "Demoness" to 6), table.keys)
    }

    /**
     * A walked chain carries every sense its blocks granted (6fi), so a Red
     * Priest 8 still holds Sequence 9's, and a mortal holds none. Each reaches
     * a Per skill's roll as a declaration, the bought rows' shape (6fh).
     */
    @Test
    fun `a potion's senses reach a Per skill's roll`() {
        fun state(pathway: String, seq: Int) = com.pathways.model.BeyonderState(pathway, seq)
        val search = SkillKey("Search", null, ControllingAttribute.Per, Difficulty.AVERAGE)
        fun offered(state: com.pathways.model.BeyonderState?) =
            TraitClauses.skillRollClauses(TraitGrants.withGrantedTraits(emptyList(), state), search)
        assertTrue(offered(null).isEmpty())
        assertTrue(offered(state("Fool", 9)).isEmpty())
        assertEquals(setOf("Acute Vision", "Acute Hearing"), offered(state("Red Priest", 8)).map { it.trait }.toSet())
        val conspirer = offered(state("Red Priest", 6))
        assertEquals(
            listOf("Acute Vision", "Acute Hearing", "Acute Taste & Smell", "Acute Touch").sorted(),
            conspirer.map { it.trait }.sorted(),
        )
        val held = TraitGrants.withGrantedTraits(emptyList(), state("Red Priest", 6))
        for (clause in conspirer) {
            assertEquals(
                ModEffect.Flat(2),
                TraitEffects.modifiers(held, search, setOf(clause.key)).single().effect,
                clause.trait,
            )
        }
        // The two rows no catalogue prints are pinned to their block's own words.
        val block = ContentLoader.pathways().single { it.pathway == "Red Priest" && it.sequence == 6 }
        for (clause in conspirer) {
            if (clause.trait in setOf("Acute Taste & Smell", "Acute Touch")) {
                assertTrue(clause.printed in block.advantages, clause.printed)
            }
        }
        // The Spirit Vision a Red Priest 8 is granted takes the Vision row.
        assertEquals("Acute Vision", TraitClauses.visionClause(TraitGrants.withGrantedTraits(emptyList(), state("Red Priest", 8)))?.trait)
        // Granted rows never enter the catalogue census.
        assertTrue("Acute Touch" !in TraitEffects.known())
    }

    /**
     * Stacking (6fi). A different row is a different chip: a Visionary 7 is
     * offered Acute Vision's +2 and Incisive Vision's +4 apart, and the GM
     * says whether both apply. The same row twice is one chip: a bought
     * Acute Vision beside Red Priest 9's and 6's.
     */
    @Test
    fun `a potion's sense rows stack by row, never by grant`() {
        val search = SkillKey("Search", null, ControllingAttribute.Per, Difficulty.AVERAGE)
        val visionary = TraitGrants.withGrantedTraits(emptyList(), com.pathways.model.BeyonderState("Visionary", 7))
        val offered = TraitClauses.skillRollClauses(visionary, search)
        assertEquals(mapOf("Acute Vision" to 2, "Incisive Vision" to 4, "Acute Smell" to 2), offered.associate { it.trait to it.value })
        val incisive = offered.single { it.trait == TraitEffects.INCISIVE_VISION }
        assertEquals(ModEffect.Flat(4), TraitEffects.modifiers(visionary, search, setOf(incisive.key)).single().effect)
        assertEquals(2, TraitEffects.modifiers(visionary, search, offered.map { it.key }.toSet() - incisive.key).size)
        // Each printed text is its own block's.
        val blocks = ContentLoader.pathways().filter { it.pathway == "Visionary" }
        for (clause in offered.filter { it.trait != "Acute Vision" }) {
            assertTrue(blocks.any { clause.printed in it.advantages }, clause.printed)
        }

        // Held, each prints its whole row, copied from its block (6fr), and
        // what of it the chip does not carry. Two rows run past the bonus.
        val rows = mapOf(
            "Acute Smell" to ("+2 sharper sense of smell; detect subtle scents and emotional cues" to
                "detect subtle scents and emotional cues"),
            "Greater Acute Vision" to ("+4 to all vision-based Perception rolls; allows the Pleasure to see their own Spider's Threads" to
                "allows the Pleasure to see their own Spider's Threads"),
            "Acute Taste & Smell" to ("+2 to taste/smell-based Perception rolls" to null),
            "Acute Touch" to ("+2 to touch-based Perception rolls" to null),
        )
        val prose = ContentLoader.pathways().map { it.advantages }
        for ((name, expected) in rows) {
            val (cell, rest) = expected
            assertTrue(prose.any { "$name $cell" in it }, name)
            val held = TraitReport.held(listOf(advantage(name)), TraitKind.ADVANTAGE).single()
            assertEquals(cell, held.cell, name)
            assertEquals(listOfNotNull(rest), held.notes.map { it.printed }, name)
        }

        // v6.9i prints Incisive Vision as a catalogue row (book 1008), so it
        // holds the catalogue cell and its block prints the same clause.
        assertTrue(prose.any { "Incisive Vision +4 to all Vision rolls" in it })
        val incisiveHeld = TraitReport.held(listOf(advantage("Incisive Vision")), TraitKind.ADVANTAGE).single()
        assertEquals("Prerequisite: Acute Vision. +4 to all Vision rolls.", incisiveHeld.cell)
        assertEquals(emptyList<String>(), incisiveHeld.notes.map { it.printed })

        val bought = listOf(advantage("Acute Vision"))
        val conspirer = TraitGrants.withGrantedTraits(bought, com.pathways.model.BeyonderState("Red Priest", 6))
        assertEquals(1, TraitClauses.skillRollClauses(conspirer, search).count { it.trait == "Acute Vision" })
    }
}
