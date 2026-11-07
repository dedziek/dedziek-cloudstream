package pl.animegatecloudstream

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only the hosting's headers enter this client, never the AnimeGate login snapshot. */
internal class AnimeGateMediaHttp(base: OkHttpClient) {
    private val client = base.newBuilder().cache(null).retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).callTimeout(8, TimeUnit.SECONDS).build()

    suspend fun read(url: String, headers: Map<String, String>): AnimeGateMediaProbe.Response {
        val request = Request.Builder().url(url).apply { headers.forEach { (key, value) -> header(key, value) } }.build()
        val call = client.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val value = response.use {
                            val prefix = if (it.code in setOf(200, 206)) it.body.byteStream().let { input ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(4096)
                                while (output.size() < 8192) {
                                    val count = input.read(buffer, 0, minOf(buffer.size, 8192 - output.size()))
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            } else ByteArray(0)
                            val value = AnimeGateMediaProbe.Response(it.code, it.header("Content-Type").orEmpty(), prefix, it.header("Location"))
                            // Closing an unfinished HTTP body can drain it for connection reuse.
                            // Cancel this stream first when the sample is truncated or unused.
                            val length = it.body.contentLength()
                            if (length != prefix.size.toLong() && (prefix.size == 8192 || it.code !in setOf(200, 206))) call.cancel()
                            value
                        }
                        if (continuation.isActive) continuation.resume(value)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }
}
