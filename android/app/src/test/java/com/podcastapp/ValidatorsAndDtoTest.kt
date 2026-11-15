package com.podcastapp

import com.podcastapp.data.remote.PageDto
import com.podcastapp.data.remote.PodcastDto
import com.podcastapp.data.repository.Validators
import com.podcastapp.ui.common.formatTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsAndDtoTest {
    @Test fun emailValidation() {
        assertTrue(Validators.isValidEmail("user@example.com"))
        assertFalse(Validators.isValidEmail("user@"))
        assertFalse(Validators.isValidEmail(""))
    }

    @Test fun passwordValidation() {
        assertTrue(Validators.isValidPassword("12345678"))
        assertFalse(Validators.isValidPassword("1234567"))
    }

    @Test fun parsesServerPageAndIgnoresUnknownFields() {
        val body = """{"items":[{"id":"1","title":"T","author":"A","feedUrl":"u","extra":1}],"page":1,"size":20,"total":1}"""
        val page = Json { ignoreUnknownKeys = true }.decodeFromString<PageDto<PodcastDto>>(body)
        assertEquals(1, page.items.size)
        assertEquals("T", page.items[0].title)
        assertEquals(null, page.items[0].coverUrl)
    }

    @Test fun formatsTime() {
        assertEquals("1:05", formatTime(65_000))
        assertEquals("1:01:01", formatTime(3_661_000))
    }
}
