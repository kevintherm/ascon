package com.ascon.engine.adblock

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Messages from `cosmetic.js`. */
@Serializable
internal sealed interface CosmeticMessage {
    val url: String

    /** A page started and asks what it hides. */
    @Serializable
    @SerialName("page")
    data class Page(override val url: String) : CosmeticMessage

    /** Class and id names seen on the page, for generic rules. */
    @Serializable
    @SerialName("names")
    data class Names(
        override val url: String,
        val classes: List<String> = emptyList(),
        val ids: List<String> = emptyList(),
        val exceptions: List<String> = emptyList()
    ) : CosmeticMessage
}

/** The reply: selectors to hide, and for a [CosmeticMessage.Page], how to treat generic rules. */
@Serializable
internal data class CosmeticReply(
    val hide: List<String>,
    val page: Boolean = false,
    val generichide: Boolean = false,
    val exceptions: List<String> = emptyList()
)

internal object CosmeticProtocol {
    /** The script batches names under this, so a bigger message is not from it. */
    private const val MAX_NAMES = 500
    private const val MAX_NAME_LENGTH = 100

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    /** The message, or null if a page sent something the script never would. */
    fun decode(text: String): CosmeticMessage? {
        val message = try {
            json.decodeFromString<CosmeticMessage>(text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        return if (message is CosmeticMessage.Names && !message.withinLimits()) null else message
    }

    private fun CosmeticMessage.Names.withinLimits(): Boolean =
        classes.size + ids.size <= MAX_NAMES && (classes + ids).all { it.length <= MAX_NAME_LENGTH }

    fun encode(reply: CosmeticReply): String = json.encodeToString(reply)
}
