package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityCard
import io.github.solcott.activitytracker.app.ui.Avatar
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.countryName
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.genderLabel
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_back
import io.github.solcott.activitytracker.composeApp.resources.profile_activities_count
import io.github.solcott.activitytracker.composeApp.resources.profile_age
import io.github.solcott.activitytracker.composeApp.resources.profile_edit
import io.github.solcott.activitytracker.composeApp.resources.profile_follow
import io.github.solcott.activitytracker.composeApp.resources.profile_followers_count
import io.github.solcott.activitytracker.composeApp.resources.profile_following
import io.github.solcott.activitytracker.composeApp.resources.profile_following_count
import io.github.solcott.activitytracker.composeApp.resources.profile_settings
import io.github.solcott.activitytracker.composeApp.resources.profile_title
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.CalendarDate
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import kotlin.time.Clock
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@CircuitInject(ProfileScreen::class, SessionScope::class)
@Composable
fun ProfileUi(state: ProfileState, modifier: Modifier = Modifier) {
    val profile = state.profile
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(profile?.displayName ?: profile?.handle?.value ?: stringResource(Res.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(ProfileEvent.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    if (state.isSelf) {
                        IconButton(onClick = { state.eventSink(ProfileEvent.OpenSettings) }) {
                            Icon(Icons.Filled.Settings, stringResource(Res.string.profile_settings))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
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
                        TextButton(onClick = { state.eventSink(ProfileEvent.OpenFollowers) }) {
                            Text(
                                countLabel(Res.plurals.profile_followers_count, p.followersCount),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { state.eventSink(ProfileEvent.OpenFollowing) }) {
                            Text(
                                stringResource(Res.string.profile_following_count, p.followsCount ?: 0),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (state.isSelf) {
                        OutlinedButton(onClick = { state.eventSink(ProfileEvent.EditProfile) }) {
                            Text(stringResource(Res.string.profile_edit))
                        }
                    } else {
                        FollowButton(
                            following = p.viewer?.following != null,
                            pending = state.followPending,
                            onClick = { state.eventSink(ProfileEvent.ToggleFollow) },
                        )
                    }
                }
            }
            items(state.activities, key = { it.uri.value }) { activity ->
                ActivityCard(
                    activity = activity,
                    onClick = { state.eventSink(ProfileEvent.Open(activity)) },
                    onAuthorClick = {},
                    onKudos = { state.eventSink(ProfileEvent.ToggleKudos(activity)) }.takeUnless { state.isSelf },
                )
            }
        }
    }
}

/** Follow, or Following when [following]. While [pending] it's disabled and shows a spinner, so a second tap can't follow twice. */
@Composable
private fun FollowButton(following: Boolean, pending: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val content: @Composable RowScope.() -> Unit = {
        if (pending) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(if (following) Res.string.profile_following else Res.string.profile_follow))
    }
    if (following) {
        OutlinedButton(onClick, modifier, enabled = !pending, content = content)
    } else {
        Button(onClick, modifier, enabled = !pending, content = content)
    }
}

/** A profile count, treating a missing count as zero. */
@Composable
private fun countLabel(resource: PluralStringResource, count: Long?): String {
    val n = count ?: 0
    return pluralStringResource(resource, n.toInt(), n)
}

private const val HEADER_KEY = "header"

/** The details this viewer may see: a location line, then age, gender and weight. */
@Composable
private fun ProfileDetailsText(details: ProfileDetails, modifier: Modifier = Modifier) {
    val now = remember { Clock.System.now() }
    val location = details.location?.let { listOfNotNull(it.locality, it.region, it.country?.let(::countryName)).joinToString(", ") }
    val age = details.birthDate?.let { CalendarDate.age(it, now) }
    val facts =
        listOfNotNull(
            age?.let { pluralStringResource(Res.plurals.profile_age, it, it) },
            details.gender?.let { genderLabel(it) },
            details.weight?.let { Format.weight(it) },
        )
    Column(modifier) {
        location?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
    }
}
