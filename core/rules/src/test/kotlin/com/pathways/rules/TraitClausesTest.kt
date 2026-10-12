package com.pathways.rules

import com.pathways.model.Attr
import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import com.pathways.model.ModEffect
import com.pathways.model.ModifierSource
import com.pathways.model.SkillKey
import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The clauses a roll offers the GM to declare ([TraitClauses]). */
class TraitClausesTest {
    private fun advantage(name: String) = Trait(name, 5, TraitKind.ADVANTAGE)
    private fun fixed(face: Int) = RandomSource { face }
    private fun skill(attr: ControllingAttribute) =
        SkillKey("Whatever", null, attr, Difficulty.AVERAGE)
    private val iq = skill(ControllingAttribute.Buyable(Attr.IQ))
    private fun frightOf(vararg names: String) =
        TraitClauses.frightClauses(names.map { Trait(it, 5, TraitKind.ADVANTAGE) })
    private val intimidation = SkillKey(
        "Intimidation", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.AVERAGE,
    )
    private fun social(name: String, cost: Int = 10) =
        listOf(Trait(name, cost, TraitKind.DISADVANTAGE))
    private fun willOf(vararg names: String) =
        TraitClauses.willClauses(names.map { Trait(it, 5, TraitKind.ADVANTAGE) })

    // ---- the five rows that modify a Fright Check --------------------------


    /**
     * **Five catalogue rows modify a roll this app has made since 6c**, and
     * `FrightCheck.against` has taken a modifier the whole time. What was
     * missing was the join, which is why the reason those rows carried was a
     * GAP rather than a refusal.
     *
     * Every value is pinned against the copy production reads, so a re-extract
     * that reworded a cell fails here rather than moving a number in silence.
     */
    @Test
    fun `every fright number is still the one its row prints`() {
        val byName =
            (ContentLoader.advantages() + ContentLoader.disadvantages()).associateBy { it.name }
        for (clause in TraitClauses.frightClauseRows()) {
            val notes = byName.getValue(clause.trait).notes
            assertTrue(clause.printed in notes, "${clause.trait}: ${clause.printed}")
            // The clause names its own number, so the two cannot drift apart.
            val sign = if (clause.value < 0) "-" else "+"
            assertTrue(
                "$sign${kotlin.math.abs(clause.value)}" in clause.printed,
                "${clause.trait} prints no ${sign}${kotlin.math.abs(clause.value)}",
            )
        }
    }

    /**
     * **Fearlessness is the only unconditional row**, so it is the only one a
     * screen may add without asking. The other four name a circumstance the GM
     * declares -- being in a church, being exposed to the phobic thing -- and
     * this app holds one sheet and no scene, so it cannot know.
     */
    @Test
    fun `only the row that names no circumstance is unconditional`() {
        val unconditional = TraitClauses.frightClauseRows()
            .filter { !it.conditional }
            .map { it.trait }
        assertEquals(listOf("Fearlessness"), unconditional)
    }

    /** "+1 per level to Fright Checks", and the cell prints no ceiling. */
    @Test
    fun `fearlessness scales with its level`() {
        assertEquals(1, frightOf("Fearlessness").single().value)
        assertEquals(3, TraitClauses.frightClauses(
            listOf(Trait("Fearlessness 3", 6, TraitKind.ADVANTAGE)),
        ).single().value)
        assertNull(TraitEffects.maxLevelsFor("Fearlessness"))
    }

    /**
     * **Sacred Symbols prints TWO circumstances and both are offered.** "-2 on
     * direct exposure" is a relic in view and "-1 in consecrated buildings" is
     * standing inside one; a cathedral holding a relic is both at once, the
     * book prints no exclusion, and two consecutive sentences of one cell were
     * read as adding once already (6aa's Precious altar and its sentient
     * offering). So they sum to -3 when both are declared.
     */
    @Test
    fun `two circumstances on one cell are two clauses`() {
        val clauses = frightOf("Phobia: Sacred Symbols")
        assertEquals(2, clauses.size)
        assertEquals(listOf(-2, -1), clauses.map { it.value })
        // Each is separately declarable, so the key has to tell them apart.
        assertEquals(2, clauses.map { it.key }.toSet().size)
        assertEquals(
            -3,
            TraitClauses.clauseBonus(clauses, clauses.map { it.key }.toSet()).total,
        )
    }

    /**
     * The number and the sentence come from one call, because a screen that
     * computes one and prints the other is how these breakdowns stopped
     * reconciling (6ag, 6ai, 6be, 6bp, 6bs). The parts must sum to the total
     * across every combination a GM can declare.
     */
    @Test
    fun `the breakdown always sums to the bonus it explains`() {
        val clauses = frightOf("Fearlessness", "Faithful Grounding", "Phobia: Sacred Symbols")
        val keys = clauses.map { it.key }
        // Every subset of the declarable clauses.
        for (mask in 0 until (1 shl keys.size)) {
            val declared = keys.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }.toSet()
            val bonus = TraitClauses.clauseBonus(clauses, declared)
            val line = bonus.line
            if (bonus.total == 0 && line == null) continue
            val summed = Regex("[+−-]\\d+").findAll(line.orEmpty())
                .sumOf { it.value.replace('−', '-').toInt() }
            assertEquals(bonus.total, summed, line)
        }
    }

    /**
     * An unconditional clause is counted whether or not it was declared: the
     * book attaches no circumstance to it, so a screen that forgot to pass it
     * must not silently withdraw a bonus the character always has.
     */
    @Test
    fun `an unconditional clause needs no declaration`() {
        val clauses = frightOf("Fearlessness")
        assertEquals(1, TraitClauses.clauseBonus(clauses, emptySet()).total)
        // ...and a conditional one is worth nothing until it is declared.
        val phobia = frightOf("Phobia (Cities)")
        assertEquals(0, TraitClauses.clauseBonus(phobia, emptySet()).total)
        assertEquals(-2, TraitClauses.clauseBonus(phobia, setOf(phobia.single().key)).total)
    }

    /**
     * **Hunter-Tracker is the only premade that reaches any of this**, through
     * the placeholder join above -- "Phobia (Cities)" against the catalogue's
     * "Phobia (specify)". Before that join the trait resolved to no row at
     * all, so the two halves really are one slice.
     */
    @Test
    fun `the one premade carrying a fright row reaches it`() {
        val carriers = Premades.all().filter {
            TraitClauses.frightClauses(it.toCharacter().traits).isNotEmpty()
        }
        assertEquals(listOf("Hunter-Tracker"), carriers.map { it.name })
        val clause = TraitClauses.frightClauses(carriers.single().toCharacter().traits).single()
        assertEquals("Phobia (specify)", clause.trait)
        // The screen names the sheet's own spelling, not the placeholder row:
        // which fear this character has is content the GM needs.
        assertEquals("Phobia (Cities)", clause.shown)
        assertEquals(-2, clause.value)
        assertTrue(clause.conditional)
    }

    // ---- the social rows whose penalty needs a circumstance ----------------



    /** Every value is still the one its row prints, against the live copy. */
    @Test
    fun `every social number is still the one its row prints`() {
        val byName = ContentLoader.disadvantages().associateBy { it.name }
        for (clause in TraitClauses.socialClauseRows()) {
            val notes = byName.getValue(clause.trait).notes
            assertTrue(clause.printed in notes, "${clause.trait}: ${clause.printed}")
            assertTrue("-${-clause.value}" in clause.printed, clause.printed)
        }
        // All three rows are conditional; none may ever be counted unasked.
        assertTrue(TraitClauses.socialClauseRows().all { it.conditional })
    }

    /**
     * **Nothing reaches the roll until the GM says the circumstance holds.**
     * Barmaid Brawler and Docks Bouncer both own Callous BESIDE Intimidation,
     * so a flat penalty would charge them for warmth nobody asked for.
     */
    @Test
    fun `a social penalty needs its circumstance declared`() {
        val callous = social("Callous")
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(callous, intimidation))
        val clause = TraitClauses.socialClauses(callous).single()
        val applied = TraitEffects.modifiers(callous, intimidation, setOf(clause.key))
        assertEquals(ModEffect.Flat(-3), applied.single().effect)
        // ...and only on a SOCIAL skill: the cell says "social skill rolls".
        val climbing = SkillKey(
            "Climbing", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE,
        )
        assertEquals(
            emptyList<Any>(),
            TraitEffects.modifiers(callous, climbing, setOf(clause.key)),
        )
    }

    /**
     * **Shyness's value comes from the SHEET, not from the GM.** Its cell
     * prices two severities and a character bought one; only the variant join
     * can tell which, so a Mild shyness must never offer Severe's -2.
     */
    @Test
    fun `a priced variant decides which clause is on offer`() {
        val mild = TraitClauses.socialClauses(social("Shyness (Mild)", 5)).single()
        assertEquals(-1, mild.value)
        assertTrue("Mild" in mild.printed)
        val severe = TraitClauses.socialClauses(social("Shyness (Severe)", 10)).single()
        assertEquals(-2, severe.value)
        // The sheet's own spelling is what the GM sees.
        assertEquals("Shyness (Mild)", mild.shown)
        // **A bare "Shyness" names no severity, so it offers NEITHER.** An
        // empty qualifier prefixes every option, which would put Mild and
        // Severe on screen together and let a GM declare both -- and severity
        // is the sheet's to state, not a choice (Codex, on the diff).
        assertEquals(emptyList<Any>(), TraitClauses.socialClauses(social("Shyness", 5)))
    }

    /**
     * Social Anxiety prints two circumstances that can both hold — a large
     * gathering of strangers who outrank you — and the book prints no
     * exclusion, so they are separate declarations that SUM. Two modifiers,
     * two distinct ids: sharing one would be a collision the resolver should
     * never be handed.
     */
    @Test
    fun `two circumstances on one social cell sum and stay distinct`() {
        val anxious = social("Social Anxiety")
        val keys = TraitClauses.socialClauses(anxious).map { it.key }.toSet()
        assertEquals(2, keys.size)
        val mods = TraitEffects.modifiers(anxious, intimidation, keys)
        assertEquals(2, mods.map { it.id }.toSet().size, "two clauses shared one id")
        assertEquals(-5, mods.sumOf { (it.effect as ModEffect.Flat).n })
    }

    /**
     * A trait modifier is `FromTrait`, never `Situational`: a cancel budget is
     * measured by `SourceKind`, and a drawback the character permanently has
     * is not something a GM said about this turn. Nothing can reach the
     * difference today, which is why it is pinned now rather than found the
     * day something does (6al's precedent).
     */
    @Test
    fun `a declared social penalty is sourced from the trait`() {
        val callous = social("Callous")
        val key = TraitClauses.socialClauses(callous).single().key
        val mod = TraitEffects.modifiers(callous, intimidation, setOf(key)).single()
        val source = mod.source
        assertTrue(source is ModifierSource.FromTrait, source.toString())
        assertEquals(false, (source as ModifierSource.FromTrait).advantage)
    }

    /**
     * **The rows are reachable on the premades that carry them**, which is
     * what a census is for (6bv). Academic Researcher's is reachable only
     * through the priced-variant join in this same slice.
     */
    @Test
    fun `the premades carrying a social row reach it`() {
        val carriers = Premades.all()
            .filter { TraitClauses.socialClauses(it.toCharacter().traits).isNotEmpty() }
            .map { it.name }
        assertEquals(
            listOf("Academic Researcher", "Barmaid Brawler", "Docks Bouncer"),
            carriers.sorted(),
        )
        // Social Anxiety is on no sheet and ships anyway: the list is every
        // conditional social clause the book prints, not a premade census.
        assertTrue("Social Anxiety" in TraitClauses.socialClauseNames())
    }

    @Test
    fun `a bare social rolls clause reaches no skill`() {
        val loner = listOf(Trait("Loner", 5, TraitKind.DISADVANTAGE))
        val diplomacy = SkillKey(
            "Diplomacy", null, ControllingAttribute.Buyable(Attr.IQ), Difficulty.HARD,
        )
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(loner, diplomacy))
        assertTrue(TraitReport.reported(loner).any { "social rolls" in it.printed })
    }

    // ---- the five rows that modify a bare Will roll ------------------------


    /**
     * **The census, with the query that reproduces it.** 6de recorded "four
     * rows name Will rolls" and stated no filter, so the number could not be
     * checked -- and `Will roll` singular returns five. The filter is `Will
     * rolls`, PLURAL: it drops `Compulsive Gambling`, whose "requires Will
     * roll at -2" is already read as a self-control check.
     *
     * A count without its query is unreproducible, which is what this test
     * exists to stop happening a second time.
     */
    @Test
    fun `four catalogue rows name Will rolls and two of them are already read`() {
        val catalogue = ContentLoader.advantages() + ContentLoader.disadvantages()
        val plural = catalogue.filter { "Will rolls" in it.notes.orEmpty() }.map { it.name }
        assertEquals(
            setOf("Incorruptible Will", "Grief-Stricken", "Reluctant Killer", "Evil Eye Fear"),
            plural.toSet(),
        )
        // The singular row is the one the plural query drops, and it is read.
        val gambling = ContentLoader.disadvantages().single { it.name == "Compulsive Gambling" }
        assertTrue("Will roll at -2" in gambling.notes.orEmpty())
        assertTrue("Will rolls" !in gambling.notes.orEmpty())
        assertTrue("Compulsive Gambling" !in TraitClauses.willClauseNames())
        // Reluctant Killer IS returned by the plural query and still carries
        // no number, so it is read as a self-control check and not as a
        // clause. A row NAMING a roll is not a row MODIFYING one.
        assertTrue("Reluctant Killer" !in TraitClauses.willClauseNames())
        // ...and "already read" is asserted, not asserted-about: both rows
        // still produce a self-control check, at the offset their cell prints
        // (Codex -- the name claimed a reader the test never called).
        for ((name, offset) in listOf("Compulsive Gambling" to -2, "Reluctant Killer" to 0)) {
            val check = SelfControl.checksFor(
                listOf(Trait(name, 10, TraitKind.DISADVANTAGE)),
            ).single()
            assertEquals(name, check.trait)
            assertEquals(offset, check.spec.offset, name)
        }
    }

    /**
     * Every value is pinned against the copy production reads, so a
     * re-extract that reworded a cell fails here rather than moving a number
     * in silence -- the guard the Fright Check's rows already have.
     */
    @Test
    fun `every will number is still the one its row prints`() {
        val byName =
            (ContentLoader.advantages() + ContentLoader.disadvantages()).associateBy { it.name }
        assertEquals(5, TraitClauses.willClauseRows().size)
        for (clause in TraitClauses.willClauseRows()) {
            val notes = byName.getValue(clause.trait).notes.orEmpty()
            assertTrue(clause.printed in notes, "${clause.trait}: ${clause.printed}")
            val sign = if (clause.value < 0) "-" else "+"
            assertTrue(
                "$sign${kotlin.math.abs(clause.value)}" in clause.printed,
                "${clause.trait} prints no $sign${kotlin.math.abs(clause.value)}",
            )
        }
    }

    /**
     * **All five name a circumstance, so none may be counted unasked.**
     * Unlike the Fright Check's rows, where Fearlessness is what the
     * character always is, there is no unconditional Will clause: every cell
     * says "to resist", "if interrupted" or "in situations that echo".
     */
    @Test
    fun `no will clause may be counted without a declaration`() {
        assertTrue(TraitClauses.willClauseRows().all { it.conditional })
        val carrier = willOf("Incorruptible Will", "Evil Eye Fear")
        assertEquals(0, TraitClauses.clauseBonus(carrier, emptySet()).total)
        assertNull(TraitClauses.clauseBonus(carrier, emptySet()).line)
    }

    /**
     * The arithmetic names both terms. +3 and -1 were chosen as the carrier
     * pair precisely because 3, -1, 0 and 2 are four different answers: no
     * assertion here survives dropping either clause, which is the vacuity
     * 6de's own targetFor test shipped (12 + 1 - 1 == 12).
     */
    @Test
    fun `declared will clauses sum and undeclared ones do not`() {
        val clauses = willOf("Incorruptible Will", "Evil Eye Fear")
        assertEquals(2, clauses.size)
        val incorruptible = clauses.single { it.trait == "Incorruptible Will" }
        val evilEye = clauses.single { it.trait == "Evil Eye Fear" }
        assertEquals(3, TraitClauses.clauseBonus(clauses, setOf(incorruptible.key)).total)
        assertEquals(-1, TraitClauses.clauseBonus(clauses, setOf(evilEye.key)).total)
        assertEquals(
            2,
            TraitClauses.clauseBonus(clauses, setOf(incorruptible.key, evilEye.key)).total,
        )
        // The sentence and the number come from one call, so they cannot drift.
        val both = TraitClauses.clauseBonus(clauses, clauses.map { it.key }.toSet())
        assertTrue("Incorruptible Will +3" in both.line.orEmpty(), both.line.orEmpty())
        assertTrue("Evil Eye Fear -1" in both.line.orEmpty(), both.line.orEmpty())
    }

    /**
     * **The suite knew and production did not.** SelfControlTest has recorded
     * since 6am that Grief-Stricken's offset "is attached TO a roll ... so it
     * is a modifier and `TraitEffects`' business" -- and `TraitEffects` had
     * never taken the business. Both halves are asserted here: it is NOT a
     * self-control check, and it IS a Will clause.
     */
    @Test
    fun `the row a test routed here is now collected`() {
        val grief = Trait("Grief-Stricken", 5, TraitKind.DISADVANTAGE)
        assertTrue(SelfControl.checksFor(listOf(grief)).isEmpty())
        val clause = TraitClauses.willClauses(listOf(grief)).single()
        assertEquals(-2, clause.value)
        // Its qualifier names no roll this book prints, which is why it
        // reaches every bare Will roll rather than one of them: contrast
        // Hard to Kill's "to avoid death", which picks one of the HP table's
        // PRINTED pair.
        assertTrue("to act clearly" in clause.printed)
        assertEquals(5, TraitClauses.willClauseNames().size)
    }

    /**
     * "-1 to Will" with no "roll" is read as a penalty on the bare Will roll,
     * a READING (6gr): every conditional "-N to Will" the book prints elsewhere
     * is a roll penalty (Sin of Greed's "-5 to Will roll to resist", Sin of
     * Lust's "-5 to Will when pursuing"), and a day without tobacco moving the
     * Will score would move the permanent CoR ceiling with it.
     *
     * Bound to a Place is NOT one: "-1 to Will per additional week,
     * cumulative" is a count of weeks, and one fixed -1 chip would misstate
     * every absence past two.
     */
    @Test
    fun `tobacco and guilt are will clauses and bound to a place is not`() {
        val clauses = willOf("Addiction: Tobacco", "Guilt", "Bound to a Place")
        assertEquals(setOf("Addiction: Tobacco", "Guilt"), clauses.map { it.trait }.toSet())
        assertTrue(clauses.all { it.value == -1 && it.conditional })
        assertTrue("Bound to a Place" !in TraitClauses.willClauseNames())
        assertTrue("Bound to a Place" !in TraitEffects.known())
        assertTrue("Addiction: Tobacco" in TraitEffects.known())
        assertTrue("Guilt" in TraitEffects.known())
    }

    /**
     * One list answers all three sites, so the same declaration moves the
     * same number wherever it is made -- and Concentrate takes it through its
     * OWN parameter, never through the one named for reeling and fatigue.
     */
    @Test
    fun `one clause list answers every bare will roll`() {
        val clauses = willOf("Grief-Stricken")
        val bonus = TraitClauses.clauseBonus(clauses, clauses.map { it.key }.toSet()).total
        assertEquals(-2, bonus)
        assertEquals(
            Concentration.targetFor(will = 12) + bonus,
            Concentration.targetFor(will = 12, fromTraits = bonus),
        )
        // Distinct from the fatigue term: passing it as `hurt` would land the
        // same number under someone else's label on screen, so the two
        // parameters must both exist and both count.
        assertEquals(
            Concentration.targetFor(will = 12, hurt = -1, fromTraits = bonus),
            Concentration.targetFor(will = 12) - 1 + bonus,
        )
        val spec = ContentLoader.disadvantages()
            .first { it.selfControl != null }.selfControl!!
        assertEquals(
            SelfControl.targetFor(spec, will = 12, iq = 10, modifier = 0) + bonus,
            SelfControl.targetFor(spec, will = 12, iq = 10, modifier = bonus),
        )
        // The third site. A Fright Check IS a bare Will roll, so the same
        // declaration moves its target too -- and the two clause lists it now
        // takes cannot collide, because [TraitClause.key] is "shown /
        // printed" and no row appears in both (Codex: the name claimed three
        // sites and the body proved two).
        // The invariant the concatenation rests on is that no TRAIT is in
        // both lists -- a same-trait pair is the only way two keys could
        // collide, and it is what a future slice adding a row to the wrong
        // list would break.
        assertEquals(
            emptySet<String>(),
            TraitClauses.frightClauseNames() intersect TraitClauses.willClauseNames(),
        )
        val fright = TraitClauses.frightClauses(listOf(advantage("Fearlessness"))) + clauses
        assertEquals(fright.size, fright.map { it.key }.toSet().size)
        val declaredAll = clauses.map { it.key }.toSet()
        assertEquals(
            1 + bonus,
            TraitClauses.clauseBonus(fright, declaredAll).total,
        )
        assertEquals(
            FrightCheck.against(will = 12, modifier = 0, source = fixed(10)).target + bonus,
            FrightCheck.against(will = 12, modifier = bonus, source = fixed(10)).target,
        )
    }

    /**
     * Thinned Veil: "Concentration in spiritually active environments at -2"
     * (book line 1472; 6fa). It reaches the Concentrate maneuver's roll, the
     * app's one concentration check, as a declaration the GM makes, and never
     * a Fright Check or a self-control roll, which a Will clause would reach.
     * Capitalisation is no warrant, because the word starts a sentence (the
     * design review). Every other roll that needs focus is said to be the
     * GM's call.
     */
    @Test
    fun `Thinned Veil reaches the Concentrate roll alone, as a declaration`() {
        val cell = ContentLoader.disadvantages().single { it.name == TraitEffects.THINNED_VEIL }.notes
        assertTrue("Concentration in spiritually active environments at -2" in cell, cell)

        val veil = Trait(TraitEffects.THINNED_VEIL, 10, TraitKind.DISADVANTAGE)
        val clause = TraitClauses.concentrationClauses(listOf(veil)).single()
        assertEquals(-2, clause.value)
        assertTrue(clause.conditional)
        assertTrue(clause.printed in cell, clause.printed)
        assertTrue(TraitClauses.willClauses(listOf(veil)).isEmpty())

        // The bare Will clauses still reach the Concentrate roll.
        val grief = Trait("Grief-Stricken", 5, TraitKind.DISADVANTAGE)
        assertEquals(TraitClauses.willClauses(listOf(grief)), TraitClauses.concentrationClauses(listOf(grief)))

        assertTrue(TraitEffects.THINNED_VEIL in TraitEffects.known())
        val note = TraitReport.reported(listOf(veil)).single()
        assertTrue("GM" in note.reason, note.reason)
        assertEquals(clause.printed, note.printed)
    }

    /**
     * Chronic Insomnia: "rolls requiring sustained concentration at -1"
     * (book lines 1255-1256; 6fb). Declared on the Concentrate roll, because
     * whether THIS concentration is sustained is the GM's to say; the book
     * sets no duration. Its other clause, the FP lost each morning, is the
     * table's to enter on the FP row.
     */
    @Test
    fun `Chronic Insomnia reaches the Concentrate roll alone, as a declaration`() {
        val cell = ContentLoader.disadvantages().single { it.name == TraitEffects.CHRONIC_INSOMNIA }.notes
        val insomnia = Trait(TraitEffects.CHRONIC_INSOMNIA, 5, TraitKind.DISADVANTAGE)

        val clause = TraitClauses.concentrationClauses(listOf(insomnia)).single()
        assertEquals(-1, clause.value)
        assertTrue(clause.conditional)
        assertEquals("rolls requiring sustained concentration at -1", clause.printed)
        assertTrue(clause.printed in cell, cell)
        assertTrue(TraitClauses.willClauses(listOf(insomnia)).isEmpty())

        // Held beside Thinned Veil, each is its own declaration.
        val veil = Trait(TraitEffects.THINNED_VEIL, 10, TraitKind.DISADVANTAGE)
        assertEquals(
            listOf(-1, -2),
            TraitClauses.concentrationClauses(listOf(insomnia, veil)).map { it.value },
        )

        assertTrue(TraitEffects.CHRONIC_INSOMNIA in TraitEffects.known())
        val notes = TraitReport.reported(listOf(insomnia)).associateBy { it.printed }
        assertEquals(
            setOf(clause.printed, "Lose 1 FP each morning that cannot be recovered through rest"),
            notes.keys,
        )
        assertTrue("GM" in notes.getValue(clause.printed).reason)
        assertTrue("FP row" in notes.getValue("Lose 1 FP each morning that cannot be recovered through rest").reason)
    }

    /**
     * Six rows that name one skill (6es's first list; 6ff). Five print a
     * circumstance and are declarations on that skill's roll; Famous Face's
     * "Disguise attempts at -2" prints none and applies flat. Navigator's
     * Eye is "rural", so Navigation (Sea) is not offered it.
     */
    @Test
    fun `six rows that name one skill reach that skill's roll`() {
        val iq = ControllingAttribute.Buyable(Attr.IQ)
        fun key(name: String, spec: String? = null) = SkillKey(name, spec, iq, Difficulty.AVERAGE)
        val catalogue = (ContentLoader.advantages() + ContentLoader.disadvantages()).associateBy { it.name }
        val offered = listOf(
            Triple("Class Mobility", key("Savoir-Faire"), 2),
            Triple("Fearsome Reputation", key("Intimidation"), 2),
            Triple("Safecracker", key("Lockpicking"), 3),
            Triple("Alcohol Tolerance", key("Carousing"), 3),
            Triple("Navigator's Eye", key("Navigation"), 2),
            Triple("Navigator's Eye", key("Navigation", "Land"), 2),
        )
        for ((trait, skill, value) in offered) {
            val held = listOf(advantage(trait))
            val clause = TraitClauses.skillRollClauses(held, skill).single()
            assertTrue(clause.conditional, trait)
            assertEquals(value, clause.value, trait)
            assertTrue(clause.printed in catalogue.getValue(trait).notes, clause.printed)
            // Nothing until declared, then exactly the clause.
            assertEquals(emptyList<Any>(), TraitEffects.modifiers(held, skill), trait)
            val mods = TraitEffects.modifiers(held, skill, setOf(clause.key))
            assertEquals(ModEffect.Flat(value), mods.single().effect, trait)
            assertTrue(trait in TraitEffects.known(), trait)
            // ...and no other skill is offered it.
            assertTrue(TraitClauses.skillRollClauses(held, key("Stealth")).isEmpty(), trait)
        }
        assertTrue(
            TraitClauses.skillRollClauses(listOf(advantage("Navigator's Eye")), key("Navigation", "Sea")).isEmpty(),
        )
        // A sheet's spelling resolves to the row, is shown as the sheet
        // prints it, and its key reaches the dice (the diff review).
        val sheet = listOf(advantage("Safecracker 1"))
        val spelt = TraitClauses.skillRollClauses(sheet, key("Lockpicking")).single()
        assertEquals("Safecracker 1", spelt.shown)
        assertEquals(
            ModEffect.Flat(3),
            TraitEffects.modifiers(sheet, key("Lockpicking"), setOf(spelt.key)).single().effect,
        )

        val face = listOf(Trait("Famous Face", 5, TraitKind.DISADVANTAGE))
        assertEquals(ModEffect.Flat(-2), TraitEffects.modifiers(face, key("Disguise")).single().effect)
        assertTrue(TraitClauses.skillRollClauses(face, key("Disguise")).isEmpty())

        // The social rows still come through the same list, once.
        val callous = listOf(Trait("Callous", 5, TraitKind.DISADVANTAGE))
        val social = TraitClauses.skillRollClauses(callous, key("Savoir-Faire"))
        assertEquals(TraitClauses.socialClauses(callous), social)
        assertEquals(1, TraitEffects.modifiers(callous, key("Savoir-Faire"), social.map { it.key }.toSet()).size)
    }

    /**
     * Absent-Mindedness, book 1242: "-3 to skill rolls requiring
     * concentration or organisation in everyday life". The cell names the
     * roll, every skill roll, and leaves the circumstance to the GM, so it is
     * offered on every skill and counted only when declared (6gj). The
     * Concentrate maneuver's Will roll is not a skill roll and is not
     * offered it (6fa).
     */
    @Test
    fun `Absent-Mindedness is declared on any skill roll and not on Concentrate`() {
        val held = listOf(Trait("Absent-Mindedness", 15, TraitKind.DISADVANTAGE))
        val cell = ContentLoader.disadvantages().first { it.name == "Absent-Mindedness" }.notes
        for (row in ContentLoader.skills()) {
            val skill = SkillKey(row.name, row.specialization, row.attr, row.difficulty)
            val clause = TraitClauses.skillRollClauses(held, skill).single()
            assertTrue(clause.conditional, row.name)
            assertEquals(-3, clause.value)
            assertTrue(clause.printed in cell, clause.printed)
            assertEquals(emptyList<Any>(), TraitEffects.modifiers(held, skill), row.name)
            assertEquals(
                ModEffect.Flat(-3),
                TraitEffects.modifiers(held, skill, setOf(clause.key)).single().effect,
            )
        }
        assertTrue("Absent-Mindedness" in TraitEffects.known())
        assertEquals(emptyList<Any>(), TraitClauses.concentrationClauses(held))
        // The sheets' "Absent-Minded" is 6am's recorded non-match, not this row.
        val sheet = listOf(Trait("Absent-Minded", 5, TraitKind.DISADVANTAGE))
        assertTrue(TraitClauses.skillRollClauses(sheet, iq).isEmpty())
    }

    /**
     * Acute Vision's "+2 to Vision rolls" and Acute Hearing's "+2 to Hearing
     * rolls" (book 936, 940; 6fh). The book never says which rolls those are,
     * so every Per-controlled skill offers both and the GM declares which
     * sense this roll is. A skill another attribute controls is offered
     * neither.
     */
    @Test
    fun `Acute Vision and Acute Hearing are declared on a Per skill's roll`() {
        val catalogue = ContentLoader.advantages().associateBy { it.name }
        val perSkills = ContentLoader.skills().filter { it.attr == ControllingAttribute.Per }
        assertEquals(10, perSkills.size, perSkills.map { it.name }.toString())
        for (trait in listOf("Acute Vision", "Acute Hearing")) {
            val held = listOf(advantage(trait))
            for (row in perSkills) {
                val skill = SkillKey(row.name, row.specialization, row.attr, row.difficulty)
                val clause = TraitClauses.skillRollClauses(held, skill).single()
                assertTrue(clause.conditional, trait)
                assertEquals(2, clause.value, trait)
                assertTrue(clause.printed in catalogue.getValue(trait).notes, clause.printed)
                assertEquals(emptyList<Any>(), TraitEffects.modifiers(held, skill), "${row.name} $trait")
                val mods = TraitEffects.modifiers(held, skill, setOf(clause.key))
                assertEquals(ModEffect.Flat(2), mods.single().effect, "${row.name} $trait")
            }
            assertTrue(trait in TraitEffects.known(), trait)
            val stealth = SkillKey("Stealth", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)
            assertTrue(TraitClauses.skillRollClauses(held, stealth).isEmpty(), trait)
        }
        // Observation is a social skill as well: both senses and a social
        // row come through the one list, each once, under distinct keys.
        val observation = SkillKey("Observation", null, ControllingAttribute.Per, Difficulty.AVERAGE)
        val both = listOf(
            advantage("Acute Vision"), advantage("Acute Hearing"),
            Trait("Callous", 5, TraitKind.DISADVANTAGE),
        )
        val clauses = TraitClauses.skillRollClauses(both, observation)
        assertEquals(TraitClauses.socialClauses(both).size + 2, clauses.size)
        assertEquals(clauses.size, clauses.map { it.key }.toSet().size)
        assertEquals(clauses.size, TraitEffects.modifiers(both, observation, clauses.map { it.key }.toSet()).size)
        // A trait held twice is one declaration and one modifier (the review).
        // Two spellings of one row are one too: the modifier's id reads the
        // catalogue name, never the sheet's (the diff review's LOW).
        val twice = listOf(advantage("Acute Vision"), advantage("Acute Vision 2"))
        val once = TraitClauses.skillRollClauses(twice, observation).single()
        assertEquals(1, TraitEffects.modifiers(twice, observation, setOf(once.key)).size)
    }

    /**
     * Light Sleeper, book 906: "Perception rolls while sleeping at +4". A Per
     * roll under a circumstance the GM declares, so it is offered wherever a
     * Per roll is made TO the character: a Per skill, and Detection's Per
     * branch ([TraitClauses.perRollClauses]). Spirit Vision is the holder's
     * own act and costs SPI to open, so no sleeper makes it.
     */
    @Test
    fun `light sleeper is declared on a Per roll and never on Spirit Vision`() {
        val held = listOf(advantage("Light Sleeper"))
        val per = SkillKey("Observation", null, ControllingAttribute.Per, Difficulty.AVERAGE)
        val clause = TraitClauses.skillRollClauses(held, per).single()
        assertTrue(clause.conditional)
        assertEquals(4, clause.value)
        assertTrue(clause.printed in ContentLoader.advantages().single { it.name == "Light Sleeper" }.notes)
        assertEquals(emptyList<Any>(), TraitEffects.modifiers(held, per))
        assertEquals(ModEffect.Flat(4), TraitEffects.modifiers(held, per, setOf(clause.key)).single().effect)
        assertEquals(listOf(clause), TraitClauses.perRollClauses(held))
        assertNull(TraitClauses.visionClause(held))
        val dx = SkillKey("Stealth", null, ControllingAttribute.Buyable(Attr.DX), Difficulty.AVERAGE)
        assertTrue(TraitClauses.skillRollClauses(held, dx).isEmpty())
        assertTrue("Light Sleeper" in TraitEffects.known())
        // An Acute Vision holder is offered the sense on a Per skill and
        // nothing on Detection, which names no sense (6fh).
        assertTrue(TraitClauses.perRollClauses(listOf(advantage("Acute Vision"))).isEmpty())
    }
}
