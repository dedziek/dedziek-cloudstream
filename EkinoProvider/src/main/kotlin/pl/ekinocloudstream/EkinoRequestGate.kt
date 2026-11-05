package pl.ekinocloudstream

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize native site reads and stop queued/new reads after a denial. No timer or retry. */
internal class EkinoRequestGate(initiallyPaused: Boolean = false,
                               private val persist: (Boolean) -> Unit = {}) {
    class Paused : Exception()
    private val mutex = Mutex()
    @Volatile var isPaused = initiallyPaused
        private set

    suspend fun <T> request(block: suspend () -> T): T = mutex.withLock {
        if (isPaused) throw Paused()
        block()
    }

    @Synchronized fun pause() {
        if (isPaused) return
        isPaused = true
        persist(true)
    }

    /** Called only by an explicit settings action or successful user-chosen session save. */
    @Synchronized fun resume() {
        persist(false)
        isPaused = false
    }
}
