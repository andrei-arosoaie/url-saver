package com.know.android

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.know.android.data.PreviewLibrary
import com.know.android.model.EntityType
import com.know.android.ui.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Destination(val label: String) { TIMELINE("Timeline"), ENTITIES("Entities"), CATEGORIES("Categories") }
enum class EntityView(val label: String) { TABLE("Table"), BOARD("Board"), YEAR("By year") }

data class LibraryUiState(
    val destination: Destination = Destination.TIMELINE,
    val timelineQuery: String = "",
    val entityQuery: String = "",
    val category: String? = null,
    val articleId: String? = null,
    val entityId: String? = null,
    val entityType: EntityType = EntityType.BOOK,
    val entityView: EntityView = EntityView.TABLE,
    val theme: ThemePreference = ThemePreference.SYSTEM,
)

class LibraryViewModel(private val saved: SavedStateHandle) : ViewModel() {
    val library = PreviewLibrary()
    private val mutable = MutableStateFlow(LibraryUiState(
        destination = enumValueOf(saved["destination"] ?: "TIMELINE"),
        timelineQuery = saved["timelineQuery"] ?: "",
        entityQuery = saved["entityQuery"] ?: "",
        category = saved["category"], articleId = saved["articleId"], entityId = saved["entityId"],
        entityType = enumValueOf(saved["entityType"] ?: "BOOK"),
        entityView = enumValueOf(saved["entityView"] ?: "TABLE"),
        theme = enumValueOf(saved["theme"] ?: "SYSTEM"),
    ))
    val state = mutable.asStateFlow()

    fun change(transform: (LibraryUiState) -> LibraryUiState) {
        mutable.update(transform)
        val s = mutable.value
        saved["destination"] = s.destination.name
        saved["timelineQuery"] = s.timelineQuery
        saved["entityQuery"] = s.entityQuery
        saved["category"] = s.category
        saved["articleId"] = s.articleId
        saved["entityId"] = s.entityId
        saved["entityType"] = s.entityType.name
        saved["entityView"] = s.entityView.name
        saved["theme"] = s.theme.name
    }
}
