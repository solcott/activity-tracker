package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.auth.GrantFeature
import io.github.solcott.activitytracker.data.auth.GrantStatus
import io.github.solcott.activitytracker.data.auth.PendingWork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** What the Account page says, which both UIs ask (dev/shared-display-text, "The Account page reads the same in both UIs"). */
class AccountTextsTest {
    private fun count(name: String, quantity: Int, shown: String = "$quantity") =
        AppText.Plural(name, quantity, persistentListOf(AppText.written(shown)))

    private fun state(grant: GrantStatus) =
        AccountState(handle = "alice.test", did = "did:plc:a", pdsHost = "pds.test", grant = grant, eventSink = {})

    @Test
    fun theSignInLineFollowsTheGrant() {
        assertEquals(AppText.named("account_sign_in_current"), AccountTexts.signIn(GrantStatus.Current))
        assertEquals(AppText.named("account_sign_in_spaces_unsupported"), AccountTexts.signIn(GrantStatus.SpacesUnsupported))
        assertEquals(AppText.named("account_sign_in_outdated"), AccountTexts.signIn(GrantStatus.Outdated(setOf(GrantFeature.Gear))))
    }

    @Test
    fun missingFeaturesAreListedInThePagesOrder() {
        assertEquals(
            listOf(AppText.named("account_feature_gear"), AppText.named("account_feature_leaving")),
            AccountTexts.missingFeatures(GrantStatus.Outdated(setOf(GrantFeature.Leaving, GrantFeature.Gear))),
        )
        assertEquals(GrantFeature.entries.size, AccountTexts.missingFeatures(GrantStatus.Outdated(GrantFeature.entries.toSet())).size)
        assertEquals(emptyList(), AccountTexts.missingFeatures(GrantStatus.Current))
    }

    @Test
    fun onlyAnOutdatedGrantOffersSigningInAgain() {
        assertTrue(state(GrantStatus.Outdated(setOf(GrantFeature.Gear))).offersSignInAgain)
        assertFalse(state(GrantStatus.Current).offersSignInAgain)
        assertFalse(state(GrantStatus.SpacesUnsupported).offersSignInAgain)
    }

    @Test
    fun aRunningExportSaysTheStepItIsOn() {
        assertEquals(AppText.named("export_data_listing"), AccountTexts.running(DataExportStatus.Running(0, 0, 0, 0, 0)))
        assertEquals(count("export_data_records", 1210, "1,210"), AccountTexts.running(DataExportStatus.Running(1210, 0, 0, 0, 0)))
        assertEquals(
            AppText.named("export_data_files", AppText.written("3"), AppText.written("10")),
            AccountTexts.running(DataExportStatus.Running(40, 3, 10, 0, 0)),
        )
        assertEquals(
            AppText.named("export_data_activities", AppText.written("120"), AppText.written("400")),
            AccountTexts.running(DataExportStatus.Running(2412, 900, 900, 120, 400)),
        )
        assertEquals(
            listOf(AccountTexts.running(DataExportStatus.Running(5, 0, 0, 0, 0))),
            AccountTexts.exportLines(DataExportStatus.Running(5, 0, 0, 0, 0)),
        )
    }

    @Test
    fun aPausedExportAsksForWholeMegabytesRoundedUp() {
        assertEquals(
            listOf(AppText.named("export_data_paused_space", AppText.named("import_megabytes", AppText.written("2")))),
            AccountTexts.exportLines(DataExportStatus.Paused(neededBytes = 1024L * 1024 + 1)),
        )
        assertEquals(listOf(AppText.named("export_data_paused_space_unknown")), AccountTexts.exportLines(DataExportStatus.Paused(null)))
    }

    @Test
    fun theOtherExportStatuses() {
        assertEquals(listOf(AppText.named("export_data_about")), AccountTexts.exportLines(DataExportStatus.None))
        assertEquals(listOf(AppText.named("export_data_failed")), AccountTexts.exportLines(DataExportStatus.Failed))
    }

    @Test
    fun endingTheSessionSaysWhatWouldBeLeftBehind() {
        val signingOut = EndingSession(signInAgain = false, PendingWork(changes = 2, importSaving = false))
        assertEquals(AppText.named("account_end_title"), AccountTexts.endTitle(signingOut))
        assertEquals(count("account_end_changes", 2), AccountTexts.endMessage(signingOut))
        assertEquals(AppText.named("account_end_sign_out_anyway"), AccountTexts.endAction(signingOut))

        val signingInAgain = EndingSession(signInAgain = true, PendingWork(changes = 0, importSaving = true, exportPending = true))
        assertEquals(AppText.named("account_end_title_sign_in_again"), AccountTexts.endTitle(signingInAgain))
        assertEquals(
            AppText.named("text_paragraph_pair", AppText.named("account_end_import"), AppText.named("account_end_export")),
            AccountTexts.endMessage(signingInAgain),
        )
        assertEquals(AppText.named("account_end_sign_in_again_anyway"), AccountTexts.endAction(signingInAgain))
    }

    @Test
    fun theExportOffersExportThenCancelThenTryAgain() {
        val export = ExportControl(ExportAction.Export, AppText.named("export_data"), primary = true)
        val cancel = ExportControl(ExportAction.Cancel, AppText.named("export_data_cancel"), primary = false)
        val tryAgain = ExportControl(ExportAction.Export, AppText.named("export_data_try_again"), primary = true)
        assertEquals(listOf(export), AccountTexts.exportControls(DataExportStatus.None))
        assertEquals(listOf(cancel), AccountTexts.exportControls(DataExportStatus.Running(5, 0, 0, 0, 0)))
        assertEquals(listOf(tryAgain, cancel), AccountTexts.exportControls(DataExportStatus.Paused(null)))
        assertEquals(listOf(tryAgain, cancel), AccountTexts.exportControls(DataExportStatus.Failed))
    }

    @Test
    fun copyReadsCopiedOnceTapped() {
        assertEquals(AppText.named("account_did_copy"), AccountTexts.didCopy(copied = false))
        assertEquals(AppText.named("account_did_copied"), AccountTexts.didCopy(copied = true))
    }
}
