package io.github.solcott.activitytracker.lexicon

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Checks [AppCollections] against the lexicon files, so a record type added there is exported too. */
class AppCollectionsTest {
    @Test
    fun mirrorsTheRecordLexicons() {
        val records =
            File("../../lexicons/app/activitytracker")
                .walkTopDown()
                .filter { it.isFile && it.extension == "json" }
                .map { Json.parseToJsonElement(it.readText()).jsonObject }
                .filter { it["defs"]?.jsonObject?.get("main")?.jsonObject?.get("type")?.jsonPrimitive?.content == "record" }
                .map { it["id"]!!.jsonPrimitive.content }
                .sorted()
                .toList()
        assertEquals(records, AppCollections.records)
    }

    @Test
    fun coversEverySpaceCollection() {
        assertTrue(AppCollections.records.containsAll(AthleteSpaceDeclaration.collections))
    }
}
