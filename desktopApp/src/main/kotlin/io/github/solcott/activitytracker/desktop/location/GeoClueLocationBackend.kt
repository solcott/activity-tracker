package io.github.solcott.activitytracker.desktop.location

import io.github.solcott.activitytracker.location.GeoPoint
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/**
 * GeoClue 2 over the system D-Bus, which desktop Linux uses for location: a client asking for a city's accuracy, the first location it
 * reports, then stopped. A distro without GeoClue, or GeoClue with no source, gives none.
 */
class GeoClueLocationBackend(private val io: CoroutineDispatcher = Dispatchers.IO) : LocationBackend {
    override suspend fun locate(): GeoPoint? =
        runInterruptible(io) {
            DBusConnectionBuilder.forSystemBus().build().use { bus ->
                val manager = bus.getRemoteObject(SERVICE, MANAGER_PATH, GeoClueManager::class.java)
                val clientPath = manager.getClient().path
                val client = bus.getRemoteObject(SERVICE, clientPath, GeoClueClient::class.java)
                val clientProperties = bus.getRemoteObject(SERVICE, clientPath, Properties::class.java)
                clientProperties.Set(CLIENT_INTERFACE, "DesktopId", DESKTOP_ID)
                clientProperties.Set(CLIENT_INTERFACE, "RequestedAccuracyLevel", UInt32(ACCURACY_CITY))
                val updated = CompletableFuture<String>()
                val handler = bus.addSigHandler(GeoClueClient.LocationUpdated::class.java, client) { updated.complete(it.newLocation.path) }
                try {
                    client.start()
                    val locationPath = updated.get(WAIT_SECONDS, TimeUnit.SECONDS)
                    val location = bus.getRemoteObject(SERVICE, locationPath, Properties::class.java)
                    readGeoClueLocation(location.GetAll(LOCATION_INTERFACE))
                } finally {
                    handler.close()
                    stopQuietly(client)
                }
            }
        }

    /** Stops [client] after a location or a failure; a client GeoClue already dropped can't be stopped, which is fine. */
    private fun stopQuietly(client: GeoClueClient) {
        try {
            client.stop()
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // Already gone.
        }
    }

    @DBusInterfaceName("org.freedesktop.GeoClue2.Manager")
    interface GeoClueManager : DBusInterface {
        @DBusMemberName("GetClient") fun getClient(): DBusPath
    }

    @DBusInterfaceName("org.freedesktop.GeoClue2.Client")
    interface GeoClueClient : DBusInterface {
        @DBusMemberName("Start") fun start()

        @DBusMemberName("Stop") fun stop()

        /** GeoClue moved the client from its [oldLocation] object to [newLocation]. */
        @DBusMemberName("LocationUpdated")
        class LocationUpdated(path: String, val oldLocation: DBusPath, val newLocation: DBusPath) :
            DBusSignal(path, oldLocation, newLocation)
    }

    private companion object {
        const val SERVICE = "org.freedesktop.GeoClue2"
        const val MANAGER_PATH = "/org/freedesktop/GeoClue2/Manager"
        const val CLIENT_INTERFACE = "org.freedesktop.GeoClue2.Client"
        const val LOCATION_INTERFACE = "org.freedesktop.GeoClue2.Location"

        /** The app's id, as GeoClue's agent names who's asking. */
        const val DESKTOP_ID = "io.github.solcott.activitytracker"

        /** `GCLUE_ACCURACY_LEVEL_CITY`: enough to open a map, and what GeoClue grants most readily. */
        const val ACCURACY_CITY = 4L

        /** GeoClue answers in seconds once it has a source; past this it has none. */
        const val WAIT_SECONDS = 25L
    }
}

/** A GeoClue `Location` object's properties as a place: its `Latitude` and `Longitude` in degrees, or null when either is missing. */
internal fun readGeoClueLocation(properties: Map<String, Variant<*>>): GeoPoint? =
    pointOrNull(properties["Latitude"]?.value as? Double, properties["Longitude"]?.value as? Double)
