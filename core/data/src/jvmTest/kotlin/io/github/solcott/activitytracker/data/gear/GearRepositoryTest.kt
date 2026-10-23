package io.github.solcott.activitytracker.data.gear

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.data.FakeAthletePds
import io.github.solcott.activitytracker.data.activity.HeldPhotos
import io.github.solcott.activitytracker.data.activity.NewPhoto
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActorGearItem
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearSportTotals
import io.github.solcott.activitytracker.lexicon.app.GearStarting
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.GearUsage
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.activitytracker.lexicon.app.GearYearTotals
import io.github.solcott.activitytracker.lexicon.app.GetGear
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import io.ktor.http.encodeURLParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

/** The athlete's gear (`gear/gear-items`): one `gear.item` per item, in the repo or the tier space of its visibility. */
class GearRepositoryTest {
    private val pds = FakeAthletePds()
    private val created = Instant.parse("2026-10-20T12:00:00Z")
    private val bike = GearItemRecord("Stumpjumper", GearType.BIKE, created, kind = "mountain")
    private val shoes = GearItemRecord("Speedgoat 5", GearType.SHOES, created)
    private val helmet = GearItemRecord("Helmet", GearType.HELMET, created)
    private val photo = NewPhoto(byteArrayOf(1, 2, 3, 4), "image/webp", 4, 3)

    private suspend fun gear(scope: String? = null): GearRepository {
        val s = pds.session(scope)
        return GearRepository(s.clients, Clock.System, s.feed, Logger)
    }

    private fun store(place: String, rkey: String, record: GearItemRecord) {
        pds.stored.getOrPut(place) { mutableMapOf() }["${Nsid.GEAR_ITEM}/$rkey"] =
            AtJson.encodeToJsonElement(GearItemRecord.serializer(), record) as JsonObject
    }

    private fun placesOf(rkey: String) = pds.placesOf(Nsid.GEAR_ITEM, rkey)

    @Test
    fun itemsComeFromTheRepoAndEveryTier() = runTest {
        store(pds.repo, "3bike", bike)
        store(Visibility.FOLLOWERS, "3helmet", helmet)
        store(Visibility.MUTUALS, "3skis", GearItemRecord("Skis", GearType.SKI, created))
        store(Visibility.SELF, "3shoes", shoes)

        val items = gear().items().associateBy { it.rkey }

        assertEquals(
            mapOf(
                "3bike" to Visibility.PUBLIC,
                "3helmet" to Visibility.FOLLOWERS,
                "3skis" to Visibility.MUTUALS,
                "3shoes" to Visibility.SELF,
            ),
            items.mapValues { it.value.visibility },
        )
        assertEquals(bike, items.getValue("3bike").record)
    }

    @Test
    fun anItemInTwoPlacesIsListedOnceAtTheNarrower() = runTest {
        store(pds.repo, "3bike", bike)
        store(Visibility.SELF, "3bike", bike.copy(name = "Stumpy"))

        val item = gear().items().single()

        assertEquals(Visibility.SELF, item.visibility)
        assertEquals("Stumpy", item.record.name)
        assertEquals(setOf(Visibility.PUBLIC, Visibility.SELF), item.places)
    }

    @Test
    fun aNewOnlyMeItemGoesInSelfOnly() = runTest {
        val item = gear().save(null, shoes, Visibility.SELF)

        assertEquals(setOf(Visibility.SELF), placesOf(item.rkey))
        assertEquals(listOf("put ${Visibility.SELF} ${Nsid.GEAR_ITEM}/${item.rkey}"), pds.writes)
    }

    @Test
    fun onlyMeToPublicKeepsTheRkeyAndLeavesSelf() = runTest {
        val repo = gear()
        val item = repo.save(null, bike, Visibility.SELF)

        val moved = repo.save(item, bike, Visibility.PUBLIC)

        assertEquals(item.rkey, moved.rkey)
        assertEquals(setOf(pds.repo), placesOf(item.rkey))
        val key = "${Nsid.GEAR_ITEM}/${item.rkey}"
        assertEquals(listOf("put ${Visibility.SELF} $key", "put ${pds.repo} $key", "delete ${Visibility.SELF} $key"), pds.writes)
    }

    @Test
    fun aFailedDeleteAfterTheFirstWriteKeepsBothAndSavingAgainFinishes() = runTest {
        val repo = gear()
        store(Visibility.MUTUALS, "3helmet", helmet)
        pds.created += Visibility.MUTUALS
        val item = repo.items().single()

        pds.failSpaceDeletes = true
        assertFailsWith<Exception> { repo.save(item, helmet, Visibility.SELF) }
        assertEquals(setOf(Visibility.MUTUALS, Visibility.SELF), placesOf("3helmet"))
        val listed = repo.items().single()
        assertEquals(Visibility.SELF, listed.visibility)

        pds.failSpaceDeletes = false
        repo.save(listed, helmet, Visibility.SELF)
        assertEquals(setOf(Visibility.SELF), placesOf("3helmet"))
    }

    @Test
    fun theFirstFollowersItemCreatesTheSpace() = runTest {
        val item = gear().save(null, helmet, Visibility.FOLLOWERS)

        assertEquals(listOf(Visibility.FOLLOWERS), pds.created)
        assertEquals(listOf(Visibility.FOLLOWERS), pds.announced)
        assertEquals(setOf(Visibility.FOLLOWERS), placesOf(item.rkey))
    }

    @Test
    fun aPhotoMovesWithItsItem() = runTest {
        val repo = gear()
        val item = repo.save(null, bike, Visibility.SELF, GearPhotoEdit.Replace(photo))
        val cid = assertNotNull(item.record.photo).image.ref.link
        assertEquals(HeldPhotos.url(cid), item.photoUrl)
        assertEquals(1, pds.uploads)

        val moved = repo.save(item, bike, Visibility.PUBLIC)

        assertEquals(2, pds.uploads)
        assertEquals(cid, moved.record.photo?.image?.ref?.link)
        assertTrue(moved.photoUrl!!.contains("com.atproto.sync.getBlob"))
        val stored =
            AtJson.decodeFromJsonElement(
                GearItemRecord.serializer(),
                pds.stored.getValue(pds.repo).getValue("${Nsid.GEAR_ITEM}/${item.rkey}"),
            )
        assertEquals(cid, stored.photo?.image?.ref?.link)
    }

    @Test
    fun removingAPhotoDropsIt() = runTest {
        val repo = gear()
        val item = repo.save(null, bike, Visibility.SELF, GearPhotoEdit.Replace(photo))

        val saved = repo.save(item, bike, Visibility.SELF, GearPhotoEdit.Remove)

        assertNull(saved.record.photo)
        assertNull(saved.photoUrl)
    }

    @Test
    fun deleteRemovesTheItemFromEveryPlace() = runTest {
        store(pds.repo, "3bike", bike)
        store(Visibility.SELF, "3bike", bike)
        val repo = gear()

        repo.delete(repo.items().single())

        assertEquals(emptySet(), placesOf("3bike"))
        assertTrue(repo.items().isEmpty())
    }

    @Test
    fun anOlderSessionCantSavePrivateGearButCanSavePublic() = runTest {
        val repo = gear(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")

        assertFailsWith<GearNeedsSignInException> { repo.save(null, shoes, Visibility.SELF) }
        assertTrue(pds.writes.isEmpty())

        val item = repo.save(null, shoes, Visibility.PUBLIC)
        assertEquals(setOf(pds.repo), placesOf(item.rkey))
        assertNotEquals(true, repo.canSavePrivate)
    }

    private val tarmac = AtUri.of(pds.did, Nsid.GEAR_ITEM, "3tarmac")
    private val totals =
        GearTotals(activities = 3, movingTime = 9_000, distance = 150_000, elevationGain = 900, elevationLoss = 900, days = 2)

    private fun ride(rkey: String) =
        ActivityView(
            uri = AtUri.of(pds.did, Nsid.FEED_ACTIVITY, rkey),
            cid = Cid("bafy$rkey"),
            author = ProfileViewBasic(pds.did, Handle("alice.test")),
            record = ActivityRecord(Sport.RIDE, Instant.parse("2026-10-05T07:00:00Z"), 3600, created),
            likeCount = 0,
            commentCount = 0,
            indexedAt = created,
        )

    @Test
    fun usageAsksForTheItemAndParsesItsTotalsAndBreakdowns() = runTest {
        val served =
            GetGear.Output(
                gear = GearView(tarmac, "Tarmac", GearType.BIKE),
                usage =
                    GearUsage(
                        totals = totals.copy(distance = 2_150_000),
                        bySport = listOf(GearSportTotals(Sport.RIDE, totals)),
                        byYear = listOf(GearYearTotals(2026, totals)),
                        starting = GearStarting(distance = 2_000_000),
                    ),
                retired = false,
                visibility = Visibility.PUBLIC,
            )
        pds.gearUsage[tarmac.value] = served

        assertEquals(served, gear().usage(tarmac))
        assertEquals(listOf("/xrpc/${Nsid.GEAR_GET_GEAR}?uri=${tarmac.value.encodeURLParameter()}"), pds.gearQueries)
    }

    @Test
    fun anItemTheViewerMayNotSeeIsNotFound() = runTest {
        val usage = gear().usageOutcome(tarmac).toList().last()
        assertEquals("NotFound", assertIs<DataError.Api>(assertIs<Outcome.Error>(usage).cause).code)
        val page = gear().activitiesPage(tarmac, null)
        assertEquals("NotFound", assertIs<DataError.Api>(assertIs<Outcome.Error>(page).cause).code)
    }

    @Test
    fun actorGearReadsEveryPage() = runTest {
        pds.actorGear =
            (1..150).map { ActorGearItem(GearView(AtUri.of(pds.did, Nsid.GEAR_ITEM, "3g$it"), "Item $it", GearType.OTHER), totals, false) }

        val items = gear().actorGear(pds.did.value)

        assertEquals(150, items.size)
        assertEquals(
            listOf(
                "/xrpc/${Nsid.GEAR_GET_ACTOR_GEAR}?actor=${pds.did.value.encodeURLParameter()}&limit=100",
                "/xrpc/${Nsid.GEAR_GET_ACTOR_GEAR}?actor=${pds.did.value.encodeURLParameter()}&limit=100&cursor=100",
            ),
            pds.gearQueries,
        )
    }

    @Test
    fun activitiesPageSendsTheCursorAndReturnsTheNextOne() = runTest {
        pds.gearActivities[tarmac.value] = (1..40).map { ride("3r$it") }
        val repository = gear()

        val first = assertIs<Outcome.Data<Page<ActivityView, Unit>>>(repository.activitiesPage(tarmac, null)).data
        assertEquals(30, first.items.size)
        assertEquals("30", first.next)
        val second = assertIs<Outcome.Data<Page<ActivityView, Unit>>>(repository.activitiesPage(tarmac, first.next)).data
        assertEquals(listOf("3r31", "3r40"), listOf(second.items.first(), second.items.last()).map { it.uri.rkey })
        assertNull(second.next)
        assertEquals(
            listOf(
                "/xrpc/${Nsid.GEAR_GET_GEAR_ACTIVITIES}?uri=${tarmac.value.encodeURLParameter()}",
                "/xrpc/${Nsid.GEAR_GET_GEAR_ACTIVITIES}?uri=${tarmac.value.encodeURLParameter()}&cursor=30",
            ),
            pds.gearQueries,
        )
    }
}
