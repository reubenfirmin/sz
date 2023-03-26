import io.IOHelpers.findDevice
import io.IOHelpers.getMounts
import io.IOHelpers.printError
import io.IOHelpers.statFile
import io.IOHelpers.toFileInfo
import kotlinx.cinterop.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ArgType.*
import kotlinx.cli.default
import platform.posix.*
import view.Reporter
import kotlin.String

const val DEBUG = false

// paths to avoid
val blacklist = setOf("/proc", "/sys")

// the only device (disk) we'll examine
private val mounts = getMounts()
private var device = ""

fun main(args: Array<String>) {

    val parser = ArgParser("sz", useDefaultHelpShortName = false)
    val threads by parser.option(Int, shortName = "t", fullName = "threads", description = "Threads").default(50)
    val pause by parser.option(Int, shortName = "p", fullName = "pause", description = "Microseconds to pause when waiting for workers").default(100)
    val human by parser.option(Boolean, shortName = "h", fullName = "human", description = "Human readable sizes").default(false)
    val nocolors by parser.option(Boolean, shortName = "c", fullName = "nocolors", description = "Turn off ansi colors (only applies to human mode)").default(false)
    val nosummary by parser.option(Boolean, shortName = "v", fullName = "all", description = "Verbose output - show all non-zero results").default(false)
    val zeroes by parser.option(Boolean, shortName = "vv", fullName = "zeroes", description = "Extra verbose output - show all output, including non-zero").default(false)
    val dir by parser.argument(ArgType.String, description = "Directory to analyze")
    parser.parse(args)

    device = findDevice(mounts, dir) ?: throw Exception("Couldn't find $dir in $mounts")

    val workers = WorkerPool<String, Result>(threads, pause)

    // initialize the scan on the top level dir
    submit(dir, workers)

    val results = mutableMapOf<String, Long>() // the final output
    // to avoid unnecessary allocations, we pass this down to collect results
    val resultQueue = mutableListOf<Result>()
    // keep iterating for results (and submitting new jobs) until there's nothing running
    while (workers.sip(resultQueue)) {
        resultQueue.forEach { result ->
            results[result.path] = result.size
            result.otherPaths.forEach { path ->
                submit(path, workers)
            }
        }
        resultQueue.clear()
        sched_yield()
    }

    // drain remaining jobs from the queue
    workers.drain(true, resultQueue)
    // there should be none, because we already drained
    if (DEBUG) {
        resultQueue.forEach { result ->
            printError("Workers was drained but found $result")
        }
    }

    Reporter(dir, results, human, nosummary, zeroes, !nocolors).report()
}

/**
 * Submit processing of a path to the worker pool. Blocks until a worker becomes available.
 */
fun submit(path: String, workers: WorkerPool<String, Result>) {
    // don't process virtual paths that are in our blacklist
    if (path in blacklist) {
        return
    }
    // if this is a different mount, we'll skip it
    val mount = mounts[path]
    if (mount != null && mount != device) {
        if (DEBUG) {
            println("Skipping path on different mount $path")
        }
        return
    }
    workers.execute(path, ::processDirectory)
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
                // TODO isOnDevice (st_dev) not working, see slack
                if (info != null) { //&& info.second.isOnDevice(device)) {
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
