package com.simplespider.dy.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.simplespider.dy.data.ApiClient
import com.simplespider.dy.data.PlayerPlaylistHolder
import com.simplespider.dy.data.RatePatchBody
import com.simplespider.dy.data.TokenStore
import com.simplespider.dy.ui.components.RateStarsRow
import com.simplespider.dy.data.VideoEndAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.max

@Composable
fun VideoPlayerScreen(
    initialEntries: List<PlayerPlaylistHolder.Entry>,
    initialIndex: Int,
    tokenStore: TokenStore,
    openedFromAuthorId: Int? = null,
    pagination: PlayerPlaylistHolder.PlaylistPagination? = null,
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

    val entryList = remember {
        mutableStateListOf<PlayerPlaylistHolder.Entry>().apply { addAll(initialEntries) }
    }

    val safeStart = initialIndex.coerceIn(0, (entryList.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeStart,
        pageCount = { max(1, entryList.size) },
    )

    LaunchedEffect(player, pagination) {
        val seed = pagination ?: return@LaunchedEffect
        val mutex = Mutex()
        var cursor = seed
        var exhausted = false
        snapshotFlow { pagerState.settledPage to entryList.size }
            .distinctUntilChanged()
            .collect { (page, size) ->
                if (exhausted || size == 0) return@collect
                val nearEnd = page >= max(0, size - 2)
                if (!nearEnd) return@collect
                mutex.withLock {
                    if (exhausted) return@withLock
                    val cur = cursor
                    val remoteTotal = cur.remoteTotal
                    if (remoteTotal > 0) {
                        val maxPage = max(1, (remoteTotal + cur.limit - 1) / cur.limit)
                        if (cur.nextPageToLoad > maxPage) {
                            exhausted = true
                            return@withLock
                        }
                    }
                    try {
                        val random = if (cur.useRandomList) "true" else null
                        val q = cur.query
                        val res = ApiClient.api.listVideos(
                            limit = cur.limit,
                            page = cur.nextPageToLoad,
                            search = cur.search,
                            authorId = cur.authorId,
                            random = random,
                            rate = q.rate,
                            minRate = q.minRate,
                            maxRate = q.maxRate,
                            isLike = q.isLike,
                            isFavor = q.isFavor,
                            status = q.status,
                        )
                        val raw = res.results.orEmpty()
                        val newTotal = if (res.count > 0) res.count else cur.remoteTotal
                        if (raw.isEmpty()) {
                            exhausted = true
                            cursor = cur.copy(remoteTotal = newTotal)
                            return@withLock
                        }
                        val existing = entryList.map { it.id }.toSet()
                        val appended = raw
                            .filter { !it.playSrc.isNullOrBlank() && it.id !in existing }
                            .map { PlayerPlaylistHolder.entryFromVideo(it) }
                        entryList.addAll(appended)
                        cursor = cur.copy(
                            nextPageToLoad = cur.nextPageToLoad + 1,
                            remoteTotal = newTotal,
                        )
                        if (raw.size < cur.limit && appended.isEmpty()) {
                            exhausted = true
                        }
                    } catch (_: Exception) {
                    }
                }
            }
    }

    LaunchedEffect(player, pagerState, entryList) {
        snapshotFlow {
            val page = pagerState.settledPage
            val entry = entryList.getOrNull(page)
            Triple(page, entry?.id, entry?.playUrl)
        }
            .distinctUntilChanged()
            .collect { (_, id, playUrl) ->
                if (id == null || playUrl.isNullOrBlank()) return@collect
                player.setMediaItem(MediaItem.fromUri(playUrl), /* resetPosition= */ true)
                player.prepare()
                player.playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition
            val d = player.duration
            durationMs = if (d == C.TIME_UNSET || d <= 0L) 0L else d
            delay(250L)
        }
    }

    DisposableEffect(player, pagerState, entryList.size, endAction) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playingNow: Boolean) {
                isPlaying = playingNow
            }

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
                            if (entryList.size <= 1) {
                                player.seekTo(0)
                                player.prepare()
                                player.play()
                            } else {
                                val cur = pagerState.settledPage
                                val next = if (cur < entryList.lastIndex) cur + 1 else 0
                                pagerState.scrollToPage(next)
                            }
                        }
                    }
                }
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        onDispose { player.removeListener(listener) }
    }

    val currentPlayer = rememberUpdatedState(player)

    val settledPage = pagerState.settledPage
    val title = entryList.getOrNull(settledPage)?.title ?: "Video"
    val settledEntry = entryList.getOrNull(settledPage)
    var rateMenuOpen by remember { mutableStateOf(false) }
    var videoRateStars by remember { mutableIntStateOf(0) }
    var rateBusy by remember { mutableStateOf(false) }

    LaunchedEffect(settledPage) {
        rateMenuOpen = false
    }

    LaunchedEffect(settledEntry?.id, settledEntry?.rate) {
        videoRateStars = settledEntry?.rate?.toInt()?.coerceIn(0, 5) ?: 0
    }

    val density = LocalDensity.current
    val backSwipePx = with(density) { 96.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                }
            },
            update = { view ->
                if (view.player !== player) {
                    view.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            val isSettledPage = page == settledPage
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (isSettledPage) {
                            Modifier.pointerInput(currentPlayer) {
                                detectTapGestures(
                                    onTap = {
                                        val p = currentPlayer.value
                                        if (p.isPlaying) {
                                            p.pause()
                                        } else {
                                            p.play()
                                        }
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSettledPage && !isPlaying) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (durationMs > 0L) {
                                "${formatMediaClock(positionMs)} / ${formatMediaClock(durationMs)}"
                            } else {
                                "${formatMediaClock(positionMs)} / --:--"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                        )
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

        if (settledEntry != null) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                val authorId = settledEntry.authorId
                if (authorId != null) {
                    val avatarOpensAuthor =
                        openedFromAuthorId == null || authorId != openedFromAuthorId
                    Box(
                        Modifier
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
                    Spacer(Modifier.height(10.dp))
                }
                Row(
                    modifier = Modifier.wrapContentWidth(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimatedVisibility(
                        visible = rateMenuOpen,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            tonalElevation = 3.dp,
                        ) {
                            Column(
                                Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                RateStarsRow(
                                    value = videoRateStars,
                                    enabled = !rateBusy,
                                    starSize = 22.dp,
                                    modifier = Modifier.widthIn(max = 200.dp),
                                    onValueChange = { newRate ->
                                        val entry = entryList.getOrNull(pagerState.settledPage) ?: return@RateStarsRow
                                        val vid = entry.id
                                        scope.launch {
                                            rateBusy = true
                                            try {
                                                val dto = ApiClient.api.patchVideo(vid, RatePatchBody(newRate))
                                                val r = dto.rate?.toInt()?.coerceIn(0, 5) ?: newRate
                                                videoRateStars = r
                                                val idx = pagerState.settledPage
                                                val e = entryList.getOrNull(idx) ?: return@launch
                                                entryList[idx] = e.copy(rate = r.toFloat())
                                                rateMenuOpen = false
                                            } catch (_: Exception) {
                                            } finally {
                                                rateBusy = false
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { rateMenuOpen = !rateMenuOpen },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .padding(bottom = 90.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatMediaClock(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}
