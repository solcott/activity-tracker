package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.app.text.TextSource
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant

class ImportActivityTextsTest {
    private val ride = ActivityType(Sport.RIDE)

    /** [local] ("2024-05-14 12:01") on the clock [offsetMinutes] from UTC. */
    private fun startAt(local: String, offsetMinutes: Int = 0) =
        ImportStart(
            LocalDateTime.parse(local.replace(' ', 'T')).toInstant(UtcOffset(seconds = offsetMinutes * 60)).epochSeconds,
            offsetMinutes,
        )

    private fun row(index: Int, start: String, title: String? = null, duplicate: DuplicateInfo? = null, dive: DiveRowExtras? = null) =
        ImportRow(
            index = index,
            title = title,
            defaultTitle = DefaultTitle(PartOfDay.Morning, ride),
            type = ride,
            start = startAt(start),
            month = LocalDate.parse(start.take(7) + "-01"),
            elapsed = 52.minutes,
            distanceMeters = 42_300.0,
            dive = dive,
            device = "Edge 840",
            fileName = "ride.fit",
            selected = duplicate == null,
            duplicate = duplicate,
        )

    private fun dup(existing: ExistingActivityPreview? = null, inBatch: Int? = null, uploading: Boolean = false) =
        DuplicateInfo(
            existing = existing,
            inBatchIndex = inBatch,
            hoursApart = 0,
            action = DuplicateAction.Combine,
            actions = persistentListOf(DuplicateAction.Ignore, DuplicateAction.ImportAnyway, DuplicateAction.Combine),
            stillUploading = uploading,
        )

    private fun state(batch: ImportBatch? = null, preview: ImportPreview? = null) =
        ImportActivityState(
            reading = false,
            preview = preview,
            type = ActivityTypeField(ride, persistentListOf()),
            title = textFieldStateOf(),
            defaultTitle = DefaultTitle(PartOfDay.Evening, ride),
            saving = false,
            error = null,
            batch = batch,
            eventSink = {},
        )

    private val source =
        object : TextSource {
            override fun string(name: String, args: List<String>): String = (listOf(name) + args).joinToString(":")

            override fun plural(name: String, quantity: Int, args: List<String>): String = name

            override fun time(epochSeconds: Long, utcOffsetMinutes: Int?): String = "$epochSeconds"
        }

    @Test
    fun zonesAreFoundByCityOrOffset() {
        val zones =
            persistentListOf(
                TimeZoneOption("America/New_York", "UTC-04:00"),
                TimeZoneOption("Asia/Singapore", "UTC+08:00"),
                TimeZoneOption("America/Cancun", "UTC-05:00"),
            )
        val clock = ImportClock("2024-10-16 17:30", zones[0], zones)
        assertEquals(listOf("Asia/Singapore"), ImportActivityTexts.zones(clock, "utc+8").map { it.id })
        assertEquals(listOf("America/New_York"), ImportActivityTexts.zones(clock, " new york").map { it.id })
        assertEquals(3, ImportActivityTexts.zones(clock, "").size)
        assertEquals(
            AppText.named("import_time_zone_option", AppText.written("America/New York"), AppText.written("UTC-04:00")),
            ImportActivityTexts.zone(zones[0]),
        )
    }

    @Test
    fun combineIsLeftOutWhileTheLoggedActivityUploads() {
        assertEquals(3, ImportActivityTexts.duplicateChoices(dup()).size)
        val uploading = dup(uploading = true)
        assertEquals(
            listOf(DuplicateAction.Ignore, DuplicateAction.ImportAnyway),
            ImportActivityTexts.duplicateChoices(uploading).map { it.action },
        )
        assertEquals(AppText.named("import_combine_uploading"), ImportActivityTexts.duplicateExplained(uploading))
    }

    @Test
    fun aWarningNamesWhatItRepeats() {
        val logged = ExistingActivityPreview(null, startAt("2024-05-14 12:01"), "Fenix 7")
        assertEquals(
            AppText.named(
                "import_duplicate_existing",
                AppText.named(
                    "text_dot_pair",
                    AppText.named("text_dot_pair", AppText.named("import_duplicate_untitled"), DateText.at(logged.start.epochSeconds, 0)),
                    AppText.written("Fenix 7"),
                ),
            ),
            ImportActivityTexts.duplicateWarning(dup(existing = logged)),
        )
        val earlier = row(1, "2024-05-03 18:16", duplicate = dup(inBatch = 0))
        val batch = ImportBatch(rows = persistentListOf(row(0, "2024-05-03 18:15", dive = DiveRowExtras(30.0, 112)), earlier))
        assertEquals(112, ImportActivityTexts.inFileNumber(batch, earlier))
    }

    @Test
    fun theBatchGroupsItsShownRowsByMonth() {
        val batch = ImportBatch(rows = persistentListOf(row(0, "2024-04-28 07:02"), row(1, "2024-05-03 18:15"), row(2, "2024-05-14 12:00")))
        val months = ImportActivityTexts.months(batch)
        assertEquals(listOf(1, 2), months.map { it.rows.size })
        assertEquals(AppText.named("import_batch_month", AppText.named("month_short_5"), AppText.written("2024")), months[1].heading)
    }

    @Test
    fun saveBatchWordsEachUntitledRow() {
        val batch = ImportBatch(rows = persistentListOf(row(0, "2024-04-28 07:02", title = "Easy"), row(1, "2024-05-03 18:15")))
        val event = state(batch = batch).saveBatchEvent(source) as ImportActivityEvent.SaveBatch
        assertEquals(mapOf(1 to "default_title_morning:sport_ride"), event.defaultTitles)
        val save = state(preview = ImportPreview("a.fit", 1.minutes, null, null, dive = null, clock = null)).saveEvent(source)
        assertEquals(ImportActivityEvent.Save("default_title_evening:sport_ride"), save)
    }

    @Test
    fun saveFollowsTheState() {
        val preview = ImportPreview("a.fit", 1.minutes, null, null, dive = null, clock = null)
        assertTrue(state(preview = preview).canSave)
        assertFalse(state(preview = preview.copy(duplicate = dup().copy(action = DuplicateAction.Ignore))).canSave)
        assertFalse(state(preview = preview).copy(addsRecording = true).canSave)
        assertFalse(state(preview = preview).copy(duplicateCheck = DuplicateCheck.Checking).canSave)
        val batch = ImportBatch(rows = persistentListOf(row(0, "2024-04-28 07:02")))
        assertTrue(state(batch = batch).canSaveBatch)
        assertFalse(state(batch = batch.copy(rows = persistentListOf(row(0, "2024-04-28 07:02").copy(selected = false)))).canSaveBatch)
    }

    @Test
    fun aRowSaysWhatItIs() {
        val details = ImportActivityTexts.rowDetails(row(0, "2024-04-28 07:02"), UnitSystem.Metric)
        val start = DateText.at(startAt("2024-04-28 07:02").epochSeconds, 0)
        assertTrue(details.toString().contains(start.toString()) && details.toString().contains("52:00"))
        assertFalse(details.toString().contains("2024-04-28"))
        assertNull(ImportActivityTexts.duplicateBanner(DuplicateCheck.Done, 0, 0))
        assertEquals(AppText.named("import_duplicates_failed"), ImportActivityTexts.duplicateBanner(DuplicateCheck.Failed, 0, 0))
    }

    @Test
    fun aStartIsItsDateAtItsTimeOnItsZonesClock() {
        // 07:02 at UTC+2: the date written out, the time of day an AppText.Time each UI formats on the device's 12- or 24-hour clock.
        val start = startAt("2024-05-12 07:02", offsetMinutes = 120)
        assertEquals(
            AppText.named("page_start_value", DateText.date(2024, 5, 12), AppText.Time(start.epochSeconds, 120)),
            ImportActivityTexts.start(start),
        )
    }

    @Test
    fun aStartOnAnotherOffsetKeepsThatOffsetsDay() {
        // An existing activity that started at 23:30 UTC, read in the file's UTC+2: the next day, at 01:30.
        val at = startAt("2024-05-12 23:30")
        val logged = ExistingActivityPreview("Lunch loop", ImportStart(at.epochSeconds, 120), null, ride, 52.minutes, 42_300.0)
        val compared = ImportActivityTexts.compareMatched(logged, UnitSystem.Metric)
        assertEquals(AppText.named("page_start_value", DateText.date(2024, 5, 13), AppText.Time(at.epochSeconds, 120)), compared[1])
        assertEquals(
            AppText.named("page_start_value", DateText.date(2024, 5, 12), AppText.Time(at.epochSeconds, 0)),
            ImportActivityTexts.compareRow(row(0, "2024-05-12 23:30"), UnitSystem.Metric)[1],
        )
    }
}
