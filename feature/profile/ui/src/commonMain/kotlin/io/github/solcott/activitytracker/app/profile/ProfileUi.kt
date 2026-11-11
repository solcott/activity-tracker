package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.posts.PostCard
import io.github.solcott.activitytracker.app.posts.QuoteSheetHost
import io.github.solcott.activitytracker.app.report.ReportingHost
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.Avatar
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.HiddenCard
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.LocalClock
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.ModerationNotice
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.activityItems
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.countryName
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.app.ui.pagedFooter
import io.github.solcott.activitytracker.app.ui.pagedRefreshStatus
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.action_report
import io.github.solcott.activitytracker.composeApp.resources.profile_posts_empty
import io.github.solcott.activitytracker.composeApp.resources.profile_tab_activities
import io.github.solcott.activitytracker.composeApp.resources.profile_tab_posts
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

@CircuitInject(ProfileScreen::class, SessionScope::class)
@Composable
fun ProfileUi(state: ProfileState, modifier: Modifier = Modifier) {
    val profile = state.profile
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = ProfileTexts.title(profile).string(),
                onBack = { state.eventSink(ProfileEvent.Back) },
                scrollBehavior = scrollBehavior,
                actions = { ProfileBarActions(state) },
            )
        },
    ) { padding ->
        val retry = { state.eventSink(ProfileEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadProfile, retry, contentPadding = padding) {
            val listState = rememberLazyListState()
            LoadMoreAtEnd(listState, state.tab == ProfileTab.Posts && state.posts.canLoadMore) {
                state.eventSink(ProfileEvent.LoadMorePosts)
            }
            ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = listPadding + PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = HEADER_KEY) {
                        // The activities show their own refresh below; the header's shows here only when they don't.
                        RefreshStatus(state.content, UiError.Kind.LoadProfile, retry, showProgress = state.blocking)
                        state.error?.let { ErrorText(it) }
                        profile?.let { p ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(
                                    p.avatar,
                                    p.shownName,
                                    72.dp,
                                    blur = state.avatarBlur,
                                    onReveal = { state.revealed.eventSink(p.did.value) },
                                )
                                Text(handleLabel(p.handle.value), style = MaterialTheme.typography.bodyMedium)
                            }
                            ModerationNotice(ModerationDecision.of(p.labels, null, state.isSelf), state.isSelf)
                            state.bio?.let { Text(it) }
                            p.details?.let { ProfileDetailsText(it) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ProfileTexts.activities(p.activitiesCount).string(), style = MaterialTheme.typography.bodySmall)
                                AppTextButton(onClick = { state.eventSink(ProfileEvent.OpenFollowers) }) {
                                    Text(ProfileTexts.followers(p.followersCount).string(), style = MaterialTheme.typography.bodySmall)
                                }
                                AppTextButton(onClick = { state.eventSink(ProfileEvent.OpenFollowing) }) {
                                    Text(ProfileTexts.following(p.followsCount).string(), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            ProfileActionsRow(state)
                        }
                    }
                    if (state.showsActivities) {
                        item(key = TABS_KEY) {
                            AppSegmentedControl(
                                labels =
                                    persistentListOf(
                                        stringResource(Res.string.profile_tab_activities),
                                        stringResource(Res.string.profile_tab_posts),
                                    ),
                                selected = state.tab.ordinal,
                                onSelect = { state.eventSink(ProfileEvent.SelectTab(ProfileTab.entries[it])) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    when (state.tab) {
                        ProfileTab.Activities -> activitiesSection(state, retry)
                        ProfileTab.Posts -> postsSection(state)
                    }
                }
            }
        }
    }
}

/**
 * The athlete's activities, loaded on their own below the header: none while the viewer blocks them, and behind a warning when they're
 * hidden and reached by URI only.
 */
private fun LazyListScope.activitiesSection(state: ProfileState, retry: () -> Unit) {
    if (state.activitiesHidden) {
        // Reached by URI only: the athlete is left out of lists, so their activities wait behind a warning.
        item(key = HIDDEN_KEY) { HiddenCard(onShow = { state.revealed.eventSink(state.hiddenKey) }) }
    } else if (state.showsActivities) {
        item(key = ACTIVITIES_STATUS_KEY) { RefreshStatus(state.activities, UiError.Kind.LoadActivities, retry) }
        activityItems(state.activities.data, state.viewerDid, authorClickable = false, state.revealed) {
            state.eventSink(ProfileEvent.Activity(it))
        }
        if (state.activitiesEmpty) {
            item(key = NO_ACTIVITIES_KEY) {
                Text(
                    ProfileTexts.activitiesEmpty.string(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The athlete's posts the viewer may see (feed/posts), paged, below the header; none while the viewer blocks them. */
private fun LazyListScope.postsSection(state: ProfileState) {
    if (state.blocking) return
    val retry = { state.eventSink(ProfileEvent.RetryPosts) }
    pagedRefreshStatus(state.posts, UiError.Kind.LoadPosts, retry)
    items(state.posts.items, key = { "post:${it.uri.value}" }) { post ->
        PostCard(post, state.viewerDid, state.reporting.available, onEvent = { state.eventSink(ProfileEvent.Post(it)) })
    }
    pagedFooter(state.posts, UiError.Kind.LoadPosts, retry) {
        Text(
            stringResource(Res.string.profile_posts_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The top bar's actions: Settings on the athlete's own profile, the block and mute menu on anyone else's. */
@Composable
private fun ProfileBarActions(state: ProfileState) {
    val profile = state.profile
    if (state.isSelf) {
        AppIconButton(AppIcon.Settings, ProfileTexts.settings.string(), { state.eventSink(ProfileEvent.OpenSettings) })
    } else if (profile != null) {
        val name = ProfileTexts.name(profile).string()
        val reporting = state.reporting
        BlockMuteMenu(
            name = name,
            blocking = state.blocking,
            offersBlock = state.offersBlock,
            muting = state.muting,
            eventSink = state.eventSink,
            // Kept on a profile the viewer blocks, which has nothing else to report from.
            onReport = state.reportEvent(name)?.let { open -> { reporting.eventSink(open) } },
        )
        ReportingHost(reporting)
        QuoteSheetHost(state.quote)
    }
}

/** The athlete's own entries, the Bluesky note, Unblock, or the follow button, as [ProfileState.actions] says. */
@Composable
private fun ProfileActionsRow(state: ProfileState) {
    when (state.actions) {
        ProfileActions.Own -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileTexts.ownEntries(state).forEach { entry ->
                    AppSecondaryButton(onClick = { state.eventSink(entry.event) }) { Text(entry.label.string()) }
                }
            }
        }

        ProfileActions.BlockedOnBluesky -> {
            Text(
                ProfileTexts.blockedOnBluesky.string(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ProfileActions.Unblock -> {
            // A blocked athlete's follows don't count: Unblock is the one thing to do here.
            AppSecondaryButton(onClick = { state.eventSink(ProfileEvent.ToggleBlock) }) {
                Text(ProfileTexts.block(blocking = true).string())
            }
        }

        ProfileActions.Follow -> {
            FollowButton(
                label = state.followLabel,
                primary = state.followPrimary,
                pending = state.followPending,
                onClick = { state.eventSink(ProfileEvent.ToggleFollow) },
            )
        }
    }
}

/**
 * Follow, Following or Requested ([label]; a tap on Requested cancels it), filled when [primary]. While [pending] it's disabled and shows a
 * spinner, so a second tap can't follow twice.
 */
@Composable
private fun FollowButton(label: FollowLabel, primary: Boolean, pending: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val content: @Composable RowScope.() -> Unit = {
        if (pending) {
            AppProgressIndicator(small = true)
            Spacer(Modifier.width(8.dp))
        }
        Text(ProfileTexts.follow(label).string())
    }
    if (primary) {
        AppButton(onClick, modifier, enabled = !pending, content = content)
    } else {
        AppSecondaryButton(onClick, modifier, enabled = !pending, content = content)
    }
}

/**
 * The "…" menu on someone else's profile: Mute or Unmute at once, and Block (after a confirmation saying they won't be told and that Public
 * activities stay visible outside the app) or Unblock; no Block or Unblock unless [offersBlock] (a block held only on Bluesky, which only
 * Bluesky can remove, offers neither).
 */
@Composable
@Suppress("LongParameterList") // What the menu offers, and where its choices go.
private fun BlockMuteMenu(
    name: String,
    blocking: Boolean,
    offersBlock: Boolean,
    muting: Boolean,
    eventSink: (ProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the report sheet; null when Report isn't offered. */
    onReport: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val mute = ProfileTexts.mute(muting).string()
    val block = ProfileTexts.block(blocking).string()
    val report = stringResource(Res.string.action_report)
    val items =
        remember(mute, block, report, blocking, offersBlock, onReport) {
            val muteItem = AppMenuItem(mute) { eventSink(ProfileEvent.ToggleMute) }
            val blockItem =
                AppMenuItem(block, destructive = !blocking) { if (blocking) eventSink(ProfileEvent.ToggleBlock) else confirming = true }
            val reportItem = onReport?.let { AppMenuItem(report) { it() } }
            listOfNotNull(muteItem, blockItem.takeIf { offersBlock }, reportItem).toImmutableList()
        }
    Box(modifier) {
        AppIconButton(AppIcon.More, ProfileTexts.more.string(), onClick = { open = true })
        AppMenu(expanded = open, onDismiss = { open = false }, items = items)
    }
    if (confirming) {
        AppAlertDialog(
            title = ProfileTexts.blockConfirmTitle(AppText.written(name)).string(),
            message = ProfileTexts.blockConfirmMessage.string(),
            actions = persistentListOf(AppDialogAction(block, destructive = true) { eventSink(ProfileEvent.ToggleBlock) }),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { confirming = false },
        )
    }
}

private const val HIDDEN_KEY = "hidden"
private const val HEADER_KEY = "header"
private const val ACTIVITIES_STATUS_KEY = "activitiesStatus"
private const val TABS_KEY = "tabs"
private const val NO_ACTIVITIES_KEY = "noActivities"

/** The details this viewer may see: a location line, then age, gender, height and weight ([ProfileTexts]). */
@Composable
private fun ProfileDetailsText(details: ProfileDetails, modifier: Modifier = Modifier) {
    val clock = LocalClock.current
    val now = remember { clock.now() }
    val units = LocalUnitSystem.current
    val location = ProfileTexts.location(details, details.location?.country?.let(::countryName))
    val facts = ProfileTexts.facts(details, now, units)
    Column(modifier) {
        location?.let { Text(it.string(), style = MaterialTheme.typography.bodyMedium) }
        facts?.let { Text(it.string(), style = MaterialTheme.typography.bodyMedium) }
    }
}
