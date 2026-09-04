@file:androidx.media3.common.util.UnstableApi

package app.kreate.android.service.player

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ErrorHandlingPolicyTest {

    private val policy = ErrorHandlingPolicy()

    @Test
    fun rejectedSignedUrlsBypassMedia3RetryDelay() {
        listOf(403, 410, 416).forEach { responseCode ->
            assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(loadError(responseCode)))
        }
    }

    @Test
    fun unrelatedHttpErrorsKeepMedia3DefaultRetryPolicy() {
        assertNotEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(loadError(500)))
    }

    @Test
    fun responseCodeLookupTraversesWrappedIoExceptions() {
        val rejected = invalidResponse(403)

        assertEquals(403, IOException("wrapper", rejected).httpResponseCodeOrNull())
        assertEquals(null, IOException("plain").httpResponseCodeOrNull())
    }

    private fun loadError( responseCode: Int ): LoadErrorInfo {
        val dataSpec = dataSpec()
        return LoadErrorInfo(
            LoadEventInfo(LoadEventInfo.getNewId(), dataSpec, 0L),
            MediaLoadData(C.DATA_TYPE_MEDIA),
            invalidResponse(responseCode, dataSpec),
            1,
        )
    }

    private fun invalidResponse(
        responseCode: Int,
        dataSpec: DataSpec = dataSpec(),
    ) = HttpDataSource.InvalidResponseCodeException(
        responseCode,
        "response",
        null,
        emptyMap(),
        dataSpec,
        byteArrayOf(),
    )

    private fun dataSpec() = DataSpec(Uri.parse("https://example.invalid/audio"))
}
