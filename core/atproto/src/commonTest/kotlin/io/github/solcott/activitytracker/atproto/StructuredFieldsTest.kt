package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.space.SfBare
import io.github.solcott.activitytracker.atproto.space.SfInnerList
import io.github.solcott.activitytracker.atproto.space.SfItem
import io.github.solcott.activitytracker.atproto.space.StructuredFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** RFC 8941 dictionaries as RFC 9421 `sf` components need them: parsed, then serialized canonically. */
class StructuredFieldsTest {
    private fun canonical(value: String) = StructuredFields.serializeDictionary(StructuredFields.parseDictionary(value))

    @Test
    fun serializesCanonically() {
        assertEquals("a=1, b=2;x=?0, c", canonical(" a=1,b=2;x=?0 ,\tc "))
        assertEquals(
            """s="q\"u\\o", t=tok/en:x, d=:AAECAw==:, n=-12, f=1.5, z=0.25""",
            canonical("""s="q\"u\\o",t=tok/en:x,d=:AAECAw:,n=-12,f=1.50,z=0.250"""),
        )
        assertEquals("""l=("a" "b";sf);k="v", e=()""", canonical("""l=(  "a"   "b";sf );k="v", e=()"""))
        // A later duplicate overwrites the earlier value in its place.
        assertEquals("a=3, b=2", canonical("a=1, b=2, a=3"))
    }

    @Test
    fun parsesMembers() {
        val members = StructuredFields.parseDictionary("""mac=("atproto-space-commit";sf);keyid="did:key:z";tag="t", flag;p=1""")
        assertEquals(
            SfInnerList(
                listOf(SfItem(SfBare.Str("atproto-space-commit"), mapOf("sf" to SfBare.Bool(true)))),
                mapOf("keyid" to SfBare.Str("did:key:z"), "tag" to SfBare.Str("t")),
            ),
            members["mac"],
        )
        assertEquals(SfItem(SfBare.Bool(true), mapOf("p" to SfBare.Integer(1))), members["flag"])
    }

    @Test
    fun rejectsMalformed() {
        for (value in
            listOf(
                "a=1,",
                "A=1",
                "a=\"unterminated",
                "a=\"tab\there\"",
                "a=:not base64!:",
                "a=1234567890123456",
                "a=1.2345",
                "a=(1 2",
                "a=(1 2)x",
                "a=?2",
                "a=1 b=2",
                "a=\"\\x\"",
            )) {
            assertFailsWith<IllegalArgumentException>(value) { StructuredFields.parseDictionary(value) }
        }
    }
}
