package pl.animegatecloudstream

import org.junit.Assert.*
import org.junit.Test

class AnimeGateSessionResponseTest {
    private val identity = """{"id":"PRIVATE_ID","username":"PRIVATE_NAME","email":"PRIVATE_EMAIL"}"""

    @Test fun recognisesWrappedUserAndDirectCurrentUserResponsesWithoutExportingValues() {
        for (body in listOf("""{"user":$identity}""", """{"data":{"user":$identity},"success":true}""", """{"session":{"user":$identity}}""")) {
            val result = AnimeGateSessionResponse.inspect(body, false)
            assertEquals(AnimeGateProbe.Outcome.VERIFIED_SESSION_API, result.outcome)
            assertFalse(result.toString().contains("PRIVATE"))
        }
        for (body in listOf(identity, """{"data":$identity}""")) {
            assertEquals(AnimeGateProbe.Outcome.VERIFIED_SESSION_API, AnimeGateSessionResponse.inspect(body, true).outcome)
            assertEquals(AnimeGateProbe.Outcome.UNCONFIRMED, AnimeGateSessionResponse.inspect(body, false).outcome)
        }
    }
    @Test fun sessionMetadataAndPartialProfilesAreNotProofOfAuthentication() {
        for (body in listOf("""{"session":{"id":"PRIVATE","name":"browser"}}""", """{"user":{"id":"PRIVATE"}}""", """{"user":{"name":"PRIVATE"}}""", """{"user":{"id":0,"username":"PRIVATE"}}"""))
            assertEquals(AnimeGateProbe.Outcome.UNCONFIRMED, AnimeGateSessionResponse.inspect(body, true).outcome)
    }
    @Test fun explicitDenialsOverrideEvenAUserObject() {
        for (flag in listOf("authenticated", "isAuthenticated", "isLoggedIn", "loggedIn"))
            assertEquals(AnimeGateProbe.Outcome.LOGIN_REQUIRED, AnimeGateSessionResponse.inspect("""{"$flag":false,"user":$identity}""", true).outcome)
        for (body in listOf("""{"success":false,"user":$identity}""", """{"error":"PRIVATE","user":$identity}""", """{"status":"error","user":$identity}""", """{"code":401,"user":$identity}"""))
            assertEquals(AnimeGateProbe.Outcome.UNCONFIRMED, AnimeGateSessionResponse.inspect(body, true).outcome)
        assertEquals(AnimeGateProbe.Outcome.LOGIN_REQUIRED, AnimeGateSessionResponse.inspect("""{"user":null,"data":{"user":$identity}}""", true).outcome)
    }
    @Test fun unknownKeysArraysTokensAndErrorsExposeOnlyBoundedTypes() {
        val result = AnimeGateSessionResponse.inspect("""{"PRIVATE_KEY":{"email":"PRIVATE_EMAIL","token":"PRIVATE_TOKEN"},"data":["PRIVATE_VALUE"],"message":"PRIVATE_MESSAGE"}""", false)
        assertFalse(result.toString().contains("PRIVATE"))
        assertTrue(result.schema.contains("{field}"))
        assertTrue(result.schema.contains("array"))
        assertTrue(result.schema.contains("email:string"))
        assertFalse(AnimeGateSessionResponse.inspect("<html>PRIVATE</html>", false).toString().contains("PRIVATE"))
        assertEquals("html", AnimeGateSessionResponse.inspect("<html>PRIVATE</html>", false).schema)
        assertEquals("non_json", AnimeGateSessionResponse.inspect("PRIVATE_TOKEN", false).schema)
    }
    @Test fun shapeDepthAndWidthAreBounded() {
        val body = (1..100).joinToString(",", "{", "}") { "\"PRIVATE_$it\":{\"data\":{\"data\":{\"email\":\"PRIVATE_EMAIL\"}}}" }
        val schema = AnimeGateSessionResponse.inspect(body, false).schema
        assertTrue(schema.length <= 1500)
        assertFalse(schema.contains("PRIVATE"))
        assertTrue(schema.contains("object"))
    }
}
