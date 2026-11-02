package io.github.solcott.activitytracker.data.importsave

/** How one source of a save is kept on Android ([planKeeping]). */
enum class KeepWay {
    /** A persisted URI grant. */
    GRANT,

    /** A copy in app storage. */
    COPY,

    /** Not kept. */
    NONE,
}

/** A source to keep: its [size] in bytes, and whether it can take a persisted URI grant ([grantable], a picked document's URI). */
data class KeepCandidate(val size: Long, val grantable: Boolean)

/** Persisted URI grants Android allows an app: 128 before Android 11 (API 30), 512 from it. */
fun persistedGrantCap(sdkInt: Int): Int = if (sdkInt >= ANDROID_11) GRANT_CAP else GRANT_CAP_BEFORE_11

/** Grants left free for anything else that persists one. */
const val SPARE_GRANTS = 64

/** The largest source copied into app storage once grants run out. */
const val MAX_COPY_BYTES = 64L * 1024 * 1024

private const val ANDROID_11 = 30
private const val GRANT_CAP = 512
private const val GRANT_CAP_BEFORE_11 = 128

/**
 * How each of [candidates] is kept, in order, given [existingGrants] already persisted on an Android of [sdkInt]: grants while the budget
 * ([persistedGrantCap] less [SPARE_GRANTS] and the grants held) allows, the largest sources first since small ones can be copied instead;
 * past it a copy for sources of [MAX_COPY_BYTES] or less, else none.
 */
fun planKeeping(existingGrants: Int, sdkInt: Int, candidates: List<KeepCandidate>): List<KeepWay> {
    var budget = (persistedGrantCap(sdkInt) - SPARE_GRANTS - existingGrants).coerceAtLeast(0)
    val ways = MutableList(candidates.size) { KeepWay.NONE }
    candidates.indices
        .sortedByDescending { candidates[it].size }
        .forEach { i ->
            val candidate = candidates[i]
            ways[i] =
                when {
                    candidate.grantable && budget > 0 -> KeepWay.GRANT.also { budget-- }
                    candidate.size <= MAX_COPY_BYTES -> KeepWay.COPY
                    else -> KeepWay.NONE
                }
        }
    return ways
}
