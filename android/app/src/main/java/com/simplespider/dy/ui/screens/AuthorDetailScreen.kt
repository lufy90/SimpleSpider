package com.simplespider.dy.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.simplespider.dy.data.ApiClient
import com.simplespider.dy.data.DyAuthorDto
import com.simplespider.dy.data.DyVideoDto
import com.simplespider.dy.data.RatePatchBody
import com.simplespider.dy.data.TokenStore
import com.simplespider.dy.ui.components.RateStarsRow
import kotlinx.coroutines.launch

@Composable
fun AuthorDetailScreen(
    authorId: Int,
    tokenStore: TokenStore,
    onBack: () -> Unit,
    onVideoClick: (DyVideoDto, List<DyVideoDto>, Int) -> Unit,
) {
    var author by remember { mutableStateOf<DyAuthorDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var authorRateBusy by remember { mutableStateOf(false) }
    var videoTotal by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(authorId) {
        loading = true
        error = null
        videoTotal = 0
        try {
            author = ApiClient.api.getAuthor(authorId)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    BackHandler { onBack() }

    when {
        loading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        author != null -> Column(
            Modifier.fillMaxSize(),
        ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = author!!.avatarSrc,
                        contentDescription = null,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(author!!.name ?: "-", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "@${author!!.uniqueId ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Status: ${author!!.status ?: "-"}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        RateStarsRow(
                            value = author!!.rate?.toInt() ?: 0,
                            enabled = !authorRateBusy,
                            modifier = Modifier.widthIn(max = 280.dp),
                            onValueChange = { newRate ->
                                scope.launch {
                                    authorRateBusy = true
                                    error = null
                                    try {
                                        author = ApiClient.api.patchAuthor(authorId, RatePatchBody(newRate))
                                    } catch (e: Exception) {
                                        error = e.message
                                    } finally {
                                        authorRateBusy = false
                                    }
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (error != null) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    "Videos ($videoTotal)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
                VideosScreen(
                    modifier = Modifier.weight(1f),
                    tokenStore = tokenStore,
                    authorId = authorId,
                    onVideoClick = onVideoClick,
                    onVideoCountUpdate = { videoTotal = it },
                )
            }

        else -> Text(
            text = error ?: "Unknown error",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error,
        )
    }
}
