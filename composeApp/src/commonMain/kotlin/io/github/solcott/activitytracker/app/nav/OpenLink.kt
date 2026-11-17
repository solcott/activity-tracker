package io.github.solcott.activitytracker.app.nav

import com.slack.circuit.backstack.SaveableBackStack
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.popUntil
import io.github.solcott.activitytracker.app.links.OpenedLink
import io.github.solcott.activitytracker.app.links.StackEdit

/**
 * Shows [destination]'s back stack, saving the one shown so it keeps its place. Choosing the tab already shown returns it to its first
 * screen when [toRoot] (a tap on the tab), and leaves it as it is otherwise (a link opening on it).
 */
fun selectTab(backStack: SaveableBackStack, navigator: Navigator, viewerDid: String, destination: TopLevelDestination, toRoot: Boolean) {
    val root = backStack.rootRecord?.screen
    if (root != null && TopLevelDestination.of(root, viewerDid) == destination) {
        if (toRoot) navigator.popUntil { it == root }
    } else {
        navigator.resetRoot(destination.screen(viewerDid), Navigator.StateOptions(save = true, restore = true))
    }
}

/**
 * Shows [link] in the Compose shell (ui/deep-links), as the presenter host does: its tab, kept up to what the stack already holds of its
 * chain, and the rest gone to.
 */
fun openLink(backStack: SaveableBackStack, navigator: Navigator, viewerDid: String, link: OpenedLink) {
    link.tab?.let { selectTab(backStack, navigator, viewerDid, it, toRoot = false) }
    // The stack lists its top first.
    val edit = StackEdit.of(backStack.map { it.screen }.asReversed(), link.chain)
    repeat(backStack.size - edit.keep) { navigator.pop() }
    edit.push.forEach(navigator::goTo)
}
