package io.github.solcott.activitytracker.app.report

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.lexicon.Did

/**
 * A host's [Reporting]: opens the report sheet for a target, closes it, and once the athlete takes its offer to block the author, asks the
 * host's usual Block confirmation, then hands the author and the reported target to [onBlock] (the host's own block, so its list and counts
 * follow).
 */
@Composable
fun rememberReporting(reports: ReportRepository, onBlock: (author: Did, target: ReportTarget) -> Unit): Reporting {
    var sheet by rememberRetained { mutableStateOf<ReportScreen?>(null) }
    var confirmBlock by rememberRetained { mutableStateOf<Pair<BlockOffer, ReportTarget>?>(null) }
    val block by rememberUpdatedState(onBlock)
    return Reporting(available = reports.canReport, sheet = sheet, confirmBlock = confirmBlock?.first) { event ->
        when (event) {
            is ReportingEvent.Open -> sheet = ReportScreen(event.target, event.authorName, event.offerBlock)
            is ReportingEvent.Sheet -> {
                val open = sheet
                sheet = null
                val outer = event.event
                if (outer is ReportOuterEvent.Block && open != null) confirmBlock = BlockOffer(outer.author, open.authorName) to open.target
            }
            is ReportingEvent.ConfirmBlock -> {
                val offer = confirmBlock
                confirmBlock = null
                if (event.confirmed && offer != null) block(offer.first.author, offer.second)
            }
        }
    }
}
