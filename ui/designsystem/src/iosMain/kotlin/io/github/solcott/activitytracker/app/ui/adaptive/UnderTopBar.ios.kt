package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGColorRetain
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSNumber
import platform.QuartzCore.CAGradientLayer
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * Hides a native view drawn over the Compose canvas (`placedAsOverlay`) where it has scrolled under its screen's [AppTopBar]. The bar's
 * blur can't reach a native view, so it would otherwise show sharp over the bar, and take the taps meant for the bar's buttons. A gradient
 * mask fades the view out across the bar's edge as Compose content is blurred there, as strongly as the bar's material has faded in. Put
 * [modifier] on the `UIKitView` and call [update] from its update block, which runs again whenever the positions it reads change; a view
 * that takes touches gives its `UIKitView` no interaction mode while [covered], since Compose's wrapper around it takes them otherwise.
 */
@Stable
internal class UnderTopBar(private val edge: TopBarEdge?) {
    private var top by mutableFloatStateOf(0f)
    private var height by mutableFloatStateOf(0f)

    val modifier: Modifier = Modifier.onGloballyPositioned {
        top = it.positionInRoot().y
        height = it.size.height.toFloat()
    }

    /** How much of the view, in pixels from its top, is above the bar's bottom edge. */
    private val hidden: Float
        get() = (edge?.bottom ?: 0f) - top

    private val material: Float
        get() = edge?.material?.invoke() ?: 0f

    /** Whether any of the view is under the bar while the bar shows its material. It changes only as the view crosses the bar's edge. */
    val covered: Boolean by derivedStateOf { material > 0f && hidden > 0f }

    @OptIn(ExperimentalForeignApi::class) // The view's bounds, a C struct.
    fun update(view: UIView) {
        val hidden = hidden
        val material = material
        val fade = edge?.fade ?: 0f
        // Without this Core Animation eases every change to the mask, so it would trail the scroll.
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        if (material > 0f && height > 0f && hidden + fade > 0f) {
            val mask = view.layer.mask as? CAGradientLayer ?: CAGradientLayer().also { view.layer.mask = it }
            mask.frame = view.bounds
            mask.colors = listOf(maskColor(1f - material), maskColor(1f))
            mask.locations =
                listOf(NSNumber(double = (hidden / height).toDouble()), NSNumber(double = ((hidden + fade) / height).toDouble()))
        } else {
            view.layer.mask = null
        }
        CATransaction.commit()
    }
}

/** An [UnderTopBar] for a native view in the current [AppScaffold]'s content. */
@Composable
internal fun rememberUnderTopBar(): UnderTopBar {
    val edge = LocalTopBarEdge.current
    return remember(edge) { UnderTopBar(edge) }
}

/** A mask colour showing what's under it at [alpha], as the Objective-C object `CAGradientLayer.colors` takes. */
@OptIn(ExperimentalForeignApi::class) // CGColor is a C type.
private fun maskColor(alpha: Float): Any? =
    CFBridgingRelease(CGColorRetain(UIColor.blackColor.colorWithAlphaComponent(alpha.toDouble()).CGColor))
