package io.github.solcott.activitytracker.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import dev.detekt.test.utils.compileContentForTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UseSingleFlightTest {
    private val rule = UseSingleFlight(Config.empty)

    private val bareScope =
        """
        fun XPresenter(): XState {
            val scope = rememberCoroutineScope()
            return XState { scope.launch { save() } }
        }
        """
            .trimIndent()

    @Test
    fun flagsABareCoroutineScopeInAPresenter() {
        assertEquals(1, rule.lint(compileContentForTest(bareScope, "XPresenter.kt")).size)
    }

    @Test
    fun allowsSingleFlight() {
        val code =
            """
            fun XPresenter(): XState {
                val flights = rememberSingleFlight()
                LaunchedEffect(Unit) { load() }
                return XState(saving = flights.isRunning(XEvent.Save)) { flights.launch(XEvent.Save) { save() } }
            }
            """
                .trimIndent()
        assertEquals(emptyList(), rule.lint(compileContentForTest(code, "XPresenter.kt")))
    }

    @Test
    fun ignoresFilesThatArentPresenters() {
        assertEquals(emptyList(), rule.lint(compileContentForTest(bareScope, "XUi.kt")))
    }
}
