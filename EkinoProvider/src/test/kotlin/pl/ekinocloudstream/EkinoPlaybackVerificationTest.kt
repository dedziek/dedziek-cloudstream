package pl.ekinocloudstream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EkinoPlaybackVerificationTest {
    @Test fun ordinaryPlaybackDoesNotOpenVerification() = runBlocking {
        val result = EkinoPlaybackVerification.resolve(read = { "video" }, verify = { error("Unexpected dialog") })
        assertEquals("video", result)
    }

    @Test fun completedVerificationRepeatsTheSameReadOnceWithTheNewSession() = runBlocking {
        var reads = 0
        var savedSession = false
        var verifications = 0
        val result = EkinoPlaybackVerification.resolve(read = {
            reads++
            if (!savedSession) throw EkinoPlaybackVerification.Required()
            "video"
        }, verify = { verifications++; savedSession = true; true })
        assertEquals("video", result)
        assertEquals(2, reads)
        assertEquals(1, verifications)
    }

    @Test fun cancellingTheWindowDoesNotRepeatRequests() = runBlocking {
        var reads = 0
        try {
            EkinoPlaybackVerification.resolve(read = { reads++; throw EkinoPlaybackVerification.Required() }, verify = { false })
            fail("Expected cancellation result")
        } catch (_: EkinoPlaybackVerification.NotCompleted) { }
        assertEquals(1, reads)
    }

    @Test fun anUnfinishedChallengeCannotCreateARepeatLoop() = runBlocking {
        var reads = 0
        var verifications = 0
        try {
            EkinoPlaybackVerification.resolve(read = { reads++; throw EkinoPlaybackVerification.Required() }, verify = { verifications++; true })
            fail("Expected remaining challenge")
        } catch (_: EkinoPlaybackVerification.Required) { }
        assertEquals(2, reads)
        assertEquals(1, verifications)
    }

    @Test fun networkDenialDoesNotOpenABrowser() = runBlocking {
        try {
            EkinoPlaybackVerification.resolve(read = { throw IllegalStateException("HTTP 403") }, verify = { error("Unexpected verification") })
            fail("Expected denial")
        } catch (error: IllegalStateException) { assertEquals("HTTP 403", error.message) }
    }

    @Test fun coroutineCancellationIsPropagated() = runBlocking {
        try {
            EkinoPlaybackVerification.resolve(read = { throw CancellationException("cancel") }, verify = { error("Unexpected verification") })
            fail("Expected coroutine cancellation")
        } catch (error: CancellationException) { assertEquals("cancel", error.message) }
    }
}
