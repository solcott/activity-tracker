package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.auth.GrantFeature
import io.github.solcott.activitytracker.data.auth.GrantStatus
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlinx.collections.immutable.persistentListOf

/**
 * What the Account page says that follows a rule (auth/account-page, auth/data-export), chosen here for the shared and the native UI
 * (dev/shared-display-text). Lists are plain lists, read once per state: Swift export carries a function's `List` result, and can't carry
 * the set of features an outdated grant holds.
 */
object AccountTexts {
    /** What the Sign-in section says about the grant. */
    fun signIn(grant: GrantStatus): AppText =
        AppText.named(
            when (grant) {
                GrantStatus.Current -> "account_sign_in_current"
                GrantStatus.SpacesUnsupported -> "account_sign_in_spaces_unsupported"
                is GrantStatus.Outdated -> "account_sign_in_outdated"
            },
        )

    /** What an outdated grant stops the athlete doing, one line per feature in the order the page lists them; none otherwise. */
    fun missingFeatures(grant: GrantStatus): List<AppText> =
        if (grant is GrantStatus.Outdated) GrantFeature.entries.filter { it in grant.missing }.map { AppText.named(it.textName) }
        else emptyList()

    /** The export's status, line by line, under the Your data heading. The web's "keep this tab open" is the shared UI's own. */
    fun exportLines(export: DataExportStatus): List<AppText> =
        when (export) {
            DataExportStatus.None -> listOf(AppText.named("export_data_about"))
            is DataExportStatus.Running -> listOf(running(export))
            is DataExportStatus.Ready ->
                listOfNotNull(
                    AppText.named("export_data_ready"),
                    count("export_data_problems", export.problems),
                    count("export_data_unsent", export.unsentChanges),
                    if (export.spacesIncluded) null else AppText.named("export_data_no_spaces"),
                )
            is DataExportStatus.Paused ->
                listOf(
                    export.neededBytes?.let {
                        AppText.named(
                            "export_data_paused_space",
                            AppText.named("import_megabytes", AppText.written(formatWhole((it + BYTES_PER_MB - 1) / BYTES_PER_MB))),
                        )
                    } ?: AppText.named("export_data_paused_space_unknown"),
                )
            DataExportStatus.Failed -> listOf(AppText.named("export_data_failed"))
        }

    /** The DID's Copy button, which reads Copied once tapped, until the page goes. */
    fun didCopy(copied: Boolean): AppText = AppText.named(if (copied) "account_did_copied" else "account_did_copy")

    /**
     * The export's buttons under its lines: Export; Cancel while it runs; Save and Delete export once ready; Try again and Cancel once
     * stopped.
     */
    fun exportControls(export: DataExportStatus): List<ExportControl> =
        when (export) {
            DataExportStatus.None -> listOf(ExportControl(ExportAction.Export, AppText.named("export_data"), primary = true))
            is DataExportStatus.Running -> listOf(cancelExport)
            is DataExportStatus.Ready ->
                listOf(
                    ExportControl(ExportAction.Save, AppText.named("export_data_save"), primary = true),
                    ExportControl(ExportAction.Cancel, AppText.named("export_data_delete"), primary = false),
                )
            is DataExportStatus.Paused,
            DataExportStatus.Failed ->
                listOf(ExportControl(ExportAction.Export, AppText.named("export_data_try_again"), primary = true), cancelExport)
        }

    private val cancelExport: ExportControl
        get() = ExportControl(ExportAction.Cancel, AppText.named("export_data_cancel"), primary = false)

    /** The running export's one line, by the step it's on. */
    fun running(export: DataExportStatus.Running): AppText =
        when (export.phase) {
            DataExportStatus.Running.Phase.Records -> count("export_data_records", export.records) ?: AppText.named("export_data_listing")
            DataExportStatus.Running.Phase.Files ->
                AppText.named(
                    "export_data_files",
                    AppText.written(formatWhole(export.filesDone)),
                    AppText.written(formatWhole(export.filesTotal)),
                )
            DataExportStatus.Running.Phase.Activities ->
                AppText.named(
                    "export_data_activities",
                    AppText.written(formatWhole(export.activitiesDone)),
                    AppText.written(formatWhole(export.activitiesTotal)),
                )
        }

    /** The question before ending the session with work pending: its title. */
    fun endTitle(ending: EndingSession): AppText =
        AppText.named(if (ending.signInAgain) "account_end_title_sign_in_again" else "account_end_title")

    /** The question's message: what would be left behind, a paragraph each (empty text with nothing pending, when it isn't asked). */
    fun endMessage(ending: EndingSession): AppText =
        AppText.paragraphs(
            listOfNotNull(
                count("account_end_changes", ending.pending.changes),
                if (ending.pending.importSaving) AppText.named("account_end_import") else null,
                if (ending.pending.exportPending) AppText.named("account_end_export") else null,
            ),
        ) ?: AppText.written("")

    /** The question's destructive answer. */
    fun endAction(ending: EndingSession): AppText =
        AppText.named(if (ending.signInAgain) "account_end_sign_in_again_anyway" else "account_end_sign_out_anyway")

    private fun count(name: String, quantity: Int): AppText? =
        if (quantity > 0) AppText.Plural(name, quantity, persistentListOf(AppText.written(formatWhole(quantity)))) else null

    private val GrantFeature.textName: String
        get() =
            when (this) {
                GrantFeature.PrivateActivities -> "account_feature_private_activities"
                GrantFeature.PrivacyZones -> "account_feature_privacy_zones"
                GrantFeature.PrivateProfile -> "account_feature_private_profile"
                GrantFeature.PrivateInteractions -> "account_feature_private_interactions"
                GrantFeature.BlocksAndMutes -> "account_feature_blocks_and_mutes"
                GrantFeature.Gear -> "account_feature_gear"
                GrantFeature.SegmentsAndGroups -> "account_feature_segments_and_groups"
                GrantFeature.Leaving -> "account_feature_leaving"
            }

    private const val BYTES_PER_MB = 1024L * 1024
}
