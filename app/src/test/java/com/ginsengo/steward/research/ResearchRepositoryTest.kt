package com.ginsengo.steward.research

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ginsengo.steward.Fixtures
import com.ginsengo.steward.SettingsStore
import com.ginsengo.steward.compliance.ComplianceEngine
import com.ginsengo.steward.data.db.Find
import com.ginsengo.steward.data.db.AppDatabase
import com.ginsengo.steward.data.db.Suggestion
import com.ginsengo.steward.data.db.TrackPoint
import com.ginsengo.steward.data.reference.ReferenceRepository
import com.ginsengo.steward.field.FixAverager
import com.ginsengo.steward.learn.UserFinds
import com.ginsengo.steward.memory.FieldMemoryRepository
import com.ginsengo.steward.terrain.DemTileStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The orchestration rules that no pure test can see: consent, keys, connectivity, failure
 * handling, and what gets remembered. Real Room database, real terrain fixture, a fake model.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResearchRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private val mosaic = Fixtures.mosaic("scan_boone_z12.bin")
    private val lat = (mosaic.northLat + mosaic.southLat) / 2
    private val lng = (mosaic.westLon + mosaic.eastLon) / 2

    private var calls = 0
    private var lastRequest: ResearchRequest? = null
    private var online = true
    private var hasKey = true
    private var behaviour: (ResearchRequest) -> ProviderReply = { req ->
        ProviderReply(
            ModelOutput("Regional context.", listOf(
                ModelItem(req.candidates[1].id, "Second is best", "Because.", "Maidenhair fern",
                    listOf("https://extension.psu.edu/ginseng", "https://invented.example/x")),
                ModelItem("C99", "ghost", "not a candidate", "", emptyList()),
            )),
            listOf(SourceRef("https://extension.psu.edu/ginseng", "PSU")),
            "claude-opus-5-5",
        )
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsStore(context).apply { researchConsent = true; provider = Provider.CLAUDE }
    }

    @After
    fun tearDown() = db.close()

    private fun repo(): ResearchRepository {
        return ResearchRepository(
            db = db,
            dem = DemTileStore(context),
            compliance = ComplianceEngine(ReferenceRepository(context)),
            settings = settings,
            keys = FakeKeys(context) { hasKey },
            isOnline = { online },
            // A 6 km radius fits the 2x2 fixture; the rule under test does not depend on size.
            mosaicFor = { mosaic },
            clientFor = { _, _, _ -> ResearchClient { req -> calls++; lastRequest = req; behaviour(req) } },
            radiusM = 6_000.0,
        )
    }

    @Test
    fun withoutConsentNothingIsSentAndSuggestionsStillArrive() = runBlocking {
        settings.researchConsent = false
        val run = repo().run(lat, lng)
        assertEquals(0, calls)
        assertEquals("NO_CONSENT", run.status)
        val s = db.suggestionDao().all()
        assertTrue(s.isNotEmpty())
        assertTrue(s.all { it.provenance == Suggestion.PROVENANCE_COMPUTED })
    }

    @Test
    fun withoutAKeyOrOfflineNothingIsSent() = runBlocking {
        hasKey = false
        assertEquals("NO_KEY", repo().run(lat, lng).status)
        hasKey = true; online = false
        assertEquals("OFFLINE", repo().run(lat, lng).status)
        assertEquals(0, calls)
    }

    /** An automatic refresh the auto-research policy did not approve sends nothing. */
    @Test
    fun anUnapprovedAutomaticRefreshStaysOnThePhone() = runBlocking {
        val run = repo().run(lat, lng, allowModel = false)
        assertEquals(0, calls)
        assertEquals("LOCAL_REFRESH", run.status)
        assertTrue(db.suggestionDao().all().isNotEmpty())
    }

    @Test
    fun aModelRunIsValidatedAndAudited() = runBlocking {
        val run = repo().run(lat, lng)
        assertEquals(1, calls)
        assertEquals("OK", run.status)
        assertEquals(1, run.idsRejected)          // C99
        assertEquals(1, run.citationsKept)
        assertEquals(1, run.citationsRejected)    // invented.example
        val s = db.suggestionDao().all().sortedBy { it.rank }
        assertEquals(Suggestion.PROVENANCE_MODEL, s.first().provenance)
        assertEquals("C2", s.first().label)
        assertFalse(s.any { it.sourcesJson.contains("invented.example") })
        // The request carried no position finer than the 0.1-degree cell.
        val text = ResearchPrompt.user(lastRequest!!)
        assertFalse(text.contains("%.3f".format(lat)))
    }

    @Test
    fun aFailedCallIsStoredAsSuchAndTheComputedRankingStillShows() = runBlocking {
        behaviour = { throw ResearchFailure(ResearchFailure.Status.REFUSED, "Declined") }
        val run = repo().run(lat, lng)
        assertEquals("REFUSED", run.status)
        assertTrue(run.message!!.contains("on-device"))
        assertTrue(db.suggestionDao().all().all { it.provenance == Suggestion.PROVENANCE_COMPUTED })
    }

    @Test
    fun theLifecycleFollowsEvidence() = runBlocking {
        repo().run(lat, lng)
        val memory = FieldMemoryRepository(db)
        val target = db.suggestionDao().all().first()

        // A track that passes 500 m away does not visit it.
        memory.storeTrack(listOf(TrackPoint(sessionId = "s", lat = target.lat + 0.0045, lng = target.lng, accuracyM = 5f, altitudeM = null, time = 1)))
        assertEquals(Suggestion.STATUS_NEW, db.suggestionDao().all().first { it.id == target.id }.status)

        // One that passes within 40 m does.
        memory.storeTrack(listOf(TrackPoint(sessionId = "s", lat = target.lat + 0.0002, lng = target.lng, accuracyM = 5f, altitudeM = null, time = 2)))
        assertEquals(Suggestion.STATUS_VISITED, db.suggestionDao().all().first { it.id == target.id }.status)

        // A find within 60 m makes it FOUND.
        val fix = FixAverager.Result(target.lat + 0.0003, target.lng, 8f, 10, 1_000)
        val find = memory.addFind(fix, 5_000, 3, 3, "")
        assertEquals("VERIFIED", find.verification)
        assertEquals(Suggestion.STATUS_FOUND, db.suggestionDao().all().first { it.id == target.id }.status)
    }

    /** The user's word is the record: a poor or stale GPS fix never demotes a find. */
    @Test
    fun noFindTheUserEntersIsSavedAsLess() = runBlocking {
        val memory = FieldMemoryRepository(db)
        val poor = memory.addFind(FixAverager.Result(lat, lng, 35f, 1, 0), 1_000, 1, null, "")
        val stale = memory.addFind(FixAverager.Result(lat, lng, 5f, 1, 0), 600_000, 1, null, "")
        val awful = memory.addFind(FixAverager.Result(lat, lng, 250f, 1, 0), 3_600_000, 1, null, "")
        for (f in listOf(poor, stale, awful)) assertEquals(UserFinds.CONFIRMED, f.verification)
        // What the phone measured is kept, as information.
        assertEquals(250f, awful.accuracyM!!, 0f)
    }

    /**
     * Learning must block its held-out test at the terrain's measured correlation range, not
     * at a fixed 200 m. Six verified finds 300 m apart in a line: at 200 m they would be six
     * "independent" spots; at this terrain's range they are one.
     */
    @Test
    fun learningBlocksAtTheMeasuredRange() = runBlocking {
        val memory = FieldMemoryRepository(db)
        repeat(6) { i ->
            memory.addFind(FixAverager.Result(lat + i * 0.0027, lng, 8f, 5, 0), 1_000, 2, 3, "")
        }
        val r = repo()
        val s = r.scanAround(lat, lng)!!
        assertTrue("range ${s.correlationRangeM()} m", s.correlationRangeM() > 300.0)
        val v = r.learn(s)
        assertEquals(6, v.findsUsed)
        assertEquals("finds 300 m apart counted as separate spots", 1, v.clusters)
    }

    /**
     * Every find the user entered teaches the learner: imprecise and stale fixes, and patches
     * imported from the older app with no accuracy recorded at all, exactly like the rest.
     */
    @Test
    fun everyFindTheUserEnteredReachesTheLearner() = runBlocking {
        val memory = FieldMemoryRepository(db)
        repeat(4) { i ->
            memory.addFind(FixAverager.Result(lat + i * 0.005, lng, 30f, 1, 0), 600_000, 1, null, "")
        }
        // As MIGRATION_1_5 imports them: no accuracy, no fix time, a source patch id.
        repeat(3) { i ->
            db.findDao().upsert(Find(
                id = "patch-p$i", lat = lat - (i + 1) * 0.005, lng = lng, accuracyM = null, fixCount = 0,
                fixTime = null, time = 1_000, plantCount = 5, maxProngs = null, note = "Old patch $i",
                checks = 0, verification = UserFinds.CONFIRMED, sourcePatchId = "p$i",
            ))
        }
        val r = repo()
        val v = r.learn(r.scanAround(lat, lng)!!)
        assertEquals(7, v.findsUsed)
        // ...and every find in range now carries its terrain factors for later.
        assertTrue(db.findDao().all().all { it.factors() != null })
    }
}

/** KeyVault needs the Android Keystore, which Robolectric does not provide. */
private class FakeKeys(context: Context, private val present: () -> Boolean) : KeyVault(context) {
    override fun get(provider: Provider): String? = if (present()) "test-key" else null
    override fun has(provider: Provider): Boolean = present()
}
