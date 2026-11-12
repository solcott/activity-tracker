package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.month_short_1
import io.github.solcott.activitytracker.composeApp.resources.month_short_10
import io.github.solcott.activitytracker.composeApp.resources.month_short_11
import io.github.solcott.activitytracker.composeApp.resources.month_short_12
import io.github.solcott.activitytracker.composeApp.resources.month_short_2
import io.github.solcott.activitytracker.composeApp.resources.month_short_3
import io.github.solcott.activitytracker.composeApp.resources.month_short_4
import io.github.solcott.activitytracker.composeApp.resources.month_short_5
import io.github.solcott.activitytracker.composeApp.resources.month_short_6
import io.github.solcott.activitytracker.composeApp.resources.month_short_7
import io.github.solcott.activitytracker.composeApp.resources.month_short_8
import io.github.solcott.activitytracker.composeApp.resources.month_short_9
import org.jetbrains.compose.resources.stringResource

/** [date] written out ("Jan 5, 2026"), as `DateText` chooses it. */
@Composable fun longDate(date: HistoryDate): String = DateText.date(date.year, date.month, date.day).string()

@Suppress("MagicNumber") // Months of the year.
@Composable
fun monthName(month: Int): String =
    stringResource(
        when (month) {
            1 -> Res.string.month_short_1
            2 -> Res.string.month_short_2
            3 -> Res.string.month_short_3
            4 -> Res.string.month_short_4
            5 -> Res.string.month_short_5
            6 -> Res.string.month_short_6
            7 -> Res.string.month_short_7
            8 -> Res.string.month_short_8
            9 -> Res.string.month_short_9
            10 -> Res.string.month_short_10
            11 -> Res.string.month_short_11
            else -> Res.string.month_short_12
        },
    )
