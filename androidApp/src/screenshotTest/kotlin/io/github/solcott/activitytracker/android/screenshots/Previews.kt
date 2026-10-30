package io.github.solcott.activitytracker.android.screenshots

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.preview.SessionPreviewSubUis
import io.github.solcott.activitytracker.app.ui.preview.AppPreview

/** A screen's primary sample at compact, medium and expanded widths (400, 610, 900dp), each 400, 500 and 1000dp tall. */
@Preview(name = "400x400", widthDp = 400, heightDp = 400)
@Preview(name = "400x500", widthDp = 400, heightDp = 500)
@Preview(name = "400x1000", widthDp = 400, heightDp = 1000)
@Preview(name = "610x400", widthDp = 610, heightDp = 400)
@Preview(name = "610x500", widthDp = 610, heightDp = 500)
@Preview(name = "610x1000", widthDp = 610, heightDp = 1000)
@Preview(name = "900x400", widthDp = 900, heightDp = 400)
@Preview(name = "900x500", widthDp = 900, heightDp = 500)
@Preview(name = "900x1000", widthDp = 900, heightDp = 1000)
annotation class PreviewScreenSizes

/** A phone-sized window, where a screen's other samples render. */
@Preview(name = "phone", widthDp = 400, heightDp = 500) annotation class PreviewPhoneSize

/** A screen's primary sample at phone size in dark theme and at 1.5x font scale. */
@Preview(name = "dark", widthDp = 400, heightDp = 500, uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "large-text", widthDp = 400, heightDp = 500, fontScale = 1.5f)
annotation class PreviewScreenVariants

/**
 * A group of shared components in light and dark theme, each at 1x and 1.5x font scale, 400x480dp (layoutlib renders a preview without a
 * height at the device's).
 */
@Preview(name = "light", widthDp = 400, heightDp = 480)
@Preview(name = "light-large-text", widthDp = 400, heightDp = 480, fontScale = 1.5f)
@Preview(name = "dark", widthDp = 400, heightDp = 480, uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "dark-large-text", widthDp = 400, heightDp = 480, uiMode = UI_MODE_NIGHT_YES, fontScale = 1.5f)
annotation class PreviewComponentVariants

/** [AppPreview] sized by the preview, in the preview's theme. */
@Composable
fun ScreenPreview(content: @Composable () -> Unit) {
    AppPreview(subUis = SessionPreviewSubUis, width = null, darkTheme = isSystemInDarkTheme(), content = content)
}

/** Shared components stacked on the theme's background. */
@Composable
fun ComponentPreview(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ScreenPreview {
        Column(
            modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** A screen's loaded sample, the one its [PreviewScreenSizes] and [PreviewScreenVariants] previews render. */
fun <T> Map<String, T>.loaded(): T = getValue(SampleKind.LOADED)

/**
 * A screen's samples other than loaded, so a new sample gets a screenshot without a code change. The IDE names each by its sample kind; the
 * screenshot engine numbers them in the samples' order.
 */
open class OtherSamples<T>(all: Map<String, T>) : PreviewParameterProvider<T> {
    private val samples = all.filterKeys { it != SampleKind.LOADED }.toList()

    override val values: Sequence<T> = samples.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = samples[index].first
}
