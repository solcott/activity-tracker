package io.github.solcott.activitytracker.data.units

/** How stats are shown and entered. Records always store SI units; this only changes display and input. */
enum class UnitSystem(val token: String) {
    Metric("metric"),
    Imperial("imperial");

    companion object {
        /** Countries that use imperial (US customary) units day to day. */
        private val IMPERIAL_REGIONS = setOf("US", "LR", "MM")

        /** `actor.profile#units` is an open enum: unknown tokens read as null. */
        fun fromToken(token: String?): UnitSystem? = entries.firstOrNull { it.token == token }

        /** The default for an ISO 3166 [region] code when the athlete hasn't chosen. */
        fun defaultFor(region: String?): UnitSystem = if (region?.uppercase() in IMPERIAL_REGIONS) Imperial else Metric
    }
}

/** The device's ISO 3166 region code, if the platform exposes one. */
expect fun deviceRegion(): String?
