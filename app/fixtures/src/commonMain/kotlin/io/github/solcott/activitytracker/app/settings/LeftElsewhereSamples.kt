package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.dataresult.DataError

/** After leaving on another device, with work still on this one (auth/leaving). */
object LeftElsewhereSamples {
    val default: LeftElsewhereState
        get() = LeftElsewhereState(handle = "bob.test", checking = false, changes = 1, eventSink = {})

    val all: Map<String, LeftElsewhereState>
        get() =
            mapOf(
                SampleKind.LOADED to default,
                "checking" to default.copy(checking = true),
                "everything" to default.copy(changes = 12, importSaving = true, exportPending = true, recording = true),
                "rejoining" to default.copy(busy = true),
                "rejoinFailed" to default.copy(error = UiError(UiError.Kind.Rejoin, DataError.Network)),
            )
}
