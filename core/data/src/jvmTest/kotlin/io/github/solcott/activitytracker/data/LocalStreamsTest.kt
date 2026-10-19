package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.feed.LocalStreams
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LocalStreamsTest {
    private val uri = AtUri("at://did:plc:alice/app.activitytracker.feed.activity/3kride")

    private fun output(data: ActivityStreamData) = GetActivityStreams.Output(uri, Visibility.PUBLIC, data.channels, data)

    private val old = output(ActivityStreamData(time = listOf(0, 1), heartRate = listOf(140, 141), power = listOf(250, 251)))
    private val rebuilt = output(ActivityStreamData(time = listOf(0, 1), heartRate = listOf(140, 141)))

    @Test
    fun theRebuiltStreamsShowUntilTheAppViewServesThem() {
        val local = LocalStreams()
        assertSame(old, local.applyTo(uri, old), "nothing rebuilt")
        local.set(uri, rebuilt)
        assertSame(rebuilt, local.applyTo(uri, old), "the AppView lags")
        assertSame(rebuilt, local.applyTo(uri, null), "or has none yet")

        val served = rebuilt.copy(heartRateZones = null)
        assertSame(served, local.applyTo(uri, served), "caught up")
        assertEquals(old, local.applyTo(uri, old), "and dropped: the AppView's own from then on")
    }
}
