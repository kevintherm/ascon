package com.ascon.feature.browser.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ascon.engine.adblock.RequestFilter
import com.ascon.engine.detection.Detection
import com.ascon.engine.detection.DetectionHost

/** A WebView that knows where to send what detection finds on its pages. */
@SuppressLint("ViewConstructor") // Only created in code, never inflated.
class TabWebView internal constructor(val wrapper: MutableContextWrapper) : WebView(wrapper) {
    internal var onDetection: ((Detection) -> Unit)? = null
    internal var onTap: ((String?) -> Unit)? = null
}

/**
 * Makes the browser's WebViews, all set up the same way, and keeps one ready.
 *
 * The first WebView in a process is slow to create because it loads Chromium, so
 * [prewarm] creates one while the app is idle and the first browser tab takes it.
 * Only tabs hold live WebViews; the pool keeps at most the warm one.
 */
class WebViewPool(
    private val app: Context,
    private val detection: () -> DetectionHost,
    /** Checked for every request a page makes, and for every page it opens. */
    internal val adblock: RequestFilter = RequestFilter.AllowAll,
    internal val guard: NavigationGuard = NavigationGuard(OkHttpSiteKey, adblock)
) {
    private var warm: TabWebView? = null

    /** Call on the main thread once the first frame is drawn. */
    fun prewarm() {
        if (warm == null) warm = create()
    }

    fun newSession(): BrowserSession {
        val view = warm ?: create()
        warm = null
        return BrowserSession(this, view)
    }

    @SuppressLint("SetJavaScriptEnabled") // Sites need it; the guard and detection depend on it.
    internal fun create(): TabWebView {
        val view = TabWebView(MutableContextWrapper(app.applicationContext))
        // Compose gives an AndroidView without layout params WRAP_CONTENT, and a WebView that
        // wraps its height sizes the page to its content, so CSS vh units become 0.
        view.layoutParams =
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        with(view.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            // onCreateWindow decides what happens to new windows; without this they replace the page.
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        hideAppFromSites(view)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(view, NO_WINDOW_OPEN, setOf("*"))
        }
        detection().install(view, onDetection = { view.onDetection?.invoke(it) }, onTap = { view.onTap?.invoke(it) })
        return view
    }

    /**
     * Stops WebView from sending `X-Requested-With: com.ascon.app` with every request,
     * which tells sites which app is browsing. androidx.webkit 1.17 marks the switch
     * deprecated and restricted. WebView 153 reports it unsupported and still sends the
     * header, checked on the emulator, so this only helps on WebViews that support it.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("RestrictedApi")
    private fun hideAppFromSites(view: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(view.settings, emptySet())
        }
    }

    private companion object {
        /**
         * Pages cannot open windows. window.open returns null, as a browser does when it
         * blocks a popup, and the property cannot be put back.
         */
        const val NO_WINDOW_OPEN = """(function () {
  try {
    Object.defineProperty(window, "open", { value: function () { return null; }, writable: false, configurable: false });
  } catch (e) {}
})();"""
    }
}
