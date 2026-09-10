package com.discomplemented.ginseng.domain.usecase

import com.discomplemented.ginseng.data.local.database.entity.TrackNodeEntity
import com.discomplemented.ginseng.domain.repository.TrackRepository
import javax.inject.Inject

/**
 * Use case for logging a ginseng patch discovery.
 */
class LogGinsengPatchUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    suspend operator fun invoke(node: TrackNodeEntity): Long {
        return trackRepository.insertTrack(node)
    }
}
