package com.ascon.engine.detection

import com.ascon.core.data.RuleHealthCount
import com.ascon.core.data.RuleStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Counts how a site's rule from the backend does, per AGENTS.md's rule health: a chapter
 * page it reads fully is a success, one it never reads fully is an empty result, and a
 * next link that leads to an earlier chapter is a backward jump.
 *
 * A page re-reports as it builds itself, so it is judged once, by its best result, when
 * the next page arrives.
 */
class RuleHealth(private val store: RuleStore) {
    private class Page(val domain: String, val url: String, var read: Detection.ChapterPage?)

    private val lock = Mutex()
    private var page: Page? = null

    /** The last page judged a success, to check where its next link led. */
    private var previous: Detection.ChapterPage? = null

    suspend fun record(domain: String, detection: Detection) = lock.withLock {
        val current = page
        val chapter = (detection as? Detection.ChapterPage)?.takeIf { it.source == DetectionSource.Rule }
        if (current != null && current.url == detection.url.withoutFragment()) {
            if (current.read == null && chapter?.isRead() == true) current.read = chapter
            return@withLock
        }
        current?.let { judge(it) }
        page = chapter?.let { Page(domain, it.url.withoutFragment(), it.takeIf { c -> c.isRead() }) }
    }

    private suspend fun judge(page: Page) {
        val version = store.rule(page.domain)?.takeIf { it.json != null }?.version ?: return
        val read = page.read
        val jumpedBack = read != null && previous?.let { it.next == page.url && read.chapter!! <= it.chapter!! } == true
        store.addHealth(
            RuleHealthCount(
                page.domain,
                version,
                successes = if (read != null) 1 else 0,
                emptyResults = if (read == null) 1 else 0,
                backwardJumps = if (jumpedBack) 1 else 0
            )
        )
        previous = read
    }

    private fun Detection.ChapterPage.isRead(): Boolean =
        images.isNotEmpty() && chapter != null && !title.isNullOrBlank()
}
