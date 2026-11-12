import kotlin.test.Test
import kotlin.test.assertEquals

class IosScreenRegistryTest {
    @Test
    fun `serializable objects and classes are screens, on one line or two`() {
        val found =
            serializableScreens(
                """
                @CircuitSerializable(SessionScope::class) data object FeedScreen : Screen

                @CircuitSerializable(SessionScope::class)
                data class SegmentScreen(val uri: String, val board: String? = null) : Screen

                @CircuitSerializable(AppScope::class) @Immutable data object LoginScreen : Screen
                """
                    .trimIndent(),
            )

        assertEquals(listOf("FeedScreen", "SegmentScreen", "LoginScreen"), found)
    }

    @Test
    fun `sub-screens, other classes and comments are not`() {
        val found =
            serializableScreens(
                """
                /** Like `@CircuitSerializable(SessionScope::class) data object OldScreen`, which was removed. */
                @Immutable data class ReportScreen(val target: ReportTarget) : SubScreen<ReportResult>
                data class FeedState(val items: List<String>) : CircuitUiState
                // @CircuitSerializable(SessionScope::class) data object CommentedScreen : Screen
                """
                    .trimIndent(),
            )

        assertEquals(emptyList(), found)
    }

    @Test
    fun `registered screens are read from register calls, not comments`() {
        val found =
            registeredScreens(
                """
                registry.register(LoginScreen.self, LoginState.self) { state in LoginView(state: state) }
                registry.register(
                    SegmentBrowseScreen.self, SegmentBrowseState.self) { state in SegmentBrowseView(state: state) }
                // registry.register(ModerationScreen.self, ModerationState.self) { state in ModerationView(state: state) }
                """
                    .trimIndent(),
            )

        assertEquals(setOf("LoginScreen", "SegmentBrowseScreen"), found)
    }
}
