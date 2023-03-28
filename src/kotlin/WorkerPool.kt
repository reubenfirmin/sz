import platform.posix.sched_yield
import platform.posix.usleep
import kotlin.native.concurrent.*

/**
 * A pool of workers.
 *
 * This pool is deliberately not thread safe (to avoid performance penalties from locking). It's intended that you
 * manage tasks from a single coordinator thread (which otherwise doesn't do any real work.)
 *
 * @param numWorkers how many threads to manage
 */
class WorkerPool<P, R>(numWorkers: Int) {

    // TODO perhaps use the built in queues on workers, though this would add a layer of complexity we may not need

    /**
     * A worker with associated state.
     * @param worker the worker
     * @param future if a task was submitted to a worker, this will be non null
     * @param busy if a worker is executing OR the result hasn't been collected from the future, this will be true
     */
    private data class Job<P, R>(val worker: Worker, var future: Future<R>?, var busy: Boolean)

    /**
     * Our worker pool, with associated state.
     */
    private val workers = Array<Job<P, R>>(numWorkers) {
        Job<P, R>(Worker.start(), null, false)
    }

    /**
     * A temporary stack of results which are ready. We add/pop on the end only, ergo it's a stack. LinkedList is
     * not available in Kotlin Native, otherwise a queue might be a more natural fit. Assumption is that removing
     * from the end of the internal array will avoid internal shuffling of elements.
     *
     * This is written to by [consumeBusy] and read from [sip] and [drain].
     */
    private val resultsStack = ArrayList<R>(numWorkers * 4)

    /**
     * A pointer to a known available worker; -1 if all are currently. Maintained by [execute] and [consumeBusy].
     */
    private var available = 0

    /**
     * Submit the specified task. Will block until a worker is available to use.
     */
    fun execute(param: P, task: (P) -> R) {
        // While nothing is known to be available, groom the queue.
        while (available == -1) {
            consumeBusy()
            if (available == -1) {
                sched_yield()
            }
        }

        // Remember, the coordinator is single threaded, so we can get away with this.
        val job = workers[available]
        job.busy = true
        job.future = job.worker.execute(TransferMode.SAFE, { param to task }) {
            it.second(it.first)
        }

        // Move the pointer to an available worker (or -1 if none are).
        available = workers.indexOfFirst { !it.busy }
    }

    /**
     * Return a result if any is ready. May groom the internal worker queue, meaning that a subsequent call to
     * anyBusy may return false unless new jobs are submitted. Does not block.
     */
    fun sip(): R? {
        if (resultsStack.isEmpty()) {
            consumeBusy()
        }
        return resultsStack.removeLastOrNull()
    }

    /**
     * Check if any workers are busy. If this returns false there may still be results to retrieve from completed
     * workers. This function does not modify state.
     */
    fun anyBusy() = workers.any { it.busy }

    /**
     * Terminate and return all pending results. The pool is unusable after this is called once.
     */
    fun drain(): List<R> {
        // must be called once (in case none are busy)
        consumeBusy()

        // but if we do have busy, wait for them to complete
        while(anyBusy()) {
            sched_yield()
            consumeBusy()
        }

        // now shut down all workers
        workers.forEach {
            it.worker.requestTermination()
        }
        return resultsStack.toList()
    }

    /**
     * Groom the current jobs, writing results from any complete jobs into the provided results stack.
     * (This modification of the input avoids unnecessary allocations, and improves performance.)
     *
     * Sets the pointer to any observed available worker.
     */
    private fun consumeBusy() {
        // Note that since we iterate forward, available is set to the last non-busy worker. This is an internal
        // implementation detail though, shouldn't be relied on, and is not advertised in the interface. (It's also
        // counteracted by [execute], which resets available to the first non-busy worker.
        workers.forEachIndexed() { i, job ->
            if (job.busy) {
                job.future!!.let {
                    // TODO other states don't seem to be needed, but for completeness/robustness worth adding
                    if (it.state == FutureState.COMPUTED) {
                        val result = it.result
                        resultsStack.add(result)
                        job.future = null
                        job.busy = false
                        // point at this worker as being available
                        available = i
                    }
                }
            } else {
                // point at this worker as being available
                available = i
            }
        }
    }
}
