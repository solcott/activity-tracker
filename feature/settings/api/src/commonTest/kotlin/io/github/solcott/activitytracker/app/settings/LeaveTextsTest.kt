package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.auth.LeaveStep
import io.github.solcott.activitytracker.data.auth.PendingWork
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf

/**
 * What Leave Activity Tracker says and offers, which both UIs ask (dev/shared-display-text, "The Leave page reads the same in both UIs").
 */
class LeaveTextsTest {
    private fun count(name: String, quantity: Int, shown: String = "$quantity") =
        AppText.Plural(name, quantity, persistentListOf(AppText.written(shown)))

    private fun state(typed: String = "") = LeaveState(handle = "alice.test", confirmHandle = textFieldStateOf(typed), eventSink = {})

    @Test
    fun placesAreCountedStayingFirst() {
        assertEquals(
            listOf(count("leave_places_active_segments", 2), count("leave_places_other_sites", 1)),
            LeaveTexts.places(SharedPlacesSummary.Loaded(activeSegments = 2, otherSegments = 0, activeSites = 0, otherSites = 1)),
        )
        assertEquals(listOf(AppText.named("leave_places_none")), LeaveTexts.places(SharedPlacesSummary.Loaded(0, 0, 0, 0)))
        assertEquals(listOf(AppText.named("leave_places_loading")), LeaveTexts.places(SharedPlacesSummary.Loading))
    }

    @Test
    fun theDeviceSectionListsOnlyWhatWouldBeLost() {
        assertFalse(state().showsDeviceWork)
        assertFalse(state().copy(pending = PendingWork(changes = 0, importSaving = false)).showsDeviceWork)
        val busy = state().copy(pending = PendingWork(changes = 2, importSaving = true), recording = true)
        assertTrue(busy.showsDeviceWork)
        assertEquals(
            listOf(count("leave_device_changes", 2), AppText.named("leave_device_import"), AppText.named("leave_device_recording")),
            LeaveTexts.deviceLines(busy),
        )
    }

    @Test
    fun aRunSaysItsStep() {
        assertEquals(AppText.named("leave_step_starting"), LeaveTexts.step(LeaveRun()))
        assertEquals(AppText.named("leave_step_spaces"), LeaveTexts.step(LeaveRun(LeaveStep.Spaces)))
        assertEquals(AppText.named("leave_step_repo"), LeaveTexts.step(LeaveRun(LeaveStep.Repo)))
    }

    @Test
    fun aRunCountsWhatItDeletedAndWhenItCarriesOn() {
        assertNull(LeaveTexts.recordsDeleted(LeaveRun()))
        assertEquals(count("leave_records_deleted", 1200, "1,200"), LeaveTexts.recordsDeleted(LeaveRun(recordsDeleted = 1200)))
        assertNull(LeaveTexts.paused(LeaveRun()))
        assertEquals(
            AppText.named("leave_paused", AppText.Time(1_794_337_500)),
            LeaveTexts.paused(LeaveRun(pausedUntil = Instant.fromEpochSeconds(1_794_337_500))),
        )
    }

    @Test
    fun leavingNeedsTheHandleTyped() {
        assertTrue(state().canLeave("@Alice.test"))
        assertTrue(state().canLeave(" alice.test "))
        assertFalse(state().canLeave("alice"))
        assertTrue(state("alice.test").canLeave)
        assertFalse(state("alice.test").copy(export = LeaveExport.Running).canLeave)
    }

    @Test
    fun aRunningLeaveCantBeLeft() {
        assertTrue(state().canGoBack)
        assertFalse(state().copy(run = LeaveRun()).canGoBack)
    }

    @Test
    fun theExportSaysAndOffersOneThingPerState() {
        assertEquals(AppText.named("leave_export_none"), LeaveTexts.exportLine(LeaveExport.None))
        assertEquals(AppText.named("leave_export_running"), LeaveTexts.exportLine(LeaveExport.Running))
        assertEquals(AppText.named("leave_export_ready"), LeaveTexts.exportLine(LeaveExport.Ready))
        assertEquals(
            listOf(ExportControl(ExportAction.Export, AppText.named("export_data"), primary = false)),
            LeaveTexts.exportControls(state()),
        )
        assertEquals(
            listOf(ExportControl(ExportAction.Export, AppText.named("export_data"), primary = false, enabled = false)),
            LeaveTexts.exportControls(state().copy(starting = true)),
            "Export data waits while leaving starts",
        )
        assertEquals(
            listOf(ExportControl(ExportAction.Cancel, AppText.named("export_data_cancel"), primary = false)),
            LeaveTexts.exportControls(state().copy(export = LeaveExport.Running)),
        )
        assertEquals(
            listOf(ExportControl(ExportAction.Save, AppText.named("leave_export_save"), primary = true)),
            LeaveTexts.exportControls(state().copy(export = LeaveExport.Ready)),
        )
    }
}
