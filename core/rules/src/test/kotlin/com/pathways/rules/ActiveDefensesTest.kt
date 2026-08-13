package com.pathways.rules

import com.pathways.model.Trait
import com.pathways.model.TraitKind
import com.pathways.rules.content.ContentLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ch. 5's three printed refusals of an active defence, and the one trait that cancels one. */
class ActiveDefensesTest {

    private val reflexes = listOf(Trait("Combat Reflexes", 15, TraitKind.ADVANTAGE))
    private val none = emptyList<Trait>()

    /** Nothing declared is the ordinary case: every defence stands. */
    @Test
    fun `an undeclared character defends normally`() {
        val state = ActiveDefenses.state(emptySet(), none)
        assertNull(state.refused)
        assertNull(state.negated)
        assertTrue(state.defends)
    }

    @Test
    fun `each declared cause refuses the defence with the book's own sentence`() {
        for (cause in DefenseRefusal.entries) {
            val state = ActiveDefenses.state(setOf(cause), none)
            assertEquals(cause, state.refused, "$cause should refuse")
            assertTrue(!state.defends, "$cause should withdraw the defences")
            assertTrue(state.printed!!.isNotEmpty(), "$cause prints nothing")
        }
    }

    /**
     * **Combat Reflexes cancels SURPRISE and nothing else.**
     *
     * Ch. 3 prints "never freeze in surprise" and Ch. 5's surprise rule is
     * the freeze it names -- the trait's own prose pairs them ("prevents
     * freezing on surprise", 2379-2380). The unaware shot is a different
     * cause: the bullet arrives before reaction, which is not a freeze, and
     * no printed clause touches it. Reading the trait as general defence
     * insurance would hand a fighter a rule the book never gave them.
     *
     * A reading, so it is pinned rather than left to be rediscovered.
     */
    @Test
    fun `Combat Reflexes cancels surprise alone`() {
        val surprised = ActiveDefenses.state(setOf(DefenseRefusal.SURPRISE), reflexes)
        assertNull(surprised.refused)
        assertEquals(DefenseRefusal.SURPRISE, surprised.negated)
        assertEquals("Combat Reflexes", surprised.negatedBy)
        assertTrue(surprised.defends)

        for (cause in DefenseRefusal.entries - DefenseRefusal.SURPRISE) {
            val state = ActiveDefenses.state(setOf(cause), reflexes)
            assertEquals(cause, state.refused, "Combat Reflexes must not cancel $cause")
            assertNull(state.negated, "$cause is not a freeze")
        }
    }

    /**
     * A cancelled cause does not shield an uncancelled one declared with it.
     *
     * A surprised character shot from concealment still cannot defend, and
     * the reason shown has to be the shot rather than the surprise the trait
     * already handled.
     */
    @Test
    fun `a surviving cause outranks a cancelled one`() {
        val state = ActiveDefenses.state(
            setOf(DefenseRefusal.SURPRISE, DefenseRefusal.UNAWARE_RANGED),
            reflexes,
        )
        assertEquals(DefenseRefusal.UNAWARE_RANGED, state.refused)
        assertEquals(DefenseRefusal.SURPRISE, state.negated)
        assertTrue(!state.defends)
    }

    /**
     * **Four maneuvers forfeit the next turn's defence, and p. 37 names
     * three.** The sentence granting the defence excepts "an All-Out Attack
     * maneuver"; the maneuver table prints "Cannot defend next turn." on the
     * three All-Out Attacks AND on Charge Attack. The table is the specific
     * printing and wins, which is the call the PCR's critical rules and the
     * ritual CoR ladder already needed.
     *
     * Read from committed content, never a second list here: `maneuvers.json`
     * has carried the sentence since the combat slice.
     */
    @Test
    fun `the forfeiting actions come from the book's own tables`() {
        assertEquals(
            listOf(
                "All-Out Attack (Determined)",
                "All-Out Attack (Strong)",
                "All-Out Attack (Double)",
                "Charge Attack",
                "Feverish Defense — Variant Rule",
            ),
            ActiveDefenses.forfeitingActions(),
        )
    }

    /**
     * **The forfeit is identified by its SENTENCE, not by its table.**
     *
     * `extra_effort.json` prints "Cannot defend next turn." on Feverish
     * Defense, word for word what the maneuver table prints on four of its
     * rows. Reading only the maneuvers -- which is what this object did when
     * it shipped -- leaves a fighter who spent 1 FP on a feverish defence
     * being offered a defence the book took away, and the omission is
     * invisible because the list it comes from looks complete.
     *
     * Found by the clause sweep 6bf opened: two mechanical clauses in one
     * cell, where the first ("+2 to one defense roll") is what the eye reads.
     */
    @Test
    fun `an extra effort option forfeits exactly as a maneuver does`() {
        val effort = ContentLoader.extraEffort().filter { ActiveDefenses.FORFEIT_PHRASE in it.effect }
        assertEquals(listOf("Feverish Defense — Variant Rule"), effort.map { it.option })
        assertTrue(effort.single().option in ActiveDefenses.forfeitingActions())
    }

    /**
     * **The forfeit cites the sentence that covers all five rows, not p. 37's.**
     *
     * Broadening the chip to "Forfeited last turn" left it quoting "unless you
     * took an All-Out Attack maneuver" at a GM whose fighter spent 1 FP on a
     * Feverish Defense -- a citation that does not cover their case (Codex).
     * The tables' own phrase governs; p. 37's narrower printing is shown
     * beside it with its scope stated, because 4.1 records a disagreement
     * rather than smoothing it.
     */
    @Test
    fun `the forfeit cites the rule that covers every forfeiting row`() {
        val state = ActiveDefenses.state(setOf(DefenseRefusal.ALL_OUT_ATTACK), none)
        assertEquals(ActiveDefenses.FORFEIT_PHRASE, state.printed)
        assertTrue(state.narrower!!.contains("unless you took an All-Out Attack maneuver"))

        // The other two print one rule each, so they carry no narrower version.
        for (cause in DefenseRefusal.entries - DefenseRefusal.ALL_OUT_ATTACK) {
            assertNull(ActiveDefenses.state(setOf(cause), none).narrower, "$cause")
        }
    }

    /**
     * **Two rules print "+2 to one defence" and neither was applied.**
     *
     * The extra-effort table: "Pay 1 FP: +2 to one defense roll. Cannot
     * defend next turn." The maneuver table: "Pay 2 FP. +2 to one active
     * defense until next turn. Cannot attack." Found by the sentence rather
     * than the table -- the forfeit's lesson one row over (6bh).
     *
     * NOT merged: the book prices them differently in both directions, so
     * which one was paid is a real choice. The costs come from the committed
     * content, never from constants here.
     */
    @Test
    fun `both boosts are printed rows and each costs what its own table says`() {
        assertEquals(1, ActiveDefenses.fpCostOf(DefenseBoost.FEVERISH))
        assertEquals(2, ActiveDefenses.fpCostOf(DefenseBoost.ALL_OUT_INCREASED))
        // Each is read from the table it lives in, and both still exist.
        assertTrue(
            ContentLoader.extraEffort().any { it.option == DefenseBoost.FEVERISH.printed },
        )
        assertTrue(
            ContentLoader.maneuvers().any { it.maneuver == DefenseBoost.ALL_OUT_INCREASED.printed },
        )
    }

    /**
     * **"One" defence, not all of them.**
     *
     * Both sentences say "one" where Combat Reflexes says "+1 to ALL active
     * defenses" and a shield's DB "adds to all active defenses". Applying
     * this like those would buy +2 on Dodge, every Parry and Block at once
     * for 1 FP. A reading, so the sentences are pinned rather than recalled.
     */
    @Test
    fun `each boost is worth two on ONE defence and says so`() {
        assertEquals(2, ActiveDefenses.BOOST_BONUS)
        for (boost in DefenseBoost.entries) {
            val printed = ActiveDefenses.printedBoost(boost)
            assertTrue(printed.contains("+2 to one"), "$boost no longer says '+2 to one': $printed")
        }
        // ...and the contrast that makes "one" load-bearing is still printed.
        assertTrue(
            ContentLoader.advantages()
                .first { it.name == TraitEffects.COMBAT_REFLEXES }
                .notes.contains("all active defenses"),
        )
    }

    /**
     * Each boost's own drawback is still printed, and they differ.
     *
     * Feverish costs the NEXT turn's defence -- which is why it is one of the
     * five rows [ActiveDefenses.forfeitingActions] returns -- and All-Out
     * Defense costs this turn's attack instead. If the two ever printed the
     * same drawback, the reason for keeping them apart would have to be made
     * again rather than inherited.
     */
    @Test
    fun `the two boosts are priced differently in both directions`() {
        val feverish = ActiveDefenses.printedBoost(DefenseBoost.FEVERISH)
        val allOut = ActiveDefenses.printedBoost(DefenseBoost.ALL_OUT_INCREASED)
        assertTrue(feverish.contains(ActiveDefenses.FORFEIT_PHRASE), feverish)
        assertTrue(allOut.contains("Cannot attack"), allOut)
        assertTrue(!allOut.contains(ActiveDefenses.FORFEIT_PHRASE), allOut)
        assertTrue(DefenseBoost.FEVERISH.printed in ActiveDefenses.forfeitingActions())
        assertTrue(DefenseBoost.ALL_OUT_INCREASED.printed !in ActiveDefenses.forfeitingActions())
    }

    /**
     * The stun penalty reaches every defence, and composes with the trait.
     *
     * "-4 to Active Defense" and "+1 to all active defenses" are both
     * unconditional, so one function answers for all seven places the screen
     * renders a defence -- and the two compose rather than one replacing the
     * other. A stunned fighter with Combat Reflexes is at -3, which is the
     * number that would be wrong if either rule were applied alone.
     */
    @Test
    fun `being stunned costs every defence four, trait or no trait`() {
        assertEquals(0, ActiveDefenses.everyDefence(emptyList(), stunned = false))
        assertEquals(-4, ActiveDefenses.everyDefence(emptyList(), stunned = true))
        assertEquals(1, ActiveDefenses.everyDefence(reflexes, stunned = false))
        assertEquals(-3, ActiveDefenses.everyDefence(reflexes, stunned = true))
    }

    /**
     * Combat Reflexes does NOT cancel the stun, and its own clause says why.
     *
     * It prints "+6 to recover from stun" -- the RECOVERY roll, which needs a
     * clock this app does not hold (6bf) -- not an exemption from the penalty
     * while stunned. It cancels [DefenseRefusal.SURPRISE] and nothing else, so
     * extending it here would author a rule from a resemblance (6bg).
     */
    @Test
    fun `combat reflexes cancels the surprise and not the stun`() {
        assertTrue(ActiveDefenses.state(setOf(DefenseRefusal.SURPRISE), reflexes).defends)
        assertEquals(-3, ActiveDefenses.everyDefence(reflexes, stunned = true))
        val printed = ContentLoader.advantages()
            .first { it.name == TraitEffects.COMBAT_REFLEXES }.notes
        assertTrue(printed.contains("recover from stun"), printed)
    }

    /**
     * The cell is quoted whole, and it still prints the number Kotlin holds.
     *
     * Its second clause is an FP rest with no clock to run it against, so the
     * screen shows both and applies one. Cutting the sentence at render time
     * would be a parser nobody tests (6f).
     */
    @Test
    fun `the stun sentence is the book's own cell`() {
        val printed = ActiveDefenses.printedStun()
        assertEquals("If Stunned: -4 to Active Defense. Otherwise rest to recover FP.", printed)
        assertTrue(printed.contains("${ActiveDefenses.STUNNED_PENALTY} to Active Defense"), printed)
    }

    /**
     * Nothing is REPORTED as unapplied that the engine actually applies.
     *
     * Found on device: the sheet read "Combat Reflexes — never freeze in
     * surprise / Not applied here: this app models no surprise", two taps
     * from a dialog that was cancelling the surprise refusal with that exact
     * trait. 6bf wrote the disclaimer when it was true and 6bg shipped the
     * declaration a slice later; a report of what is not applied expires the
     * moment the code changes (6ba), and only a test that reads BOTH objects
     * can notice.
     */
    @Test
    fun `a clause the engine applies is not reported as unapplied`() {
        val notes = TraitEffects.reported(reflexes).map { it.printed }
        if (ActiveDefenses.state(setOf(DefenseRefusal.SURPRISE), reflexes).defends) {
            assertTrue(notes.none { it.contains("surprise", ignoreCase = true) }, "$notes")
        }
        // The stun clause is still reported, and correctly: the trait's own
        // words are "+6 to RECOVER from stun", which is the roll this app has
        // no clock for -- not the -4 it now applies while stunned.
        assertTrue(notes.any { it.contains("recover from stun") }, "$notes")
    }

    /** Every cause the enum names is a rule the book prints, and the reverse. */
    @Test
    fun `every cause is backed by an extracted sentence`() {
        val extracted = ContentLoader.defenseRefusals().map { it.cause }.toSet()
        assertEquals(DefenseRefusal.entries.map { it.name }.toSet(), extracted)
    }

    /**
     * A cause with no printed sentence is refused rather than defaulted.
     *
     * Answering with an empty string would let a content drop present as a
     * refusal nobody can read -- the refusal-over-a-plausible-answer call
     * `Detection.attributeFor` already needed (6ah).
     */
    @Test
    fun `a cause the content does not print is refused`() {
        assertThrows<IllegalArgumentException> { ActiveDefenses.printedFor("NOT_A_CAUSE") }
    }
}
