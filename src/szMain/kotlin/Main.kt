import io.Color
import io.IOHelpers.ansiFg
import kotlinx.atomicfu.*
import io.IOHelpers.printErr
import kotlinx.cinterop.*
import platform.posix.*

val workers = WorkerPool(20) // TODO tuneable

fun main(args: Array<String>) {

    if (args.isEmpty()) {
        printErr("Please specify a device or a directory to scan from")
        exit(1)
    }

    val dir = args[0].removeSuffix("/")

    val results = mutableMapOf<String, Long>()
    val res = submitAndCollect(dir, results)
    val resultQueue = mutableListOf<WorkerPool.Result>()

    // keep iterating for results until there's nothing running
    while (workers.drain(false, resultQueue)) {
        resultQueue.forEach { result ->
//            println("Adding ${result} in top")
            results[result.path] = result.size
            result.otherPaths.forEach { path ->
                submitAndCollect(path, results)
            }
        }
        resultQueue.clear()
    }
    workers.drain(true, resultQueue)
    resultQueue.forEach {result ->
  //      println("Adding ${result} in middle")
        results[result.path] = result.size
        if (result.otherPaths.isNotEmpty()) {
            print(ansiFg(Color.HIGHLIGHT_1, "ERROR"))
            println(" ${result.path} ${result.otherPaths.size}")
        }
    }


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

fun submitAndCollect(path: String, resultCollector: MutableMap<String, Long>) {
//    println("calling execute on $path")
    val results = workers.execute(path)
    for (result in results) {
//        println("Adding ${result} in bottom")
        resultCollector[result.path] = result.size
        for (otherPath in result.otherPaths) {
            //println(">> $otherPath")
            submitAndCollect(otherPath, resultCollector)
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
