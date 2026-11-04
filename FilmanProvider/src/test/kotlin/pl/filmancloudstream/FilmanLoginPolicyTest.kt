package pl.filmancloudstream

import org.junit.Assert.*
import org.junit.Test

class FilmanLoginPolicyTest {
    @Test fun keepsSavedCredentialsRestrictedToTheExactHttpsOrigin() {
        assertTrue(FilmanLoginPolicy.isFilmanUrl("https://filman.cc/logowanie"))
        assertTrue(FilmanLoginPolicy.isFilmanUrl("https://FILMAN.cc:443/"))
        for (url in listOf("http://filman.cc/", "https://www.filman.cc/", "https://filman.cc.evil.example/",
            "https://user:pass@filman.cc/", "https://filman.cc:444/", "https://challenges.cloudflare.com/")) {
            assertFalse(url, FilmanLoginPolicy.isFilmanUrl(url))
        }
    }

    @Test fun permitsTurnstileFrameNavigationInTheLegacyCallback() {
        assertTrue(FilmanLoginPolicy.allowLegacyNavigation("https://filman.cc/logowanie"))
        assertTrue(FilmanLoginPolicy.allowLegacyNavigation("https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/g/turnstile/if/abc"))
        assertTrue(FilmanLoginPolicy.allowLegacyNavigation("about:blank#widget"))
        assertTrue(FilmanLoginPolicy.allowLegacyNavigation("about:srcdoc"))
    }

    @Test fun rejectsSpoofedChallengeOriginsAndLocalSchemes() {
        for (url in listOf("https://challenges.cloudflare.com.evil.example/", "https://challenges.cloudflare.com@evil.example/",
            "https://user@challenges.cloudflare.com/", "https://challenges.cloudflare.com:444/", "http://challenges.cloudflare.com/",
            "file:///tmp/login.html", "javascript:alert(1)", "about:blank?url=https://evil.example/")) {
            assertFalse(url, FilmanLoginPolicy.allowLegacyNavigation(url))
        }
    }

    @Test fun verificationFramesNeverQualifyForCredentialForwarding() {
        for (url in listOf("https://challenges.cloudflare.com/", "about:blank", "about:srcdoc")) {
            assertTrue(FilmanLoginPolicy.isVerificationFrame(url))
            assertFalse(FilmanLoginPolicy.isFilmanUrl(url))
        }
    }
}
