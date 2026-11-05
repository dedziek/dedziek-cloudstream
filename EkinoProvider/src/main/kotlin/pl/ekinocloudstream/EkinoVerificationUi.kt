package pl.ekinocloudstream

import android.os.Handler
import android.os.Looper
import com.lagradost.cloudstream3.CommonActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Always visible and user-operated. Never solves or submits a verification challenge. */
internal object EkinoVerificationUi {
    private val active = AtomicBoolean(false)

    suspend fun verify(session: EkinoSession, materialUrl: String): Boolean {
        if (!EkinoParser.isDetailUrl(materialUrl) || !active.compareAndSet(false, true)) return false
        return try {
            withTimeoutOrNull(180_000L) {
                withContext(Dispatchers.Main.immediate) {
                    suspendCancellableCoroutine { continuation ->
                        val activity = CommonActivity.activity
                        if (activity == null || activity.isFinishing || activity.isDestroyed) {
                            continuation.resume(false)
                            return@suspendCancellableCoroutine
                        }
                        if (!continuation.isActive) return@suspendCancellableCoroutine
                        val dialog = EkinoLoginUi.openLogin(activity, session, materialUrl, verification = true) { completed ->
                            if (continuation.isActive) continuation.resume(completed)
                        }
                        continuation.invokeOnCancellation {
                            Handler(Looper.getMainLooper()).post { runCatching { dialog?.dismiss() } }
                        }
                    }
                }
            } ?: false
        } finally { active.set(false) }
    }
}
