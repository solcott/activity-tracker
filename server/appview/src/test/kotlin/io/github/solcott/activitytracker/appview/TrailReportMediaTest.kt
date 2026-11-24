package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.LabelValues
import io.github.solcott.activitytracker.appview.api.PdsLookup
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/** Trail report photos through `/img/media` (segments/trail-reports): redirected while served, never under `!takedown`. */
class TrailReportMediaTest : TrailReportTestBase() {
    @Test
    fun servesATrailPhotoAndRefusesATakenDownOne() = testApplication {
        application { appView(config, db, verifier, PdsLookup { "http://pds.test" }) }
        activeSegment()
        effort(alice)
        report("3kr", photos = listOf(photoCid(1)))
        val noRedirects = createClient { followRedirects = false }

        val redirect = noRedirects.get("/img/media/$alice/${photoCid(1)}")
        assertEquals(HttpStatusCode.Found, redirect.status)
        assertEquals(
            "http://pds.test/xrpc/com.atproto.sync.getBlob?did=did%3Aplc%3Aalice&cid=${photoCid(1)}",
            redirect.headers[HttpHeaders.Location],
        )
        assertEquals(HttpStatusCode.NotFound, noRedirects.get("/img/media/$alice/bafkunknown").status, "an unknown CID")
        assertEquals(HttpStatusCode.NotFound, noRedirects.get("/img/media/$bob/${photoCid(1)}").status, "another athlete's")

        label(reportUri("3kr"), LabelValues.TAKEDOWN)
        assertEquals(HttpStatusCode.NotFound, noRedirects.get("/img/media/$alice/${photoCid(1)}").status, "a taken-down report's photo")
    }

    @Test
    fun aTakenDownAuthorsTrailPhotosAreRefused() = testApplication {
        application { appView(config, db, verifier, PdsLookup { "http://pds.test" }) }
        activeSegment()
        effort(alice)
        report("3kr", photos = listOf(photoCid(1)))
        val noRedirects = createClient { followRedirects = false }
        // The first request starts the app, which sets its trusted labelers, so the label goes after it.
        assertEquals(HttpStatusCode.Found, noRedirects.get("/img/media/$alice/${photoCid(1)}").status)
        label(alice, LabelValues.TAKEDOWN)
        assertEquals(HttpStatusCode.NotFound, noRedirects.get("/img/media/$alice/${photoCid(1)}").status)
    }
}
