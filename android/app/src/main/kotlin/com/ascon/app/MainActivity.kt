package com.ascon.app

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** The single activity. Every screen is a Compose destination in [AsconApp]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as AsconApplication).container
        val startUrl = if (savedInstanceState == null) debugStartUrl() else null
        setContent { AsconApp(container, startUrl) }
        // The first WebView loads Chromium and takes a while. Do it once the app is idle,
        // so the first page opens fast and launch stays fast.
        Looper.myQueue().addIdleHandler {
            container.webViews.prewarm()
            false
        }
    }

    /**
     * Debug builds only: `--es url <address>` on launch opens a browser tab there, so UI
     * flows can start on a page without typing into the address field.
     */
    private fun debugStartUrl(): String? {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return if (debuggable) intent.getStringExtra(EXTRA_URL) else null
    }

    private companion object {
        const val EXTRA_URL = "url"
    }
}
