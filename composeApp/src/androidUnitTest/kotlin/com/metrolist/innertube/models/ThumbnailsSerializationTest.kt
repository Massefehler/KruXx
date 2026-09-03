package com.metrolist.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailsSerializationTest {

    @OptIn( ExperimentalSerializationApi::class )
    @Test
    fun musicThumbnailWithoutThumbnailsIsTreatedAsMissingArtwork() {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        val renderer = json.decodeFromString<ThumbnailRenderer.MusicThumbnailRenderer>(
            """{"thumbnail":{}}"""
        )

        assertNull( renderer.getThumbnailUrl() )
    }
}
