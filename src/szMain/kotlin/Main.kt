import io.IOHelpers.findDevice
import io.IOHelpers.getMounts
import io.IOHelpers.isDirectory
import io.IOHelpers.isFile
import io.IOHelpers.printErr
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
    val parser = ArgParser("sz")
    val threads by parser.option(Int, shortName = "t", fullName = "threads", description = "Threads").default(50)
    val pause by parser.option(Int, shortName = "pause", description = "Microseconds to pause when waiting for workers").default(100)
    val dir by parser.argument(ArgType.String, description = "Directory to analyze")

    parser.parse(args)
    device = findDevice(mounts, dir) ?: throw Exception("Couldn't find $dir in $mounts")

    // to avoid unnecessary allocations, we pass these down and write to them in functions
    val resultQueue = mutableListOf<Result>() // queue of results we have yet to process
    val results = mutableMapOf<String, Long>() // the final output

    val workers = WorkerPool<String, Result>(threads, pause)

    // initialize the scan on the top level dir
    submitAndCollect(dir, workers, results)

    // keep iterating for results (and submitting new jobs) until there's nothing running
    while (workers.sip(resultQueue)) {
        resultQueue.forEach { result ->
            results[result.path] = result.size
            result.otherPaths.forEach { path ->
                submitAndCollect(path, workers, results)
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

    Reporter(dir, results).report()
}

/**
 * Submit processing of a path to the worker pool, and collect results. Doesn't block on jobs; collects any results
 * that have become available, not necessarily associated with this path.
 */
fun submitAndCollect(path: String, workers: WorkerPool<String, Result>, resultCollector: MutableMap<String, Long>) {
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
    val results = workers.execute(path, ::processDirectory)
    for (result in results) {
        if (DEBUG && resultCollector.containsKey(result.path)) {
            printError("${result.path} was already added(1)")
        }
        resultCollector[result.path] = result.size
        for (otherPath in result.otherPaths) {
            submitAndCollect(otherPath, workers, resultCollector)
        }
    }
}

/**
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
                // TODO change to avoid Pair structure
                // TODO isOnDevice (st_dev) not working, see slack
                if (info != null) { //&& info.second.isOnDevice(device)) {
                    if (info.second.isDirectory()) {
                        // indicate that we need to process this dir
                        subPaths.add(info.first)
                    } else if (info.second.isFile()) {
                        fileSize += info.second.st_size
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
 * @param size the total size in bytes of all files in the directory (not including subdirectory)
 * @param otherPaths other paths found that need to be processed
 */
data class Result(val path: String, val size: Long, val otherPaths: List<String>)
