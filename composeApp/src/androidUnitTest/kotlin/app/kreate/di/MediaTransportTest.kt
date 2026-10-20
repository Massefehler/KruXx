package app.kreate.di

import android.app.Application
import java.net.InetAddress
import java.net.ProtocolException
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MediaTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun startTlsCdn() {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName(ORIGIN)
            .addSubjectAlternativeName(REDIRECT)
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory())
            start()
        }
        client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .dns { listOf(InetAddress.getByName("127.0.0.1")) }
            .callTimeout(5, TimeUnit.SECONDS)
            .build().forMediaTransport()
    }

    @After
    fun closeCdn() {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        server.close()
    }

    @Test
    fun httpCdnRedirectIsReadOverTlsWithSignedQueryAndRangeIntact() {
        val target = cdnUrl(REDIRECT).newBuilder().scheme("http")
            .encodedQuery("sig=a%2Fb%2Bc%3D&n=x%2By").build()
        server.enqueue(redirect(target))
        server.enqueue(MockResponse.Builder().code(206).body("audio bytes").build())

        val request = Request.Builder().url(cdnUrl())
            .header("Range", "bytes=131072-")
            .header("User-Agent", "stream-client")
            .header("Authorization", "test-only-authorization")
            .header("Cookie", "test-only-cookie")
            .build()
        client.newCall(request).execute().use {
            assertEquals(206, it.code)
            assertEquals("audio bytes", it.body.string())
        }

        assertEquals("https", server.takeRequest().url.scheme)
        val redirected = server.takeRequest()
        assertEquals("https", redirected.url.scheme)
        assertEquals(REDIRECT, redirected.url.host)
        assertEquals(target.encodedQuery, redirected.url.encodedQuery)
        assertEquals("bytes=131072-", redirected.headers["Range"])
        assertEquals("stream-client", redirected.headers["User-Agent"])
        assertNull(redirected.headers["Authorization"])
        assertNull(redirected.headers["Cookie"])
        assertFalse(client.followSslRedirects)
    }

    @Test
    fun anInitialHttpCdnUrlIsUpgradedBeforeConnecting() {
        server.enqueue(MockResponse.Builder().body("audio bytes").build())
        val url = cdnUrl().newBuilder().scheme("http").build()

        client.newCall(Request.Builder().url(url).build()).execute().use {
            assertEquals("audio bytes", it.body.string())
        }

        assertEquals("https", server.takeRequest().url.scheme)
    }

    @Test
    fun normalHttpsRedirectsContinueToWork() {
        server.enqueue(redirect(cdnUrl(REDIRECT)))
        server.enqueue(MockResponse.Builder().body("audio bytes").build())

        client.newCall(Request.Builder().url(cdnUrl()).build()).execute().use {
            assertEquals("audio bytes", it.body.string())
        }

        assertEquals(ORIGIN, server.takeRequest().url.host)
        assertEquals(REDIRECT, server.takeRequest().url.host)
    }

    @Test
    fun unrelatedHttpHostsAndLookalikeDomainsAreNotFollowed() {
        listOf("example.invalid", "googlevideo.com.example.invalid", "fakegooglevideo.com")
            .forEachIndexed { index, host ->
                server.enqueue(redirect(cdnUrl(host).newBuilder().scheme("http").build()))

                client.newCall(Request.Builder().url(cdnUrl()).build()).execute().use {
                    assertEquals(302, it.code)
                }

                assertEquals(index + 1, server.requestCount)
                assertEquals("https", server.takeRequest().url.scheme)
            }
    }

    @Test
    fun repeatedDowngradesStopAfterFiveSecureRedirects() {
        repeat(7) {
            server.enqueue(redirect(cdnUrl(REDIRECT).newBuilder().scheme("http").build()))
        }

        assertThrows(ProtocolException::class.java) {
            client.newCall(Request.Builder().url(cdnUrl()).build()).execute().close()
        }

        assertEquals(6, server.requestCount)
        repeat(server.requestCount) { assertEquals("https", server.takeRequest().url.scheme) }
    }

    private fun cdnUrl(host: String = ORIGIN): HttpUrl =
        server.url("/videoplayback").newBuilder().host(host).build()

    private fun redirect(target: HttpUrl) = MockResponse.Builder()
        .code(302).addHeader("Location", target.toString()).build()

    private companion object {
        const val ORIGIN = "origin.googlevideo.com"
        const val REDIRECT = "redirect.googlevideo.com"
    }
}
