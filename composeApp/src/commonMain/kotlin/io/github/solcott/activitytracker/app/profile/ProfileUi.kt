package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityCard
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_back
import io.github.solcott.activitytracker.composeApp.resources.profile_activities_count
import io.github.solcott.activitytracker.composeApp.resources.profile_follow
import io.github.solcott.activitytracker.composeApp.resources.profile_followers_count
import io.github.solcott.activitytracker.composeApp.resources.profile_following
import io.github.solcott.activitytracker.composeApp.resources.profile_following_count
import io.github.solcott.activitytracker.composeApp.resources.profile_settings
import io.github.solcott.activitytracker.composeApp.resources.profile_title
import io.github.solcott.activitytracker.data.di.SessionScope
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
            state.error?.let { item { Text(errorText(it), color = MaterialTheme.colorScheme.error) } }
            profile?.let { p ->
                item {
                    Text(handleLabel(p.handle.value), style = MaterialTheme.typography.bodyMedium)
                    p.description?.let { Text(it) }
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
                    if (!state.isSelf) {
                        if (p.viewer?.following == null) {
                            Button(onClick = { state.eventSink(ProfileEvent.ToggleFollow) }) {
                                Text(stringResource(Res.string.profile_follow))
                            }
                        } else {
                            OutlinedButton(onClick = { state.eventSink(ProfileEvent.ToggleFollow) }) {
                                Text(stringResource(Res.string.profile_following))
                            }
                        }
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

/** A profile count, treating a missing count as zero. */
@Composable
private fun countLabel(resource: PluralStringResource, count: Long?): String {
    val n = count ?: 0
    return pluralStringResource(resource, n.toInt(), n)
}
