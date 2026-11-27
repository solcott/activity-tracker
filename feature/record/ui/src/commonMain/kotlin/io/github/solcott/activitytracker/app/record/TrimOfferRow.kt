package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.adaptive.AppListRow
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.trim_offer
import org.jetbrains.compose.resources.stringResource

/** "Trim start or end" for an unsaved activity (feed/activity-trim), with the suggestion or that it's trimmed under it; opens the trim. */
@Composable
fun TrimOfferRow(offer: TrimOffer, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    AppListRow(
        title = stringResource(Res.string.trim_offer),
        modifier = modifier,
        summary = offer.summary(LocalUnitSystem.current)?.string(),
        onClick = onClick,
        enabled = enabled,
    )
}
