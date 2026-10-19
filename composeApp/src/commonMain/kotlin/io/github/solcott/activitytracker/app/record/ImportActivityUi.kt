package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityDetailsForm
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.ActivityVisibilityForm
import io.github.solcott.activitytracker.app.ui.DepthProfile
import io.github.solcott.activitytracker.app.ui.DiveFormat
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.Stat
import io.github.solcott.activitytracker.app.ui.StatsRow
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.distanceStat
import io.github.solcott.activitytracker.app.ui.elevationStat
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_save
import io.github.solcott.activitytracker.composeApp.resources.action_saving
import io.github.solcott.activitytracker.composeApp.resources.default_title_afternoon
import io.github.solcott.activitytracker.composeApp.resources.default_title_evening
import io.github.solcott.activitytracker.composeApp.resources.default_title_morning
import io.github.solcott.activitytracker.composeApp.resources.default_title_night
import io.github.solcott.activitytracker.composeApp.resources.dive_bottom_time
import io.github.solcott.activitytracker.composeApp.resources.dive_closed_circuit
import io.github.solcott.activitytracker.composeApp.resources.dive_cns
import io.github.solcott.activitytracker.composeApp.resources.dive_gradient_factors
import io.github.solcott.activitytracker.composeApp.resources.dive_number
import io.github.solcott.activitytracker.composeApp.resources.dive_open_circuit
import io.github.solcott.activitytracker.composeApp.resources.dive_otu
import io.github.solcott.activitytracker.composeApp.resources.dive_profile
import io.github.solcott.activitytracker.composeApp.resources.dive_rmv
import io.github.solcott.activitytracker.composeApp.resources.dive_sac
import io.github.solcott.activitytracker.composeApp.resources.dive_site
import io.github.solcott.activitytracker.composeApp.resources.dive_surface_interval
import io.github.solcott.activitytracker.composeApp.resources.dive_tank
import io.github.solcott.activitytracker.composeApp.resources.dive_tank_numbered
import io.github.solcott.activitytracker.composeApp.resources.import_hint
import io.github.solcott.activitytracker.composeApp.resources.import_original_too_large
import io.github.solcott.activitytracker.composeApp.resources.import_pick
import io.github.solcott.activitytracker.composeApp.resources.import_pick_another
import io.github.solcott.activitytracker.composeApp.resources.import_reading
import io.github.solcott.activitytracker.composeApp.resources.import_recorded_with
import io.github.solcott.activitytracker.composeApp.resources.import_threshold_change
import io.github.solcott.activitytracker.composeApp.resources.import_threshold_change_sport
import io.github.solcott.activitytracker.composeApp.resources.import_thresholds_heading
import io.github.solcott.activitytracker.composeApp.resources.import_title
import io.github.solcott.activitytracker.composeApp.resources.stat_avg_depth
import io.github.solcott.activitytracker.composeApp.resources.stat_dive_time
import io.github.solcott.activitytracker.composeApp.resources.stat_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.stat_max_depth
import io.github.solcott.activitytracker.composeApp.resources.stat_time
import io.github.solcott.activitytracker.composeApp.resources.stat_water_temp
import io.github.solcott.activitytracker.composeApp.resources.threshold_ftp
import io.github.solcott.activitytracker.composeApp.resources.threshold_max_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.threshold_resting_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.threshold_threshold_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.unit_bpm
import io.github.solcott.activitytracker.composeApp.resources.unit_watts
import io.github.solcott.activitytracker.composeApp.resources.value_missing
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.stringResource

@CircuitInject(ImportActivityScreen::class, SessionScope::class)
@Composable
fun ImportActivityUi(state: ImportActivityState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    // Unfiltered: Android and iOS have no MIME type or UTType for .fit, so an extension filter hides the files. The presenter checks it.
    val pick =
        rememberFilePickerLauncher(type = FileKitType.File()) { file ->
            file ?: return@rememberFilePickerLauncher
            scope.launch {
                runSuspendCatching { file.readBytes() }
                    .onSuccess { state.eventSink(ImportActivityEvent.FilePicked(file.name, ByteString(it))) }
            }
        }
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.import_title),
                onBack = { state.eventSink(ImportActivityEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val scroll = rememberScrollState()
        ScrollbarBox(scroll, contentPadding = padding) {
            Column(
                Modifier.verticalScroll(scroll).padding(padding).readableWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val preview = state.preview
                val dives = state.dives
                val picked = preview != null || dives != null
                if (!picked) Text(stringResource(Res.string.import_hint))
                AppSecondaryButton(onClick = { pick.launch() }, enabled = !state.reading && !state.saving) {
                    Text(stringResource(if (picked) Res.string.import_pick_another else Res.string.import_pick))
                }
                if (state.reading) {
                    AppProgressIndicator()
                    Text(stringResource(Res.string.import_reading))
                }
                state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
                if (picked && state.originalTooLarge) {
                    Text(stringResource(Res.string.import_original_too_large), style = MaterialTheme.typography.bodySmall)
                }
                if (preview != null) PreviewForm(state, preview)
                if (dives != null) DiveBatchForm(state, dives)
            }
        }
    }
}

@Composable
private fun PreviewForm(state: ImportActivityState, preview: ImportPreview, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(preview.fileName, style = MaterialTheme.typography.titleMedium)
        preview.device?.let { Text(stringResource(Res.string.import_recorded_with, it), style = MaterialTheme.typography.bodySmall) }
        preview.clock?.let { clock ->
            TimeZoneNotice(clock, { state.eventSink(ImportActivityEvent.TimeZoneChanged(it)) }, enabled = !state.saving)
        }
        PreviewDuplicate(state, preview)
        val dive = preview.dive
        if (dive != null) {
            DiveDetails(dive, preview.elapsed.inWholeSeconds)
        } else {
            StatsRow(
                listOfNotNull(
                        distanceStat(state.type.selected.sport, preview.distanceMeters),
                        Stat(
                            stringResource(Res.string.stat_time),
                            Format.duration(preview.movingSeconds ?: preview.elapsed.inWholeSeconds),
                        ),
                        elevationStat(state.type.selected.sport, preview.elevationGainMeters),
                        Stat(
                            stringResource(Res.string.stat_heart_rate),
                            preview.avgHeartRate?.let { stringResource(Res.string.unit_bpm, it) }
                                ?: stringResource(Res.string.value_missing),
                        ),
                    )
                    .toImmutableList()
            )
        }
        preview.polyline?.let { ActivityMap(it) }
        if (dive == null) RideDetails(preview)
        ActivityTypePicker(
            state.type,
            enabled = !state.saving,
        )
        val defaultTitle = state.defaultTitle?.let { defaultTitleLabel(it) }
        // Present whenever a single activity is: the title, description, dive site and photos.
        state.details?.let { ActivityDetailsForm(state.title, titlePlaceholder = defaultTitle, fields = it, enabled = !state.saving) }
        if (state.thresholdChanges.isNotEmpty()) ThresholdChanges(state.thresholdChanges)
        ActivityVisibilityForm(
            state.visibility,
            enabled = !state.saving,
        )
        AppButton(onClick = { state.eventSink(ImportActivityEvent.Save(defaultTitle)) }, enabled = state.canSave(preview)) {
            Text(stringResource(if (state.saving) Res.string.action_saving else Res.string.action_save))
        }
    }
}

/** The thresholds saving updates from the device's settings: "FTP: 231 → 240 W", or "Run · FTP: …" for a sport's override. */
@Composable
private fun ThresholdChanges(changes: ImmutableList<ThresholdChange>, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(Res.string.import_thresholds_heading),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        changes.forEach { change ->
            val from = thresholdValue(change.threshold, change.from)
            val to = thresholdValue(change.threshold, change.to)
            Text(
                change.sport?.let { sport ->
                    stringResource(Res.string.import_threshold_change_sport, sportLabel(sport), thresholdLabel(change.threshold), from, to)
                } ?: stringResource(Res.string.import_threshold_change, thresholdLabel(change.threshold), from, to),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** A threshold's value in watts or bpm, or a dash when it wasn't set. */
@Composable
private fun thresholdValue(threshold: String, value: Long?): String =
    when {
        value == null -> stringResource(Res.string.value_missing)
        threshold == Threshold.FUNCTIONAL_THRESHOLD_POWER -> stringResource(Res.string.unit_watts, value.toString())
        else -> stringResource(Res.string.unit_bpm, value.toString())
    }

@Composable
private fun thresholdLabel(threshold: String): String =
    when (threshold) {
        Threshold.FUNCTIONAL_THRESHOLD_POWER -> stringResource(Res.string.threshold_ftp)
        Threshold.MAX_HEART_RATE -> stringResource(Res.string.threshold_max_heart_rate)
        Threshold.THRESHOLD_HEART_RATE -> stringResource(Res.string.threshold_threshold_heart_rate)
        Threshold.RESTING_HEART_RATE -> stringResource(Res.string.threshold_resting_heart_rate)
        else -> threshold
    }

/** "Morning Mountain Bike Ride". */
@Composable
internal fun defaultTitleLabel(title: DefaultTitle): String =
    stringResource(
        when (title.partOfDay) {
            PartOfDay.Morning -> Res.string.default_title_morning
            PartOfDay.Afternoon -> Res.string.default_title_afternoon
            PartOfDay.Evening -> Res.string.default_title_evening
            PartOfDay.Night -> Res.string.default_title_night
        },
        activityTypeLabel(title.type),
    )

@Composable
private fun DiveDetails(dive: DivePreview, elapsedSeconds: Long, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsRow(
            persistentListOf(
                Stat(stringResource(Res.string.stat_max_depth), Format.depth(dive.maxDepthMeters)),
                Stat(stringResource(Res.string.stat_dive_time), Format.duration(elapsedSeconds)),
                Stat(stringResource(Res.string.stat_avg_depth), Format.depth(dive.avgDepthMeters)),
                Stat(
                    stringResource(Res.string.stat_water_temp),
                    Format.temperature(dive.minWaterTempCelsius?.let { (it * TENTHS_PER_DEGREE).roundToLong() }),
                ),
            )
        )
        DepthProfile(dive.profile, stringResource(Res.string.dive_profile))
        val circuit = stringResource(if (dive.closedCircuit) Res.string.dive_closed_circuit else Res.string.dive_open_circuit)
        val gases = dive.gases.map { DiveFormat.gasName(it.oxygenPercent, it.heliumPercent) }
        Text((listOf(circuit) + gases.joinToString(" → ").ifEmpty { null }).filterNotNull().joinToString(" · "))
        val counts =
            listOfNotNull(
                dive.diveNumber?.let { stringResource(Res.string.dive_number, it) },
                dive.bottomTimeSeconds?.let { stringResource(Res.string.dive_bottom_time, Format.duration(it)) },
                dive.surfaceIntervalMinutes?.let { stringResource(Res.string.dive_surface_interval, DiveFormat.surfaceInterval(it)) },
            )
        if (counts.isNotEmpty()) Text(counts.joinToString(" · "))
        val deco =
            listOfNotNull(
                dive.gradientFactorLow?.let { low ->
                    dive.gradientFactorHigh?.let { high -> stringResource(Res.string.dive_gradient_factors, low, high) }
                },
                dive.startCns?.let { start -> dive.endCns?.let { end -> stringResource(Res.string.dive_cns, start, end) } },
                dive.oxygenToxicityOtu?.let { stringResource(Res.string.dive_otu, it) },
            )
        if (deco.isNotEmpty()) Text(deco.joinToString(" · "))
        DiveAir(dive)
        dive.site?.let {
            Text(
                stringResource(
                    Res.string.dive_site,
                    formatDecimal(it.latitude, COORDINATE_DECIMALS),
                    formatDecimal(it.longitude, COORDINATE_DECIMALS),
                )
            )
        }
    }
}

/** Each tank's pressures, then air consumption: by volume when the tank's size was set, else by pressure. */
@Composable
private fun DiveAir(dive: DivePreview) {
    val tanks =
        dive.tanks.mapIndexed { i, tank ->
            val start = DiveFormat.pressure(tank.startPressureBar)
            val end = DiveFormat.pressure(tank.endPressureBar)
            if (dive.tanks.size == 1) stringResource(Res.string.dive_tank, start, end)
            else stringResource(Res.string.dive_tank_numbered, i + 1, start, end)
        }
    val consumption =
        dive.rmvLitersPerMinute?.let { stringResource(Res.string.dive_rmv, DiveFormat.gasRate(it)) }
            ?: dive.sacBarPerMinute?.let { stringResource(Res.string.dive_sac, DiveFormat.pressureRate(it)) }
    val air = tanks + listOfNotNull(consumption)
    if (air.isNotEmpty()) Text(air.joinToString(" · "))
}

/** About 10 m: enough to find the site, no more. */
private const val COORDINATE_DECIMALS = 4

/** The formatter takes tenths of a degree Celsius, like records. */
private const val TENTHS_PER_DEGREE = 10
