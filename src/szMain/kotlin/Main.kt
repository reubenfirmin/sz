import io.IOHelpers.statFile
import io.IOHelpers.toFileInfo
import kotlinx.cinterop.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import platform.posix.*
import view.Reporter
import kotlin.String

const val DEBUG = false

// paths to avoid
val blacklist = setOf("/proc", "/sys")

// the only device (disk) we'll examine
private var device = 0.toULong()

fun main(args: Array<String>) {

    val parser = ArgParser("sz", useDefaultHelpShortName = false)
    val threads by parser.option(ArgType.Int, shortName = "t", fullName = "threads", description = "Threads").default(50)
    val human by parser.option(ArgType.Boolean, shortName = "h", fullName = "human", description = "Human readable sizes").default(false)
    val nocolors by parser.option(ArgType.Boolean, shortName = "c", fullName = "nocolors", description = "Turn off ansi colors (only applies to human mode)").default(false)
    val nosummary by parser.option(ArgType.Boolean, shortName = "v", fullName = "all", description = "Verbose output - show all non-zero results").default(false)
    val zeroes by parser.option(ArgType.Boolean, shortName = "vv", fullName = "zeroes", description = "Extra verbose output - show all output, including non-zero").default(false)
    val dir by parser.argument(ArgType.String, description = "Directory to analyze")
    parser.parse(args)

    val results = scanPath(dir, threads)
    Reporter(dir, results, human, nosummary, zeroes, !nocolors).report()
}

/**
 * Scan a path and all sub-paths, summing the size of files in each path. Run in parallel with [threads] threads.
 * @return map of fully qualified path to size in bytes
 */
fun scanPath(dir: String, threads: Int): Map<String, Long> {
    val startAt = statFile(dir)
    device = startAt!!.device

    val workers = WorkerPool<String, Result>(threads)

    // initialize the scan on the top level dir
    workers.execute(dir, ::processDirectory)

    val results = mutableMapOf<String, Long>() // the final output

    // keep iterating for results (and submitting new jobs) until there's nothing running. since the coordinator is
    // single threaded we can get away with this - we guarantee that we'll have something busy as long as there are
    // paths to iterate.
    var result: Result? = null
    while (  run { result = workers.sip(); result} != null || workers.anyBusy()) {
        if (result != null) {
            results[result!!.path] = result!!.size
            result!!.otherPaths.forEach { path ->
                // don't process virtual paths that are in our blacklist
                if (path !in blacklist) {
                    workers.execute(path, ::processDirectory)
                }
            }
        }
        sched_yield()
    }

    // drain remaining jobs from the queue. none of these jobs have paths
    workers.drain().let {
        if (it.isNotEmpty()) {
            throw Exception("Should be none remaining but there were ${it.size}")
        }
    }
    return results
}

/**
 * Iterate through the contents of a directory. For each entry:
 * - if it's a file then add the size
 * - if it's a directory then keep track of the path
 *
 * This function is submitted to workers, and therefore cannot access shared memory.
 *
 * @return size of files in this directory, plus list of paths to subdirectories found
 */
fun processDirectory(path: String): Result {
    val subPaths = mutableListOf<String>() // TODO deallocate using shared mem?
    val directory = opendir(path)
    var fileSize = 0L
    if (directory != null) {
        try {
            var entry = readdir(directory)?.pointed

            while (entry != null) {
                val info = entry.toFileInfo(path)
                if (info != null && info.isOnDevice(device)) {
                    if (info.isDirectory()) {
                        // indicate that we need to process this dir
                        subPaths.add(info.fullPath)
                    } else if (info.isFile()) {
                        fileSize += info.size
                    }
                }
                entry = readdir(directory)?.pointed
            }
        } finally {
            closedir(directory)
        }
    }
    return Result(path, fileSize, subPaths)
}

/**
 * @param path the directory that was checked
 * @param size the total size in bytes of all files in the directory (not including subdirectories)
 * @param otherPaths other paths found that need to be processed
 */
data class Result(val path: String, val size: Long, val otherPaths: List<String>)
