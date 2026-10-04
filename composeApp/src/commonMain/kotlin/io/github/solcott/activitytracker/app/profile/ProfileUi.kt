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
import io.github.solcott.activitytracker.data.di.SessionScope

@CircuitInject(ProfileScreen::class, SessionScope::class)
@Composable
fun ProfileUi(state: ProfileState, modifier: Modifier = Modifier) {
    val profile = state.profile
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(profile?.displayName ?: profile?.handle?.value ?: "Profile") },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(ProfileEvent.Back) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (state.isSelf) {
                        TextButton(onClick = { state.eventSink(ProfileEvent.SignOut) }) { Text("Sign out") }
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
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            profile?.let { p ->
                item {
                    Text("@${p.handle.value}", style = MaterialTheme.typography.bodyMedium)
                    p.description?.let { Text(it) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.activitiesCount ?: 0} activities", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { state.eventSink(ProfileEvent.OpenFollowers) }) {
                            Text("${p.followersCount ?: 0} followers", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { state.eventSink(ProfileEvent.OpenFollowing) }) {
                            Text("${p.followsCount ?: 0} following", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!state.isSelf) {
                        if (p.viewer?.following == null) {
                            Button(onClick = { state.eventSink(ProfileEvent.ToggleFollow) }) { Text("Follow") }
                        } else {
                            OutlinedButton(onClick = { state.eventSink(ProfileEvent.ToggleFollow) }) { Text("Following") }
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
