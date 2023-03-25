import io.Color
import io.IOHelpers.ansiFg
import io.IOHelpers.printErr
import io.IOHelpers.printError
import kotlinx.cinterop.*
import platform.posix.*

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

    val workers = WorkerPool<String, Pair<Long, List<String>>, Result>(threads, sleepTime)

    val results = mutableMapOf<String, Long>()

    submitAndCollect(dir, workers, results)
    // keep iterating for results until there's nothing running
    val resultQueue = mutableListOf<Result>()
    // TODO producer consumer with a shared pool? need to add thread safety back if so
    while (workers.drain(false, resultQueue, ::resultTransformer)) {
        resultQueue.forEach { result ->
            results[result.param] = result.size
            result.otherPaths.forEach { path ->
                submitAndCollect(path, workers, results)
            }
        }
        resultQueue.clear()

    }
    // drain remaining jobs from the queue
    workers.drain(true, resultQueue, ::resultTransformer)
    // there should be none, because we already drained
    resultQueue.forEach {result ->
        printError("Workers was drained but found $result")
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

fun resultTransformer(path: String, result: Pair<Long, List<String>>) = Result(path, result.first, result.second)

data class Result(val param: String, val size: Long, val otherPaths: List<String>)

fun submitAndCollect(path: String, workers: WorkerPool<String, Pair<Long, List<String>>, Result>, resultCollector: MutableMap<String, Long>) {
    val results = workers.execute(path, ::resultTransformer, ::processDirectory)
    for (result in results) {
        if (resultCollector.containsKey(result.param)) {
            printError("${result.param} was already added(1)")
        }
        resultCollector[result.param] = result.size
        for (otherPath in result.otherPaths) {
            submitAndCollect(otherPath, workers, resultCollector)
        }
    }
}

/**
 * @return size of files in this directory, plus list of paths to subdirectories found
 */
fun processDirectory(path: String): Pair<Long, List<String>> {
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
    return fileSize to toIterate
}

private fun stat.isDirectory() = st_mode and S_IFMT.toUInt() == S_IFDIR.toUInt()

private fun stat.isFile() = st_mode and S_IFMT.toUInt() != S_IFDIR.toUInt()

private fun dirent.toFileInfo(parentPath: String): Pair<String, stat>? {
    val nameBuf = this.d_name.toKString()
    val name = nameBuf.trimEnd('\u0000')
    if (name != "." && name != "..") {
        val fullPath = "$parentPath/$name"
        return fullPath to statFile(fullPath)
    }
    return null
}

private fun statFile(path: String) = memScoped {
    val info = alloc<stat>()
    stat(path, info.ptr)
    info
}
