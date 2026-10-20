package app.kreate.di

import co.touchlab.kermit.Logger
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.net.ProtocolException

/**
 * Keep proxy, DNS and functional interceptors, but never buffer media through an HTTP logger.
 * A BODY logger would wait for the entire throttled song; signed URLs must not be logged either.
 *
 * Google Video can redirect an HTTPS stream to HTTP. Stop OkHttp before it attempts that
 * connection, then follow the same signed CDN destination over HTTPS. Android's cleartext
 * policy stays enabled for playback and downloads.
 */
internal fun OkHttpClient.forMediaTransport(): OkHttpClient =
    newBuilder()
        .followSslRedirects(false)
        .apply {
            interceptors().removeAll {
                it is HttpLoggingInterceptor || it is HttpsMediaRedirectInterceptor
            }
            networkInterceptors().removeAll { it is HttpLoggingInterceptor }
            interceptors().add(0, HttpsMediaRedirectInterceptor())
        }
        .build()

internal class HttpsMediaRedirectInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val secureUrl = original.url.secureGoogleVideoUrl()
        val request = if (secureUrl != null) {
            logger.w("CDN stream upgraded to HTTPS (host=${secureUrl.host})")
            original.newBuilder().url(secureUrl).build()
        } else original

        var response = chain.proceed(request)
        var redirects = 0
        while (response.isRedirect && response.request.method in setOf("GET", "HEAD")) {
            val target = response.header("Location")?.let(response.request.url::resolve)
                ?.secureGoogleVideoUrl() ?: return response

            if (redirects++ >= MAX_HTTPS_REDIRECTS) {
                response.close()
                throw ProtocolException("Too many HTTPS media redirects")
            }

            val followUp = response.request.newBuilder().url(target).apply {
                // Match the authentication boundary of a normal cross-origin redirect.
                if (target.host != response.request.url.host || target.port != response.request.url.port) {
                    removeHeader("Authorization")
                    removeHeader("Cookie")
                }
            }.build()
            response.close()
            logger.w("CDN redirect kept on HTTPS (host=${target.host})")
            response = chain.proceed(followUp)
        }
        return response
    }

    private fun HttpUrl.secureGoogleVideoUrl(): HttpUrl? =
        if (scheme == "http" && (host == "googlevideo.com" || host.endsWith(".googlevideo.com")))
            newBuilder().scheme("https").build()
        else null

    private companion object {
        const val MAX_HTTPS_REDIRECTS = 5
        val logger = Logger.withTag("MediaTransport")
    }
}
