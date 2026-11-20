package io.github.solcott.activitytracker.appview.reindex

import io.github.solcott.activitytracker.appview.auth.SigningKey
import io.github.solcott.activitytracker.appview.ipld.BlockCid
import io.github.solcott.activitytracker.appview.ipld.CarFile
import io.github.solcott.activitytracker.appview.ipld.DagCbor
import io.github.solcott.activitytracker.appview.ipld.RepoFormatException
import io.github.solcott.activitytracker.appview.ipld.atprotoJson
import io.github.solcott.activitytracker.lexicon.Did
import kotlinx.serialization.json.JsonObject

/** One record of a repo: where it is, and the CID of its block. */
internal class RepoEntry(val collection: String, val rkey: String, internal val block: BlockCid) {
    /** The record's CID, as the indexer and Jetstream write it. */
    val cid: String
        get() = block.string
}

/**
 * An account's repo as `getRepo` returned it, after its commit checked out: the CAR's root is a version-3 commit naming [did], signed by
 * its key. [entries] walks the repo's tree in key order, and [record] decodes one record's block, each block checked against its CID as
 * it's read, so nothing that doesn't belong to the signed commit is ever used. Repo-level, not index-level: every collection is listed.
 */
internal class RepoSnapshot private constructor(private val car: CarFile, val did: Did, val rev: String, private val data: BlockCid) {
    /** Every record in the repo, in key order. Throws [RepoFormatException] for a malformed or over-deep tree. */
    fun entries(): Sequence<RepoEntry> = sequence {
        var last: String? = null
        var nodes = 0
        @Suppress("ThrowsCount") // Untrusted input: a too deep, looping or unordered tree is refused with its own reason.
        suspend fun SequenceScope<RepoEntry>.walk(cid: BlockCid, depth: Int) {
            if (depth > MAX_DEPTH) throw RepoFormatException("bad_tree", "The repo's tree is too deep")
            // A tree can't have more nodes than the CAR has blocks: more means it loops back on itself.
            if (++nodes > car.blockCount) throw RepoFormatException("bad_tree", "The repo's tree loops")
            val node = treeNode(cid)
            node.left?.let { walk(it, depth + 1) }
            var previous = ByteArray(0)
            for (entry in node.entries) {
                val (key, value, right) = treeEntry(entry, previous)
                val text = key.decodeToString()
                if (last?.let { text <= it } == true) throw RepoFormatException("bad_tree", "Tree keys out of order")
                last = text
                previous = key
                entryOf(text, value)?.let { yield(it) }
                right?.let { walk(it, depth + 1) }
            }
        }
        walk(data, 0)
    }

    private class TreeNode(val left: BlockCid?, val entries: List<*>)

    private data class TreeEntry(val key: ByteArray, val value: BlockCid, val right: BlockCid?)

    private fun treeNode(cid: BlockCid): TreeNode {
        val node = DagCbor.decode(car.block(cid)) as? Map<*, *> ?: throw RepoFormatException("bad_tree", "Tree node isn't a map")
        return TreeNode(treeLink(node["l"]), node["e"] as? List<*> ?: throw RepoFormatException("bad_tree", "Tree node without entries"))
    }

    /**
     * One entry of a tree node: its key, rebuilt from the [previous] key's first `p` bytes and its own `k`, its value, its right subtree.
     */
    @Suppress("ThrowsCount") // Untrusted input: each malformed part is refused with its own reason.
    private fun treeEntry(entry: Any?, previous: ByteArray): TreeEntry {
        val e = entry as? Map<*, *> ?: throw RepoFormatException("bad_tree", "Tree entry isn't a map")
        val prefix = (e["p"] as? Long)?.takeIf { it in 0..previous.size } ?: throw RepoFormatException("bad_tree", "Bad key prefix")
        val suffix = e["k"] as? ByteArray ?: throw RepoFormatException("bad_tree", "Tree entry without a key")
        val key = previous.copyOfRange(0, prefix.toInt()) + suffix
        if (key.size > MAX_KEY_BYTES) throw RepoFormatException("bad_tree", "Tree key too long")
        val value = (e["v"] as? DagCbor.Link)?.cid ?: throw RepoFormatException("bad_tree", "Tree entry without a value")
        return TreeEntry(key, value, treeLink(e["t"]))
    }

    /** Reads [entry]'s block, checking it against its CID, without decoding it. Throws [RepoFormatException] when missing or mismatched. */
    fun verify(entry: RepoEntry) {
        car.block(entry.block)
    }

    /** The record [entry] names, as atproto JSON. Throws [RepoFormatException] when its block isn't a DAG-CBOR map. */
    fun record(entry: RepoEntry): JsonObject {
        if (entry.block.codec != BlockCid.DAG_CBOR) throw RepoFormatException("bad_record", "Record isn't DAG-CBOR")
        return atprotoJson(DagCbor.decode(car.block(entry.block))) as? JsonObject
            ?: throw RepoFormatException("bad_record", "Record isn't a map")
    }

    companion object {
        private const val REPO_VERSION = 3L

        /** Far deeper than any real tree: a fanout of 16 per level reaches a billion records in 8. */
        private const val MAX_DEPTH = 64

        /** `collection/rkey`: an NSID (at most 317) and a record key (at most 512). */
        private const val MAX_KEY_BYTES = 1024

        /**
         * Checks [car]'s root commit: version 3, naming [did], signed with [key] over its canonical encoding without `sig`. Throws
         * [RepoFormatException] (code `bad_commit` or `bad_signature`) otherwise.
         */
        @Suppress("ThrowsCount") // Untrusted input: each malformed part is refused with its own reason.
        fun open(car: CarFile, did: Did, key: SigningKey): RepoSnapshot {
            if (car.root.codec != BlockCid.DAG_CBOR) throw RepoFormatException("bad_commit", "The repo's root isn't a commit")
            val commit = DagCbor.decode(car.block(car.root)) as? Map<*, *> ?: throw RepoFormatException("bad_commit", "Commit isn't a map")
            if (commit["did"] != did.value) throw RepoFormatException("bad_commit", "The commit names another account")
            if (commit["version"] != REPO_VERSION) throw RepoFormatException("bad_commit", "Unsupported repo version")
            val rev = commit["rev"] as? String ?: throw RepoFormatException("bad_commit", "Commit without a revision")
            val data = (commit["data"] as? DagCbor.Link)?.cid ?: throw RepoFormatException("bad_commit", "Commit without a tree")
            val sig = commit["sig"] as? ByteArray ?: throw RepoFormatException("bad_signature", "Commit isn't signed")
            if (!key.verifies(DagCbor.encode(commit - "sig"), sig)) {
                throw RepoFormatException("bad_signature", "The commit's signature doesn't verify against the account's key")
            }
            return RepoSnapshot(car, did, rev, data)
        }

        private fun treeLink(value: Any?): BlockCid? =
            when (value) {
                null -> null
                is DagCbor.Link ->
                    value.cid.also { if (it.codec != BlockCid.DAG_CBOR) throw RepoFormatException("bad_tree", "Bad tree link") }
                else -> throw RepoFormatException("bad_tree", "Bad tree link")
            }

        /** `collection/rkey` split; null (skipped) for a key that isn't one, which no indexed collection could hold anyway. */
        private fun entryOf(key: String, cid: BlockCid): RepoEntry? {
            val slash = key.indexOf('/')
            if (slash <= 0 || slash == key.lastIndex || key.indexOf('/', slash + 1) >= 0) return null
            return RepoEntry(key.substring(0, slash), key.substring(slash + 1), cid)
        }
    }
}
