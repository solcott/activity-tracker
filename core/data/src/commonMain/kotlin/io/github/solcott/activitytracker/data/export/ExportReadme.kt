package io.github.solcott.activitytracker.data.export

/**
 * `README.txt` in a data export: what the archive holds, how it's laid out and what it leaves out (auth/data-export). In English, like the
 * archive's file names: it's read outside the app, where the app's language isn't known.
 */
internal object ExportReadme {
    fun text(manifest: ExportManifest): String = buildString {
        appendLine("Activity Tracker data export")
        appendLine("============================")
        appendLine()
        appendLine("Account: ${manifest.handle} (${manifest.did}), hosted on ${manifest.pds}")
        appendLine("Exported: ${manifest.exportedAt}")
        appendLine()
        appendLine("This archive holds everything Activity Tracker could read of yours, including private data:")
        appendLine("exact tracks (not trimmed by your privacy zones), your privacy zones, private profile details and")
        appendLine("non-public activities. Keep it somewhere safe.")
        appendLine()
        appendLine("What's here")
        appendLine("-----------")
        appendLine("activities/     One file per activity: the file it was imported from, else a FIT file rebuilt from what")
        appendLine("                the app stores (no sub-second timing or fields the app doesn't keep). Manual entries")
        appendLine("                with no track or streams have none. manifest.json maps each activity to its file.")
        appendLine("records/        Every Activity Tracker record of yours, as JSON exactly as your PDS stores it, by where it")
        appendLine("                lives: repo/ (public, in your atproto repository) or followers/, mutuals/, self/ and")
        appendLine("                interactions/ (your private athlete spaces), then collection and record key. A ':' in")
        appendLine("                a record key is written %3A.")
        appendLine("blobs/          The files those records name (streams, tracks, photos...), by place and CID. Original")
        appendLine("                imported files are in activities/ instead; manifest.json maps their CIDs there.")
        appendLine("manifest.json   Counts, the activity-to-file map, and anything that couldn't be read.")
        appendLine()
        appendLine("What's not here")
        appendLine("---------------")
        appendLine("- Segment efforts, leaderboard places, group pairings and segment statuses: the Activity Tracker")
        appendLine("  server works these out; they aren't records of yours.")
        appendLine("- Other apps' records (Bluesky posts, follows, blocks...) and other athletes' data.")
        appendLine("- Your atproto account itself. Your PDS (${manifest.pds}) can export your whole repository and")
        appendLine("  account; ask it, or use its settings.")
        if (!manifest.spacesIncluded) {
            appendLine("- Your private data: this sign-in couldn't read your athlete spaces. Sign in again and export")
            appendLine("  again to include them.")
        }
        if (manifest.unsentChanges > 0) {
            appendLine("- ${manifest.unsentChanges} change(s) still waiting to upload from the device that made this export.")
        }
        if (manifest.problems.isNotEmpty()) {
            appendLine("- ${manifest.problems.size} item(s) that couldn't be read; manifest.json lists them.")
        }
    }
}
