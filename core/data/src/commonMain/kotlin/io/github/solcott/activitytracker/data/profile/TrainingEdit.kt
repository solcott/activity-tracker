package io.github.solcott.activitytracker.data.profile

import io.github.solcott.activitytracker.activityfile.DeviceThresholds
import io.github.solcott.activitytracker.data.training.ThresholdChange
import io.github.solcott.activitytracker.data.training.ThresholdUpdates
import io.github.solcott.activitytracker.data.training.TrainingHistory
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.ThresholdSource
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.time.Instant

/**
 * Saves the athlete's training thresholds and zone models ([DetailGroup.Training]) with [visibility], leaving their name, picture and other
 * details as they are. Null [training] clears it.
 */
suspend fun ProfileRepository.saveTraining(training: TrainingDetails?, visibility: String) {
    val record = ownProfileRecord()
    save(
        ProfileEdit(
            displayName = record?.displayName.orEmpty(),
            description = record?.description.orEmpty(),
            details = ProfileDetails(training = training),
            visibility = DetailsVisibility(training = visibility),
            groups = setOf(DetailGroup.Training),
        )
    )
}

/**
 * Saves [transform] of the training details as saved now, with [visibility], or the saved visibility when null. It reads them at save time,
 * so a screen editing the defaults keeps a sport override saved from another screen since it loaded, and the other way round. [transform]
 * returning null clears them.
 *
 * Each threshold [transform] changes is added to the history as entered by the athlete. The history is kept as saved, whatever [transform]
 * returns for it, so an editor that builds its training from a form can't drop it.
 */
suspend fun ProfileRepository.updateTraining(visibility: String? = null, transform: (TrainingDetails) -> TrainingDetails? = { it }) {
    val own = ownProfile()
    val current = TrainingHistory.backfilled(own.details.training ?: TrainingDetails())
    saveTraining(
        transform(current)
            ?.copy(history = current.history)
            ?.let { TrainingHistory.record(current, it, ThresholdSource.MANUAL, clock.now()) }
            ?.takeUnless { it == TrainingDetails() },
        visibility ?: DetailGroup.Training.visibility(own.visibility) ?: Visibility.SELF,
    )
}

/**
 * Removes [entry] from the threshold history, keeping the training visibility. When it's the threshold's current value, the threshold goes
 * back to the value before it, or is cleared.
 */
suspend fun ProfileRepository.removeThresholdEntry(entry: ThresholdEntry) {
    val own = ownProfile()
    val current = TrainingHistory.backfilled(own.details.training ?: return)
    saveTraining(
        TrainingHistory.remove(current, entry).takeUnless { it == TrainingDetails() },
        DetailGroup.Training.visibility(own.visibility) ?: Visibility.SELF,
    )
}

/**
 * Updates thresholds from an activity of [sport] that started at [activityStart] and was recorded with [device]'s settings, keeping the
 * training visibility, and adds them to the history as read from [deviceName]. The changes are worked out again from the saved profile,
 * which another device may have changed since a preview. Returns the changes made; when there are none it writes nothing.
 */
suspend fun ProfileRepository.applyDeviceThresholds(
    device: DeviceThresholds?,
    activityStart: Instant,
    sport: String? = null,
    deviceName: String? = null,
): List<ThresholdChange> {
    val own = ownProfile()
    val current = TrainingHistory.backfilled(own.details.training ?: return emptyList())
    val changes = ThresholdUpdates.pending(current, device, activityStart, sport)
    if (changes.isNotEmpty()) {
        val updated = ThresholdUpdates.apply(current, changes, activityStart)
        saveTraining(
            TrainingHistory.record(current, updated, ThresholdSource.DEVICE, activityStart, deviceName?.take(MAX_DEVICE_NAME)),
            DetailGroup.Training.visibility(own.visibility) ?: Visibility.SELF,
        )
    }
    return changes
}

/** The lexicon's `maxGraphemes` of a history entry's device name; cutting by characters keeps within it. */
private const val MAX_DEVICE_NAME = 64
