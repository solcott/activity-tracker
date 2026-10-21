package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.atproto.CreateReport
import io.github.solcott.activitytracker.lexicon.atproto.ReasonType
import io.github.solcott.activitytracker.lexicon.atproto.ReportSubject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `com.atproto.moderation.createReport`: its subject union round-trips by `$type`. */
class CreateReportSerializationTest {
    private val activity = AtUri("at://did:plc:bob/app.activitytracker.feed.activity/3k")

    @Test
    fun recordSubjectRoundTripsAsAStrongRef() {
        val input = CreateReport.Input(ReasonType.RUDE, ReportSubject.Record(activity, Cid("bafyrei1")), "photo 3")
        val json = AtJson.encodeToJsonElement(CreateReport.Input.serializer(), input).jsonObject
        val subject = json.getValue("subject").jsonObject
        assertEquals("com.atproto.repo.strongRef", subject["\$type"]?.jsonPrimitive?.content)
        assertEquals(activity.value, subject["uri"]?.jsonPrimitive?.content)
        assertEquals("com.atproto.moderation.defs#reasonRude", json["reasonType"]?.jsonPrimitive?.content)
        assertEquals(input, AtJson.decodeFromJsonElement(CreateReport.Input.serializer(), json))
    }

    @Test
    fun accountSubjectRoundTripsAsARepoRef() {
        val input = CreateReport.Input(ReasonType.SPAM, ReportSubject.Repo(Did("did:plc:bob")))
        val json = AtJson.encodeToJsonElement(CreateReport.Input.serializer(), input).jsonObject
        val subject = json.getValue("subject").jsonObject
        assertEquals("com.atproto.admin.defs#repoRef", subject["\$type"]?.jsonPrimitive?.content)
        assertEquals(null, json["reason"])
        assertEquals(input, AtJson.decodeFromJsonElement(CreateReport.Input.serializer(), json))
    }

    @Test
    fun decodesTheLabelersAnswer() {
        val output =
            AtJson.decodeFromString(
                CreateReport.Output.serializer(),
                """{"id":7,"reasonType":"com.atproto.moderation.defs#reasonSpam","reportedBy":"did:plc:alice",
                  "createdAt":"2026-10-20T10:00:00.000Z","subject":{"${'$'}type":"com.atproto.admin.defs#repoRef","did":"did:plc:bob"},
                  "extra":true}""",
            )
        assertEquals(7, output.id)
        assertEquals(ReportSubject.Repo(Did("did:plc:bob")), output.subject)
    }

    @Test
    fun unknownSubjectTypeFails() {
        assertFailsWith<SerializationException> {
            AtJson.decodeFromString(ReportSubject.serializer(), """{"${'$'}type":"com.example.thing","id":1}""")
        }
    }
}
