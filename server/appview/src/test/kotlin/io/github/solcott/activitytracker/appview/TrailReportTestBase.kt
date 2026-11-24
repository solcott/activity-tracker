package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.LabelSources
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.Visibility
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update

/** Trail reports (segments/trail-reports): an active segment, riders' efforts on it and their reports, on a clock the tests move. */
open class TrailReportTestBase : SegmentTestBase() {
    protected val labeler = "did:plc:labeler"
    protected val clock = MutableClock(Instant.parse("2026-11-20T12:00:00Z"))
    protected val idx = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" }, clock, Did(importer))
    protected val seg = segmentUri("3ks", importer)
    private var effortIds = 0

    @AfterTest fun forgetLabelers() = run { LabelSources.trusted = emptyList() }

    protected fun later(by: Duration) = run { clock.now += by }

    /** A trusted label [value] on [uri] (a record or an account), or its negation. */
    protected fun label(uri: String, value: String, neg: Boolean = false, cts: String = "2026-11-29T10:00:00Z") = runBlocking {
        LabelSources.trusted = listOf(labeler)
        LabelStore(db, labeler, null, clock).apply(labeler, 1, listOf(testLabel(labeler, uri, value, cts, labelExtras(neg = neg))))
    }

    /** An active ride segment of the importer's. */
    protected fun activeSegment(rkey: String = "3ks", kind: String? = null) {
        commitAs(importer, Nsid.FEED_SEGMENT, rkey, segmentJson(sport = "ride", name = "Wiggly Woods"))
        runBlocking {
            db.tx {
                Segments.update({ Segments.uri eq segmentUri(rkey, importer) }) {
                    it[status] = SegmentStatus.ACTIVE
                    if (kind != null) it[Segments.kind] = kind
                }
            }
        }
    }

    /** An effort by [did] on [segment] that began [ago] before now, at [visibility]. */
    protected fun effort(did: String, ago: Duration = 2.days, segment: String = seg, visibility: String = Visibility.SELF) = runBlocking {
        val began = clock.now - ago
        db.tx {
            SegmentEfforts.insert {
                it[id] = "effort-${effortIds++}"
                it[segmentUri] = segment
                it[activityUri] = activityUri("3k$effortIds", did)
                it[trackUri] = trackUri("3k$effortIds", did)
                it[authorDid] = did
                it[sport] = "ride"
                it[eBike] = false
                it[startOffsetS] = 0
                it[elapsedS] = 600
                it[SegmentEfforts.visibility] = visibility
                it[startedOn] =
                    java.time.Instant.ofEpochMilli(began.toEpochMilliseconds()).atOffset(ZoneOffset.UTC).toLocalDate().toString()
                it[startedAt] = began.toEpochMilliseconds()
            }
        }
    }

    protected fun commitAs(did: String, collection: String, rkey: String, record: String?) = runBlocking {
        idx.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-11-20T12:00:00.000000Z",
                operation = if (record == null) "delete" else "create",
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafy$rkey${record?.hashCode()}",
            ),
        )
    }

    protected fun reportUri(rkey: String, did: String = alice) = "at://$did/${Nsid.FEED_TRAIL_REPORT}/$rkey"

    /** [did]'s report on [segment] with [tags], [note] and photos with the blob CIDs [photos]. */
    @Suppress("LongParameterList")
    protected fun report(
        rkey: String,
        did: String = alice,
        tags: List<String> = listOf("muddy"),
        note: String? = null,
        photos: List<String> = emptyList(),
        segment: String = seg,
    ) = commitAs(did, Nsid.FEED_TRAIL_REPORT, rkey, reportJson(tags, note, photos, segment))

    protected fun reportJson(tags: List<String>, note: String?, photos: List<String>, segment: String = seg): String {
        val conditions = tags.joinToString(",") { "\"$it\"" }
        val images =
            photos.joinToString(",") {
                """{"image":{"${'$'}type":"blob","ref":{"${'$'}link":"$it"},"mimeType":"image/jpeg","size":1000},"alt":"Photo $it",""" +
                    """"aspectRatio":{"width":4,"height":3}}"""
            }
        val noteJson = note?.let { ""","note":"$it"""" }.orEmpty()
        return """{"segment":{"uri":"$segment","cid":"bafyseg"},"conditions":[$conditions],"images":[$images]$noteJson,""" +
            """"createdAt":"2026-11-20T00:00:00Z"}"""
    }

    /** A photo's blob CID. */
    protected fun photoCid(n: Int) = "bafkphoto$n"
}
