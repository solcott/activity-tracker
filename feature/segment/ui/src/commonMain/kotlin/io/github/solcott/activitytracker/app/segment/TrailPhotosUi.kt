package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.report.ReportTexts
import io.github.solcott.activitytracker.app.report.ReportingHost
import io.github.solcott.activitytracker.app.ui.BlurCover
import io.github.solcott.activitytracker.app.ui.BlurredMedia
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_more
import io.github.solcott.activitytracker.composeApp.resources.details_photo
import io.github.solcott.activitytracker.composeApp.resources.media_close
import io.github.solcott.activitytracker.composeApp.resources.trail_photo_remove
import io.github.solcott.activitytracker.composeApp.resources.trail_photo_see_report
import io.github.solcott.activitytracker.composeApp.resources.trail_photo_see_segment
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * Trail photos full screen (segments/trail-reports), swiped through one at a time on black: "1 of 16" over each, who took it, when and with
 * which report, its alt text, and links to its report and segment; a photo behind a label is blurred until tapped. The menu offers Report
 * and Block on someone else's photo and Remove from trail on the viewer's own.
 */
@CircuitInject(TrailPhotosScreen::class, SessionScope::class)
@Composable
fun TrailPhotosUi(state: TrailPhotosState, modifier: Modifier = Modifier) {
    val retry = { state.eventSink(TrailPhotosEvent.Retry) }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        ContentStateLayout(state.content, UiError.Kind.LoadTrailReports, retry, Modifier.safeDrawingPadding()) { pages ->
            Box(Modifier.fillMaxSize()) {
                if (!pages.isNullOrEmpty()) Pager(state)
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(16.dp)) {
                    RefreshStatus(state.content, UiError.Kind.LoadTrailReports, retry)
                    state.error?.let { Text(errorText(it), color = Color.White) }
                    state.pages.getOrNull(state.index)?.let { Caption(it, state) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(
                AppIcon.Close,
                contentDescription = stringResource(Res.string.media_close),
                onClick = { state.eventSink(TrailPhotosEvent.Back) },
                tint = Color.White,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                state.position?.let { Text(it.string(), color = Color.White) }
                state.pages.getOrNull(state.index)?.let { PhotoMenu(it, state) }
            }
        }
    }
    ReportingHost(state.reporting)
}

@Composable
private fun Pager(state: TrailPhotosState) {
    val pages = state.pages
    val pager = rememberPagerState(initialPage = state.index.coerceIn(0, pages.lastIndex)) { pages.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { state.eventSink(TrailPhotosEvent.Show(it)) } }
    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { "${pages[it].report} ${pages[it].position}" }) {
        index ->
        Photo(pages[index])
    }
}

/** One photo, fitted; behind its label's reason, blurred, until tapped. */
@Composable
private fun Photo(page: TrailPhotoPage) {
    var revealed by remember(page.url) { mutableStateOf(false) }
    BlurredMedia(page.blur, revealed, onReveal = { revealed = true }, Modifier.fillMaxSize(), cover = BlurCover.OnBlack) {
        AsyncImage(
            page.url,
            contentDescription = page.alt ?: stringResource(Res.string.details_photo),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Who took the shown photo, when and with which report, its alt text, and See report and See segment, wrapping when they don't fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Caption(page: TrailPhotoPage, state: TrailPhotosState) {
    Column(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = CAPTION_ALPHA), MaterialTheme.shapes.medium).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(page.author, color = Color.White, style = MaterialTheme.typography.titleSmall)
        Text(page.caption.string(), color = Color.White.copy(alpha = MUTED_ALPHA), style = MaterialTheme.typography.bodySmall)
        page.alt?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium) }
        FlowRow {
            AppTextButton(onClick = { state.eventSink(TrailPhotosEvent.OpenReport) }) {
                Text(stringResource(Res.string.trail_photo_see_report), color = Color.White)
            }
            AppTextButton(onClick = { state.eventSink(TrailPhotosEvent.OpenSegment) }) {
                Text(stringResource(Res.string.trail_photo_see_segment), color = Color.White)
            }
        }
    }
}

/**
 * The "…" menu: Remove from trail on the viewer's own photo, Report (when a labeler is set up) and Block, asked first, on anyone else's.
 */
@Composable
private fun PhotoMenu(page: TrailPhotoPage, state: TrailPhotosState) {
    var open by remember { mutableStateOf(false) }
    var confirmingBlock by remember { mutableStateOf(false) }
    val send = state.eventSink
    val remove = stringResource(Res.string.trail_photo_remove)
    val report = ReportTexts.menuEntry.string()
    val block = ReportTexts.block.string()
    val items =
        remember(page.own, state.reporting.available, remove, report, block) {
            buildList {
                if (page.own) {
                    add(AppMenuItem(remove, AppIcon.Delete, destructive = true) { send(TrailPhotosEvent.Remove) })
                } else {
                    if (state.reporting.available) add(AppMenuItem(report) { send(TrailPhotosEvent.Report) })
                    add(AppMenuItem(block, destructive = true) { confirmingBlock = true })
                }
            }
                .toImmutableList()
        }
    Box {
        AppIconButton(AppIcon.More, stringResource(Res.string.detail_more), onClick = { open = true }, tint = Color.White)
        AppMenu(expanded = open, onDismiss = { open = false }, items = items)
    }
    if (confirmingBlock) {
        TrailBlockConfirm(
            page.author,
            onBlock = {
                confirmingBlock = false
                send(TrailPhotosEvent.Block)
            },
            onDismiss = { confirmingBlock = false },
        )
    }
}

private const val CAPTION_ALPHA = 0.55f
private const val MUTED_ALPHA = 0.8f
