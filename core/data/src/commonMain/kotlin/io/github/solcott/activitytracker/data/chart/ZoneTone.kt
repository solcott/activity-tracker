package io.github.solcott.activitytracker.data.chart

/**
 * Which of training zones' fixed colours (DESIGN.md "Training zones") a zone is drawn in. The rule is here, once: the shared `ZoneBars` and
 * the iOS app's `ZoneColors` only say what colour each tone is in their theme.
 */
enum class ZoneTone {
    Recovery,
    Blue,
    Green,
    Yellow,
    Orange,
    Red,
    Purple;

    companion object {
        private val HOT = listOf(Blue, Green, Yellow, Orange, Red, Purple)
        private val FOUR = listOf(Blue, Green, Orange, Red)
        private val THREE = listOf(Green, Orange, Red)

        /**
         * The tone of zone [index] (0 the lowest) of [count]: the lowest is recovery grey, the rest run blue, green, yellow, orange, red
         * and purple, dropping the middle colours when there are fewer zones.
         */
        fun of(index: Int, count: Int): ZoneTone {
            if (index == 0) return Recovery
            val tones =
                when (count - 1) {
                    in HOT.size..Int.MAX_VALUE -> HOT
                    HOT.size - 1 -> HOT.dropLast(1)
                    HOT.size - 2 -> FOUR
                    else -> THREE
                }
            return tones[(index - 1).coerceAtMost(tones.lastIndex)]
        }
    }
}
