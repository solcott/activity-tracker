package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser

/** Material icons the app needs from material-icons-extended, copied here rather than pulling in the whole set. */
internal object MaterialIcons {
    val Fullscreen: ImageVector =
        materialIcon("Filled.Fullscreen") {
            addPath(
                PathParser()
                    .parsePathString("M7,14L5,14v5h5v-2L7,17v-3zM5,10h2L7,7h3L10,5L5,5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2L19,5h-5z")
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }

    val CenterFocusStrong: ImageVector =
        materialIcon("Filled.CenterFocusStrong") {
            addPath(
                PathParser()
                    .parsePathString(
                        "M12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4z" +
                            "M5,15L3,15v4c0,1.1 0.9,2 2,2h4v-2L5,19v-4zM5,5h4L9,3L5,3c-1.1,0 -2,0.9 -2,2v4h2L5,5z" +
                            "M19,3h-4v2h4v4h2L21,5c0,-1.1 -0.9,-2 -2,-2zM19,19h-4v2h4c1.1,0 2,-0.9 2,-2v-4h-2v4z"
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }

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

    val CallSplit: ImageVector =
        materialIcon("Filled.CallSplit") {
            materialPath {
                moveTo(14f, 4f)
                lineToRelative(2.29f, 2.29f)
                lineToRelative(-2.88f, 2.88f)
                lineToRelative(1.42f, 1.42f)
                lineToRelative(2.88f, -2.88f)
                lineTo(20f, 10f)
                lineTo(20f, 4f)
                close()
                moveTo(10f, 4f)
                lineTo(4f, 4f)
                verticalLineToRelative(6f)
                lineToRelative(2.29f, -2.29f)
                lineToRelative(4.71f, 4.7f)
                lineTo(11f, 20f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-8.41f)
                lineToRelative(-5.29f, -5.3f)
                close()
            }
        }

    val Visibility: ImageVector =
        materialIcon("Filled.Visibility") {
            addPath(
                PathParser()
                    .parsePathString(
                        "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5z" +
                            "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z" +
                            "M12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z"
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }

    val VisibilityOff: ImageVector =
        materialIcon("Filled.VisibilityOff") {
            addPath(
                PathParser()
                    .parsePathString(
                        "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75" +
                            " -1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7z" +
                            "M2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 4.38,-0.84" +
                            "l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27z" +
                            "M7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 0.65,-0.08" +
                            "l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2z" +
                            "M11.84,9.02l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z"
                    )
                    .toNodes(),
                fill = SolidColor(Color.Black),
            )
        }
}
