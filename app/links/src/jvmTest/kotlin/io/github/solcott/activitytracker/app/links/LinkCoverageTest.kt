package io.github.solcott.activitytracker.app.links

import io.github.solcott.activitytracker.app.divesite.DiveSiteBrowseScreen
import io.github.solcott.activitytracker.app.login.LoginScreen
import io.github.solcott.activitytracker.app.segment.SegmentBrowseScreen
import io.github.solcott.activitytracker.app.settings.LeaveScreen
import io.github.solcott.activitytracker.app.settings.LeftElsewhereScreen
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every screen a navigator can push has a link or is named as having none (ui/deep-links "Pages not reached by link are named"): a new
 * `@CircuitSerializable` screen in a contract module fails here until it gets a route and a sample in [linkSamples], or joins
 * [AppLinkRoutes.excluded]. Reads the contracts' sources, as `checkIosScreenRegistry` does, so it needs no graph.
 */
class LinkCoverageTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }

    private val declared: Set<String> =
        sequenceOf("feature", "app/common/api")
            .map { File(root, it) }
            .flatMap { it.walkTopDown().onEnter { dir -> dir.name != "build" } }
            .filter { it.isFile && it.extension == "kt" && "/api/src/commonMain/" in it.invariantSeparatorsPath }
            .flatMap { file -> SCREEN.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()

    @Test
    fun everyScreenHasALinkOrIsExcluded() {
        assertTrue(declared.size > 40, "found the contracts' screens: $declared")
        val excluded = AppLinkRoutes.excluded.map { it.simpleName!! }.toSet()
        val missing = declared - linkSamples.keys - excluded
        assertTrue(missing.isEmpty(), "screens with no link and no exclusion (add a route and a LinkSample, or exclude): $missing")
    }

    @Test
    fun noSampleOrExclusionIsStale() {
        val excluded = AppLinkRoutes.excluded.map { it.simpleName!! }.toSet()
        assertEquals(emptySet(), (linkSamples.keys + excluded) - declared, "samples or exclusions naming no contract screen")
        assertEquals(emptySet(), linkSamples.keys.intersect(excluded), "excluded screens with a sample")
    }

    @Test
    fun excludedScreensHaveNoLink() {
        assertEquals(null, AppLinkRoutes.linkOf(LoginScreen))
        assertEquals(null, AppLinkRoutes.linkOf(LeaveScreen(resume = true)))
        assertEquals(null, AppLinkRoutes.linkOf(LeftElsewhereScreen))
        assertEquals(null, AppLinkRoutes.linkOf(SegmentBrowseScreen))
        assertEquals(null, AppLinkRoutes.linkOf(DiveSiteBrowseScreen))
    }

    private companion object {
        // As checkIosScreenRegistry reads them (build-logic IosScreenRegistry.kt).
        val SCREEN = Regex("""@CircuitSerializable\b(?:\([^)]*\))?\s*(?:@\w+(?:\([^)]*\))?\s*)*data\s+(?:object|class)\s+(\w+)""")
    }
}
