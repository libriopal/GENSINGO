package com.ginsengo.steward.memory

import com.ginsengo.steward.data.db.AppDatabase
import com.ginsengo.steward.data.db.Find
import com.ginsengo.steward.data.db.Suggestion
import com.ginsengo.steward.data.db.TrackPoint
import com.ginsengo.steward.field.FixAverager
import com.ginsengo.steward.learn.FindVerifier
import com.ginsengo.steward.prospect.Prospects
import kotlinx.coroutines.flow.Flow

/**
 * Where you have been, what you found, and what became of each suggestion.
 *
 * The suggestion lifecycle is driven by evidence rather than by taps wherever it can be:
 * a suggestion becomes VISITED when a recorded track actually passes within [VISIT_M] of it,
 * and FOUND when a find is recorded within [FOUND_M]. Only NOT_FOUND ("walked it, none
 * there") needs the user to say so, because absence cannot be observed.
 */
class FieldMemoryRepository(private val db: AppDatabase) {

    fun trackSince(since: Long): Flow<List<TrackPoint>> = db.trackDao().observeSince(since)
    fun finds(): Flow<List<Find>> = db.findDao().observeAll()

    suspend fun lastPoint(sessionId: String): TrackPoint? = db.trackDao().lastOf(sessionId)

    /** Stores filtered track points and marks any open suggestion they pass as VISITED. */
    suspend fun storeTrack(points: List<TrackPoint>) {
        if (points.isEmpty()) return
        db.trackDao().insertAll(points)
        val open = db.suggestionDao().open().filter { it.status == Suggestion.STATUS_NEW }
        for (s in open) {
            val hit = points.firstOrNull {
                Prospects.distanceMetres(it.lat, it.lng, s.lat, s.lng) <= VISIT_M
            } ?: continue
            db.suggestionDao().setStatus(s.id, Suggestion.STATUS_VISITED, hit.time)
        }
    }

    /**
     * Records a find. Verification is computed here from the measured fix, never passed in,
     * so no caller can declare a find verified.
     */
    suspend fun addFind(
        fix: FixAverager.Result,
        now: Long,
        plantCount: Int,
        maxProngs: Int?,
        note: String,
        checksMask: Int,
    ): Find {
        val verdict = FindVerifier.verify(fix.accuracyM, now - fix.newestTime, checksMask)
        val find = Find(
            lat = fix.lat, lng = fix.lng,
            accuracyM = fix.accuracyM,
            fixCount = fix.fixCount,
            fixTime = fix.newestTime,
            time = now,
            plantCount = plantCount,
            maxProngs = maxProngs,
            note = note.trim(),
            checks = checksMask,
            verification = verdict.level.name,
        )
        db.findDao().upsert(find)
        for (s in db.suggestionDao().open()) {
            if (Prospects.distanceMetres(find.lat, find.lng, s.lat, s.lng) <= FOUND_M) {
                db.suggestionDao().setStatus(s.id, Suggestion.STATUS_FOUND, now)
            }
        }
        return find
    }

    suspend fun deleteFind(find: Find) = db.findDao().delete(find)

    suspend fun markNotFound(suggestionId: String, now: Long) =
        db.suggestionDao().setStatus(suggestionId, Suggestion.STATUS_NOT_FOUND, now)

    companion object {
        const val VISIT_M = 40.0
        const val FOUND_M = 60.0
    }
}
