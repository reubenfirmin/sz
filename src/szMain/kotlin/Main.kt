import io.Color
import io.IOHelpers.ansiFg
import io.IOHelpers.isDirectory
import io.IOHelpers.isFile
import io.IOHelpers.printErr
import io.IOHelpers.printError
import io.IOHelpers.toFileInfo
import kotlinx.cinterop.*
import platform.posix.*

const val DEBUG = false

fun main(args: Array<String>) {

    // TODO use arg parsing library
    if (args.isEmpty()) {
        printErr("Please specify a device or a directory to scan from")
        exit(1)
    }

    val dir = args[0].removeSuffix("/")
    val threads = if (args.size > 1) {
        args[1].toInt()
    } else {
        50
    }
    val sleepTime = if (args.size > 2) {
        args[2].toInt()
    } else {
        100
    }

    val workers = WorkerPool<String, Result>(threads, sleepTime)

    val results = mutableMapOf<String, Long>()

    submitAndCollect(dir, workers, results)
    // keep iterating for results until there's nothing running
    val resultQueue = mutableListOf<Result>()

    // TODO submitter/groomer as separate threads? i don't think WorkerPool can be shared
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

    // TODO split into reporter
    println("$dir files size: ${results[dir]}")
    val fullSize = results.entries.sumOf { it.value }
    println("$dir total size: $fullSize")

    val onePercent = fullSize / 100.0
    println("Entries that consume at least 1% of space in this folder")
    println("---------------------------------------------------------")
    results.entries
        .sortedByDescending { it.value }
        .filter { it.value > onePercent }
        .forEach {
            print(ansiFg(Color.HIGHLIGHT_2, it.value.toString()))
            print("\t")
            println(ansiFg(Color.HIGHLIGHT_0, it.key))
        }
}

/**
 * Submit processing of a path to the worker pool, and collect results. Doesn't block on jobs; collects any results
 * that have become available, not necessarily associated with this path.
 */
fun submitAndCollect(path: String, workers: WorkerPool<String, Result>, resultCollector: MutableMap<String, Long>) {
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
 * @param path the directory that was checked
 * @param size the total size in bytes of all files in the directory (not including subdirectory)
 * @param otherPaths other paths found that need to be processed
 */
data class Result(val path: String, val size: Long, val otherPaths: List<String>)


/**
 * @return size of files in this directory, plus list of paths to subdirectories found
 */
fun processDirectory(path: String): Result {
    val toIterate = mutableListOf<String>()
    val directory = opendir(path)
    var fileSize = 0L
    if (directory != null) {
        try {
            var entry = readdir(directory)?.pointed

            while (entry != null) {
                val info = entry.toFileInfo(path)
                if (info != null) {
                    if (info.second.isDirectory()) {
                        // indicate that we need to process this dir
                        toIterate.add(info.first)
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
    return Result(path, fileSize, toIterate)
}
