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
class WorkerPool<P, R>(numWorkers: Int, val sleepTime: Int) {
    private val workers = mutableListOf<Worker>()
    private val busy = mutableListOf<Job<P, R>>()

    init {
        for (i in 0..numWorkers) {
            workers.add(Worker.start())
        }
    }

    /**
     * Submit the specified task; may return results that completed in the meantime, which should be handled.
     * NOTE that these results are NOT directly associated with this task - they are from previously submitted and now
     * complete tasks.
     */
    fun execute(param: P, task: (P) -> R): List<R> {
        val results = mutableListOf<R>()
        while (workers.size == 0) {
            consumeBusy(results)
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
    fun sip(results: MutableList<R>): Boolean {
        val found = busy.isNotEmpty()
        consumeBusy(results)
        return found
    }

    /**
     * Obtain results from any workers, optionally terminating. Runs until no workers are active. Returns
     * whether or not it found any running.
     *
     * @param terminate shut down workers if true
     * @param results list to accumulate results in
     */
    fun drain(terminate: Boolean, results: MutableList<R>): Boolean {
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
     * Groom the current jobs.
     */
    private fun consumeBusy(results: MutableList<R>) {
        val iterator = busy.iterator()
        while (iterator.hasNext()) {
            val job = iterator.next()
            // TODO other states?
            if (job.future.state == FutureState.COMPUTED) {
                val result = job.future.result
                results.add(result)
                iterator.remove()
                workers.add(job.workerToReturn)
            }
            sched_yield()
        }
    }

    data class Job<T, S>(val param: T, val future: Future<S>, val workerToReturn: Worker)
}