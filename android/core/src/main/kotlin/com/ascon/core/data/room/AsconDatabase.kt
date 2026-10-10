package com.ascon.core.data.room

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** The library on this device, and the detection rules it has fetched. */
@Database(
    entities = [
        SeriesEntity::class,
        SourceEntity::class,
        ChapterEntity::class,
        ProgressEntity::class,
        SiteEntity::class,
        RuleEntity::class,
        RuleHealthEntity::class
    ],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4)
    ]
)
@TypeConverters(Converters::class)
abstract class AsconDatabase : RoomDatabase() {
    internal abstract fun library(): LibraryDao

    internal abstract fun rules(): RuleDao

    companion object {
        fun open(context: Context, name: String = "ascon.db"): AsconDatabase =
            Room.databaseBuilder(context.applicationContext, AsconDatabase::class.java, name).build()
    }
}
