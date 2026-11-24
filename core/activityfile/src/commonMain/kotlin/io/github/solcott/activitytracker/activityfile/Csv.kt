package io.github.solcott.activitytracker.activityfile

import com.jsoizo.kotlincsv.csvReader
import com.jsoizo.kotlincsv.exceptions.MalformedCsvException
import com.jsoizo.kotlincsv.reader.ExcessFieldsRowBehaviour
import com.jsoizo.kotlincsv.reader.InsufficientFieldsRowBehaviour

/**
 * A CSV file (RFC 4180, read with kotlin-csv): fields split on commas, quoted fields holding commas, newlines and doubled quotes. Its first
 * row names the columns; where a name repeats (Strava's export has two "Distance" columns), the first wins.
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
        /**
         * [text]'s rows. When it isn't well-formed CSV (a quote left open, or text after a closing one), each line is read on its own and
         * only the lines that are well-formed are kept, so one bad row doesn't lose the rest (a quoted field spanning lines is lost then).
         */
        fun parse(text: String): Csv {
            val body = text.removePrefix("\uFEFF")
            val records =
                try {
                    READER.readAll(body)
                } catch (_: MalformedCsvException) {
                    body.lineSequence().mapNotNull(::wellFormedLine).toList()
                }
            val header = records.firstOrNull().orEmpty()
            val columns = mutableMapOf<String, Int>()
            header.forEachIndexed { index, name ->
                val column = name.normalized()
                if (column !in columns) columns[column] = index
            }
            return Csv(columns, records.drop(1).filterNot { row -> row.all { it.isBlank() } })
        }

        private fun String.normalized() = trim().lowercase()

        /** [line]'s fields when it's a well-formed CSV record on its own, else null. */
        private fun wellFormedLine(line: String): List<String>? =
            if (line.isBlank()) null
            else
                try {
                    READER.readAll(line).singleOrNull()
                } catch (_: MalformedCsvException) {
                    null
                }

        /** RFC 4180, with a row shorter than the header padded and a longer one cut to it. */
        private val READER = csvReader {
            skipEmptyLine = true
            insufficientFieldsRowBehaviour = InsufficientFieldsRowBehaviour.EMPTY_STRING
            excessFieldsRowBehaviour = ExcessFieldsRowBehaviour.TRIM
        }
    }
}
