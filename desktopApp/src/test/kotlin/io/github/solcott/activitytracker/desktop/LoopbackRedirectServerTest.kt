package io.github.solcott.activitytracker.desktop

import io.github.solcott.activitytracker.data.auth.parseRedirectParams
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class LoopbackRedirectServerTest {
    @Test
    fun successSaysSignedIn() {
        val page = redirectPage(parseRedirectParams("http://127.0.0.1/callback?code=abc&state=xyz&iss=https%3A%2F%2Fpds.test"))

        assertContains(page, "Signed in.")
        assertFalse("failed" in page)
    }

    @Test
    fun errorShowsTheDescription() {
        val page =
            redirectPage(
                parseRedirectParams(
                    "http://127.0.0.1/callback?error=access_denied&error_description=This+request+was+initiated+from+another+device"
                )
            )

        assertContains(page, "Sign-in failed: This request was initiated from another device.")
        assertFalse("Signed in" in page)
    }

    @Test
    fun errorWithoutDescriptionShowsTheCode() {
        val page = redirectPage(mapOf("error" to "access_denied"))

        assertContains(page, "Sign-in failed: access_denied.")
    }

    @Test
    fun errorDescriptionIsHtmlEscaped() {
        val page = redirectPage(mapOf("error" to "x", "error_description" to "<script>alert('&\"')</script>"))

        assertContains(page, "&lt;script&gt;alert(&#39;&amp;&quot;&#39;)&lt;/script&gt;")
        assertFalse("<script>" in page)
    }
}
