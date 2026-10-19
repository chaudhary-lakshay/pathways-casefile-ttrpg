package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SealedArtifactGrade

/**
 * Ch. 6's Sealed Artifact grade table -- the classification of danger and
 * church access. v6.9i prints no individual artifact any more, only the grades.
 *
 * **A grade is a church access rule and a danger rating, never a usage
 * requirement.** The table's fourth column is "Church Access Rules" and every
 * cell in it names an institutional RANK. Its third column, "Power
 * Equivalent", says how strong an ARTIFACT of that grade is, and the chapter
 * requires a Sequence of a wielder nowhere. So there is deliberately no
 * `canUse` here: the rows are quoted.
 */
object SealedArtifacts {

    /** The four grades, most accessible first. */
    fun grades(): List<SealedArtifactGrade> = ContentLoader.sealedArtifactGrades()
}
