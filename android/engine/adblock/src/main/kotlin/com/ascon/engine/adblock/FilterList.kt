package com.ascon.engine.adblock

/**
 * A filter list the user can turn on or off. A copy ships in the app's assets as
 * `adblock/<id>.txt`; a list with an [updateUrl] is refreshed from it weekly.
 */
data class FilterList(val id: String, val title: String, val updateUrl: String?) {
    companion object {
        val EasyList = FilterList("easylist", "EasyList", "https://easylist.to/easylist/easylist.txt")
        val EasyPrivacy = FilterList("easyprivacy", "EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt")

        /** Ads and pop-unders on manga sites that general lists miss. Updates with the app. */
        val Ascon = FilterList("ascon", "Ascon manga sites", updateUrl = null)

        val All = listOf(EasyList, EasyPrivacy, Ascon)
    }
}

/** Rules for telling filter list copies apart. */
object FilterLists {
    private val Version = Regex("""^!\s*Version:\s*(\d+)""", RegexOption.MULTILINE)

    /** Real lists hold thousands of rules. A short download is an error page or a cut-off transfer. */
    private const val MIN_RULES = 20

    /** How far into a list its header can reach. */
    private const val HEADER_CHARS = 2_000

    /** The number in the list's `! Version:` header, which grows with each release. */
    fun versionOf(text: String): Long? = Version.find(text.take(HEADER_CHARS))?.groupValues?.get(1)?.toLongOrNull()

    /** The copy with the higher version. A copy without one never replaces one with. */
    fun newer(current: String, candidate: String?): String {
        val candidateVersion = candidate?.let(::versionOf)
        val currentVersion = versionOf(current)
        return when {
            candidateVersion == null -> current
            currentVersion == null || candidateVersion > currentVersion -> candidate
            else -> current
        }
    }

    /** Whether a download is a filter list worth keeping. */
    fun isFilterList(text: String): Boolean =
        text.startsWith("[Adblock") && text.lineSequence().count { it.isNotBlank() && !it.startsWith("!") } > MIN_RULES
}
