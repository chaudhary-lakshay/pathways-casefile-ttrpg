package com.pathways.rules.content

import com.pathways.model.ControllingAttribute
import com.pathways.model.Difficulty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The book's skill list writes controlling attribute as a bare short code
 * ("DX", "Per", "SPI", "Will", ...) and [ControllingAttribute] itself is a
 * polymorphic sealed interface (it needs its own `{"type": ...}` envelope
 * under kotlinx's default derived serializer). This use-site serializer
 * bridges the two: read/write the plain code, resolve through
 * [ControllingAttribute.fromCode] exactly like [com.pathways.rules.Premades]
 * already does by hand.
 */
internal object ControllingAttributeCodeSerializer : KSerializer<ControllingAttribute> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ControllingAttributeCode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ControllingAttribute) {
        encoder.encodeString(value.code)
    }

    override fun deserialize(decoder: Decoder): ControllingAttribute =
        ControllingAttribute.fromCode(decoder.decodeString())
}

/**
 * The book's skill list writes difficulty as the short code ("E"/"A"/"H"/"VH"),
 * matching [Difficulty.code] -- not the enum entry name kotlinx's default
 * derived serializer expects ("EASY"/"AVERAGE"/...). Same bridging pattern
 * as [ControllingAttributeCodeSerializer].
 */
internal object DifficultyCodeSerializer : KSerializer<Difficulty> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DifficultyCode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Difficulty) {
        encoder.encodeString(value.code)
    }

    override fun deserialize(decoder: Decoder): Difficulty =
        Difficulty.fromCode(decoder.decodeString())
}

@Serializable
data class ContentFile<T>(
    val sourceLines: Map<String, List<List<Int>>> = emptyMap(),
    val entries: List<T>,
)
