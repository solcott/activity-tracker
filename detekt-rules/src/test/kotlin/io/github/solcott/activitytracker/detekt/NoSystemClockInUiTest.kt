package io.github.solcott.activitytracker.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import dev.detekt.test.utils.compileContentForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoSystemClockInUiTest {
    private val rule = NoSystemClockInUi(Config.empty)

    @Test
    fun flagsTheSystemClockAndTimeZoneInAUi() {
        val code =
            """
            package io.github.solcott.activitytracker.app.profile

            fun ProfileUi() {
                val now = remember { Clock.System.now() }
                val later = kotlin.time.Clock.System.now()
                val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
            }
            """
                .trimIndent()
        val findings = rule.lint(compileContentForTest(code, "ProfileUi.kt"))
        assertEquals(3, findings.size)
        assertTrue(findings.first().message.contains("LocalClock"))
        assertTrue(findings.last().message.contains("LocalTimeZone"))
    }

    @Test
    fun allowsTheLocals() {
        val code =
            """
            package io.github.solcott.activitytracker.app.profile

            fun ProfileUi() {
                val clock = LocalClock.current
                val now = remember { clock.now() }
                val local = now.toLocalDateTime(LocalTimeZone.current)
                val utc = TimeZone.UTC
            }
            """
                .trimIndent()
        assertEquals(emptyList(), rule.lint(compileContentForTest(code, "ProfileUi.kt")))
    }

    @Test
    fun ignoresFilesThatArentUis() {
        val code =
            """
            package io.github.solcott.activitytracker.app.ui

            val LocalClock = staticCompositionLocalOf<Clock> { Clock.System }
            val LocalTimeZone = staticCompositionLocalOf { TimeZone.currentSystemDefault() }
            """
                .trimIndent()
        assertEquals(emptyList(), rule.lint(compileContentForTest(code, "Format.kt")))
    }
}
