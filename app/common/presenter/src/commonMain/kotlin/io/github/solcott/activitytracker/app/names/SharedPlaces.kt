package io.github.solcott.activitytracker.app.names

import io.github.solcott.activitytracker.data.segments.SharedPlace
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.DiveSiteViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic

/** [segment] as a post sharing a proposal for it shows it until the AppView lists the post (feed/posts). */
fun sharedPlaceOf(segment: SegmentView): SharedPlace =
    SharedPlace(
        segment =
            SegmentViewBasic(
                uri = segment.uri,
                name = segment.currentName,
                sport = segment.record.sport,
                distanceMeters = segment.distanceMeters,
                status = segment.status,
                kind = segment.record.kind?.takeIf { it != SegmentKind.RUN },
            ),
    )

/** [site] as a post sharing a proposal for it shows it, as for a segment. */
fun sharedPlaceOf(site: DiveSiteView): SharedPlace = SharedPlace(diveSite = DiveSiteViewBasic(site.uri, site.currentName, site.status))
