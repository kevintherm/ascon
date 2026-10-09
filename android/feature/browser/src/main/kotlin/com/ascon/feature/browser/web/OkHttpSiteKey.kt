package com.ascon.feature.browser.web

import okhttp3.HttpUrl

/**
 * Sites by the public suffix list, so `a.example.co.uk` and `b.example.co.uk` are one
 * site and `example.co.uk` and `other.co.uk` are two. Hosts without a registrable
 * domain, such as IP addresses, are their own site.
 */
object OkHttpSiteKey : SiteKey {
    override fun siteOf(host: String): String =
        HttpUrl.Builder().scheme("https").host(host).build().topPrivateDomain() ?: host
}
