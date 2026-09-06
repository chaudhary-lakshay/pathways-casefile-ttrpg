package com.pathways.rules

import com.pathways.model.Outcome
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
        assertEquals(0, ActiveDefenses.everyDefence(emptyList(), stunned = false, poolPenalty = 0))
        assertEquals(-4, ActiveDefenses.everyDefence(emptyList(), stunned = true, poolPenalty = 0))
        assertEquals(1, ActiveDefenses.everyDefence(reflexes, stunned = false, poolPenalty = 0))
        assertEquals(-3, ActiveDefenses.everyDefence(reflexes, stunned = true, poolPenalty = 0))
    }

    /**
     * The pool penalties reach every defence, on the phrase Shock already
     * reads at its word.
     *
     * p. 40's "Reeling: -1 to all rolls" and p. 42's "-1 to all rolls" are the
     * same unqualified wording the Major Wound's success row prints, and
     * [Shock] takes that literally for [RollKind.ACTIVE_DEFENSE]. They arrive
     * as one number rather than as a fourth term computed here, because
     * [Conditions] is where a pool's penalty is decided and a second site
     * deciding it is the drift this codebase has produced three times.
     */
    @Test
    fun `a reeling and fatigued defender is at minus two on every row`() {
        val penalty = Conditions.penaltyFor(RollKind.ACTIVE_DEFENSE, Pools.hp(3, 10), Pools.fp(3, 12))
        assertEquals(-2, penalty)
        assertEquals(-2, ActiveDefenses.everyDefence(emptyList(), stunned = false, poolPenalty = penalty))
        // Stunned, shocked, reeling and fatigued at once: four rules, four
        // sources, one number. Any of them applied alone gives a different one.
        assertEquals(
            -10,
            ActiveDefenses.everyDefence(emptyList(), stunned = true, shocked = true, poolPenalty = penalty),
        )
    }

    /**
     * The fatigue row's two clauses land in a printed ORDER this book does not
     * print, so the choice is recorded here.
     *
     * "At 1/3 FP or below: -1 to all rolls; Move and Dodge halved" (p. 42).
     * The halving is a property of the Dodge SCORE and the -1 is a property of
     * every ROLL, so the -1 lands where the stun's -4, shock's -4, Retreat's
     * +3 and a shield's DB already land: on top of the halved score. The
     * refused reading subtracts first and halves after, which at an odd base
     * returns a different number -- that is what makes this a decision rather
     * than a formatting detail.
     *
     * **What this pins, and what it does not.** It pins that the two readings
     * DIFFER and which one the numbers below are the app's; it composes them
     * by hand, so it would stay green if `MainActivity` were edited to halve
     * `vitals.dodge + poolPenalty` instead (a diff review's finding, taken).
     * What stops that is the boundary rather than this test: `DefenseDialog`
     * is handed a Dodge that is already halved and never sees the raw score,
     * so the dialog CANNOT compose the refused order -- only the one call site
     * that owns the halving could, and no test in this repo can reach a
     * composable to check it (6cq).
     */
    @Test
    fun `the fatigue penalty lands after the halving, not inside it`() {
        val tired = Pools.fp(3, 12)
        val penalty = Conditions.penaltyFor(RollKind.ACTIVE_DEFENSE, Pools.hp(10, 10), tired)
        val shipped = Pools.halvedForFatigue(8, tired) +
            ActiveDefenses.everyDefence(emptyList(), stunned = false, poolPenalty = penalty)
        assertEquals(3, shipped)
        // ceil((8 - 1) / 2) = 4. Both readings apply both clauses; they differ.
        assertEquals(4, Pools.halvedForFatigue(8 + penalty, tired))
    }

    /**
     * The facing penalty stays out of [ActiveDefenses.everyDefence], and this
     * pins the WORDING that keeps it out rather than the decision.
     *
     * The stun reaches every row because a second printing says "active
     * defenseS"; the facing bullets say "to defense", singular, once each. This
     * repo has one bar for that composition point and it is not lowered here.
     * If the book is ever re-read as plural this fails, and the refusal is
     * re-argued rather than quietly outliving its reason (6bl) -- the only way
     * a recorded "not applied" can expire on its own.
     */
    @Test
    fun `the facing bullets still print a singular defence, which is why they reach nothing`() {
        val facing = ContentLoader.defenseModifiers()
            .map { it.modifier }
            .filter { it.startsWith("Attacked from") }
        assertEquals(2, facing.size, "the facing bullets changed: $facing")
        for (bullet in facing) {
            assertTrue(bullet.endsWith("to defense"), "no longer singular: $bullet")
        }
    }

    /**
     * A shield's DB is withheld from behind and offered elsewhere.
     *
     * The three states are the book's, not a design: it grants the bonus "from
     * the front or shield side", which decides the front and the rear and
     * leaves the side open -- "shield side" names a hand this book never gives
     * a character. Answering the side either way would invent an arc (4.1).
     */
    @Test
    fun `the shield bonus follows the printed arc, and the side stays the GM's`() {
        assertEquals(ShieldCoverage.COUNTS, Shields.coverageAt(Facing.FRONT))
        assertEquals(ShieldCoverage.GM_DECIDES, Shields.coverageAt(Facing.SIDE))
        assertEquals(ShieldCoverage.WITHHELD, Shields.coverageAt(Facing.REAR))
        // The clause the withholding rests on is the book's own, not a
        // paraphrase -- and extract_shields.PINNED fails if the page loses it.
        assertEquals(
            "against attacks from the front or shield side",
            Shields.PRINTED_ARC,
        )
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
        assertEquals(-3, ActiveDefenses.everyDefence(reflexes, stunned = true, poolPenalty = 0))
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
        // The stun clause was reported here until 6dr, on the reason "this app
        // models no stun recovery roll to make" -- while `DamageDialog`
        // rendered the Major Wound row that prints one. [StunRecovery] is that
        // roll, so the disclaimer would now be the same defect in reverse: a
        // GM told a trait does nothing on a screen two taps from where it
        // moves the number. Same failure mode, opposite direction.
        assertTrue(notes.none { it.contains("recover from stun") }, "$notes")
        assertEquals(6, StunRecovery.bonusFor(reflexes) - StunRecovery.bonusFor(emptyList()))
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

    /**
     * The Acrobatic Dodge swings the same magnitude either way, and neither
     * way until it is rolled.
     *
     * A critical counts as its plain sibling: the bullet prints a success and
     * a failure and nothing else, so grading it further would invent a rule
     * the book does not print (4.1).
     *
     * The phrases asserted here are SIGN-ANCHORED. 6cg measured that a guard
     * reading `"$n Dodge"` is satisfied by a sign flip -- "2 Dodge" is a
     * substring of "-2 Dodge" -- so a check on the magnitude alone would pass
     * the cheaper mutation, which is the one this repo has already shipped
     * once (section 5, base -4 landing on +2).
     */
    @Test
    fun `the acrobatic dodge is worth two either way, and nothing unrolled`() {
        assertEquals(0, ActiveDefenses.acrobaticDodge(null))
        for (win in listOf(Outcome.SUCCESS, Outcome.CRITICAL_SUCCESS)) {
            assertEquals(ActiveDefenses.ACROBATIC_SWING, ActiveDefenses.acrobaticDodge(win))
        }
        for (loss in listOf(Outcome.FAILURE, Outcome.CRITICAL_FAILURE)) {
            assertEquals(-ActiveDefenses.ACROBATIC_SWING, ActiveDefenses.acrobaticDodge(loss))
        }
        val bullet = ActiveDefenses.printedAcrobaticDodge()
        assertTrue("Roll vs. ${ActiveDefenses.ACROBATIC_SKILL}" in bullet, bullet)
        assertTrue("success gives +${ActiveDefenses.ACROBATIC_SWING} Dodge" in bullet, bullet)
        assertTrue("failure gives -${ActiveDefenses.ACROBATIC_SWING} Dodge" in bullet, bullet)
        // The Dodge row adds this bullet's swing to the Retreat's +3, and the
        // warrant for doing so is printed in this cell rather than argued from
        // p. 36's general "alongside any active defense": the sentence names
        // the other bullet. If it stops being printed, that composition is back
        // to resting on the general line, and that is the finding.
        assertTrue("Stacks with Retreat." in bullet, bullet)
    }

    /**
     * The skill the bullet rolls against is a skill the book prints.
     *
     * The pin 6cg found missing runs JSON -> Kotlin: `extract_defenses.py`
     * pins the book's sentence into the content, and nothing pinned the
     * content to the constant the app looks the character's level up by. A
     * renamed row would otherwise leave the roll silently unofferable to every
     * character who has the skill.
     */
    @Test
    fun `the skill the bullet names is one the book prints`() {
        val printed = ContentLoader.skills().firstOrNull { it.name == ActiveDefenses.ACROBATIC_SKILL }
        assertTrue(printed != null, "skills.json no longer prints ${ActiveDefenses.ACROBATIC_SKILL}")
        assertTrue(ActiveDefenses.ACROBATIC_SKILL in ActiveDefenses.printedAcrobaticDodge())
    }

    /**
     * A bullet the app applies is not also listed as one it does not.
     *
     * The dialog printed all five as bare notes while all five were
     * display-only. One now reaches a number, and going on printing it beside
     * the four that do not is the disclaimer-outlives-its-rule defect 6bl
     * found on this same screen -- which only a test reading BOTH halves can
     * notice.
     *
     * The partition is asserted rather than the count: an added bullet must
     * land in exactly one half, and a test on "four" alone would pass a sixth
     * bullet quietly joining the wrong one.
     *
     * The last assertion is deliberately ASYMMETRIC and that is what gives it
     * teeth: `List.minus` drops the first match while `filterNot` drops every
     * one, so a second bullet carrying the same tag fails here -- and it would
     * otherwise vanish from the screen entirely, since the control quotes only
     * the first (a review's observation).
     */
    @Test
    fun `the applied bullets are not also reported as unapplied`() {
        val all = ContentLoader.defenseModifiers().map { it.modifier }
        val unapplied = ActiveDefenses.unappliedModifiers()
        val applied = listOf(
            ActiveDefenses.printedAcrobaticDodge(),
            ActiveDefenses.printedDive(),
            ActiveDefenses.printedRetreat(),
        )
        applied.forEach {
            assertTrue(it in all, "an applied bullet left the content: $it")
            assertTrue(it !in unapplied, "applied and reported unapplied: $it")
        }
        assertEquals(all - applied.toSet(), unapplied)
    }

    /**
     * The Dive! is worth three, and ONLY against a shot.
     *
     * Both scopes are asserted because the bullet prints both -- "+3 Dodge"
     * names the row and "vs. ranged only" names the attack -- and a build that
     * kept the number while losing the second condition would hand a fighter +3
     * against a sword, which is the whole content of the word "only".
     *
     * The four combinations are enumerated rather than sampled: `taken` and
     * `isRanged` are independent, and a `dive` that answered off either one
     * alone still passes any two of the four.
     */
    @Test
    fun `the dive is worth three against a shot and nothing against anything else`() {
        assertEquals(ActiveDefenses.DIVE_BONUS, ActiveDefenses.dive(taken = true, isRanged = true))
        assertEquals(0, ActiveDefenses.dive(taken = true, isRanged = false))
        assertEquals(0, ActiveDefenses.dive(taken = false, isRanged = true))
        assertEquals(0, ActiveDefenses.dive(taken = false, isRanged = false))
    }

    /**
     * The phrases the rule above depends on, still printed.
     *
     * SIGN-ANCHORED like the Acrobatic Dodge's, for the reason 6cg measured: a
     * guard reading `"$n Dodge"` is satisfied by a sign flip, because "3 Dodge"
     * is a substring of "-3 Dodge".
     *
     * The third clause is pinned too, and it is the one the app does NOT apply.
     * A cost that stops being printed while the bonus stays is exactly the
     * disagreement 4.1 exists to surface, and the note under the control quotes
     * the whole bullet on the strength of this assertion.
     */
    @Test
    fun `the dive bullet still prints both scopes and the turn it costs`() {
        val bullet = ActiveDefenses.printedDive()
        assertTrue("+${ActiveDefenses.DIVE_BONUS} Dodge" in bullet, bullet)
        assertTrue("vs. ranged only" in bullet, bullet)
        assertTrue("Spend 1 turn getting back up" in bullet, bullet)
    }

    /**
     * The Retreat is worth its own number on each row it names, and nothing at
     * all against a shot.
     *
     * Every row is enumerated against all four states rather than sampled, for
     * the reason the Dive!'s four combinations are: `taken` and `isMelee` are
     * independent, and an implementation answering off either one alone still
     * passes any two of the four. Running the loop over `entries` is what makes
     * a fourth row added later fail here rather than ship untested.
     *
     * The three figures are NOT asserted as literals -- they are pinned to the
     * printed sentence by the test below, which is the only oracle for them.
     * Restating 3 / 1 / 1 here would pin Kotlin to Kotlin and pass a build that
     * had drifted from the book (6cg's missing direction).
     */
    @Test
    fun `the retreat is worth its row's number in reach, and nothing against a shot`() {
        ActiveDefenses.Retreat.entries.forEach { row ->
            assertEquals(row.bonus, ActiveDefenses.retreat(taken = true, isMelee = true, row = row))
            assertEquals(0, ActiveDefenses.retreat(taken = true, isMelee = false, row = row))
            assertEquals(0, ActiveDefenses.retreat(taken = false, isMelee = true, row = row))
            assertEquals(0, ActiveDefenses.retreat(taken = false, isMelee = false, row = row))
        }
        // The rows carry three figures and not one repeated: a build folding
        // them into a single number passes every assertion above, since each
        // row is only ever compared against its own.
        assertTrue(
            ActiveDefenses.Retreat.DODGE.bonus != ActiveDefenses.Retreat.PARRY.bonus,
            "the Dodge and Parry figures collapsed into one",
        )
    }

    /**
     * The phrases the rule above depends on, still printed.
     *
     * SIGN-ANCHORED for the reason 6cg measured: "1 Parry" is a substring of
     * "-1 Parry", so a guard omitting the plus passes a flipped sign -- the
     * cheaper mutation, and the one this repo has already shipped once.
     *
     * Each row's phrase is built from the enum member that carries its number,
     * so the pin runs JSON -> Kotlin. Three separate assertions rather than one
     * over the whole bullet, because a build that gave every row the same
     * figure would still satisfy a check that only looked for "+3 Dodge".
     */
    @Test
    fun `the retreat bullet still prints all three rows and its melee scope`() {
        val bullet = ActiveDefenses.printedRetreat()
        assertTrue("+${ActiveDefenses.Retreat.DODGE.bonus} Dodge" in bullet, bullet)
        assertTrue("+${ActiveDefenses.Retreat.BLOCK.bonus} Block" in bullet, bullet)
        assertTrue("+${ActiveDefenses.Retreat.PARRY.bonus} Parry" in bullet, bullet)
        // The scope the rule composes, and the limit it deliberately does not.
        assertTrue("vs. 1 melee attack per round" in bullet, bullet)
    }
}
