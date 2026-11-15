package com.know.android.model

import java.time.Instant
import java.time.ZoneOffset

data class Category(
    val key: String,
    val name: String,
    val color: Long,
    val enabled: Boolean = true,
    val isDefault: Boolean = true,
)

data class Article(
    val id: String,
    val title: String,
    val url: String,
    val savedAt: Instant,
    val categoryKey: String,
    val html: String?,
    val entityId: String? = null,
    val version: Long = 0,
)

enum class EntityType(val label: String, val statuses: List<String>, val completionLabel: String) {
    BOOK("Books", listOf("To read", "Reading", "Read"), "Read on"),
    MOVIE("Movies", listOf("To be seen", "Seen"), "Seen on"),
    TV_SHOW("TV shows", listOf("To be seen", "Seeing", "Seen"), "Seen on"),
    GAME("Games", listOf("To play", "Playing", "Finished"), "Finished on"),
}

data class Completion(val id: String, val entityId: String, val at: Instant)

data class Entity(
    val id: String,
    val title: String,
    val type: EntityType,
    val status: String,
    val createdAt: Instant,
    val completions: List<Completion> = emptyList(),
) {
    init { require(status in type.statuses) }
    fun completionCount(year: Int): Int = completions.count { it.at.atOffset(ZoneOffset.UTC).year == year }
}

/** Read-only boundary for the first UI checkpoint. Backend writes and authentication follow separately. */
interface LibraryRepository {
    val categories: List<Category>
    val articles: List<Article>
    val entities: List<Entity>
}

fun LibraryRepository.timeline(query: String, categoryKey: String?): List<Article> = articles
    .filter { (categoryKey == null || it.categoryKey == categoryKey) && it.title.contains(query, ignoreCase = true) }
    .sortedByDescending { it.savedAt }

fun LibraryRepository.collection(type: EntityType, query: String): List<Entity> = entities
    .filter { it.type == type && it.title.contains(query, ignoreCase = true) }

/** The web breakpoint is 760 logical pixels. Large-font layouts also need usable pane space. */
fun useWideLayout(widthDp: Int, fontScale: Float): Boolean = widthDp > 760 && widthDp / fontScale >= 760
