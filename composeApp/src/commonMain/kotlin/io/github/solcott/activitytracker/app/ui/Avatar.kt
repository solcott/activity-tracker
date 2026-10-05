package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage

/**
 * A round profile picture loaded from [url]. Under it, and in its place while it loads, fails or is missing, is the first letter of [name].
 * Decorative: the name is always shown beside it.
 */
@Composable
fun Avatar(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    AvatarFrame(name, size, modifier) {
        if (url != null) AsyncImage(url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** The circle behind an [Avatar], with [name]'s initial under [content]. For an image that isn't at a URL yet. */
@Composable
fun AvatarFrame(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase().orEmpty(),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = if (size.value >= LARGE_AVATAR_DP) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.labelLarge,
        )
        content()
    }
}

private const val LARGE_AVATAR_DP = 56f
