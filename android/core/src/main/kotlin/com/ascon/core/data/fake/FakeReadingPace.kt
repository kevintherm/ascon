package com.ascon.core.data.fake

import com.ascon.core.data.ReadingPaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** A reading pace in memory, for previews and tests. */
class FakeReadingPace(samples: List<Float> = emptyList()) : ReadingPaceRepository {
    private val all = MutableStateFlow(samples)

    override val secondsPerImage: StateFlow<List<Float>> = all

    override suspend fun recordSecondsPerImage(seconds: Float) {
        all.update { it + seconds }
    }
}
