package com.ascon.engine.adblock

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hides page elements the filter lists name. `cosmetic.js` runs at document start, asks
 * for the page's own hide rules, then sends the class and id names it sees for generic
 * rules. Replies are worked out off the main thread and posted from [scope], the main one.
 */
class CosmeticFilter(
    private val adblock: Adblock,
    private val script: String,
    private val scope: CoroutineScope,
    /** False for a page that hides nothing, because blocking is off or the site is trusted. */
    private val filters: (pageUrl: String) -> Boolean = { true }
) {
    @SuppressLint("RequiresFeature") // Checked first.
    fun install(webView: WebView): Boolean {
        if (!isSupported()) return false
        WebViewCompat.addDocumentStartJavaScript(webView, script, ALL_ORIGINS)
        WebViewCompat.addWebMessageListener(webView, BRIDGE_NAME, ALL_ORIGINS) {
                _,
                message,
                origin,
                isMainFrame,
                reply
            ->
            val text = message.data
            if (isMainFrame && text != null) onMessage(text, origin.toString(), reply)
        }
        return true
    }

    private fun onMessage(text: String, sourceOrigin: String, reply: JavaScriptReplyProxy) {
        val message = CosmeticProtocol.decode(text) ?: return
        // A page may only ask about itself.
        if (originOf(message.url) != sourceOrigin.trimEnd('/').lowercase() || !filters(message.url)) return
        scope.launch {
            val answer = withContext(Dispatchers.Default) { answer(message) } ?: return@launch
            reply.postMessage(CosmeticProtocol.encode(answer))
        }
    }

    private fun answer(message: CosmeticMessage): CosmeticReply? = when (message) {
        is CosmeticMessage.Page -> adblock.pageCosmetics(message.url)?.let {
            CosmeticReply(it.hideSelectors, page = true, generichide = it.generichide, exceptions = it.exceptions)
        }
        is CosmeticMessage.Names ->
            adblock.hiddenSelectors(message.classes, message.ids, message.exceptions)
                ?.takeIf { it.isNotEmpty() }
                ?.let { CosmeticReply(it) }
    }

    companion object {
        const val BRIDGE_NAME = "asconAdblock"
        private val ALL_ORIGINS = setOf("*")

        fun isSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

        fun loadScript(context: Context): String = context.assets.open("cosmetic.js").bufferedReader().use {
            it.readText()
        }

        private fun originOf(url: String): String? = try {
            val uri = URI(url)
            val port = if (uri.port == -1) "" else ":${uri.port}"
            uri.host?.let { "${uri.scheme.lowercase()}://${it.lowercase()}$port" }
        } catch (_: URISyntaxException) {
            null
        }
    }
}
