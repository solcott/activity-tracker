package io.github.solcott.activitytracker.app.nav

/** A browser history entry the app wrote (ui/web-history): the stack record it stands for, by its key, on its tab, at [path]. */
data class HistoryEntry(val key: String, val tab: TopLevelDestination?, val path: String)

/** What the browser's history is told to do. */
sealed interface HistoryOp {
    /** A new entry after the current one ([History.pushState]). */
    data class Push(val index: Int, val entry: HistoryEntry) : HistoryOp

    /** The current entry, rewritten ([History.replaceState]). */
    data class Replace(val index: Int, val entry: HistoryEntry) : HistoryOp

    /** Back [steps] entries ([History.go] with a negative count). */
    data class Back(val steps: Int) : HistoryOp
}

/**
 * The browser history the web app keeps in step with its back stack (ui/web-history), with no browser in it, so it is tested on the JVM. It
 * remembers the entries it wrote by their place in the history, and [index], the current one. Opening screens pushes an entry each, going
 * back in the app goes back in the history when the entry there is the screen now on top, a tab switch is an entry of its own, and anything
 * else (one settings page replacing another, a link that changed what was under the top) rewrites the current entry. The browser's Back and
 * Forward report where they landed ([landed]), and the shell then makes the app's stack match.
 */
class HistoryMirror {
    private val entries = mutableMapOf<Int, HistoryEntry>()
    private var shown: List<HistoryEntry> = emptyList()

    /** The browser landed on an entry this mirror didn't write, which the next stack shown takes over. */
    private var landedUnknown = false

    /** The current entry's place. */
    var index: Int = 0
        private set

    /**
     * The first stack shown, from the entry at [at] (the place a reload kept): its first screen there, and an entry above for each other.
     */
    fun start(stack: List<HistoryEntry>, at: Int = 0): List<HistoryOp> {
        entries.clear()
        index = at
        shown = stack
        val ops = mutableListOf<HistoryOp>(replace(stack.first()))
        stack.drop(1).forEach { ops += push(it) }
        return ops
    }

    /** What the history does now that the app shows [stack] (records first to top). Nothing when its current entry already is the top. */
    @Suppress("ReturnCount") // A taken-over entry and one already shown are answered first.
    fun show(stack: List<HistoryEntry>): List<HistoryOp> {
        val old = shown
        shown = stack
        val top = stack.last()
        if (landedUnknown) {
            landedUnknown = false
            return listOf(replace(top))
        }
        if (entries[index]?.key == top.key) return emptyList()
        val sameTab = old.firstOrNull()?.tab == top.tab
        val shared = old.zip(stack).takeWhile { (a, b) -> a.key == b.key }.size
        return when {
            sameTab && shared == old.size && stack.size > old.size -> stack.drop(shared).map(::push)
            sameTab && shared == stack.size && old.size > stack.size -> {
                val steps = old.size - stack.size
                if (entries[index - steps]?.key == top.key) {
                    index -= steps
                    listOf(HistoryOp.Back(steps))
                } else {
                    listOf(replace(top))
                }
            }
            !sameTab -> listOf(push(top))
            else -> listOf(replace(top))
        }
    }

    /**
     * The browser's Back or Forward landed on the entry at [at]: the entry the app wrote there, which the shell shows by switching to its
     * tab and going back to its record, or null for one this mirror didn't write (from before a reload), which the shell opens as a link.
     */
    fun landed(at: Int): HistoryEntry? {
        index = at
        return entries[at].also { landedUnknown = it == null }
    }

    /**
     * The entry [landed] on names a record the stack no longer holds (a screen gone back from, reopened by Forward), which the shell opens
     * again as a link: the stack that opens takes the entry over.
     */
    fun takeOver() {
        landedUnknown = true
    }

    private fun push(entry: HistoryEntry): HistoryOp {
        index++
        // Pushing drops every entry after the current one, as the browser does.
        entries.keys.filter { it >= index }.forEach(entries::remove)
        entries[index] = entry
        return HistoryOp.Push(index, entry)
    }

    private fun replace(entry: HistoryEntry): HistoryOp {
        entries[index] = entry
        return HistoryOp.Replace(index, entry)
    }
}
