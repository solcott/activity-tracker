package io.github.solcott.activitytracker.app.detail

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadableTokenTest {
    @Test
    fun anExerciseTheAppHasNoLabelForReadsAsWords() {
        assertEquals("Single arm cable row", readableToken("single_arm_cable_row"))
        assertEquals("Weighted 45 degree plank", readableToken("weighted_45_degree_plank"))
        assertEquals("Squat", readableToken("squat"))
    }

    @Test
    fun aCamelCaseTokenReadsAsWordsToo() {
        assertEquals("Im by round", readableToken("imByRound"))
        assertEquals("New kind", readableToken("newKind"))
    }
}
