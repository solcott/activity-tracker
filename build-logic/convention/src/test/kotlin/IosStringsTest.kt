import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** The iOS app's strings against the shared UI's (ui/ios-native-shell, "Native text matches the shared text"). */
class IosStringsTest {
    private val shared =
        """
        <resources>
            <string name="login_intro">Sign in with your ATProto account.</string>
            <string name="login_left">You\'ve left. Your account %1${'$'}s is unchanged.</string>
            <string name="nav_feed">Feed</string>
            <plurals name="likes"><item quantity="one">%d like</item><item quantity="other">%d likes</item></plurals>
        </resources>
        """
            .trimIndent()

    private fun catalog(vararg entries: Pair<String, String>): String =
        entries.joinToString(prefix = """{"sourceLanguage":"en","strings":{""", postfix = """},"version":"1.0"}""") { (name, text) ->
            val value = text.replace("\\", "\\\\").replace("\"", "\\\"")
            """"$name":{"localizations":{"en":{"stringUnit":{"state":"translated","value":"$value"}}}}"""
        }

    @Test
    fun `matching strings pass, and the catalog needn't hold every shared string`() {
        assertEquals(emptyList(), iosStringFaults(shared, catalog("login_intro" to "Sign in with your ATProto account.")))
    }

    @Test
    fun `a string argument is an object in Swift`() {
        assertEquals(emptyList(), iosStringFaults(shared, catalog("login_left" to "You've left. Your account %1${'$'}@ is unchanged.")))
    }

    @Test
    fun `a reworded shared string fails naming it`() {
        val faults = iosStringFaults(shared, catalog("login_intro" to "Sign in with your account.", "nav_feed" to "Feed"))

        assertEquals(1, faults.size)
        assertContains(faults.single(), "login_intro")
        assertContains(faults.single(), "Sign in with your ATProto account.")
    }

    @Test
    fun `a string the shared UI no longer has fails naming it`() {
        val faults = iosStringFaults(shared, catalog("login_gone" to "Gone"))

        assertContains(faults.single(), "login_gone")
    }

    @Test
    fun `a Kotlin argument left as it is fails`() {
        assertEquals(1, iosStringFaults(shared, catalog("login_left" to "You've left. Your account %1${'$'}s is unchanged.")).size)
    }

    @Test
    fun `an entry with no English text fails`() {
        val json = """{"sourceLanguage":"en","strings":{"nav_feed":{}},"version":"1.0"}"""

        assertContains(iosStringFaults(shared, json).single(), "nav_feed")
    }
}
