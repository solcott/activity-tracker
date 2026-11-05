package io.github.solcott.activitytracker.lexicon

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Checks [AthleteSpaceDeclaration] against the space type's lexicon, so a collection added there is mirrored here too. */
class AthleteSpaceDeclarationTest {
    @Test
    fun mirrorsTheLexicon() {
        val declared =
            Json.parseToJsonElement(File("../../lexicons/app/activitytracker/space/athlete.json").readText())
                .jsonObject["defs"]!!
                .jsonObject["main"]!!
                .jsonObject["collections"]!!
                .jsonArray
                .map { it.jsonPrimitive.content }
        assertEquals(declared, AthleteSpaceDeclaration.collections)
    }
}
