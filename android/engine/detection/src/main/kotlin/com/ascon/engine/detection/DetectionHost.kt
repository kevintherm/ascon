package com.ascon.engine.detection

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Connects a WebView to detection. It injects evaluator.js and bridge.js before page
 * scripts run, answers the page's request for rules, and hands results to a listener.
 *
 * All calls happen on the main thread, where WebView delivers messages.
 */
class DetectionHost(private val script: String, private val rules: RuleSource, private val scope: CoroutineScope) {
    /**
     * Installs detection on [webView]. Returns false when the WebView is too old to run
     * scripts at document start, in which case the page is shown without detection.
     */
    @SuppressLint("RequiresFeature") // Checked by isSupported.
    fun install(webView: WebView, onDetection: (Detection) -> Unit): Boolean {
        if (!isSupported()) return false
        WebViewCompat.addDocumentStartJavaScript(webView, script, ORIGIN_RULES)
        val listener = WebViewCompat.WebMessageListener { _, message, origin, isMainFrame, reply ->
            if (isMainFrame) onMessage(message, origin.toString(), reply, onDetection)
        }
        WebViewCompat.addWebMessageListener(webView, BRIDGE_NAME, ORIGIN_RULES, listener)
        return true
    }

    private fun onMessage(
        message: WebMessageCompat,
        sourceOrigin: String,
        reply: JavaScriptReplyProxy,
        onDetection: (Detection) -> Unit
    ) {
        val decoded = message.data?.let(BridgeProtocol::decode) ?: return
        val url = when (decoded) {
            is PageMessage.Opened -> decoded.url
            is PageMessage.Result -> decoded.url
            is PageMessage.Position -> decoded.url
        }
        // A page may only report about itself.
        if (BridgeProtocol.originOf(url) != sourceOrigin.trimEnd('/').lowercase()) return
        when (decoded) {
            is PageMessage.Opened -> scope.launch {
                val host = java.net.URI(url).host ?: return@launch
                reply.postMessage(BridgeProtocol.encodeRules(rules.candidatesFor(host)))
            }
            is PageMessage.Result -> onDetection(BridgeProtocol.toDetection(decoded))
            is PageMessage.Position -> BridgeProtocol.toPosition(decoded)?.let(onDetection)
        }
    }

    companion object {
        /** The name bridge.js finds the listener under, then deletes from the page. */
        const val BRIDGE_NAME = "asconBridge"

        /**
         * Every origin, because WebView has no rule for "any http or https site". The
         * restriction to web pages is in code instead: bridge.js exits on any other
         * scheme, and [onMessage] drops messages whose origin is not http or https.
         */
        val ORIGIN_RULES = setOf("*")

        fun isSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

        /** evaluator.js then bridge.js, as one script. */
        fun loadScript(context: Context): String = listOf("evaluator.js", "bridge.js").joinToString("\n") { name ->
            context.assets.open(name).bufferedReader().use { it.readText() }
        }

        fun loadBuiltInRules(context: Context): List<RuleCandidate> =
            BuiltInRules.parse(context.assets.open(BuiltInRules.ASSET).bufferedReader().use { it.readText() })
    }
}
