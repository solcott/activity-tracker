package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.media.mediaItems
import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.app.report.ReportingEvent
import io.github.solcott.activitytracker.app.report.ReportingHost
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.ActivityTypeIcon
import io.github.solcott.activitytracker.app.ui.CategoryLock
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.DiveSite
import io.github.solcott.activitytracker.app.ui.HiddenCard
import io.github.solcott.activitytracker.app.ui.MediaRow
import io.github.solcott.activitytracker.app.ui.ModerationNotice
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.VisibilityBadge
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.AppVerticalScrollbar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.WindowWidth
import io.github.solcott.activitytracker.app.ui.adaptive.WithWindowWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.app.ui.rememberFileHandOff
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_hide_likes
import io.github.solcott.activitytracker.composeApp.resources.detail_like
import io.github.solcott.activitytracker.composeApp.resources.detail_liked
import io.github.solcott.activitytracker.composeApp.resources.detail_more
import io.github.solcott.activitytracker.composeApp.resources.detail_no_likes
import io.github.solcott.activitytracker.composeApp.resources.detail_segment_created
import io.github.solcott.activitytracker.composeApp.resources.detail_show_likes
import io.github.solcott.activitytracker.composeApp.resources.detail_title
import io.github.solcott.activitytracker.composeApp.resources.detail_zone_added
import io.github.solcott.activitytracker.composeApp.resources.like_count
import io.github.solcott.activitytracker.composeApp.resources.report_activity
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.activityType
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.persistentListOf
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
                actions = {
                    val loaded = state.content.data
                    loaded?.manage?.let {
                        ManageMenu(it, loaded.canHideRouteEnds, loaded.export, loaded.eventSink, canCreateSegment = loaded.canCreateSegment)
                    }
                    if (loaded != null && !loaded.isOwn && loaded.reporting.available) OthersActivityMenu(loaded)
                },
            )
        },
    ) { padding ->
        val retry = { state.eventSink(ActivityDetailEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadActivity, retry, contentPadding = padding) { page ->
            // Built whenever the activity has loaded.
            page?.let { LoadedContent(it, state.content, padding) }
        }
    }
}

/** The "…" menu on someone else's activity, beside where the owner's [ManageMenu] sits: Report. */
@Composable
private fun OthersActivityMenu(state: ActivityDetailPage) {
    var open by remember { mutableStateOf(false) }
    val report = stringResource(Res.string.report_activity)
    val activity = state.activity
    val name = activity.author.displayName ?: handleLabel(activity.author.handle.value)
    val items =
        remember(report, activity.uri, activity.cid, name) {
            persistentListOf(
                AppMenuItem(report) {
                    val target = ReportTarget.Activity(activity.uri, activity.cid, activity.author.did)
                    state.reporting.eventSink(ReportingEvent.Open(target, name))
                }
            )
        }
    Box {
        AppIconButton(AppIcon.More, stringResource(Res.string.detail_more), onClick = { open = true })
        AppMenu(expanded = open, onDismiss = { open = false }, items = items)
    }
}

@Composable
private fun LoadedContent(
    state: ActivityDetailPage,
    content: ContentState<*>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    ReportingHost(state.reporting)
    val handOff = rememberFileHandOff()
    val exported = state.export?.file
    SideEffect(exported) {
        if (exported != null) {
            handOff(exported)
            state.eventSink(ActivityDetailEvent.ExportHandedOff)
        }
    }
    val activity = state.activity
    val decision = remember(activity, state.isOwn) { ModerationDecision.of(activity.labels, activity.author.labels, state.isOwn) }
    val hiddenKey = Revealed.hiddenKey(activity.uri.value)
    if (decision == ModerationDecision.Hidden && hiddenKey !in state.revealed) {
        // Opened by URI: lists never show it, so the page waits behind a warning.
        HiddenCard(onShow = { state.revealed.eventSink(hiddenKey) }, modifier.padding(contentPadding).padding(16.dp))
        return
    }
    WithWindowWidth(modifier.fillMaxSize()) { width ->
        if (width == WindowWidth.Expanded) PanedPage(state, content, contentPadding) else ColumnPage(state, content, contentPadding, width)
    }
    state.combineSuggestion?.takeIf { it.asPrompt }?.let { CombineSuggestionPrompt(it, state.eventSink) }
}

/**
 * Under 840dp: one list. Compact windows stack the hero, map, training load, cards, stats and comments; medium ones put the hero and the
 * map side by side, add the avg/max table, and set the cards and stats two to a row.
 */
@Composable
private fun ColumnPage(state: ActivityDetailPage, content: ContentState<*>, contentPadding: PaddingValues, width: WindowWidth) {
    val page = state.page
    val medium = width == WindowWidth.Medium
    val listState = rememberLazyListState()
    ReadableList(listState, Modifier.fillMaxSize(), contentPadding) { listPadding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = listPadding + PaddingValues(vertical = CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            // Over the loaded page: a spinner while it reloads, or why the last reload failed with Retry.
            item(key = "refresh") {
                RefreshStatus(content, UiError.Kind.LoadActivity, onRetry = { state.eventSink(ActivityDetailEvent.Retry) })
            }
            item(key = "header") { Header(state) }
            if (medium) {
                item(key = "summary") {
                    Row(horizontalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
                        HeroRow(page.hero, Modifier.weight(1f))
                        PageMedia(state, Modifier.weight(1f), height = 200.dp)
                    }
                }
                item(key = "avgMax") { AvgMaxTable(page.avgMax) }
            } else {
                item(key = "hero") { HeroRow(page.hero) }
                item(key = "media") { PageMedia(state) }
            }
            item(key = "likes") { Likes(state) }
            item(key = "trainingLoad") { TrainingLoadCard(page.trainingLoad) }
            metricCards(state, columns = if (medium) 2 else 1)
            segments(state)
            item(key = "stats") {
                StatsList(page.stats, columns = if (medium) 2 else 1, onOpenGear = { state.eventSink(ActivityDetailEvent.OpenGear(it)) })
            }
            comments(state)
        }
    }
    LoadMoreNearTheEnd(listState, state)
}

/**
 * From 840dp: a side pane with the map, hero, avg/max table, training load, stats and comments, scrolling on its own so the map stays in
 * view, beside the cards, two to a row once their pane has room.
 */
@Composable
private fun PanedPage(state: ActivityDetailPage, content: ContentState<*>, contentPadding: PaddingValues) {
    val page = state.page
    val paneState = rememberLazyListState()
    val cardsState = rememberLazyListState()
    val padding = contentPadding + PaddingValues(CARD_PADDING)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
        Box(Modifier.width(SIDE_PANE_WIDTH).fillMaxHeight()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = paneState,
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
            ) {
                // Over the loaded page: a spinner while it reloads, or why the last reload failed with Retry.
                item(key = "refresh") {
                    RefreshStatus(content, UiError.Kind.LoadActivity, onRetry = { state.eventSink(ActivityDetailEvent.Retry) })
                }
                item(key = "media") { PageMedia(state) }
                item(key = "header") { Header(state) }
                item(key = "hero") { HeroRow(page.hero) }
                item(key = "likes") { Likes(state) }
                item(key = "avgMax") { AvgMaxTable(page.avgMax) }
                item(key = "trainingLoad") { TrainingLoadCard(page.trainingLoad) }
                item(key = "stats") { StatsList(page.stats, onOpenGear = { state.eventSink(ActivityDetailEvent.OpenGear(it)) }) }
                comments(state)
            }
            AppVerticalScrollbar(paneState, Modifier.align(Alignment.CenterEnd).padding(contentPadding).fillMaxHeight())
        }
        BoxWithConstraints(Modifier.weight(1f, fill = false).widthIn(max = CARDS_MAX_WIDTH).fillMaxHeight()) {
            val columns = if (maxWidth >= TWO_CARD_COLUMNS) 2 else 1
            LazyColumn(
                Modifier.fillMaxSize(),
                state = cardsState,
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
            ) {
                metricCards(state, columns)
                segments(state)
            }
            AppVerticalScrollbar(cardsState, Modifier.align(Alignment.CenterEnd).padding(contentPadding).fillMaxHeight())
        }
    }
    LoadMoreNearTheEnd(paneState, state)
}

/** The metric cards, [columns] to a row; laps and the other tables take a row of their own. */
private fun LazyListScope.metricCards(state: ActivityDetailPage, columns: Int) {
    val charts = state.page.charts ?: return
    val rows = mutableListOf<List<MetricCard>>()
    for (card in state.page.cards) {
        val last = rows.lastOrNull()
        when {
            card.section in FULL_WIDTH || columns == 1 -> rows += listOf(card)
            last != null && last.size < columns && last.none { it.section in FULL_WIDTH } -> rows[rows.lastIndex] = last + card
            else -> rows += listOf(card)
        }
    }
    val onSetThresholds = { state.eventSink(ActivityDetailEvent.OpenTrainingZones) }
    items(rows, key = { row -> "cards/" + row.joinToString("+") { it.section.name } }) { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
            row.forEach { card ->
                val modifier = if (card.section in FULL_WIDTH) Modifier.fillMaxWidth() else Modifier.weight(1f)
                val sources = state.chartSources.firstOrNull { it.section == card.section }?.members
                MetricCardUi(card, charts, modifier, state.chartsMissingZones, onSetThresholds, sources)
            }
            if (row.none { it.section in FULL_WIDTH }) repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private val FULL_WIDTH = setOf(DetailSection.Laps, DetailSection.Legs, DetailSection.Lengths, DetailSection.Sets)
private val SIDE_PANE_WIDTH = 400.dp
private val CARDS_MAX_WIDTH = 1040.dp
private val TWO_CARD_COLUMNS = 640.dp

/**
 * Who, when and what: the author, the start, the title and type, the dive site and description, the other recordings, and what an action
 * reported.
 */
@Composable
private fun Header(state: ActivityDetailPage, modifier: Modifier = Modifier) {
    val record = state.activity.record
    val activity = state.activity
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ModerationNotice(
            remember(activity, state.isOwn) { ModerationDecision.of(activity.labels, activity.author.labels, state.isOwn) },
            state.isOwn,
            warn = ModerationDecision.warnOf(activity.labels, activity.author.labels),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.activity.author.displayName ?: state.activity.author.handle.value,
                Modifier.clickable(role = Role.Button) { state.eventSink(ActivityDetailEvent.OpenAuthor) },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            VisibilityBadge(state.activity.visibilityOrPublic)
        }
        StartLine(state.page.start)
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
        record.dive?.site?.let { DiveSite(it, Modifier.padding(top = 2.dp)) }
        record.description?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium) }
        Recordings(state.activity, onOpen = { state.eventSink(ActivityDetailEvent.OpenActivity(it)) })
        // The owner edits the combination in a sheet, which closes once a split leaves nothing combined.
        val combined = !state.activity.members.isNullOrEmpty()
        var editing by rememberSaveable(combined) { mutableStateOf(false) }
        Members(
            state.activity,
            onOpen = { state.eventSink(ActivityDetailEvent.OpenActivity(it)) },
            onEdit = if (state.manage != null) ({ editing = true }) else null,
        )
        if (editing && combined) {
            EditCombinationSheet(state.activity, state.dataSources, state.eventSink, onDismiss = { editing = false })
        }
        state.combineSuggestion?.takeIf { !it.asPrompt }?.let { CombineSuggestionBanner(it, state.eventSink) }
        state.manage?.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        state.routeError?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        if (state.zoneAdded) Text(stringResource(Res.string.detail_zone_added), style = MaterialTheme.typography.bodySmall)
        if (state.segmentCreated) Text(stringResource(Res.string.detail_segment_created), style = MaterialTheme.typography.bodySmall)
    }
}

/** The map and photos, the map marked with the Map category's visibility on the owner's page when it's narrower than the activity. */
@Composable
private fun PageMedia(state: ActivityDetailPage, modifier: Modifier = Modifier, height: Dp = 240.dp) {
    val activity = state.activity
    val media = remember(activity) { activity.mediaItems() }
    val decision = remember(activity, state.isOwn) { ModerationDecision.of(activity.labels, activity.author.labels, state.isOwn) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MediaRow(
            media,
            onOpen = { state.eventSink(ActivityDetailEvent.OpenMedia(it)) },
            height = height,
            // The owner sees their whole route, marked where it starts and ends outside their privacy zones; everyone else the polyline
            // trimmed by them, marked at its own ends.
            map =
                state.route?.let { route ->
                    { mapModifier, mapHeight ->
                        ActivityMap(
                            route.polyline,
                            mapModifier,
                            mapHeight,
                            route.gaps,
                            route.ends,
                            onExpand = { state.eventSink(ActivityDetailEvent.OpenMap) },
                        )
                    }
                },
            blur = remember(activity) { ModerationDecision.mediaOf(activity.labels, activity.author.labels) },
            revealed = activity.uri.value in state.revealed,
            onReveal = { state.revealed.eventSink(activity.uri.value) },
            takenDown = decision == ModerationDecision.Takedown,
        )
        state.page.mapMark?.let { CategoryLock(DetailCategory.MAP, it) }
    }
}

/** The like count or button, the toggle for who liked it, why a like couldn't be given, and the list once shown. */
@Composable
private fun Likes(state: ActivityDetailPage, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LikeButton(state, Modifier.weight(1f))
            // Hiding stays available if the last like goes away while the list is open.
            if (state.activity.likeCount > 0 || state.likesList != null) {
                AppTextButton(onClick = { state.eventSink(ActivityDetailEvent.ToggleLikesList) }) {
                    Text(stringResource(if (state.likesList != null) Res.string.detail_hide_likes else Res.string.detail_show_likes))
                }
            }
        }
        state.likeError?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        state.likesList?.let { LikesList(it, state.eventSink) }
    }
}

@Composable
private fun LikeButton(state: ActivityDetailPage, modifier: Modifier = Modifier) {
    val count = state.activity.likeCount
    if (state.isOwn) {
        Text(pluralStringResource(Res.plurals.like_count, count.toInt(), formatWhole(count)), modifier.padding(vertical = 12.dp))
    } else {
        Box(modifier) {
            AppTextButton(onClick = { state.eventSink(ActivityDetailEvent.ToggleLike) }) {
                Text(
                    stringResource(
                        if (state.activity.viewer?.like != null) Res.string.detail_liked else Res.string.detail_like,
                        count,
                    )
                )
            }
        }
    }
}

/** Who liked it, under the like row: a spinner, an error, the empty state, or one tappable row per athlete. */
@Composable
private fun LikesList(list: LikesList, eventSink: (ActivityDetailEvent) -> Unit, modifier: Modifier = Modifier) {
    val error = list.error
    Column(modifier.fillMaxWidth()) {
        when {
            list.isLoading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { AppProgressIndicator() }
            error != null -> Text(errorText(error), color = MaterialTheme.colorScheme.error)
            list.athletes.isEmpty() -> Text(stringResource(Res.string.detail_no_likes), style = MaterialTheme.typography.bodyMedium)
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
