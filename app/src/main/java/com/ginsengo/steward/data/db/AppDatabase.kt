package com.ginsengo.steward.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        GinsengPatch::class,
        HabitatReadingRecord::class,
        TrackPoint::class,
        Find::class,
        Suggestion::class,
        ResearchRun::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun patchDao(): PatchDao
    abstract fun habitatReadingDao(): HabitatReadingDao
    abstract fun trackDao(): TrackDao
    abstract fun findDao(): FindDao
    abstract fun suggestionDao(): SuggestionDao
    abstract fun researchRunDao(): ResearchRunDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * NO destructive fallback. The previous build used fallbackToDestructiveMigration(),
         * which made "persistent memory" last exactly until the next schema change, and
         * installing it over the field-tested build (schema 1) would have deleted every patch
         * logged in the field. A missing migration now fails loudly in testing instead of
         * silently at a user's first launch.
         */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gensingo.db",
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { instance = it }
            }

        /**
         * Schema 5 tables, byte-for-byte as Room generates them (pinned against the exported
         * schema by MigrationSqlTest, so a hand edit that drifts fails the build).
         */
        val SCHEMA_5_CREATE = listOf(
            "CREATE TABLE IF NOT EXISTS `track_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `accuracyM` REAL NOT NULL, `altitudeM` REAL, `time` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_track_points_time` ON `track_points` (`time`)",
            "CREATE INDEX IF NOT EXISTS `index_track_points_sessionId` ON `track_points` (`sessionId`)",
            "CREATE TABLE IF NOT EXISTS `finds` (`id` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `accuracyM` REAL, `fixCount` INTEGER NOT NULL, `fixTime` INTEGER, `time` INTEGER NOT NULL, `plantCount` INTEGER NOT NULL, `maxProngs` INTEGER, `note` TEXT NOT NULL, `checks` INTEGER NOT NULL, `verification` TEXT NOT NULL, `fHeat` REAL, `fPosition` REAL, `fWetness` REAL, `fSlope` REAL, `fCurvature` REAL, `fElevation` REAL, `featureZoom` INTEGER, `sourcePatchId` TEXT, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_finds_time` ON `finds` (`time`)",
            "CREATE TABLE IF NOT EXISTS `suggestions` (`id` TEXT NOT NULL, `runId` TEXT NOT NULL, `candidateKey` TEXT NOT NULL, `label` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `rank` INTEGER NOT NULL, `terrainScore` REAL NOT NULL, `factorsCsv` TEXT NOT NULL, `elevationM` REAL NOT NULL, `slopeDeg` REAL NOT NULL, `aspectDeg` REAL NOT NULL, `headline` TEXT NOT NULL, `rationale` TEXT NOT NULL, `lookFor` TEXT NOT NULL, `sourcesJson` TEXT NOT NULL, `provenance` TEXT NOT NULL, `status` TEXT NOT NULL, `statusTime` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_suggestions_runId` ON `suggestions` (`runId`)",
            "CREATE INDEX IF NOT EXISTS `index_suggestions_status` ON `suggestions` (`status`)",
            "CREATE TABLE IF NOT EXISTS `research_runs` (`id` TEXT NOT NULL, `time` INTEGER NOT NULL, `centerLat` REAL NOT NULL, `centerLng` REAL NOT NULL, `radiusM` REAL NOT NULL, `provider` TEXT, `model` TEXT, `status` TEXT NOT NULL, `message` TEXT, `summary` TEXT, `candidatesComputed` INTEGER NOT NULL, `idsRejected` INTEGER NOT NULL, `citationsKept` INTEGER NOT NULL, `citationsRejected` INTEGER NOT NULL, `weights` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_research_runs_time` ON `research_runs` (`time`)",
        )

        /**
         * Patches logged by the older app become finds, confirmed like any other: the user's
         * field data is treated as true and accurate (UserFinds), so they are drawn at full
         * weight and learned from. `sourcePatchId` records where each came from; the patch
         * rows themselves are left in place.
         */
        const val IMPORT_PATCHES =
            "INSERT OR IGNORE INTO `finds` (`id`, `lat`, `lng`, `accuracyM`, `fixCount`, `fixTime`, " +
                "`time`, `plantCount`, `maxProngs`, `note`, `checks`, `verification`, `sourcePatchId`) " +
                "SELECT 'patch-' || `id`, `lat`, `lng`, NULL, 0, NULL, `lastVisitedDate`, `plantCount`, " +
                "NULL, `name` || CASE WHEN `notes` = '' THEN '' ELSE ' - ' || `notes` END, 0, 'VERIFIED', `id` " +
                "FROM `ginseng_patches`"

        private fun upgradeTo5(db: SupportSQLiteDatabase) {
            SCHEMA_5_CREATE.forEach(db::execSQL)
            db.execSQL(IMPORT_PATCHES)
        }

        /** From the field-tested build (patches and readings only). */
        val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = upgradeTo5(db)
        }

        /**
         * From the headless build. Its five extra tables (observations, Monte Carlo logs, radius
         * buffers, harvest polygons, tours) are left in place, unread: some hold user input,
         * and their coordinates cannot be trusted (the survey code fell back to a fixed
         * 35.50, -82.95 when there was no fix), so they are neither deleted nor imported.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = upgradeTo5(db)
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_5, MIGRATION_4_5)
    }
}
