package io.github.solcott.activitytracker.app.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.details_photo
import io.github.solcott.activitytracker.composeApp.resources.media_close
import io.github.solcott.activitytracker.composeApp.resources.media_counter
import io.github.solcott.activitytracker.composeApp.resources.media_next
import io.github.solcott.activitytracker.composeApp.resources.media_previous
import io.github.solcott.activitytracker.composeApp.resources.media_zoom_hint
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The activity's photos full screen, swiped through one at a time. Tapping a photo zooms it (pinch, double tap, drag, and the mouse wheel
 * on desktop and web, through ZoomImage); tapping again lets the pager swipe once more.
 */
@CircuitInject(MediaGalleryScreen::class, SessionScope::class)
@Composable
fun MediaGalleryUi(state: MediaGalleryState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        val error = state.error
        when {
            state.items.isNotEmpty() -> Pager(state)
            error != null -> Text(errorText(error), Modifier.align(Alignment.Center).padding(24.dp), color = Color.White)
            else -> AppProgressIndicator(Modifier.align(Alignment.Center))
        }
        Row(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(
                AppIcon.Close,
                contentDescription = stringResource(Res.string.media_close),
                onClick = { state.eventSink(MediaGalleryEvent.Back) },
                tint = Color.White,
            )
            if (state.items.size > 1) {
                Text(stringResource(Res.string.media_counter, state.page + 1, state.items.size), color = Color.White)
            }
        }
    }
}

/**
 * Swiped on phones. A mouse doesn't drag a pager, so desktop and web get previous and next buttons too, and every platform's arrow keys
 * page while the gallery has focus.
 */
@Composable
private fun Pager(state: MediaGalleryState, modifier: Modifier = Modifier) {
    val pager = rememberPagerState(initialPage = state.page) { state.items.size }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    fun go(page: Int) {
        if (!state.zoomed && page in state.items.indices) scope.launch { pager.animateScrollToPage(page) }
    }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { state.eventSink(MediaGalleryEvent.PageChanged(it)) } }
    LaunchedEffect(Unit) {
        // Once laid out: a requester only takes focus for a node that's attached.
        withFrameNanos {}
        focus.requestFocus()
    }
    Box(
        modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent { event ->
            event.type == KeyEventType.KeyDown &&
                pageKey(event.key, onPage = { go(pager.currentPage + it) }, onBack = { state.eventSink(MediaGalleryEvent.Back) })
        }
    ) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !state.zoomed, beyondViewportPageCount = 1, key = { it }) { page
            ->
            val zoomed = state.zoomed && page == state.page
            when (val item = state.items[page]) {
                is MediaItem.Photo -> Photo(item, zoomed, onTap = { state.eventSink(MediaGalleryEvent.ToggleZoom) })
            }
        }
        if (!LocalUiPlatform.current.isMobile && !state.zoomed) {
            if (pager.currentPage > 0) {
                PageButton(
                    AppIcon.ChevronBack,
                    Res.string.media_previous,
                    { go(pager.currentPage - 1) },
                    Modifier.align(Alignment.CenterStart),
                )
            }
            if (pager.currentPage < state.items.size - 1) {
                PageButton(AppIcon.Chevron, Res.string.media_next, { go(pager.currentPage + 1) }, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

@Composable
private fun PageButton(icon: AppIcon, label: StringResource, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppIconButton(
        icon,
        contentDescription = stringResource(label),
        onClick = onClick,
        tint = Color.White,
        modifier = modifier.padding(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = SCRIM_ALPHA)),
    )
}

private const val SCRIM_ALPHA = 0.5f

@Composable
private fun Photo(photo: MediaItem.Photo, zoomed: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val description = photo.alt ?: stringResource(Res.string.details_photo)
    if (zoomed) {
        // A fresh zoom state each time, so leaving zoom mode resets the scale.
        CoilZoomAsyncImage(photo.url, contentDescription = description, modifier = modifier.fillMaxSize(), onTap = { onTap() })
    } else {
        val hint = stringResource(Res.string.media_zoom_hint)
        AsyncImage(
            photo.url,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier =
                modifier
                    .fillMaxSize()
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = hint, onClick = onTap),
        )
    }
}

/** The arrow keys page ([onPage] by -1 or 1) and Escape leaves; true when [key] was one of them. */
private fun pageKey(key: Key, onPage: (Int) -> Unit, onBack: () -> Unit): Boolean =
    when (key) {
        Key.DirectionLeft -> true.also { onPage(-1) }
        Key.DirectionRight -> true.also { onPage(1) }
        Key.Escape -> true.also { onBack() }
        else -> false
    }
