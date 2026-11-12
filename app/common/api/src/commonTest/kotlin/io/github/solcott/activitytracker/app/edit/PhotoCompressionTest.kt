package io.github.solcott.activitytracker.app.edit

import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoCompressionTest {
    @Test
    fun theStepsGoDownInSizeAndQualityUnderTheTarget() {
        assertEquals(
            listOf(PhotoStep(2048, 80), PhotoStep(2048, 70), PhotoStep(1600, 70), PhotoStep(1280, 60)),
            PhotoCompression.steps,
        )
        assertEquals(1_500_000, PhotoCompression.targetBytes)
    }
}
