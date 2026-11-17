package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.terrain.TerrainTiles
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking

/** The importer's commands, parsed by hand: a few subcommands don't need a CLI library. */
private const val USAGE =
    """Usage: segment-importer <command>

Commands:
  extract [file.osm.pbf] [--agency <kind>=<file.geojsonl>]... --region <id> [--lang <code>] --out <candidates.ndjson> [--paths <paths.ndjson>]
          [--terrain <dir> [--terrain-zoom <n>]]
      Reads a region's OpenStreetMap extract and agency trail datasets, at least one of them, and writes its candidate segments, one JSON
      object per line. An agency dataset is a GeoJSON text sequence in WGS 84 (made with ogr2ogr, see the README), of the kind usfs (Forest
      Service trails, TrailNFS_Publish), nps (Park Service trails) or blm (BLM managed trails). The region id is lower-case letters, digits
      and dashes, such as us-vermont. The language, a two-letter ISO 639-1 code (en unless given), picks the generic words names drop,
      such as Trail; a language with no list keeps names as their source has them. With --paths, also writes the region's trails and
      named roads as paths that segments may lie on, one JSON object per line. With --terrain, the folder of terrain tiles
      scripts/terrain-fetch.sh writes (at zoom 12 unless --terrain-zoom says), a whole trail that goes both ways and clearly climbs is named
      "<trail> (uphill)" and "<trail> (downhill)" where the tiles cover it, rather than by compass.
  extract-sites <file.osm.pbf> --region <id> --out <sites.ndjson>
      Reads a region's OpenStreetMap extract and writes its named dive sites (sport=scuba_diving or free_diving, but no shops, amenities,
      clubs, buildings, healthcare or names like "Dive Center" or "Free diving platform"), one JSON object per line, each with a radius from its outline or kind (wreck, reef) and an id from its OSM element.
  publish-sites <sites.ndjson>
      Publishes a sites file's dive sites that have no live site within 50 m as feed.diveSite records, keyed by their ids. A site
      already published is never written again.
  stage <candidates.ndjson>
      Uploads a candidates file to the AppView as one import run, replacing the region's candidates from earlier runs.
  publish --region <id> [--all <candidates.ndjson>]
      Publishes the region's ridden candidates that duplicate no live segment as feed.segment records. With --all and the region's staged
      candidates file, publishes every candidate in it that is still staged and duplicates no live segment, ridden or not.
  handoff
      Republishes the segments and dive sites athletes deleted or left behind while others still used them, as listed by the AppView:
      each becomes a feed.segment or feed.diveSite keyed by its handoff id, with source athlete, CC0-1.0 and no attribution. A handoff
      already republished is never written again.
  publish-paths <paths.ndjson> [--rewrite]
      Publishes as feed.path records the paths in the file that a live segment on no path lies on, as the AppView lists them, and
      rewrites published paths whose name, line or sports changed. Each path names the regions whose files list it: one the file no longer
      holds loses the file's region and is deleted once no region lists it. A road gets a path only once someone has made a segment on it. --rewrite rewrites every published path the file holds, for
      an AppView that missed them.
  refine <candidates.ndjson>
      Gives segments already published from the file's candidates their candidate's line when it's the same path drawn finer, within 6 m
      of the published line all along; a trail that moved further keeps its line. Each also takes its candidate's name.

stage, publish, publish-sites, publish-paths, handoff and refine act as the importer's service account: set SEGMENT_IMPORTER_IDENTIFIER, SEGMENT_IMPORTER_PASSWORD (an app password)
and SEGMENT_IMPORTER_PDS_URL, and APPVIEW_URL and APPVIEW_DID unless the AppView is the local one (Gradle's run task gives the local
one its DID, device mode included). Their repo writes go out in commits of up to 100 records, at most 2,400 commits an hour (under a
relay's limits for a PDS it doesn't trust) unless SEGMENT_IMPORTER_COMMITS_PER_HOUR says otherwise."""

private val REGION_ID = Regex("[a-z0-9-]{1,64}")

private val LANGUAGE = Regex("[a-z]{2}")

fun main(args: Array<String>) {
    val command = parse(args.toList())
    if (command == null) {
        System.err.println(USAGE)
        exitProcess(2)
    }
    when (command) {
        is Command.Extract ->
            extract(
                command.pbf,
                command.agency,
                command.region,
                command.out,
                GenericWords.forLanguage(command.language),
                command.paths,
                command.terrain?.let { TerrainTiles(it, command.terrainZoom) },
            )
        is Command.ExtractSites -> extractSites(command.pbf, command.region, command.out)
        is Command.Stage -> asServiceAccount { stageFile(it, command.file) }
        is Command.Publish -> asServiceAccount { publishRegion(it, command.region, command.all) }
        is Command.Refine -> asServiceAccount { refineFile(it, command.file) }
        is Command.PublishPaths -> asServiceAccount { publishPathsFile(it, command.file, command.rewrite) }
        is Command.PublishSites -> asServiceAccount { publishSitesFile(it, command.file) }
        Command.Handoff -> asServiceAccount { handoffAll(it) }
    }
}

/** Runs [block] as the service account, turning each way it can fail into a message and exit status 1. */
private fun asServiceAccount(block: suspend (Clients) -> Unit) {
    val failure =
        try {
            val settings = Settings.fromEnv()
            HttpClient(CIO) { expectSuccess = false }.use { http -> runBlocking { block(Clients(http, settings)) } }
            null
        } catch (e: Settings.Missing) {
            e.message
        } catch (e: Settings.Invalid) {
            e.message
        } catch (e: PasswordSession.SignInFailed) {
            e.message
        } catch (e: Clients.NotImporter) {
            e.message
        } catch (e: XrpcException) {
            e.message
        } catch (e: IllegalArgumentException) {
            "Can't use this file: ${e.message}"
        } catch (e: IOException) {
            e.message
        }
    if (failure != null) {
        System.err.println(failure)
        exitProcess(1)
    }
}

/** A parsed command line. */
internal sealed interface Command {
    data class Extract(
        val pbf: File?,
        val agency: List<AgencyDataset>,
        val region: String,
        val out: File,
        /** The region's language, as an ISO 639-1 code. */
        val language: String = "en",
        /** Where to write the region's paths, if anywhere. */
        val paths: File? = null,
        /** Terrain tiles to name two-way trails by their climb (segments/segment-direction), and their zoom. */
        val terrain: File? = null,
        val terrainZoom: Int = TerrainTiles.DEFAULT_ZOOM,
    ) : Command

    data class ExtractSites(val pbf: File, val region: String, val out: File) : Command

    data class Stage(val file: File) : Command

    /** [all]: a candidates file of [region] whose every candidate is published, ridden or not. */
    data class Publish(val region: String, val all: File? = null) : Command

    data class Refine(val file: File) : Command

    data class PublishPaths(val file: File, val rewrite: Boolean = false) : Command

    data class PublishSites(val file: File) : Command

    data object Handoff : Command
}

/** [args] as a [Command], or null when they don't make one. */
internal fun parse(args: List<String>): Command? =
    when (args.firstOrNull()) {
        "extract" -> parseExtract(args.drop(1))
        "extract-sites" -> parseExtractSites(args.drop(1))
        "stage" -> args.drop(1).singleOrNull()?.takeUnless { it.startsWith("--") }?.let { Command.Stage(File(it)) }
        "publish" -> parsePublish(args.drop(1))
        "refine" -> args.drop(1).singleOrNull()?.takeUnless { it.startsWith("--") }?.let { Command.Refine(File(it)) }
        "publish-paths" -> parsePublishPaths(args.drop(1))
        "publish-sites" -> args.drop(1).singleOrNull()?.takeUnless { it.startsWith("--") }?.let { Command.PublishSites(File(it)) }
        "handoff" -> Command.Handoff.takeIf { args.size == 1 }
        else -> null
    }

@Suppress("ReturnCount", "CyclomaticComplexMethod") // Each way the arguments can fail to make a command.
private fun parseExtract(args: List<String>): Command.Extract? {
    val options = mutableMapOf<String, String>()
    val agency = mutableListOf<AgencyDataset>()
    val positional = mutableListOf<String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (arg.startsWith("--")) {
            val value = args.getOrNull(i + 1) ?: return null
            if (arg == "--agency") {
                val rules = AgencyRules.ofKind(value.substringBefore('=', "")) ?: return null
                agency += AgencyDataset(rules, File(value.substringAfter('=').takeIf { it.isNotEmpty() } ?: return null))
            } else if (options.put(arg, value) != null) {
                return null
            }
            i += 2
        } else {
            positional += arg
            i++
        }
    }
    if (positional.size > 1 || (positional.isEmpty() && agency.isEmpty())) return null
    val region = options["--region"]?.takeIf { REGION_ID.matches(it) } ?: return null
    val out = options["--out"] ?: return null
    val language = options["--lang"] ?: "en"
    if (!LANGUAGE.matches(language)) return null
    if (options.keys.any { it !in setOf("--region", "--out", "--lang", "--paths", "--terrain", "--terrain-zoom") }) return null
    val zoom = options["--terrain-zoom"]?.let { it.toIntOrNull()?.takeIf { z -> z in 1..TerrainTiles.MAX_ZOOM } ?: return null }
    if (zoom != null && "--terrain" !in options) return null
    return Command.Extract(
        positional.singleOrNull()?.let(::File),
        agency,
        region,
        File(out),
        language,
        options["--paths"]?.let(::File),
        options["--terrain"]?.let(::File),
        zoom ?: TerrainTiles.DEFAULT_ZOOM,
    )
}

/** An extract, then `--region <id>` and `--out <sites.ndjson>`, each once and in any order. */
private fun parseExtractSites(args: List<String>): Command.ExtractSites? {
    val pbf = args.firstOrNull()?.takeUnless { it.startsWith("--") }
    val rest = args.drop(1)
    val options = rest.chunked(2).filter { it.size == 2 }.associate { (name, value) -> name to value }
    val wellFormed = rest.size % 2 == 0 && options.size == rest.size / 2 && options.keys == setOf("--region", "--out")
    val region = options["--region"]?.takeIf { pbf != null && wellFormed && REGION_ID.matches(it) }
    return region?.let { Command.ExtractSites(File(requireNotNull(pbf)), it, File(options.getValue("--out"))) }
}

/** `--region <id>`, then optionally `--all <candidates.ndjson>`, each once and in any order. */
private fun parsePublish(args: List<String>): Command.Publish? {
    val options = args.chunked(2).filter { it.size == 2 }.associate { (name, value) -> name to value }
    val wellFormed = args.size % 2 == 0 && options.size == args.size / 2 && options.keys.all { it in setOf("--region", "--all") }
    val region = options["--region"]?.takeIf { wellFormed && REGION_ID.matches(it) }
    return region?.let { Command.Publish(it, options["--all"]?.let(::File)) }
}

/** A paths file, then optionally `--rewrite`. */
private fun parsePublishPaths(args: List<String>): Command.PublishPaths? {
    val file = args.firstOrNull()?.takeUnless { it.startsWith("--") } ?: return null
    return when (args.drop(1)) {
        emptyList<String>() -> Command.PublishPaths(File(file))
        listOf("--rewrite") -> Command.PublishPaths(File(file), rewrite = true)
        else -> null
    }
}
