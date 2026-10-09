package com.ascon.feature.browser.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ascon.core.model.ProtectionSettings
import com.ascon.engine.adblock.CosmeticFilter
import com.ascon.engine.adblock.RequestFilter
import com.ascon.engine.detection.Detection
import com.ascon.engine.detection.DetectionHost

/** A WebView that knows where to send what detection finds on its pages. */
@SuppressLint("ViewConstructor") // Only created in code, never inflated.
class TabWebView internal constructor(val wrapper: MutableContextWrapper) : WebView(wrapper) {
    internal var onDetection: ((Detection) -> Unit)? = null
    internal var onTap: ((String?) -> Unit)? = null

    /** The script that sets what `window.open` does, and whether it blocks popups. */
    internal var windowOpen: ScriptHandler? = null
    internal var popupsBlocked: Boolean? = null

    /** The script that adds space after the page's end, and how much. */
    internal var endSpaceScript: ScriptHandler? = null
    internal var endSpace = 0
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
    /** Hides the page elements the filter lists name, or null to hide nothing. */
    private val cosmetics: () -> CosmeticFilter? = { null },
    internal val guard: NavigationGuard = NavigationGuard(OkHttpSiteKey, adblock),
    /** Read on WebView threads for every request, so it must not wait. */
    internal val protection: () -> ProtectionSettings = { ProtectionSettings() }
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
        applyPopupSetting(view)
        cosmetics()?.install(view)
        detection().install(view, onDetection = { view.onDetection?.invoke(it) }, onTap = { view.onTap?.invoke(it) })
        return view
    }

    /**
     * Sets what `window.open` does on [view]'s next page: nothing while popups are
     * blocked, a same-tab navigation while they are allowed. Call before each navigation
     * the app starts or lets through, so a changed setting applies to the next page.
     */
    @SuppressLint("RequiresFeature") // Checked first.
    internal fun applyPopupSetting(view: TabWebView) {
        val block = protection().blockPopups
        if (view.popupsBlocked == block || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return
        }
        view.windowOpen?.remove()
        view.windowOpen =
            WebViewCompat.addDocumentStartJavaScript(view, if (block) NO_WINDOW_OPEN else SAME_TAB_OPEN, setOf("*"))
        view.popupsBlocked = block
    }

    /**
     * Adds [px] CSS pixels of empty space after the end of [view]'s pages, the current
     * one and every later one, so the floating bar never hides the end of a page.
     */
    @SuppressLint("RequiresFeature") // Checked first.
    internal fun applyEndSpace(view: TabWebView, px: Int) {
        if (view.endSpace == px || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val script = END_SPACE.replace("END_PX", px.toString())
        view.endSpaceScript?.remove()
        view.endSpaceScript = WebViewCompat.addDocumentStartJavaScript(view, script, setOf("*"))
        view.endSpace = px
        view.evaluateJavascript(script, null)
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

        /**
         * Space after the page, in the main frame only. It is a box after the body in a
         * sheet the page cannot see in its DOM, so it moves nothing on the page. Running
         * it again changes the height.
         */
        const val END_SPACE = """(function () {
  if (window.top !== window) return;
  try {
    var rule = "html::after{content:'';display:block;height:END_PXpx}";
    var sheet = window.__asconEndSpace;
    if (sheet) { sheet.replaceSync(rule); return; }
    sheet = new CSSStyleSheet();
    sheet.replaceSync(rule);
    document.adoptedStyleSheets = document.adoptedStyleSheets.concat(sheet);
    Object.defineProperty(window, "__asconEndSpace", { value: sheet });
  } catch (e) {}
})();"""

        /**
         * With popups allowed, window.open loads the page in this tab instead, and the
         * navigation guard lets it through. A frame may only do so when the browser lets
         * it navigate the tab.
         */
        const val SAME_TAB_OPEN = """(function () {
  function open(url) {
    if (url) {
      try {
        window.top.location.assign(new URL(String(url), location.href).href);
      } catch (e) {}
    }
    return null;
  }
  try {
    Object.defineProperty(window, "open", { value: open, writable: false, configurable: false });
  } catch (e) {}
})();"""
    }
}
