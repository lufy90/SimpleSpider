package com.simplespider.dy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.simplespider.dy.data.ApiClient
import com.simplespider.dy.data.DyAuthorDto
import kotlinx.coroutines.launch

@Composable
fun AuthorsScreen(
    modifier: Modifier = Modifier,
    onAuthorClick: (Int) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    val items = remember { mutableStateListOf<DyAuthorDto>() }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun resetAndLoad() {
        scope.launch {
            loading = true
            error = null
            page = 1
            items.clear()
            try {
                val res = ApiClient.api.listAuthors(limit = 20, page = 1, search = search.ifBlank { null })
                total = res.count
                res.results?.let { items.addAll(it) }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { resetAndLoad() }

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to items.size
        }.collect { (lastVisible, size) ->
            if (size == 0 || loading) return@collect
            if (lastVisible >= size - 3 && items.size < total) {
                loading = true
                try {
                    val next = page + 1
                    val res = ApiClient.api.listAuthors(limit = 20, page = next, search = search.ifBlank { null })
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

    Column(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search authors") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                "Search",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable { resetAndLoad() },
            )
        }
        when {
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            items.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.id }) { author ->
                    AuthorRow(author, onClick = { onAuthorClick(author.id) })
                }
                if (loading && items.isNotEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
            }
        }
    }
}

@Composable
private fun AuthorRow(author: DyAuthorDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = author.avatarSrc,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(author.name ?: "-", style = MaterialTheme.typography.titleMedium)
                Text(
                    "@${author.uniqueId ?: author.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Status: ${author.status ?: "-"}  Rate: ${author.rate?.toInt() ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
