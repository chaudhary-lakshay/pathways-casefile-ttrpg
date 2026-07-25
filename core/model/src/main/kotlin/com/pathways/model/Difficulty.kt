package com.pathways.model

import kotlinx.serialization.Serializable

@Serializable
enum class Difficulty(val code: String, val firstRungOffset: Int, val defaultPenalty: Int?) {
    EASY("E", 0, -4),
    AVERAGE("A", -1, -5),
    HARD("H", -2, -6),
    /** Very Hard skills have no untrained default — Ch. 2, p. 10. */
    VERY_HARD("VH", -3, null);

    companion object {
        fun fromCode(code: String): Difficulty =
            entries.firstOrNull { it.code == code.trim().uppercase() }
                ?: throw IllegalArgumentException("unknown difficulty code '$code'")
    }
}
