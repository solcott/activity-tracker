package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.PathSectionSegments
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PathRecord
import io.github.solcott.activitytracker.lexicon.app.PathSection
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere

// Indexing of a `feed.path`'s sections (segments/segment-paths): the importer's segments named after each section of its trail family.

/**
 * Replaces [uri]'s sections with [sections] (segments/segment-paths): each named, its record keys naming segments in [author]'s repo, the
 * importer's. A blank-named section, a key that isn't one, and anything past the lexicon's limits are skipped; a key in two sections stays
 * in the first. Keys naming no indexed segment are stored too, and simply never served.
 */
internal fun JdbcTransaction.replaceSections(uri: AtUri, author: Did, sections: List<PathSection>) {
    PathSectionSegments.deleteWhere { pathUri eq uri.value }
    val rows = linkedMapOf<String, Pair<String, Int>>()
    sections.take(PathRecord.MAX_SECTIONS).forEachIndexed { order, section ->
        val name = section.name.trim().takeIf { it.isNotEmpty() }?.take(MAX_NAME_LENGTH) ?: return@forEachIndexed
        for (key in section.segments.take(PathSection.MAX_SEGMENTS)) {
            if (!RECORD_KEY.matches(key)) continue
            rows.putIfAbsent("at://${author.value}/${Nsid.FEED_SEGMENT}/$key", name to order)
        }
    }
    PathSectionSegments.batchInsert(rows.entries) { (segment, section) ->
        this[PathSectionSegments.pathUri] = uri.value
        this[PathSectionSegments.segmentUri] = segment
        this[PathSectionSegments.sectionName] = section.first
        this[PathSectionSegments.sectionOrder] = section.second
    }
}

/** An atproto record key: 1 to 512 of its allowed characters, never `.` or `..`. */
private val RECORD_KEY = Regex("""^(?!\.{1,2}$)[A-Za-z0-9._:~-]{1,512}$""")
