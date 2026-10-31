package io.github.solcott.activitytracker.activityfile

/**
 * A CSV file (RFC 4180): fields split on commas, quoted fields holding commas, newlines and doubled quotes. Its first row names the
 * columns; where a name repeats (Strava's export has two "Distance" columns), the first wins.
 */
internal class Csv private constructor(private val columns: Map<String, Int>, val rows: List<List<String>>) {
    /**
     * [row]'s value in the column named [name] (case and surrounding spaces ignored), or null when there's no such column or it's blank.
     */
    fun value(row: List<String>, name: String): String? =
        columns[name.normalized()]?.let(row::getOrNull)?.trim()?.takeIf { it.isNotEmpty() }

    /** Whether the file has a column named [name]. */
    fun has(name: String): Boolean = name.normalized() in columns

    companion object {
        fun parse(text: String): Csv {
            val records = records(text.removePrefix("﻿"))
            val header = records.firstOrNull().orEmpty()
            val columns = mutableMapOf<String, Int>()
            header.forEachIndexed { index, name -> columns.getOrPut(name.normalized()) { index } }
            return Csv(columns, records.drop(1).filterNot { row -> row.all { it.isBlank() } })
        }

        private fun String.normalized() = trim().lowercase()

        private fun records(text: String): List<List<String>> {
            val records = mutableListOf<List<String>>()
            val record = mutableListOf<String>()
            val field = StringBuilder()
            var quoted = false
            var i = 0
            fun endField() {
                record += field.toString()
                field.clear()
            }
            fun endRecord() {
                endField()
                records += record.toList()
                record.clear()
            }
            while (i < text.length) {
                val c = text[i]
                when {
                    quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
                    quoted -> field.append(c)
                    c == ',' -> endField()
                    c == '\r' && text.getOrNull(i + 1) == '\n' -> Unit
                    c == '\n' || c == '\r' -> endRecord()
                    else -> field.append(c)
                }
                i++
            }
            if (field.isNotEmpty() || record.isNotEmpty()) endRecord()
            return records
        }
    }
}
