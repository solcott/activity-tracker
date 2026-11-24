package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.pagedStateOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Every report on the segment at [segment] (segments/trail-reports), titled with its [name] (loaded when absent, as from a link): the last
 * 14 days first, then older ones.
 */
@CircuitSerializable(SessionScope::class) data class TrailReportsScreen(val segment: String, val name: String? = null) : Screen

@Immutable
data class TrailReportsState(
    val name: String,
    /** The reports as rows, newest first, a page at a time (ui/content-loading). */
    val reports: PagedState<TrailReportRow, Unit> = pagedStateOf(),
    /** Report on others' reports (moderation/reports). */
    val reporting: Reporting = Reporting(),
    /** Why the last delete or block failed. */
    val error: UiError? = null,
    val eventSink: (TrailReportsEvent) -> Unit = {},
) : CircuitUiState {
    /**
     * "Last 14 days" or "Older" over the row at [index] of [reports]' items when it starts its section (the first row, or the first older
     * one), else null.
     */
    fun heading(index: Int): AppText? {
        val row = reports.items.getOrNull(index) ?: return null
        val previous = reports.items.getOrNull(index - 1)
        return if (previous == null || previous.current != row.current) TrailReportTexts.heading(row.current) else null
    }
}

/**
 * One report as the list shows it: under the "Last 14 days" or "Older" heading ([current]), its [author] and [age], tags, note and photos;
 * the viewer's own ([own]) offers Delete, anyone else's Report and Block.
 */
@Immutable
data class TrailReportRow(
    val uri: String,
    val cid: String,
    val author: String,
    val authorDid: String,
    val avatar: String?,
    val age: AppText,
    val tags: ImmutableList<AppText> = persistentListOf(),
    val note: String? = null,
    val photos: ImmutableList<PhotoThumb> = persistentListOf(),
    val current: Boolean = true,
    val own: Boolean = false,
) {
    val shownTags: List<AppText>
        get() = tags.toList()

    val shownPhotos: List<PhotoThumb>
        get() = photos.toList()
}

sealed interface TrailReportsEvent {
    data object Back : TrailReportsEvent

    data object Retry : TrailReportsEvent

    data object LoadMore : TrailReportsEvent

    /** Deletes the viewer's own report [uri]. */
    data class Delete(val uri: String) : TrailReportsEvent

    /** Opens the report sheet on the report [uri]. */
    data class Report(val uri: String) : TrailReportsEvent

    /** Blocks the author of the report [uri], after the usual confirmation. */
    data class Block(val uri: String) : TrailReportsEvent

    /** Opens photo [position] (from 1) of the report [uri] in the viewer. */
    data class OpenPhoto(val uri: String, val position: Int) : TrailReportsEvent

    data object DismissError : TrailReportsEvent
}

/**
 * Trail photos full screen (segments/trail-reports): those of one segment's reports ([segment]) or of a trail's ([path]), opened on the
 * photo [start] of them (from 0).
 */
@CircuitSerializable(SessionScope::class)
data class TrailPhotosScreen(val segment: String? = null, val path: String? = null, val start: Int = 0) : Screen

@Immutable
data class TrailPhotosState(
    /** The photos read so far, newest first; null until the first page loads. */
    val content: ContentState<ImmutableList<TrailPhotoPage>?> = ContentState(null),
    /** The photo shown, from 0. */
    val index: Int = 0,
    /** How many photos there are in all, for "1 of 16"; null until known. */
    val total: Int? = null,
    val reporting: Reporting = Reporting(),
    val error: UiError? = null,
    val eventSink: (TrailPhotosEvent) -> Unit = {},
) : CircuitUiState {
    val pages: List<TrailPhotoPage>
        get() = content.data.orEmpty().toList()

    /** "1 of 16". */
    val position: AppText?
        get() =
            pages
                .takeIf { it.isNotEmpty() }
                ?.let { AppText.named("trail_photo_position", AppText.whole(index + 1), AppText.whole(total ?: it.size)) }
}

/**
 * One photo: [url], [alt] text and [blur] reason; its [author], [age] and [tags] ("with a report: Muddy, Downed tree"); the report [report]
 * and its segment [segment] it links to. The viewer's own ([own]) offers Remove from trail, anyone else's Report and Block.
 */
@Immutable
data class TrailPhotoPage(
    val url: String,
    val alt: String?,
    val blur: AppText?,
    val author: String,
    val age: AppText,
    val tags: AppText?,
    val report: String,
    val position: Int,
    val segment: String,
    val own: Boolean,
) {
    /** "2 days ago · with a report: Muddy, Downed tree", or just the age. */
    val caption: AppText
        get() = tags?.let { AppText.named("trail_photo_with_report", age, it) } ?: age
}

sealed interface TrailPhotosEvent {
    data object Back : TrailPhotosEvent

    data object Retry : TrailPhotosEvent

    /** Shows photo [index] (from 0), loading more when it nears the end of those read. */
    data class Show(val index: Int) : TrailPhotosEvent

    /** Opens the shown photo's report list. */
    data object OpenReport : TrailPhotosEvent

    /** Opens the shown photo's segment. */
    data object OpenSegment : TrailPhotosEvent

    /** Reports the shown photo. */
    data object Report : TrailPhotosEvent

    /** Blocks the shown photo's author, after the usual confirmation. */
    data object Block : TrailPhotosEvent

    /** Removes the viewer's own shown photo from the trail. */
    data object Remove : TrailPhotosEvent

    data object DismissError : TrailPhotosEvent
}
