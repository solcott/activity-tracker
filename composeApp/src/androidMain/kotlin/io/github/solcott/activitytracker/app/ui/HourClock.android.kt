package io.github.solcott.activitytracker.app.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

@Composable @ReadOnlyComposable actual fun uses24HourClock(): Boolean = DateFormat.is24HourFormat(LocalContext.current)
