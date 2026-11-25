package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentSetOf

/**
 * The page's card grid (feed/activity-page-layout): opening a strip gives it a row of its own, and the strips beside it widen to fill
 * theirs gradually rather than jumping.
 */
@OptIn(ExperimentalTestApi::class)
class CardGridMotionUiTest {
    @Test
    fun aStripsNeighboursWidenGraduallyWhenItOpens() = runComposeUiTest {
        val ride = ActivityDetailSamples.variants.getValue("ownRide").content.data as ActivityDetailPage
        val closed = ride.copy(openCards = persistentSetOf())
        val strips = cardRows(closed, columns = 3, inPane = false).first { it.strips && it.cards.size == 3 }.cards
        var page by mutableStateOf(closed)
        setContent {
            AppPreview { CardGrid(page, cardRows(page, columns = 3, inPane = false), remember { CardTops() }, Modifier.width(900.dp)) }
        }
        fun headers() = onAllNodes(hasClickAction()).fetchSemanticsNodes()
        // The middle of the first row of three strips' headers, found by its place before opening and followed by its node after.
        val neighbour = headers().groupBy { it.boundsInRoot.top }.values.first { it.size == 3 }.sortedBy { it.boundsInRoot.left }[1].id
        fun neighbourWidth() = headers().single { it.id == neighbour }.boundsInRoot.width
        val before = neighbourWidth()
        mainClock.autoAdvance = false
        page = closed.copy(openCards = persistentSetOf(strips[2].section))
        mainClock.advanceTimeBy(120)
        val during = neighbourWidth()
        mainClock.autoAdvance = true
        waitForIdle()
        val after = neighbourWidth()
        assertTrue(after > before, "a row of two is wider than a row of three: $before, then $after")
        assertTrue(during > before && during < after, "midway it is between them, not $during (from $before to $after)")
    }
}
