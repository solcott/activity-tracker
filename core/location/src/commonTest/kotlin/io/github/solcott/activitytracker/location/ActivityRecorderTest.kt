package io.github.solcott.activitytracker.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** Recording (record/gps-recording): totals leave out pauses, and each climb counts once. */
class ActivityRecorderTest {
    private val start = Instant.parse("2026-10-24T07:00:00Z")

    private class TestClock(var now: Instant) : Clock {
        override fun now() = now
    }

    private val clock = TestClock(start)

    private val tracker =
        object : LocationTracker {
            override fun hasPermission() = true

            override fun updates() = emptyFlow<LocationSample>()
        }

    /** A position [meters] east of the start along the equator, [after] the start. */
    private fun at(meters: Double, after: Duration, altitude: Double? = null, speed: Double? = null) =
        LocationSample(0.0, meters / METERS_PER_DEGREE, altitude, speed = speed, accuracy = 5.0, time = start + after)

    private val journal = InMemoryRecordingJournal()

    private fun TestScope.recorder() = ActivityRecorder(tracker, clock, journal, dispatcher = StandardTestDispatcher(testScheduler))

    private fun TestScope.advance(to: Duration) {
        clock.now = start + to
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun walkingToACafeWhilePausedAddsNothing() = runTest {
        val recorder = recorder()
        recorder.start()
        advance(0.seconds)
        (0..60).forEach { recorder.onSample(at(it * 3.0, it.seconds)) } // 180 m in a minute
        clock.now = start + 60.seconds
        recorder.pause()
        advance(60.seconds)
        recorder.onSample(at(400.0, 5.minutes)) // dropped: paused
        clock.now = start + 11.minutes
        recorder.resume()
        advance(11.minutes)
        (0..60).forEach { recorder.onSample(at(580.0 + it * 3.0, 11.minutes + it.seconds)) } // 180 m more, 400 m on

        val state = recorder.state.value
        assertEquals(360.0, state.distanceMeters, 1.0, "the 400 m walk isn't counted")
        assertEquals(120, state.movingSeconds, "nor its 10 minutes")
        clock.now = start + 12.minutes
        val result = assertNotNull(recorder.finish())
        assertEquals(2.minutes, result.state.activeBeforeSegment, "elapsed leaves out the pause")
        assertEquals(122, result.samples.size, "every accepted sample, in order")
    }

    @Test
    fun topSpeedIsUnchangedByAPause() = runTest {
        val recorder = recorder()
        recorder.start()
        advance(0.seconds)
        (0..10).forEach { recorder.onSample(at(it * 3.0, it.seconds)) }
        recorder.pause()
        advance(10.seconds)
        clock.now = start + 3.minutes
        recorder.resume()
        advance(3.minutes)
        // 2 km on, 3 minutes later: the first sample after the resume is measured from nothing.
        recorder.onSample(at(2030.0, 3.minutes))
        recorder.onSample(at(2033.0, 3.minutes + 1.seconds))

        assertEquals(3.0, recorder.state.value.maxSpeedMps, 0.1)
        assertEquals(33.0, recorder.state.value.distanceMeters, 0.5)
    }

    @Test
    fun aClimbArrivingAsPauseIsTappedCountsOnce() = runTest {
        val recorder = recorder()
        recorder.start()
        advance(0.seconds)
        recorder.onSample(at(0.0, 0.seconds, altitude = 100.0))
        recorder.pause() // queued behind the sample below
        recorder.onSample(at(3.0, 1.seconds, altitude = 105.0))
        advance(1.seconds)

        assertEquals(RecordingState.Status.Paused, recorder.state.value.status)
        assertEquals(5.0, recorder.state.value.elevationGainMeters, 0.01)
    }

    @Test
    fun aClimbDuringAPauseIsNotGain() = runTest {
        val recorder = recorder()
        recorder.start()
        advance(0.seconds)
        recorder.onSample(at(0.0, 0.seconds, altitude = 100.0))
        recorder.pause()
        advance(1.seconds)
        recorder.resume()
        advance(10.minutes)
        recorder.onSample(at(50.0, 10.minutes, altitude = 160.0))
        recorder.onSample(at(53.0, 10.minutes + 1.seconds, altitude = 164.0))

        assertEquals(4.0, recorder.state.value.elevationGainMeters, 0.01, "only the climb after the resume")
    }

    @Test
    fun inaccurateSamplesAreDropped() = runTest {
        val recorder = recorder()
        recorder.start()
        advance(0.seconds)
        recorder.onSample(at(0.0, 0.seconds).copy(accuracy = 80.0))
        assertEquals(0, recorder.state.value.sampleCount)
    }

    @Test
    fun aRecordingRebuiltAfterTheBatteryDiesIsWhatWasShown() = runTest {
        val recorder = recorder()
        recorder.start(ALICE)
        advance(0.seconds)
        (0..30).forEach { recorder.onSample(at(it * 3.0, it.seconds, altitude = 100.0 + it)) }
        clock.now = start + 31.seconds
        recorder.pause()
        advance(31.seconds)
        clock.now = start + 40.seconds
        recorder.resume()
        advance(40.seconds)
        (0..20).forEach { recorder.onSample(at(200.0 + it * 3.0, 40.seconds + it.seconds)) }
        val shown = recorder.state.value

        // The phone dies: a new recorder over what was saved.
        val again = recorder()
        again.recover(ALICE)
        val rebuilt = again.state.value
        assertEquals(RecordingState.Status.Paused, rebuilt.status)
        assertTrue(rebuilt.recovered)
        assertEquals(shown.distanceMeters, rebuilt.distanceMeters, 0.001)
        assertEquals(shown.movingSeconds, rebuilt.movingSeconds)
        assertEquals(shown.elevationGainMeters, rebuilt.elevationGainMeters, 0.001)
        assertEquals(shown.sampleCount, rebuilt.sampleCount)
        assertEquals(51.seconds, rebuilt.activeBeforeSegment, "31 s, then 20 s to the last saved sample")
    }

    @Test
    fun anotherAccountIsOfferedNothing() = runTest {
        val recorder = recorder()
        recorder.start(ALICE)
        advance(0.seconds)
        recorder.onSample(at(0.0, 0.seconds))

        val bob = recorder()
        bob.recover("did:plc:bob")
        assertEquals(RecordingState.Status.Idle, bob.state.value.status)
        assertEquals(1, journal.saved.size, "Alice's recording waits for her")
    }

    @Test
    fun resumingAfterACrashLeavesOutTheGap() = runTest {
        val recorder = recorder()
        recorder.start(ALICE)
        advance(0.seconds)
        (0..60).forEach { recorder.onSample(at(it * 3.0, it.seconds)) }

        // Killed after the last sample at 60 s; resumed 8 minutes later, 3 km on.
        val again = recorder()
        again.recover(ALICE)
        clock.now = start + 9.minutes
        again.resume()
        advance(9.minutes)
        (0..10).forEach { again.onSample(at(3180.0 + it * 3.0, 9.minutes + it.seconds)) }
        clock.now = start + 9.minutes + 10.seconds
        val result = assertNotNull(again.finish())

        assertEquals(210.0, result.state.distanceMeters, 1.0, "the 3 km aren't counted")
        assertEquals(70, result.state.movingSeconds)
        assertEquals(70.seconds, result.state.activeBeforeSegment, "nor the 8 minutes")
        assertEquals(72, result.samples.size, "the route keeps both sides of the gap, where map breaks decide")
    }

    @Test
    fun aSecondCrashStillLeavesOutTheFirstGap() = runTest {
        val recorder = recorder()
        recorder.start(ALICE)
        advance(0.seconds)
        (0..60).forEach { recorder.onSample(at(it * 3.0, it.seconds)) }

        // Killed at 60 s, resumed at 9 minutes, then killed again 10 s later.
        val again = recorder()
        again.recover(ALICE)
        clock.now = start + 9.minutes
        again.resume()
        advance(9.minutes)
        (0..10).forEach { again.onSample(at(3180.0 + it * 3.0, 9.minutes + it.seconds)) }

        val third = recorder()
        third.recover(ALICE)
        assertEquals(70.seconds, third.state.value.activeBeforeSegment, "neither gap counts")
        assertEquals(210.0, third.state.value.distanceMeters, 1.0)
    }

    @Test
    fun signingOutKeepsTheRecordingForItsAccount() = runTest {
        val recorder = recorder()
        recorder.start(ALICE)
        advance(0.seconds)
        (0..10).forEach { recorder.onSample(at(it * 3.0, it.seconds)) }
        clock.now = start + 20.minutes
        recorder.suspendForSignOut()
        assertEquals(RecordingState.Status.Idle, recorder.state.value.status)

        recorder.recover(ALICE)
        assertTrue(recorder.state.value.recovered)
        assertEquals(30.0, recorder.state.value.distanceMeters, 0.5)
    }

    @Test
    fun signingBackInAfterAnExpiryLeavesTheRunningRecordingAsItIs() = runTest {
        val recorder = recorder()
        recorder.start(ALICE)
        advance(0.seconds)
        (0..10).forEach { recorder.onSample(at(it * 3.0, it.seconds)) }

        recorder.recover(ALICE)

        assertEquals(RecordingState.Status.Recording, recorder.state.value.status)
        assertFalse(recorder.state.value.recovered)
        assertEquals(11, recorder.state.value.sampleCount)
        assertTrue(recorder.state.value.inProgressFor(ALICE), "signing in lands on it")
        assertFalse(recorder.state.value.inProgressFor("did:plc:bob"))
    }

    @Test
    fun anotherAccountSigningInStopsTheRecordingAndKeepsItForItsAccount() = runTest {
        val recorder = recorder()
        recorder.start(ALICE)
        advance(0.seconds)
        (0..10).forEach { recorder.onSample(at(it * 3.0, it.seconds)) }

        recorder.recover("did:plc:bob")
        assertEquals(RecordingState.Status.Idle, recorder.state.value.status, "bob sees no recording")
        assertFalse(recorder.state.value.inProgressFor(ALICE))

        recorder.recover(ALICE)
        assertTrue(recorder.state.value.recovered)
        assertEquals(30.0, recorder.state.value.distanceMeters, 0.5)
    }

    @Test
    fun aRecordingWithNoAccountIsLeftAlone() = runTest {
        val recorder = recorder()
        recorder.start()
        advance(0.seconds)

        recorder.recover(ALICE)

        assertEquals(RecordingState.Status.Recording, recorder.state.value.status)
        assertNull(recorder.state.value.did)
    }

    @Test
    fun aSampleThatCantBeSavedIsntCounted() = runTest {
        val recorder = recorder()
        recorder.start(ALICE)
        advance(0.seconds)
        recorder.onSample(at(0.0, 0.seconds))
        journal.failing = true
        recorder.onSample(at(3.0, 1.seconds))

        assertTrue(recorder.state.value.saveFailed)
        assertEquals(1, recorder.state.value.sampleCount, "what's shown is never ahead of what's saved")
        journal.failing = false
        recorder.onSample(at(6.0, 2.seconds))
        assertEquals(2, recorder.state.value.sampleCount)
        assertEquals(false, recorder.state.value.saveFailed)
    }

    @Test
    fun finishingKeepsTheRecordingUntilItsQueued() = runTest {
        val recorder = recorder()
        recorder.start(ALICE)
        advance(0.seconds)
        recorder.onSample(at(0.0, 0.seconds))
        assertNotNull(recorder.finish())
        assertEquals(RecordingState.Status.Paused, recorder.state.value.status)
        assertEquals(1, journal.saved.size)

        recorder.finished()
        assertEquals(RecordingState.Status.Idle, recorder.state.value.status)
        assertTrue(journal.saved.isEmpty())
    }

    @Test
    fun theForegroundHearsEveryStatusChange() = runTest {
        val heard = mutableListOf<RecordingState.Status>()
        val recorder = ActivityRecorder(tracker, clock, journal, { heard += it }, StandardTestDispatcher(testScheduler))
        recorder.start(ALICE)
        advance(0.seconds)
        recorder.pause()
        advance(1.seconds)
        recorder.resume()
        advance(2.seconds)
        recorder.finish()
        recorder.finished()

        assertEquals(
            listOf(
                RecordingState.Status.Recording,
                RecordingState.Status.Paused,
                RecordingState.Status.Recording,
                RecordingState.Status.Paused,
                RecordingState.Status.Idle,
            ),
            heard,
        )
    }

    private companion object {
        const val ALICE = "did:plc:alice"

        /** Meters in a degree of longitude at the equator, by [TrackMath.distanceMeters]'s radius. */
        const val METERS_PER_DEGREE = 6_371_000.0 * kotlin.math.PI / 180
    }
}
