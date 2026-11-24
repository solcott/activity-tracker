package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `app.activitytracker.feed.trailReport`: what a segment is like now, from a rider who was just on it (segments/trail-reports). Public, so
 * it lives only in the author's repo. Holds a known [conditions] tag or an image, or the AppView ignores it.
 */
@Immutable
@Serializable
data class TrailReportRecord(
    val segment: StrongRef,
    /** [TrailCondition] values, at most [TrailCondition.MAX]; unknown ones are ignored. */
    val conditions: List<String> = emptyList(),
    /** At most [MAX_NOTE_GRAPHEMES] graphemes. */
    val note: String? = null,
    /** At most [MAX_IMAGES] photos, re-encoded by the client without their metadata. */
    val images: List<ActivityMedia.Image> = emptyList(),
    val createdAt: Instant,
    @SerialName($$"$type") val type: String = Nsid.FEED_TRAIL_REPORT,
) {
    companion object {
        const val MAX_NOTE_GRAPHEMES = 300
        const val MAX_NOTE_LENGTH = 3000
        const val MAX_IMAGES = 4
    }
}

/** `feed.trailReport#conditions` known values, in the lexicon's order: the surface tags, then what is on the trail. */
object TrailCondition {
    const val DRY = "dry"
    const val TACKY = "tacky"
    const val WET = "wet"
    const val MUDDY = "muddy"
    const val SNOW = "snow"
    const val ICE = "ice"
    const val DOWNED_TREE = "downedTree"
    const val OVERGROWN = "overgrown"
    const val WASHED_OUT = "washedOut"
    const val CLOSED = "closed"

    /** The lexicon's `maxLength` for `conditions`. */
    const val MAX = 10

    /** Tags describing the trail's surface, which may lead a summary by riders. */
    val surface: List<String> = listOf(DRY, TACKY, WET, MUDDY, SNOW, ICE)

    /** Tags for what is on the trail. */
    val onTrail: List<String> = listOf(DOWNED_TREE, OVERGROWN, WASHED_OUT, CLOSED)

    /** Every known tag, in order. */
    val all: List<String> = surface + onTrail

    /** [tags]' known values, each once, in [all]'s order. */
    fun known(tags: Collection<String>): List<String> = all.filter { it in tags }
}

/** `app.activitytracker.feed.defs#trailConditions`: a segment's conditions from trail reports, as the viewer may see them. */
@Immutable
@Serializable
data class TrailConditions(
    /** The [TrailCondition] the summary leads with; null when no current report names a lead. */
    val lead: String? = null,
    /** How many riders' counted reports name [lead]. */
    val leadRiders: Int? = null,
    /** How many riders' reports count now. */
    val riders: Int,
    /** When the newest counted report was indexed. */
    val latestAt: Instant? = null,
    /** Every current tag but [lead], most riders first. */
    val tags: List<TrailConditionTag>,
    /** The newest current report with a note. */
    val latestNote: TrailReportView? = null,
    /** With no current conditions: when the newest older report with tags was indexed. */
    val lastReportAt: Instant? = null,
    /** The tags of the report [lastReportAt] dates. */
    val lastReportTags: List<String> = emptyList(),
    val photoCount: Int,
    /** The 4 newest photos; only from `getSegment`. */
    val photos: List<TrailPhotoView> = emptyList(),
)

/** `app.activitytracker.feed.defs#trailConditionTag` */
@Immutable @Serializable data class TrailConditionTag(val value: String, val riders: Int)

/** `app.activitytracker.feed.defs#trailReportView` */
@Immutable
@Serializable
data class TrailReportView(
    val uri: AtUri,
    val cid: Cid,
    val segment: AtUri,
    val author: ProfileViewBasic,
    /** Its known [TrailCondition] tags, in order. */
    val conditions: List<String>,
    val note: String? = null,
    val images: List<ActivityMediaView.Image>,
    /** When the AppView first indexed it, which dates it. */
    val indexedAt: Instant,
    /** Indexed in the last 14 days. */
    val current: Boolean,
    /** Labels on the report, which apply to its photos; its author's are on [author]. Null when there are none. */
    val labels: List<LabelView>? = null,
)

/** `app.activitytracker.feed.defs#trailPhotoView`: one photo of a trail report. */
@Immutable
@Serializable
data class TrailPhotoView(
    val image: ActivityMediaView.Image,
    /** The report holding it. */
    val report: AtUri,
    /** The report's CID, for a report on the photo. */
    val cid: Cid,
    /** Its place among the report's photos, from 1. */
    val position: Int,
    val segment: AtUri,
    val author: ProfileViewBasic,
    val conditions: List<String> = emptyList(),
    val indexedAt: Instant,
    val labels: List<LabelView>? = null,
)

object GetTrailReports {
    @Serializable data class Params(val segment: AtUri, val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val reports: List<TrailReportView>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.FEED_GET_TRAIL_REPORTS, Params.serializer(), Output.serializer())
}

object GetTrailPhotos {
    /** Exactly one of [segment] and [path]: one segment's photos, or those on every segment of a trail or road. */
    @Serializable data class Params(val segment: AtUri? = null, val path: AtUri? = null, val limit: Int? = null, val cursor: String? = null)

    @Serializable data class Output(val photos: List<TrailPhotoView>, val cursor: String? = null)

    val method = XrpcQuery(Nsid.FEED_GET_TRAIL_PHOTOS, Params.serializer(), Output.serializer())
}

object GetPathConditions {
    @Serializable data class Params(val path: AtUri)

    /**
     * Each listed segment with current conditions, closed ones first, then newest; [segmentCount] the segments the path lists for the
     * viewer; and the path's [photoCount] and 5 newest [photos].
     */
    @Serializable
    data class Output(
        val segments: List<SegmentConditions>,
        val segmentCount: Int,
        val photoCount: Int,
        val photos: List<TrailPhotoView> = emptyList(),
    )

    /** `app.activitytracker.feed.getPathConditions#segmentConditions` */
    @Immutable @Serializable data class SegmentConditions(val segment: AtUri, val name: String, val conditions: TrailConditions)

    val method = XrpcQuery(Nsid.FEED_GET_PATH_CONDITIONS, Params.serializer(), Output.serializer())
}
