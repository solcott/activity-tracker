package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.interactions.CommentCount
import io.github.solcott.activitytracker.app.interactions.CommentsPreviewBlock
import io.github.solcott.activitytracker.app.interactions.LikersList
import io.github.solcott.activitytracker.app.interactions.SocialCount
import io.github.solcott.activitytracker.app.interactions.SocialCountsRow
import io.github.solcott.activitytracker.app.media.MediaMosaic
import io.github.solcott.activitytracker.app.media.mediaItems
import io.github.solcott.activitytracker.app.report.ReportingHost
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.ActivityTypeIcon
import io.github.solcott.activitytracker.app.ui.CategoryLock
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.HiddenCard
import io.github.solcott.activitytracker.app.ui.MOSAIC_HEIGHT
import io.github.solcott.activitytracker.app.ui.MediaMosaicUi
import io.github.solcott.activitytracker.app.ui.ModerationNotice
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.VisibilityBadge
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.AppVerticalScrollbar
import io.github.solcott.activitytracker.app.ui.adaptive.HighlightPlacement
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.WindowWidth
import io.github.solcott.activitytracker.app.ui.adaptive.WithWindowWidth
import io.github.solcott.activitytracker.app.ui.adaptive.highlight
import io.github.solcott.activitytracker.app.ui.adaptive.highlightInset
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.rememberFileHandOff
import io.github.solcott.activitytracker.app.ui.shownNameLabel
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.activityType
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@CircuitInject(ActivityDetailScreen::class, SessionScope::class)
@Composable
fun ActivityDetailUi(state: ActivityDetailState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // The bar names the activity and its sport once it has loaded, so it stays named as the page scrolls.
            val shown = state.content.data?.activity
            AppTopBar(
                title = (shown?.let(ActivityPageTexts::heading) ?: ActivityPageTexts.title).string(),
                subtitle = shown?.let(ActivityPageTexts::typeLine)?.string(),
                onBack = { state.eventSink(ActivityDetailEvent.Back) },
                scrollBehavior = scrollBehavior,
                actions = {
                    val loaded = state.content.data
                    loaded?.let { ManageMenu(it) }
                    if (loaded != null && ActivityPageTexts.offersReport(loaded)) OthersActivityMenu(loaded)
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
    val report = ActivityPageTexts.report.string()
    val name = shownNameLabel(state.activity.author)
    val items =
        remember(report, state, name) {
            persistentListOf(AppMenuItem(report) { ActivityPageTexts.reportEvent(state, name)?.let(state.reporting.eventSink) })
        }
    Box {
        AppIconButton(AppIcon.More, ActivityPageTexts.more.string(), onClick = { open = true })
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
    if (ActivityPageTexts.waitsHidden(state)) {
        // Opened by URI: lists never show it, so the page waits behind a warning.
        HiddenCard(
            onShow = { state.revealed.eventSink(ActivityPageTexts.hiddenKey(state)) },
            modifier.padding(contentPadding).padding(16.dp),
        )
        return
    }
    WithWindowWidth(modifier.fillMaxSize()) { width ->
        if (width == WindowWidth.Expanded) PanedPage(state, content, contentPadding) else ColumnPage(state, content, contentPadding, width)
    }
    state.combineSuggestion?.takeIf { it.asPrompt }?.let { CombineSuggestionPrompt(it, state.eventSink) }
}

/**
 * Under 840dp: one list (feed/activity-page-layout). Compact windows stack the mosaic, header, hero, likes and comments card, training
 * load, the cards in the sport's order (the lead open, the rest as strips, tables five rows long), segments, lifts and the All data row;
 * medium ones put the mosaic and the hero side by side and set the strips two to a row. Once the mosaic scrolls away, chips for the page's
 * sections stick under the bar.
 */
@Composable
private fun ColumnPage(state: ActivityDetailPage, content: ContentState<*>, contentPadding: PaddingValues, width: WindowWidth) {
    val page = state.page
    val medium = width == WindowWidth.Medium
    val listState = rememberLazyListState()
    val rows = cardRows(state, if (medium) 2 else 1, inPane = false)
    val offersData = ActivityDataTexts.offered(state.data)
    // Every item's key, in the list's order, so a chip finds the item it scrolls to.
    val keys = pageKeys(state, rows, medium, offersData)
    val tops = remember { CardTops() }
    Box(Modifier.fillMaxSize()) {
        ReadableList(listState, Modifier.fillMaxSize(), contentPadding) { listPadding ->
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = listPadding + PaddingValues(vertical = CARD_PADDING),
                verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
            ) {
                // Over the loaded page, in the first item so it leaves no gap when idle: a spinner while it reloads, or why the last
                // reload failed with Retry.
                val refresh: @Composable () -> Unit = {
                    RefreshStatus(content, UiError.Kind.LoadActivity, onRetry = { state.eventSink(ActivityDetailEvent.Retry) })
                }
                if (medium) {
                    item(key = PageJump.OVERVIEW) {
                        Column {
                            refresh()
                            Row(horizontalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
                                PageMedia(state, Modifier.weight(1f), height = MEDIUM_MOSAIC_HEIGHT)
                                HeroCard(page.hero, Modifier.weight(1f))
                            }
                        }
                    }
                    item(key = "header") { Header(state) }
                } else {
                    item(key = PageJump.OVERVIEW) {
                        Column {
                            refresh()
                            PageMedia(state)
                        }
                    }
                    item(key = "header") { Header(state) }
                    item(key = "hero") { HeroCard(page.hero) }
                }
                item(key = "social") { Social(state) }
                if (state.showsGroup) item(key = "group") { TogetherWith(state) }
                item(key = "trainingLoad") { TrainingLoadCard(page.trainingLoad) }
                if (rows.isNotEmpty()) item(key = CARDS_KEY) { CardGrid(state, rows, tops) }
                segments(state)
                lifts(state)
                if (offersData) item(key = PageJump.ALL_DATA) { AllDataRow(state, inPane = false) }
            }
        }
        // A card's chip scrolls to its place in the cards' item.
        val positionOf = { jump: PageJump ->
            val card = jump.card
            if (card == null) keys.indexOf(jump.key).takeIf { it >= 0 }?.let { JumpPosition(it) }
            else tops.of(jump.key)?.let { JumpPosition(keys.indexOf(CARDS_KEY), it) }
        }
        StickyJumps(
            state,
            listState,
            keys.indexOf(PageJump.OVERVIEW),
            positionOf,
            Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding()),
        )
    }
}

/**
 * From 840dp: a side pane with the mosaic, header, hero, likes and comments card, Together with, training load and the All data row (or All
 * data itself, once opened there), scrolling on its own so it stays in view, beside the cards, their strips two to a row once their pane
 * has room.
 */
@Composable
private fun PanedPage(state: ActivityDetailPage, content: ContentState<*>, contentPadding: PaddingValues) {
    val page = state.page
    val paneState = rememberLazyListState()
    val cardsState = rememberLazyListState()
    val padding = contentPadding + PaddingValues(CARD_PADDING)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
        Box(Modifier.width(SIDE_PANE_WIDTH).fillMaxHeight()) {
            val allData = state.allData
            if (allData != null) {
                // Keyed on where it opened, so a second Show all starts there afresh.
                key(allData) {
                    val dataState = rememberLazyListState()
                    ActivityDataList(
                        state.data,
                        dataState,
                        padding,
                        onOpenGear = { state.eventSink(ActivityDetailEvent.OpenGear(it)) },
                        before = { PaneDataHeader(state) },
                    )
                    AppVerticalScrollbar(dataState, Modifier.align(Alignment.CenterEnd).padding(contentPadding).fillMaxHeight())
                }
            } else {
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
                    item(key = "hero") { HeroCard(page.hero) }
                    item(key = "social") { Social(state) }
                    if (state.showsGroup) item(key = "group") { TogetherWith(state) }
                    item(key = "trainingLoad") { TrainingLoadCard(page.trainingLoad) }
                    if (ActivityDataTexts.offered(state.data)) item(key = PageJump.ALL_DATA) { AllDataRow(state, inPane = true) }
                }
                AppVerticalScrollbar(paneState, Modifier.align(Alignment.CenterEnd).padding(contentPadding).fillMaxHeight())
            }
        }
        BoxWithConstraints(Modifier.weight(1f, fill = false).widthIn(max = CARDS_MAX_WIDTH).fillMaxHeight()) {
            val columns = if (maxWidth >= TWO_CARD_COLUMNS) 2 else 1
            val rows = cardRows(state, columns, inPane = true)
            val tops = remember { CardTops() }
            LazyColumn(
                Modifier.fillMaxSize(),
                state = cardsState,
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
            ) {
                // Always the first item, empty until the charts load: a list keeps its first item where it is when items arrive
                // above it, so the segments would otherwise stay at the top and push the cards above the fold.
                item(key = CARDS_KEY) { CardGrid(state, rows, tops) }
                segments(state)
                lifts(state)
            }
            AppVerticalScrollbar(cardsState, Modifier.align(Alignment.CenterEnd).padding(contentPadding).fillMaxHeight())
        }
    }
}

private val SIDE_PANE_WIDTH = 400.dp
private val CARDS_MAX_WIDTH = 1040.dp
private val TWO_CARD_COLUMNS = 640.dp
private val MEDIUM_MOSAIC_HEIGHT = 240.dp

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
                state.activity.author.shownName,
                Modifier.highlight(placement = HighlightPlacement.Bleed)
                    .clickable(role = Role.Button) { state.eventSink(ActivityDetailEvent.OpenAuthor) }
                    .highlightInset(compact = true),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            VisibilityBadge(state.activity.visibilityOrPublic)
        }
        StartLine(state.page.start)
        Text(ActivityPageTexts.heading(activity).string(), style = MaterialTheme.typography.headlineSmall)
        ActivityPageTexts.typeLine(activity)?.let { type ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ActivityTypeIcon(record.activityType, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    type.string(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        DiveSiteLine(state, Modifier.padding(top = 2.dp))
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
        state.manage?.error?.let { ErrorText(it) }
        state.routeError?.let { ErrorText(it) }
        ActivityPageTexts.notices(state).forEach { Text(it.string(), style = MaterialTheme.typography.bodySmall) }
    }
}

/**
 * The map and photos as the page's mosaic (feed/activity-page-layout), the map marked with the Map category's visibility on the owner's
 * page when it's narrower than the activity; nothing when the activity has neither.
 */
@Composable
private fun PageMedia(state: ActivityDetailPage, modifier: Modifier = Modifier, height: Dp = MOSAIC_HEIGHT) {
    val activity = state.activity
    val media = remember(activity) { activity.mediaItems() }
    val mosaic = remember(media, state.route) { MediaMosaic.of(state.route != null, media.size) } ?: return
    val decision = remember(activity, state.isOwn) { ModerationDecision.of(activity.labels, activity.author.labels, state.isOwn) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MediaMosaicUi(
            mosaic,
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

/** The hero figures in a hairline card; nothing when there are none. */
@Composable
private fun HeroCard(figures: ImmutableList<Figure>, modifier: Modifier = Modifier) {
    if (figures.isEmpty()) return
    AppCard(modifier) { HeroRow(figures, Modifier.padding(CARD_PADDING)) }
}

/**
 * Everything social about the activity in one card: its counts on one line (likes, with the like button on someone else's, and comments),
 * who liked it once shown and why a like couldn't be given, then, over a hairline, its comments in brief (feed/comments), which open the
 * Comments screen.
 */
@Composable
private fun Social(state: ActivityDetailPage, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            SocialCountsRow {
                LikeCount(state)
                CommentCount(state.comments)
            }
            val inset = Modifier.padding(horizontal = 12.dp)
            state.likeError?.let { ErrorText(it, inset) }
            state.blockError?.let { ErrorText(it, inset) }
            state.likesList?.let { list -> LikersList(list, { state.eventSink(ActivityDetailEvent.OpenProfile(it)) }, inset) }
            HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            CommentsPreviewBlock(state.comments, Modifier.padding(bottom = 8.dp))
        }
    }
}

/**
 * The like count, which shows or hides who liked it while anyone has (hiding stays offered if the last like goes while the list is open),
 * after the like button on someone else's activity.
 */
@Composable
private fun LikeCount(state: ActivityDetailPage) {
    val activity = state.activity
    val toggles = ActivityPageTexts.showsLikesToggle(state)
    SocialCount(
        AppIcon.Like,
        ActivityPageTexts.likeNumber(activity),
        ActivityPageTexts.likeCount(activity).string(),
        onClickLabel = ActivityPageTexts.likesToggle(shown = state.likesList != null).string(),
        onClick = if (toggles) ({ state.eventSink(ActivityDetailEvent.ToggleLikesList) }) else null,
        filled = activity.viewer?.like != null,
        iconLabel = ActivityPageTexts.like.string(),
        onIconClick = if (state.isOwn) null else ({ state.eventSink(ActivityDetailEvent.ToggleLike) }),
    )
}
