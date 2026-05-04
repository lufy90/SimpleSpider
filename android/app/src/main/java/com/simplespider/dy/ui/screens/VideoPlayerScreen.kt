package com.simplespider.dy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.simplespider.dy.data.PlayerPlaylistHolder
import com.simplespider.dy.data.TokenStore
import com.simplespider.dy.data.VideoEndAction
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun VideoPlayerScreen(
    entries: List<PlayerPlaylistHolder.Entry>,
    initialIndex: Int,
    tokenStore: TokenStore,
    openedFromAuthorId: Int? = null,
    onNavigateToAuthor: (Int) -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember {
        ExoPlayer.Builder(context).build()
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    var endAction by remember { mutableStateOf(VideoEndAction.PLAY_NEXT) }
    LaunchedEffect(tokenStore) {
        tokenStore.videoEndActionFlow.collect { endAction = it }
    }

    val safeStart = initialIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeStart,
        pageCount = { entries.size },
    )

    LaunchedEffect(player, entries) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val entry = entries.getOrNull(page) ?: return@collect
                player.stop()
                player.setMediaItem(MediaItem.fromUri(entry.playUrl))
                player.prepare()
                player.playWhenReady = true
            }
    }

    DisposableEffect(player, pagerState, entries, endAction) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_ENDED) return
                scope.launch {
                    when (endAction) {
                        VideoEndAction.REPLAY_CURRENT -> {
                            player.seekTo(0)
                            player.prepare()
                            player.play()
                        }
                        VideoEndAction.PLAY_NEXT -> {
                            if (entries.size <= 1) {
                                player.seekTo(0)
                                player.prepare()
                                player.play()
                            } else {
                                val cur = pagerState.settledPage
                                val next = if (cur < entries.lastIndex) cur + 1 else 0
                                pagerState.scrollToPage(next)
                            }
                        }
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val title = entries.getOrNull(pagerState.settledPage)?.title ?: "Video"
    val settledEntry = entries.getOrNull(pagerState.settledPage)
    val density = LocalDensity.current
    val backSwipePx = with(density) { 96.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            Box(Modifier.fillMaxSize()) {
                if (page == pagerState.settledPage) {
                    val entry = entries.getOrNull(page)
                    if (entry != null) {
                        key(entry.playUrl, page) {
                            AndroidView(
                                factory = { ctx ->
                                    val exoPlayer = player
                                    PlayerView(ctx).apply {
                                        useController = true
                                        this.player = exoPlayer
                                    }
                                },
                                update = { view ->
                                    val exoPlayer = player
                                    if (view.player !== exoPlayer) {
                                        view.player = exoPlayer
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(40.dp)
                .fillMaxHeight()
                .pointerInput(onBack, backSwipePx) {
                    var rightAccum = 0f
                    var fired = false
                    detectDragGestures(
                        onDragStart = {
                            rightAccum = 0f
                            fired = false
                        },
                        onDrag = { change, dragAmount ->
                            if (fired) return@detectDragGestures
                            if (dragAmount.x > abs(dragAmount.y) * 0.65f) {
                                rightAccum += dragAmount.x
                                if (rightAccum > backSwipePx) {
                                    fired = true
                                    change.consume()
                                    onBack()
                                }
                            }
                        },
                        onDragEnd = {
                            rightAccum = 0f
                            fired = false
                        },
                        onDragCancel = {
                            rightAccum = 0f
                            fired = false
                        },
                    )
                },
        )

        val authorId = settledEntry?.authorId
        if (authorId != null) {
            val avatarOpensAuthor =
                openedFromAuthorId == null || authorId != openedFromAuthorId
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                    .then(
                        if (avatarOpensAuthor) {
                            Modifier.clickable { onNavigateToAuthor(authorId) }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val src = settledEntry.authorAvatarSrc
                if (!src.isNullOrBlank()) {
                    AsyncImage(
                        model = src,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 90.dp)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
