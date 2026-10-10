package com.ascon.core.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** A synced field's value and when it was last changed, as the sync contract carries it. */
data class Stamped<out T>(val value: T, val updatedAt: Instant)

/**
 * One record of the synced library, with the fields that changed. A field left null
 * didn't change. The records mirror contracts/openapi.yaml; ids are the sync ids below.
 */
sealed interface SyncRecord {
    val id: String

    data class SeriesRecord(override val id: String, val title: Stamped<String>?) : SyncRecord

    /** The library entry: where the user is with the series. */
    data class Entry(override val id: String, val seriesId: Stamped<String>?, val status: Stamped<ReadingStatus>?) :
        SyncRecord

    data class SourceRecord(
        override val id: String,
        val seriesId: Stamped<String>?,
        val domain: Stamped<String>?,
        val lastChapterUrl: Stamped<String?>?,
        val lastChapter: Stamped<BigDecimal>?
    ) : SyncRecord

    data class Progress(
        override val id: String,
        val seriesId: Stamped<String>?,
        val chapter: Stamped<BigDecimal>?,
        val page: Stamped<Int>?,
        val pageCount: Stamped<Int>?,
        val pageOffset: Stamped<Float>?,
        val readAt: Stamped<Instant>?
    ) : SyncRecord
}

/** A page of pulled records and the cursor to pull after them. */
data class SyncPage(val records: List<SyncRecord>, val cursor: String, val hasMore: Boolean)

/**
 * Where sync stands for the signed-in account: the pull [cursor], when the phone last
 * pushed, counted from before reading what to push, and when sync last finished.
 */
data class SyncPosition(val cursor: String? = null, val pushedAt: Instant? = null, val syncedAt: Instant? = null)

/**
 * The sync id of the series titled [title]: a name-based UUID from its title key, so the
 * same series found on two phones before they sync becomes one.
 */
fun seriesSyncId(title: String): String = nameId("series:${titleKey(title)}")

fun entrySyncId(seriesSyncId: String): String = nameId("entry:$seriesSyncId")

fun progressSyncId(seriesSyncId: String): String = nameId("progress:$seriesSyncId")

fun sourceSyncId(seriesSyncId: String, domain: String): String = nameId("source:$seriesSyncId:$domain")

private fun nameId(name: String): String = UUID.nameUUIDFromBytes("ascon:$name".toByteArray()).toString()
