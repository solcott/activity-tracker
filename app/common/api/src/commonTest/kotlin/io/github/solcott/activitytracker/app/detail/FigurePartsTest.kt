package io.github.solcott.activitytracker.app.detail

import kotlin.test.Test
import kotlin.test.assertEquals

class FigurePartsTest {
    @Test fun aSwimPaceBreaksOnlyBeforeItsUnit() = assertEquals("1:59 /\u2060100\u00A0yd", FigureParts.unbreakable("1:59 /100 yd"))

    @Test fun aDistanceKeepsItsOneSpaceBreakable() = assertEquals("7.83 mi", FigureParts.unbreakable("7.83 mi"))

    @Test fun aTimeHasNothingToBreak() = assertEquals("2:16:25", FigureParts.unbreakable("2:16:25"))

    @Test
    fun aDistanceIsANumberThenItsUnit() =
        assertEquals(listOf(FigurePart("7.83", isNumber = true), FigurePart(" mi", isNumber = false)), FigureParts.of("7.83 mi"))

    @Test fun aClockTimeIsOneNumber() = assertEquals(listOf(FigurePart("1:02:03", isNumber = true)), FigureParts.of("1:02:03"))

    @Test
    fun aGroupedNumberIsOneRun() =
        assertEquals(listOf(FigurePart("1,959", isNumber = true), FigurePart(" ft", isNumber = false)), FigureParts.of("1,959 ft"))

    @Test
    fun aSwimPacesUnitIsOneUnbreakableRun() =
        assertEquals(
            listOf(FigurePart("1:59", true), FigurePart(" /\u2060", false), FigurePart("100", true), FigurePart("\u00A0yd", false)),
            FigureParts.of("1:59 /100 yd"),
        )

    @Test
    fun aNameWithoutANumberIsOneRun() = assertEquals(listOf(FigurePart("Road\u00A0bike", isNumber = false)), FigureParts.of("Road bike"))

    @Test fun aNameWithANumberStillSplits() = assertEquals(true, FigureParts.of("Edge 840").last().isNumber)
}
