package com.ascon.core.data.room

import com.ascon.core.model.Chapter
import com.ascon.core.model.ChapterLink
import com.ascon.core.model.Cover
import com.ascon.core.model.ReadingProgress
import com.ascon.core.model.Series
import com.ascon.core.model.Site
import com.ascon.core.model.Source

internal fun SeriesRecord.toModel(): Series {
    val cover = Cover.Placeholder(series.coverTop, series.coverMiddle, series.coverBottom)
    return Series(
        id = series.id,
        title = series.title,
        altTitles = series.altTitles,
        cover = cover,
        status = series.status,
        linkedToAniList = series.linkedToAniList,
        sources = sources.sortedBy { it.position }.map {
            val link = if (it.lastOpenedUrl != null && it.lastOpenedChapter != null) {
                ChapterLink(it.lastOpenedUrl, it.lastOpenedChapter)
            } else {
                null
            }
            Source(it.id, it.siteName, it.official, it.firstChapter, it.lastChapter, link, it.updatedAt)
        },
        chapters = chapters.sortedBy { it.number }.map {
            Chapter(
                it.number,
                it.publishedOn,
                it.read,
                it.isNew,
                it.downloaded,
                it.readOnSourceId,
                it.openedUrl,
                it.openedOnSourceId,
                it.updatedAt
            )
        },
        progress = progress?.let {
            ReadingProgress(it.chapter, it.page, it.pageCount, it.sourceId, it.pageOffset, it.updatedAt)
        },
        lastReadAt = series.lastReadAt,
        syncId = series.syncId,
        statusUpdatedAt = series.statusUpdatedAt,
        aniListId = series.aniListId,
        mangaUpdatesId = series.mangaUpdatesId,
        linkUpdatedAt = series.linkUpdatedAt
    )
}

internal fun Series.toRecord(): SeriesRecord {
    // The only cover kind so far. Real covers will add a column.
    val placeholder = cover as Cover.Placeholder
    return SeriesRecord(
        series = SeriesEntity(
            id,
            title,
            altTitles,
            placeholder.top,
            placeholder.middle,
            placeholder.bottom,
            status,
            linkedToAniList,
            lastReadAt,
            syncId,
            statusUpdatedAt,
            aniListId,
            mangaUpdatesId,
            linkUpdatedAt
        ),
        sources = sources.mapIndexed { position, source ->
            SourceEntity(
                id,
                source.id,
                position,
                source.siteName,
                source.official,
                source.firstChapter,
                source.lastChapter,
                source.lastOpened?.url,
                source.lastOpened?.chapter,
                source.updatedAt
            )
        },
        chapters = chapters.map {
            ChapterEntity(
                id,
                it.number,
                it.publishedOn,
                it.read,
                it.isNew,
                it.downloaded,
                it.readOnSourceId,
                it.openedUrl,
                it.openedOnSourceId,
                it.updatedAt
            )
        },
        progress = progress?.let {
            ProgressEntity(id, it.chapter, it.page, it.pageCount, it.sourceId, it.pageOffset, it.updatedAt)
        }
    )
}

internal fun SiteEntity.toModel() = Site(domain, name, monogram)
