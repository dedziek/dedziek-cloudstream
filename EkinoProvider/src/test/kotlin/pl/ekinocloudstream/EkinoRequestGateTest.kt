package pl.ekinocloudstream

import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EkinoRequestGateTest {
    @Test fun overlappingNativeRequestsRunOneAtATime() = runBlocking {
        val gate = EkinoRequestGate()
        var active = 0; var maximum = 0; var completed = 0
        List(20) { launch {
            gate.request {
                active++; maximum = maxOf(maximum, active)
                yield()
                completed++; active--
            }
        } }.joinAll()
        assertEquals(20, completed)
        assertEquals(1, maximum)
    }

    @Test fun aQueuedRequestDoesNotReachTheNetworkAfterTheFirstOneGetsBlocked() = runBlocking {
        val gate = EkinoRequestGate()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.request { requests++; release.await(); gate.pause() }
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            try { gate.request { requests++ }; false }
            catch (_: EkinoRequestGate.Paused) { true }
        }
        release.complete(Unit)
        first.join()
        assertTrue(second.await())
        assertEquals(1, requests)
        assertTrue(gate.isPaused)
    }

    @Test fun newRequestsRemainPausedAcrossReconstructionUntilAnExplicitResume() = runBlocking {
        var marker = false
        val original = EkinoRequestGate(marker) { marker = it }
        original.request { original.pause() }
        assertTrue(marker)
        val reopened = EkinoRequestGate(marker) { marker = it }
        var requests = 0
        repeat(5) {
            try { reopened.request { requests++ }; fail("Expected local pause") }
            catch (_: EkinoRequestGate.Paused) { }
        }
        assertEquals(0, requests)
        reopened.resume()
        assertFalse(marker)
        reopened.request { requests++ }
        assertEquals(1, requests)
    }

    @Test fun cancellingAQueuedRequestDoesNotExecuteItOrLeaveTheMutexLocked() = runBlocking {
        val gate = EkinoRequestGate()
        val release = CompletableDeferred<Unit>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) { gate.request { release.await() } }
        var requests = 0
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) { gate.request { requests++ } }
        waiting.cancelAndJoin()
        release.complete(Unit); first.join()
        assertEquals(0, requests)
        gate.request { requests++ }
        assertEquals(1, requests)
    }

    @Test fun failedMarkerRemovalKeepsRequestsPaused() = runBlocking {
        val gate = EkinoRequestGate(true) { if (!it) throw IOException("fixture write failure") }
        try { gate.resume(); fail("Expected storage failure") } catch (_: IOException) { }
        assertTrue(gate.isPaused)
        try { gate.request { error("Must not send a request") }; fail("Expected pause") }
        catch (_: EkinoRequestGate.Paused) { }
    }
}
