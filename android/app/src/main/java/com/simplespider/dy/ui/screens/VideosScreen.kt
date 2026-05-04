package com.simplespider.dy.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.simplespider.dy.data.ApiClient
import com.simplespider.dy.data.DyVideoDto
import com.simplespider.dy.data.TokenStore
import com.simplespider.dy.ui.gestures.rememberSearchBarSwipeNestedConnection
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

@Composable
fun VideosScreen(
    modifier: Modifier = Modifier,
    tokenStore: TokenStore,
    authorId: Int? = null,
    onVideoClick: (DyVideoDto, List<DyVideoDto>, Int) -> Unit,
    onVideoCountUpdate: ((Int) -> Unit)? = null,
) {
    var search by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    val items = remember { mutableStateListOf<DyVideoDto>() }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var gridColumns by remember { mutableIntStateOf(2) }
    var showSearchBar by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val authorIdState = rememberUpdatedState(authorId)
    val searchSwipeConnection = rememberSearchBarSwipeNestedConnection(
        enabled = { authorIdState.value == null },
        searchVisible = { showSearchBar },
        onReveal = { if (authorIdState.value == null) showSearchBar = true },
        onHide = { showSearchBar = false },
    )

    LaunchedEffect(tokenStore) {
        tokenStore.videoGridColumnsFlow.collect { gridColumns = it }
    }

    suspend fun randomQueryParam(): String? =
        if (tokenStore.videoListRandomFlow.first()) "true" else null

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
                    random = randomQueryParam(),
                )
                total = res.count
                onVideoCountUpdate?.invoke(res.count)
                res.results?.let { items.addAll(it) }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(authorId, tokenStore) {
        if (authorId != null) {
            tokenStore.videoListRandomFlow.collect {
                resetAndLoad()
            }
        } else {
            merge(
                tokenStore.videoListRandomFlow.map { },
                snapshotFlow { search }
                    .drop(1)
                    .debounce(1600L)
                    .map { },
            ).collect {
                resetAndLoad()
            }
        }
    }

    LaunchedEffect(gridState, authorId, tokenStore) {
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
                        random = randomQueryParam(),
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

    val listPullNestedModifier =
        if (authorId == null) Modifier.nestedScroll(searchSwipeConnection) else Modifier

    Box(modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Column(Modifier.fillMaxSize()) {
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                items.isEmpty() && loading -> Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(listPullNestedModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                else -> LazyVerticalGrid(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(listPullNestedModifier),
                    columns = GridCells.Fixed(gridColumns.coerceIn(2, 4)),
                    state = gridState,
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { video ->
                        VideoGridItem(
                            video = video,
                            onOpen = {
                                val playable = items.filter { !it.playSrc.isNullOrBlank() }
                                val idx = playable.indexOfFirst { it.id == video.id }
                                if (idx >= 0) onVideoClick(video, playable, idx)
                            },
                        )
                    }
                }
            }
        }
        if (authorId == null) {
            AnimatedVisibility(
                visible = showSearchBar,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Search", style = MaterialTheme.typography.labelMedium) },
                        placeholder = {
                            Text(
                                "Updates ~1.6s after you pause",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoGridItem(
    video: DyVideoDto,
    onOpen: () -> Unit,
) {
    val title = video.desc?.takeIf { it.isNotBlank() } ?: video.name ?: "-"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
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
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
