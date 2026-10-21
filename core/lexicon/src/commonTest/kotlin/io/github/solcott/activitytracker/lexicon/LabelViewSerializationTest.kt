package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.LabelView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `app.activitytracker.moderation.defs#label` on views: `val` on the wire, absent when there are none. */
class LabelViewSerializationTest {
    @Test
    fun labelsRoundTripWithValAsTheirValue() {
        val view = ProfileViewBasic(Did("did:plc:bob"), Handle("bob.test"), labels = listOf(LabelView(Did("did:plc:labeler"), "spam")))
        val json = AtJson.encodeToJsonElement(ProfileViewBasic.serializer(), view).jsonObject
        val label = json.getValue("labels").jsonArray.single().jsonObject
        assertEquals("spam", label["val"]?.jsonPrimitive?.content)
        assertEquals("did:plc:labeler", label["src"]?.jsonPrimitive?.content)
        assertEquals(view, AtJson.decodeFromJsonElement(ProfileViewBasic.serializer(), json))
    }

    @Test
    fun noLabelsIsNoField() {
        val json = AtJson.encodeToJsonElement(ProfileViewBasic.serializer(), ProfileViewBasic(Did("did:plc:bob"), Handle("bob.test")))
        assertEquals(null, json.jsonObject["labels"])
    }
}
