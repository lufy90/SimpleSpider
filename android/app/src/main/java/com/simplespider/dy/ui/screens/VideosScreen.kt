package com.simplespider.dy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.simplespider.dy.data.ApiClient
import com.simplespider.dy.data.DyVideoDto
import kotlinx.coroutines.launch

@Composable
fun VideosScreen(
    modifier: Modifier = Modifier,
    authorId: Int? = null,
    onVideoClick: (DyVideoDto) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    val items = remember { mutableStateListOf<DyVideoDto>() }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    fun resetAndLoad() {
        scope.launch {
            loading = true
            error = null
            page = 1
            items.clear()
            try {
                val res = ApiClient.api.listVideos(
                    limit = 20,
                    page = 1,
                    search = search.ifBlank { null },
                    authorId = authorId,
                )
                total = res.count
                res.results?.let { items.addAll(it) }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(authorId) { resetAndLoad() }

    LaunchedEffect(gridState, authorId) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to items.size
        }.collect { (lastVisible, size) ->
            if (size == 0 || loading) return@collect
            if (lastVisible >= size - 4 && items.size < total) {
                loading = true
                try {
                    val next = page + 1
                    val res = ApiClient.api.listVideos(
                        limit = 20,
                        page = next,
                        search = search.ifBlank { null },
                        authorId = authorId,
                    )
                    val newItems = res.results.orEmpty().filter { n -> items.none { it.id == n.id } }
                    if (newItems.isNotEmpty()) {
                        items.addAll(newItems)
                        page = next
                    }
                } catch (_: Exception) {
                } finally {
                    loading = false
                }
            }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        if (authorId == null) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search videos") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Text(
                "Search",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(8.dp)
                    .clickable { resetAndLoad() },
            )
        }
        when {
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            items.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { video ->
                    VideoGridItem(video) { onVideoClick(video) }
                }
            }
        }
    }
}

@Composable
private fun VideoGridItem(video: DyVideoDto, onClick: () -> Unit) {
    val title = video.desc?.takeIf { it.isNotBlank() } ?: video.name ?: "-"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            AsyncImage(
                model = video.coverSrc,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f),
                contentScale = ContentScale.Crop,
            )
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
