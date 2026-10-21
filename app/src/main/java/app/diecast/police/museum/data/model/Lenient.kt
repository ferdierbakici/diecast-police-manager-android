package app.diecast.police.museum.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Postgres "int" kolonlari bazi satirlarda JSON sayi, bazi satirlarda metin olarak gelebilir.
 * Bu serilestiriciler her ikisini de kabul eder; cozumleme hatasinda tum sayfa cokmez.
 */
object LenientInt : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeInt()
        return when (val el = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> el.content.trim().toIntOrNull()
                ?: el.content.trim().toDoubleOrNull()?.toInt()
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}

object LenientDouble : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        return when (val el = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> el.content.trim().toDoubleOrNull()
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

object LenientBool : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientBool", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        return when (val el = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> when (el.content.trim().lowercase()) {
                "true", "1", "yes", "t" -> true
                "false", "0", "no", "f" -> false
                else -> null
            }
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean?) {
        if (value == null) encoder.encodeNull() else encoder.encodeBoolean(value)
    }
}

object LenientString : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val el = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> el.content
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}
