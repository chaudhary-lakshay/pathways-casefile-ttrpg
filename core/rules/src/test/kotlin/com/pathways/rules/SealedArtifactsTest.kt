package com.pathways.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Ch. 6's Sealed Artifact grades, and the column that is not a gate. */
class SealedArtifactsTest {

    /**
     * The grade is a CHURCH ACCESS rule: every cell names a rank, and none
     * names a Sequence the wielder must have reached. That is why there is no
     * `canUse` on the object.
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
}
