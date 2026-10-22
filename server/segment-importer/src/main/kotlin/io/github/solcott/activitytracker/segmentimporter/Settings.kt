package io.github.solcott.activitytracker.segmentimporter

/**
 * The service account `stage` and `publish` act as (segments/segment-import), from the environment so the password never reaches the
 * command line. The AppView's `SEGMENT_IMPORTER_DID` must name the same account.
 */
internal data class Settings(
    /** The account's handle or DID (`SEGMENT_IMPORTER_IDENTIFIER`). */
    val identifier: String,
    /** Its app password (`SEGMENT_IMPORTER_PASSWORD`). */
    val password: String,
    /** The PDS that hosts it (`SEGMENT_IMPORTER_PDS_URL`). */
    val pdsUrl: String,
    /** Where the AppView is served (`APPVIEW_URL`). */
    val appViewUrl: String,
    /** The AppView's DID, the `aud` of its service-auth tokens (`APPVIEW_DID`). */
    val appViewDid: String,
) {
    override fun toString() = "Settings($identifier at $pdsUrl, AppView $appViewDid at $appViewUrl)"

    /** Settings that can't be used, naming each missing variable. */
    class Missing(val names: List<String>) : Exception("Set ${names.joinToString()} for the importer's service account")

    companion object {
        const val DEFAULT_APPVIEW_URL = "http://localhost:8081"
        const val DEFAULT_APPVIEW_DID = "did:web:localhost%3A8081"

        /** The settings in [env]; throws [Missing] when the account's identifier, password or PDS isn't set. */
        fun fromEnv(env: Map<String, String> = System.getenv()): Settings {
            fun value(key: String) = env[key]?.trim()?.takeIf { it.isNotEmpty() }
            val required = listOf("SEGMENT_IMPORTER_IDENTIFIER", "SEGMENT_IMPORTER_PASSWORD", "SEGMENT_IMPORTER_PDS_URL")
            val (identifier, password, pdsUrl) = required.map(::value)
            if (identifier == null || password == null || pdsUrl == null) throw Missing(required.filter { value(it) == null })
            return Settings(
                identifier = identifier,
                password = password,
                pdsUrl = pdsUrl.trimEnd('/'),
                appViewUrl = (value("APPVIEW_URL") ?: DEFAULT_APPVIEW_URL).trimEnd('/'),
                appViewDid = value("APPVIEW_DID") ?: DEFAULT_APPVIEW_DID,
            )
        }
    }
}
