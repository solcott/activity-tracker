package io.github.solcott.activitytracker.segmentimporter

import java.io.File
import java.io.Writer
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The sites file's format, apart from the candidates file's; bump it when what a line holds changes. */
internal const val SITES_VERSION = 1

/** The radius of every OSM dive site, in meters: wide enough for a boat's drift or a late GPS fix at the surface. */
internal const val OSM_SITE_RADIUS_M = 150

/** The first line of a sites file. */
@Serializable
internal data class SitesHeader(
    val region: String,
    /** When the OSM extract's data was current, from its header; null when it doesn't say. */
    val extractTimestamp: Instant?,
    val sitesVersion: Int,
)

/** Each later line: one dive site, to publish as a `feed.diveSite` under [id]. */
@Serializable
internal data class SiteEntry(
    /** From the OSM element's type and id alone, so a re-extract keeps it; a record key. */
    val id: String,
    val name: String,
    /** The centre, in degrees times 1e7. */
    val lat: Int,
    val lon: Int,
    /** In meters. */
    val radius: Int,
    val source: String,
    val license: String,
)

private val json = Json { explicitNulls = false }

/** Reads [pbf]'s dive sites and writes them for [region] to [out]. */
internal fun extractSites(pbf: File, region: String, out: File) {
    val data = OsmReader.readDiveSites(pbf)
    val sites = data.diveSites()
    out.bufferedWriter().use { writeSites(it, region, data.timestamp, sites) }
    println("Wrote ${sites.size} dive sites for $region to $out")
}

/** The sites file: a header line, then one line per site, in the order given. */
internal fun writeSites(out: Writer, region: String, timestamp: Instant?, sites: List<OsmDiveSite>) {
    out.appendLine(json.encodeToString(SitesHeader.serializer(), SitesHeader(region, timestamp, SITES_VERSION)))
    for (site in sites) out.appendLine(json.encodeToString(SiteEntry.serializer(), entryOf(site)))
}

internal fun entryOf(site: OsmDiveSite): SiteEntry =
    SiteEntry(
        siteIdOf(site.type, site.id),
        site.name,
        (site.at.lat * E7).roundToInt(),
        (site.at.lng * E7).roundToInt(),
        OSM_SITE_RADIUS_M,
        SOURCE_OSM,
        LICENSE_OSM,
    )

/** A site's id: `osm-node-<id>`, `osm-way-<id>` or `osm-relation-<id>`, which are valid record keys. */
internal fun siteIdOf(type: OsmMember.Type, id: Long): String = "osm-${type.name.lowercase()}-$id"

private const val SOURCE_OSM = "osm"

private const val E7 = 1e7

/** A sites file: its [region] and its sites, in file order. */
internal data class SitesFile(val region: String, val sites: List<SiteEntry>)

/** Reads a sites file's [lines]; throws [IllegalArgumentException] for an empty file or one of another format version. */
internal fun readSites(lines: Sequence<String>): SitesFile {
    val iterator = lines.filter { it.isNotBlank() }.iterator()
    require(iterator.hasNext()) { "an empty sites file" }
    val header = json.decodeFromString(SitesHeader.serializer(), iterator.next())
    require(header.sitesVersion == SITES_VERSION) {
        "a sites file of format version ${header.sitesVersion}; this importer reads version $SITES_VERSION"
    }
    val sites = mutableListOf<SiteEntry>()
    for (line in iterator) sites += json.decodeFromString(SiteEntry.serializer(), line)
    return SitesFile(header.region, sites)
}
