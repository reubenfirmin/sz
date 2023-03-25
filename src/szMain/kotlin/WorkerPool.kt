import platform.posix.sched_yield
import platform.posix.usleep
import kotlin.native.concurrent.*

/**
 * A pool of workers. This avoids using the queue built into individual workers.
 *
 * This pool is not thread safe.
 */
class WorkerPool(numWorkers: Int) {
    private val workers = mutableListOf<Worker>()
    private val busy = mutableListOf<Job>()

    init {
        for (i in 0..numWorkers) {
            workers.add(Worker.start())
        }
    }

    private fun next(path: String, job: (worker: Worker) -> Future<Pair<Long, List<String>>>): Worker {
        while (workers.size == 0) {
            sched_yield()
        }

        val worker = workers.removeFirstOrNull()
        if (worker != null) {
            busy.add(Job(path, job.invoke(worker), worker))
            return worker
        }

        // we missed it because of race condition; go around again
        // TODO timeout
        return next(path, job)
    }

    data class Job(val path: String, val future: Future<Pair<Long, List<String>>>, val workerToReturn: Worker)

    data class Result(val path: String, val size: Long, val otherPaths: List<String>)

    /**
     * Submit the specificed job; may return results that completed in the meantime, which should be handled.
     * NOTE that these results are NOT directly associated with this path - they are from previously complete jobs.
     */
    fun execute(fullPath: String): List<Result> {
        val results = mutableListOf<Result>() // TODO pass in
        while (workers.size == 0) {
            consumeBusy(results)
            usleep(10000) // TODO tuneable
        }
        val worker = next(fullPath) { worker ->
            worker.execute(TransferMode.SAFE, { fullPath }) {
                processDirectory(it)
            }
        }
        return results
    }

    /**
     * Groom the current jobs
     */
    private fun consumeBusy(results: MutableList<Result>): List<Result> {
        val iterator = busy.iterator()
        while (iterator.hasNext()) {
            val job = iterator.next()
            // TODO other states?
            if (job.future.state == FutureState.COMPUTED) {
                val result = job.future.result
                results.add(Result(job.path, result.first, result.second))
                iterator.remove()
                workers.add(job.workerToReturn)
            }
            sched_yield()
        }
        return results
    }

    /**
     * Obtain results from any remaining workers, optionally terminating
     */
    fun drain(terminate: Boolean, results: MutableList<Result>): Boolean {
        var found = false
        while(busy.isNotEmpty()) {
            consumeBusy(results)
            found = true
        }
        if (terminate) {
            workers.forEach {
                it.requestTermination()
            }
            workers.clear()
        }
        return found
    }
}