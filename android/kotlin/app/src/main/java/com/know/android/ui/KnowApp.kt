package com.know.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.know.android.*
import com.know.android.data.PreviewLibrary
import com.know.android.model.*
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val SmallShape = RoundedCornerShape(7.dp)
private val DayFormat = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneOffset.UTC)
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)

@Composable
fun KnowApp(library: LibraryRepository, state: LibraryUiState, change: ((LibraryUiState) -> LibraryUiState) -> Unit) {
    BackHandler(state.articleId != null || state.entityId != null) {
        change { if (it.destination == Destination.TIMELINE) it.copy(articleId = null) else it.copy(entityId = null) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = useWideLayout(maxWidth.value.toInt(), LocalDensity.current.fontScale)
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Header(state, change, wide)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            if (!wide) {
                Navigation(state.destination, false) { destination -> change { it.copy(destination = destination) } }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
            Row(Modifier.weight(1f)) {
                if (wide) {
                    Navigation(state.destination, true) { destination -> change { it.copy(destination = destination) } }
                    VerticalDivider(color = MaterialTheme.colorScheme.outline)
                }
                Box(Modifier.weight(1f)) {
                    when (state.destination) {
                        Destination.TIMELINE -> TimelineWorkspace(library, state, change, wide)
                        Destination.ENTITIES -> EntityWorkspace(library, state, change, wide)
                        Destination.CATEGORIES -> CategoryScreen(library) { key -> change { it.copy(destination = Destination.TIMELINE, category = key, articleId = null) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: LibraryUiState, change: ((LibraryUiState) -> LibraryUiState) -> Unit, wide: Boolean) {
    Column(Modifier.padding(horizontal = if (wide) 26.dp else 17.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(painterResource(R.drawable.know_logo), contentDescription = null, Modifier.size(29.dp, 33.dp))
                    Text("Know", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                }
                Text("v0.13.0 · native preview", Modifier.padding(start = 37.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            Row {
                listOf(ThemePreference.SYSTEM to "◫", ThemePreference.LIGHT to "☀", ThemePreference.DARK to "☾").forEach { (preference, label) ->
                    Surface(
                        onClick = { change { it.copy(theme = preference) } },
                        shape = SmallShape,
                        color = if (state.theme == preference) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(label, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { contentDescription = "${preference.name.lowercase()} theme" })
                        }
                    }
                }
            }
        }
        if (state.destination != Destination.CATEGORIES) {
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = if (state.destination == Destination.TIMELINE) state.timelineQuery else state.entityQuery,
                onValueChange = { query -> change { if (it.destination == Destination.TIMELINE) it.copy(timelineQuery = query) else it.copy(entityQuery = query) } },
                placeholder = { Text(if (state.destination == Destination.TIMELINE) "Search saved entries…" else "Search ${state.entityType.label.lowercase()}…", style = MaterialTheme.typography.bodySmall) },
                singleLine = true, shape = SmallShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline, unfocusedContainerColor = MaterialTheme.colorScheme.background),
            )
        }
    }
}

@Composable
private fun Navigation(selected: Destination, vertical: Boolean, select: (Destination) -> Unit) {
    val content: @Composable (Destination, Modifier) -> Unit = { destination, modifier ->
        Surface(
            onClick = { select(destination) }, shape = SmallShape,
            color = if (selected == destination) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            modifier = modifier.heightIn(min = 48.dp),
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(when (destination) { Destination.TIMELINE -> "♧"; Destination.ENTITIES -> "☷"; Destination.CATEGORIES -> "▦" }, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(destination.label, fontSize = 12.sp, color = if (selected == destination) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (vertical) Column(Modifier.width(145.dp).fillMaxHeight().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Destination.entries.forEach { content(it, Modifier.fillMaxWidth()) }
    } else Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Destination.entries.forEach { content(it, Modifier.weight(1f)) }
    }
}

@Composable
private fun TimelineWorkspace(library: LibraryRepository, state: LibraryUiState, change: ((LibraryUiState) -> LibraryUiState) -> Unit, wide: Boolean) {
    val article = library.articles.find { it.id == state.articleId }
    Row(Modifier.fillMaxSize()) {
        if (wide || article == null) Box(Modifier.weight(1f)) {
            TimelineScreen(library, state, change)
        }
        if (wide) VerticalDivider(color = MaterialTheme.colorScheme.outline)
        if (wide || article != null) Box(Modifier.weight(1f)) {
            if (article == null) Text("Select an article to read", Modifier.padding(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else ReaderScreen(article, library, onBack = { change { it.copy(articleId = null) } }, onEntity = { id ->
                val entity = library.entities.find { it.id == id }
                if (entity != null) change { it.copy(destination = Destination.ENTITIES, entityId = id, entityType = entity.type) }
            })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineScreen(library: LibraryRepository, state: LibraryUiState, change: ((LibraryUiState) -> LibraryUiState) -> Unit) {
    val articles = library.timeline(state.timelineQuery, state.category)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 22.dp)) {
        item {
            Text("Saved entries", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text("${articles.size} entries · Newest first", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(22.dp))
            Text("Filter by category", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Choice("All categories", state.category == null) { change { it.copy(category = null) } }
                library.categories.filter { it.enabled }.forEach { category ->
                    Choice(category.name, state.category == category.key, Color(category.color)) { change { it.copy(category = category.key) } }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        if (articles.isEmpty()) item { Text("No saved entries match these filters.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(articles, key = { it.id }) { article ->
            val category = library.categories.first { it.key == article.categoryKey }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column(Modifier.width(69.dp).padding(top = 15.dp), horizontalAlignment = Alignment.End) {
                    Text(DayFormat.format(article.savedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(TimeFormat.format(article.savedAt), fontSize = 17.sp, fontFamily = FontFamily.Serif)
                }
                TimelineNode(Color(category.color), Modifier.width(30.dp).fillMaxHeight())
                Surface(
                    onClick = { change { it.copy(articleId = article.id) } },
                    shape = RoundedCornerShape(10.dp),
                    color = if (state.articleId == article.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (state.articleId == article.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.weight(1f).padding(bottom = 13.dp).heightIn(min = 103.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(Color(category.color))
                            Text(category.name, Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelSmall, color = Color(category.color))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(library.entities.find { it.id == article.entityId }?.title ?: article.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineNode(color: Color, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Canvas(modifier) {
        val x = size.width / 2
        val path = Path().apply { moveTo(x - 2.dp.toPx(), 0f); cubicTo(x + 8.dp.toPx(), size.height * .3f, x - 8.dp.toPx(), size.height * .7f, x + 2.dp.toPx(), size.height) }
        drawPath(path, line, style = Stroke(1.6.dp.toPx()))
        drawCircle(color, 6.dp.toPx(), androidx.compose.ui.geometry.Offset(x, 31.dp.toPx()))
    }
}

@Composable
internal fun Choice(label: String, selected: Boolean, color: Color? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = SmallShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (color != null) { Dot(color); Spacer(Modifier.width(6.dp)) }
            Text(label, style = MaterialTheme.typography.bodySmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun Dot(color: Color) { Canvas(Modifier.size(8.dp)) { drawCircle(color) } }

@Composable
private fun ReaderScreen(article: Article, library: LibraryRepository, onBack: () -> Unit, onEntity: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(23.dp)) {
        Choice("← Timeline", false, onClick = onBack)
        Spacer(Modifier.height(25.dp))
        Text("SAVED ARTICLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(article.title, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.headlineLarge)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text("Category: ${library.categories.first { it.key == article.categoryKey }.name}", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodySmall)
        Text("Source: ${article.url}", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        Text("Saved: ${article.savedAt}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        if (article.html == null) Text("Only the link was saved.", Modifier.padding(vertical = 24.dp))
        else SavedArticleBody(article.html, Modifier.fillMaxWidth().height(440.dp))
        article.entityId?.let { id ->
            val entity = library.entities.find { it.id == id }
            if (entity != null) {
                Text("Linked entities", fontWeight = FontWeight.SemiBold)
                Choice(entity.title, false) { onEntity(id) }
            }
        }
    }
}

@Composable
private fun CategoryScreen(library: LibraryRepository, onCategory: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Categories", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Text("Your categories and defaults", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(library.categories, key = { it.key }) { category ->
            Surface(shape = SmallShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), onClick = { onCategory(category.key) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(Color(category.color))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(category.name, fontWeight = FontWeight.Medium)
                        Text(if (category.isDefault) "Predefined · Enabled" else "Custom · Enabled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${library.articles.count { it.categoryKey == category.key }} articles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Preview(name = "Phone - light", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PhonePreview() = StatefulPreview()

@Preview(name = "Tablet - light", widthDp = 1280, heightDp = 900, showBackground = true)
@Composable
private fun TabletPreview() = StatefulPreview()

@Preview(name = "Phone - dark", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun DarkPreview() = StatefulPreview(ThemePreference.DARK)

@Composable
private fun StatefulPreview(theme: ThemePreference = ThemePreference.LIGHT) {
    var state by remember { mutableStateOf(LibraryUiState(theme = theme)) }
    KnowTheme(state.theme) { KnowApp(remember { PreviewLibrary() }, state) { state = it(state) } }
}
