package com.ascon.core.model

enum class ReadingMode { LongStrip, Paged }

/** User settings shown on the settings screen. */
data class Settings(
    val blockAds: Boolean = true,
    val blockPopups: Boolean = true,
    val keepScreenOn: Boolean = false,
    val readingMode: ReadingMode = ReadingMode.LongStrip,
    val secureDnsProvider: String = "Cloudflare"
)

/** Numbers the settings screen reports but does not own. */
data class SettingsSummary(
    val blockedThisWeek: Int,
    val activeFilterLists: Int,
    val hiddenSeriesLocked: Boolean,
    val downloadsBytes: Long
)
