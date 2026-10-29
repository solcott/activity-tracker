package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.map_credit
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun RouteMapTile(polyline: String, height: Dp, gaps: ImmutableList<Int>?, modifier: Modifier) {
    // Previews, tests and screenshot tools draw the route without MapLibre or a remote style.
    if (LocalInspectionMode.current) {
        RoutePreview(polyline, modifier, height, gaps)
        return
    }
    val dark = isSystemInDarkTheme()
    val color = MaterialTheme.colorScheme.primary.toArgb()
    val density = LocalDensity.current.density
    val snapshot = remember(polyline, gaps, dark, color, density) { RouteSnapshot(polyline, gaps, dark, color, density) }
    val loader = RouteImages.loader(LocalPlatformContext.current)
    var shown by remember(snapshot) { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().height(height)) {
        // Under the image, so it shows until the image is drawn and stays if it can't be.
        RoutePreview(polyline, Modifier, height, gaps)
        AsyncImage(
            model = snapshot,
            contentDescription = null,
            imageLoader = loader,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            onSuccess = { shown = true },
        )
        if (shown) {
            Text(
                stringResource(Res.string.map_credit),
                Modifier.align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = CREDIT_ALPHA), CREDIT_SHAPE)
                    .padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private const val CREDIT_ALPHA = 0.8f
private val CREDIT_SHAPE = RoundedCornerShape(4.dp)
