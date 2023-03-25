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
 * @param sleepTime how long to sleep (microseconds) between grooming when waiting for a worker to become available
 */
class WorkerPool<P, R>(numWorkers: Int, val sleepTime: Int) {
    // TODO potential optimization: store workers in an array, and booleans in a shadow array indicating busy / available
    // workers that are available to run something
    private val workers = ArrayList<Worker>(numWorkers)
    // workers that are busy doing something
    private val busy = ArrayList<Job<P, R>>(numWorkers)
    // temporary stack of results which are ready
    private val resultsStack = ArrayList<R>(numWorkers)

    init {
        for (i in 0..numWorkers) {
            workers.add(Worker.start())
        }
    }

    /**
     * Submit the specified task.
     */
    fun execute(param: P, task: (P) -> R) {

        while (workers.size == 0) {
            consumeBusy(resultsStack)
            if (workers.size == 0) {
                usleep(sleepTime.toUInt())
            }
        }
        val worker = workers.removeFirst()
        val future = worker.execute(TransferMode.SAFE, { param to task }) {
            it.second(it.first)
        }
        busy.add(Job(param, future, worker))
    }

    /**
     * Check for results. Returns whether jobs were busy before consuming results.
     */
    fun sip(results: MutableList<R>): Boolean {
        val found = busy.isNotEmpty()
        consumeBusy(results)
        drainStack(results)
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
        drainStack(results)
        if (terminate) {
            workers.forEach {
                it.requestTermination()
            }
            workers.clear()
        }
        return found
    }

    /**
     * Groom the current jobs, writing results from any complete jobs into the provided results list. (This modification
     * of the input avoids unnecessary allocations, and improves performance.)
     */
    private fun consumeBusy(results: MutableList<R>) {
        val iterator = busy.iterator()
        while (iterator.hasNext()) {
            val job = iterator.next()
            // TODO other states don't seem to be needed, but for completeness/robustness worth adding
            if (job.future.state == FutureState.COMPUTED) {
                val result = job.future.result
                results.add(result)
                iterator.remove()
                workers.add(job.workerToReturn)
            }
            sched_yield()
        }
    }

    /**
     * Drain the stack of results accumulated from calls to execute.
     */
    private fun drainStack(results: MutableList<R>) {
        val iterator = resultsStack.iterator()
        while (iterator.hasNext()) {
            val result = iterator.next()
            results.add(result)
            iterator.remove()
        }
    }

    data class Job<T, S>(val param: T, val future: Future<S>, val workerToReturn: Worker)
}