package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A surface for one group of content, like a metric's card on an activity's page: an outlined Material card, or on iOS an inset grouped
 * section, the white (or near-black) rounded cell on the grey grouped background that iOS settings and Health use. It fills the width and
 * clips [content] to its shape; pad the content inside it.
 */
@Composable expect fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit)
