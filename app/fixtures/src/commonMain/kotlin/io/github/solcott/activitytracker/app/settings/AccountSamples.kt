package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.data.auth.GrantFeature
import io.github.solcott.activitytracker.data.auth.GrantStatus
import io.github.solcott.activitytracker.data.auth.PendingWork

object AccountSamples {
    val default: AccountState
        get() =
            AccountState(
                handle = "alice.test",
                did = "did:plc:4z5n2d7xkqvu3hy6wbjmlcfe",
                pdsHost = "pds.example.com",
                grant = GrantStatus.Current,
                eventSink = {},
            )

    val all: Map<String, AccountState>
        get() =
            mapOf(
                SampleKind.LOADED to default,
                "outdated" to
                    default.copy(grant = GrantStatus.Outdated(setOf(GrantFeature.PrivateInteractions, GrantFeature.SegmentsAndGroups))),
                "noSpaceScope" to default.copy(grant = GrantStatus.Outdated(GrantFeature.all)),
                "spacesUnsupported" to default.copy(grant = GrantStatus.SpacesUnsupported),
                "pendingUploads" to
                    default.copy(confirming = EndingSession(signInAgain = false, PendingWork(changes = 3, importSaving = false))),
                "pendingImport" to
                    default.copy(confirming = EndingSession(signInAgain = false, PendingWork(changes = 12, importSaving = true))),
                "signInAgainPending" to
                    default.copy(
                        grant = GrantStatus.Outdated(GrantFeature.all),
                        confirming = EndingSession(signInAgain = true, PendingWork(changes = 1, importSaving = false)),
                    ),
            )
}
