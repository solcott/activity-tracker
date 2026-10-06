package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.DepthProfile
import io.github.solcott.activitytracker.app.ui.DiveFormat
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.Stat
import io.github.solcott.activitytracker.app.ui.StatsRow
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_save
import io.github.solcott.activitytracker.composeApp.resources.action_saving
import io.github.solcott.activitytracker.composeApp.resources.dive_bottom_time
import io.github.solcott.activitytracker.composeApp.resources.dive_closed_circuit
import io.github.solcott.activitytracker.composeApp.resources.dive_cns
import io.github.solcott.activitytracker.composeApp.resources.dive_gas_air
import io.github.solcott.activitytracker.composeApp.resources.dive_gas_nitrox
import io.github.solcott.activitytracker.composeApp.resources.dive_gas_oxygen
import io.github.solcott.activitytracker.composeApp.resources.dive_gas_trimix
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
import io.github.solcott.activitytracker.composeApp.resources.field_title
import io.github.solcott.activitytracker.composeApp.resources.import_hint
import io.github.solcott.activitytracker.composeApp.resources.import_pick
import io.github.solcott.activitytracker.composeApp.resources.import_pick_another
import io.github.solcott.activitytracker.composeApp.resources.import_reading
import io.github.solcott.activitytracker.composeApp.resources.import_recorded_with
import io.github.solcott.activitytracker.composeApp.resources.import_title
import io.github.solcott.activitytracker.composeApp.resources.stat_avg_depth
import io.github.solcott.activitytracker.composeApp.resources.stat_distance
import io.github.solcott.activitytracker.composeApp.resources.stat_dive_time
import io.github.solcott.activitytracker.composeApp.resources.stat_elevation
import io.github.solcott.activitytracker.composeApp.resources.stat_heart_rate
import io.github.solcott.activitytracker.composeApp.resources.stat_max_depth
import io.github.solcott.activitytracker.composeApp.resources.stat_time
import io.github.solcott.activitytracker.composeApp.resources.stat_water_temp
import io.github.solcott.activitytracker.composeApp.resources.unit_bpm
import io.github.solcott.activitytracker.composeApp.resources.unit_days_hours
import io.github.solcott.activitytracker.composeApp.resources.unit_hours_minutes
import io.github.solcott.activitytracker.composeApp.resources.value_missing
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlin.math.roundToLong
import kotlinx.collections.immutable.persistentListOf
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
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = stringResource(Res.string.import_title), onBack = { state.eventSink(ImportActivityEvent.Back) }) },
    ) { padding ->
        val scroll = rememberScrollState()
        ScrollbarBox(scroll, Modifier.padding(padding)) {
            Column(
                Modifier.verticalScroll(scroll).readableWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val preview = state.preview
                if (preview == null) Text(stringResource(Res.string.import_hint))
                AppSecondaryButton(onClick = { pick.launch() }, enabled = !state.reading && !state.saving) {
                    Text(stringResource(if (preview == null) Res.string.import_pick else Res.string.import_pick_another))
                }
                if (state.reading) {
                    AppProgressIndicator()
                    Text(stringResource(Res.string.import_reading))
                }
                state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
                if (preview != null) PreviewForm(state, preview)
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
        val dive = preview.dive
        if (dive != null) {
            DiveDetails(dive, preview.elapsed.inWholeSeconds)
        } else {
            StatsRow(
                persistentListOf(
                    Stat(stringResource(Res.string.stat_distance), Format.distance(preview.distanceMeters)),
                    Stat(stringResource(Res.string.stat_time), Format.duration(preview.movingSeconds ?: preview.elapsed.inWholeSeconds)),
                    Stat(stringResource(Res.string.stat_elevation), Format.elevation(preview.elevationGainMeters)),
                    Stat(
                        stringResource(Res.string.stat_heart_rate),
                        preview.avgHeartRate?.let { stringResource(Res.string.unit_bpm, it) } ?: stringResource(Res.string.value_missing),
                    ),
                )
            )
        }
        preview.polyline?.let { ActivityMap(it) }
        ActivityTypePicker(
            state.type,
            state.quickTypes,
            { state.eventSink(ImportActivityEvent.TypeChanged(it)) },
            enabled = !state.saving,
        )
        AppTextField(
            state.title,
            label = stringResource(Res.string.field_title),
            modifier = Modifier.fillMaxWidth(),
        )
        AppButton(onClick = { state.eventSink(ImportActivityEvent.Save) }, enabled = !state.saving && !state.reading) {
            Text(stringResource(if (state.saving) Res.string.action_saving else Res.string.action_save))
        }
    }
}

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
        val gases = dive.gases.map { gasName(it) }
        Text((listOf(circuit) + gases.joinToString(" → ").ifEmpty { null }).filterNotNull().joinToString(" · "))
        val counts =
            listOfNotNull(
                dive.diveNumber?.let { stringResource(Res.string.dive_number, it) },
                dive.bottomTimeSeconds?.let { stringResource(Res.string.dive_bottom_time, Format.duration(it)) },
                dive.surfaceIntervalMinutes?.let { stringResource(Res.string.dive_surface_interval, surfaceInterval(it)) },
            )
        if (counts.isNotEmpty()) Text(counts.joinToString(" · "))
        val deco =
            listOfNotNull(
                if (dive.gradientFactorLow != null && dive.gradientFactorHigh != null) {
                    stringResource(Res.string.dive_gradient_factors, dive.gradientFactorLow!!, dive.gradientFactorHigh!!)
                } else null,
                if (dive.startCns != null && dive.endCns != null) stringResource(Res.string.dive_cns, dive.startCns!!, dive.endCns!!)
                else null,
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

@Composable
private fun gasName(gas: DiveGasPreview): String =
    when {
        gas.heliumPercent > 0 -> stringResource(Res.string.dive_gas_trimix, gas.oxygenPercent, gas.heliumPercent)
        gas.oxygenPercent == AIR_O2 -> stringResource(Res.string.dive_gas_air)
        gas.oxygenPercent >= PURE_O2 -> stringResource(Res.string.dive_gas_oxygen)
        else -> stringResource(Res.string.dive_gas_nitrox, gas.oxygenPercent)
    }

/**
 * "5 h 55 min" within a day, as a clock time reads badly; "11 d 22 h" beyond, where minutes are noise. FIT logs count the interval since
 * the last dive however long ago it was.
 */
@Composable
private fun surfaceInterval(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    return if (hours < HOURS_PER_DAY) stringResource(Res.string.unit_hours_minutes, hours, minutes % MINUTES_PER_HOUR)
    else stringResource(Res.string.unit_days_hours, hours / HOURS_PER_DAY, hours % HOURS_PER_DAY)
}

private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24

/** About 10 m: enough to find the site, no more. */
private const val COORDINATE_DECIMALS = 4

/** The formatter takes tenths of a degree Celsius, like records. */
private const val TENTHS_PER_DEGREE = 10
private const val AIR_O2 = 21
/** Mixes this rich are oxygen; Garmin logs it as 99%. */
private const val PURE_O2 = 99
