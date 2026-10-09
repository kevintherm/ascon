package com.ascon.engine.adblock

/**
 * Refreshes filter lists from their update URLs. A download replaces the current copy
 * only when it is a valid list with a newer version.
 */
class FilterListUpdater(
    private val current: (FilterList) -> String?,
    private val fetch: suspend (url: String) -> String?,
    private val save: (FilterList, String) -> Unit
) {
    /** Returns true when any list changed, so the engine needs rebuilding. */
    suspend fun update(lists: List<FilterList>): Boolean {
        var changed = false
        for (list in lists) {
            val download = list.updateUrl?.let { fetch(it) }?.takeIf(FilterLists::isFilterList)
            val now = current(list)
            if (download != null && (now == null || FilterLists.newer(now, download) !== now)) {
                save(list, download)
                changed = true
            }
        }
        return changed
    }
}
