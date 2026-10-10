package com.ascon.core.data.room

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.ascon.core.model.ReadingStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

// Tables follow AGENTS.md: series, source, chapter, progress. Chapter numbers are stored
// as plain decimal text, so 10.5 stays exact and 12 and 12.0 are one chapter.

@Entity(tableName = "series")
internal data class SeriesEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "alt_titles") val altTitles: List<String>,
    @ColumnInfo(name = "cover_top") val coverTop: Long,
    @ColumnInfo(name = "cover_middle") val coverMiddle: Long,
    @ColumnInfo(name = "cover_bottom") val coverBottom: Long,
    val status: ReadingStatus,
    @ColumnInfo(name = "linked_to_anilist") val linkedToAniList: Boolean,
    @ColumnInfo(name = "last_read_at") val lastReadAt: Instant?,
    /** Sync, added in version 6. */
    @ColumnInfo(name = "sync_id") val syncId: String? = null,
    @ColumnInfo(name = "status_updated_at") val statusUpdatedAt: Instant? = null
)

private const val SERIES_ID = "series_id"

@Entity(
    tableName = "source",
    primaryKeys = [SERIES_ID, "id"],
    foreignKeys = [ForeignKey(SeriesEntity::class, ["id"], [SERIES_ID], onDelete = ForeignKey.CASCADE)]
)
internal data class SourceEntity(
    @ColumnInfo(name = SERIES_ID) val seriesId: String,
    val id: String,
    /** Order on the series screen. */
    val position: Int,
    @ColumnInfo(name = "site_name") val siteName: String,
    val official: Boolean,
    @ColumnInfo(name = "first_chapter") val firstChapter: BigDecimal,
    @ColumnInfo(name = "last_chapter") val lastChapter: BigDecimal,
    /** The chapter page last opened on this site, and its chapter. Added in version 2. */
    @ColumnInfo(name = "last_opened_url") val lastOpenedUrl: String? = null,
    @ColumnInfo(name = "last_opened_chapter") val lastOpenedChapter: BigDecimal? = null,
    /** Added in version 6. */
    @ColumnInfo(name = "updated_at") val updatedAt: Instant? = null
)

@Entity(
    tableName = "chapter",
    primaryKeys = [SERIES_ID, "number"],
    foreignKeys = [ForeignKey(SeriesEntity::class, ["id"], [SERIES_ID], onDelete = ForeignKey.CASCADE)]
)
internal data class ChapterEntity(
    @ColumnInfo(name = SERIES_ID) val seriesId: String,
    val number: BigDecimal,
    @ColumnInfo(name = "published_on") val publishedOn: LocalDate?,
    val read: Boolean,
    @ColumnInfo(name = "is_new") val isNew: Boolean,
    val downloaded: Boolean,
    @ColumnInfo(name = "read_on_source_id") val readOnSourceId: String?,
    /** The chapter page the chapter was last opened at, and its source. Added in version 5. */
    @ColumnInfo(name = "opened_url") val openedUrl: String? = null,
    @ColumnInfo(name = "opened_source_id") val openedOnSourceId: String? = null
)

@Entity(
    tableName = "progress",
    foreignKeys = [ForeignKey(SeriesEntity::class, ["id"], [SERIES_ID], onDelete = ForeignKey.CASCADE)]
)
internal data class ProgressEntity(
    @PrimaryKey @ColumnInfo(name = SERIES_ID) val seriesId: String,
    val chapter: BigDecimal,
    val page: Int,
    @ColumnInfo(name = "page_count") val pageCount: Int,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "page_offset", defaultValue = "0") val pageOffset: Float,
    /** Added in version 6. */
    @ColumnInfo(name = "updated_at") val updatedAt: Instant? = null
)

@Entity(tableName = "site", indices = [Index("position")])
internal data class SiteEntity(
    @PrimaryKey val domain: String,
    val name: String,
    val monogram: String,
    val position: Int
)

/** A series row with everything that hangs off it. */
internal data class SeriesRecord(
    @Embedded val series: SeriesEntity,
    @Relation(parentColumn = "id", entityColumn = SERIES_ID) val sources: List<SourceEntity>,
    @Relation(parentColumn = "id", entityColumn = SERIES_ID) val chapters: List<ChapterEntity>,
    @Relation(parentColumn = "id", entityColumn = SERIES_ID) val progress: ProgressEntity?
)
