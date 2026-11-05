package pl.ekinocloudstream

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EkinoPlayerRequestsTest {
    private fun player(host: String, language: String = "Lektor PL") = EkinoParser.Player("https://ekino.ws/watch/f/$host/fixture", host, language)

    @Test fun anEmittedSourcePreventsFetchingOtherMirrorsOfTheSameLanguage() = runBlocking {
        val reads = mutableListOf<String>()
        EkinoPlayerRequests.visit(listOf(player("player"), player("upzone"))) {
            reads.add(it.host); true
        }
        assertEquals(listOf("player"), reads)
    }

    @Test fun aFailedMirrorStillAllowsFallbackUntilOneReturnsVideo() = runBlocking {
        val reads = mutableListOf<String>()
        EkinoPlayerRequests.visit(listOf(player("player"), player("mix"), player("upzone"))) {
            reads.add(it.host); it.host == "mix"
        }
        assertEquals(listOf("player", "mix"), reads)
    }

    @Test fun distinctLanguagesRemainAvailableAndUnknownLabelsAreNotCollapsed() = runBlocking {
        val reads = mutableListOf<String>()
        EkinoPlayerRequests.visit(listOf(player("a", " Lektor PL "), player("b", "lektor pl"),
            player("c", "Napisy PL"), player("d", ""), player("e", ""))) {
            reads.add(it.host); true
        }
        assertEquals(listOf("a", "c", "d", "e"), reads)
    }

    @Test fun theNextPlaybackHasItsOwnSelectionAndDoesNotReuseAnOldSource() = runBlocking {
        val reads = mutableListOf<String>()
        repeat(2) { EkinoPlayerRequests.visit(listOf(player("player"), player("upzone"))) {
            reads.add(it.host); true
        } }
        assertEquals(listOf("player", "player"), reads)
    }

    @Test fun denialStopsTheSequenceWithoutTryingAnotherMirror() = runBlocking {
        val reads = mutableListOf<String>()
        try {
            EkinoPlayerRequests.visit(listOf(player("player"), player("upzone"))) {
                reads.add(it.host); throw IllegalStateException("verification fixture")
            }
            fail("Expected denial")
        } catch (_: IllegalStateException) { }
        assertEquals(listOf("player"), reads)
    }
}
