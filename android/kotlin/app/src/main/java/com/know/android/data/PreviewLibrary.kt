package com.know.android.data

import com.know.android.model.*
import java.time.Instant

/** Fictional, immutable fixtures for the preview flavor. Never connected to a real account. */
class PreviewLibrary : LibraryRepository {
    private val saved = Instant.parse("2026-11-08T09:12:00.123456Z")
    override val categories = listOf(
        Category("AI", "AI", 0xFF8654B0),
        Category("OTHER", "Other", 0xFF66738D),
        Category("BOOKS", "Books", 0xFF9864BD),
        Category("MOVIES", "Movies", 0xFFD08839),
        Category("TV_SHOWS", "TV shows", 0xFF168E91),
        Category("GAMES", "Games", 0xFFCF576B),
        Category("TECHNOLOGY", "Technology", 0xFF2876DC),
    )
    override val entities = listOf(
        Entity("book-dune", "Dune", EntityType.BOOK, "Read", saved, listOf(
            Completion("dune-first", "book-dune", saved.minusSeconds(86400)),
            Completion("dune-second", "book-dune", saved),
        )),
        Entity("book-left-hand", "The Left Hand of Darkness", EntityType.BOOK, "Reading", saved),
        Entity("book-dispossessed", "The Dispossessed", EntityType.BOOK, "To read", saved),
        Entity("movie-arrival", "Arrival", EntityType.MOVIE, "Seen", saved),
        Entity("tv-severance", "Severance", EntityType.TV_SHOW, "Seeing", saved),
        Entity("game-hades", "Hades", EntityType.GAME, "Playing", saved),
    )
    override val articles = listOf(
        article("postgres", "PostgreSQL indexes, explained", "TECHNOLOGY", null, 0),
        article("dune", "Dune: a world worth returning to", "BOOKS", "book-dune", 1),
        article("arrival", "The art of Arrival", "MOVIES", "movie-arrival", 2),
        article("severance", "Severance and the divided self", "TV_SHOWS", "tv-severance", 3),
        article("hades", "Exploring the world of Hades", "GAMES", "game-hades", 4),
        article("ideas", "A quieter way to collect ideas", "OTHER", null, 5),
    )
    private fun article(id: String, title: String, category: String, entity: String?, order: Long) = Article(
        id, title, "https://example.com/library/$id", saved.minusSeconds(order * 3600), category,
        "<h2>$title</h2><p>This fictional article is part of the native Know preview.</p>" +
            "<p>Collect the things you want to return to. Saved pages and their linked entities stay together, using the same design as your web library.</p>" +
            "<h3>A place for what matters</h3><p>The production reader will display the server-sanitized article content with the web application's resource restrictions.</p>",
        entity,
    )
}
