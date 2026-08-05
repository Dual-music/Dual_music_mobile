package com.dualmusic.domain.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Booléen **tolérant** : accepte `true`/`false` MAIS AUSSI `0`/`1` (et `"0"`/`"1"`).
 *
 * Le backend MySQL stocke les booléens en `TINYINT(1)` et les renvoie souvent en `0`/`1`
 * (nombre), ce que le JS accepte mais pas kotlinx-serialization (strict) → sinon toute la
 * désérialisation d'une liste échoue. À appliquer via `@Serializable(with = ...)`.
 */
object FlexibleBoolSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleBool", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val jd = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val p = jd.decodeJsonElement() as? JsonPrimitive ?: return false
        p.booleanOrNull?.let { return it }
        p.intOrNull?.let { return it != 0 }
        return p.content.equals("true", ignoreCase = true) || p.content == "1"
    }

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}
