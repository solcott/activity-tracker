package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Who an account export says may see an activity. */
enum class ExportVisibility {
    Everyone,
    Followers,
    OnlyMe,
}

/** What an account export says of one activity: each part null where the export doesn't say, or says something we don't map. */
data class AccountExportActivity(val title: String?, val description: String?, val type: ActivityType?, val visibility: ExportVisibility?)

/** The account export a picked archive is, if any. */
enum class ExportKind {
    Strava,
    Garmin,
}

/** An account export's metadata, looked up for each activity file in it. */
internal sealed interface ExportMetadata {
    val kind: ExportKind

    /** What the export says of [activity], read from the file at [path] in the archive (members of nested zips after the first). */
    fun lookup(path: List<String>, activity: ImportedActivity): AccountExportActivity?

    companion object {
        /**
         * The export [archive] is, read from its metadata file, or null for any other archive (or an export whose metadata can't be read:
         * its activities still import, from their files alone).
         */
        suspend fun of(archive: ZipArchive): ExportMetadata? {
            val paths = archive.entries.map { it.path }
            // Strava's CSV sits beside its activities folder, at the root or in the export's own folder.
            val strava = paths.firstOrNull { path ->
                path.substringAfterLast('/').equals(STRAVA_CSV, ignoreCase = true) &&
                    paths.any { it.startsWith(path.substringBeforeLast('/', "").withSlash() + STRAVA_ACTIVITIES) }
            }
            val garmin = paths.filter { GARMIN_SUMMARIES.containsMatchIn(it) }
            return when {
                strava != null -> readText(archive, strava)?.let { StravaExport.parse(it, strava.substringBeforeLast('/', "")) }
                garmin.isNotEmpty() ->
                    GarminExport(garmin.flatMap { path -> readText(archive, path)?.let(GarminExport::summaries).orEmpty() })
                else -> null
            }
        }

        private suspend fun readText(archive: ZipArchive, path: String): String? {
            val entry = archive.entries.first { it.path == path }
            return runCatchingMetadata { archive.open(entry).readAll(MAX_METADATA_BYTES).decodeToString() }
        }

        private fun String.withSlash() = if (isEmpty()) "" else "$this/"

        private const val STRAVA_CSV = "activities.csv"
        private const val STRAVA_ACTIVITIES = "activities/"
        private val GARMIN_SUMMARIES =
            Regex("""(^|/)DI_CONNECT/DI-Connect-Fitness/[^/]*summarizedActivities[^/]*\.json$""", RegexOption.IGNORE_CASE)

        /** A metadata file far beyond any athlete's: a decade of daily activities is a few MB. */
        private const val MAX_METADATA_BYTES = 64 * 1024 * 1024
    }
}

/** Strava's export: `activities.csv` names each activity's file (`activities/123.fit.gz`) in its `Filename` column. */
internal class StravaExport(private val byPath: Map<String, AccountExportActivity>) : ExportMetadata {
    override val kind = ExportKind.Strava

    override fun lookup(path: List<String>, activity: ImportedActivity): AccountExportActivity? = path.singleOrNull()?.let(byPath::get)

    companion object {
        /** Reads `activities.csv` from [folder] of the archive (empty at its root). */
        fun parse(text: String, folder: String): StravaExport {
            val csv = Csv.parse(text)
            val prefix = if (folder.isEmpty()) "" else "$folder/"
            val byPath =
                csv.rows.mapNotNull { row ->
                    val file = csv.value(row, "Filename") ?: return@mapNotNull null
                    prefix + file.removePrefix("/") to
                        AccountExportActivity(
                            title = csv.value(row, "Activity Name"),
                            description = csv.value(row, "Activity Description"),
                            type = csv.value(row, "Activity Type")?.let(StravaTypes::of),
                            visibility = visibility(csv, row),
                        )
                }
            return StravaExport(byPath.toMap())
        }

        /** From a visibility column where the export has one, else a yes/no private one; null with neither. */
        private fun visibility(csv: Csv, row: List<String>): ExportVisibility? {
            val named = VISIBILITY_COLUMNS.firstNotNullOfOrNull { csv.value(row, it) }
            val private = PRIVATE_COLUMNS.firstNotNullOfOrNull { csv.value(row, it) }
            return when {
                named != null -> namedVisibility(named)
                private != null -> if (private.lowercase() in TRUE_VALUES) ExportVisibility.OnlyMe else ExportVisibility.Everyone
                else -> null
            }
        }

        private fun namedVisibility(named: String): ExportVisibility? =
            when (named.lowercase().replace(' ', '_')) {
                "everyone" -> ExportVisibility.Everyone
                "followers_only",
                "followers" -> ExportVisibility.Followers
                "only_me",
                "only_you",
                "private" -> ExportVisibility.OnlyMe
                else -> null
            }

        private val VISIBILITY_COLUMNS = listOf("Activity Visibility", "Visibility")
        private val PRIVATE_COLUMNS = listOf("Activity Private", "Private")
        private val TRUE_VALUES = setOf("true", "yes", "1")
    }
}

/**
 * Garmin Connect's export: summaries in `DI_CONNECT/DI-Connect-Fitness/…summarizedActivities….json`, and the uploaded files (often named
 * `…_<activityId>.fit`) in zips under `DI-Connect-Uploaded-Files`. A file is matched by its activity ID, else by its start and length.
 */
internal class GarminExport(summaries: List<Summary>) : ExportMetadata {
    override val kind = ExportKind.Garmin

    private val byId = summaries.filter { it.id != null }.associateBy { it.id }
    private val byStart = summaries.filter { it.start != null }.sortedBy { it.start }

    class Summary(val id: Long?, val start: Instant?, val elapsedMillis: Double?, val activity: AccountExportActivity)

    override fun lookup(path: List<String>, activity: ImportedActivity): AccountExportActivity? {
        val id = ID_IN_NAME.find(path.last().substringAfterLast('/'))?.groupValues?.get(1)?.toLongOrNull()
        id?.let(byId::get)?.let {
            return it.activity
        }
        return byStart
            .filter { summary ->
                summary.start?.let { abs((it - activity.startedAt).inWholeSeconds) <= START_TOLERANCE.inWholeSeconds } == true
            }
            .firstOrNull { summary ->
                val millis = summary.elapsedMillis ?: return@firstOrNull true
                val elapsed = activity.elapsed.inWholeMilliseconds.toDouble()
                elapsed == 0.0 || abs(millis - elapsed) <= elapsed * DURATION_TOLERANCE
            }
            ?.activity
    }

    companion object {
        /** Every object holding an `activityId` anywhere in [text]: the export nests them in a wrapper that has changed over the years. */
        fun summaries(text: String): List<Summary> {
            val root = runCatchingMetadata { Json.parseToJsonElement(text) } ?: return emptyList()
            val found = mutableListOf<Summary>()
            fun walk(element: JsonElement) {
                when (element) {
                    is JsonArray -> element.forEach(::walk)
                    is JsonObject -> if ("activityId" in element) found += summary(element) else element.values.forEach(::walk)
                    is JsonPrimitive -> Unit
                }
            }
            walk(root)
            return found
        }

        private fun summary(json: JsonObject): Summary {
            fun string(key: String) = (json[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
            fun number(key: String) = (json[key] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
            /** A key whose value is a token, or an object holding one as `typeKey`. */
            fun typeKey(key: String): String? =
                when (val value = json[key]) {
                    is JsonPrimitive -> value.contentOrNull
                    is JsonObject -> (value["typeKey"] as? JsonPrimitive)?.contentOrNull
                    else -> null
                }
            val start = (json["beginTimestamp"] ?: json["startTimeGmt"]) as? JsonPrimitive
            return Summary(
                id = (json["activityId"] as? JsonPrimitive)?.longOrNull,
                start = start?.longOrNull?.let { Instant.fromEpochMilliseconds(it) },
                elapsedMillis = number("elapsedDuration") ?: number("duration"),
                activity =
                    AccountExportActivity(
                        title = string("name"),
                        description = string("description"),
                        type = typeKey("activityType")?.let(GarminTypes::of),
                        visibility =
                            when (typeKey("privacy")?.lowercase()) {
                                "public" -> ExportVisibility.Everyone
                                "subscribers",
                                "followers",
                                "connections",
                                "groups" -> ExportVisibility.Followers
                                "private" -> ExportVisibility.OnlyMe
                                else -> null
                            },
                    ),
            )
        }

        private val ID_IN_NAME = Regex("""(\d{6,})\.(fit|gpx|tcx)$""", RegexOption.IGNORE_CASE)
        private val START_TOLERANCE = 60.seconds
        private const val DURATION_TOLERANCE = 0.05
    }
}

/** Strava's activity types, by their display name in `activities.csv` with case, spaces and dashes ignored. */
internal object StravaTypes {
    fun of(name: String): ActivityType? = TYPES[name.lowercase().filter { it.isLetter() }]

    private val TYPES: Map<String, ActivityType> =
        mapOf(
            "run" to ActivityType(Sport.RUN),
            "trailrun" to ActivityType(Sport.RUN, SubSport.TRAIL),
            "virtualrun" to ActivityType(Sport.RUN, SubSport.VIRTUAL),
            "ride" to ActivityType(Sport.RIDE),
            "mountainbikeride" to ActivityType(Sport.RIDE, SubSport.MOUNTAIN),
            "gravelride" to ActivityType(Sport.RIDE, SubSport.GRAVEL),
            "virtualride" to ActivityType(Sport.RIDE, SubSport.VIRTUAL),
            "ebikeride" to ActivityType(Sport.RIDE, SubSport.E_BIKE),
            "emountainbikeride" to ActivityType(Sport.RIDE, SubSport.E_MOUNTAIN),
            "velomobile" to ActivityType(Sport.RIDE, SubSport.VELOMOBILE),
            "handcycle" to ActivityType(Sport.HANDCYCLE),
            "wheelchair" to ActivityType(Sport.WHEELCHAIR),
            "swim" to ActivityType(Sport.SWIM),
            "walk" to ActivityType(Sport.WALK),
            "hike" to ActivityType(Sport.HIKE),
            "alpineski" to ActivityType(Sport.SKI),
            "backcountryski" to ActivityType(Sport.SKI, SubSport.BACKCOUNTRY),
            "nordicski" to ActivityType(Sport.XC_SKI),
            "rollerski" to ActivityType(Sport.XC_SKI, SubSport.ROLLER),
            "snowboard" to ActivityType(Sport.SNOWBOARD),
            "snowshoe" to ActivityType(Sport.SNOWSHOE),
            "iceskate" to ActivityType(Sport.ICE_SKATE),
            "inlineskate" to ActivityType(Sport.INLINE_SKATE),
            "skateboard" to ActivityType(Sport.SKATEBOARD),
            "rowing" to ActivityType(Sport.ROW),
            "virtualrow" to ActivityType(Sport.ROW, SubSport.INDOOR),
            "kayaking" to ActivityType(Sport.KAYAK),
            "canoeing" to ActivityType(Sport.CANOE),
            "standuppaddling" to ActivityType(Sport.SUP),
            "surfing" to ActivityType(Sport.SURF),
            "kitesurf" to ActivityType(Sport.KITEBOARD),
            "windsurf" to ActivityType(Sport.WINDSURF),
            "sail" to ActivityType(Sport.SAIL),
            "rockclimbing" to ActivityType(Sport.CLIMB),
            "weighttraining" to ActivityType(Sport.STRENGTH),
            "crossfit" to ActivityType(Sport.STRENGTH, SubSport.CROSSFIT),
            "workout" to ActivityType(Sport.CARDIO),
            "hiit" to ActivityType(Sport.HIIT),
            "yoga" to ActivityType(Sport.YOGA),
            "pilates" to ActivityType(Sport.PILATES),
            "elliptical" to ActivityType(Sport.ELLIPTICAL),
            "stairstepper" to ActivityType(Sport.STAIR_STEPPER),
            "golf" to ActivityType(Sport.GOLF),
            "soccer" to ActivityType(Sport.SOCCER),
            "tennis" to ActivityType(Sport.TENNIS),
            "pickleball" to ActivityType(Sport.PICKLEBALL),
            "padel" to ActivityType(Sport.PADEL),
            "badminton" to ActivityType(Sport.BADMINTON),
            "squash" to ActivityType(Sport.SQUASH),
            "racquetball" to ActivityType(Sport.RACQUETBALL),
            "tabletennis" to ActivityType(Sport.TABLE_TENNIS),
            "dance" to ActivityType(Sport.DANCE),
        )
}

/** Garmin Connect's activity type keys (`activityType.typeKey`). */
internal object GarminTypes {
    fun of(key: String): ActivityType? = TYPES[key.lowercase()]

    private val TYPES: Map<String, ActivityType> =
        mapOf(
            "running" to ActivityType(Sport.RUN),
            "street_running" to ActivityType(Sport.RUN),
            "trail_running" to ActivityType(Sport.RUN, SubSport.TRAIL),
            "treadmill_running" to ActivityType(Sport.RUN, SubSport.TREADMILL),
            "track_running" to ActivityType(Sport.RUN, SubSport.TRACK),
            "indoor_running" to ActivityType(Sport.RUN, SubSport.INDOOR_TRACK),
            "virtual_run" to ActivityType(Sport.RUN, SubSport.VIRTUAL),
            "ultra_run" to ActivityType(Sport.RUN, SubSport.ULTRA),
            "obstacle_run" to ActivityType(Sport.RUN, SubSport.OBSTACLE),
            "cycling" to ActivityType(Sport.RIDE),
            "road_biking" to ActivityType(Sport.RIDE, SubSport.ROAD),
            "mountain_biking" to ActivityType(Sport.RIDE, SubSport.MOUNTAIN),
            "gravel_cycling" to ActivityType(Sport.RIDE, SubSport.GRAVEL),
            "cyclocross" to ActivityType(Sport.RIDE, SubSport.CYCLOCROSS),
            "indoor_cycling" to ActivityType(Sport.RIDE, SubSport.INDOOR),
            "virtual_ride" to ActivityType(Sport.RIDE, SubSport.VIRTUAL),
            "e_bike_fitness" to ActivityType(Sport.RIDE, SubSport.E_BIKE),
            "e_bike_mountain" to ActivityType(Sport.RIDE, SubSport.E_MOUNTAIN),
            "bmx" to ActivityType(Sport.RIDE, SubSport.BMX),
            "hand_cycling" to ActivityType(Sport.HANDCYCLE),
            "lap_swimming" to ActivityType(Sport.SWIM, SubSport.POOL),
            "open_water_swimming" to ActivityType(Sport.SWIM, SubSport.OPEN_WATER),
            "swimming" to ActivityType(Sport.SWIM),
            "walking" to ActivityType(Sport.WALK),
            "casual_walking" to ActivityType(Sport.WALK),
            "speed_walking" to ActivityType(Sport.WALK),
            "hiking" to ActivityType(Sport.HIKE),
            "mountaineering" to ActivityType(Sport.MOUNTAINEERING),
            "rock_climbing" to ActivityType(Sport.CLIMB),
            "indoor_climbing" to ActivityType(Sport.CLIMB),
            "bouldering" to ActivityType(Sport.CLIMB, SubSport.BOULDERING),
            "resort_skiing" to ActivityType(Sport.SKI),
            "resort_skiing_snowboarding_ws" to ActivityType(Sport.SKI),
            "resort_snowboarding" to ActivityType(Sport.SNOWBOARD),
            "backcountry_skiing" to ActivityType(Sport.SKI, SubSport.BACKCOUNTRY),
            "backcountry_skiing_snowboarding_ws" to ActivityType(Sport.SKI, SubSport.BACKCOUNTRY),
            "cross_country_skiing_ws" to ActivityType(Sport.XC_SKI, SubSport.CLASSIC),
            "skate_skiing_ws" to ActivityType(Sport.XC_SKI, SubSport.SKATE),
            "snowshoeing_ws" to ActivityType(Sport.SNOWSHOE),
            "snow_shoe_ws" to ActivityType(Sport.SNOWSHOE),
            "inline_skating" to ActivityType(Sport.INLINE_SKATE),
            "skating_ws" to ActivityType(Sport.ICE_SKATE),
            "rowing" to ActivityType(Sport.ROW),
            "indoor_rowing" to ActivityType(Sport.ROW, SubSport.INDOOR),
            "kayaking_v2" to ActivityType(Sport.KAYAK),
            "kayaking" to ActivityType(Sport.KAYAK),
            "stand_up_paddleboarding_v2" to ActivityType(Sport.SUP),
            "stand_up_paddleboarding" to ActivityType(Sport.SUP),
            "surfing_v2" to ActivityType(Sport.SURF),
            "surfing" to ActivityType(Sport.SURF),
            "kiteboarding_v2" to ActivityType(Sport.KITEBOARD),
            "windsurfing_v2" to ActivityType(Sport.WINDSURF),
            "sailing_v2" to ActivityType(Sport.SAIL),
            "wakeboarding_v2" to ActivityType(Sport.WAKEBOARD),
            "boating_v2" to ActivityType(Sport.BOAT),
            "whitewater_rafting_kayaking" to ActivityType(Sport.WHITEWATER),
            "whitewater_rafting_v2" to ActivityType(Sport.WHITEWATER),
            "strength_training" to ActivityType(Sport.STRENGTH),
            "indoor_cardio" to ActivityType(Sport.CARDIO),
            "hiit" to ActivityType(Sport.HIIT),
            "yoga" to ActivityType(Sport.YOGA),
            "pilates" to ActivityType(Sport.PILATES),
            "elliptical" to ActivityType(Sport.ELLIPTICAL),
            "stair_climbing" to ActivityType(Sport.STAIR_STEPPER),
            "floor_climbing" to ActivityType(Sport.FLOOR_CLIMB),
            "jump_rope" to ActivityType(Sport.JUMP_ROPE),
            "boxing" to ActivityType(Sport.BOXING),
            "multi_sport" to ActivityType(Sport.MULTISPORT),
            "triathlon" to ActivityType(Sport.MULTISPORT, SubSport.TRIATHLON),
            "duathlon" to ActivityType(Sport.MULTISPORT, SubSport.DUATHLON),
            "single_gas_diving" to ActivityType(Sport.DIVE, SubSport.SINGLE_GAS),
            "multi_gas_diving" to ActivityType(Sport.DIVE, SubSport.MULTI_GAS),
            "gauge_diving" to ActivityType(Sport.DIVE, SubSport.GAUGE),
            "ccr_diving" to ActivityType(Sport.DIVE, SubSport.CCR),
            "apnea_diving" to ActivityType(Sport.DIVE, SubSport.APNEA),
            "apnea_hunting" to ActivityType(Sport.DIVE, SubSport.APNEA_HUNT),
            "golf" to ActivityType(Sport.GOLF),
            "disc_golf" to ActivityType(Sport.DISC_GOLF),
            "tennis" to ActivityType(Sport.TENNIS),
            "pickleball" to ActivityType(Sport.PICKLEBALL),
            "padel" to ActivityType(Sport.PADEL),
            "badminton" to ActivityType(Sport.BADMINTON),
            "squash" to ActivityType(Sport.SQUASH),
            "racquetball" to ActivityType(Sport.RACQUETBALL),
            "table_tennis" to ActivityType(Sport.TABLE_TENNIS),
            "soccer" to ActivityType(Sport.SOCCER),
            "basketball" to ActivityType(Sport.BASKETBALL),
            "volleyball" to ActivityType(Sport.VOLLEYBALL),
            "horseback_riding" to ActivityType(Sport.HORSEBACK),
            "hunting" to ActivityType(Sport.HUNT),
            "fishing" to ActivityType(Sport.FISH),
            "motorcycling" to ActivityType(Sport.MOTORCYCLE),
            "motocross" to ActivityType(Sport.MOTOCROSS),
            "atv" to ActivityType(Sport.ATV),
            "snowmobiling_ws" to ActivityType(Sport.SNOWMOBILE),
        )
}

/** [block]'s result, or null for metadata that can't be read: the export's activities still import from their files. */
@Suppress("TooGenericExceptionCaught") // Any failure reading the metadata only loses it.
private inline fun <T> runCatchingMetadata(block: () -> T): T? =
    try {
        block()
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
