package io.github.solcott.activitytracker.app.links

import com.slack.circuit.runtime.screen.Screen

/** The tab a link opens on (ui/deep-links): one of the app's three, or whichever is shown ([Current], Feed while the app starts). */
enum class LinkTab {
    Feed,
    Search,
    You,
    Current,
}

/**
 * What a link opens: [chain] on [tab], its page last and the pages it is reached through under it, all above the tab's first screen (which
 * is never part of the chain). An empty chain returns the tab to its first screen.
 */
data class LinkPlan(val tab: LinkTab, val chain: List<Screen>)

/**
 * How a stack takes a link's chain (ui/deep-links): keep the first [keep] screens and go to each of [push]. When the stack already holds
 * the chain's first screen it is kept up to its last place and as far past it as it goes on matching the chain, so a page isn't opened
 * twice; otherwise the whole chain goes on top. An empty chain keeps only the tab's first screen.
 */
data class StackEdit(val keep: Int, val push: List<Screen>) {
    companion object {
        @Suppress("ReturnCount") // An empty chain and one the stack doesn't hold are answered first.
        fun of(current: List<Screen>, chain: List<Screen>): StackEdit {
            if (chain.isEmpty()) return StackEdit(1, emptyList())
            val at = current.lastIndexOf(chain.first())
            if (at < 0) return StackEdit(current.size, chain)
            var matched = 1
            while (matched < chain.size && current.getOrNull(at + matched) == chain[matched]) matched++
            return StackEdit(maxOf(at + matched, 1), chain.drop(matched))
        }
    }
}
