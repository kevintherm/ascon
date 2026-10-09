package com.ascon.core.model

enum class SecureDns(val label: String) { Cloudflare("Cloudflare") }

/**
 * What the browser blocks. Shared by Settings, the protection sheet and the browser.
 * App links and APK downloads are always blocked, whatever these say.
 */
data class ProtectionSettings(
    /** Requests and page elements on the filter lists. */
    val adblockEnabled: Boolean = true,
    /** `window.open`, and pages sending the tab to another site without a tap. */
    val blockPopups: Boolean = true,
    /** Ids of filter lists the user turned off. */
    val disabledFilterLists: Set<String> = emptySet(),
    /** Registrable domains, such as `example.com`, where nothing but the always-on rules apply. */
    val trustedSites: Set<String> = emptySet(),
    val secureDns: SecureDns = SecureDns.Cloudflare
) {
    fun trusts(site: String?): Boolean = site != null && site in trustedSites
}
