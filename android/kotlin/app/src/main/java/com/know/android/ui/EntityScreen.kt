package com.know.android.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.know.android.EntityView
import com.know.android.LibraryUiState
import com.know.android.model.*
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntityWorkspace(library: LibraryRepository, state: LibraryUiState, change: ((LibraryUiState) -> LibraryUiState) -> Unit, wide: Boolean) {
    val entities = library.collection(state.entityType, state.entityQuery)
    val selected = library.entities.find { it.id == state.entityId && it.type == state.entityType }
    Row(Modifier.fillMaxSize()) {
        if (wide || selected == null) Column(Modifier.weight(1f).padding(24.dp)) {
            Text(state.entityType.label, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EntityType.entries.forEach { type -> Choice(type.label, state.entityType == type) { change { it.copy(entityType = type, entityId = null) } } }
            }
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                EntityView.entries.forEach { view -> Choice(view.label, state.entityView == view) { change { it.copy(entityView = view) } } }
            }
            Spacer(Modifier.height(20.dp))
            if (entities.isEmpty()) Text("No ${state.entityType.label.lowercase()} match this search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val select: (String) -> Unit = { id -> change { it.copy(entityId = id) } }
            when (state.entityView) {
                EntityView.TABLE -> EntityTable(entities, state.entityId, select)
                EntityView.BOARD -> EntityBoard(entities, state.entityType, select)
                EntityView.YEAR -> EntityYears(entities, select)
            }
        }
        if (selected != null) {
            if (wide) VerticalDivider(color = MaterialTheme.colorScheme.outline)
            Box(if (wide) Modifier.width(300.dp) else Modifier.weight(1f)) {
                EntityDetail(selected, library, onBack = { change { it.copy(entityId = null) } }) { article ->
                    change { it.copy(destination = com.know.android.Destination.TIMELINE, articleId = article.id) }
                }
            }
        }
    }
}

@Composable
private fun EntityTable(entities: List<Entity>, selected: String?, onSelect: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))) {
        item {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(12.dp)) {
                Text("Title", Modifier.weight(1.6f), style = MaterialTheme.typography.bodySmall)
                Text("Status", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(entities.firstOrNull()?.type?.completionLabel ?: "Completed", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(entities, key = { it.id }) { entity ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Surface(onClick = { onSelect(entity.id) }, color = if (entity.id == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(entity.title, Modifier.weight(1.6f).padding(end = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(entity.status, Modifier.weight(1f).padding(end = 6.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Text(entity.completions.lastOrNull()?.at?.atOffset(ZoneOffset.UTC)?.toLocalDate()?.toString() ?: "—", Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EntityBoard(entities: List<Entity>, type: EntityType, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        type.statuses.forEach { status ->
            Column(Modifier.width(220.dp).background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp)).padding(12.dp)) {
                Text("$status · ${entities.count { it.status == status }}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                entities.filter { it.status == status }.forEach { entity ->
                    Surface(onClick = { onSelect(entity.id) }, shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(entity.title, style = MaterialTheme.typography.titleLarge)
                            Text("Added ${entity.createdAt.atOffset(ZoneOffset.UTC).toLocalDate()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntityYears(entities: List<Entity>, onSelect: (String) -> Unit) {
    val years = entities.flatMap { entity -> entity.completions.map { it.at.atOffset(ZoneOffset.UTC).year } }.distinct().sortedDescending()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (years.isEmpty()) item { Text("No completion history yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        years.forEach { year ->
            item { Text(year.toString(), style = MaterialTheme.typography.headlineMedium) }
            items(entities.filter { it.completionCount(year) > 0 }, key = { "$year:${it.id}" }) { entity ->
                Surface(onClick = { onSelect(entity.id) }, shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(entity.title, Modifier.weight(1f))
                        Text("${entity.completionCount(year)} completions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntityDetail(entity: Entity, library: LibraryRepository, onBack: () -> Unit, onArticle: (Article) -> Unit) {
    val date = DateTimeFormatter.ISO_INSTANT
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Choice("← ${entity.type.label}", false, onClick = onBack) }
        item {
            Text(entity.type.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(entity.title, style = MaterialTheme.typography.headlineMedium)
            Text(entity.status, color = MaterialTheme.colorScheme.primary)
            Text("Created ${date.format(entity.createdAt)}", style = MaterialTheme.typography.bodySmall)
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline); Text("Completion history", Modifier.padding(top = 16.dp), fontWeight = FontWeight.SemiBold) }
        if (entity.completions.isEmpty()) item { Text("No completions recorded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(entity.completions, key = { it.id }) { completion -> Text(date.format(completion.at), style = MaterialTheme.typography.bodySmall) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outline); Text("Linked source pages", Modifier.padding(top = 16.dp), fontWeight = FontWeight.SemiBold) }
        items(library.articles.filter { it.entityId == entity.id }, key = { it.id }) { article -> Choice(article.title, false) { onArticle(article) } }
    }
}
