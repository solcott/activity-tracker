package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.label_graphic_media
import io.github.solcott.activitytracker.composeApp.resources.label_hidden_message
import io.github.solcott.activitytracker.composeApp.resources.label_hidden_show
import io.github.solcott.activitytracker.composeApp.resources.label_hidden_title
import io.github.solcott.activitytracker.composeApp.resources.label_impersonation
import io.github.solcott.activitytracker.composeApp.resources.label_nudity
import io.github.solcott.activitytracker.composeApp.resources.label_own_blurred
import io.github.solcott.activitytracker.composeApp.resources.label_porn
import io.github.solcott.activitytracker.composeApp.resources.label_sexual
import io.github.solcott.activitytracker.composeApp.resources.label_show
import io.github.solcott.activitytracker.composeApp.resources.label_spam
import io.github.solcott.activitytracker.composeApp.resources.label_taken_down
import io.github.solcott.activitytracker.composeApp.resources.label_warn
import io.github.solcott.activitytracker.data.moderation.BlurReason
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.data.moderation.WarnReason
import org.jetbrains.compose.resources.stringResource

@Composable
fun blurLabel(reason: BlurReason): String =
    stringResource(
        when (reason) {
            BlurReason.Porn -> Res.string.label_porn
            BlurReason.Sexual -> Res.string.label_sexual
            BlurReason.Nudity -> Res.string.label_nudity
            BlurReason.GraphicMedia -> Res.string.label_graphic_media
        }
    )

@Composable
fun warnLabel(reason: WarnReason): String =
    stringResource(
        when (reason) {
            WarnReason.Warn -> Res.string.label_warn
            WarnReason.Spam -> Res.string.label_spam
            WarnReason.Impersonation -> Res.string.label_impersonation
        }
    )

/**
 * [content] (a photo or avatar) blurred under [reason] until [revealed], with the reason and Show on top; tapping reveals it. On Android
 * before 12, where blur isn't drawn, the scrim alone covers it. Without a [reason] it's [content] as is.
 */
@Composable
fun BlurredMedia(
    reason: BlurReason?,
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shown = reason?.takeUnless { revealed }
    Box(modifier, contentAlignment = Alignment.Center) {
        // Covered, the photo is neither seen nor reachable: the scrim on top takes the tap, which reveals rather than opens it.
        Box(if (shown != null) Modifier.fillMaxSize().blur(BLUR_RADIUS).clearAndSetSemantics {} else Modifier.fillMaxSize()) { content() }
        if (shown != null) {
            Column(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SCRIM_ALPHA))
                    .clickable(role = Role.Button, onClick = onReveal),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                if (!compact) {
                    Text(blurLabel(shown), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    Text(
                        stringResource(Res.string.label_show),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * The line a labeled subject shows above its content: a warning naming the label, the athlete's own note that their media is blurred for
 * everyone, or that their content was taken down. A [warn]ing on the content or its author shows whatever else applies (beside a blur, or
 * once a hidden subject is opened), since warnings never hide anything. Nothing when no label calls for one.
 */
@Composable
fun ModerationNotice(decision: ModerationDecision, isOwn: Boolean, modifier: Modifier = Modifier, warn: WarnReason? = null) {
    val primary =
        when (decision) {
            is ModerationDecision.Warn -> warnLabel(decision.reason)
            is ModerationDecision.Blur -> if (isOwn) stringResource(Res.string.label_own_blurred, blurLabel(decision.reason)) else null
            ModerationDecision.Takedown -> stringResource(Res.string.label_taken_down)
            ModerationDecision.Hidden,
            ModerationDecision.None -> null
        }
    val warning = warn?.takeUnless { decision is ModerationDecision.Warn || decision == ModerationDecision.Takedown }?.let { warnLabel(it) }
    if (primary == null && warning == null) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        primary?.let { NoticeChip(it, error = decision == ModerationDecision.Takedown) }
        warning?.let { NoticeChip(it, error = false) }
    }
}

@Composable
private fun NoticeChip(text: String, error: Boolean) {
    Text(
        text,
        Modifier.clip(CHIP_SHAPE)
            .background(if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        style = MaterialTheme.typography.labelMedium,
    )
}

/** A hidden subject reached by URI, collapsed behind a warning until the viewer chooses to view it ([onShow]). */
@Composable
fun HiddenCard(onShow: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(CARD_SHAPE).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(Res.string.label_hidden_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.label_hidden_message), style = MaterialTheme.typography.bodyMedium)
        AppSecondaryButton(onClick = onShow) { Text(stringResource(Res.string.label_hidden_show)) }
    }
}

private val BLUR_RADIUS = 24.dp

/** Opaque enough to hide the photo where blur isn't drawn. */
private const val SCRIM_ALPHA = 0.85f
private val CHIP_SHAPE = RoundedCornerShape(8.dp)
private val CARD_SHAPE = RoundedCornerShape(12.dp)
