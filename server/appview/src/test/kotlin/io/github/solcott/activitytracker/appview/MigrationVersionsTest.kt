package io.github.solcott.activitytracker.appview

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Flyway reads db/migration and db/vendor/<database> as one sequence. Tests only run H2, so they never load the PostgreSQL folder; this
 * catches a version used in both before PostgreSQL refuses to start.
 */
class MigrationVersionsTest {
    @Test
    fun versionsAreUniqueAcrossPortableAndVendorMigrations() {
        val root = File(requireNotNull(javaClass.classLoader.getResource("db/migration")).toURI()).parentFile
        val files = root.walkTopDown().filter { it.isFile && it.name.endsWith(".sql") }.toList()
        assertTrue(files.any { it.parentFile.name == "postgresql" }, "expected PostgreSQL-only migrations under ${root.path}")
        val versions = files.map { it.name.substringAfter('V').substringBefore("__").toInt() to it.relativeTo(root).path }
        val duplicates = versions.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
        assertEquals(emptyMap(), duplicates)
    }
}
