package com.pathways.app

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A turn of the tablet must not recreate the activity (6fv). Every screen's
 * state is plain `remember`, so a recreated activity returns to the
 * character list with the open dialog and its roll gone; on the SM-T570 one
 * rotation took an open sheet back to the list. The manifest handles the
 * change instead, and this pins the line that does it: no test here can
 * rotate a device, so the proof of the behaviour is the device pass.
 */
class ManifestTest {

    @Test
    fun `the activity handles a rotation itself`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val handled = Regex("""android:configChanges="([^"]*)"""").find(manifest)
            ?.groupValues?.get(1)?.split('|')?.toSet().orEmpty()
        // `orientation` alone does nothing: a turn changes the screen size too.
        assertEquals(setOf("orientation", "screenSize"), handled intersect setOf("orientation", "screenSize"))
    }
}
