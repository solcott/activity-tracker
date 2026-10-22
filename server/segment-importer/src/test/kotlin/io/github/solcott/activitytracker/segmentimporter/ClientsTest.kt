package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetRiddenSegmentCandidates
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class ClientsTest {
    private val fake = FakeServices()
    private val clients = Clients(fake.http, fake.settings)

    private suspend fun listRidden() = clients.asImporter {
        clients.appView.query(GetRiddenSegmentCandidates.method, GetRiddenSegmentCandidates.Params("us-vermont"))
    }

    @Test
    fun appViewCallsCarryServiceAuthForTheMethod() = runTest {
        fake.on(Nsid.IMPORTER_GET_RIDDEN_SEGMENT_CANDIDATES) { json("""{"candidates":[]}""") }
        listRidden()
        val call = fake.calls(Nsid.IMPORTER_GET_RIDDEN_SEGMENT_CANDIDATES).single()
        assertEquals(FakeServices.APPVIEW, "http://${call.host}")
        assertEquals("Bearer svc:${FakeServices.APPVIEW_DID}:${Nsid.IMPORTER_GET_RIDDEN_SEGMENT_CANDIDATES}", call.auth)
        assertEquals(FakeServices.DID, clients.session.did().value)
    }

    @Test
    fun notTheConfiguredImporterSaysSo() = runTest {
        fake.on(Nsid.IMPORTER_GET_RIDDEN_SEGMENT_CANDIDATES) { error(HttpStatusCode.Forbidden, "NotImporter") }
        val refused = assertFailsWith<Clients.NotImporter> { listRidden() }
        assertEquals(true, "SEGMENT_IMPORTER_DID" in refused.message.orEmpty())
    }
}
