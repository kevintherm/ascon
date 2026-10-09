package com.ascon.feature.browser.web

import java.net.URLEncoder

/** Where typed text that is not an address goes. DuckDuckGo keeps no search history. */
private const val SEARCH_URL = "https://duckduckgo.com/?q="

private val HasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
private val LooksLikeHost = Regex("^[^\\s/?#]+\\.[^\\s/?#]{2,}(?::\\d+)?(?:[/?#]\\S*)?$")

/**
 * Turns what the user typed in the address field into a URL to load. An address with a
 * scheme is used as it is, a bare domain gets https, and anything else is a search.
 */
fun addressToUrl(input: String): String? {
    val text = input.trim()
    if (text.isEmpty()) return null
    return when {
        HasScheme.containsMatchIn(text) -> text.takeIf { schemeOf(it) in setOf("http", "https") }
            ?: search(text)
        LooksLikeHost.matches(text) -> "https://$text"
        else -> search(text)
    }
}

private fun search(text: String) = SEARCH_URL + URLEncoder.encode(text, "UTF-8")

/** The host as the address pill shows it, without `www.`. */
fun displayHost(url: String): String = hostOf(url)?.removePrefix("www.") ?: url
