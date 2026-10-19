package com.pathways.rules

import com.pathways.rules.content.ContentLoader
import com.pathways.rules.content.SpiritualSense

/**
 * Ch. 4's two universal Beyonder abilities, Spiritual Intuition and
 * Spiritual Perception.
 *
 * "Roll 3d6. The difficulty class is based on however much current SPI you
 * have -- if you have 1 SPI, you need a critical roll (3-4); if you have 9
 * SPI, you need 9 or lower." So the target is the pool's balance, and
 * [SuccessRoll]'s always-succeed 3-4 is what makes 1 SPI need a critical.
 * The roll itself is [AttributeRoll.Kind.INTUITION] and
 * [AttributeRoll.Kind.PERCEPTION]. Neither prints a result table.
 */
object SpiritualSenses {

    /** Both, in printed order. */
    fun all(): List<SpiritualSense> = ContentLoader.spiritualSenses()

    /**
     * Whether the senses can be rolled: "available to any Beyonder", and
     * "0 SPI -- Spiritual Exhaustion: All Beyonder abilities deactivate".
     */
    fun isOffered(isBeyonder: Boolean, currentSpi: Int): Boolean = isBeyonder && currentSpi >= 1
}
