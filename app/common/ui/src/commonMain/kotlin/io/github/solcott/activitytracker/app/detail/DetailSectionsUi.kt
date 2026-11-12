package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.string

/** A card's title: its section's name. */
@Composable internal fun sectionTitle(section: DetailSection): String = FigureTexts.section(section).string()

/** Gaps shared by the page's cards, so every card lines up. */
val CARD_PADDING = 16.dp
val ITEM_GAP = 12.dp
