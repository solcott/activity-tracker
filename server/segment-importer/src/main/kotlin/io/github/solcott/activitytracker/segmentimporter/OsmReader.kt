package io.github.solcott.activitytracker.segmentimporter

import crosby.binary.BinaryParser
import crosby.binary.Osmformat
import crosby.binary.file.BlockInputStream
import java.io.File
import kotlin.time.Instant

/**
 * Reads an `.osm.pbf` extract into [OsmData], keeping only what trails need. PBF stores nodes, then ways, then relations, so it reads the
 * file three times: relations first (named route relations and their member ways), then ways (trail ways, members, and named highways for
 * junctions), then the coordinates of only the nodes trail ways and members use. Ids are held in sorted [LongArray]s.
 */
internal object OsmReader {
    fun read(pbf: File): OsmData {
        val relations = RelationPass { TrailTags.relationGroups(it).isNotEmpty() }.also { pbf.process(it) }
        val members =
            LongSetBuilder().apply { relations.kept.forEach { r -> r.members.forEach { if (it.type == WAY) add(it.ref) } } }.build()
        val ways = WayPass(members).also { pbf.process(it) }
        val coords = NodeCoords(ways.nodes.build())
        pbf.process(NodePass(coords))
        return OsmData(ways.kept, relations.kept, coords, relations.timestamp)
    }

    /**
     * Reads [pbf] for the dive-site pass ([diveSites]): relations [DiveSiteTags] accepts, then those ways and the relations' member ways,
     * then the tagged nodes it accepts and the coordinates of the kept ways' nodes and the relations' member nodes. It keeps nothing of the
     * trail network.
     */
    fun readDiveSites(pbf: File): OsmData {
        val relations = RelationPass(DiveSiteTags::isDiveSite).also { pbf.process(it) }
        val members = relations.kept.flatMap { it.members }
        val memberWays = LongSetBuilder().apply { members.forEach { if (it.type == WAY) add(it.ref) } }.build()
        val ways = SiteWayPass(memberWays).also { pbf.process(it) }
        members.forEach { if (it.type == OsmMember.Type.NODE) ways.nodes.add(it.ref) }
        val nodes = SiteNodePass(NodeCoords(ways.nodes.build())).also { pbf.process(it) }
        return OsmData(ways.kept, relations.kept, nodes.coords, relations.timestamp, nodes = nodes.kept)
    }

    private val WAY = OsmMember.Type.WAY

    private fun File.process(parser: BinaryParser) = inputStream().buffered().use { BlockInputStream(it, parser).process() }

    /** The base of each pass: tags from the block's string table, and nothing done for the elements it doesn't read. */
    private open class Pass : BinaryParser() {
        override fun parseRelations(rels: List<Osmformat.Relation>) = Unit

        override fun parseDense(nodes: Osmformat.DenseNodes) = Unit

        override fun parseNodes(nodes: List<Osmformat.Node>) = Unit

        override fun parseWays(ways: List<Osmformat.Way>) = Unit

        override fun parse(header: Osmformat.HeaderBlock) = Unit

        override fun complete() = Unit

        protected fun tags(keys: List<Int>, values: List<Int>): Map<String, String> =
            keys.indices.associate { getStringById(keys[it]) to getStringById(values[it]) }

        protected fun members(rel: Osmformat.Relation): List<OsmMember> {
            var ref = 0L
            return rel.memidsList.indices.map { i ->
                ref += rel.getMemids(i)
                val type =
                    when (rel.getTypes(i)) {
                        Osmformat.Relation.MemberType.NODE -> OsmMember.Type.NODE
                        Osmformat.Relation.MemberType.WAY -> OsmMember.Type.WAY
                        else -> OsmMember.Type.RELATION
                    }
                OsmMember(type, ref, getStringById(rel.getRolesSid(i)))
            }
        }

        /**
         * Each of [nodes]' id and position, delta-decoded, with its tags when [tagged] (otherwise, and for an untagged node, none): a pass
         * that only needs positions skips the string lookups.
         */
        protected fun forEachDense(
            nodes: Osmformat.DenseNodes,
            tagged: Boolean,
            visit: (Long, Double, Double, Map<String, String>) -> Unit,
        ) {
            var id = 0L
            var lat = 0L
            var lng = 0L
            var kv = 0
            for (i in 0 until nodes.idCount) {
                id += nodes.getId(i)
                lat += nodes.getLat(i)
                lng += nodes.getLon(i)
                // Each node's tags are key and value string ids, ended by a 0; a block of untagged nodes has no list at all.
                val tags =
                    if (!tagged) {
                        emptyMap()
                    } else {
                        buildMap {
                            while (kv < nodes.keysValsCount && nodes.getKeysVals(kv) != 0) {
                                put(getStringById(nodes.getKeysVals(kv)), getStringById(nodes.getKeysVals(kv + 1)))
                                kv += 2
                            }
                            kv++
                        }
                    }
                visit(id, parseLat(lat), parseLon(lng), tags)
            }
        }

        protected fun refs(way: Osmformat.Way): LongArray {
            var ref = 0L
            return LongArray(way.refsCount) { i -> (ref + way.getRefs(i)).also { ref = it } }
        }
    }

    /** Keeps the relations [keep] accepts, with their members, and the extract's replication time. */
    private class RelationPass(private val keep: (Map<String, String>) -> Boolean) : Pass() {
        val kept = mutableListOf<OsmRelation>()
        var timestamp: Instant? = null

        override fun parse(header: Osmformat.HeaderBlock) {
            if (header.hasOsmosisReplicationTimestamp()) timestamp = Instant.fromEpochSeconds(header.osmosisReplicationTimestamp)
        }

        override fun parseRelations(rels: List<Osmformat.Relation>) {
            for (rel in rels) {
                val tags = tags(rel.keysList, rel.valsList)
                if (keep(tags)) kept += OsmRelation(rel.id, tags, members(rel))
            }
        }
    }

    /** Keeps trail ways, relation [members], ski lifts and named highways; collects the nodes whose coordinates all but the last need. */
    private class WayPass(private val members: LongArray) : Pass() {
        val kept = mutableListOf<OsmWay>()
        val nodes = LongSetBuilder()

        override fun parseWays(ways: List<Osmformat.Way>) {
            for (way in ways) {
                val tags = TrailTags.normalized(tags(way.keysList, way.valsList))
                val trail = TrailTags.wayGroups(tags).isNotEmpty() || members.binarySearch(way.id) >= 0 || TrailTags.isLift(tags)
                if (!trail && !TrailTags.marksJunctions(tags)) continue
                val refs = refs(way)
                kept += OsmWay(way.id, tags, refs)
                if (trail) nodes.addAll(refs)
            }
        }
    }

    private class NodePass(private val coords: NodeCoords) : Pass() {
        override fun parseNodes(nodes: List<Osmformat.Node>) {
            for (node in nodes) if (node.id in coords) coords.set(node.id, parseLat(node.lat), parseLon(node.lon))
        }

        override fun parseDense(nodes: Osmformat.DenseNodes) =
            forEachDense(nodes, tagged = false) { id, lat, lng, _ -> if (id in coords) coords.set(id, lat, lng) }
    }

    /** Keeps dive-site ways and dive-site relations' [members], collecting their nodes. */
    private class SiteWayPass(private val members: LongArray) : Pass() {
        val kept = mutableListOf<OsmWay>()
        val nodes = LongSetBuilder()

        override fun parseWays(ways: List<Osmformat.Way>) {
            for (way in ways) {
                val tags = tags(way.keysList, way.valsList)
                if (!DiveSiteTags.isDiveSite(tags) && members.binarySearch(way.id) < 0) continue
                val refs = refs(way)
                kept += OsmWay(way.id, tags, refs)
                nodes.addAll(refs)
            }
        }
    }

    /** Keeps dive-site nodes with their positions, and fills in [coords]. */
    private class SiteNodePass(val coords: NodeCoords) : Pass() {
        val kept = mutableListOf<OsmNode>()

        override fun parseNodes(nodes: List<Osmformat.Node>) {
            for (node in nodes) visit(node.id, parseLat(node.lat), parseLon(node.lon), tags(node.keysList, node.valsList))
        }

        override fun parseDense(nodes: Osmformat.DenseNodes) = forEachDense(nodes, tagged = true, ::visit)

        private fun visit(id: Long, lat: Double, lng: Double, tags: Map<String, String>) {
            if (id in coords) coords.set(id, lat, lng)
            if (tags.isNotEmpty() && DiveSiteTags.isDiveSite(tags)) kept += OsmNode(id, tags, lat, lng)
        }
    }
}
