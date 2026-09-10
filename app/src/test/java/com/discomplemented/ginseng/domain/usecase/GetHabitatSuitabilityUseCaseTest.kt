package com.discomplemented.ginseng.domain.usecase

import com.discomplemented.ginseng.ai.feature.FeatureExtractor
import com.discomplemented.ginseng.ai.inference.HabitatInferenceManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class GetHabitatSuitabilityUseCaseTest {

    @Mock
    private lateinit var inferenceManager: HabitatInferenceManager

    @Mock
    private lateinit var featureExtractor: FeatureExtractor

    private lateinit var useCase: GetHabitatSuitabilityUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        useCase = GetHabitatSuitabilityUseCase(inferenceManager, featureExtractor)
    }

    @Test
    fun `invoke should coordinate extraction and inference`() = runBlocking {
        // Given
        val lat = 35.5
        val lon = -82.5
        val alt = 500f
        val canopy = 0.7f
        val slope = 15f
        val aspect = 45f
        val moisture = 0.6f
        val expectedScore = 0.85f

        val mockFeatureSet = com.discomplemented.ginseng.ai.feature.FeatureSet(
            lat, lon, alt, canopy, slope, aspect, moisture
        )

        `when`(featureExtractor.extract(lat, lon, alt, canopy, slope, aspect, moisture))
            .thenReturn(mockFeatureSet)
        `when`(inferenceManager.predictSuitability(mockFeatureSet))
            .thenReturn(expectedScore)

        // When
        val result = useCase(lat, lon, alt, canopy, slope, aspect, moisture)

        // Then
        assertEquals(expectedScore, result, 0.0001f)
    }
}
