package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.HandleCheck
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.reindex.RepoReindexer
import io.github.solcott.activitytracker.appview.sync.BskyBlockBackfill
import io.github.solcott.activitytracker.appview.sync.NotIndexedException
import io.github.solcott.activitytracker.appview.sync.SpaceSyncer
import io.github.solcott.activitytracker.atproto.identity.isResolvableDid
import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import io.ktor.server.response.respondRedirect
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.p
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select

/**
 * The account page (admin/account-tools): look an account up by DID or verified handle, see what the AppView holds for it (through
 * [AccountSummary] only, so identifiers, statuses, counts and times), and, as an operator, re-verify its handle, backfill its Bluesky
 * blocks, reindex its repo or resync its spaces. "Resync spaces" exists only with a [syncer].
 */
class AccountsPage(
    private val db: Database,
    private val indexer: Indexer,
    private val blockBackfill: BskyBlockBackfill,
    private val reindexer: RepoReindexer,
    private val syncer: SpaceSyncer?,
) : AdminPage {
    override val path = "accounts"
    override val title = "Accounts"

    override fun privacyProbes(subject: String): List<String> =
        if (isResolvableDid(subject)) {
            val base = "$path/${subject.encodeURLPathPart()}"
            listOf(path, base, "$base/runs", "$base/spaces")
        } else {
            listOf(path, "$path?q=${subject.encodeURLParameter()}")
        }

    override fun AdminRoutes.install() {
        pageRoutes()
        repairRoutes()
    }

    /** The lookup, the account page and its two fragments. */
    private fun AdminRoutes.pageRoutes() {
        get(path) {
            val q = call.request.queryParameters["q"]?.trim()?.takeIf { it.isNotEmpty() }
            val found = q?.let { AccountLookup.of(db, it) }
            if (found is AccountLookup.Found) return@get call.respondRedirect(accountUrl(found.did))
            page(title) {
                lookupForm()
                when (found) {
                    AccountLookup.NoMatch -> p { +"No indexed account has that verified handle. Paste its DID instead." }
                    AccountLookup.Malformed -> p { +"That is neither a DID nor a handle." }
                    else -> Unit
                }
            }
        }
        get("$path/{did}") {
            val did =
                accountDidOf(call.parameters["did"])
                    ?: return@get page(title) {
                        lookupForm()
                        p { +"Not a DID." }
                    }
            val summary = AccountSummary.load(db, did)
            val runs = reindexer.runs(did)
            val operator = admin.isOperator
            page(title) {
                lookupForm()
                account(summary)
                h2 { +"Spaces" }
                spaces(did, summary)
                h2 { +"Reindex runs" }
                runs(did, runs)
                if (operator) actions(did, syncer != null)
            }
        }
        get("$path/{did}/runs") {
            val did = accountDidOf(call.parameters["did"]) ?: return@get fragment { p { +"Not a DID." } }
            val runs = reindexer.runs(did)
            fragment { runs(did, runs) }
        }
        get("$path/{did}/spaces") {
            val did = accountDidOf(call.parameters["did"]) ?: return@get fragment { p { +"Not a DID." } }
            val summary = AccountSummary.load(db, did)
            fragment { spaces(did, summary) }
        }
    }

    /** The operator repairs, each audited with the account's DID as its target. */
    private fun AdminRoutes.repairRoutes() {
        accountAction("recheck-handle", "account.recheckHandle") { recheckHandle(it) }
        accountAction("backfill-blocks", "account.backfillBlocks") { backfillBlocks(it) }
        accountAction("reindex", "account.reindex") { reindex(it) }
        if (syncer != null) {
            accountAction("resync-spaces", "account.resyncSpaces") { did ->
                val queued = syncer.resyncAccount(did)
                call.response.headers.append(ACCOUNT_HX_TRIGGER, ACCOUNT_SPACES_CHANGED)
                ActionResult("$queued spaces queued.", detail = "$queued spaces")
            }
        }
    }

    /** An action on the account whose DID is its `target`, refused when the target isn't a DID. */
    private fun AdminRoutes.accountAction(name: String, key: String, body: suspend ActionCall.(Did) -> ActionResult) =
        action("$path/$name", key) {
            accountDidOf(target)?.let { body(it) } ?: ActionResult.refused("invalid-target", "That is not a DID.")
        }

    private suspend fun recheckHandle(did: Did): ActionResult {
        val check = indexer.recheckHandle(did) ?: return NOT_INDEXED
        val (handle, due) =
            db.tx {
                Actors.select(Actors.handle, Actors.handleCheckDueAt)
                    .where { Actors.did eq did.value }
                    .single()
                    .let { it[Actors.handle] to it[Actors.handleCheckDueAt] }
            }
        val outcome =
            when (check) {
                is HandleCheck.Verified -> "Verified ${check.handle}"
                HandleCheck.Invalid -> "Invalid: shown as $handle"
                HandleCheck.Unreachable -> "Unreachable: kept $handle"
            }
        return ActionResult("$outcome; next check ${shownTime(due)}.")
    }

    private suspend fun backfillBlocks(did: Did): ActionResult {
        val put =
            try {
                blockBackfill.backfillNow(did)
            } catch (@Suppress("SwallowedException") e: NotIndexedException) {
                return NOT_INDEXED
            }
        return ActionResult("$put Bluesky blocks indexed.", detail = "$put blocks")
    }

    /** Queues a reindex, or reports the one already queued or running; `resyncSpaces` defaults on, and is off with no syncer. */
    private suspend fun ActionCall.reindex(did: Did): ActionResult {
        val resync = params["resyncSpaces"]?.let { it == "true" } ?: true
        val active = reindexer.runs(did).firstOrNull { it.state.active }
        val run = reindexer.request(did, admin.did, resyncSpaces = resync && syncer != null)
        call.response.headers.append(ACCOUNT_HX_TRIGGER, "$ACCOUNT_RUNS_CHANGED, $ACCOUNT_SPACES_CHANGED")
        return if (run.id == active?.id)
            ActionResult("Run ${run.id} is already ${run.state.code}; no other run started.", detail = "run ${run.id}")
        else ActionResult("Run ${run.id} ${run.state.code}.", detail = "run ${run.id}")
    }

    private companion object {
        /** Nothing is stored for an account the AppView never indexed, so a repair of one changes nothing. */
        val NOT_INDEXED = ActionResult.refused("not-indexed", "Not indexed: nothing stored.")
    }
}
