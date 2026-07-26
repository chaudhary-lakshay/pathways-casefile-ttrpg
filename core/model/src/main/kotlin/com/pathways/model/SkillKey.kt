package com.pathways.model

import kotlinx.serialization.Serializable

/**
 * A skill's identity. All four parts matter: the sheets carry
 * "History (Occult)/IQ" and "Guns (Pistol)/DX", and the potion-grant rule
 * (spec 4.3) keys on an exact name-and-difficulty match.
 */
@Serializable
data class SkillKey(
    val name: String,
    val specialization: String? = null,
    val attr: ControllingAttribute,
    val difficulty: Difficulty,
) {
    val display: String
        get() = buildString {
            append(name)
            specialization?.let { append(" (").append(it).append(')') }
            append('/').append(attr.code)
        }
}
