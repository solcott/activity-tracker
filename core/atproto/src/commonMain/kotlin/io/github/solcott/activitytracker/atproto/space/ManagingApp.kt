package io.github.solcott.activitytracker.atproto.space

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.atproto.DidDocument
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * The AppView as managing app of athletes' spaces: the id a space's `managingAppPolicy` names, which the PDS resolves to call
 * `checkUserAccess`.
 */
object ManagingApp {
    const val FRAGMENT = "activitytracker_space"
    const val SERVICE_TYPE = "AtprotoSpaceService"

    /**
     * The managing-app id (`<service DID>#activitytracker_space`) from the DID document the AppView at [appViewUrl] serves. Taken from the
     * document, not from the URL or the client's audience: the web client reaches the AppView at a localhost address the PDS can't resolve,
     * while the document always names the AppView's public service DID.
     */
    suspend fun resolve(http: HttpClient, appViewUrl: String): String {
        val response = http.get("${appViewUrl.trimEnd('/')}/.well-known/did.json")
        if (!response.status.isSuccess()) throw ManagingAppException("AppView DID document: ${response.status}")
        val doc = AtJson.decodeFromString(DidDocument.serializer(), response.bodyAsText())
        val id = "${doc.id}#$FRAGMENT"
        doc.service.firstOrNull { (it.id == "#$FRAGMENT" || it.id == id) && it.type == SERVICE_TYPE }
            ?: throw ManagingAppException("AppView ${doc.id} lists no $SERVICE_TYPE #$FRAGMENT")
        return id
    }
}

class ManagingAppException(message: String) : Exception(message)
