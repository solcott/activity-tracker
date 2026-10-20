package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.sp
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.like_count
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.pluralStringResource

class PageFiguresTest {
    private val locale = Locale.getDefault()

    @BeforeTest fun useUsEnglish() = Locale.setDefault(Locale.US)

    @AfterTest fun restoreLocale() = Locale.setDefault(locale)

    @Test fun `a swim pace breaks only before its unit`() = assertEquals("1:59 /⁠100 yd", unbreakableUnit("1:59 /100 yd"))

    @Test fun `a distance keeps its one space breakable`() = assertEquals("7.83 mi", unbreakableUnit("7.83 mi"))

    @Test fun `a time has nothing to break`() = assertEquals("2:16:25", unbreakableUnit("2:16:25"))

    @Test fun `a count is grouped`() = assertEquals("6,676", text(FigureValue.Count(6676)))

    @Test fun `a quantity is grouped`() = assertEquals("1,000 W", text(FigureValue.Quantity(1000, FigureUnit.Watts)))

    @Test fun `an elevation is grouped`() = assertEquals("1,959 ft", text(FigureValue.Elevation(597.1)))

    @Test fun `a negative elevation keeps its sign before the groups`() = assertEquals("-1,234 ft", text(FigureValue.Elevation(-376.12)))

    @Test fun `a temperature is grouped`() = assertEquals("1,000 °C", text(FigureValue.Temperature(10_000), UnitSystem.Metric))

    @Test fun `a distance is grouped`() = assertEquals("1,013.94 mi", text(FigureValue.Distance(1_631_771.0)))

    @Test fun `a decimal is grouped`() = assertEquals("1,234.5", text(FigureValue.Decimal(1234.5, 1)))

    @Test fun `a stream value is grouped`() = assertEquals("1,234 W", text(FigureValue.Stream(StreamKind.Power, 1234.0)))

    @Test fun `each side is grouped`() = assertContains(text(FigureValue.LeftRight(StreamKind.Power, 1200.0, 1300.0)), "1,300 W")

    @Test fun `a duration is not grouped`() = assertEquals("10:05:03", text(FigureValue.Duration(36_303)))

    @Test
    fun `a grouped number keeps one style`() {
        val styled = withSmallUnits("1,959 ft", SpanStyle(fontSize = 10.sp))
        assertEquals(listOf(" ft"), styled.spanStyles.map { styled.text.substring(it.start, it.end) })
    }

    @Test
    fun `a plural count takes grouped text`() =
        assertEquals("1,234 likes", rendered { pluralStringResource(Res.plurals.like_count, 1234, formatWhole(1234L)) })

    /** [value] as the page writes it, in [units]. */
    private fun text(value: FigureValue, units: UnitSystem = UnitSystem.Imperial): String = rendered(units) { figureText(value) }

    /** The text [content] composes to, in [units]. */
    private fun rendered(units: UnitSystem = UnitSystem.Imperial, content: @Composable () -> String): String {
        var text = ""
        val scene = ImageComposeScene(1, 1) { CompositionLocalProvider(LocalUnitSystem provides units) { text = content() } }
        try {
            scene.render()
        } finally {
            scene.close()
        }
        return text
    }
}
