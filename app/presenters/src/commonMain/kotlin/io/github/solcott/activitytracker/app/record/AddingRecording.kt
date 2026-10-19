package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.slack.circuit.runtime.screen.Screen
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.SourceFile
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.DraftSource
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import kotlin.time.Duration.Companion.hours
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/**
 * Adding a recording to the athlete's own activity ([ImportActivityScreen.combineInto], "Add a recording…"): the activity once loaded
 * ([target]), and of a logbook of several the [entry] starting nearest it, the only one offered. Inert when the screen has no target.
 */
internal class AddingRecording(val requested: Boolean, val target: OwnActivity?, private val file: ImportFile?, val entry: Int?) {
    /** The file as the screen offers it: a logbook narrowed to [entry]. */
    val offered: ImportFile? = entry?.let { index -> file?.let { (name, read) -> name to persistentListOf(read[index]) } } ?: file

    /** Saving waits for the target to load. */
    val waiting: Boolean
        get() = requested && target == null

    /** [activity] (placed in its zone) starts more than 12 hours from the target, so it can't be another recording of it. */
    fun tooFar(activity: ImportedActivity?): Boolean {
        val start = target?.record?.startedAt ?: return false
        return activity != null && (activity.startedAt - start).absoluteValue > MAX_DISTANCE
    }

    /** The target as the screen names it, on [zone]'s clock. */
    fun preview(zone: TimeZone): ExistingActivityPreview? =
        target?.record?.let { ExistingActivityPreview(it.title, it.startedAt.toLocalDateTime(zone).format(LOCAL_START), it.device) }

    /** [source] as the saved recording's: naming [entry] when it's one of several in a logbook, else as a single file's. */
    fun sourceOf(source: SourceFile?): DraftSource? {
        val several = (file?.second?.size ?: 0) > 1
        return source?.let { if (several && entry != null) DraftSource(it, entry = entry) else it.single() }
    }

    /**
     * What Save saves of the file's [activity]: nothing when it's an ignored duplicate (not [selected]), or a recording that can't be added
     * to the target (too far from it, or the target still loading).
     */
    fun saved(activity: ImportedActivity?, selected: Boolean): ImportedActivity? = activity?.takeIf { selected && !tooFar(it) && !waiting }

    /** The logged activity to combine the saved file with: the target, else the duplicate the athlete chose to combine with ([chosen]). */
    fun into(chosen: OwnActivity?): OwnActivity? = target ?: chosen

    /** Where saving goes next: a recording added from an activity's page opens the combined activity it's now part of. */
    fun opened(combined: AtUri?): Screen? = combined?.takeIf { requested }?.let { ActivityDetailScreen(it.value) }

    /** [draft] as it saves: a recording takes the target's visibility. */
    fun placed(draft: ActivityDraft): ActivityDraft = target?.let { draft.atVisibility(it.visibility) } ?: draft

    private companion object {
        val MAX_DISTANCE = 12.hours
    }
}

/** A picked file by its name: one activity, or a logbook's several. */
internal typealias ImportFile = Pair<String, ImmutableList<ImportedActivity>>

/** [AddingRecording] for [screen], once its target loads; a failure to load it goes to [onError]. */
@Composable
internal fun rememberAddingRecording(
    feed: FeedRepository,
    screen: ImportActivityScreen,
    file: ImportFile?,
    zone: TimeZone,
    onError: (UiError) -> Unit,
): AddingRecording {
    val uri = remember(screen) { screen.combineInto?.let(::AtUri) }
    val reportError by rememberUpdatedState(onError)
    var target by remember(uri) { mutableStateOf<OwnActivity?>(null) }
    LaunchedEffect(uri) {
        uri ?: return@LaunchedEffect
        runSuspendCatching { feed.activity(uri) }
            .onSuccess { target = OwnActivity(it.uri, it.cid, it.record, it.visibilityOrPublic, it.combinedInto) }
            .onFailure { reportError(UiError.of(UiError.Kind.LoadActivity, it)) }
    }
    return remember(uri, target, file, zone) {
        AddingRecording(uri != null, target, file, target?.let { nearestEntry(file?.second, it, zone) })
    }
}

/** Of a logbook's [activities] (placed in [zone]), the index of the one starting nearest [target]'s start: the one to add to it. */
private fun nearestEntry(activities: List<ImportedActivity>?, target: OwnActivity, zone: TimeZone): Int? =
    activities?.indices?.minByOrNull { (activities[it].inZone(zone).startedAt - target.record.startedAt).absoluteValue }
