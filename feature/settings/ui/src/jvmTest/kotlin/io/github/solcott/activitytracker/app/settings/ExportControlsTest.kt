package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The data export's steps both UIs draw (auth/data-export, dev/shared-display-text): a ready archive's buttons and every name. */
class ExportControlsTest {
    @Test
    fun aReadyArchiveOffersSaveThenDelete() {
        val ready = AccountSamples.all.getValue("exportReady").export
        assertEquals(
            listOf(
                ExportControl(ExportAction.Save, AppText.named("export_data_save"), primary = true),
                ExportControl(ExportAction.Cancel, AppText.named("export_data_delete"), primary = false),
            ),
            AccountTexts.exportControls(ready),
        )
    }

    @Test
    fun everyExportTextNamesAString() {
        val leave = LeaveState(handle = "alice.test", confirmHandle = textFieldStateOf(""), eventSink = {})
        val settings = SettingsState("alice.test", needsSignIn = true, UnitSystem.Metric, saving = false, error = null, eventSink = {})
        val texts =
            (AccountSamples.all.values + AccountSamples.variants.values)
                .flatMap { AccountTexts.exportControls(it.export) }
                .map { it.label } +
                LeaveExport.entries.flatMap { export ->
                    LeaveTexts.exportControls(leave.copy(export = export)).map { it.label } + LeaveTexts.exportLine(export)
                } +
                listOfNotNull(SettingsTexts.accountSummary(settings))
        val missing = texts.map { (it as AppText.Named).name }.distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }
}
