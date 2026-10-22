package io.github.solcott.activitytracker.segmentimporter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SettingsTest {
    private val account =
        mapOf(
            "SEGMENT_IMPORTER_IDENTIFIER" to "importer.test",
            "SEGMENT_IMPORTER_PASSWORD" to "app-password",
            "SEGMENT_IMPORTER_PDS_URL" to "http://localhost:2583/",
        )

    @Test
    fun theAppViewDefaultsToTheLocalOne() {
        val settings = Settings.fromEnv(account)
        assertEquals(
            Settings("importer.test", "app-password", "http://localhost:2583", "http://localhost:8081", "did:web:localhost%3A8081"),
            settings,
        )
        val custom =
            Settings.fromEnv(account + mapOf("APPVIEW_URL" to "https://appview.example/", "APPVIEW_DID" to "did:web:appview.example"))
        assertEquals("https://appview.example" to "did:web:appview.example", custom.appViewUrl to custom.appViewDid)
        assertFalse("app-password" in settings.toString(), "the password stays out of logs")
    }

    @Test
    fun eachMissingAccountSettingIsNamed() {
        val missing = assertFailsWith<Settings.Missing> { Settings.fromEnv(mapOf("SEGMENT_IMPORTER_PASSWORD" to " ")) }
        assertEquals(listOf("SEGMENT_IMPORTER_IDENTIFIER", "SEGMENT_IMPORTER_PASSWORD", "SEGMENT_IMPORTER_PDS_URL"), missing.names)
        val noPds = assertFailsWith<Settings.Missing> { Settings.fromEnv(account - "SEGMENT_IMPORTER_PDS_URL") }
        assertEquals(listOf("SEGMENT_IMPORTER_PDS_URL"), noPds.names)
    }
}
