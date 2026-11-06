package io.github.solcott.activitytracker.app.interactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_no_likes
import io.github.solcott.activitytracker.lexicon.Did
import org.jetbrains.compose.resources.stringResource

/** Who liked an activity or a post, under its like row: a spinner, an error, the empty state, or one tappable row per athlete. */
@Composable
fun LikersList(list: LikesList, onOpenProfile: (Did) -> Unit, modifier: Modifier = Modifier) {
    val error = list.error
    Column(modifier.fillMaxWidth()) {
        when {
            list.isLoading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { AppProgressIndicator() }
            error != null -> ErrorText(error)
            list.athletes.isEmpty() -> Text(stringResource(Res.string.detail_no_likes), style = MaterialTheme.typography.bodyMedium)
            else -> {
                list.athletes.forEach { athlete ->
                    Column(Modifier.fillMaxWidth().clickable { onOpenProfile(athlete.did) }.padding(vertical = 6.dp)) {
                        Text(athlete.shownName, style = MaterialTheme.typography.titleSmall)
                        Text(handleLabel(athlete.handle.value), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
