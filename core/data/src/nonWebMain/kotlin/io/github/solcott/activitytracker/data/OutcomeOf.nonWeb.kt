package io.github.solcott.activitytracker.data

/** Every native and JVM engine reports transport failures as `IOException`s. */
internal actual fun Throwable.isPlatformNetworkFailure(): Boolean = false
