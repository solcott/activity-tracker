package io.github.solcott.activitytracker.data.di

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

/** Where the desktop app keeps its database, key and HTTP cache. The desktop graph factory provides it, as Android provides `Context`. */
class DesktopAppDirs(val data: Path) {
    val cache: Path
        get() = data.resolve("cache")

    init {
        cache.createDirectories()
    }

    companion object {
        /** The per-OS user data directory: Application Support on macOS, `%APPDATA%` on Windows, XDG elsewhere. */
        fun forCurrentOs(): DesktopAppDirs {
            val home = System.getProperty("user.home")
            val os = System.getProperty("os.name").lowercase()
            val data =
                when {
                    "mac" in os -> Path(home, "Library", "Application Support", "ActivityTracker")
                    "win" in os -> Path(System.getenv("APPDATA") ?: home, "ActivityTracker")
                    else -> Path(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "activity-tracker")
                }
            return DesktopAppDirs(Files.createDirectories(data))
        }
    }
}
