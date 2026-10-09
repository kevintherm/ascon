package com.ascon.feature.browser.web

import android.content.Context
import androidx.webkit.WebViewCompat
import com.ascon.engine.detection.DetectionHost

/** Whether the installed Android System WebView is good enough to browse with. */
enum class WebViewHealth {
    Ok,

    /** Still works, but old enough to miss security fixes. */
    Outdated,

    /** Too old for document-start scripts, so detection cannot run. */
    NoDetection,

    /** No WebView provider is installed or enabled. */
    Missing
}

/**
 * Chromium major version older than this is about two years out of date. Bump it now
 * and then; it only decides when to show a warning.
 */
private const val OLDEST_CURRENT_MAJOR = 120

fun webViewHealth(context: Context): WebViewHealth {
    val info = WebViewCompat.getCurrentWebViewPackage(context) ?: return WebViewHealth.Missing
    val major = info.versionName?.substringBefore('.')?.toIntOrNull()
    return when {
        !DetectionHost.isSupported() -> WebViewHealth.NoDetection
        major != null && major < OLDEST_CURRENT_MAJOR -> WebViewHealth.Outdated
        else -> WebViewHealth.Ok
    }
}
