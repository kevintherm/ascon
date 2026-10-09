package com.ascon.engine.adblock

import java.net.URI
import java.net.URISyntaxException

/** A request's type, named as filter lists name it, such as `$image` or `$script`. */
enum class RequestType(val filterName: String) {
    Document("document"),
    Subdocument("subdocument"),
    Script("script"),
    Stylesheet("stylesheet"),
    Image("image"),
    Font("font"),
    Media("media"),
    Other("other");

    companion object {
        /**
         * WebView does not say what a request is for, so infer it from the main-frame flag,
         * the `Accept` header and the file extension, in that order.
         */
        fun of(url: String, isMainFrame: Boolean, accept: String?): RequestType {
            val fromAccept = accept?.lowercase()?.let { header ->
                when {
                    header.startsWith("text/html") -> Subdocument
                    header.startsWith("text/css") -> Stylesheet
                    header.startsWith("image/") -> Image
                    else -> null
                }
            }
            return when {
                isMainFrame -> Document
                fromAccept != null -> fromAccept
                else -> Extensions[extensionOf(url)] ?: Other
            }
        }

        private val Extensions = buildMap {
            listOf("js", "mjs").forEach { put(it, Script) }
            put("css", Stylesheet)
            listOf("png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico", "bmp").forEach { put(it, Image) }
            listOf("woff", "woff2", "ttf", "otf", "eot").forEach { put(it, Font) }
            listOf("mp4", "webm", "m3u8", "mp3", "ogg", "wav", "m4a").forEach { put(it, Media) }
            listOf("html", "htm").forEach { put(it, Subdocument) }
        }

        private fun extensionOf(url: String): String? {
            val path = try {
                URI(url).rawPath
            } catch (_: URISyntaxException) {
                null
            } ?: return null
            val name = path.substringAfterLast('/')
            return name.substringAfterLast('.', "").lowercase().ifEmpty { null }
        }
    }
}
