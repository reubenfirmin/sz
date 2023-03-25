package io

import kotlinx.cinterop.*
import platform.linux.endmntent
import platform.linux.getmntent
import platform.linux.setmntent
import platform.posix.*

/**
 * Various POSIX and system related helpers.
 */
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
     * Print error to stdout, with ansi coloring of "ERROR".
     */
    fun printError(message: Any) {
        print(ansiFg(Color.HIGHLIGHT_1, "ERROR"))
        println(message)
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

    // convert masks to UInt once to avoid repeated ops
    private val fileTypeBm = S_IFMT.toUInt() // bitmask
    private val dirBits = S_IFDIR.toUInt()
    private val fileBits = S_IFREG.toUInt()
    private val symlinkBits = S_IFLNK.toUInt()

    fun stat.isDirectory() = st_mode and fileTypeBm == dirBits

    fun stat.isFile() = st_mode and fileTypeBm == fileBits

    fun stat.isSymlink() = st_mode and fileTypeBm == symlinkBits

    /**
     * XXX this is suspect; don't rely on it yet
     */
    fun stat.isOnDevice(device: ULong) = st_dev == device

    /**
     * Convert a [dirent] (obtained from iterating over [readdir]) to a pair of absolute path, [stat] object
     */
    fun dirent.toFileInfo(parentPath: String): Pair<String, stat>? {
        val nameBuf = this.d_name.toKString()
        // remove trailing null characters (which might be there because we're converting from a c array)
        // TODO this may not be necessary with toKString. Verify
        val name = nameBuf.trimEnd('\u0000')
        if (name != "." && name != "..") {
            // File is not available in Kotlin native :(. So, this is a unix path structure (for now). If we really
            // wanted to support el doze, we could handle at compilation time somehow.
            val fullPath = "${parentPath.removeSuffix("/")}/$name"
            val st = statFile(fullPath)
            return fullPath to st
        }
        return null
    }

    /**
     * Return stat info about a file path. This calls lstat; symlinks will be identified as such rather than
     * dereferenced.
     */
    fun statFile(path: String) = memScoped {
        val info = alloc<stat>()
        lstat(path, info.ptr)
        info
    }

    /**
     * Get the system mounts; this returns a map of paths to mounts.
     */
    fun getMounts(): Map<String, String> {
        val mounts = setmntent("/proc/mounts", "r")
        var res = getmntent(mounts)
        val result = mutableMapOf<String, String>()
        while (res != null) {
            res.pointed.let {
                result[it.mnt_dir!!.toKString()] = it.mnt_fsname!!.toKString()
            }
            res = getmntent(mounts)
        }
        endmntent(mounts)
        return result
    }


    /**
     * Figure out which mount [dir] is on.
     */
    fun findDevice(mounts: Map<String, String>, dir: String): String? {
        var mount = ""
        val str = StringBuilder()
        // find the mount of the starting dir (the longest possible match)
        for (char in dir) {
            str.append(char)
            mounts[str.toString()] ?.let {
                mount = it
            }
        }
        return mount
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