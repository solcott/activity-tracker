package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.record.ActivityTypeChoices
import io.github.solcott.activitytracker.app.record.NewDeviceChoices
import io.github.solcott.activitytracker.data.gear.PastImportsMatcher
import kotlin.time.Clock

// Record's producers over SessionTestFixture's repositories, here beside record's presenters until record has its own modules.

/** The type picker's producer, as Metro injects it into the record, manual entry and import presenters. */
val SessionTestFixture.Repos.typeChoices: ActivityTypeChoices
    get() = ActivityTypeChoices(recentTypes)

/** The import's new-device prompt, as Metro injects it into the import presenter. */
val SessionTestFixture.Repos.newDeviceChoices: NewDeviceChoices
    get() =
        NewDeviceChoices(
            gear,
            gearCollections,
            gearDefaults,
            PastImportsMatcher(devices, places, gear, feed, manager),
            Clock.System,
        )
