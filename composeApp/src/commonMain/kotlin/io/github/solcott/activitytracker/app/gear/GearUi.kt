package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.activityItems
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.pagedFooter
import io.github.solcott.activitytracker.app.ui.visibilityLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.gear_linked_profiles
import io.github.solcott.activitytracker.composeApp.resources.gear_linked_sensors
import io.github.solcott.activitytracker.composeApp.resources.gear_not_found
import io.github.solcott.activitytracker.composeApp.resources.gear_page_activities
import io.github.solcott.activitytracker.composeApp.resources.gear_page_activity_count
import io.github.solcott.activitytracker.composeApp.resources.gear_page_bottom_time
import io.github.solcott.activitytracker.composeApp.resources.gear_page_by_sport
import io.github.solcott.activitytracker.composeApp.resources.gear_page_by_year
import io.github.solcott.activitytracker.composeApp.resources.gear_page_days
import io.github.solcott.activitytracker.composeApp.resources.gear_page_distance
import io.github.solcott.activitytracker.composeApp.resources.gear_page_dives
import io.github.solcott.activitytracker.composeApp.resources.gear_page_edit
import io.github.solcott.activitytracker.composeApp.resources.gear_page_elevation_gain
import io.github.solcott.activitytracker.composeApp.resources.gear_page_elevation_loss
import io.github.solcott.activitytracker.composeApp.resources.gear_page_moving_time
import io.github.solcott.activitytracker.composeApp.resources.gear_page_no_activities
import io.github.solcott.activitytracker.composeApp.resources.gear_page_starting
import io.github.solcott.activitytracker.composeApp.resources.gear_page_totals
import io.github.solcott.activitytracker.composeApp.resources.gear_retired
import io.github.solcott.activitytracker.composeApp.resources.gear_total_activities
import io.github.solcott.activitytracker.composeApp.resources.gear_total_joined
import io.github.solcott.activitytracker.composeApp.resources.gear_unlink
import io.github.solcott.activitytracker.composeApp.resources.gear_unlink_named
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearMainTotal
import io.github.solcott.activitytracker.lexicon.app.GearStarting
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A gear item's page (gear/gear-usage): its photo and details, its totals with any starting usage, the totals by activity type and by year,
 * then the activities counted, newest first. Components mounted on it, and what it was mounted on, come before the totals; the athlete's
 * own item offers Edit and managing its components.
 */
@CircuitInject(GearScreen::class, SessionScope::class)
@Composable
fun GearUi(state: GearState, modifier: Modifier = Modifier) {
    val page = state.page
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = page?.gear?.name.orEmpty(),
                onBack = { state.eventSink(GearEvent.Back) },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (page?.isOwner == true) {
                        AppTextButton(onClick = { state.eventSink(GearEvent.Edit) }) { Text(stringResource(Res.string.gear_page_edit)) }
                    }
                },
            )
        },
    ) { padding ->
        if (state.notFound) {
            Text(
                stringResource(Res.string.gear_not_found),
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppScaffold
        }
        val retry = { state.eventSink(GearEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadGearItem, onRetry = retry, contentPadding = padding) { loaded ->
            // Loaded content is never null; the placeholder before the first load is.
            if (loaded != null) Loaded(state, loaded, padding, retry)
        }
        MountDialogs(state)
    }
}

@Composable
private fun Loaded(state: GearState, page: GearPage, padding: PaddingValues, onRetry: () -> Unit) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.activities.canLoadMore) { state.eventSink(GearEvent.LoadMore) }
    ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 12.dp)) {
            item(key = "refresh") { RefreshStatus(state.content, UiError.Kind.LoadGearItem, onRetry) }
            item(key = "header") { Header(page) }
            if (page.isOwner && state.collections.isNotEmpty()) {
                item(key = "collections") {
                    GearCollectionsField(
                        state.collections,
                        enabled = true,
                        onToggle = { state.eventSink(GearEvent.ToggleCollection(it)) },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            componentItems(state, page) { Section(it) }
            if (state.linkedSensors.isNotEmpty()) {
                item(key = "linked-sensors") { Section(stringResource(Res.string.gear_linked_sensors)) }
                items(state.linkedSensors, key = { "sensor/${it.id}" }) { sensor ->
                    LinkedSensorRow(
                        sensor,
                        stringResource(Res.string.gear_unlink),
                        stringResource(Res.string.gear_unlink_named, deviceName(sensor.manufacturer, sensor.model)),
                        { state.eventSink(GearEvent.UnlinkSensor(sensor.id)) },
                    )
                }
            }
            if (state.linkedProfiles.isNotEmpty()) {
                item(key = "linked-profiles") { Section(stringResource(Res.string.gear_linked_profiles)) }
                items(state.linkedProfiles, key = { "profile/$it" }) { name ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        AppTextButton(onClick = { state.eventSink(GearEvent.UnlinkProfile(name)) }) {
                            Text(stringResource(Res.string.gear_unlink))
                        }
                    }
                }
            }
            item(key = "totals") {
                Section(stringResource(Res.string.gear_page_totals))
                AppCard(Modifier.fillMaxWidth()) { Totals(page.usage.totals, page.gear.type, page.usage.starting) }
            }
            val bySport = page.usage.bySport
            if (bySport.size > 1) {
                breakdown(
                    "sport",
                    Res.string.gear_page_by_sport,
                    page.gear.type,
                    bySport.map { "${it.sport}/${it.subSport}" to it.totals },
                ) {
                    activityTypeLabel(ActivityType(bySport[it].sport, bySport[it].subSport))
                }
            }
            val byYear = page.usage.byYear
            if (byYear.size > 1) {
                breakdown("year", Res.string.gear_page_by_year, page.gear.type, byYear.map { "${it.year}" to it.totals }) {
                    byYear[it].year.toString()
                }
            }
            item(key = "activities-header") { Section(stringResource(Res.string.gear_page_activities)) }
            state.error
                ?.takeIf { it.kind !in MOUNT_ERRORS }
                ?.let { error ->
                    item(key = "action-error") {
                        Text(errorText(error), Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
            activityItems(
                state.activities.items.toImmutableList(),
                state.viewerDid,
                authorClickable = !page.isOwner,
                state.revealed,
                gap = ACTIVITY_GAP,
            ) {
                state.eventSink(GearEvent.Activity(it))
            }
            pagedFooter(state.activities, UiError.Kind.LoadGearActivities, onRetry = onRetry) {
                Text(
                    stringResource(Res.string.gear_page_no_activities),
                    Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Header(page: GearPage) {
    val gear = page.gear
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            gear.photo?.let { AsyncImage(it, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f)) {
            Text(gear.name, style = MaterialTheme.typography.titleLarge)
            Text(
                gear.kind?.let { gearKindLabel(GearType.known(gear.type), it) } ?: gearTypeLabel(gear.type),
                style = MaterialTheme.typography.bodyMedium,
            )
            listOfNotNull(gear.brand, gear.model)
                .joinToString(" ")
                .takeIf { it.isNotEmpty() }
                ?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val notes =
                listOfNotNull(page.visibility?.let { visibilityLabel(it) }, stringResource(Res.string.gear_retired).takeIf { page.retired })
            if (notes.isNotEmpty()) {
                Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
}

/** Every total that applies to [type]'s gear, and the starting usage they include. */
@Composable
private fun Totals(totals: GearTotals, type: String, starting: GearStarting?) {
    val main = GearType.mainTotal(type)
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Figure(stringResource(Res.string.gear_page_activity_count), formatWhole(totals.activities))
        Figure(stringResource(Res.string.gear_page_moving_time), gearHoursLabel(totals.movingTime))
        if (totals.distance > 0 || main == GearMainTotal.DISTANCE) {
            Figure(stringResource(Res.string.gear_page_distance), gearDistanceLabel(totals.distance))
        }
        if (totals.elevationGain > 0)
            Figure(stringResource(Res.string.gear_page_elevation_gain), Format.elevation(totals.elevationGain.toDouble()))
        if (totals.elevationLoss > 0 || main == GearMainTotal.DAYS_AND_VERTICAL) {
            Figure(stringResource(Res.string.gear_page_elevation_loss), Format.elevation(totals.elevationLoss.toDouble()))
        }
        Figure(stringResource(Res.string.gear_page_days), formatWhole(totals.days))
        totals.dives?.let { Figure(stringResource(Res.string.gear_page_dives), formatWhole(it)) }
        totals.bottomTime?.let { Figure(stringResource(Res.string.gear_page_bottom_time), Format.duration(it)) }
        startingLabel(starting)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Includes 2,000 km · 100 h before tracking"; null without starting usage. */
@Composable
private fun startingLabel(starting: GearStarting?): String? {
    val parts =
        listOfNotNull(
            starting?.distance?.takeIf { it > 0 }?.let { gearDistanceLabel(it) },
            starting?.time?.takeIf { it > 0 }?.let { gearHoursLabel(it) },
        )
    if (parts.isEmpty()) return null
    return stringResource(Res.string.gear_page_starting, parts.joinToString(" · "))
}

@Composable
private fun Figure(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A breakdown's [rows] (each a key and its totals), the [index]th labeled by [label], with its activities and [type]'s main total. */
private fun LazyListScope.breakdown(
    key: String,
    title: StringResource,
    type: String,
    rows: List<Pair<String, GearTotals>>,
    label: @Composable (index: Int) -> String,
) {
    item(key = "$key-header") { Section(stringResource(title)) }
    rows.forEachIndexed { index, (row, totals) ->
        item(key = "$key/$row") {
            val count = pluralStringResource(Res.plurals.gear_total_activities, totals.activities.toInt(), formatWhole(totals.activities))
            Figure(label(index), stringResource(Res.string.gear_total_joined, count, gearMainTotalLabel(GearType.mainTotal(type), totals)))
        }
    }
}

/** Between activity cards, as the feed spaces them. */
private val ACTIVITY_GAP = 12.dp
