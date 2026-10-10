package com.ascon.core.data

import com.ascon.core.model.ChapterLink
import com.ascon.core.model.ReadingProgress
import com.ascon.core.model.Series
import com.ascon.core.model.Source
import com.ascon.core.model.Stamped
import com.ascon.core.model.SyncRecord
import com.ascon.core.model.entrySyncId
import com.ascon.core.model.progressSyncId
import com.ascon.core.model.seriesSyncId
import com.ascon.core.model.sourceSyncId
import java.time.Instant
import java.time.temporal.ChronoUnit

// How the library meets sync, per AGENTS.md: each synced part of a series carries the
// time it last changed, the phone pushes what changed since its last push, and a pulled
// part replaces the phone's own only when it is newer. Every LibraryRepository applies
// these, so the fake and Room agree.

/** The sync id of this series, kept once it is given. */
val Series.syncKey: String get() = syncId ?: seriesSyncId(title)

/**
 * This series as changed on the phone from [old], with the time [now] on each synced part
 * that changed, and its sync id.
 */
internal fun Series.stamped(old: Series?, at: Instant): Series {
    // Room keeps milliseconds, so a time read back equals the one pushed.
    val now = at.truncatedTo(ChronoUnit.MILLIS)
    val oldProgress = old?.progress?.copy(updatedAt = null)
    return copy(
        syncId = syncKey,
        statusUpdatedAt = if (old == null || old.status != status) now else statusUpdatedAt,
        sources = sources.map { source ->
            val before = old?.source(source.id)?.copy(updatedAt = null)
            if (before != source.copy(updatedAt = null)) source.copy(updatedAt = now) else source
        },
        progress = progress?.let { if (it.copy(updatedAt = null) != oldProgress) it.copy(updatedAt = now) else it }
    )
}

/**
 * The records for what changed after [since], or everything when [since] is null. A part
 * with no time, saved before sync existed, goes only with everything.
 */
internal fun Series.syncRecords(since: Instant?): List<SyncRecord> {
    fun changed(at: Instant?) = since == null || (at != null && at > since)
    val id = syncKey
    val series = Stamped(id, Instant.EPOCH)
    val parts = buildList {
        if (changed(statusUpdatedAt)) {
            add(SyncRecord.Entry(entrySyncId(id), series, Stamped(status, statusUpdatedAt ?: Instant.EPOCH)))
        }
        sources.filter { changed(it.updatedAt) }.forEach { add(it.record(id)) }
        progress?.takeIf { changed(it.updatedAt) }?.let { add(it.record(id, lastReadAt)) }
    }
    // A record that names a series follows the series' own, so a phone new to it can make it.
    return if (parts.isEmpty()) {
        emptyList()
    } else {
        listOf(SyncRecord.SeriesRecord(id, Stamped(title, Instant.EPOCH))) +
            parts
    }
}

private fun Source.record(seriesId: String): SyncRecord.SourceRecord {
    val at = updatedAt ?: Instant.EPOCH
    return SyncRecord.SourceRecord(
        id = sourceSyncId(seriesId, id),
        seriesId = Stamped(seriesId, at),
        domain = Stamped(id, at),
        lastChapterUrl = Stamped(lastOpened?.url, at),
        lastChapter = Stamped(lastOpened?.chapter ?: lastChapter, at)
    )
}

private fun ReadingProgress.record(seriesId: String, readAt: Instant?): SyncRecord.Progress {
    val at = updatedAt ?: readAt ?: Instant.EPOCH
    return SyncRecord.Progress(
        id = progressSyncId(seriesId),
        seriesId = Stamped(seriesId, at),
        chapter = Stamped(chapter, at),
        page = Stamped(page, at),
        pageCount = Stamped(pageCount, at),
        pageOffset = Stamped(pageOffset, at),
        readAt = Stamped(readAt ?: at, at)
    )
}

/**
 * The series [records] pulled from the server change or add, given the whole [library].
 * A series new to the phone gets an id from [newId]. Records for a series the phone
 * doesn't have and isn't told about are skipped.
 */
internal fun pulled(library: List<Series>, records: List<SyncRecord>, newId: () -> String): List<Series> {
    val bySyncId = library.associateBy { it.syncKey }.toMutableMap()
    val changed = mutableSetOf<String>()
    // A record names its series by sync id, or only by its own id when just some fields changed.
    val owners = bySyncId.keys.flatMap { id ->
        val series = bySyncId.getValue(id)
        listOf(entrySyncId(id) to id, progressSyncId(id) to id) + series.sources.map { sourceSyncId(id, it.id) to id }
    }.toMap().toMutableMap()
    for (record in records.sortedBy { it.order }) {
        val seriesId = record.seriesId ?: owners[record.id]
        val current = seriesId?.let { bySyncId[it] }
        val next = seriesId?.let { current.applying(record, it, newId) }
        if (seriesId != null && next != null && next != current) {
            bySyncId[seriesId] = next
            changed += seriesId
            next.sources.forEach { owners[sourceSyncId(seriesId, it.id)] = seriesId }
            owners[entrySyncId(seriesId)] = seriesId
            owners[progressSyncId(seriesId)] = seriesId
        }
    }
    return changed.map { bySyncId.getValue(it) }
}

/** This series, or none yet, with [record] for the series [syncId] applied. */
private fun Series?.applying(record: SyncRecord, syncId: String, newId: () -> String): Series? = when (record) {
    is SyncRecord.SeriesRecord -> this ?: record.title?.let { newPulledSeries(newId(), it.value, syncId) }
    is SyncRecord.Entry -> this?.withEntry(record)
    is SyncRecord.SourceRecord -> this?.withSource(record)
    is SyncRecord.Progress -> this?.withProgress(record)
}

/** Series first, so the records after them find their series in the same page. */
private val SyncRecord.order: Int
    get() = when (this) {
        is SyncRecord.SeriesRecord -> 0
        is SyncRecord.Entry -> 1
        is SyncRecord.SourceRecord -> 2
        is SyncRecord.Progress -> 3
    }

private val SyncRecord.seriesId: String?
    get() = when (this) {
        is SyncRecord.SeriesRecord -> id
        is SyncRecord.Entry -> seriesId?.value
        is SyncRecord.SourceRecord -> seriesId?.value
        is SyncRecord.Progress -> seriesId?.value
    }

private fun newPulledSeries(id: String, title: String, syncId: String): Series =
    newSeries(id, title).copy(syncId = syncId)

private fun Series.withEntry(record: SyncRecord.Entry): Series {
    val status = record.status ?: return this
    val newer = statusUpdatedAt == null || status.updatedAt > statusUpdatedAt
    return if (newer) copy(status = status.value, statusUpdatedAt = status.updatedAt) else this
}

private fun Series.withSource(record: SyncRecord.SourceRecord): Series {
    val source = pulledSource(record) ?: return this
    val all = if (source(source.id) ==
        null
    ) {
        sources + source
    } else {
        sources.map { if (it.id == source.id) source else it }
    }
    // Progress pulled before any source had none to point at.
    val place = progress?.let { p -> if (all.none { it.id == p.sourceId }) p.copy(sourceId = source.id) else p }
    return copy(sources = all, progress = place)
}

/** The source [record] makes, or null when the phone's own is as new. */
private fun Series.pulledSource(record: SyncRecord.SourceRecord): Source? {
    val at = newest(record.domain, record.lastChapterUrl, record.lastChapter)
    val known = sources.firstOrNull { sourceSyncId(syncKey, it.id) == record.id }
    val link = pulledLink(record, known?.lastOpened)
    return when {
        at == null -> null
        known?.updatedAt?.let { it >= at } == true -> null
        known != null -> known.copy(
            firstChapter = link?.let { known.firstChapter.min(it.chapter) } ?: known.firstChapter,
            lastChapter = link?.let { known.lastChapter.max(it.chapter) } ?: known.lastChapter,
            lastOpened = link,
            updatedAt = at
        )
        else -> record.domain?.value?.let { domain ->
            val chapter = record.lastChapter?.value
            chapter?.let { Source(domain, domain, official = false, it, it, link, updatedAt = at) }
        }
    }
}

/** The chapter page [record] gives, filled in from the phone's [own] where it gives only part. */
private fun pulledLink(record: SyncRecord.SourceRecord, own: ChapterLink?): ChapterLink? {
    val chapter = record.lastChapter?.value ?: own?.chapter
    val url = record.lastChapterUrl?.value ?: own?.url
    return if (url != null && chapter != null) ChapterLink(url, chapter) else own
}

private fun Series.withProgress(record: SyncRecord.Progress): Series {
    val place = pulledPlace(record) ?: return this
    val finished = place.pageCount > 0 && place.page >= place.pageCount
    return copy(
        progress = place,
        chapters = withChapter(place.chapter).markedRead(place.chapter, before = true, it = finished),
        lastReadAt = listOfNotNull(lastReadAt, record.readAt?.value).maxOrNull()
    )
}

/** The place [record] gives, or null when the phone's own is as new. */
private fun Series.pulledPlace(record: SyncRecord.Progress): ReadingProgress? {
    val at = newest(record.chapter, record.page, record.pageCount, record.pageOffset, record.readAt)
    val current = progress
    val chapter = record.chapter?.value ?: current?.chapter
    return when {
        at == null || chapter == null -> null
        current?.updatedAt?.let { it >= at } == true -> null
        else -> ReadingProgress(
            chapter = chapter,
            page = record.page?.value ?: current?.page ?: 0,
            pageCount = record.pageCount?.value ?: current?.pageCount ?: 0,
            sourceId = current?.sourceId ?: sources.firstOrNull()?.id.orEmpty(),
            pageOffset = record.pageOffset?.value ?: current?.pageOffset ?: 0f,
            updatedAt = at
        )
    }
}

private fun newest(vararg fields: Stamped<*>?): Instant? = fields.filterNotNull().maxOfOrNull { it.updatedAt }
