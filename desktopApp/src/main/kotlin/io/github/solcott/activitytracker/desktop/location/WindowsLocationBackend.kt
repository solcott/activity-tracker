package io.github.solcott.activitytracker.desktop.location

import io.github.solcott.activitytracker.location.GeoPoint
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/**
 * The Windows location service through Windows PowerShell 5.1, which ships with Windows 10 and 11 and reaches WinRT's `Geolocator`: one
 * position, printed as `lat,lng` in the invariant culture. Location off for desktop apps fails the call, which reads as none.
 */
class WindowsLocationBackend(private val io: CoroutineDispatcher = Dispatchers.IO) : LocationBackend {
    override suspend fun locate(): GeoPoint? =
        runInterruptible(io) {
            val encoded = Base64.getEncoder().encodeToString(SCRIPT.toByteArray(Charsets.UTF_16LE))
            val shell = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded).start()
            try {
                val output = shell.inputStream.bufferedReader().readText()
                if (shell.waitFor(SHELL_WAIT_SECONDS, TimeUnit.SECONDS) && shell.exitValue() == 0) parsePowerShellOutput(output) else null
            } finally {
                shell.destroyForcibly()
            }
        }

    private companion object {
        /** The script waits 25 seconds for a position; this only stops a wedged shell. */
        const val SHELL_WAIT_SECONDS = 35L

        val SCRIPT =
            """
            ${'$'}ErrorActionPreference = 'Stop'
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            ${'$'}asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
                ${'$'}_.Name -eq 'AsTask' -and ${'$'}_.GetParameters().Count -eq 1 -and
                ${'$'}_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
            } | Select-Object -First 1
            [Windows.Devices.Geolocation.Geolocator, Windows.Devices.Geolocation, ContentType = WindowsRuntime] | Out-Null
            ${'$'}locator = New-Object Windows.Devices.Geolocation.Geolocator
            ${'$'}task = ${'$'}asTask.MakeGenericMethod([Windows.Devices.Geolocation.Geoposition]).Invoke(${'$'}null, @(${'$'}locator.GetGeopositionAsync()))
            if (-not ${'$'}task.Wait(25000)) { exit 2 }
            ${'$'}at = ${'$'}task.Result.Coordinate.Point.Position
            [Console]::Out.WriteLine([string]::Format([Globalization.CultureInfo]::InvariantCulture, '{0},{1}', ${'$'}at.Latitude, ${'$'}at.Longitude))
            """
                .trimIndent()
    }
}

/** The script's last line, `lat,lng` with `.` decimals, as a place; null for anything else (an error's text, a localized number). */
internal fun parsePowerShellOutput(output: String): GeoPoint? {
    val parts = output.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }?.split(',')
    return parts?.takeIf { it.size == 2 }?.let { (lat, lng) -> pointOrNull(lat.toDoubleOrNull(), lng.toDoubleOrNull()) }
}
