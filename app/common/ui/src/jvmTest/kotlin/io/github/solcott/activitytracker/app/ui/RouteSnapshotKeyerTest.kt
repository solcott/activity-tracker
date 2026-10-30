package io.github.solcott.activitytracker.app.ui

import coil3.PlatformContext
import coil3.request.Options
import coil3.size.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

class RouteSnapshotKeyerTest {
    private val snapshot = RouteSnapshot("_p~iF~ps|U_ulLnnqC", gaps = null, dark = false, routeColor = 0xFF00897B.toInt(), density = 2f)
    private val size = Size(600, 400)

    private fun key(data: RouteSnapshot = snapshot, at: Size = size) = RouteSnapshotKeyer().key(data, Options(PlatformContext.INSTANCE, at))

    @Test
    fun equalInputsShareAKey() {
        assertEquals(key(), key(snapshot.copy()))
        assertEquals(key(), snapshot.cacheKey(size))
    }

    @Test
    fun eachInputChangesTheKey() {
        val variants =
            listOf(
                key(snapshot.copy(polyline = "_p~iF~ps|U_ulLnnqC_mqNvxq`@")),
                key(snapshot.copy(gaps = persistentListOf(1))),
                key(snapshot.copy(dark = true)),
                key(snapshot.copy(routeColor = 0xFFFF0000.toInt())),
                key(snapshot.copy(density = 3f)),
                key(at = Size(600, 300)),
            )
        assertEquals(variants.size + 1, (variants + key()).toSet().size)
    }

    @Test
    fun theKeyNamesTheStyleAndDrawingVersion() {
        val key = key()
        assert(MapStyles.LIGHT in key) { key }
        assert("v$ROUTE_IMAGE_VERSION:" in key) { key }
    }
}
