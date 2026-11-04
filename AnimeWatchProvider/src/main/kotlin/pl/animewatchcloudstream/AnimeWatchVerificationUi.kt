package pl.animewatchcloudstream

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.lagradost.cloudstream3.CommonActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Opened only by the plugin's settings button, never by loadLinks(). */
object AnimeWatchVerificationUi {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var active: Job? = null

    fun show(context: Context, session: AnimeWatchPlaybackSession) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { show(context, session) }
            return
        }
        val activity = CommonActivity.activity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Toast.makeText(context, "Otwórz ustawienia dodatku na ekranie CloudStream.", Toast.LENGTH_LONG).show()
            return
        }
        val request = session.pending()
        val window = AlertDialog.Builder(activity).setTitle("AnimeWatch — weryfikacja Byse")
            .setNegativeButton("Zamknij", null)
        if (request == null) {
            window.setMessage("Nie ma zapamiętanej próby Byse. Najpierw spróbuj odtworzyć wybrany odcinek, a następnie wróć do tych ustawień, jeśli odtwarzanie się nie powiedzie.")
        } else {
            window.setMessage("Otwórz odtwarzacz ostatniego odcinka. Samodzielnie naciśnij przycisk odtwarzania i dokończ weryfikację, jeżeli Byse jej wymaga. Po uzyskaniu źródła okno zamknie się. Następnie uruchom ten sam odcinek ponownie w CloudStream.")
            window.setPositiveButton("Otwórz weryfikację") { _, _ ->
                if (active?.isActive == true) return@setPositiveButton
                active = scope.launch {
                    try {
                        val stream = AnimeWatchBrowser.resolve(request.embedUrl, request.episodeUrl, request.frameUrl, interactive = true)
                        if (stream != null) {
                            session.store(request, stream)
                            Toast.makeText(activity, "Uzyskano źródło. Uruchom ponownie ten sam odcinek w ciągu minuty.", Toast.LENGTH_LONG).show()
                        }
                    } catch (_: CancellationException) {
                        // Closing the verification window cancels only this manual attempt.
                    } catch (error: Exception) {
                        Toast.makeText(activity, error.message ?: "Byse nie udostępnił źródła wideo.", Toast.LENGTH_LONG).show()
                    } finally {
                        active = null
                    }
                }
            }
        }
        window.show()
    }
}
