package io.github.solcott.activitytracker.app.links

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Android claims https URLs on the link host only for the first segments that are links (ui/deep-links "Android verifies only its own
 * paths"): the manifest's path prefixes are [AppLinkRoutes.topLevelPrefixes], no more, so `/oauth` and `/xrpc` still open in the browser.
 */
class ManifestLinksTest {
    private val manifest =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
            .resolve("androidApp/src/main/AndroidManifest.xml")
            .readText()

    @Test
    fun theHttpsFilterClaimsExactlyTheLinksPrefixes() {
        val prefixes = Regex("""android:pathPrefix="/([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(AppLinkRoutes.topLevelPrefixes.sorted(), prefixes.sorted())
    }

    @Test
    fun theAppsOwnSchemeIsClaimed() {
        assertEquals(true, """android:scheme="${AppLinks.SCHEME}"""" in manifest)
    }
}
