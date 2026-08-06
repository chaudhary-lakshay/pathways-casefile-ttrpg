package com.pathways.rules

import com.pathways.rules.content.ArtifactAbility
import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SealedArtifact
import com.pathways.rules.content.SealedArtifactGrade

/** What using one artifact ability costs, in the two pools the app tracks. */
data class ArtifactCost(val spi: Int, val hp: Int, val perTurn: Boolean)

/**
 * Ch. 6's Sealed Artifacts (p. 47-53) -- the four documented items and the
 * grade table that classifies them.
 *
 * **A grade is a church access rule and a danger rating, never a usage
 * requirement.** The table's fourth column is "Church Access Rules" and every
 * cell in it names an institutional RANK -- "Formal Nighthawk members and
 * above", "Diocesan Bishop or Nighthawk deacon and above". Its third column,
 * "Power Equivalent (Seq 5-7)", says how strong the ARTIFACT is. Enforced as a
 * requirement it would lock a Sequence 9 character out of a gauntlet the book
 * says is found on the body of an out-of-control Beyonder, and the chapter
 * requires a Sequence of a wielder nowhere. So there is deliberately no
 * `canUse` here: [gradeOf] and [powerEquivalentOf] answer, and the screen
 * quotes them. Same shape as a wealth level's reaction modifier, which is shown
 * beside the choice and never applied to a roll this book does not print.
 *
 * The two tables are joined by grade NUMBER, which is warranted because each
 * artifact's own header prints a "Seq N equivalent" that lands inside its
 * grade's printed band -- four independent confirmations, pinned in
 * `extract_artifacts.verify()` and again in `SealedArtifactsTest`.
 *
 * **What the app spends is [costOf] and nothing else.** Every ability prints an
 * SPI cost; one artifact charges HP for any use of any ability, and one ability
 * charges HP in its own effect cell. The other three artifacts also print HP
 * numbers in their drawbacks -- conditional on telling a lie, on touching the
 * flame, on a use count -- and those are the GM's to apply. The 13 effects
 * themselves are unique prose (duplicate an object, walk through a wall, erase
 * a memory); there is no formula to implement, so they are quoted.
 */
object SealedArtifacts {

    /** Every documented artifact, in printed order. */
    fun all(): List<SealedArtifact> = ContentLoader.sealedArtifacts()

    /** The four grades, most accessible first. */
    fun grades(): List<SealedArtifactGrade> = ContentLoader.sealedArtifactGrades()

    fun named(name: String): SealedArtifact =
        all().firstOrNull { it.name == name }
            ?: error("the book documents no Sealed Artifact called '$name'")

    /**
     * The grade row for an artifact.
     *
     * Throws for a grade the table does not print, rather than answering null:
     * an artifact whose grade has no row is a parse failure, not a silent cell.
     * The same distinction [Wounds.multiplierFor] draws between an unlisted
     * damage type and a key that is not a damage type at all.
     */
    fun gradeOf(artifact: SealedArtifact): SealedArtifactGrade =
        grades().firstOrNull { it.grade == artifact.grade }
            ?: error(
                "${artifact.name} is Grade ${artifact.grade}, which the grade " +
                    "table does not print"
            )

    /** The printed "Power Equivalent" cell -- how strong the ARTIFACT is. */
    fun powerEquivalentOf(artifact: SealedArtifact): String =
        gradeOf(artifact).powerEquivalent

    /**
     * True when an artifact's own printed sequence equivalent falls inside its
     * grade's printed band.
     *
     * This is not a gate on anything -- it is the agreement that licenses
     * joining the two tables at all, exposed so a test can assert it in Kotlin
     * as well as in the extractor. All four artifacts satisfy it today; the day
     * one stops, the join needs rereading rather than reusing.
     */
    fun agreesWithGrade(artifact: SealedArtifact): Boolean {
        val band = gradeOf(artifact)
        return artifact.sequenceEquivalent in band.sequenceLow..band.sequenceHigh
    }

    fun abilityNamed(artifact: SealedArtifact, name: String): ArtifactAbility =
        artifact.abilities.firstOrNull { it.name == name }
            ?: error("${artifact.name} has no ability called '$name'")

    /**
     * What one use of [ability] costs its wielder.
     *
     * SPI is the ability's printed cell. HP is the ability's own charge plus the
     * artifact's per-use charge, because they are two different rules that can
     * both apply: the candle's Extinguish Memory burns the fingers that pinch
     * the flame, and the gauntlet's Soul's Price is levied on "any ability".
     * No artifact prints both today, so composing them is what stops the next
     * one that does from being charged only once.
     *
     * [ArtifactCost.perTurn] carries the gauntlet's False Majesty forward: its
     * cell reads "1 SPI/turn", so the cost is per turn maintained, not a single
     * payment, and a caller that spends it once has underpaid.
     */
    fun costOf(artifact: SealedArtifact, ability: ArtifactAbility): ArtifactCost {
        require(artifact.abilities.any { it.name == ability.name }) {
            "'${ability.name}' is not an ability of ${artifact.name}"
        }
        return ArtifactCost(
            spi = ability.spi,
            hp = (ability.hpCost ?: 0) + (artifact.hpPerUse ?: 0),
            perTurn = ability.perTurn,
        )
    }
}
