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
        print(ansiFg(Color.RED, "ERROR"))
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

    fun ansiFg(fg: Color, content: String) = "\u001B[${fg.hex}m$content\u001B[0m"

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

    /**
     * Convert a [dirent] (obtained from iterating over [readdir]) to a pair of absolute path, [stat] object
     */
    fun dirent.toFileInfo(parentPath: String): StatInfo? {
        val nameBuf = this.d_name.toKString()
        // remove trailing null characters (which might be there because we're converting from a c array)
        // TODO this may not be necessary with toKString. Verify
        val name = nameBuf.trimEnd('\u0000')
        if (name != "." && name != "..") {
            // File is not available in Kotlin native :(. So, this is a unix path structure (for now). If we really
            // wanted to support el doze, we could handle at compilation time somehow.
            return statFile("${parentPath.removeSuffix("/")}/$name")
        }
        return null
    }

    /**
     * Return stat info about a file path. This calls lstat; symlinks will be identified as such rather than
     * dereferenced.
     */
    fun statFile(path: String): StatInfo? = memScoped {
        val info = alloc<stat>()
        if (lstat(path, info.ptr) != 0) {
            return null // potentially return Go style error, or throw exception
        }
        return StatInfo(path, info.st_mode, info.st_dev, info.st_size)
    }

    data class StatInfo(val fullPath: String, val fileType: UInt, val device: ULong, val size: Long) {

        // convert masks to UInt once to avoid repeated ops
        private val fileTypeBm = S_IFMT.toUInt() // bitmask
        private val dirBits = S_IFDIR.toUInt()
        private val fileBits = S_IFREG.toUInt()
        private val symlinkBits = S_IFLNK.toUInt()

        fun isDirectory() = fileType and fileTypeBm == dirBits

        fun isFile() = fileType and fileTypeBm == fileBits

        fun isSymlink() = fileType and fileTypeBm == symlinkBits

        fun isOnDevice(device: ULong) = this.device == device
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
    BLACK(0),
    HIGHLIGHT_0(36),
    HIGHLIGHT_1(32),
    HIGHLIGHT_2(35),
    HIGHLIGHT_3(33),
    HIGHLIGHT_4(31),
    RED(31);
}