package com.ginsengo.steward.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ginsengo.steward.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrading keeps the user's data. Run against real SQLite files built the way the older
 * builds left them, then opened by the current Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Exactly Room's createSql for these two entities, unchanged since schema 1.
    private val v1Tables = listOf(
        "CREATE TABLE IF NOT EXISTS `ginseng_patches` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `photoPath` TEXT, `plantCount` INTEGER NOT NULL, `notes` TEXT NOT NULL, `habitatScore` REAL, `harvested` INTEGER NOT NULL, `rootsHarvested` INTEGER, `seedsReplanted` INTEGER, `lastVisitedDate` INTEGER NOT NULL, `provenance` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `habitat_readings` (`id` TEXT NOT NULL, `patchId` TEXT, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `slopeOrientation` TEXT NOT NULL, `slopePosition` TEXT NOT NULL, `treesPresent` TEXT NOT NULL, `companionPlantsSeen` TEXT NOT NULL, `soilCheck` INTEGER NOT NULL, `result` TEXT NOT NULL, `modelScore` REAL, `modelShareFromAnswers` REAL, `createdDate` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'schema-1')",
    )

    /** The headless build's extra tables. Their exact columns do not matter: v5 leaves them alone. */
    private val v4Extra = listOf(
        "CREATE TABLE IF NOT EXISTS `ginseng_observations` (`id` TEXT NOT NULL, `county` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `monte_carlo_logs` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `radius_buffers` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `verified_harvest_polygons` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `prospecting_tours` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "INSERT INTO `ginseng_observations` VALUES ('o1', 'Haywood')",
    )

    private fun legacyDb(name: String, version: Int, extra: List<String> = emptyList()) {
        context.deleteDatabase(name)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        (v1Tables + extra).forEach(db::execSQL)
        db.execSQL("INSERT INTO ginseng_patches VALUES ('p1', 'Big cove', 35.51, -82.97, NULL, 12, 'north side', NULL, 0, NULL, NULL, 1700000000000, 'prototype')")
        db.execSQL("INSERT INTO habitat_readings VALUES ('r1', 'p1', 35.51, -82.97, 'NE', 'lower', 'Sugar maple\u001FTulip poplar', 'Black cohosh', 1, 'good', NULL, NULL, 1700000000000)")
        db.version = version
        db.close()
    }

    private fun open(name: String, withMigrations: Boolean = true) =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .apply { if (withMigrations) addMigrations(*AppDatabase.MIGRATIONS) }
            .allowMainThreadQueries()
            .build()

    @Test
    fun fromTheFieldTestedBuildNothingIsLost() = runBlocking {
        legacyDb("v1.db", 1)
        val db = open("v1.db")
        val patches = db.patchDao().observeAll().first()
        assertEquals(listOf("Big cove"), patches.map { it.name })
        val reading = db.habitatReadingDao().observeAll().first().single()
        assertEquals(listOf("Sugar maple", "Tulip poplar"), reading.treesPresent)
        assertEquals(listOf("Black cohosh"), reading.companionPlantsSeen)
        val finds = db.findDao().all()
        assertEquals(1, finds.size)
        assertEquals("LEGACY", finds.single().verification)
        assertEquals("p1", finds.single().sourcePatchId)
        assertEquals(12, finds.single().plantCount)
        assertEquals(35.51, finds.single().lat, 0.0)
        db.close()
    }

    @Test
    fun fromTheHeadlessBuildItsTablesAreLeftInPlace() = runBlocking {
        legacyDb("v4.db", 4, v4Extra)
        val db = open("v4.db")
        assertEquals(1, db.findDao().all().size)
        db.openHelper.readableDatabase.query("SELECT county FROM ginseng_observations").use {
            assertTrue(it.moveToFirst())
            assertEquals("Haywood", it.getString(0))
        }
        db.close()
    }

    /** NEGATIVE CONTROL: without migrations the same file must refuse to open. */
    @Test
    fun withoutMigrationsTheSameFileFails() {
        legacyDb("v1-bare.db", 1)
        val db = open("v1-bare.db", withMigrations = false)
        try {
            db.openHelper.writableDatabase
            fail("a schema-1 database opened at schema 5 with no migration")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("igration"))
        } finally {
            db.close()
        }
    }
}
