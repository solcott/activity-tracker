package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.activityfile.UddfExportFixtures.decoDive
import io.github.solcott.activitytracker.activityfile.UddfExportFixtures.olderDive
import io.github.solcott.activitytracker.activityfile.UddfExportFixtures.write
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.w3c.dom.Element

/**
 * Checks that every parent [UddfWriter] writes lists its children in UDDF 3.2's `xs:sequence` order (from the 3.2.1 schema, the latest
 * published), so a validating reader accepts the file. Parents the schema declares `xs:all` (`personal`, `geography`,
 * `informationafterdive`) take any order and aren't listed.
 */
class UddfSchemaOrderTest {
    @Test fun aDecoDiveFollowsTheSchemasOrder() = assertSchemaOrder(write(decoDive.copy(dive = decoDive.dive!!.copy(closedCircuit = true))))

    @Test fun anOlderDiveFollowsTheSchemasOrder() = assertSchemaOrder(write(olderDive))

    @Test
    fun theRootIsInTheUddfNamespace() {
        val root = parse(write(decoDive))
        assertEquals("uddf", root.localName)
        assertEquals(UddfWriter.UDDF_NS, root.namespaceURI)
    }

    private fun assertSchemaOrder(xml: String) {
        val root = parse(xml)
        val checked = mutableSetOf<String>()
        fun visit(element: Element) {
            val children = element.childElements()
            SEQUENCES[element.localName]?.let { order ->
                checked += element.localName
                val positions = children.map { child ->
                    order.indexOf(child.localName).also {
                        if (it < 0) fail("<${child.localName}> isn't a child of <${element.localName}> in UDDF 3.2")
                    }
                }
                assertEquals(positions.sorted(), positions, "<${element.localName}> children: ${children.map { it.localName }}")
            }
            children.forEach(::visit)
        }
        visit(root)
        assertTrue(checked.containsAll(listOf("uddf", "dive", "informationbeforedive", "waypoint")), "checked $checked")
    }

    private fun parse(xml: String): Element =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(xml.byteInputStream())
            .documentElement

    private fun Element.childElements(): List<Element> = (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

    private companion object {
        /** Each parent's children in schema order, with the ones the writer never writes left in so the table reads against the XSD. */
        val SEQUENCES =
            mapOf(
                // xs:all in the schema, but every reader we know of expects this order.
                "uddf" to
                    listOf(
                        "generator",
                        "mediadata",
                        "maker",
                        "business",
                        "diver",
                        "divesite",
                        "divetrip",
                        "gasdefinitions",
                        "decomodel",
                        "profiledata",
                    ),
                "generator" to listOf("name", "aliasname", "type", "link", "manufacturer", "version", "datetime"),
                "diver" to listOf("owner", "buddy"),
                "owner" to listOf("personal", "address", "contact", "equipment", "medical", "education", "notes"),
                "equipment" to listOf("boots", "buoyancycontroldevice", "camera", "compass", "compressor", "divecomputer"),
                "divecomputer" to listOf("name", "aliasname", "link", "manufacturer", "model", "serialnumber"),
                "divesite" to listOf("divebase", "site"),
                "site" to listOf("name", "aliasname", "environment", "geography", "ecology", "sitedata", "rating", "notes"),
                "gasdefinitions" to listOf("mix"),
                "mix" to listOf("name", "aliasname", "o2", "n2", "he", "ar", "h2"),
                "profiledata" to listOf("repetitiongroup"),
                "repetitiongroup" to listOf("dive"),
                "dive" to listOf("informationbeforedive", "applicationdata", "samples", "tankdata", "informationafterdive"),
                "informationbeforedive" to
                    listOf(
                        "link",
                        "divenumber",
                        "divenumberofday",
                        "internaldivenumber",
                        "datetime",
                        "airtemperature",
                        "surfaceintervalbeforedive",
                        "altitude",
                    ),
                "surfaceintervalbeforedive" to listOf("infinity", "passedtime", "wayaltitude", "exposuretoaltitude"),
                "samples" to listOf("waypoint"),
                "waypoint" to
                    listOf(
                        "alarm",
                        "batterychargecondition",
                        "cns",
                        "decostop",
                        "calculatedpo2",
                        "depth",
                        "divetime",
                        // Not in the 3.2.1 schema; written where the readers we know of take it.
                        "heartrate",
                        "heading",
                        "otu",
                        "remainingbottomtime",
                        "remainingo2time",
                        "setpo2",
                        "switchmix",
                        "tankpressure",
                        "temperature",
                        "divemode",
                        "gradientfactor",
                        "measuredpo2",
                        "nodecotime",
                    ),
                "tankdata" to listOf("link", "tankvolume", "tankpressurebegin", "tankpressureend", "breathingconsumptionvolume"),
            )
    }
}
