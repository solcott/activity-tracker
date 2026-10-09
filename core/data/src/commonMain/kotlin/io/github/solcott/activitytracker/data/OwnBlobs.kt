package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments

/** A blob on the athlete's own PDS, for showing one (a new avatar or photo) before the AppView has indexed the record that holds it. */
internal fun ownBlobUrl(pdsUrl: String, did: Did, cid: String): String =
    URLBuilder(pdsUrl)
        .apply {
            appendPathSegments("xrpc", "com.atproto.sync.getBlob")
            parameters.append("did", did.value)
            parameters.append("cid", cid)
        }
        .buildString()
