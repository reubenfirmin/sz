package view

import io.Color
import io.Color.*
import io.IOHelpers.ansiBold
import io.IOHelpers.ansiFg
import kotlin.math.round

/**
 * @param dir - the directory that was scanned
 * @param results - the data derived from the scan
 * @param human - whether to show results in human readable form or not (e.g. 2.1G)
 * @param nosummary - turn OFF summary mode if selected
 * @param zeroes - include zeroes
 * @param colors - whether to use colors in output
 */
class Reporter(private val dir: String,
               private val results: Map<String, Long>,
               private val human: Boolean,
               private val nosummary: Boolean,
               private val zeroes: Boolean,
               private val colors: Boolean) {

    fun report() {
        println("$dir files size: ${format(results[dir]!!, human)}")
        val fullSize = results.entries.sumOf { it.value }
        println("$dir total size: ${format(fullSize, human)}")

        val summary = !nosummary && !zeroes

        val onePercent = fullSize / 100.0
        if (summary) {
            println("Entries that consume at least 1% of space in this path")
        }
        println("---------------------------------------------------------")
        results.entries
            .sortedByDescending { it.value }
            .filter { !summary || it.value > onePercent }
            .filter { zeroes || it.value > 0 }
            .forEach {
                print(format(it.value, human))
                print("\t\t")
                println(it.key)
            }
    }

    private fun format(size: Long, human: Boolean) =
        if (!human) {
            size.toString()
        } else {
            if (size > 1_000_000_000) {
                format(colors, ::ansiBold) {
                    format(colors, HIGHLIGHT_4, ::ansiFg) {
                        "${round2(size, 1_000_000_000)}G"
                    }
                }
            } else if (size > 1_000_000) {
                format(colors, HIGHLIGHT_4, ::ansiFg) {
                    "${round2(size, 1_000_000)}M"
                }
            } else if (size > 1_000) {
                format(colors, HIGHLIGHT_2, ::ansiFg) {
                    "${round2(size, 1_000)}K"
                }
            } else {
                format(colors, HIGHLIGHT_1, ::ansiFg) {
                    size.toString()
                }
            }
        }

    /**
     * Round to 2 decimal places
     */
    private fun round2(size: Long, divisor: Int) = round((size / divisor.toDouble()) * 100) / 100.0

    private fun format(colors: Boolean, formatter: (String) -> String, content: () -> String) =
        if (colors) {
            formatter(content.invoke())
        } else {
            content.invoke()
        }

    private fun format(colors: Boolean, color: Color, formatter: (Color, String) -> String, content: () -> String) =
        if (colors) {
            formatter(color, content.invoke())
        } else {
            content.invoke()
        }
}