package com.ascon.feature.browser.web

/**
 * Downloads that install or run code are never saved. Manga sites push fake reader
 * apps and installers far more often than anything a reader needs.
 */
object DownloadPolicy {
    private val BlockedTypes = setOf(
        "application/vnd.android.package-archive",
        "application/x-msdownload",
        "application/x-msdos-program",
        "application/x-ms-installer",
        "application/x-executable",
        "application/x-sh",
        "application/java-archive"
    )
    private val BlockedExtensions = setOf(
        "apk", "apks", "xapk", "apkm", "aab", "dex", "jar", "exe", "msi", "bat", "cmd", "com", "scr", "sh", "ps1", "vbs"
    )
    private val FileName = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)

    fun isBlocked(url: String, contentDisposition: String?, mimeType: String?): Boolean {
        if (mimeType?.substringBefore(';')?.trim()?.lowercase() in BlockedTypes) return true
        val names = listOfNotNull(
            contentDisposition?.let { FileName.find(it)?.groupValues?.get(1) },
            url.substringBefore('#').substringBefore('?').substringAfterLast('/')
        )
        return names.any { it.substringAfterLast('.', "").trim().lowercase() in BlockedExtensions }
    }
}
