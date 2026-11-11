package io.github.solcott.activitytracker.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import dev.detekt.test.utils.compileContentForTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoPairInApiTest {
    private val rule = NoPairInApi(Config.empty)

    private fun lint(code: String, set: String = "commonMain") =
        rule.lint(compileContentForTest(code.trimIndent(), Path.of("/repo/core/data/src/$set/kotlin/io/github/x/Code.kt")))

    @Test
    fun flagsPairsAndTriplesInNonPrivateSignatures() {
        val findings =
            lint(
                """
                package io.github.x

                fun ends(points: List<Pair<Double, Double>>): Int = 0
                internal fun Pair<Int, Int>.sum(): Int = first + second
                fun bounds(): Triple<Int, Int, Int>? = null
                val start: Pair<Double, Double>? = null
                typealias Move = Pair<String, Int>

                class Box(val parts: List<Pair<String, Int>>) {
                    fun pick(): kotlin.Pair<Int, Int> = 1 to 2
                    var choice: ((Pair<Int, Int>) -> Unit)? = null
                }
                """,
            )
        assertEquals(8, findings.size)
        assertTrue(findings.first().message.contains("data class"))
    }

    @Test
    fun flagsInferredPairs() {
        val findings =
            lint(
                """
                package io.github.x

                fun ends() = 1 to 2
                val corner = Pair(0.0, 0.0)
                val moves = mutableListOf<Triple<Int, Int, Int>>()
                class Box {
                    val size get() = 3 to 4
                }
                """,
            )
        assertEquals(4, findings.size)
    }

    @Test
    fun allowsPrivateLocalAndOverridingDeclarations() {
        val findings =
            lint(
                """
                package io.github.x

                private fun ends(points: List<Pair<Double, Double>>) = points.first()
                private val start = 0.0 to 0.0
                private typealias Move = Pair<String, Int>

                class Box private constructor(private val parts: List<Pair<String, Int>>) {
                    private fun pick(): Pair<Int, Int> = 1 to 2
                    override fun toString(): String {
                        val local: Pair<Int, Int> = 1 to 2
                        fun inner(): Pair<Int, Int> = local
                        return inner().toString()
                    }
                    override val size: Pair<Int, Int> get() = 1 to 2
                }

                private class Hidden(val parts: List<Pair<String, Int>>) {
                    fun pick(): Pair<Int, Int> = 1 to 2
                    class Nested { val corner = 0 to 0 }
                }

                fun run() {
                    val listener = object { fun pick(): Pair<Int, Int> = 1 to 2 }
                }

                fun repair(): Repair = Repair()
                val total = mutableListOf<Int>()
                """,
            )
        assertEquals(emptyList(), findings)
    }

    @Test
    fun tellsTestSourcesApart() {
        assertTrue(isTestSource("/repo/core/data/src/commonTest/kotlin/io/github/x/Code.kt"))
        assertTrue(isTestSource("/repo/server/appview/src/test/kotlin/Code.kt"))
        assertTrue(isTestSource("/repo/androidApp/src/screenshotTestDefaultDebug/kotlin/Code.kt"))
        assertTrue(isTestSource("C:\\repo\\core\\data\\src\\jvmTest\\kotlin\\Code.kt"))
        assertFalse(isTestSource("/repo/core/data/src/commonMain/kotlin/io/github/x/Code.kt"))
        assertTrue(isTestSource("/repo/app/common/presenter-testing/src/jvmMain/kotlin/Code.kt"))
        assertFalse(isTestSource("/repo/app/common/presenter/src/jvmMain/kotlin/Code.kt"))
        assertFalse(isTestSource("/Code.kt"))
    }
}
