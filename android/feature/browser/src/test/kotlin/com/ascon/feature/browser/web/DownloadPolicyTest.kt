package com.ascon.feature.browser.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {
    @Test
    fun `blocks installers by type, by URL and by file name`() {
        assertTrue(DownloadPolicy.isBlocked("https://x.example/get", null, "application/vnd.android.package-archive"))
        assertTrue(DownloadPolicy.isBlocked("https://x.example/MangaReader.APK?ref=1#top", null, null))
        assertTrue(
            DownloadPolicy.isBlocked(
                "https://x.example/download",
                "attachment; filename=\"reader-pro.xapk\"",
                "application/octet-stream"
            )
        )
        assertTrue(DownloadPolicy.isBlocked("https://x.example/d", "attachment; filename*=UTF-8''setup.exe", null))
    }

    @Test
    fun `lets other files through`() {
        assertFalse(DownloadPolicy.isBlocked("https://x.example/chapter-12.zip", null, "application/zip"))
        assertFalse(DownloadPolicy.isBlocked("https://x.example/apk-guide.html", null, "text/html"))
    }
}
