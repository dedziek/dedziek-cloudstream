package pl.animewatchcloudstream

import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.*
import org.junit.Test

class AnimeWatchVerificationFlowTest {
    private val stream = AnimeWatchPlayback.Stream("https://cdn.example/master.m3u8", emptyMap(), "https://frame.example/id/code", true)

    @Test fun aReadySourceNeverOpensVerification() {
        val calls = mutableListOf<Boolean>()
        val result = immediate {
            AnimeWatchPlayback.resolveWithVerification { interactive -> calls += interactive; stream }
        }
        assertEquals(stream, result)
        assertEquals(listOf(false), calls)
    }

    @Test fun verificationWaitsAndReturnsTheSourceToTheOriginalCaller() {
        val calls = mutableListOf<Boolean>()
        var waiting: Continuation<AnimeWatchPlayback.Stream?>? = null
        var outcome: Result<AnimeWatchPlayback.Stream?>? = null
        val load: suspend () -> AnimeWatchPlayback.Stream? = {
            AnimeWatchPlayback.resolveWithVerification { interactive ->
                calls += interactive
                if (!interactive) throw AnimeWatchPlayback.VerificationRequired()
                suspendCoroutine { waiting = it }
            }
        }
        load.startCoroutine(collect { outcome = it })
        assertEquals(listOf(false, true), calls)
        assertNotNull(waiting)
        assertNull(outcome)
        waiting!!.resume(stream)
        assertEquals(stream, outcome!!.getOrThrow())
        assertEquals(listOf(false, true), calls)
    }

    @Test fun aMissingSourceDoesNotOpenAnUnrequestedWindow() {
        val calls = mutableListOf<Boolean>()
        val result = immediate {
            AnimeWatchPlayback.resolveWithVerification { interactive -> calls += interactive; null }
        }
        assertNull(result)
        assertEquals(listOf(false), calls)
    }

    @Test fun networkErrorsAndCancellationDoNotOpenVerification() {
        for (error in listOf(IllegalStateException("HTTP 403"), CancellationException("cancelled"))) {
            val calls = mutableListOf<Boolean>()
            val outcome = runCatching {
                immediate {
                    AnimeWatchPlayback.resolveWithVerification { interactive -> calls += interactive; throw error }
                }
            }
            assertSame(error, outcome.exceptionOrNull())
            assertEquals(listOf(false), calls)
        }
    }

    @Test fun failureOrCancellationInsideVerificationNeverReopensIt() {
        for (error in listOf(IllegalStateException("verification unavailable"), CancellationException("user closed window"))) {
            val calls = mutableListOf<Boolean>()
            val outcome = runCatching {
                immediate {
                    AnimeWatchPlayback.resolveWithVerification { interactive ->
                        calls += interactive
                        if (!interactive) throw AnimeWatchPlayback.VerificationRequired()
                        throw error
                    }
                }
            }
            assertSame(error, outcome.exceptionOrNull())
            assertEquals(listOf(false, true), calls)
        }
    }

    private fun <T> immediate(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(collect { outcome = it })
        return checkNotNull(outcome) { "Expected an immediate fake resolver" }.getOrThrow()
    }

    private fun <T> collect(receive: (Result<T>) -> Unit) = object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) = receive(result)
    }
}
