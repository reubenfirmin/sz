package io

import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.*

object IOHelpers {

    val ESC = Char(27)

    /**
     * Print to stderr.
     */
    fun printErr(message: String) {
        fprintf(stderr!!, message + "\n")
        fflush(stderr!!)
    }

    /**
     * Replace kotlin's impl of readLine, which is broken on native (https://youtrack.jetbrains.com/issue/KT-39495)
     * TODO consider using readln() instead (added in 1.6) (test)
     * @return null if we hit EOF
     */
    fun readLine(buffer: ByteArray): String? {
        val read = fgets(buffer.refTo(0), buffer.size, stdin)?.toKString() ?: return null
        return read.substring(0, read.length - 1)
    }

    private fun ansi(command: String, tail: String = "") = "$ESC[$command$tail"

    /**
     * Clears the current line
     */
    fun ansiClear() = ansi("2K")

    /**
     * Resets cursor to column 0 and writes whatever's provided to the line (does not clear)
     */
    fun ansiOverwrite(content: String) = ansi("0G", content)

    fun ansiBold(content: String) = ansi("1m", content) + ansi("22m")

    fun ansiItalic(content: String) = ansi("3m", content) + ansi("23m")

    fun ansiUnderline(content: String) = ansi("4m", content) + ansi("24m")

    fun ansiFg(fg: Color, content: String) = ansi("38;5;${fg.hex}m", content) + ansi("39m")

    fun ansiBg(bg: Color, content: String) = ansi("48;5;${bg.hex}m", content) + ansi("49m")

    fun ansiColor(fg: Color, bg: Color, content: String) = ansiFg(fg, ansiBg(bg, content))

    /**
     * Run a command, returning results. Throws exception on failure.
     */
    fun runCommand(command: String, buffer: ByteArray): String {
        // cribbed from https://stackoverflow.com/questions/57123836/kotlin-native-execute-command-and-get-the-output
        val fp = popen(command, "r") ?: error("Failed to run command: $command")

        val stdout = buildString {
            while (true) {
                val input = fgets(buffer.refTo(0), buffer.size, fp) ?: break
                append(input.toKString())
            }
        }

        val status = pclose(fp)
        if (status != 0) {
            error("Command `$command` failed with status $status $stdout")
        }

        return stdout.trim()
    }
}

enum class Color(val hex: Int) {
    // https://gist.github.com/fnky/458719343aabd01cfb17a3a4f7296797
    BLACK(0),
    SCALE1_0(45), // TODO terminal color codes are terrible. possible to algorithmically pick an aesthetic range
    SCALE1_1(44),
    SCALE1_2(43),
    SCALE1_3(42),
    SCALE1_4(41),
    SCALE1_5(40),
    HIGHLIGHT_0(226),
    HIGHLIGHT_1(197),
    HIGHLIGHT_2(87),
    HIGHLIGHT_3(82),
    HIGHLIGHT_4(202);

    companion object {
        val scale1 = arrayListOf(SCALE1_0, SCALE1_1, SCALE1_2, SCALE1_3, SCALE1_4, SCALE1_5)
        val highlights = arrayListOf(HIGHLIGHT_0, HIGHLIGHT_1, HIGHLIGHT_2, HIGHLIGHT_3, HIGHLIGHT_4)
    }
}