package com.simplespider.dy.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.simplespider.dy.data.ApiClient
import com.simplespider.dy.data.DyAuthorDto
import com.simplespider.dy.data.RatePatchBody
import com.simplespider.dy.data.cursorFromPaginationLink
import com.simplespider.dy.data.hasMoreFromPaginationLink
import com.simplespider.dy.ui.components.RateStarsRow
import com.simplespider.dy.ui.gestures.rememberSearchBarSwipeNestedConnection
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

@Composable
fun AuthorsScreen(
    modifier: Modifier = Modifier,
    hoistedState: AuthorsListHoistedState? = null,
    onAuthorClick: (Int) -> Unit,
) {
    val internalState = rememberAuthorsListHoistedState()
    val state = hoistedState ?: internalState
    val listState = state.listState
    val scope = rememberCoroutineScope()

    val searchSwipeConnection = rememberSearchBarSwipeNestedConnection(
        enabled = { true },
        searchVisible = { state.showSearchBar },
        onReveal = { state.showSearchBar = true },
        onHide = { state.showSearchBar = false },
    )

    fun applyPaginationLinks(next: String?, previous: String?) {
        state.nextCursor = cursorFromPaginationLink(next)
        state.previousCursor = cursorFromPaginationLink(previous)
        state.hasMore = hasMoreFromPaginationLink(next)
        state.hasPrevious = hasMoreFromPaginationLink(previous)
    }

    fun resetAndLoad() {
        scope.launch {
            state.loading = true
            state.error = null
            state.nextCursor = null
            state.previousCursor = null
            state.hasMore = false
            state.hasPrevious = false
            state.items.clear()
            try {
                val res = ApiClient.api.listAuthors(
                    limit = 20,
                    cursor = null,
                    search = state.search.ifBlank { null },
                )
                applyPaginationLinks(res.next, res.previous)
                res.results?.let { state.items.addAll(it) }
            } catch (e: Exception) {
                state.error = e.message
            } finally {
                state.loading = false
            }
        }
    }

    fun loadMoreAuthors() {
        scope.launch {
            if (state.loadingMore || state.loading || !state.hasMore) return@launch
            val cursor = state.nextCursor ?: return@launch
            state.loadingMore = true
            try {
                val res = ApiClient.api.listAuthors(
                    limit = 20,
                    cursor = cursor,
                    search = state.search.ifBlank { null },
                )
                val newItems = res.results.orEmpty().filter { n -> state.items.none { it.id == n.id } }
                if (newItems.isNotEmpty()) {
                    state.items.addAll(newItems)
                }
                applyPaginationLinks(res.next, res.previous)
                if (res.results.isNullOrEmpty()) {
                    state.hasMore = false
                    state.nextCursor = null
                }
            } catch (_: Exception) {
            } finally {
                state.loadingMore = false
            }
        }
    }

    fun loadPreviousAuthors() {
        scope.launch {
            if (state.loadingPrevious || state.loading || !state.hasPrevious) return@launch
            val cursor = state.previousCursor ?: return@launch
            state.loadingPrevious = true
            try {
                val res = ApiClient.api.listAuthors(
                    limit = 20,
                    cursor = cursor,
                    search = state.search.ifBlank { null },
                )
                val newItems = res.results.orEmpty().filter { n -> state.items.none { it.id == n.id } }
                if (newItems.isNotEmpty()) {
                    val added = newItems.size
                    state.items.addAll(0, newItems)
                    val first = listState.firstVisibleItemIndex
                    val offset = listState.firstVisibleItemScrollOffset
                    listState.scrollToItem(first + added, offset)
                }
                applyPaginationLinks(res.next, res.previous)
                if (res.results.isNullOrEmpty()) {
                    state.hasPrevious = false
                    state.previousCursor = null
                }
            } catch (_: Exception) {
            } finally {
                state.loadingPrevious = false
            }
        }
    }

    LaunchedEffect(state) {
        merge(
            if (state.items.isEmpty()) flowOf(Unit).map { } else emptyFlow(),
            snapshotFlow { state.search }
                .drop(1)
                .debounce(1600L)
                .map { },
        ).collect {
            resetAndLoad()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val first = listState.firstVisibleItemIndex
            Triple(first, last, state.items.size)
        }.collect { (firstVisible, lastVisible, size) ->
            if (size == 0 || state.loading) return@collect
            if (firstVisible <= 2 && state.hasPrevious && !state.loadingPrevious) {
                loadPreviousAuthors()
            }
            if (lastVisible >= size - 3 && state.hasMore && !state.loadingMore) {
                loadMoreAuthors()
            }
        }
    }

    val listPullNestedModifier = Modifier.nestedScroll(searchSwipeConnection)

    Box(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Column(Modifier.fillMaxSize()) {
            when {
                state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error)
                state.items.isEmpty() && state.loading -> Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(listPullNestedModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                else -> LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(listPullNestedModifier),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.loadingPrevious) {
                        item(key = "loading_previous") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                            }
                        }
                    }
                    items(state.items, key = { it.id }) { author ->
                        AuthorRow(
                            author = author,
                            rateBusy = state.rateSavingId == author.id,
                            onOpen = { onAuthorClick(author.id) },
                            onRateChange = { newRate ->
                                scope.launch {
                                    state.rateSavingId = author.id
                                    state.error = null
                                    try {
                                        val updated = ApiClient.api.patchAuthor(author.id, RatePatchBody(newRate))
                                        val i = state.items.indexOfFirst { it.id == author.id }
                                        if (i >= 0) state.items[i] = updated
                                    } catch (e: Exception) {
                                        state.error = e.message
                                    } finally {
                                        state.rateSavingId = null
                                    }
                                }
                            },
                        )
                    }
                    if (state.loadingMore) {
                        item(key = "loading_more") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = state.showSearchBar,
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
                    value = state.search,
                    onValueChange = { state.search = it },
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

@Composable
private fun AuthorRow(
    author: DyAuthorDto,
    rateBusy: Boolean,
    onOpen: () -> Unit,
    onRateChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    .clip(CircleShape)
                    .clickable(onClick = onOpen),
                contentScale = ContentScale.Crop,
            )
            Column(
                Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
                    .clickable(onClick = onOpen),
            ) {
                Text(author.name ?: "-", style = MaterialTheme.typography.titleMedium)
                Text(
                    "@${author.uniqueId ?: author.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Status: ${author.status ?: "-"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RateStarsRow(
                value = author.rate?.toInt() ?: 0,
                enabled = !rateBusy,
                starSize = 22.dp,
                onValueChange = onRateChange,
            )
        }
    }
}
