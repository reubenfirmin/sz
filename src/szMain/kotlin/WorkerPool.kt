import platform.posix.sched_yield
import platform.posix.usleep
import kotlin.native.concurrent.*

/**
 * A pool of workers. This avoids using the queue built into individual workers.
 *
 * This pool is not thread safe.
 * @param numWorkers how many threads to manage
 * @param sleepTime how long to sleep (microseconds) between grooming when waiting for a worker to become available
 */
class WorkerPool<P, R, S>(numWorkers: Int, val sleepTime: Int) {
    private val workers = mutableListOf<Worker>()
    private val busy = mutableListOf<Job<P, R>>()

    init {
        for (i in 0..numWorkers) {
            workers.add(Worker.start())
        }
    }

    /**
     * Submit the specificed job; may return results that completed in the meantime, which should be handled.
     * NOTE that these results are NOT directly associated with this path - they are from previously submitted and now
     * complete jobs.
     */
    fun execute(param: P, resultTransformer: (P, R) -> S, task: (P) -> R): List<S> {
        val results = mutableListOf<S>() // TODO pass in
        while (workers.size == 0) {
            consumeBusy(results, resultTransformer)
            if (workers.size == 0) {
                usleep(sleepTime.toUInt())
            }
        }
        val worker = next(param) { worker ->
            worker.execute(TransferMode.SAFE, { param to task }) {
                it.second(it.first)
            }
        }
        return results
    }

    /**
     * Check for results. Returns whether jobs were busy before consuming results.
     */
    fun sip(results: MutableList<S>, resultTransformer: (P, R) -> S): Boolean {
        val found = busy.isNotEmpty()
        consumeBusy(results, resultTransformer)
        return found
    }

    /**
     * Obtain results from any workers, optionally terminating. Runs until no workers are active. Returns
     * whether or not it found any running.
     */
    fun drain(terminate: Boolean, results: MutableList<S>, resultTransformer: (P, R) -> S): Boolean {
        var found = false
        while(busy.isNotEmpty()) {
            consumeBusy(results, resultTransformer)
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

    /**
     * Get the next available worker, and run the specified job once available.
     */
    private fun next(param: P, job: (worker: Worker) -> Future<R>): Worker {
        while (workers.size == 0) {
            sched_yield()
        }

        val worker = workers.removeFirstOrNull()
        if (worker != null) {
            busy.add(Job(param, job.invoke(worker), worker))
            return worker
        }

        // we missed it because of race condition; go around again
        // TODO timeout
        return next(param, job)
    }

    /**
     * Groom the current jobs
     */
    private fun consumeBusy(results: MutableList<S>, resultTransformer: (P, R) -> S): List<S> {
        val iterator = busy.iterator()
        while (iterator.hasNext()) {
            val job = iterator.next()
            // TODO other states?
            if (job.future.state == FutureState.COMPUTED) {
                val result = job.future.result
                results.add(resultTransformer.invoke(job.param, result))
                iterator.remove()
                workers.add(job.workerToReturn)
            }
            sched_yield()
        }
        return results
    }

    data class Job<T, S>(val param: T, val future: Future<S>, val workerToReturn: Worker)
}