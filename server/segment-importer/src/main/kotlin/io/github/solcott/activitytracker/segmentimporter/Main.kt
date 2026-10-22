package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
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
  extract [file.osm.pbf] [--agency <kind>=<file.geojsonl>]... --region <id> --out <candidates.ndjson>
      Reads a region's OpenStreetMap extract and agency trail datasets, at least one of them, and writes its candidate segments, one JSON
      object per line. An agency dataset is a GeoJSON text sequence in WGS 84 (made with ogr2ogr, see the README), of the kind usfs (Forest
      Service trails, TrailNFS_Publish) or nps (Park Service trails). The region id is lower-case letters, digits and dashes, such as
      us-vermont.
  stage <candidates.ndjson>
      Uploads a candidates file to the AppView as one import run, replacing the region's candidates from earlier runs.
  publish --region <id>
      Publishes the region's ridden candidates that duplicate no live segment as feed.segment records.

stage and publish act as the importer's service account: set SEGMENT_IMPORTER_IDENTIFIER, SEGMENT_IMPORTER_PASSWORD (an app password)
and SEGMENT_IMPORTER_PDS_URL, and APPVIEW_URL and APPVIEW_DID unless the AppView is the local one."""

private val REGION_ID = Regex("[a-z0-9-]{1,64}")

fun main(args: Array<String>) {
    val command = parse(args.toList())
    if (command == null) {
        System.err.println(USAGE)
        exitProcess(2)
    }
    when (command) {
        is Command.Extract -> extract(command.pbf, command.agency, command.region, command.out)
        is Command.Stage -> asServiceAccount { stageFile(it, command.file) }
        is Command.Publish -> asServiceAccount { publishRegion(it, command.region) }
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
        } catch (e: PasswordSession.SignInFailed) {
            e.message
        } catch (e: Clients.NotImporter) {
            e.message
        } catch (e: XrpcException) {
            e.message
        } catch (e: IllegalArgumentException) {
            "Can't use this candidates file: ${e.message}"
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
    data class Extract(val pbf: File?, val agency: List<Pair<AgencyRules, File>>, val region: String, val out: File) : Command

    data class Stage(val file: File) : Command

    data class Publish(val region: String) : Command
}

/** [args] as a [Command], or null when they don't make one. */
internal fun parse(args: List<String>): Command? =
    when (args.firstOrNull()) {
        "extract" -> parseExtract(args.drop(1))
        "stage" -> args.drop(1).singleOrNull()?.takeUnless { it.startsWith("--") }?.let { Command.Stage(File(it)) }
        "publish" -> parsePublish(args.drop(1))
        else -> null
    }

@Suppress("ReturnCount", "CyclomaticComplexMethod") // Each way the arguments can fail to make a command.
private fun parseExtract(args: List<String>): Command.Extract? {
    val options = mutableMapOf<String, String>()
    val agency = mutableListOf<Pair<AgencyRules, File>>()
    val positional = mutableListOf<String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (arg.startsWith("--")) {
            val value = args.getOrNull(i + 1) ?: return null
            if (arg == "--agency") {
                val rules = AgencyRules.ofKind(value.substringBefore('=', "")) ?: return null
                agency += rules to File(value.substringAfter('=').takeIf { it.isNotEmpty() } ?: return null)
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
    if (options.keys.any { it !in setOf("--region", "--out") }) return null
    return Command.Extract(positional.singleOrNull()?.let(::File), agency, region, File(out))
}

private fun parsePublish(args: List<String>): Command.Publish? {
    if (args.size != 2 || args[0] != "--region") return null
    return args[1].takeIf { REGION_ID.matches(it) }?.let(Command::Publish)
}
