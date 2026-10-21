package io.github.solcott.activitytracker.segmentimporter

import java.io.File
import kotlin.system.exitProcess

/** The importer's commands, parsed by hand: a few subcommands don't need a CLI library. */
private const val USAGE =
    """Usage: segment-importer <command>

Commands:
  extract <file.osm.pbf> --region <id> --out <candidates.ndjson>
      Reads an OpenStreetMap extract and writes the region's candidate segments, one JSON object per line.
      The region id is lower-case letters, digits and dashes, such as us-vermont."""

private val REGION_ID = Regex("[a-z0-9-]{1,64}")

fun main(args: Array<String>) {
    val command = parse(args.toList())
    if (command == null) {
        System.err.println(USAGE)
        exitProcess(2)
    }
    when (command) {
        is Command.Extract -> extract(command.pbf, command.region, command.out)
    }
}

/** A parsed command line. */
internal sealed interface Command {
    data class Extract(val pbf: File, val region: String, val out: File) : Command
}

/** [args] as a [Command], or null when they don't make one. */
internal fun parse(args: List<String>): Command? =
    when (args.firstOrNull()) {
        "extract" -> parseExtract(args.drop(1))
        else -> null
    }

@Suppress("ReturnCount") // Each way the arguments can fail to make a command.
private fun parseExtract(args: List<String>): Command.Extract? {
    val options = mutableMapOf<String, String>()
    val positional = mutableListOf<String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (arg.startsWith("--")) {
            options[arg] = args.getOrNull(i + 1) ?: return null
            i += 2
        } else {
            positional += arg
            i++
        }
    }
    val pbf = positional.singleOrNull() ?: return null
    val region = options["--region"]?.takeIf { REGION_ID.matches(it) } ?: return null
    val out = options["--out"] ?: return null
    if (options.keys.any { it !in setOf("--region", "--out") }) return null
    return Command.Extract(File(pbf), region, File(out))
}
