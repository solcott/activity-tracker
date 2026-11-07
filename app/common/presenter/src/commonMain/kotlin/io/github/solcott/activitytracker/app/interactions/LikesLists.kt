package io.github.solcott.activitytracker.app.interactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.circuit.produceRetainedContentState
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.isLoading
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf

/**
 * Who liked [uri], an activity or a post, while [show] is set, reloaded each time it's shown. The AppView lags the viewer's own toggles, so
 * the viewer is added or dropped to match [viewerGave].
 */
@Composable
fun rememberLikesList(
    show: Boolean,
    uri: AtUri,
    viewerGave: Boolean,
    feed: FeedRepository,
    profiles: ProfileRepository,
): LikesList? = rememberAthletesList(show, viewerGave, profiles, UiError.Kind.LoadLikes) { feed.likes(uri) }

/**
 * Who reposted the post [uri] (feed/reposts), while [show] is set, as [rememberLikesList] lists likers: the viewer is added or dropped to
 * match [viewerGave], their repost as this session left it.
 */
@Composable
fun rememberRepostsList(
    show: Boolean,
    uri: AtUri,
    viewerGave: Boolean,
    feed: FeedRepository,
    profiles: ProfileRepository,
): LikesList? = rememberAthletesList(show, viewerGave, profiles, UiError.Kind.LoadReposts) { feed.reposts(uri) }

/**
 * The athletes [load] lists while [show] is set, reloaded each time it's shown, failing as [errorKind]. The AppView lags the viewer's own
 * toggles, so the viewer is added or dropped to match [viewerGave].
 */
@Composable
private fun rememberAthletesList(
    show: Boolean,
    viewerGave: Boolean,
    profiles: ProfileRepository,
    errorKind: UiError.Kind,
    load: suspend () -> List<ProfileViewBasic>,
): LikesList? {
    // Starts (and goes back to) loading while hidden, so the first frame after showing doesn't flash the list as it was.
    val listed =
        produceRetainedContentState<List<ProfileViewBasic>>(emptyList(), show) {
            if (show) outcomeFlow { load() } else flowOf(Outcome.Loading)
        }
    val athletes = listed.data
    val loading = listed.isLoading
    val error = listed.errorOrNull?.let { UiError(errorKind, it) }
    var me by remember { mutableStateOf<ProfileViewBasic?>(null) }

    val viewer = profiles.did
    val viewerListed = athletes.any { it.did == viewer }
    val needMe = show && !loading && error == null && viewerGave && !viewerListed && me == null
    LaunchedEffect(needMe) {
        if (needMe) {
            runSuspendCatching { profiles.profile(viewer.value) }.onSuccess { me = ProfileViewBasic(it.did, it.handle, it.displayName) }
        }
    }

    if (!show) return null
    val merged =
        when {
            !viewerGave -> athletes.filter { it.did != viewer }
            viewerListed -> athletes
            else -> listOfNotNull(me) + athletes
        }
    return LikesList(merged.toImmutableList(), loading, error)
}
