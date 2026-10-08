package io.github.solcott.activitytracker.app.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.ActivityTypeIcon
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.Stat
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.activitytracker.composeApp.resources.charts_visibility_heading
import io.github.solcott.activitytracker.composeApp.resources.detail_comment_count
import io.github.solcott.activitytracker.composeApp.resources.detail_comment_placeholder
import io.github.solcott.activitytracker.composeApp.resources.detail_give_kudos
import io.github.solcott.activitytracker.composeApp.resources.detail_hide_kudos
import io.github.solcott.activitytracker.composeApp.resources.detail_kudos_given
import io.github.solcott.activitytracker.composeApp.resources.detail_no_kudos
import io.github.solcott.activitytracker.composeApp.resources.detail_section_comments
import io.github.solcott.activitytracker.composeApp.resources.detail_send_comment
import io.github.solcott.activitytracker.composeApp.resources.detail_show_kudos
import io.github.solcott.activitytracker.composeApp.resources.detail_title
import io.github.solcott.activitytracker.composeApp.resources.kudos_count
import io.github.solcott.activitytracker.composeApp.resources.stat_distance
import io.github.solcott.activitytracker.composeApp.resources.stat_elevation
import io.github.solcott.activitytracker.composeApp.resources.stat_moving_time
import io.github.solcott.activitytracker.composeApp.resources.stat_temperature
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.activityType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@CircuitInject(ActivityDetailScreen::class, SessionScope::class)
@Composable
fun ActivityDetailUi(state: ActivityDetailState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.detail_title),
                onBack = { state.eventSink(ActivityDetailEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when (state) {
            is ActivityDetailState.Loading -> {
                Column(
                    Modifier.padding(padding).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppProgressIndicator(Modifier.padding(32.dp))
                }
            }

            is ActivityDetailState.Error -> {
                Column(Modifier.padding(padding).padding(16.dp)) {
                    Text(errorText(state.error), color = MaterialTheme.colorScheme.error)
                    AppTextButton(onClick = { state.eventSink(ActivityDetailEvent.Retry) }) {
                        Text(stringResource(Res.string.action_retry))
                    }
                }
            }

            is ActivityDetailState.Loaded -> {
                LoadedContent(state, padding)
            }
        }
    }
}

@Composable
private fun LoadedContent(state: ActivityDetailState.Loaded, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    ReadableList(listState, modifier.fillMaxSize(), contentPadding) { listPadding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = listPadding + PaddingValues(vertical = CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            item(key = "overview") { Overview(state, Modifier.animateContentSize()) }
            if (state.sections.isNotEmpty()) {
                item(key = "sections") { SectionsHeader(state) }
                items(state.sections, key = { "section/${it.name}" }) { section ->
                    SectionCard(section, state, Modifier.animateContentSize())
                }
            }
        }
    }
}

/** What always shows: who and what, the headline figures, the map, and kudos. */
@Composable
private fun Overview(state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    val record = state.activity.record
    DetailCard(modifier) {
        Column(Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(CARD_PADDING)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    state.activity.author.displayName ?: state.activity.author.handle.value,
                    Modifier.clickable(role = Role.Button) { state.eventSink(ActivityDetailEvent.OpenAuthor) },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(record.title ?: activityTypeLabel(record.activityType), style = MaterialTheme.typography.headlineSmall)
                if (record.title != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ActivityTypeIcon(record.activityType, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            activityTypeLabel(record.activityType),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                record.description?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            StatTiles(overviewStats(record))
            record.polyline?.let { ActivityMap(it, Modifier.clip(RoundedCornerShape(12.dp))) }
            Kudos(state)
            if (state.isOwn) state.charts?.let { ChartSharing(it.visibility, state) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Comments(state)
        }
    }
}

/** The owner's choice of who may see the activity's charts. */
@Composable
private fun ChartSharing(visibility: String, state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.charts_visibility_heading), style = MaterialTheme.typography.titleSmall)
        ChartsVisibility(visibility, state.savingChartsVisibility, state.eventSink)
    }
}

/** The comment count, every comment oldest first, then a field to add one. */
@Composable
private fun Comments(state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.detail_section_comments), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(
                pluralStringResource(Res.plurals.detail_comment_count, state.comments.size, state.comments.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.comments.forEachIndexed { i, comment ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(comment.author.displayName ?: comment.author.handle.value, style = MaterialTheme.typography.labelLarge)
                Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AppTextField(
                state = state.draftComment,
                placeholder = stringResource(Res.string.detail_comment_placeholder),
                modifier = Modifier.weight(1f),
            )
            AppIconButton(
                AppIcon.Send,
                stringResource(Res.string.detail_send_comment),
                { state.eventSink(ActivityDetailEvent.SendComment) },
            )
        }
    }
}

@Composable
private fun overviewStats(record: ActivityRecord): ImmutableList<Stat> {
    val time = record.movingTime ?: record.elapsedTime
    return listOfNotNull(
            Stat(stringResource(Res.string.stat_distance), Format.distance(record.distance?.toDouble())),
            Stat(stringResource(Res.string.stat_moving_time), Format.duration(time)),
            Stat(stringResource(Res.string.stat_elevation), Format.elevation(record.elevationGain?.toDouble())),
            Format.rateStat(record.sport, record.distance?.toDouble(), time),
            record.avgTemperature?.let { Stat(stringResource(Res.string.stat_temperature), Format.temperature(it)) },
        )
        .toImmutableList()
}

/** The kudos count or button, the toggle for who gave them, and the list once shown. */
@Composable
private fun Kudos(state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KudosButton(state, Modifier.weight(1f))
            // Hiding stays available if the last kudos goes away while the list is open.
            if (state.activity.kudosCount > 0 || state.kudosList != null) {
                AppTextButton(onClick = { state.eventSink(ActivityDetailEvent.ToggleKudosList) }) {
                    Text(stringResource(if (state.kudosList != null) Res.string.detail_hide_kudos else Res.string.detail_show_kudos))
                }
            }
        }
        state.kudosList?.let { KudosList(it, state.eventSink) }
    }
}

@Composable
private fun KudosButton(state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    val count = state.activity.kudosCount
    if (state.isOwn) {
        Text(pluralStringResource(Res.plurals.kudos_count, count.toInt(), count), modifier.padding(vertical = 12.dp))
    } else {
        Box(modifier) {
            AppTextButton(onClick = { state.eventSink(ActivityDetailEvent.ToggleKudos) }) {
                Text(
                    stringResource(
                        if (state.activity.viewer?.kudos != null) Res.string.detail_kudos_given else Res.string.detail_give_kudos,
                        count,
                    )
                )
            }
        }
    }
}

/** Who gave kudos, under the kudos row: a spinner, an error, the empty state, or one tappable row per athlete. */
@Composable
private fun KudosList(list: KudosList, eventSink: (ActivityDetailEvent) -> Unit, modifier: Modifier = Modifier) {
    val error = list.error
    Column(modifier.fillMaxWidth()) {
        when {
            list.isLoading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { AppProgressIndicator() }
            error != null -> Text(errorText(error), color = MaterialTheme.colorScheme.error)
            list.athletes.isEmpty() -> Text(stringResource(Res.string.detail_no_kudos), style = MaterialTheme.typography.bodyMedium)
            else -> {
                list.athletes.forEach { athlete ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { eventSink(ActivityDetailEvent.OpenProfile(athlete.did)) }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(athlete.displayName ?: athlete.handle.value, style = MaterialTheme.typography.titleSmall)
                        Text(handleLabel(athlete.handle.value), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private const val DIVIDER_ALPHA = 0.5f
