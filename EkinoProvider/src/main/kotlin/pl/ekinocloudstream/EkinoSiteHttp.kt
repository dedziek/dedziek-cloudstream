package pl.ekinocloudstream

import com.lagradost.nicehttp.Requests
import okhttp3.CookieJar
import okhttp3.OkHttpClient

internal object EkinoSiteHttp {
    /** Preserve CloudStream's transport/DNS, but not its default headers or shared cookies.
     * NiceHttp 0.4.11 merges header maps case-sensitively: its default `user-agent`
     * and our saved `User-Agent` would otherwise both be sent, with different identities.
     */
    fun isolated(base: OkHttpClient, journal: EkinoNetworkJournal? = null): Requests = Requests(
        baseClient = base.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .retryOnConnectionFailure(false)
            .apply { if (journal != null) eventListenerFactory(journal.factory()) }
            .build(),
        defaultHeaders = emptyMap(),
        defaultCookies = emptyMap(),
        defaultTimeOut = 25L
    )
}
