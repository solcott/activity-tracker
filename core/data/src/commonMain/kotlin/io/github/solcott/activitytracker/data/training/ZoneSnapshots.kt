package io.github.solcott.activitytracker.data.training

import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot

/** The athlete's zones for an activity, kept with its streams so later threshold changes don't change its time in zone. */
object ZoneSnapshots {
    /** [sport]'s heart rate zones from [training]; null when the model's thresholds are missing. */
    fun heartRate(training: TrainingDetails?, sport: String): ZoneSnapshot? {
        val effective = training?.forSport(sport) ?: return null
        return snapshot(ZoneModels.heartRate(effective.heartRateZoneModel), effective)
    }

    /** [sport]'s power zones from [training]; null without an FTP. */
    fun power(training: TrainingDetails?, sport: String): ZoneSnapshot? {
        val effective = training?.forSport(sport) ?: return null
        return snapshot(ZoneModels.power(effective.powerZoneModel), effective)
    }

    /** [snapshot]'s zones with their labels and names, when its model is one this client knows with the same number of zones. */
    fun zones(snapshot: ZoneSnapshot, models: List<ZoneModel>): List<Zone>? {
        val model = models.find { it.token == snapshot.model }?.takeIf { it.zones.size == snapshot.lows.size } ?: return null
        return model.zones.mapIndexed { i, start ->
            Zone(start.label, start.name, snapshot.lows[i], snapshot.lows.getOrNull(i + 1)?.minus(1))
        }
    }

    private fun snapshot(model: ZoneModel, training: EffectiveTraining): ZoneSnapshot? =
        Zones.compute(model, training)?.let { zones -> ZoneSnapshot(model.token, zones.map { it.low }) }
}
