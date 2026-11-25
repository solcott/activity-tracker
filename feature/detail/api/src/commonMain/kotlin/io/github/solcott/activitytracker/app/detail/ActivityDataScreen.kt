package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * An activity's All data page (feed/activity-page-layout): every figure, the avg/max table, every row of its tables and where a combined
 * activity's data came from, opened at [section] (its name, the top when null) from the activity page on a window without a side pane.
 */
@CircuitSerializable(SessionScope::class) data class ActivityDataScreen(val uri: String, val section: String? = null) : Screen

/** The page's [content]: the activity's load (ui/content-loading), its data null until it first loads. */
@Immutable
data class ActivityDataState(
    val content: ContentState<ActivityData?> = ContentState(null),
    val eventSink: (ActivityDataEvent) -> Unit = {},
) : CircuitUiState

sealed interface ActivityDataEvent {
    data object Back : ActivityDataEvent

    data object Retry : ActivityDataEvent

    /** Opens the gear item [uri] from the Device group. */
    data class OpenGear(val uri: String) : ActivityDataEvent
}

/** A part of All data, in the order it shows. */
enum class DataSection {
    Stats,
    AvgMax,
    Laps,
    Legs,
    Lengths,
    Sets,
    Sources;

    companion object {
        /** The table [section] is All data's part for; null for a chart. */
        fun of(section: DetailSection): DataSection? =
            when (section) {
                DetailSection.Laps -> Laps
                DetailSection.Legs -> Legs
                DetailSection.Lengths -> Lengths
                DetailSection.Sets -> Sets
                else -> null
            }

        /** The section named [name] ([ActivityDataScreen.section]); null for none or a name no section has. */
        fun named(name: String?): DataSection? = entries.firstOrNull { it.name == name }
    }
}

/** One of All data's sections, as a chip and a heading; a class so Swift can read a list of them. */
@Immutable data class DataSectionOption(val section: DataSection, val label: AppText)

/**
 * What All data shows of an activity, built from its page ([of]) so the page, its side pane and the All data screen agree.
 *
 * @property start the section to open at; null for the top.
 */
@Immutable
data class ActivityData(
    val stats: ImmutableList<StatsGroup> = persistentListOf(),
    val avgMax: ImmutableList<AvgMaxRow> = persistentListOf(),
    /** The tables come from its laps, legs, lengths and sets; null without streams. */
    val charts: ActivityCharts? = null,
    /** On a combined activity, the members each card's data came from. */
    val sources: ImmutableList<ChartSource> = persistentListOf(),
    val start: DataSection? = null,
) {
    /** The sections it has, in order, for its chips. */
    val sections: ImmutableList<DataSectionOption>
        get() = DataSection.entries.filter { has(it) }.map { DataSectionOption(it, ActivityDataTexts.heading(it)) }.toImmutableList()

    /** Whether it has anything to show under [section]. */
    fun has(section: DataSection): Boolean =
        when (section) {
            DataSection.Stats -> stats.isNotEmpty()
            DataSection.AvgMax -> avgMax.isNotEmpty()
            DataSection.Laps -> !charts?.laps.isNullOrEmpty()
            DataSection.Legs -> !charts?.legs.isNullOrEmpty()
            DataSection.Lengths -> !charts?.lengths.isNullOrEmpty()
            DataSection.Sets -> !charts?.sets.isNullOrEmpty()
            DataSection.Sources -> sources.isNotEmpty()
        }

    /** How many figures its stats list holds, as the activity page's All data row says. */
    val figureCount: Int
        get() = stats.sumOf { it.rows.size }

    companion object {
        fun of(page: ActivityPageContent, sources: ImmutableList<ChartSource>, start: DataSection? = null): ActivityData =
            ActivityData(page.stats, page.avgMax, page.charts, sources, start)
    }
}

/** All data's words, and the activity page's ways into it. */
object ActivityDataTexts {
    val title: AppText = AppText.named("data_title")
    val close: AppText = AppText.named("data_close")

    fun heading(section: DataSection): AppText =
        when (section) {
            DataSection.Stats -> AppText.named("page_all_stats")
            DataSection.AvgMax -> FigureTexts.avgMaxHeading
            DataSection.Laps -> FigureTexts.section(DetailSection.Laps)
            DataSection.Legs -> FigureTexts.section(DetailSection.Legs)
            DataSection.Lengths -> FigureTexts.section(DetailSection.Lengths)
            DataSection.Sets -> FigureTexts.section(DetailSection.Sets)
            DataSection.Sources -> AppText.named("data_section_sources")
        }

    /** The activity page's last row, opening All data: "All data", and how many figures it holds. */
    val rowTitle: AppText = AppText.named("data_title")

    fun rowSummary(data: ActivityData): AppText = AppText.count("data_figure_count", data.figureCount)

    /** Whether the activity page offers All data at all: it has something a card or the hero doesn't already show in full. */
    fun offered(data: ActivityData): Boolean = data.stats.isNotEmpty() || data.avgMax.isNotEmpty() || data.sources.isNotEmpty()

    /** A table's Show all on the activity page, naming its rows ("Show all 13 laps"); null when it shows them all already. */
    fun showAll(section: DetailSection, charts: ActivityCharts): AppText? {
        val rows = tableRows(section, charts)
        val name =
            when (section) {
                DetailSection.Laps -> if (charts.skiRuns != null) "data_show_all_runs" else "data_show_all_laps"
                DetailSection.Legs -> "data_show_all_legs"
                DetailSection.Lengths -> "data_show_all_lengths"
                DetailSection.Sets -> "data_show_all_sets"
                else -> null
            }
        return name?.takeIf { rows > TABLE_PREVIEW_ROWS }?.let { AppText.count(it, rows) }
    }

    /** How many rows [section]'s table has. */
    fun tableRows(section: DetailSection, charts: ActivityCharts): Int =
        when (section) {
            DetailSection.Laps -> charts.laps.size
            DetailSection.Legs -> charts.legs.size
            DetailSection.Lengths -> charts.lengths.size
            DetailSection.Sets -> charts.sets.size
            else -> 0
        }

    /** A combined activity's source row: the card's name, and the devices its data came from. */
    fun sourceLabel(source: ChartSource): AppText = FigureTexts.section(source.section)

    fun sourceValue(source: ChartSource): AppText? = RecordingTexts.sourceCaption(source.members)
}
