package com.ascon.core.model

/** Numbers the settings screen reports but does not own. */
data class SettingsSummary(
    val blockedThisWeek: Int,
    val activeFilterLists: Int,
    val hiddenSeriesLocked: Boolean,
    val downloadsBytes: Long
)
