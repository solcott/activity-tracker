package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/** An activity's All data page on a window without a side pane: every figure, the avg/max table, its full tables and its sources. */
@CircuitInject(ActivityDataScreen::class, SessionScope::class)
@Composable
fun ActivityDataUi(state: ActivityDataState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = ActivityDataTexts.title.string(),
                onBack = { state.eventSink(ActivityDataEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val retry = { state.eventSink(ActivityDataEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadActivity, retry, contentPadding = padding) { data ->
            data?.let {
                val listState = rememberLazyListState()
                ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
                    ActivityDataList(
                        it,
                        listState,
                        listPadding,
                        onOpenGear = { uri -> state.eventSink(ActivityDataEvent.OpenGear(uri)) },
                        before = { RefreshStatus(state.content, UiError.Kind.LoadActivity, onRetry = retry) },
                    )
                }
            }
        }
    }
}

/**
 * All data as one list under its section chips, which stay above it: the stats list, the avg/max table, each table in full and the sources,
 * opening at [ActivityData.start]. The All data page and a wide activity page's side pane both draw it.
 */
@Composable
internal fun ActivityDataList(
    data: ActivityData,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onOpenGear: (String) -> Unit,
    modifier: Modifier = Modifier,
    before: (@Composable () -> Unit)? = null,
) {
    val sections = data.sections
    val scope = rememberCoroutineScope()
    // A table's section only shows once the streams load, after the activity, so this waits for the start section to be there, then
    // scrolls to it once.
    var startedAt by rememberSaveable { mutableStateOf(false) }
    val startIndex = sections.indexOfFirst { it.section == data.start }
    LaunchedEffect(startIndex) {
        if (startIndex >= 0 && !startedAt) {
            listState.scrollToItem(startIndex)
            startedAt = true
        }
    }
    val current by remember(sections) { derivedStateOf { sections.getOrNull(listState.firstVisibleItemIndex)?.section } }
    val top = contentPadding.calculateTopPadding()
    val sides =
        PaddingValues(
            start = contentPadding.calculateStartPadding(LayoutDirection.Ltr),
            end = contentPadding.calculateEndPadding(LayoutDirection.Ltr),
        )
    // The chips stay above the list rather than in it, so they show wherever it opened.
    Column(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(top = top).padding(sides).padding(vertical = 8.dp)) {
            before?.invoke()
            SectionChips(
                sections.map { it.label }.toImmutableList(),
                selected = sections.indexOfFirst { it.section == current },
                onSelect = { i -> scope.launch { listState.animateScrollToItem(i) } },
            )
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            state = listState,
            contentPadding = sides + PaddingValues(top = 4.dp, bottom = contentPadding.calculateBottomPadding() + CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            sections.forEach { option -> item(key = option.section.name) { DataSectionUi(data, option.section, onOpenGear) } }
        }
    }
}

@Composable
private fun DataSectionUi(data: ActivityData, section: DataSection, onOpenGear: (String) -> Unit) {
    when (section) {
        DataSection.Stats -> StatsList(data.stats, onOpenGear = onOpenGear)
        DataSection.AvgMax -> AvgMaxTable(data.avgMax)
        DataSection.Laps,
        DataSection.Legs,
        DataSection.Lengths,
        DataSection.Sets -> {
            val charts = data.charts ?: return
            val table = DetailSection.entries.first { DataSection.of(it) == section }
            Section(ActivityDataTexts.heading(section)) {
                AppCard { Column(Modifier.padding(CARD_PADDING)) { DetailTableUi(table, charts) } }
            }
        }
        DataSection.Sources ->
            Section(ActivityDataTexts.heading(section)) {
                AppCard {
                    Column(Modifier.padding(horizontal = CARD_PADDING, vertical = 4.dp)) {
                        data.sources.forEachIndexed { i, source ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            LabelValueRow(
                                ActivityDataTexts.sourceLabel(source).string(),
                                ActivityDataTexts.sourceValue(source)?.string().orEmpty(),
                            )
                        }
                    }
                }
            }
    }
}

/** A section's heading over its [content]. */
@Composable
private fun Section(heading: AppText, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        Text(heading.string(), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/** A row of chips naming a page's sections, [selected] marked, scrolling sideways when they don't fit. */
@Composable
internal fun SectionChips(labels: ImmutableList<AppText>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            AppFilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(label.string()) })
        }
    }
}
