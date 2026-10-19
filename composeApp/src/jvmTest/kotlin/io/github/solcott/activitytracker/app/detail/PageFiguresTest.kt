package io.github.solcott.activitytracker.app.detail

import kotlin.test.Test
import kotlin.test.assertEquals

class PageFiguresTest {
    @Test fun `a swim pace breaks only before its unit`() = assertEquals("1:59 /\u2060100\u00A0yd", unbreakableUnit("1:59 /100 yd"))

    @Test fun `a distance keeps its one space breakable`() = assertEquals("7.83 mi", unbreakableUnit("7.83 mi"))

    @Test fun `a time has nothing to break`() = assertEquals("2:16:25", unbreakableUnit("2:16:25"))
}
