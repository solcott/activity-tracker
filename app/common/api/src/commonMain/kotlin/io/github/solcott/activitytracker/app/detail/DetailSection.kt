package io.github.solcott.activitytracker.app.detail

/** A part of the detail screen with data of its own: a metric's card, or the device. In the order they show. */
enum class DetailSection {
    HeartRate,
    Power,
    /** Torque effectiveness, pedal smoothness and platform centre offset, each left and right. */
    CyclingDynamics,
    Speed,
    Cadence,
    /** Elevation and grade. */
    Elevation,
    Temperature,
    /** Core temperature, SmO₂ and total hemoglobin. */
    Physiology,
    Depth,
    /** A dive's ppO₂ and CNS. */
    Oxygen,
    TankPressure,
    Respiration,
    /**
     * Vertical oscillation, stance time and its balance, step length and vertical ratio, and Stryd's form power and leg spring stiffness.
     */
    RunningDynamics,
    /** An e-bike's battery, assist mode and assist level. */
    EBike,
    Gears,
    /** A multisport activity's legs and transitions. */
    Legs,
    /** A pool swim's lengths. */
    Lengths,
    /** A workout's sets. */
    Sets,
    Laps,
    /** The recording device and its sensors. */
    Device,
}
