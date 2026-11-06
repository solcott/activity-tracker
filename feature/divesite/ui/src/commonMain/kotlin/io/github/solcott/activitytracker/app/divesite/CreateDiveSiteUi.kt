package io.github.solcott.activitytracker.app.divesite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.RouteEnds
import io.github.solcott.activitytracker.app.segment.CreatePlaceMessage
import io.github.solcott.activitytracker.app.segment.PlaceDuplicates
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSlider
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.create_dive_site_at_site
import io.github.solcott.activitytracker.composeApp.resources.create_dive_site_duplicates
import io.github.solcott.activitytracker.composeApp.resources.create_dive_site_hidden_start
import io.github.solcott.activitytracker.composeApp.resources.create_dive_site_inside_zone
import io.github.solcott.activitytracker.composeApp.resources.create_dive_site_limit
import io.github.solcott.activitytracker.composeApp.resources.create_dive_site_name
import io.github.solcott.activitytracker.composeApp.resources.create_dive_site_names_dive
import io.github.solcott.activitytracker.composeApp.resources.create_dive_site_no_position
import io.github.solcott.activitytracker.composeApp.resources.create_dive_site_public
import io.github.solcott.activitytracker.composeApp.resources.create_dive_site_publish
import io.github.solcott.activitytracker.composeApp.resources.create_dive_site_radius
import io.github.solcott.activitytracker.composeApp.resources.create_dive_site_title
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import kotlin.math.roundToInt
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * Creating a dive site from one's own dive (dive-sites/site-creation): its radius around the dive's entry on a map and a slider, its name,
 * the warning that it will be public, then any site already there, and Publish; or why this dive can't give one.
 */
@CircuitInject(CreateDiveSiteScreen::class, SessionScope::class)
@Composable
fun CreateDiveSiteUi(state: CreateDiveSiteState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.create_dive_site_title),
                onBack = { state.eventSink(CreateDiveSiteEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ContentStateLayout(
            state.content,
            state.loadErrorKind,
            onRetry = { state.eventSink(CreateDiveSiteEvent.Retry) },
            contentPadding = padding,
        ) {
            val unavailable = state.unavailable
            val outline = state.outline
            when {
                unavailable != null -> CreatePlaceMessage(unavailableText(unavailable), Modifier.padding(padding))
                outline != null -> Form(state, outline, padding)
            }
        }
    }
}

@Composable
private fun Form(state: CreateDiveSiteState, outline: String, padding: PaddingValues) {
    ReadableScrollColumn(padding, Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActivityMap(outline, Modifier.fillMaxWidth(), ends = RouteEnds.HIDDEN)
        Text(
            stringResource(Res.string.create_dive_site_radius, Format.elevation(state.radius.toDouble())),
            style = MaterialTheme.typography.titleSmall,
        )
        AppSlider(
            value = state.radius.toFloat(),
            onValueChange = { state.eventSink(CreateDiveSiteEvent.RadiusChanged(it.roundToInt())) },
            valueRange = DiveSiteRecord.MIN_RADIUS.toFloat()..DiveSiteRecord.MAX_RADIUS.toFloat(),
            enabled = !state.publishing,
            modifier = Modifier.fillMaxWidth(),
        )
        AppTextField(
            state.name,
            label = stringResource(Res.string.create_dive_site_name),
            enabled = !state.publishing,
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(Res.string.create_dive_site_public) +
                if (state.namesDive) " " + stringResource(Res.string.create_dive_site_names_dive) else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.duplicates.isNotEmpty()) {
            val labels = state.duplicates.map { it.currentName }.toImmutableList()
            PlaceDuplicates(
                stringResource(Res.string.create_dive_site_duplicates),
                labels,
                onOpen = { state.eventSink(CreateDiveSiteEvent.OpenSite(state.duplicates[it].uri)) },
            )
        }
        if (state.limitReached) Text(stringResource(Res.string.create_dive_site_limit), color = MaterialTheme.colorScheme.error)
        state.actionError?.let { ErrorText(it) }
        AppButton(onClick = { state.eventSink(CreateDiveSiteEvent.Publish) }, enabled = state.canPublish) {
            Text(stringResource(Res.string.create_dive_site_publish))
        }
        if (state.publishing) AppProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun unavailableText(unavailable: CreateDiveSiteUnavailable): String =
    stringResource(
        when (unavailable) {
            CreateDiveSiteUnavailable.NoPosition -> Res.string.create_dive_site_no_position
            CreateDiveSiteUnavailable.InsideZone -> Res.string.create_dive_site_inside_zone
            CreateDiveSiteUnavailable.HiddenStart -> Res.string.create_dive_site_hidden_start
            CreateDiveSiteUnavailable.AtSite -> Res.string.create_dive_site_at_site
        }
    )
