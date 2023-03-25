package view

import io.IOHelpers.ansiUnderline
import view.Table.VisibleColumn.Companion.defaultFormatter
import kotlin.math.max
import kotlin.math.min


const val HEADING_IDX = -1

/**
 * @param formatting if true, use color to format
 * @param maxWidth terminal width (TODO doesn't fully obey this yet)
 */
class Table(val formatting: Boolean, val maxWidth: Int) {

    interface Column {
        val prePadding: Int
            get() = 0
        val width: Int
            get() = 0
    }

    private class VisibleColumn(val heading: String,
                                val headingFormatter: (Int, String, String) -> String,
                                override val width: Int,
                                override val prePadding: Int,
                                val rowFormatter: (Int, String, String) -> String): Column {

        val buffer = StringBuilder(1024)
        var bufferLen = 0
        var bufferReadIdx = 0

        fun cellFormatter(formatting: Boolean, rowIndex: Int) = if (formatting) {
            if (rowIndex == HEADING_IDX) {
                headingFormatter
            } else {
                // don't format -1
                if (rowIndex >= 0) {
                    rowFormatter
                } else {
                    defaultFormatter
                }
            }
        } else {
            defaultFormatter
        }

        companion object {
            val defaultFormatter: (Int, String, String) -> String = { _, _, str -> str }
        }
    }

    private class Sink: Column

    // visible for testing
    val columns = ArrayList<Column>()

    /**
     * Formatters - if specified, special formatting to apply to the provided padded value. Params are index, raw value,
     * padded/cropped value. The padded/cropped value should be the one that's output. Index / raw value are provided
     * to allow calculation of formatting rules.
     *
     * @param heading - column heading
     * @param headingFormatter - if specified, special formatting to apply to heading (underlined by default); provided
     *                          index / raw value / padded value
     * @param width - width to crop/pad the column to
     * @param prePadding - spaces to insert prior to the column
     * @param consumeRemainingWidth - can set to true on last column to fill width
     * @param rowFormatter - formatter for values in this row/column - provided index / raw value / padded value
     */
    fun column(heading: String,
               headingFormatter: (Int, String, String) -> String = { _, _, str -> ansiUnderline(str) },
               width: Int = 20,
               prePadding: Int = if (maxWidth < 80) { 1 } else { 2 },
               consumeRemainingWidth: Boolean = false,
               rowFormatter: (Int, String, String) -> String = defaultFormatter): Table {

        val renderWidth = if (consumeRemainingWidth) {
            val usedWidth = columns.sumOf { it.prePadding + it.width } + prePadding
            // hide if less than 0
            max(maxWidth - usedWidth, 0)
        } else {
            width
        }

        columns.add(VisibleColumn(heading, headingFormatter, renderWidth, prePadding, rowFormatter))
        return this
    }

    /**
     * Adds a column if condition is true, otherwise adds a Sink. See [column]
     */
    fun optionalColumn(condition: Boolean,
                       heading: String,
                       headingFormatter: (Int, String, String) -> String = { _, _, str -> ansiUnderline(str) },
                       width: Int = 20,
                       prePadding: Int = 5,
                       consumeRemainingWidth: Boolean = false,
                       rowFormatter: (Int, String, String) -> String = defaultFormatter): Table {

        if (condition) {
            column(heading, headingFormatter, width, prePadding, consumeRemainingWidth, rowFormatter)
        } else {
            columns.add(Sink())
        }
        return this
    }

    fun printHeading() {
        row(HEADING_IDX, columns.map { if (it is VisibleColumn) { it.heading } else { "" } }.toTypedArray())
            .forEach { println(it) }
    }

    fun printRow(index: Int, vararg data: String) {
        row(index, data as Array<String>)
            .forEach { println(it) }
    }

    fun printPost() {
        if (columns.firstOrNull { it.width == 0 } != null) {
            println(">>> One or more columns were hidden due to terminal width")
        }
    }

    /**
     * If index is -1, row formatter will be skipped
     */
    fun row(rowIndex: Int, data: Array<String>): List<String> {

        var dataToRender = false
        data.forEachIndexed { idx, item ->
            val column = columns[idx]
            if (column is VisibleColumn) {
                column.buffer.insert(0, item)
                column.bufferLen = item.length
                column.bufferReadIdx = 0
                dataToRender = true
            }
        }

        val buffer = StringBuilder()
        val result = mutableListOf<String>()
        // take multiple passes to allow wrapping within columns
        while (dataToRender) {
            buffer.clear()
            var dataRemaining = false
            for (idx in 0 until data.size) {
                val column = columns[idx]
                if (column.prePadding >= 0 && column.width > 0) {
                    buffer.append("".padEnd(column.prePadding, ' '))
                }

                if (column is VisibleColumn && column.width > 0) {
                    val item = column.buffer.slice(column.bufferReadIdx until
                            min(column.bufferReadIdx + column.width, column.bufferLen)).toString()

                    column.bufferReadIdx += item.length
                    buffer.append(column.cellFormatter(formatting, rowIndex)
                        .invoke(rowIndex, item,item.padEnd(column.width, ' ')))

                    dataRemaining = dataRemaining || column.bufferReadIdx < column.bufferLen
                }
            }
            dataToRender = dataRemaining
            result.add(buffer.toString())
        }
        return result
    }
}