package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.AspectRatio
import io.github.solcott.activitytracker.lexicon.app.GetPathConditions
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.github.solcott.activitytracker.lexicon.app.TrailCondition
import io.github.solcott.activitytracker.lexicon.app.TrailConditionTag
import io.github.solcott.activitytracker.lexicon.app.TrailConditions
import io.github.solcott.activitytracker.lexicon.app.TrailPhotoView
import io.github.solcott.activitytracker.lexicon.app.TrailReportView
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.TimeZone

/**
 * Trail reports' sample states (segments/trail-reports): the Segment page's Conditions card in each of its states, the report sheet, adding
 * photos, the reports list, the photo viewer and the Trail page's card, on the samples' clock ([Fixed.NOW]) in UTC.
 */
object TrailReportSamples {
    private val zone = TimeZone.UTC
    private val maya = fakeProfileBasic(Fixed.BOB).copy(displayName = "Maya Chen")
    private val sam = fakeProfileBasic(Fixed.CAROL).copy(displayName = "Sam Ortiz")
    private val segmentUri: AtUri = SegmentSamples.segment.uri

    private fun photoUrl(n: Int) = "https://appview.test/img/media/${maya.did.value}/bafkphoto$n"

    private val photoAspect = AspectRatio(4, 3)

    private fun image(n: Int, alt: String) = ActivityMediaView.Image(photoUrl(n), alt, photoAspect)

    private val mayas =
        TrailReportView(
            uri = Fixed.uri(maya.did, Nsid.FEED_TRAIL_REPORT, "3kreport1"),
            cid = Fixed.cid("3kreport1"),
            segment = segmentUri,
            author = maya,
            conditions = listOf(TrailCondition.MUDDY, TrailCondition.DOWNED_TREE),
            note = "Big tree down just past the second bridge, easy to walk round. Lower half is soup.",
            images = listOf(image(1, "A large fallen tree across a narrow dirt singletrack"), image(2, "A muddy berm")),
            indexedAt = Fixed.NOW - 2.days,
            current = true,
        )

    private val sams =
        TrailReportView(
            uri = Fixed.uri(sam.did, Nsid.FEED_TRAIL_REPORT, "3kreport2"),
            cid = Fixed.cid("3kreport2"),
            segment = segmentUri,
            author = sam,
            conditions = listOf(TrailCondition.WET),
            note = "Roots slick after the rain, otherwise rolling fine.",
            images = emptyList(),
            indexedAt = Fixed.NOW - 9.days,
            current = true,
        )

    private val older =
        TrailReportView(
            uri = Fixed.uri(Fixed.ALICE, Nsid.FEED_TRAIL_REPORT, "3kreport3"),
            cid = Fixed.cid("3kreport3"),
            segment = segmentUri,
            author = fakeProfileBasic(Fixed.ALICE),
            conditions = listOf(TrailCondition.DRY),
            note = "Perfect hero dirt this morning.",
            images = emptyList(),
            indexedAt = Fixed.NOW - 21.days,
            current = false,
        )

    private fun photo(n: Int, report: TrailReportView = mayas, position: Int = n) =
        TrailPhotoView(
            image = image(n, "Photo $n"),
            report = report.uri,
            cid = report.cid,
            position = position,
            segment = segmentUri,
            author = report.author,
            conditions = report.conditions,
            indexedAt = report.indexedAt,
        )

    /** Wiggly Woods this week: Muddy from three riders, a downed tree, Maya's note, and 16 photos. */
    val muddy: TrailConditions =
        TrailConditions(
            lead = TrailCondition.MUDDY,
            leadRiders = 3,
            riders = 3,
            latestAt = Fixed.NOW - 2.days,
            tags = listOf(TrailConditionTag(TrailCondition.DOWNED_TREE, 2), TrailConditionTag(TrailCondition.WET, 1)),
            latestNote = mayas,
            photoCount = 16,
            photos = (1..4).map { photo(it, position = 1) },
        )

    private fun withConditions(conditions: TrailConditions?, viewer: SegmentViewerState? = null): SegmentState {
        val base = SegmentSamples.loaded
        val view: SegmentView = checkNotNull(base.segment).copy(conditions = conditions, viewer = viewer)
        return base.copy(
            content = ContentStates.loaded(view),
            reports = ReportFields(card = TrailReportTexts.card(view, Fixed.NOW, zone, canWrite = true)),
        )
    }

    private val rider = SegmentViewerState(canReportConditions = true, lastEffortAt = Fixed.NOW - 3.days)

    /** The Conditions card with Muddy leading, to a recent rider who may report. */
    val conditions: SegmentState
        get() = withConditions(muddy, rider)

    /** The card leading with Closed, in the error colour. */
    val closed: SegmentState
        get() =
            withConditions(
                TrailConditions(
                    lead = TrailCondition.CLOSED,
                    leadRiders = 1,
                    riders = 2,
                    latestAt = Fixed.NOW - 1.days,
                    tags = listOf(TrailConditionTag(TrailCondition.MUDDY, 1)),
                    latestNote =
                        mayas.copy(
                            conditions = listOf(TrailCondition.CLOSED),
                            note = "Gate locked at the bottom, sign says logging until Dec 1.",
                        ),
                    photoCount = 0,
                ),
            )

    /** Nothing in the last 14 days, to a viewer who rode it 3 days ago: the card asks how it was. */
    val ask: SegmentState
        get() =
            withConditions(
                TrailConditions(
                    riders = 0,
                    tags = emptyList(),
                    lastReportAt = Fixed.NOW - 35.days,
                    lastReportTags = listOf(TrailCondition.DRY),
                    photoCount = 0,
                ),
                rider,
            )

    /** [conditions] with the report sheet open: Muddy and Downed tree chosen, a note and one photo. */
    val sheet: SegmentState
        get() {
            val state = conditions
            val view = checkNotNull(state.segment)
            val chosen = setOf(TrailCondition.MUDDY, TrailCondition.DOWNED_TREE)
            val note = "Big tree down just past the second bridge, easy to walk round."
            return state.copy(
                reports =
                    state.reports.copy(
                        sheet =
                            ReportSheet(
                                subtitle = TrailReportTexts.subtitle(view, Fixed.NOW, zone),
                                surface = TrailReportTexts.toggles(TrailCondition.surface, chosen),
                                onTrail = TrailReportTexts.toggles(TrailCondition.onTrail, chosen),
                                note = textFieldStateOf(note),
                                noteCount = TrailReportTexts.noteCount(TrailReportTexts.noteLength(note)),
                                photos = persistentListOf(ReportPhotoChoice("p1", photoUrl(1), "A fallen tree")),
                            ),
                    ),
            )
        }

    /** [sheet] with the add-photos screen over it: two rides, one Followers only, two photos ticked. */
    val addPhotos: SegmentState
        get() {
            val state = sheet
            fun ride(title: String, visibility: AppText, vararg picks: SamplePick) =
                RidePhotos(
                    AppText.written(title),
                    visibility,
                    picks
                        .map { (key, ticked) -> RidePhoto(key, photoUrl(key.last().digitToInt()), "Photo $key", ticked, enabled = true) }
                        .toImmutableList(),
                )
            return state.copy(
                reports =
                    state.reports.copy(
                        addPhotos =
                            AddPhotos(
                                rides =
                                    persistentListOf(
                                        ride(
                                            "Evening loop",
                                            AppText.named("activity_visibility_badge_followers"),
                                            SamplePick("r1", ticked = true),
                                            SamplePick("r2", ticked = true),
                                            SamplePick("r3", ticked = false),
                                        ),
                                        ride("Saturday shred", AppText.named("visibility_public"), SamplePick("r4", ticked = false)),
                                    ),
                                picked = 3,
                            ),
                    ),
            )
        }

    /** [sheet] asking before it shares a Followers ride's photo publicly. */
    val confirmShare: SegmentState
        get() = sheet.let { it.copy(reports = it.reports.copy(confirmingShare = true)) }

    val all: Map<String, SegmentState>
        get() =
            mapOf(
                CONDITIONS to conditions,
                CLOSED to closed,
                ASK to ask,
                SHEET to sheet,
                ADD_PHOTOS to addPhotos,
                CONFIRM_SHARE to confirmShare,
            )

    const val CONDITIONS = "conditions"
    const val CLOSED = "conditionsClosed"
    const val ASK = "conditionsAsk"
    const val SHEET = "reportSheet"
    const val ADD_PHOTOS = "reportAddPhotos"
    const val CONFIRM_SHARE = "reportConfirmShare"

    private fun row(report: TrailReportView, own: Boolean = false) =
        TrailReportRow(
            uri = report.uri.value,
            cid = report.cid.value,
            author = report.author.shownName,
            authorDid = report.author.did.value,
            avatar = null,
            age = TrailReportTexts.age(report.indexedAt, Fixed.NOW, zone),
            tags = report.conditions.map(TrailReportTexts::tag).toImmutableList(),
            note = report.note,
            photos = report.images.map { PhotoThumb(it.fullsize, it.alt) }.toImmutableList(),
            current = report.current,
            own = own,
        )

    private val rows: List<TrailReportRow>
        get() = listOf(row(mayas), row(sams), row(older, own = true))

    /** Every report on Wiggly Woods: two in the last 14 days, the viewer's own older one. */
    val reports: TrailReportsState
        get() = TrailReportsState(name = "Wiggly Woods", reports = PagedStates.end(rows, Unit))

    /** The reports list's states for each paged-list kind (ui/content-loading). */
    val reportsAll: Map<String, TrailReportsState>
        get() = pagedSamples(rows, Unit) { TrailReportsState(name = "Wiggly Woods", reports = it) } + (SampleKind.LOADED to reports)

    /** Maya's first photo full screen, one of 16. */
    val photos: TrailPhotosState
        get() =
            TrailPhotosState(
                content =
                    ContentStates.loaded(
                        (1..2)
                            .map { n ->
                                TrailPhotoPage(
                                    url = photoUrl(n),
                                    alt = mayas.images[n - 1].alt,
                                    blur = null,
                                    author = "Maya Chen",
                                    age = TrailReportTexts.age(mayas.indexedAt, Fixed.NOW, zone),
                                    tags = TrailReportTexts.tags(mayas.conditions),
                                    report = mayas.uri.value,
                                    position = n,
                                    segment = segmentUri.value,
                                    own = false,
                                )
                            }
                            .toImmutableList(),
                    ),
                total = 16,
            )

    /** Kingdom Loop's roll-up: Ridge Run closed, Wiggly Woods muddy, Lower Loop dry, and the trail's photos. */
    val pathRollUp: GetPathConditions.Output
        get() {
            val importer = Did("did:plc:importer")
            // Keyed by its name's first word.
            fun row(name: String, lead: String, ago: kotlin.time.Duration, riders: Int, extra: List<TrailConditionTag> = emptyList()) =
                GetPathConditions.SegmentConditions(
                    Fixed.uri(importer, Nsid.FEED_SEGMENT, name.substringBefore(' ').lowercase()),
                    name,
                    TrailConditions(
                        lead = lead,
                        leadRiders = riders,
                        riders = riders,
                        latestAt = Fixed.NOW - ago,
                        tags = extra,
                        photoCount = 0,
                    ),
                )
            return GetPathConditions.Output(
                segments =
                    listOf(
                        row("Ridge Run", TrailCondition.CLOSED, 1.days, 2),
                        row(
                            "Wiggly Woods",
                            TrailCondition.MUDDY,
                            2.days,
                            3,
                            listOf(TrailConditionTag(TrailCondition.DOWNED_TREE, 2)),
                        ),
                        row("Lower Loop", TrailCondition.DRY, 5.days, 1),
                    ),
                segmentCount = 8,
                photoCount = 41,
                photos = (1..5).map { photo(it, position = 1) },
            )
        }

    /** The Trail page's Conditions card for [pathRollUp]. */
    val pathCard: PathConditionsCard
        get() = checkNotNull(PathConditionsTexts.card(pathRollUp, Fixed.NOW, zone))

    /** Only so callers can build an instant near the samples' clock. */
    fun hoursAgo(hours: Int): Instant = Fixed.NOW - hours.hours
}

/** One photo of a sample ride, by [key], and whether it is [ticked]. */
private data class SamplePick(val key: String, val ticked: Boolean)
