package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.Avatar
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.activityItems
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
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
import io.github.solcott.activitytracker.composeApp.resources.profile_activities_count
import io.github.solcott.activitytracker.composeApp.resources.profile_age
import io.github.solcott.activitytracker.composeApp.resources.profile_edit
import io.github.solcott.activitytracker.composeApp.resources.profile_follow
import io.github.solcott.activitytracker.composeApp.resources.profile_follow_requests
import io.github.solcott.activitytracker.composeApp.resources.profile_followers_count
import io.github.solcott.activitytracker.composeApp.resources.profile_following
import io.github.solcott.activitytracker.composeApp.resources.profile_following_count
import io.github.solcott.activitytracker.composeApp.resources.profile_requested
import io.github.solcott.activitytracker.composeApp.resources.profile_settings
import io.github.solcott.activitytracker.composeApp.resources.profile_title
import io.github.solcott.activitytracker.composeApp.resources.unit_height_cm
import io.github.solcott.activitytracker.composeApp.resources.unit_height_ft_in
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.CalendarDate
import io.github.solcott.activitytracker.data.units.HeightConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import kotlin.math.roundToLong
import kotlin.time.Clock
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
                actions = {
                    if (state.isSelf) {
                        AppIconButton(
                            AppIcon.Settings,
                            stringResource(Res.string.profile_settings),
                            { state.eventSink(ProfileEvent.OpenSettings) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        val listState = rememberLazyListState()
        ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = listPadding + PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Always present, even before the profile loads: the activities can show first (from the local cache), and an item
                // inserted above the first visible one is scrolled out of view to keep that item in place.
                item(key = HEADER_KEY) {
                    state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
                    profile?.let { p ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.avatar, p.displayName ?: p.handle.value, 72.dp)
                            Text(handleLabel(p.handle.value), style = MaterialTheme.typography.bodyMedium)
                        }
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
                                    stringResource(Res.string.profile_following_count, p.followsCount ?: 0),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (state.isSelf) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppSecondaryButton(onClick = { state.eventSink(ProfileEvent.EditProfile) }) {
                                    Text(stringResource(Res.string.profile_edit))
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
                        } else {
                            FollowButton(
                                following = p.viewer?.following != null,
                                requested = p.viewer?.followRequested == true,
                                pending = state.followPending,
                                onClick = { state.eventSink(ProfileEvent.ToggleFollow) },
                            )
                        }
                    }
                }
                activityItems(state.activities, state.viewerDid, authorClickable = false) { state.eventSink(ProfileEvent.Activity(it)) }
            }
        }
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

/** A profile count, treating a missing count as zero. */
@Composable
private fun countLabel(resource: PluralStringResource, count: Long?): String {
    val n = count ?: 0
    return pluralStringResource(resource, n.toInt(), n)
}

private const val HEADER_KEY = "header"

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
