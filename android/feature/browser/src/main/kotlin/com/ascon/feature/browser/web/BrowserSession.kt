package com.ascon.feature.browser.web

import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ascon.engine.adblock.RequestType
import com.ascon.engine.detection.Detection
import java.io.ByteArrayInputStream

/** How a main-frame load failed. */
enum class LoadErrorKind { NotFound, Unreachable, Insecure, Crashed, Other }

/** Something the browser stopped, told to the user briefly. */
enum class BlockedKind { Redirect, OtherApp, Popup, AppDownload, Download, Ad }

/** What a [BrowserSession] reports to the screen. Plain values only, so a view model can take them. */
interface BrowserEvents {
    fun onPageStarted(url: String)

    fun onPageFinished(url: String)

    fun onProgress(percent: Int)

    fun onHistoryChanged(url: String, canGoBack: Boolean, canGoForward: Boolean)

    fun onLoadError(url: String, kind: LoadErrorKind)

    fun onBlocked(kind: BlockedKind, url: String)

    fun onDetection(detection: Detection)

    /** The ad blocker stopped a request from [pageUrl]. Called off the main thread. */
    fun onRequestBlocked(pageUrl: String)
}

/**
 * One browser tab's live WebView. It outlives the screen that shows it, so leaving the
 * browser for a series page and coming back keeps the page and its history. The screen
 * attaches the view while shown and detaches it after.
 *
 * The view is created on a [MutableContextWrapper]: it points at the activity while
 * attached and at the application otherwise, so a detached view never holds an activity.
 */
class BrowserSession internal constructor(private val pool: WebViewPool, first: TabWebView) {
    @Volatile
    var events: BrowserEvents? = null

    /** Changes when the WebView is replaced after its renderer died. */
    var generation by mutableIntStateOf(0)
        private set

    private var webView: TabWebView = first.also(::setUp)

    /** The page in the main frame, for requests checked off the main thread, where [WebView.getUrl] can't be read. */
    @Volatile
    private var pageUrl: String? = null

    /** The user's last tap, for the navigation guard. */
    private var lastTap: Tap? = null

    /** [href] is the link under the tap, or null; [at] is uptime in milliseconds. */
    private class Tap(val href: String?, val at: Long)

    /** The view to place on screen, detached from any old parent and bound to [activity]. */
    fun attach(activity: Context): WebView {
        webView.wrapper.baseContext = activity
        (webView.parent as? ViewGroup)?.removeView(webView)
        return webView
    }

    /** Takes [view] off screen. The session keeps it alive for the next [attach]. */
    fun detach(view: WebView) {
        (view.parent as? ViewGroup)?.removeView(view)
        if (view is TabWebView) view.wrapper.baseContext = view.wrapper.applicationContext
    }

    /** True until the first page starts loading, and after a renderer crash replaced the view. */
    val isEmpty: Boolean get() = webView.url == null

    fun load(url: String) = webView.loadUrl(url)

    fun reload() = webView.reload()

    fun goBack() = webView.goBack()

    fun goForward() = webView.goForward()

    fun destroy() {
        events = null
        detach(webView)
        webView.destroy()
    }

    private fun setUp(view: TabWebView) {
        view.webViewClient = Client()
        view.webChromeClient = Chrome()
        view.onDetection = { events?.onDetection(it) }
        view.onTap = { href -> lastTap = Tap(href, SystemClock.uptimeMillis()) }
        view.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            val kind = if (DownloadPolicy.isBlocked(url, contentDisposition, mimeType)) {
                BlockedKind.AppDownload
            } else {
                BlockedKind.Download
            }
            events?.onBlocked(kind, url)
        }
    }

    /** The renderer is gone and took the view with it. A fresh one replaces it. */
    private fun replaceView() {
        val old = webView
        detach(old)
        old.destroy()
        webView = pool.create().also(::setUp)
        generation++
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val to = request.url.toString()
            val blocked = pool.guard.check(
                from = view.url,
                to = to,
                isMainFrame = request.isForMainFrame,
                hasGesture = request.hasGesture(),
                isRedirect = request.isRedirect,
                tappedLink = lastTap?.takeIf { SystemClock.uptimeMillis() - it.at < TAP_WINDOW_MS }?.href
            ) ?: return false
            if (request.isForMainFrame) {
                val kind = when (blocked) {
                    BlockReason.Scheme -> BlockedKind.OtherApp
                    BlockReason.Ad -> BlockedKind.Ad
                    else -> BlockedKind.Redirect
                }
                events?.onBlocked(kind, to)
            }
            return true
        }

        /** Runs on a WebView thread for every request. A blocked one gets an empty response. */
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            // Main-frame pages are checked in shouldOverrideUrlLoading, which can tell the user.
            if (request.isForMainFrame) return null
            val url = request.url.toString()
            val accept = request.requestHeaders.entries.firstOrNull {
                it.key.equals("Accept", ignoreCase = true)
            }?.value
            val type = RequestType.of(url, isMainFrame = false, accept = accept)
            val page = pageUrl ?: url
            val blocked = pool.adblock.shouldBlock(url, page, type)
            if (blocked) events?.onRequestBlocked(page)
            return if (blocked) emptyResponse() else null
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            pageUrl = url
            events?.onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            events?.onPageFinished(url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            pageUrl = url
            events?.onHistoryChanged(url, view.canGoBack(), view.canGoForward())
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) events?.onLoadError(request.url.toString(), error.errorCode.toKind())
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // Never proceed past a certificate error. The page is not shown.
            handler.cancel()
            events?.onLoadError(error.url, LoadErrorKind.Insecure)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // Returning true keeps the app alive; the dead view must not be used again.
            if (view === webView) {
                val url = view.url.orEmpty()
                replaceView()
                events?.onLoadError(url, LoadErrorKind.Crashed)
            }
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            events?.onProgress(newProgress)
        }

        /**
         * Sites open new windows for ads far more than for anything else, so windows are
         * never created. A link the user tapped that asks for a new window opens here.
         */
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            val hit = view.hitTestResult
            val link = hit.extra?.takeIf { hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE }
            if (isUserGesture && link != null && schemeOf(link) in setOf("http", "https")) {
                view.loadUrl(link)
            } else {
                events?.onBlocked(BlockedKind.Popup, view.url ?: "")
            }
            return false
        }
    }

    private companion object {
        /** How long after a tap a navigation can still come from it. */
        const val TAP_WINDOW_MS = 1500L

        fun emptyResponse() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

        fun Int.toKind() = when (this) {
            WebViewClient.ERROR_HOST_LOOKUP -> LoadErrorKind.NotFound
            WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT -> LoadErrorKind.Unreachable
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> LoadErrorKind.Insecure
            else -> LoadErrorKind.Other
        }
    }
}

/**
 * Keeps a tab's [BrowserSession] for as long as the tab is on the back stack, across
 * rotation and while other screens cover it, and destroys the WebView when the tab closes.
 */
class BrowserSessionHolder(pool: WebViewPool) : ViewModel() {
    val session: BrowserSession = pool.newSession()

    override fun onCleared() {
        session.destroy()
    }
}
