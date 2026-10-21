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
        val relations = RelationPass().also { pbf.process(it) }
        val members =
            LongSetBuilder().apply { relations.kept.forEach { r -> r.members.forEach { if (it.type == WAY) add(it.ref) } } }.build()
        val ways = WayPass(members).also { pbf.process(it) }
        val coords = NodeCoords(ways.nodes.build())
        pbf.process(NodePass(coords))
        return OsmData(ways.kept, relations.kept, coords, relations.timestamp)
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
    }

    private class RelationPass : Pass() {
        val kept = mutableListOf<OsmRelation>()
        var timestamp: Instant? = null

        override fun parse(header: Osmformat.HeaderBlock) {
            if (header.hasOsmosisReplicationTimestamp()) timestamp = Instant.fromEpochSeconds(header.osmosisReplicationTimestamp)
        }

        override fun parseRelations(rels: List<Osmformat.Relation>) {
            for (rel in rels) {
                val tags = tags(rel.keysList, rel.valsList)
                if (TrailTags.relationGroups(tags).isEmpty()) continue
                var ref = 0L
                val members =
                    rel.memidsList.indices.map { i ->
                        ref += rel.getMemids(i)
                        val type =
                            when (rel.getTypes(i)) {
                                Osmformat.Relation.MemberType.NODE -> OsmMember.Type.NODE
                                Osmformat.Relation.MemberType.WAY -> OsmMember.Type.WAY
                                else -> OsmMember.Type.RELATION
                            }
                        OsmMember(type, ref, getStringById(rel.getRolesSid(i)))
                    }
                kept += OsmRelation(rel.id, tags, members)
            }
        }
    }

    /** Keeps trail ways, relation [members] and named highways; collects the nodes whose coordinates the first two need. */
    private class WayPass(private val members: LongArray) : Pass() {
        val kept = mutableListOf<OsmWay>()
        val nodes = LongSetBuilder()

        override fun parseWays(ways: List<Osmformat.Way>) {
            for (way in ways) {
                val tags = tags(way.keysList, way.valsList)
                val trail = TrailTags.wayGroups(tags).isNotEmpty() || members.binarySearch(way.id) >= 0
                if (!trail && !TrailTags.marksJunctions(tags)) continue
                var ref = 0L
                val refs = LongArray(way.refsCount) { i -> (ref + way.getRefs(i)).also { ref = it } }
                kept += OsmWay(way.id, tags, refs)
                if (trail) nodes.addAll(refs)
            }
        }
    }

    private class NodePass(private val coords: NodeCoords) : Pass() {
        override fun parseNodes(nodes: List<Osmformat.Node>) {
            for (node in nodes) if (node.id in coords) coords.set(node.id, parseLat(node.lat), parseLon(node.lon))
        }

        override fun parseDense(nodes: Osmformat.DenseNodes) {
            var id = 0L
            var lat = 0L
            var lng = 0L
            for (i in 0 until nodes.idCount) {
                id += nodes.getId(i)
                lat += nodes.getLat(i)
                lng += nodes.getLon(i)
                if (id in coords) coords.set(id, parseLat(lat), parseLon(lng))
            }
        }
    }
}
