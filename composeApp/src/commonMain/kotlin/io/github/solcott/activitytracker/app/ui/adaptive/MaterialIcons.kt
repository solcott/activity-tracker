package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** Material icons the app needs from material-icons-extended, copied here rather than pulling in the whole set. */
internal object MaterialIcons {
    val UnfoldMore: ImageVector =
        materialIcon("Filled.UnfoldMore") {
            materialPath {
                moveTo(12f, 5.83f)
                lineTo(15.17f, 9f)
                lineToRelative(1.41f, -1.41f)
                lineTo(12f, 3f)
                lineTo(7.41f, 7.59f)
                lineTo(8.83f, 9f)
                lineTo(12f, 5.83f)
                close()
                moveTo(12f, 18.17f)
                lineTo(8.83f, 15f)
                lineToRelative(-1.41f, 1.41f)
                lineTo(12f, 21f)
                lineToRelative(4.59f, -4.59f)
                lineTo(15.17f, 15f)
                lineTo(12f, 18.17f)
                close()
            }
        }

    val UnfoldLess: ImageVector =
        materialIcon("Filled.UnfoldLess") {
            materialPath {
                moveTo(7.41f, 18.59f)
                lineTo(8.83f, 20f)
                lineTo(12f, 16.83f)
                lineTo(15.17f, 20f)
                lineToRelative(1.41f, -1.41f)
                lineTo(12f, 14f)
                lineToRelative(-4.59f, 4.59f)
                close()
                moveTo(16.59f, 5.41f)
                lineTo(15.17f, 4f)
                lineTo(12f, 7.17f)
                lineTo(8.83f, 4f)
                lineTo(7.41f, 5.41f)
                lineTo(12f, 10f)
                lineToRelative(4.59f, -4.59f)
                close()
            }
        }
}
