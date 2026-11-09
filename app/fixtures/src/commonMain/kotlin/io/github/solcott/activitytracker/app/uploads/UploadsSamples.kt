package io.github.solcott.activitytracker.app.uploads

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.data.activity.ImportSaveStatus
import io.github.solcott.activitytracker.data.activity.RejectedChange
import io.github.solcott.activitytracker.data.activity.UploadImport
import io.github.solcott.activitytracker.data.activity.UploadItem
import io.github.solcott.activitytracker.data.activity.UploadQueueView
import io.github.solcott.activitytracker.data.activity.UploadSource
import io.github.solcott.activitytracker.data.activity.UploadState
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.uistate.ContentState
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf

/** The upload queue (data/upload-queue): own items, an import part way, both pauses, refused changes and finished items. */
object UploadsSamples {
    const val IMPORT_ID = "batch1"

    @Suppress("LongParameterList") // The item's fields, defaulted.
    private fun item(
        id: Long,
        state: UploadState,
        title: String? = null,
        sport: String? = Sport.RIDE,
        rkey: String? = "3k$id",
        isEdit: Boolean = false,
        sent: Int = 0,
        total: Int = 5,
        startedAt: Instant = Fixed.STARTED_AT - id.hours,
        batchId: String? = null,
    ) =
        UploadItem(
            id = id,
            uri = rkey?.let { Fixed.uri(Fixed.ALICE, Nsid.FEED_ACTIVITY, it) },
            batchId = batchId,
            isEdit = isEdit,
            state = state,
            title = title,
            sport = sport,
            subSport = null,
            startedAt = startedAt,
            sent = if (state == UploadState.Uploaded) total else sent,
            total = total,
            finishedAt = if (state == UploadState.Uploaded || state == UploadState.Skipped) Fixed.NOW - id.minutes else null,
        )

    /** The recording uploading now, 2 of its 5 writes sent. */
    val current = item(1, UploadState.Uploading, title = "Evening Run", sport = Sport.RUN, sent = 2)

    private val own =
        persistentListOf(
            item(2, UploadState.Waiting, title = "Morning Ride"),
            item(3, UploadState.Waiting, title = "Lunch Swim", sport = Sport.SWIM, isEdit = true),
        )

    private val ownFinished = persistentListOf(item(4, UploadState.Uploaded, title = "Hill Repeats", sport = Sport.RUN))

    /** An import of 1,362 activities with 412 uploaded, one refused, 3 skipped, and more waiting than show before Show all. */
    val partWay =
        UploadImport(
            id = IMPORT_ID,
            source = UploadSource.Export(listOf("Strava")),
            savedAt = Fixed.NOW - 5.hours,
            saved = 1362,
            skipped = 3,
            uploaded = 412,
            refused = 1,
            live =
                listOf(item(100, UploadState.Refused, sport = Sport.RIDE, startedAt = Fixed.STARTED_AT - 400.days, batchId = IMPORT_ID)) +
                    (101L..125L).map {
                        item(it, UploadState.Waiting, sport = Sport.RUN, startedAt = Fixed.STARTED_AT - (it * 2).days, batchId = IMPORT_ID)
                    },
            finished =
                listOf(
                    item(200, UploadState.Uploaded, title = "Gravel Loop", batchId = IMPORT_ID),
                    item(201, UploadState.Skipped, title = "Broken file", rkey = null, batchId = IMPORT_ID),
                ),
        )

    val refusedLike = RejectedChange("3klike", RejectedChange.Kind.Like, Fixed.NOW)

    val emptyView =
        UploadQueueView(current = null, own = emptyList(), imports = emptyList(), finished = emptyList(), refusedOther = emptyList())

    /** Everything at once. */
    val loadedView =
        UploadQueueView(current = current, own = own, imports = listOf(partWay), finished = ownFinished, refusedOther = listOf(refusedLike))

    private fun state(content: ContentState<UploadQueueView?>) = UploadsState(content = content)

    private fun loaded(view: UploadQueueView) = state(ContentStates.loaded(view))

    val loaded: UploadsState
        get() = loaded(loadedView)

    val empty: UploadsState
        get() = loaded(emptyView)

    /** Only the athlete's own items: the recording uploading and two waiting. */
    val ownOnly: UploadsState
        get() = loaded(emptyView.copy(current = current, own = own))

    /** The import part way, with a time left, on a platform where uploads go on only while the app is open. */
    val importPartWay: UploadsState
        get() = loaded(emptyView.copy(imports = listOf(partWay))).copy(timeLeft = 26.hours, isOpenOnly = true)

    /** The import shown whole after Show all. */
    val importExpanded: UploadsState
        get() = importPartWay.copy(expandedImports = persistentListOf(IMPORT_ID))

    /** Imports paused by the athlete and uploads by the server until 3:40 PM (UTC). */
    val paused: UploadsState
        get() =
            loaded(
                    emptyView.copy(
                        current = current,
                        imports = listOf(partWay.copy(source = UploadSource.Export(listOf("Strava", "Garmin")))),
                    ),
                )
                .copy(
                    importsPaused = true,
                    serverPausedUntil = Fixed.NOW + 3.hours + 40.minutes,
                )

    /** A refused activity of the athlete's own, and a refused like. */
    val refused: UploadsState
        get() =
            loaded(
                emptyView.copy(
                    own = listOf(item(5, UploadState.Refused, title = null, sport = Sport.RUN, startedAt = Fixed.STARTED_AT)),
                    refusedOther = listOf(refusedLike),
                ),
            )

    /** Nothing left to upload: the import done overnight and a finished recording. */
    val finished: UploadsState
        get() =
            loaded(
                emptyView.copy(
                    imports =
                        listOf(
                            partWay.copy(
                                source = UploadSource.Files("Morning_Ride.fit", 15),
                                uploaded = 12,
                                refused = 0,
                                saved = 12,
                                live = emptyList(),
                            ),
                        ),
                    finished = ownFinished,
                ),
            )

    /** An import of 1,362 activities still saving, 500 saved so far, 40 of them uploaded. */
    val saving: UploadsState
        get() =
            loaded(
                emptyView.copy(
                    imports =
                        listOf(
                            partWay.copy(
                                saved = 500,
                                skipped = 0,
                                uploaded = 40,
                                refused = 0,
                                live = partWay.live.drop(1),
                                finished = partWay.finished.take(1),
                                save = ImportSaveStatus.Saving(done = 500, total = 1362),
                            ),
                        ),
                ),
            )

    /** An import of 400 selected activities that stopped saving after 120, all of them uploaded. */
    val stopped: UploadsState
        get() =
            loaded(
                emptyView.copy(
                    imports =
                        listOf(
                            partWay.copy(
                                source = UploadSource.Files("Morning_Ride.fit", 400),
                                saved = 120,
                                skipped = 0,
                                uploaded = 120,
                                refused = 0,
                                live = emptyList(),
                                finished = partWay.finished.take(1),
                                save = ImportSaveStatus.Stopped(saved = 120, total = 400),
                            ),
                        ),
                ),
            )

    /** Asking to confirm cancelling the rest of the import still saving. */
    val confirmingCancelRestSaving: UploadsState
        get() = saving.copy(confirmingCancelRest = IMPORT_ID)

    /** Asking to confirm cancelling the waiting Morning Ride. */
    val confirmingCancel: UploadsState
        get() = loaded.copy(confirmingCancel = "3k2")

    /** Asking to confirm cancelling the rest of the import. */
    val confirmingCancelRest: UploadsState
        get() = loaded.copy(confirmingCancelRest = IMPORT_ID)

    /** A cancel that came too late: the item had started uploading. */
    val cancelFailed: UploadsState
        get() = ownOnly.copy(error = UiError(UiError.Kind.CancelUpload))

    /** Sending the refused like again, in flight. */
    val sendingAgain: UploadsState
        get() = refused.copy(busy = persistentListOf(refusedLike.rkey))

    val all: Map<String, UploadsState>
        get() =
            contentSamples<UploadQueueView?, UploadsState>(null, loadedView, ::state) +
                mapOf(
                    SampleKind.EMPTY to empty,
                    "ownOnly" to ownOnly,
                    "importPartWay" to importPartWay,
                    "importExpanded" to importExpanded,
                    "paused" to paused,
                    "refused" to refused,
                    "finished" to finished,
                    "saving" to saving,
                    "stopped" to stopped,
                    "confirmingCancelRestSaving" to confirmingCancelRestSaving,
                    "confirmingCancel" to confirmingCancel,
                    "confirmingCancelRest" to confirmingCancelRest,
                    "cancelFailed" to cancelFailed,
                    "sendingAgain" to sendingAgain,
                )
}
