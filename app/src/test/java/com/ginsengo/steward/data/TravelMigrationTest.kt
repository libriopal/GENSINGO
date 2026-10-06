package com.ginsengo.steward.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ginsengo.steward.data.db.AppDatabase
import com.ginsengo.steward.field.TravelCells
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

/**
 * Upgrading to schema 6 turns every stored track point into travel memory, on the very grid the
 * app writes from then on (J31). Run against a real schema-5 file, opened by the current Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TravelMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A schema-5 file exactly as Room left it: every table and index from 5.json; points are (lat, lng, accuracy, time). */
    private fun schema5(name: String, points: List<DoubleArray>) {
        context.deleteDatabase(name)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        val json = Json.parseToJsonElement(File("schemas/com.ginsengo.steward.data.db.AppDatabase/5.json").readText()) as JsonObject
        val database = json["database"] as JsonObject
        for (e in database["entities"] as JsonArray) {
            e as JsonObject
            val table = (e["tableName"] as JsonPrimitive).content
            db.execSQL((e["createSql"] as JsonPrimitive).content.replace("\${TABLE_NAME}", table))
            (e["indices"] as? JsonArray)?.forEach { ix ->
                db.execSQL(((ix as JsonObject)["createSql"] as JsonPrimitive).content.replace("\${TABLE_NAME}", table))
            }
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${(database["identityHash"] as JsonPrimitive).content}')")
        for (p in points) {
            db.execSQL(
                "INSERT INTO track_points (sessionId, lat, lng, accuracyM, altitudeM, time) VALUES (?, ?, ?, ?, NULL, ?)",
                arrayOf<Any>("s1", p[0], p[1], p[2], p[3].toLong()),
            )
        }
        db.version = 5
        db.close()
    }

    @Test
    fun storedTracksBecomeTheSameCellsTheAppWrites() = runBlocking {
        val pts = listOf(
            doubleArrayOf(35.55003, -82.95007, 5.0, 1_000.0),
            doubleArrayOf(35.55004, -82.95008, 8.0, 2_000.0),   // same cell, later
            doubleArrayOf(35.5, -82.5, 30.0, 3_000.0),          // on a cell corner, at the accuracy limit
            doubleArrayOf(35.6, -83.1, 30.5, 4_000.0),          // vaguer than the recorder keeps
            doubleArrayOf(-12.34567, 45.67891, 4.0, 5_000.0),
        )
        schema5("travel5.db", pts)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "travel5.db")
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        val rows = ArrayList<LongArray>()
        db.openHelper.readableDatabase.query("SELECT cell, firstAt, lastAt FROM visited_cells ORDER BY cell").use {
            while (it.moveToNext()) rows += longArrayOf(it.getLong(0), it.getLong(1), it.getLong(2))
        }
        val expected = listOf(
            longArrayOf(TravelCells.key(-12.34567, 45.67891), 5_000, 5_000),
            longArrayOf(TravelCells.key(35.5, -82.5), 3_000, 3_000),
            longArrayOf(TravelCells.key(35.55003, -82.95007), 1_000, 2_000),
        ).sortedBy { it[0] }
        assertEquals(expected.map { it.toList() }, rows.map { it.toList() })
        assertTrue(TravelCells.key(35.6, -83.1) !in rows.map { it[0] })
        assertEquals(3, db.visitedDao().count())
        db.close()
    }

    @Test
    fun sqliteComputesExactlyTheKotlinKey() {
        val db = SQLiteDatabase.create(null)
        db.execSQL("CREATE TABLE p (lat REAL NOT NULL, lng REAL NOT NULL)")
        val rnd = Random(31)
        val pts = ArrayList<Pair<Double, Double>>()
        // Random ground in the ginseng range, plus points exactly on cell edges.
        repeat(3_000) { pts += (33.0 + rnd.nextDouble() * 14.0) to (-95.0 + rnd.nextDouble() * 28.0) }
        for (i in 0..200) pts += (35.0 + i * 1e-4) to (-83.0 + i * 1e-4)
        db.beginTransaction()
        for ((la, lo) in pts) db.execSQL("INSERT INTO p VALUES (?, ?)", arrayOf<Any>(la, lo))
        db.setTransactionSuccessful(); db.endTransaction()
        var i = 0
        db.rawQuery("SELECT ${TravelCells.SQL_KEY} FROM p ORDER BY rowid", null).use {
            while (it.moveToNext()) {
                val (la, lo) = pts[i++]
                assertEquals("($la, $lo)", TravelCells.key(la, lo), it.getLong(0))
            }
        }
        assertEquals(pts.size, i)
        db.close()
    }

    @Test
    fun theDaoAddsNewCellsAndMovesKnownOnesOn() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val a = TravelCells.key(35.55003, -82.95007)
        val dao = db.visitedDao()
        dao.add(longArrayOf(a, a + 1), 100)
        dao.add(longArrayOf(a + 1, a + 2), 200)
        dao.add(longArrayOf(a), 50) // an older batch arriving late never moves a visit back
        assertEquals(3, dao.count())
        val band = TravelCells.band(35.54, 35.56)
        assertEquals(listOf(a, a + 1, a + 2), dao.between(band.first, band.last).sorted())
        db.openHelper.readableDatabase.query("SELECT cell, firstAt, lastAt FROM visited_cells ORDER BY cell").use {
            val got = ArrayList<List<Long>>()
            while (it.moveToNext()) got += listOf(it.getLong(0), it.getLong(1), it.getLong(2))
            assertEquals(listOf(listOf(a, 100L, 100L), listOf(a + 1, 100L, 200L), listOf(a + 2, 200L, 200L)), got)
        }
        db.close()
    }
}
