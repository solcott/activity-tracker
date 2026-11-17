package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.lexicon.AtJson
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database

/** The files Android App Links and iOS Universal Links verify the app's links against (ui/deep-links), served only once configured. */
class AppLinkFilesTest {
    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier("did:web:localhost%3A8081", keys = { null })

    /** ui/deep-links "The AppView proves the app's links": the association files, only once configured. */
    @Test
    fun servesTheAppLinkAssociationFilesOnlyOnceConfigured() = testApplication {
        val linked =
            AppViewConfig.fromEnv(
                mapOf(
                    "ANDROID_CERT_SHA256" to "AB:CD, EF:01",
                    "APPLE_APP_IDS" to "TEAM123.io.github.solcott.activitytracker",
                ),
            )
        application { appView(linked, db, verifier) }
        val noRedirects = createClient { followRedirects = false }

        val android = noRedirects.get("/.well-known/assetlinks.json")
        assertEquals(HttpStatusCode.OK, android.status)
        assertEquals(true, android.contentType()?.match(ContentType.Application.Json))
        val target = (AtJson.parseToJsonElement(android.bodyAsText()) as JsonArray).single().jsonObject
        assertEquals("delegate_permission/common.handle_all_urls", target.getValue("relation").jsonArray.single().jsonPrimitive.content)
        val app = target.getValue("target").jsonObject
        assertEquals("android_app", app.getValue("namespace").jsonPrimitive.content)
        assertEquals("io.github.solcott.activitytracker", app.getValue("package_name").jsonPrimitive.content)
        assertEquals(listOf("AB:CD", "EF:01"), app.getValue("sha256_cert_fingerprints").jsonArray.map { it.jsonPrimitive.content })

        val apple = noRedirects.get("/.well-known/apple-app-site-association")
        assertEquals(HttpStatusCode.OK, apple.status)
        assertEquals(true, apple.contentType()?.match(ContentType.Application.Json))
        val details =
            AtJson.parseToJsonElement(apple.bodyAsText()).jsonObject.getValue("applinks").jsonObject.getValue("details").jsonArray.single()
        assertEquals(
            listOf("TEAM123.io.github.solcott.activitytracker"),
            details.jsonObject.getValue("appIDs").jsonArray.map { it.jsonPrimitive.content },
        )
        val components = details.jsonObject.getValue("components").jsonArray.map { it.jsonObject }
        val excluded = components.filter { it["exclude"]?.jsonPrimitive?.content == "true" }.map { it.getValue("/").jsonPrimitive.content }
        assertEquals(true, excluded.containsAll(listOf("/oauth/*", "/xrpc/*", "/.well-known/*")))
        assertEquals("/*", components.last().getValue("/").jsonPrimitive.content, "everything else is the app's")
    }

    @Test
    fun answersNotFoundForUnconfiguredAppLinkFiles() = testApplication {
        application { appView(config, db, verifier) }
        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/assetlinks.json").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/.well-known/apple-app-site-association").status)
    }
}
