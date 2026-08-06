package com.pathways.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Ch. 6's Sealed Artifacts (p. 47-53), and the column that is not a gate. */
class SealedArtifactsTest {

    private val gauntlet = SealedArtifacts.named("Gauntlet of the False Baron")
    private val coin = SealedArtifacts.named("Echoing Coin of the Serpent")
    private val candle = SealedArtifacts.named("Candle of Shared Slumber")
    private val flagon = SealedArtifacts.named("The Peerless Flagon")

    // ---- the warrant -----------------------------------------------------

    /**
     * **The whole basis for joining the two tables.** Each artifact's header
     * prints its own "Seq N equivalent" and the grade table prints a band per
     * grade; all four land inside their own. Four independent confirmations are
     * what license reading a grade's power column as describing the artifact --
     * the per-table standard the appendix's matching "Pi" had to meet (6v), not
     * "the chapter is reliable".
     */
    @Test
    fun `every artifact confirms its own grade's printed band`() {
        assertEquals(4, SealedArtifacts.all().size)
        for (artifact in SealedArtifacts.all()) {
            assertTrue(SealedArtifacts.agreesWithGrade(artifact)) {
                "${artifact.name} is Seq ${artifact.sequenceEquivalent}, outside " +
                    "Grade ${artifact.grade}'s band"
            }
        }
        assertEquals(7, gauntlet.sequenceEquivalent)
        assertEquals(2, gauntlet.grade)
        assertEquals(5, SealedArtifacts.gradeOf(gauntlet).sequenceLow)
        assertEquals(7, SealedArtifacts.gradeOf(gauntlet).sequenceHigh)
    }

    /**
     * The grade is a CHURCH ACCESS rule: every cell names a rank, and none
     * names a Sequence the wielder must have reached. That is why there is no
     * `canUse` on the object -- see the next test.
     */
    @Test
    fun `every grade gates on institutional rank, not on a sequence`() {
        val grades = SealedArtifacts.grades()
        assertEquals(listOf(3, 2, 1, 0), grades.map { it.grade })
        for (grade in grades) {
            assertTrue(
                Regex("Nighthawk|Bishop|confidential").containsMatchIn(grade.churchAccess),
            ) { "Grade ${grade.grade} access names no rank: ${grade.churchAccess}" }
        }
    }

    /**
     * **The refusal this slice is about.** "Power Equivalent (Seq 5-7)" is how
     * strong the artifact is, not what the wielder must be. Enforced, it would
     * lock a Sequence 9 character out of an item the book says is found on a
     * dead Beyonder. The object answers with the printed cell and takes no
     * character to test it against -- [SealedArtifacts.powerEquivalentOf] has
     * no parameter a wielder could be passed as.
     */
    @Test
    fun `power equivalent is quoted, and nothing enforces it`() {
        assertEquals(
            "Mid-Sequence Beyonder (Seq 5–7)",
            SealedArtifacts.powerEquivalentOf(gauntlet),
        )
        assertEquals(
            "Low-Sequence Beyonder (Seq 8–9)",
            SealedArtifacts.powerEquivalentOf(coin),
        )
        // A Sequence 9 mortal and a Sequence 6 Beyonder get the same answer,
        // because the printed cell describes the artifact and not the wielder.
        assertEquals("Saint (Seq 3–4)", SealedArtifacts.powerEquivalentOf(flagon))
    }

    // ---- the costs the app spends ----------------------------------------

    @Test
    fun `every ability prints an spi cost`() {
        assertEquals(13, SealedArtifacts.all().sumOf { it.abilities.size })
        for (artifact in SealedArtifacts.all()) {
            for (ability in artifact.abilities) {
                assertTrue(ability.spi > 0) { "${artifact.name}/${ability.name}" }
                assertTrue(ability.effect.isNotBlank())
            }
        }
    }

    /**
     * The gauntlet's Soul's Price is levied on "any ability", so it composes
     * with the ability's own SPI. Nothing else on that artifact charges HP.
     */
    @Test
    fun `the gauntlet charges one hp for any ability`() {
        assertEquals(1, gauntlet.hpPerUse)
        val weaken = SealedArtifacts.abilityNamed(gauntlet, "Bribe — Weaken")
        assertEquals(
            ArtifactCost(spi = 1, hp = 1, perTurn = false),
            SealedArtifacts.costOf(gauntlet, weaken),
        )
    }

    /**
     * Extinguish Memory charges HP in its OWN effect cell, on an artifact with
     * no per-use charge. The two rules are separate and [SealedArtifacts.costOf]
     * adds them, so an artifact that ever prints both is not charged once.
     */
    @Test
    fun `the candle's own ability charges hp without a per-use rule`() {
        assertNull(candle.hpPerUse)
        val extinguish = SealedArtifacts.abilityNamed(candle, "Extinguish Memory")
        assertEquals(1, extinguish.hpCost)
        assertEquals(
            ArtifactCost(spi = 3, hp = 1, perTurn = false),
            SealedArtifacts.costOf(candle, extinguish),
        )

        val slumber = SealedArtifacts.abilityNamed(candle, "Slumber")
        assertNull(slumber.hpCost)
        assertEquals(0, SealedArtifacts.costOf(candle, slumber).hp)
    }

    /**
     * **Three of the four artifacts print an HP number in their drawbacks and
     * only one is charged.** The flagon's is conditional on telling a lie, the
     * coin's on three uses in a week, the candle's Burns True on touching the
     * flame. A loose "1 HP" rule would charge all of them. This pins that the
     * uncharged prose is still THERE, so the narrow reading stays a decision.
     */
    @Test
    fun `an hp number in a drawback is not a cost unless it is per use`() {
        assertNull(flagon.hpPerUse)
        assertNull(coin.hpPerUse)
        assertTrue(flagon.drawbacks.any { it.contains("1 HP burn to the hand") })
        assertTrue(coin.drawbacks.any { it.contains("loses 1 HP permanently") })
        assertTrue(candle.drawbacks.any { it.contains("1d6 burning damage") })

        for (ability in flagon.abilities + coin.abilities) {
            assertNull(ability.hpCost) { ability.name }
        }
        assertEquals(
            0,
            SealedArtifacts.costOf(
                flagon,
                SealedArtifacts.abilityNamed(flagon, "Bottled Dawn"),
            ).hp,
        )
    }

    /**
     * False Majesty's cell reads "1 SPI/turn" -- maintained, not paid once. A
     * caller that spends it a single time has underpaid, so the distinction
     * travels with the cost rather than being left in the effect prose.
     */
    @Test
    fun `a maintained ability is marked per turn`() {
        val majesty = SealedArtifacts.abilityNamed(gauntlet, "False Majesty")
        assertTrue(SealedArtifacts.costOf(gauntlet, majesty).perTurn)
        assertEquals(1, SealedArtifacts.costOf(gauntlet, majesty).spi)
        assertEquals(1, gauntlet.abilities.count { it.perTurn })
        assertFalse(
            SealedArtifacts.costOf(
                coin,
                SealedArtifacts.abilityNamed(coin, "Duplicate"),
            ).perTurn,
        )
    }

    // ---- refusals ---------------------------------------------------------

    @Test
    fun `an ability from another artifact is refused`() {
        val slumber = SealedArtifacts.abilityNamed(candle, "Slumber")
        assertThrows<IllegalArgumentException> { SealedArtifacts.costOf(gauntlet, slumber) }
    }

    @Test
    fun `an artifact or ability the book never prints is refused`() {
        assertThrows<IllegalStateException> { SealedArtifacts.named("Hat of Holding") }
        assertThrows<IllegalStateException> { SealedArtifacts.abilityNamed(coin, "Fly") }
    }

    /** Every artifact keeps the three prose sections the GM reads. */
    @Test
    fun `each artifact carries its appearance, drawbacks and sealing`() {
        for (artifact in SealedArtifacts.all()) {
            assertTrue(artifact.appearance.isNotBlank()) { artifact.name }
            assertEquals(3, artifact.drawbacks.size) { artifact.name }
            assertTrue(artifact.sealing.isNotBlank()) { artifact.name }
            assertTrue(artifact.pathway.isNotBlank()) { artifact.name }
            assertTrue(artifact.code.contains("-")) { artifact.code }
        }
    }
}
