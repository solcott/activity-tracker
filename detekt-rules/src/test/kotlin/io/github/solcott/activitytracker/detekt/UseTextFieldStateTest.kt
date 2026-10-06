package io.github.solcott.activitytracker.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class UseTextFieldStateTest {
    private val rule = UseTextFieldState(Config.empty)

    @Test
    fun flagsTheValueOverloads() {
        val code =
            """
            fun ui() {
                OutlinedTextField(value = title, onValueChange = { title = it })
                TextField(title, { title = it }, label = {})
                BasicTextField(value = title, onValueChange = onChange) { inner -> inner() }
                OutlinedTextField(query, onChange, modifier = Modifier)
            }
            """
                .trimIndent()
        assertEquals(4, rule.lint(code).size)
    }

    @Test
    fun allowsTheStateOverloads() {
        val code =
            """
            fun ui() {
                OutlinedTextField(state.title, label = {}, lineLimits = TextFieldLineLimits.SingleLine)
                TextField(state = query, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(query, Modifier.fillMaxWidth())
                BasicTextField(rememberTextFieldState())
                LimitedTextField(value = name, onValueChange = onChange)
            }
            """
                .trimIndent()
        assertEquals(emptyList(), rule.lint(code))
    }
}
