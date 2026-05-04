package com.simplespider.dy.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorDetailScreen(
    authorId: Int,
    onBack: () -> Unit,
    onVideoClick: (DyVideoDto) -> Unit,
) {
    var author by remember { mutableStateOf<DyAuthorDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authorId) {
        loading = true
        error = null
        try {
            author = ApiClient.api.getAuthor(authorId)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(author?.name ?: "Author") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when {
            loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            error != null -> Text(error!!, Modifier.padding(padding))
            author != null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Videos",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
                VideosScreen(
                    modifier = Modifier.weight(1f),
                    authorId = authorId,
                    onVideoClick = onVideoClick,
                )
            }
        }
    }
}
