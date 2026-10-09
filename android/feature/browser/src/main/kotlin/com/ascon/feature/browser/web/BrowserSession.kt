package com.ascon.feature.browser.web

import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ascon.engine.detection.Detection

/** How a main-frame load failed. */
enum class LoadErrorKind { NotFound, Unreachable, Insecure, Crashed, Other }

/** Something the browser stopped, told to the user briefly. */
enum class BlockedKind { Redirect, OtherApp, Popup, AppDownload, Download }

/** What a [BrowserSession] reports to the screen. Plain values only, so a view model can take them. */
interface BrowserEvents {
    fun onPageStarted(url: String)

    fun onPageFinished(url: String)

    fun onProgress(percent: Int)

    fun onHistoryChanged(url: String, canGoBack: Boolean, canGoForward: Boolean)

    fun onLoadError(url: String, kind: LoadErrorKind)

    fun onBlocked(kind: BlockedKind, url: String)

    fun onDetection(detection: Detection)
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
    var events: BrowserEvents? = null

    /** Changes when the WebView is replaced after its renderer died. */
    var generation by mutableIntStateOf(0)
        private set

    private var webView: TabWebView = first.also(::setUp)

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
                isRedirect = request.isRedirect
            ) ?: return false
            if (request.isForMainFrame) {
                events?.onBlocked(if (blocked == BlockReason.Scheme) BlockedKind.OtherApp else BlockedKind.Redirect, to)
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            events?.onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            events?.onPageFinished(url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
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
