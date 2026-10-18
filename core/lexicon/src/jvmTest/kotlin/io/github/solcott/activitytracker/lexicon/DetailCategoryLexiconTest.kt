package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.DetailVisibilities
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Checks [DetailCategory] against the lexicon JSON itself, so a channel added there gets a deliberate category. */
class DetailCategoryLexiconTest {
    private fun lexicon(path: String) = Json.parseToJsonElement(File("../../lexicons/app/activitytracker/$path").readText()).jsonObject

    @Test
    fun everyKnownChannelHasOneCategoryOrIsCore() {
        val known =
            lexicon("feed/activityStreams.json")["defs"]!!
                .jsonObject["main"]!!
                .jsonObject["record"]!!
                .jsonObject["properties"]!!
                .jsonObject["channels"]!!
                .jsonObject["items"]!!
                .jsonObject["knownValues"]!!
                .jsonArray
                .map { it.jsonPrimitive.content }
        val covered = DetailCategory.CHANNELS.values.flatten().toSet() + DetailCategory.CORE_CHANNELS
        assertEquals(emptySet(), known.toSet() - covered, "channels in neither a category nor CORE")
    }

    @Test
    fun defaultsListEveryCategory() {
        val props = lexicon("actor/activityDefaults.json")["defs"]!!.jsonObject["details"]!!.jsonObject["properties"]!!.jsonObject
        assertEquals(DetailCategory.all.toSet(), props.keys)
        val names = DetailVisibilities.serializer().descriptor.let { d -> (0 until d.elementsCount).map { d.getElementName(it) } }
        assertEquals(DetailCategory.all.toSet(), names.toSet())
    }
}
