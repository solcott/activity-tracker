package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy

/**
 * Whether the signed-in athlete approves followers, read once [enabled] turns true; false until then, and if it can't be read. The policy
 * comes from their record, since the AppView may lag a change they just made.
 */
@Composable
fun rememberApprovesFollowers(profiles: ProfileRepository, enabled: Boolean = true): Boolean {
    var approves by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (enabled) approves = runSuspendCatching { profiles.ownProfileRecord()?.followPolicy == FollowPolicy.APPROVE }.getOrDefault(false)
    }
    return approves
}
