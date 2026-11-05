package pl.ekinocloudstream

/** One user-completed verification, followed by one fresh read using its saved browser session. */
internal object EkinoPlaybackVerification {
    class Required : Exception()
    class NotCompleted : Exception()

    suspend fun <T> resolve(read: suspend () -> T, verify: suspend () -> Boolean): T =
        try { read() } catch (_: Required) {
            if (!verify()) throw NotCompleted()
            read()
        }
}
