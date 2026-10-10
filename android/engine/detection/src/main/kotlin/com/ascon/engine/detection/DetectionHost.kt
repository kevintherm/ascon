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
class DetectionHost(
    private val script: String,
    private val rules: RuleSource,
    private val scope: CoroutineScope,
    private val health: RuleHealth? = null,
    private val generation: RuleGeneration? = null
) {
    /** The latest structure fingerprint per site, sent with its AI detection request. */
    private val fingerprints = mutableMapOf<String, String>()

    /**
     * Installs detection on [webView], and returns the way to send its page messages.
     * Returns null when the WebView is too old to run scripts at document start, in which
     * case the page is shown without detection.
     */
    @SuppressLint("RequiresFeature") // Checked by isSupported.
    fun install(webView: WebView, onDetection: (Detection) -> Unit, onTap: (String?) -> Unit = {}): PageLink? {
        if (!isSupported()) return null
        WebViewCompat.addDocumentStartJavaScript(webView, script, ORIGIN_RULES)
        val link = PageLink()
        val listener = WebViewCompat.WebMessageListener { _, message, origin, isMainFrame, reply ->
            if (isMainFrame) onMessage(message, origin.toString(), reply, Listeners(onDetection, onTap), link)
        }
        WebViewCompat.addWebMessageListener(webView, BRIDGE_NAME, ORIGIN_RULES, listener)
        return link
    }

    /**
     * Messages to the page on screen, sent through the reply channel of its latest
     * detection result, so they reach only the page that reported.
     */
    class PageLink internal constructor() {
        internal var url: String? = null
        internal var reply: JavaScriptReplyProxy? = null

        /**
         * Brings page [page] of [pageCount] of the chapter at [url] to the top of the screen,
         * [offset] of the way down it, if it is still the page shown.
         */
        fun scrollToPage(url: String, page: Int, pageCount: Int, offset: Float) {
            if (this.url?.substringBefore('#') != url.substringBefore('#')) return
            reply?.postMessage(BridgeProtocol.encodeScroll(page, pageCount, offset))
        }
    }

    private fun onMessage(
        message: WebMessageCompat,
        sourceOrigin: String,
        reply: JavaScriptReplyProxy,
        listeners: Listeners,
        link: PageLink
    ) {
        val decoded = message.data?.let(BridgeProtocol::decode) ?: return
        val url = decoded.url
        // A page may only report about itself.
        val host = java.net.URI(url).host?.takeIf {
            BridgeProtocol.originOf(url) == sourceOrigin.trimEnd('/').lowercase()
        } ?: return
        when (decoded) {
            is PageMessage.Opened -> scope.launch {
                reply.postMessage(BridgeProtocol.encodeRules(rules.candidatesFor(host)))
            }
            is PageMessage.Result -> {
                link.url = url
                link.reply = reply
                val detection = BridgeProtocol.toDetection(decoded)
                health?.let { scope.launch { it.record(host.lowercase(), detection) } }
                listeners.onDetection(detection)
            }
            is PageMessage.Structure -> scope.launch { onStructure(host, decoded, reply) }
            is PageMessage.Snapshot -> onSnapshot(host, decoded, reply)
            is PageMessage.Position -> BridgeProtocol.toPosition(decoded)?.let(listeners.onDetection)
            is PageMessage.Tap -> listeners.onTap(BridgeProtocol.tappedLink(decoded))
        }
    }

    /**
     * A site without its own rule may borrow one from a site built the same way. With
     * nothing to borrow, a chapter only heuristics found is offered to AI detection.
     */
    private suspend fun onStructure(host: String, message: PageMessage.Structure, reply: JavaScriptReplyProxy) {
        val fingerprint = BridgeProtocol.fingerprintOf(message) ?: return
        fingerprints[host] = fingerprint
        val borrowed = rules.forStructure(host, fingerprint)
        if (borrowed != null) {
            reply.postMessage(BridgeProtocol.encodeRules(borrowed))
        } else if (message.via == DetectionSource.Heuristic && generation?.wants(host) == true) {
            reply.postMessage(BridgeProtocol.encodeSnapshotRequest())
        }
    }

    /** Hands a snapshot to AI detection, and the page its new rule once there is one. */
    private fun onSnapshot(host: String, message: PageMessage.Snapshot, reply: JavaScriptReplyProxy) {
        val generation = generation
        val found = BridgeProtocol.toSnapshot(message)
        if (generation == null || found == null) return
        scope.launch {
            if (generation.offer(host, fingerprints[host], found.first, found.second)) {
                reply.postMessage(BridgeProtocol.encodeRules(rules.candidatesFor(host)))
            }
        }
    }

    /** Where results go, and where taps go for the navigation guard. */
    private class Listeners(val onDetection: (Detection) -> Unit, val onTap: (String?) -> Unit)

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
