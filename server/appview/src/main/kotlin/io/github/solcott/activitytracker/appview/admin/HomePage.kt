package io.github.solcott.activitytracker.appview.admin

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span

/**
 * Home (admin/admin-site): every page's summary tiles, those needing attention first (`bad`, then `warn`, each in navigation order), then
 * the rest, redrawn every 30 seconds from `/admin/tiles`. It knows no page: each page's tiles come from its own [AdminPage.summary].
 */
internal class HomePage(private val summaries: Summaries) : AdminPage {
    override val path = ""
    override val title = "Home"

    override fun privacyProbes(subject: String) = listOf(path, TILES, "nav")

    override fun AdminRoutes.install() {
        get(path) {
            val read = summaries.read()
            page(title) { tiles(read) }
        }
        get(TILES) {
            val read = summaries.read()
            fragment { tiles(read) }
        }
    }

    private fun FlowContent.tiles(read: List<PageSummary>) {
        div {
            attributes["id"] = "home-tiles"
            attributes["hx-get"] = "/admin/$TILES"
            attributes["hx-trigger"] = "every ${REFRESH_SECONDS}s"
            attributes["hx-select"] = "#home-tiles"
            attributes["hx-swap"] = "outerHTML"
            val placed = placedTiles(read)
            val attention = placed.filter { it.tile.status == TileStatus.Bad || it.tile.status == TileStatus.Warn }
            section {
                h2 { +"Needs attention" }
                if (attention.isEmpty()) p("all-clear") { +"Nothing needs attention." } else tileGrid(attention)
            }
            section {
                h2 { +"Everything else" }
                tileGrid(placed - attention.toSet())
            }
        }
    }

    private fun FlowContent.tileGrid(tiles: List<PlacedTile>) {
        div("tiles") {
            tiles.forEach { (page, tile) ->
                a(href = tile.href, classes = "tile ${tile.status.css}") {
                    span("page") { +page }
                    span("label") { +tile.label }
                    span("value") { +tile.value }
                    tile.status?.let { badge(it.name.lowercase(), it) }
                }
            }
        }
    }

    companion object {
        const val TILES = "tiles"
        const val REFRESH_SECONDS = 30
    }
}

/** A tile with the title of the page it summarises. */
internal data class PlacedTile(val page: String, val tile: Tile)

/**
 * Every page's tiles in Home's order: `bad`, then `warn`, then the rest, keeping navigation order within each; a page whose summary
 * couldn't be read gives one tile saying so, with no status.
 */
internal fun placedTiles(read: List<PageSummary>): List<PlacedTile> {
    val all = read.flatMap { summary ->
        val tiles = summary.tiles ?: listOf(Tile("Summary", "Unavailable", null, "/admin/${summary.page.path}".trimEnd('/')))
        tiles.map { PlacedTile(summary.page.title, it) }
    }
    fun rank(status: TileStatus?) =
        when (status) {
            TileStatus.Bad -> 0
            TileStatus.Warn -> 1
            else -> 2
        }
    return all.sortedBy { rank(it.tile.status) }
}
