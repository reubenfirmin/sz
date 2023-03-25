package view

import io.Color
import io.IOHelpers

class Reporter(val dir: String, val results: Map<String, Long>) {

    fun report() {
        println("$dir files size: ${results[dir]}")
        val fullSize = results.entries.sumOf { it.value }
        println("$dir total size: $fullSize")

        val onePercent = fullSize / 100.0
        println("Entries that consume at least 1% of space in this path")
        println("---------------------------------------------------------")
        results.entries
            .sortedByDescending { it.value }
            .filter { it.value > onePercent }
            .forEach {
                print(IOHelpers.ansiFg(Color.HIGHLIGHT_2, it.value.toString()))
                print("\t")
                println(IOHelpers.ansiFg(Color.HIGHLIGHT_0, it.key))
            }
    }
}