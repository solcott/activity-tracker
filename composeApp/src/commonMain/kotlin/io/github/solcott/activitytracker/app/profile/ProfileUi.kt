package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.app.report.ReportingEvent
import io.github.solcott.activitytracker.app.report.ReportingHost
import io.github.solcott.activitytracker.app.ui.Avatar
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.HiddenCard
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
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.countryName
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.genderLabel
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_block
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.action_mute
import io.github.solcott.activitytracker.composeApp.resources.action_report
import io.github.solcott.activitytracker.composeApp.resources.action_unblock
import io.github.solcott.activitytracker.composeApp.resources.action_unmute
import io.github.solcott.activitytracker.composeApp.resources.block_confirm_message
import io.github.solcott.activitytracker.composeApp.resources.block_confirm_title
import io.github.solcott.activitytracker.composeApp.resources.blocked_on_bluesky
import io.github.solcott.activitytracker.composeApp.resources.profile_activities_count
import io.github.solcott.activitytracker.composeApp.resources.profile_activities_none
import io.github.solcott.activitytracker.composeApp.resources.profile_age
import io.github.solcott.activitytracker.composeApp.resources.profile_edit
import io.github.solcott.activitytracker.composeApp.resources.profile_follow
import io.github.solcott.activitytracker.composeApp.resources.profile_follow_requests
import io.github.solcott.activitytracker.composeApp.resources.profile_followers_count
import io.github.solcott.activitytracker.composeApp.resources.profile_following
import io.github.solcott.activitytracker.composeApp.resources.profile_following_count
import io.github.solcott.activitytracker.composeApp.resources.profile_more
import io.github.solcott.activitytracker.composeApp.resources.profile_requested
import io.github.solcott.activitytracker.composeApp.resources.profile_settings
import io.github.solcott.activitytracker.composeApp.resources.profile_title
import io.github.solcott.activitytracker.composeApp.resources.starred_segments_title
import io.github.solcott.activitytracker.composeApp.resources.unit_height_cm
import io.github.solcott.activitytracker.composeApp.resources.unit_height_ft_in
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.profile.CalendarDate
import io.github.solcott.activitytracker.data.units.HeightConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.uistate.hasLoaded
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
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
                title = profile?.displayName ?: profile?.handle?.value ?: stringResource(Res.string.profile_title),
                onBack = { state.eventSink(ProfileEvent.Back) },
                scrollBehavior = scrollBehavior,
                actions = { ProfileBarActions(state) },
            )
        },
    ) { padding ->
        val retry = { state.eventSink(ProfileEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadProfile, retry, contentPadding = padding) {
            val listState = rememberLazyListState()
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
                        state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
                        profile?.let { p ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(
                                    p.avatar,
                                    p.displayName ?: p.handle.value,
                                    72.dp,
                                    blur = ModerationDecision.mediaOf(p.labels, null),
                                    revealed = p.did.value in state.revealed,
                                    onReveal = { state.revealed.eventSink(p.did.value) },
                                )
                                Text(handleLabel(p.handle.value), style = MaterialTheme.typography.bodyMedium)
                            }
                            ModerationNotice(ModerationDecision.of(p.labels, null, state.isSelf), state.isSelf)
                            p.description?.let { Text(it) }
                            p.details?.let { ProfileDetailsText(it) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    countLabel(Res.plurals.profile_activities_count, p.activitiesCount),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                AppTextButton(onClick = { state.eventSink(ProfileEvent.OpenFollowers) }) {
                                    Text(
                                        countLabel(Res.plurals.profile_followers_count, p.followersCount),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                AppTextButton(onClick = { state.eventSink(ProfileEvent.OpenFollowing) }) {
                                    Text(
                                        stringResource(Res.string.profile_following_count, formatWhole(p.followsCount ?: 0L)),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            ProfileActions(state, following = p.viewer?.following != null, requested = p.viewer?.followRequested == true)
                        }
                    }
                    activitiesSection(state, retry)
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
    val profile = state.profile
    val hiddenKey = Revealed.hiddenKey(profile?.did?.value.orEmpty())
    val hidden = profile != null && ModerationDecision.of(profile.labels, null, state.isSelf) == ModerationDecision.Hidden
    if (hidden && hiddenKey !in state.revealed) {
        // Reached by URI only: the athlete is left out of lists, so their activities wait behind a warning.
        item(key = HIDDEN_KEY) { HiddenCard(onShow = { state.revealed.eventSink(hiddenKey) }) }
    } else if (!state.blocking) {
        item(key = ACTIVITIES_STATUS_KEY) { RefreshStatus(state.activities, UiError.Kind.LoadActivities, retry) }
        activityItems(state.activities.data, state.viewerDid, authorClickable = false, state.revealed) {
            state.eventSink(ProfileEvent.Activity(it))
        }
        if (state.activities.hasLoaded && state.activities.data.isEmpty()) {
            item(key = NO_ACTIVITIES_KEY) {
                Text(
                    stringResource(Res.string.profile_activities_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The top bar's actions: Settings on the athlete's own profile, the block and mute menu on anyone else's. */
@Composable
private fun ProfileBarActions(state: ProfileState) {
    val profile = state.profile
    if (state.isSelf) {
        AppIconButton(
            AppIcon.Settings,
            stringResource(Res.string.profile_settings),
            { state.eventSink(ProfileEvent.OpenSettings) },
        )
    } else if (profile != null) {
        val name = profile.displayName ?: handleLabel(profile.handle.value)
        val reporting = state.reporting
        BlockMuteMenu(
            name = name,
            blocking = state.blocking,
            blockedOnBluesky = state.blockedOnBluesky,
            muting = state.muting,
            eventSink = state.eventSink,
            // Kept on a profile the viewer blocks, which has nothing else to report from.
            onReport =
                if (reporting.available) {
                    { reporting.eventSink(ReportingEvent.Open(ReportTarget.Account(profile.did), name, offerBlock = !state.blocking)) }
                } else {
                    null
                },
        )
        ReportingHost(reporting)
    }
}

/** Edit, starred segments and follow requests on the athlete's own profile; Unblock on a blocked one; Follow otherwise. */
@Composable
private fun ProfileActions(state: ProfileState, following: Boolean, requested: Boolean) {
    if (state.isSelf) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSecondaryButton(onClick = { state.eventSink(ProfileEvent.EditProfile) }) { Text(stringResource(Res.string.profile_edit)) }
            AppSecondaryButton(onClick = { state.eventSink(ProfileEvent.OpenStarredSegments) }) {
                Text(stringResource(Res.string.starred_segments_title))
            }
            state.followRequestCount?.let { count ->
                AppSecondaryButton(onClick = { state.eventSink(ProfileEvent.OpenFollowRequests) }) {
                    Text(
                        stringResource(
                            Res.string.profile_follow_requests,
                            if (state.moreFollowRequests) "$count+" else count.toString(),
                        )
                    )
                }
            }
        }
    } else if (state.blockedOnBluesky) {
        Text(
            stringResource(Res.string.blocked_on_bluesky),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (state.blocking) {
        // A blocked athlete's follows don't count: Unblock is the one thing to do here.
        AppSecondaryButton(onClick = { state.eventSink(ProfileEvent.ToggleBlock) }) { Text(stringResource(Res.string.action_unblock)) }
    } else {
        FollowButton(
            following = following,
            requested = requested,
            pending = state.followPending,
            onClick = { state.eventSink(ProfileEvent.ToggleFollow) },
        )
    }
}

/**
 * Follow, Following when [following], or Requested when the follow waits for approval ([requested]; a tap cancels it). While [pending] it's
 * disabled and shows a spinner, so a second tap can't follow twice.
 */
@Composable
private fun FollowButton(following: Boolean, requested: Boolean, pending: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val content: @Composable RowScope.() -> Unit = {
        if (pending) {
            AppProgressIndicator(small = true)
            Spacer(Modifier.width(8.dp))
        }
        val label =
            when {
                requested -> Res.string.profile_requested
                following -> Res.string.profile_following
                else -> Res.string.profile_follow
            }
        Text(stringResource(label))
    }
    if (following) {
        AppSecondaryButton(onClick, modifier, enabled = !pending, content = content)
    } else {
        AppButton(onClick, modifier, enabled = !pending, content = content)
    }
}

/**
 * The "…" menu on someone else's profile: Mute or Unmute at once, and Block (after a confirmation saying they won't be told and that Public
 * activities stay visible outside the app) or Unblock; no Block or Unblock for a block held only on Bluesky ([blockedOnBluesky]), which
 * only Bluesky can remove.
 */
@Composable
@Suppress("LongParameterList") // What the menu offers, and where its choices go.
private fun BlockMuteMenu(
    name: String,
    blocking: Boolean,
    blockedOnBluesky: Boolean,
    muting: Boolean,
    eventSink: (ProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the report sheet; null when Report isn't offered. */
    onReport: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val mute = stringResource(if (muting) Res.string.action_unmute else Res.string.action_mute)
    val block = stringResource(if (blocking) Res.string.action_unblock else Res.string.action_block)
    val report = stringResource(Res.string.action_report)
    val items =
        remember(mute, block, report, blocking, blockedOnBluesky, onReport) {
            val muteItem = AppMenuItem(mute) { eventSink(ProfileEvent.ToggleMute) }
            val blockItem =
                AppMenuItem(block, destructive = !blocking) { if (blocking) eventSink(ProfileEvent.ToggleBlock) else confirming = true }
            val reportItem = onReport?.let { AppMenuItem(report) { it() } }
            listOfNotNull(muteItem, blockItem.takeUnless { blockedOnBluesky }, reportItem).toImmutableList()
        }
    Box(modifier) {
        AppIconButton(AppIcon.More, stringResource(Res.string.profile_more), onClick = { open = true })
        AppMenu(expanded = open, onDismiss = { open = false }, items = items)
    }
    if (confirming) {
        AppAlertDialog(
            title = stringResource(Res.string.block_confirm_title, name),
            message = stringResource(Res.string.block_confirm_message),
            actions = persistentListOf(AppDialogAction(block, destructive = true) { eventSink(ProfileEvent.ToggleBlock) }),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { confirming = false },
        )
    }
}

/** A profile count, treating a missing count as zero. */
@Composable
private fun countLabel(resource: PluralStringResource, count: Long?): String {
    val n = count ?: 0
    return pluralStringResource(resource, n.toInt(), formatWhole(n))
}

private const val HIDDEN_KEY = "hidden"
private const val HEADER_KEY = "header"
private const val ACTIVITIES_STATUS_KEY = "activitiesStatus"
private const val NO_ACTIVITIES_KEY = "noActivities"

/** The details this viewer may see: a location line, then age, gender, height and weight. */
@Composable
private fun ProfileDetailsText(details: ProfileDetails, modifier: Modifier = Modifier) {
    val now = remember { Clock.System.now() }
    val location = details.location?.let { listOfNotNull(it.locality, it.region, it.country?.let(::countryName)).joinToString(", ") }
    val age = details.birthDate?.let { CalendarDate.age(it, now) }
    val facts =
        listOfNotNull(
            age?.let { pluralStringResource(Res.plurals.profile_age, it, it) },
            details.gender?.let { genderLabel(it) },
            details.height?.let { heightText(it) },
            details.weight?.let { Format.weight(it) },
        )
    Column(modifier) {
        location?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
    }
}

/** A profile height (millimeters) in whole cm, or feet and inches like `5′ 11″`. */
@Composable
private fun heightText(mm: Long): String =
    when (LocalUnitSystem.current) {
        UnitSystem.Metric -> stringResource(Res.string.unit_height_cm, HeightConversions.toCentimeters(mm).roundToLong())
        UnitSystem.Imperial -> {
            val (feet, inches) = HeightConversions.toFeetInches(mm)
            stringResource(Res.string.unit_height_ft_in, feet, inches)
        }
    }
