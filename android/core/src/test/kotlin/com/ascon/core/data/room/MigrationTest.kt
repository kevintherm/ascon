package com.ascon.core.data.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each migration, run on a database made from the previous exported schema. Opening it
 * with Room migrates it and checks the result against the current schema.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    /** A database at [version], built from its schema in core/schemas. */
    private fun create(version: Int, fill: SQLiteDatabase.() -> Unit) {
        val schema = JSONObject(File("schemas/${AsconDatabase::class.java.name}/$version.json").readText())
            .getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        db.fill()
        db.close()
    }

    @Test
    fun `version 1 to 2 keeps sources and adds no chapter page yet`() = runBlocking {
        create(1) {
            execSQL(
                "INSERT INTO series VALUES ('aztec', 'Aztec', '[]', 1, 2, 3, 'Reading', 0, NULL)"
            )
            execSQL("INSERT INTO source VALUES ('aztec', 'site.example', 0, 'site.example', 0, '1', '12')")
        }

        val db = AsconDatabase.open(context, name)
        val series = RoomLibraryRepository(db).series("aztec").first()!!
        db.close()

        val source = series.sources.single()
        assertEquals("site.example", source.id)
        assertEquals(BigDecimal(12), source.lastChapter)
        assertNull(source.lastOpened)
    }

    @Test
    fun `version 2 to 3 keeps progress at the top of its page`() = runBlocking {
        create(2) {
            execSQL(
                "INSERT INTO series VALUES ('aztec', 'Aztec', '[]', 1, 2, 3, 'Reading', 0, NULL)"
            )
            execSQL("INSERT INTO source VALUES ('aztec', 'site.example', 0, 'site.example', 0, '1', '12', NULL, NULL)")
            execSQL("INSERT INTO progress VALUES ('aztec', '12', 5, 40, 'site.example')")
        }

        val db = AsconDatabase.open(context, name)
        val progress = RoomLibraryRepository(db).series("aztec").first()!!.progress!!
        db.close()

        assertEquals(5, progress.page)
        assertEquals(40, progress.pageCount)
        assertEquals(0f, progress.pageOffset)
    }

    @Test
    fun `version 3 to 4 keeps the library and starts with no rules`() = runBlocking {
        create(3) {
            execSQL(
                "INSERT INTO series VALUES ('aztec', 'Aztec', '[]', 1, 2, 3, 'Reading', 0, NULL)"
            )
            execSQL("INSERT INTO source VALUES ('aztec', 'site.example', 0, 'site.example', 0, '1', '12', NULL, NULL)")
            execSQL("INSERT INTO progress VALUES ('aztec', '12', 5, 40, 'site.example', 0.5)")
        }

        val db = AsconDatabase.open(context, name)
        val progress = RoomLibraryRepository(db).series("aztec").first()!!.progress!!
        val store = RoomRuleStore(db)
        val rule = store.rule("site.example")
        val health = store.health()
        db.close()

        assertEquals(0.5f, progress.pageOffset)
        assertNull(rule)
        assertEquals(emptyList<Any>(), health)
    }

    @Test
    fun `version 4 to 5 keeps chapters, with no page they were opened at yet`() = runBlocking {
        create(4) {
            execSQL(
                "INSERT INTO series VALUES ('aztec', 'Aztec', '[]', 1, 2, 3, 'Reading', 0, NULL)"
            )
            execSQL("INSERT INTO chapter VALUES ('aztec', '12', NULL, 1, 0, 0, 'site.example')")
        }

        val db = AsconDatabase.open(context, name)
        val chapter = RoomLibraryRepository(db).series("aztec").first()!!.chapters.single()
        db.close()

        assertEquals(BigDecimal(12), chapter.number)
        assertEquals("site.example", chapter.readOnSourceId)
        assertNull(chapter.openedUrl)
    }

    @Test
    fun `version 5 to 6 keeps the library, with nothing synced yet`() = runBlocking {
        create(5) {
            execSQL("INSERT INTO series VALUES ('aztec', 'Aztec', '[]', 1, 2, 3, 'Reading', 0, NULL)")
            execSQL("INSERT INTO source VALUES ('aztec', 'site.example', 0, 'site.example', 0, '1', '12', NULL, NULL)")
            execSQL("INSERT INTO progress VALUES ('aztec', '12', 5, 40, 'site.example', 0.5)")
        }

        val db = AsconDatabase.open(context, name)
        val series = RoomLibraryRepository(db).series("aztec").first()!!
        db.close()

        assertEquals(5, series.progress?.page)
        assertNull(series.syncId)
        assertNull(series.progress?.updatedAt)
        assertNull(series.sources.single().updatedAt)
    }

    @Test
    fun `version 6 to 7 keeps chapters, not yet stamped for sync`() = runBlocking {
        create(6) {
            execSQL("INSERT INTO series VALUES ('aztec', 'Aztec', '[]', 1, 2, 3, 'Reading', 0, NULL, NULL, NULL)")
            execSQL(
                "INSERT INTO chapter VALUES " +
                    "('aztec', '12', NULL, 1, 0, 0, NULL, 'https://site.example/12', 'site.example')"
            )
        }

        val db = AsconDatabase.open(context, name)
        val chapter = RoomLibraryRepository(db).series("aztec").first()!!.chapters.single()
        db.close()

        assertEquals("https://site.example/12", chapter.openedUrl)
        assertNull(chapter.updatedAt)
    }
}
