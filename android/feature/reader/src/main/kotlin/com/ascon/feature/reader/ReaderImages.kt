package com.ascon.feature.reader

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches page images natively, the way the WebView would: with its cookies and its
 * User-Agent, and the chapter page as Referer. Cloudflare ties its clearance cookie to
 * the User-Agent, so both must match the WebView's. One per app.
 */
class ReaderImages(context: Context) {
    private val app = context.applicationContext

    private val client: OkHttpClient by lazy {
        // The browser never changes its WebViews' User-Agent, so the default is theirs.
        val userAgent = WebSettings.getDefaultUserAgent(app)
        OkHttpClient.Builder()
            .cookieJar(WebViewCookieJar)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
            }
            .build()
    }

    internal val loader: ImageLoader by lazy {
        ImageLoader.Builder(app)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }

    /** A page's original file, for saving or sharing. Null when the site refuses it. */
    internal suspend fun fetch(url: String, referer: String): PageFile? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Referer", referer).build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val type = response.body.contentType()
                if (!response.isSuccessful || type?.type != "image") return@use null
                PageFile(response.body.bytes(), "${type.type}/${type.subtype}")
            }
        }.getOrNull()
    }
}

/** Shares the WebView's cookies, so a site's session and Cloudflare clearance carry over. */
private object WebViewCookieJar : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        return header.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager = CookieManager.getInstance()
        cookies.forEach { manager.setCookie(url.toString(), it.toString()) }
    }
}
