package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.data.auth.LeaveStep
import io.github.solcott.activitytracker.data.auth.PendingWork
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.dataresult.DataError
import kotlin.time.Duration.Companion.minutes

/** The leave screen (auth/leaving): before confirming, with each thing it warns about, then the leave's progress. */
object LeaveSamples {
    private val places = SharedPlacesSummary.Loaded(activeSegments = 3, otherSegments = 2, activeSites = 1, otherSites = 0)

    private fun state(typed: String = "", places: SharedPlacesSummary = this.places) =
        LeaveState(handle = "alice.test", confirmHandle = textFieldStateOf(typed), places = places, eventSink = {})

    val default: LeaveState
        get() = state()

    val all: Map<String, LeaveState>
        get() =
            mapOf(
                SampleKind.LOADED to default,
                "confirmed" to state(typed = "alice.test"),
                "wrongHandle" to state(typed = "alice.tset"),
                "placesLoading" to state(places = SharedPlacesSummary.Loading),
                "placesFailed" to state(places = SharedPlacesSummary.Failed(UiError(UiError.Kind.LoadSharedPlaces, DataError.Network))),
                "noPlaces" to state(places = SharedPlacesSummary.Loaded(0, 0, 0, 0)),
                "pendingWork" to default.copy(pending = PendingWork(changes = 2, importSaving = true), recording = true),
                "exportRunning" to state(typed = "alice.test").copy(export = LeaveExport.Running),
                "exportReady" to default.copy(export = LeaveExport.Ready),
                "signInAgain" to default.copy(needsSignInAgain = true),
                "unreachable" to state(typed = "alice.test").copy(error = UiError(UiError.Kind.StartLeave, DataError.Network)),
                "starting" to state(typed = "alice.test").copy(starting = true),
                "resuming" to default.copy(run = LeaveRun()),
                "deletingSpaces" to default.copy(run = LeaveRun(LeaveStep.Spaces)),
                "deletingRecords" to default.copy(run = LeaveRun(LeaveStep.Repo, recordsDeleted = 1_200)),
                "rateLimited" to default.copy(run = LeaveRun(LeaveStep.Repo, recordsDeleted = 800, pausedUntil = Fixed.NOW + 5.minutes)),
                "stopped" to
                    default.copy(
                        run =
                            LeaveRun(
                                LeaveStep.Repo,
                                recordsDeleted = 400,
                                error = UiError(UiError.Kind.Leave, DataError.Api(listOf("Request refused"), code = "InvalidRequest")),
                            ),
                    ),
            )
}
