package io.github.solcott.activitytracker.activityfile

import kotlinx.datetime.TimeZone

internal actual fun deviceTimeZone(): TimeZone = TimeZone.currentSystemDefault()
