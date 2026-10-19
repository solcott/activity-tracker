@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable

private fun hour12(): Boolean = js("new Intl.DateTimeFormat(undefined, { hour: 'numeric' }).resolvedOptions().hour12 === true")

@Composable actual fun uses24HourClock(): Boolean = !hour12()
