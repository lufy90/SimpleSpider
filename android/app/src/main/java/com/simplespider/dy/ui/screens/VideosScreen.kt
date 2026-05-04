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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.simplespider.dy.data.ApiClient
import com.simplespider.dy.data.DyVideoDto
import com.simplespider.dy.data.PlayerPlaylistHolder
import com.simplespider.dy.data.TokenStore
import com.simplespider.dy.ui.VideosFeedHoist
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
    mainTabFeed: VideosFeedHoist? = null,
    onVideoClick: (DyVideoDto, List<DyVideoDto>, Int, PlayerPlaylistHolder.PlaylistPagination?) -> Unit,
    onVideoCountUpdate: ((Int) -> Unit)? = null,
) {
    val feed = mainTabFeed ?: remember { VideosFeedHoist() }
    val scope = rememberCoroutineScope()
    val authorIdState = rememberUpdatedState(authorId)
    val searchSwipeConnection = rememberSearchBarSwipeNestedConnection(
        enabled = { authorIdState.value == null },
        searchVisible = { feed.showSearchBar },
        onReveal = { if (authorIdState.value == null) feed.showSearchBar = true },
        onHide = { feed.showSearchBar = false },
    )

    LaunchedEffect(tokenStore) {
        tokenStore.videoGridColumnsFlow.collect { feed.gridColumns = it }
    }

    LaunchedEffect(tokenStore) {
        tokenStore.videoListRandomFlow.collect { feed.useRandomList = it }
    }

    suspend fun randomQueryParam(): String? =
        if (tokenStore.videoListRandomFlow.first()) "true" else null

    fun resetAndLoad() {
        scope.launch {
            feed.loading = true
            feed.error = null
            feed.page = 1
            feed.items.clear()
            try {
                val res = ApiClient.api.listVideos(
                    limit = 20,
                    page = 1,
                    search = feed.search.ifBlank { null },
                    authorId = authorId,
                    random = randomQueryParam(),
                )
                feed.total = res.count
                onVideoCountUpdate?.invoke(res.count)
                res.results?.let { feed.items.addAll(it) }
            } catch (e: Exception) {
                feed.error = e.message
            } finally {
                feed.loading = false
            }
        }
    }

    LaunchedEffect(authorId, tokenStore) {
        if (authorId != null) {
            tokenStore.videoListRandomFlow.collect {
                resetAndLoad()
            }
        } else {
            if (feed.items.isEmpty()) {
                resetAndLoad()
            }
            merge(
                tokenStore.videoListRandomFlow.drop(1).map { },
                snapshotFlow { feed.search }
                    .drop(1)
                    .debounce(1600L)
                    .map { },
            ).collect {
                resetAndLoad()
            }
        }
    }

    LaunchedEffect(feed.gridState, authorId, tokenStore) {
        snapshotFlow {
            val info = feed.gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to feed.items.size
        }.collect { (lastVisible, size) ->
            if (size == 0 || feed.loading) return@collect
            if (lastVisible >= size - 4 && feed.items.size < feed.total) {
                feed.loading = true
                try {
                    val next = feed.page + 1
                    val res = ApiClient.api.listVideos(
                        limit = 20,
                        page = next,
                        search = feed.search.ifBlank { null },
                        authorId = authorId,
                        random = randomQueryParam(),
                    )
                    val newItems = res.results.orEmpty().filter { n -> feed.items.none { it.id == n.id } }
                    if (newItems.isNotEmpty()) {
                        feed.items.addAll(newItems)
                        feed.page = next
                    }
                } catch (_: Exception) {
                } finally {
                    feed.loading = false
                }
            }
        }
    }

    val listPullNestedModifier =
        if (authorId == null) Modifier.nestedScroll(searchSwipeConnection) else Modifier

    Box(modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Column(Modifier.fillMaxSize()) {
            when {
                feed.error != null -> Text(feed.error!!, color = MaterialTheme.colorScheme.error)
                feed.items.isEmpty() && feed.loading -> Box(
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
                    columns = GridCells.Fixed(feed.gridColumns.coerceIn(2, 4)),
                    state = feed.gridState,
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(feed.items, key = { it.id }) { video ->
                        VideoGridItem(
                            video = video,
                            onOpen = {
                                val playable = feed.items.filter { !it.playSrc.isNullOrBlank() }
                                val idx = playable.indexOfFirst { it.id == video.id }
                                if (idx >= 0) {
                                    val pagination = PlayerPlaylistHolder.PlaylistPagination(
                                        limit = 20,
                                        nextPageToLoad = feed.page + 1,
                                        remoteTotal = feed.total,
                                        search = feed.search.ifBlank { null },
                                        authorId = authorId,
                                        useRandomList = feed.useRandomList,
                                    )
                                    onVideoClick(video, playable, idx, pagination)
                                }
                            },
                        )
                    }
                }
            }
        }
        if (authorId == null) {
            AnimatedVisibility(
                visible = feed.showSearchBar,
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
                        value = feed.search,
                        onValueChange = { feed.search = it },
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
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}
