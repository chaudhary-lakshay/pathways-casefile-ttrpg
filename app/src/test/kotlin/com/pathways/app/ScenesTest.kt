package com.pathways.app

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * One scene per character, kept across a trip to the gallery (play session
 * 7: a Retreat, a stun and a lit phobia went when the GM switched PCs).
 */
class ScenesTest {

    @Test
    fun `a character opened again gets the scene it left`() {
        val scenes = Scenes()
        val brann = scenes.of("Brann")
        brann.stunned.value = true
        scenes.of("Mira")
        assertSame(brann, scenes.of("Brann"))
        assertTrue(scenes.of("Brann").stunned.value)
    }

    @Test
    fun `each character holds its own scene`() {
        val scenes = Scenes()
        scenes.of("Brann").stunned.value = true
        assertFalse(scenes.of("Mira").stunned.value)
    }

    @Test
    fun `a forgotten character starts a clean scene`() {
        val scenes = Scenes()
        scenes.of("Brann").stunned.value = true
        scenes.forget("Brann")
        assertFalse(scenes.of("Brann").stunned.value)
    }
}
