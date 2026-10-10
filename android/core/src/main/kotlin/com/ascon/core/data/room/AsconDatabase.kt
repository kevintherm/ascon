package com.ascon.core.data.room

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** The library on this device. */
@Database(
    entities = [
        SeriesEntity::class,
        SourceEntity::class,
        ChapterEntity::class,
        ProgressEntity::class,
        SiteEntity::class
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
@TypeConverters(Converters::class)
abstract class AsconDatabase : RoomDatabase() {
    internal abstract fun library(): LibraryDao

    companion object {
        fun open(context: Context, name: String = "ascon.db"): AsconDatabase =
            Room.databaseBuilder(context.applicationContext, AsconDatabase::class.java, name).build()
    }
}
