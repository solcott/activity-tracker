package io.github.solcott.activitytracker.app.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The iOS app's rating colours (`TrailRating0Color`… in its asset catalog, segments/trail-ratings) hold [TrailRatingColors]' values, light
 * and dark, since the two are kept by hand.
 */
class TrailRatingCatalogTest {
    private val catalog = File(System.getProperty("user.dir"), "../../iosApp/iosApp/Assets.xcassets")

    /** The colour set's universal and dark colours, as `RRGGBB`. */
    private fun colorSet(name: String): Pair<String, String> {
        val json = File(catalog, "$name.colorset/Contents.json").readText()
        val components = Regex(""""red" : "0x(..)",\s*"green" : "0x(..)",\s*"blue" : "0x(..)"""")
        val hex = components.findAll(json).map { it.groupValues.drop(1).joinToString("") }.toList()
        assertEquals(2, hex.size, "$name has a light and a dark colour")
        return hex[0] to hex[1]
    }

    private fun Color.hex() = "%06X".format(java.util.Locale.ROOT, toArgb() and 0xFFFFFF)

    @Test
    fun theCatalogMatchesThePalette() {
        for (level in 0..4) {
            assertEquals(
                TrailRatingColors.ON_LIGHT.line(level).hex() to TrailRatingColors.ON_DARK.line(level).hex(),
                colorSet("TrailRating${level}Color"),
                "level $level",
            )
        }
        assertEquals(
            TrailRatingColors.ON_LIGHT.unrated.hex() to TrailRatingColors.ON_DARK.unrated.hex(),
            colorSet("TrailUnratedColor"),
        )
        assertEquals(
            TrailRatingColors.ON_LIGHT.casing(0).hex() to TrailRatingColors.ON_DARK.casing(0).hex(),
            colorSet("TrailRatingCasing0Color"),
        )
    }
}
