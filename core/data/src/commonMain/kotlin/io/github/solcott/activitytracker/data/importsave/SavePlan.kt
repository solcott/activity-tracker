package io.github.solcott.activitytracker.data.importsave

import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * One activity of a persisted batch save (`import_save_item.planJson`), resolved as the athlete reviewed it at Save: its title, type,
 * visibility, description, the zone its file's clock is in, whether its original is kept, and the logged activity its combine group joins.
 */
@Serializable
@Suppress("LongParameterList") // One per field an activity's draft is resolved to.
class PlannedActivity(
    val title: String? = null,
    val type: ActivityType,
    val visibility: String,
    val details: Map<String, String> = emptyMap(),
    val description: String? = null,
    /** The zone a zone-less file's clock is in, by id; null when the file has zones. */
    val zone: String? = null,
    val originalTooLarge: Boolean = false,
    /** Epoch millis, for listing it as skipped when its file can't be read. */
    val startedAt: Long,
    /** The logged activity its combine group joins, if any. */
    val into: PlannedInto? = null,
    /** The combined activity's record key, for a group of the batch's own copies. */
    val combinedRkey: String? = null,
) {
    val visibilityChoice: VisibilityChoice
        get() = VisibilityChoice(visibility, details)

    val start: Instant
        get() = Instant.fromEpochMilliseconds(startedAt)

    fun encode(): String = AtJson.encodeToString(serializer(), this)

    companion object {
        fun decode(json: String): PlannedActivity = AtJson.decodeFromString(serializer(), json)
    }
}

/** An [OwnActivity] as a save stores it. */
@Serializable
class PlannedInto(val uri: AtUri, val cid: Cid?, val record: ActivityRecord, val visibility: String, val combinedInto: AtUri? = null) {
    fun own(): OwnActivity = OwnActivity(uri, cid, record, visibility, combinedInto)

    companion object {
        fun of(own: OwnActivity) = PlannedInto(own.uri, own.cid, own.record, own.visibility, own.combinedInto)
    }
}

/** What every activity of a save shares (`import_save.choicesJson`): the athlete's training, for zones, and the gear chosen. */
@Serializable
class PlannedChoices(val training: TrainingDetails? = null, val gear: List<AtUri> = emptyList()) {
    fun encode(): String = AtJson.encodeToString(serializer(), this)

    companion object {
        fun decode(json: String): PlannedChoices = AtJson.decodeFromString(serializer(), json)
    }
}

/** An archive path (`EntryRef.path`) as `import_save_item.pathJson`. */
fun encodePath(path: List<String>): String = AtJson.encodeToString(ListSerializer(String.serializer()), path)

fun decodePath(json: String): List<String> = AtJson.decodeFromString(ListSerializer(String.serializer()), json)
