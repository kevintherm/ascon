package com.ascon.engine.detection

import com.ascon.core.model.ReadingStatus
import com.ascon.core.model.Stamped
import com.ascon.core.model.SyncRecord
import com.ascon.core.model.toChapterLabel
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// Sync records as contracts/openapi.yaml's Change: an entity, an id, and each field as
// its value and updatedAt.

private val Statuses = mapOf(
    ReadingStatus.Reading to "reading",
    ReadingStatus.Plan to "planned",
    ReadingStatus.Paused to "paused",
    ReadingStatus.Done to "completed"
)

internal fun SyncRecord.toJson(): JsonObject {
    val (entity, fields) = when (this) {
        is SyncRecord.SeriesRecord -> "series" to mapOf(
            "title" to title.json { JsonPrimitive(it) },
            "anilistId" to aniListId.json { JsonPrimitive(it) },
            "mangaUpdatesId" to mangaUpdatesId.json { JsonPrimitive(it) }
        )
        is SyncRecord.Entry -> "libraryEntry" to mapOf(
            "seriesId" to seriesId.json { JsonPrimitive(it) },
            "status" to status.json { JsonPrimitive(Statuses.getValue(it)) }
        )
        is SyncRecord.SourceRecord -> "source" to mapOf(
            "seriesId" to seriesId.json { JsonPrimitive(it) },
            "domain" to domain.json { JsonPrimitive(it) },
            "lastChapterUrl" to lastChapterUrl.json { JsonPrimitive(it) },
            "lastChapter" to lastChapter.json { JsonPrimitive(it.toChapterLabel()) }
        )
        is SyncRecord.ChapterRecord -> "chapter" to mapOf(
            "seriesId" to seriesId.json { JsonPrimitive(it) },
            "number" to number.json { JsonPrimitive(it.toChapterLabel()) },
            "read" to read.json { JsonPrimitive(it) },
            "openedUrl" to openedUrl.json { JsonPrimitive(it) },
            "openedDomain" to openedDomain.json { JsonPrimitive(it) }
        )
        is SyncRecord.Progress -> "progress" to progressFields()
    }
    return buildJsonObject {
        put("entity", JsonPrimitive(entity))
        put("id", JsonPrimitive(id))
        put("fields", JsonObject(fields.filterValues { it != null }.mapValues { it.value!! }))
    }
}

private fun SyncRecord.Progress.progressFields(): Map<String, JsonObject?> {
    // pagePosition is how far through the chapter, for clients that don't know pages.
    val page = page
    val count = pageCount?.takeIf { it.value > 0 }
    val position = if (page != null && count != null) {
        val through = (page.value - 1).coerceAtLeast(0) + (pageOffset?.value ?: 0f)
        Stamped((through / count.value).coerceIn(0f, 1f), count.updatedAt)
    } else {
        null
    }
    return mapOf(
        "seriesId" to seriesId.json { JsonPrimitive(it) },
        "chapter" to chapter.json { JsonPrimitive(it.toChapterLabel()) },
        "page" to page.json { JsonPrimitive(it) },
        "pageCount" to pageCount.json { JsonPrimitive(it) },
        "pageOffset" to pageOffset.json { JsonPrimitive(it.coerceIn(0f, 1f)) },
        "pagePosition" to position.json { JsonPrimitive(it) },
        "readAt" to readAt.json { JsonPrimitive(it.toString()) }
    )
}

private fun <T> Stamped<T>?.json(value: (T) -> JsonElement): JsonObject? = this?.let {
    buildJsonObject {
        put("value", it.value?.let(value) ?: JsonNull)
        put("updatedAt", JsonPrimitive(it.updatedAt.toString()))
    }
}

/** The record a pulled change gives, or null for an entity or tombstone the app doesn't use. */
internal fun JsonObject.toSyncRecord(): SyncRecord? {
    val id = this["id"]?.jsonPrimitive?.contentOrNull ?: return null
    val fields = (this["fields"] as? JsonObject).orEmpty()
    fun <T> field(name: String, read: (JsonPrimitive) -> T?): Stamped<T>? {
        val field = fields[name] as? JsonObject
        val at = field?.get("updatedAt")?.jsonPrimitive?.contentOrNull?.let(::parseInstant)
        val value = (field?.get("value") as? JsonPrimitive)?.let(read)
        return if (at != null && value != null) Stamped(value, at) else null
    }
    return when (this["entity"]?.jsonPrimitive?.contentOrNull) {
        "series" -> SyncRecord.SeriesRecord(
            id,
            field("title") { it.contentOrNull },
            field("anilistId") { it.longOrNull },
            field("mangaUpdatesId") { it.longOrNull }
        )
        "libraryEntry" -> SyncRecord.Entry(id, field("seriesId") { it.contentOrNull }, field("status", ::status))
        "source" -> SyncRecord.SourceRecord(
            id,
            field("seriesId") { it.contentOrNull },
            field("domain") { it.contentOrNull },
            field("lastChapterUrl") { it.contentOrNull },
            field("lastChapter") { it.contentOrNull?.toBigDecimalOrNull() }
        )
        "chapter" -> SyncRecord.ChapterRecord(
            id,
            field("seriesId") { it.contentOrNull },
            field("number") { it.contentOrNull?.toBigDecimalOrNull() },
            field("read") { it.booleanOrNull },
            field("openedUrl") { it.contentOrNull },
            field("openedDomain") { it.contentOrNull }
        )
        "progress" -> SyncRecord.Progress(
            id,
            field("seriesId") { it.contentOrNull },
            field("chapter") { it.contentOrNull?.toBigDecimalOrNull() },
            field("page") { it.intOrNull },
            field("pageCount") { it.intOrNull },
            field("pageOffset") { it.floatOrNull },
            field("readAt") { it.contentOrNull?.let(::parseInstant) }
        )
        else -> null
    }
}

/** The app has no Dropped yet, so a series dropped elsewhere shows as paused. */
private fun status(value: JsonPrimitive): ReadingStatus? = when (value.contentOrNull) {
    "dropped" -> ReadingStatus.Paused
    else -> Statuses.entries.firstOrNull { it.value == value.contentOrNull }?.key
}

private fun parseInstant(text: String): Instant? = runCatching { Instant.parse(text) }.getOrNull()

/** The records in a pulled page's `changes`. */
internal fun JsonObject.changes(): List<SyncRecord> =
    (this["changes"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toSyncRecord() }
