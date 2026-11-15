package com.know.android.model

import com.know.android.data.PreviewLibrary
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class LibraryTest {
    private val library = PreviewLibrary()

    @Test fun `timeline combines category and case insensitive search without mutating records`() {
        val matches = library.timeline("DUNE", "BOOKS")
        assertEquals(listOf("dune"), matches.map { it.id })
        assertEquals(0, library.timeline("DUNE", "MOVIES").size)
        assertEquals(6, library.articles.size)
    }

    @Test fun `timeline is newest first and IDs survive changed display titles`() {
        val articles = library.timeline("", null)
        assertEquals(articles.sortedByDescending { it.savedAt }, articles)
        val renamed = articles.first().copy(title = "Renamed")
        assertEquals(articles.first().id, renamed.id)
    }

    @Test fun `completion years are UTC and repeated completions count separately`() {
        val at = Instant.parse("2027-01-01T00:00:00.123456Z")
        val entity = library.entities.first().copy(completions = listOf(
            Completion("first", "book-dune", at.minusSeconds(1)),
            Completion("second", "book-dune", at),
            Completion("third", "book-dune", at.plusSeconds(1)),
        ))
        assertEquals(1, entity.completionCount(2026))
        assertEquals(2, entity.completionCount(2027))
        assertEquals("2027-01-01T00:00:00.123456Z", at.toString())
    }

    @Test fun `TV status list includes currently seeing`() {
        assertEquals(listOf("To be seen", "Seeing", "Seen"), EntityType.TV_SHOW.statuses)
        assertThrows(IllegalArgumentException::class.java) { library.entities.first().copy(status = "Seen") }
    }

    @Test fun `layout follows web breakpoint and avoids squeezed panes at large font scale`() {
        assertFalse(useWideLayout(760, 1f))
        assertTrue(useWideLayout(761, 1f))
        assertTrue(useWideLayout(1280, 1f))
        assertFalse(useWideLayout(800, 2f))
    }
}
