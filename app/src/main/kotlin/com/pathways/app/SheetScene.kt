package com.pathways.app

import androidx.compose.runtime.mutableStateOf

/**
 * What the table has declared about one character this scene, held by [App]
 * in [Scenes] for as long as the app runs: until play session 7 it lived only
 * while that character was selected, so a GM switching PCs mid-round lost a
 * Retreat, a stun and a lit phobia.
 *
 * Each of these was a `remember` inside the screen or dialog that shows it,
 * and so lived only while that composable did. Play session 1 found both
 * halves: opening the builder tore the sheet down and unlit a declared phobia
 * (the next roll lost its -1, and the tab jumped back to Skills), and closing
 * Defend forgot a Retreat, which the book allows "once per round", and a
 * paid +2 whose FP had already been charged.
 *
 * Still in memory, never in the session file: a scene's state is not a
 * purchase, and `SessionStore.load` drops the whole file on a decode failure.
 */
internal class SheetScene {
    val tab = mutableStateOf(SheetTab.SKILLS)
    /** The Major Wound's shock, latched by Damage, cleared by the GM. */
    val shocked = mutableStateOf(false)
    /** The held rows' "-N to all rolls" the GM says are in force (6gs). */
    val declaredStates = mutableStateOf(emptySet<String>())
    /** Defend's round: what p. 36's two per-round sentences govern. */
    val round = mutableStateOf(RoundState())
    /** Defend's Stunned: its own clock, not the round's (see [RoundState]). */
    val stunned = mutableStateOf(false)
}

/** Each character's [SheetScene], kept across trips to the gallery, as [RollLog] keeps throws. */
internal class Scenes {
    private val scenes = mutableMapOf<String, SheetScene>()

    fun of(character: String): SheetScene = scenes.getOrPut(character, ::SheetScene)

    /** Reset to sheet and Delete start the character's scene clean. */
    fun forget(character: String) {
        scenes.remove(character)
    }
}
