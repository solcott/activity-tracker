package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every string the manual form's texts choose exists (dev/shared-display-text): `SharedTextNamesTest` can't see a feature's contracts. */
class RecordTextNamesTest {
    @Test
    fun everyNameTheFormChoosesHasItsString() {
        val texts = UnitSystem.entries.flatMap { listOf(ManualActivityTexts.distanceLabel(it), ManualActivityTexts.temperatureLabel(it)) }
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    @Test
    fun everyNameTheImportScreenChoosesHasItsString() {
        val t = ImportActivityTexts
        val units = UnitSystem.Metric
        val states = ImportActivitySamples.all.values + ImportActivitySamples.variants.values + ImportActivitySamples.batchPreview
        val texts =
            listOf(
                t.hint,
                t.cancel,
                t.retry,
                t.originalTooLarge,
                t.savingTitle,
                t.viewUploads,
                t.stoppedDismiss,
                t.thresholdsHeading,
                t.tooFar,
                t.detailsHint,
                t.batchVisibilityHeading,
                t.bulkHeading,
                t.itemTitle,
                t.itemDone,
                t.itemTitleLabel,
                t.compareExact,
                t.compareThis,
                t.batchSavedTitle,
                t.suggestionsTitle,
                t.suggestionsExplained,
                t.suggestionCombine,
                t.savedDone,
                t.timeZone,
                t.afterTitle,
                t.afterLinksNotSaved,
                t.afterDone,
                t.pastApply,
                t.pastSkip,
                t.newDevicesTitle,
                t.newDevicesHelp,
                t.deviceNameLabel,
                t.deviceTypeLabel,
                t.deviceKindLabel,
                t.deviceMountLabel,
                t.deviceCollectionsLabel,
                t.profileLinkHelp,
            ) +
                listOf(true, false).flatMap { listOf(t.title(it), t.pick(it), t.save(it), t.uploadingNote(it)) } +
                PartOfDay.entries.map { t.defaultTitle(DefaultTitle(it, ActivityType(Sport.RIDE))) } +
                DuplicateAction.entries.map(t::action) +
                BatchFilter.entries.map(t::filter) +
                NewDeviceDecision.entries.map(t::decision) +
                DuplicateCheck.entries.mapNotNull { t.duplicateBanner(it, 2, 1, 1) } +
                listOf(t.reading(null), t.reading(ReadProgress(3, 4))) +
                states.flatMap { state -> stateTexts(state, units) }
        val strings = Res.allStringResources.keys
        val plurals = Res.allPluralStringResources.keys
        val missing = texts.flatMap(::names).distinct().filterNot { it in strings || it in plurals }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun stateTexts(state: ImportActivityState, units: UnitSystem): List<AppText> {
        val t = ImportActivityTexts
        val batch = state.batch
        val preview = state.preview
        return listOfNotNull(t.duplicateBanner(state), state.addingTo?.let(t::addingTo), preview?.clock?.let { t.zoneNotice(it) }) +
            listOfNotNull(preview?.duplicate?.let { t.duplicateWarning(it, 1) }, preview?.duplicate?.let(t::duplicateExplained)) +
            state.thresholdChanges.map(t::thresholdChange) +
            state.stoppedImports.map(t::stopped) +
            listOfNotNull(state.savingImport?.let(t::savingProgress), state.batchSaved?.let { t.batchSaved(it, true) }) +
            state.savedSuggestions.orEmpty().map(t::suggestionCue) +
            state.newDevices.cards.flatMap {
                listOfNotNull(t.deviceName(it), t.deviceAbout(it), t.deviceKind(it), t.deviceMount(it, state.newDevices))
            } +
            listOfNotNull(state.newDevices.profileOffer?.let(t::profileLinkTitle), state.newDevices.profileOffer?.let(t::profileLinked)) +
            state.gear?.conflicts.orEmpty().map(t::gearConflict) +
            listOfNotNull(state.afterImport?.pastImports?.let(t::pastOffer), state.afterImport?.pastImports?.let(t::pastProgress)) +
            (batch
                ?.let { b ->
                    listOfNotNull(
                        t.found(b),
                        t.skipped(b),
                        t.ignored(b),
                        t.originalsTooLarge(b),
                        t.selected(b),
                        t.selectAll(b),
                        t.originalsSize(b, true),
                        t.saveFailure(state, b),
                        t.saveBatch(state, b),
                        t.batchDuplicateBanner(state, b),
                    ) +
                        b.exports.map(t::exportNote) +
                        b.clocks.map { t.zoneNotice(it.clock, it.fileName) } +
                        t.months(b).map { it.heading } +
                        b.rows.flatMap { row ->
                            listOfNotNull(t.rowTitle(row), t.rowDetails(row, units), t.narrowed(row)) +
                                listOfNotNull(
                                    row.duplicate?.let { t.duplicateWarning(it, t.inFileNumber(b, row)) },
                                    row.duplicate?.let(t::compareOther),
                                ) +
                                t.compareRow(row, units)
                        } +
                        listOfNotNull(b.preview?.matched?.let { t.compareMatched(it, units) }).flatten()
                }
                .orEmpty())
    }

    /** Names a text needs, leaving out those with a fallback (a gear kind this client may not name). */
    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> (if (text.fallback == null) listOf(text.name) else emptyList()) + text.args.flatMap(::names)
            is AppText.Plural -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
