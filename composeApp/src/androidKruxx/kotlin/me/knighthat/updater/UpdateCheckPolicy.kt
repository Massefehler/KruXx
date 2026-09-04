package me.knighthat.updater

internal object UpdateCheckPolicy {
    private const val FIRST_RETRY_DELAY_MS = 2_000L
    private const val SECOND_RETRY_DELAY_MS = 8_000L

    fun isAutomaticCheckDue(
        nowMillis: Long,
        lastSuccessfulCheckMillis: Long,
        intervalMillis: Long
    ): Boolean {
        require(intervalMillis > 0L)

        return lastSuccessfulCheckMillis <= 0L ||
                nowMillis < lastSuccessfulCheckMillis ||
                nowMillis - lastSuccessfulCheckMillis >= intervalMillis
    }

    /**
     * Returns the delay after an automatic request failure. A null result ends the retry chain.
     */
    fun retryDelayMillis(failedAttempt: Int): Long? = when(failedAttempt) {
        1 -> FIRST_RETRY_DELAY_MS
        2 -> SECOND_RETRY_DELAY_MS
        else -> null
    }

    fun isRetryableHttpStatus(statusCode: Int): Boolean =
        statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode in 500..599
}
