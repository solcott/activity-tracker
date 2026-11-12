package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.activityfile.ExportKind
import io.github.solcott.activitytracker.activityfile.SkipReason
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.ActivityDetailSamples
import io.github.solcott.activitytracker.app.edit.ActivityDetailsFields
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.fakeActivity
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.activity.CombineCue
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.SensorType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.RecordingState
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.datetime.LocalDate

private val typeField: ActivityTypeField
    get() =
        ActivityTypeField(
            ActivityType(Sport.RIDE),
            persistentListOf(ActivityType(Sport.RIDE), ActivityType(Sport.RUN), ActivityType(Sport.WALK)),
        )

object RecordSamples {
    private fun state(status: RecordingState.Status) =
        RecordState(
            status = status,
            elapsed = if (status == RecordingState.Status.Idle) 0.minutes else 42.minutes,
            distanceMeters = if (status == RecordingState.Status.Idle) 0.0 else 15_300.0,
            movingSeconds = if (status == RecordingState.Status.Idle) 0 else 2_400,
            elevationGainMeters = if (status == RecordingState.Status.Idle) 0.0 else 180.0,
            type = typeField,
            title = textFieldStateOf(),
            hasPermission = true,
            saving = false,
            error = null,
            eventSink = {},
        )

    val all: Map<String, RecordState>
        get() =
            mapOf(
                SampleKind.LOADED to state(RecordingState.Status.Idle),
                "recording" to state(RecordingState.Status.Recording),
                "paused" to state(RecordingState.Status.Paused),
                // Offered back after the app was killed mid-ride.
                "recovered" to state(RecordingState.Status.Paused).copy(recovered = true),
                "saveFailed" to state(RecordingState.Status.Recording).copy(saveFailed = true),
            )
}

object ManualActivitySamples {
    private fun state(duration: String, error: UiError? = null, visibility: VisibilityFields = VisibilityFields()) =
        ManualActivityState(
            type = typeField,
            title = textFieldStateOf("Evening Ride"),
            distance = textFieldStateOf("24.5"),
            durationMinutes = textFieldStateOf(duration),
            temperature = textFieldStateOf(),
            units = UnitSystem.Metric,
            saving = false,
            error = error,
            visibility = visibility,
            eventSink = {},
        )

    val all: Map<String, ManualActivityState>
        get() =
            mapOf(
                SampleKind.LOADED to state("55"),
                SampleKind.INVALID to state("0"),
                // On a PDS without Spaces: the picker is Public and says why, and a save the session thought it could make was refused.
                "spacesUnsupported" to
                    state(
                        "55",
                        UiError(UiError.Kind.SpacesUnsupported),
                        VisibilityFields(canChoose = false, spacesUnsupported = true),
                    ),
            )

    /** The states beyond these the native form draws: a gravel ride with gear and categories, in imperial, saving, and its type sheet. */
    val variants: Map<String, ManualActivityState>
        get() {
            val full =
                state("55", visibility = FormSamples.visibility)
                    .copy(
                        type = ActivityTypeField(ActivityType(Sport.RIDE, SubSport.GRAVEL), typeField.quickTypes),
                        temperature = textFieldStateOf("18"),
                        gear = FormSamples.gear,
                    )
            return mapOf(
                "full" to full,
                "imperial" to full.copy(units = UnitSystem.Imperial, distance = textFieldStateOf("15.2")),
                "saving" to full.copy(saving = true),
                "noSubSports" to full.copy(type = ActivityTypeField(ActivityType(Sport.YOGA), typeField.quickTypes)),
            )
        }
}

object ImportActivitySamples {
    private fun state(reading: Boolean, preview: ImportPreview?) =
        ImportActivityState(
            reading = reading,
            preview = preview,
            type = typeField,
            title = textFieldStateOf(),
            defaultTitle = preview?.let { DefaultTitle(PartOfDay.Morning, ActivityType(Sport.RIDE)) },
            saving = false,
            error = null,
            eventSink = {},
        )

    private val preview =
        ImportPreview(
            fileName = "morning-ride.fit",
            elapsed = 65.minutes,
            device = "Garmin Edge 840",
            polyline = Fixed.POLYLINE,
            dive = null,
            clock = null,
        )

    private val tarmac = AtUri("at://${Fixed.ALICE.value}/${Nsid.GEAR_ITEM}/3ktarmac")
    private val stumpjumper = AtUri("at://${Fixed.ALICE.value}/${Nsid.GEAR_ITEM}/3kstumpy")

    private val gear =
        GearField(
            options =
                persistentListOf(
                    GearOption(tarmac, "Tarmac", GearType.BIKE, "road", Visibility.PUBLIC),
                    GearOption(stumpjumper, "Stumpjumper", GearType.BIKE, "mountain", Visibility.PUBLIC),
                ),
            selected = persistentListOf(tarmac, stumpjumper),
            conflicts = persistentListOf(GearConflict(GearType.BIKE, persistentListOf("Tarmac", "Stumpjumper"))),
        )

    /** An Edge 840 ride naming three devices not linked yet: the power meter being added, mounted on the Tarmac. */
    private val newDevices =
        NewDevicesFields(
            cards =
                persistentListOf(
                    NewDeviceCard("edge", "Garmin", "Edge 840", recorder = true, serialTail = "2071", type = GearType.DEVICE),
                    NewDeviceCard(
                        "hrm",
                        "Garmin",
                        "HRM 200",
                        recorder = false,
                        sensorKind = SensorType.HEART_RATE,
                        weak = true,
                        decision = NewDeviceDecision.NotGear,
                        type = GearType.SENSOR,
                        kind = "heartRate",
                    ),
                    NewDeviceCard(
                        "meter",
                        "SRAM",
                        "1052",
                        recorder = false,
                        sensorKind = SensorType.POWER,
                        serialTail = "3426",
                        decision = NewDeviceDecision.Add,
                        name = textFieldStateOf("SRAM Power meter"),
                        type = GearType.COMPONENT,
                        kind = "powerMeter",
                        mountOn = tarmac,
                        collections = persistentSetOf("3kcol1"),
                        expanded = true,
                    ),
                ),
            mountOptions = gear.options,
            collections = persistentListOf(GearCollectionOption("3kcol1", "Race kit", 4), GearCollectionOption("3kcol2", "Winter", 2)),
            profileOffer = ProfileLinkOffer("MOUNTAIN", gear.options),
        )

    /** A new, selected row of a run or ride from [file]; samples copy in the rest. */
    private fun row(index: Int, start: String, type: ActivityType, title: String?, file: String) =
        ImportRow(
            index = index,
            title = title,
            defaultTitle = DefaultTitle(PartOfDay.Morning, type),
            type = type,
            start = start,
            month = LocalDate.parse(start.substringBeforeLast('-') + "-01"),
            elapsed = ROW_ELAPSED,
            distanceMeters = null,
            dive = null,
            device = "Garmin Forerunner 965",
            fileName = file,
            selected = true,
            duplicate = null,
            narrowedTo = null,
        )

    private val logged =
        DuplicateInfo(
            existing = ExistingActivityPreview("Lunch loop", "2024-05-14 12:01", "Garmin Fenix 7"),
            inBatchIndex = null,
            hoursApart = 0,
            action = DuplicateAction.Combine,
            actions = persistentListOf(DuplicateAction.Ignore, DuplicateAction.ImportAnyway, DuplicateAction.Combine),
        )

    private val mixedRows =
        persistentListOf(
            row(0, "2024-04-28 07:02", ActivityType(Sport.RUN), "Easy run", "run.fit").copy(distanceMeters = 8_120.0),
            row(1, "2024-05-03 18:15", ActivityType(Sport.RIDE), null, "ride.fit").copy(distanceMeters = 42_300.0),
            row(2, "2024-05-03 18:16", ActivityType(Sport.RIDE), null, "edge.fit")
                // The same upload as ride.fit, found again in another export: an exact copy, left out.
                .copy(
                    distanceMeters = 42_100.0,
                    selected = false,
                    duplicate = logged.copy(existing = null, inBatchIndex = 1, exactCopy = true, action = DuplicateAction.Ignore),
                ),
            row(3, "2024-05-14 12:00", ActivityType(Sport.RUN), "Lunch loop", "lunch.gpx")
                .copy(distanceMeters = 6_400.0, selected = false, duplicate = logged),
            row(4, "2024-05-20 09:30", ActivityType(Sport.DIVE), "Blue Hole", "dives.uddf")
                .copy(dive = DiveRowExtras(31.2, 112), device = "Shearwater Perdix"),
        )

    private val mixed =
        ImportBatch(
            rows = mixedRows,
            // The dive logbook keeps local time with no zone.
            clocks =
                persistentListOf(
                    FileClock(
                        key = 4,
                        fileName = "dives.uddf",
                        clock =
                            ImportClock(
                                "2024-05-20 09:30",
                                TimeZoneOption("America/Denver", "UTC-06:00"),
                                persistentListOf(TimeZoneOption("America/Denver", "UTC-06:00"), TimeZoneOption("UTC", "UTC+00:00")),
                            ),
                    ),
                ),
            originalsBytes = 3_400_000,
        )

    private val strava =
        ImportBatch(
            rows =
                persistentListOf(
                    row(0, "2019-11-02 08:00", ActivityType(Sport.RUN), "Parkrun", "2843.fit.gz").copy(distanceMeters = 5_000.0),
                    row(1, "2019-11-09 08:00", ActivityType(Sport.RUN), "Parkrun PB!", "2911.fit.gz")
                        .copy(distanceMeters = 5_000.0, narrowedTo = Visibility.SELF),
                    row(2, "2020-01-04 10:12", ActivityType(Sport.RIDE), "Zwift: Watopia", "3070.fit.gz").copy(distanceMeters = 30_500.0),
                ),
            skipped = persistentListOf(SkippedFile("4410.tcx.gz", SkipReason.Damaged), SkippedFile("notes.xml", SkipReason.Unsupported)),
            ignoredFiles = 214,
            exports = persistentListOf(BatchExport(ExportKind.Strava, privacy = true)),
            originalsTooLarge = persistentListOf("9001.gpx"),
            originalsBytes = 812_000_000,
        )

    private fun batchState(batch: ImportBatch) = state(false, null).copy(batch = batch, gear = gear.copy(conflicts = persistentListOf()))

    /**
     * A batch with one activity opened in its sheet. Not in [all]: the screenshot renderer can't draw a modal sheet's window, so UI tests
     * render it instead.
     */
    val batchPreview: ImportActivityState
        get() =
            batchState(
                mixed.copy(
                    preview =
                        ImportItemPreview(
                            index = 2,
                            title = textFieldStateOf(),
                            type = typeField,
                            defaultTitle = DefaultTitle(PartOfDay.Evening, ActivityType(Sport.RIDE)),
                            preview = preview.copy(fileName = "edge.fit"),
                            // The earlier copy it repeats, ride.fit, to compare with.
                            matched =
                                ExistingActivityPreview(
                                    title = null,
                                    start = "2024-05-03 18:15",
                                    device = "Garmin Forerunner 965",
                                    type = ActivityType(Sport.RIDE),
                                    elapsed = ROW_ELAPSED,
                                    distanceMeters = 42_300.0,
                                ),
                        ),
                ),
            )

    val all: Map<String, ImportActivityState>
        get() =
            mapOf(
                SampleKind.LOADED to state(false, null),
                "reading" to state(true, null),
                "readingProgress" to state(true, null).copy(readProgress = ReadProgress(files = 812, activities = 790)),
                "batch" to batchState(mixed),
                "batchExport" to batchState(strava),
                "batchDuplicates" to batchState(mixed.copy(filter = BatchFilter.Duplicates)),
                "batchSaving" to batchState(mixed.copy(progress = SaveProgress(queued = 2, total = 4))).copy(saving = true),
                "batchSaved" to state(false, null).copy(batchSaved = BatchSaved(saved = 1_986, skipped = 3)),
                "batchSaveFailed" to
                    batchState(mixed.copy(saveFailure = SaveFailure(UiError(UiError.Kind.SaveActivity), saved = 3, total = 5))),
                "savingImport" to state(false, null).copy(savingImport = SavingImport(SaveProgress(queued = 412, total = 1_362))),
                "stoppedImport" to
                    state(false, null).copy(stoppedImports = persistentListOf(StoppedImport("3kbatch", saved = 120, total = 400))),
                "duplicateCheckFailed" to state(false, preview).copy(duplicateCheck = DuplicateCheck.Failed),
                "preview" to state(false, preview),
                "newDevices" to state(false, preview).copy(gear = gear, newDevices = newDevices),
                "afterImport" to
                    state(false, preview)
                        .copy(afterImport = AfterImport(pastImports = PastImportsOffer(persistentListOf("Tarmac"), count = 10))),
            )

    private val details =
        ActivityDetailsFields(textFieldStateOf(), diveSite = null, media = persistentListOf(), maxMedia = 4, eventSink = {})

    /** A single file read, its page built, as the form shows it. */
    private val read: ImportActivityState
        get() =
            state(false, preview)
                .copy(page = ActivityDetailSamples.ridePage, details = details, gear = gear.copy(conflicts = persistentListOf()))

    private val lunchLoop = ExistingActivityPreview("Lunch loop", "2024-05-14 12:01", "Garmin Fenix 7")

    /** Beyond [all]: what the native screen draws and the screenshots don't need again. */
    val variants: Map<String, ImportActivityState>
        get() =
            mapOf(
                "page" to
                    read.copy(
                        originalTooLarge = true,
                        thresholdChanges =
                            persistentListOf(
                                ThresholdChange(Threshold.FUNCTIONAL_THRESHOLD_POWER, from = 231, to = 240),
                                ThresholdChange(Threshold.MAX_HEART_RATE, from = null, to = 188, sport = Sport.RUN),
                            ),
                        gear = gear,
                    ),
                "diveZone" to
                    read.copy(
                        preview =
                            preview.copy(
                                fileName = "blue-hole.xml",
                                polyline = null,
                                device = "Shearwater Perdix",
                                clock =
                                    ImportClock(
                                        "2024-10-16 17:30",
                                        TimeZoneOption("America/Cancun", "UTC-05:00"),
                                        persistentListOf(
                                            TimeZoneOption("America/Cancun", "UTC-05:00"),
                                            TimeZoneOption("America/New_York", "UTC-04:00"),
                                            TimeZoneOption("Asia/Singapore", "UTC+08:00"),
                                        ),
                                    ),
                            ),
                        type = ActivityTypeField(ActivityType(Sport.DIVE), typeField.quickTypes),
                        defaultTitle = DefaultTitle(PartOfDay.Evening, ActivityType(Sport.DIVE)),
                        page = ActivityDetailSamples.divePage,
                        details = details.copy(diveSite = textFieldStateOf("Blue Hole")),
                    ),
                "duplicate" to read.copy(preview = preview.copy(duplicate = logged)),
                "duplicateUploading" to
                    read.copy(
                        preview = preview.copy(duplicate = logged.copy(stillUploading = true, action = DuplicateAction.ImportAnyway)),
                    ),
                "duplicateChecking" to read.copy(duplicateCheck = DuplicateCheck.Checking),
                "addsRecording" to read.copy(addsRecording = true, addingTo = lunchLoop),
                "tooFar" to read.copy(addsRecording = true, addingTo = lunchLoop, tooFarFromTarget = true),
                "saving" to read.copy(saving = true),
                "saveFailed" to read.copy(error = UiError(UiError.Kind.SaveActivity)),
                "loadFailed" to state(false, null).copy(error = UiError(UiError.Kind.ReadActivityFile)),
                "batchPreview" to batchPreview,
                "batchChecking" to batchState(mixed).copy(duplicateCheck = DuplicateCheck.Failed),
                "savedSuggestions" to
                    state(false, null)
                        .copy(
                            batchSaved = BatchSaved(saved = 12, skipped = 0),
                            savedSuggestions =
                                persistentListOf(
                                    SavedSuggestion(
                                        uri = AtUri("at://${Fixed.ALICE.value}/${Nsid.FEED_ACTIVITY}/3kdive1"),
                                        title = "Blue Hole",
                                        startEpochSeconds = Fixed.NOW.epochSeconds - 7_200,
                                        cue = CombineCue.SameActivity,
                                        other = fakeActivity(),
                                    ),
                                ),
                        ),
                "pastApplying" to
                    state(false, preview)
                        .copy(
                            afterImport =
                                AfterImport(
                                    linksNotSaved = true,
                                    pastImports =
                                        PastImportsOffer(
                                            persistentListOf("Tarmac", "SRAM Power meter"),
                                            count = 10,
                                            applying = true,
                                            done = 4,
                                        ),
                                ),
                        ),
            )
}

private val ROW_ELAPSED = 52.minutes
