package com.simplespider.dy.data

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object SlideShowPlayback {
    const val IMAGE_SLIDE_MS = 3000L

    suspend fun run(
        player: ExoPlayer,
        playSrc: PlaySrcDto,
        onSlideChanged: (PlaySrcSlideDto?) -> Unit,
        onFinished: () -> Unit,
    ) {
        val slides = playSrc.slides.orEmpty().sortedBy { it.index }
        val musicUrl = playSrc.music?.trim()?.takeIf { it.isNotEmpty() }

        if (slides.isEmpty()) {
            onSlideChanged(null)
            if (musicUrl != null) {
                player.setMediaItem(MediaItem.fromUri(musicUrl), true)
                player.prepare()
                player.playWhenReady = true
                awaitPlaybackEnded(player)
            }
            onFinished()
            return
        }

        for ((idx, slide) in slides.withIndex()) {
            when (slide.kind) {
                "image" -> {
                    onSlideChanged(slide)
                    if (musicUrl != null) {
                        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
                        if (idx == 0 || currentUri != musicUrl) {
                            player.setMediaItem(MediaItem.fromUri(musicUrl), true)
                            player.prepare()
                        }
                        player.playWhenReady = true
                    }
                    delay(IMAGE_SLIDE_MS)
                }
                "clip" -> {
                    onSlideChanged(slide)
                    player.setMediaItem(MediaItem.fromUri(slide.url), true)
                    player.prepare()
                    player.playWhenReady = true
                    awaitPlaybackEnded(player)
                }
                else -> onSlideChanged(slide)
            }
        }
        onSlideChanged(null)
        onFinished()
    }

    private suspend fun awaitPlaybackEnded(player: ExoPlayer) {
        if (player.playbackState == Player.STATE_ENDED) return
        suspendCoroutine { cont ->
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        player.removeListener(this)
                        cont.resume(Unit)
                    }
                }
            }
            player.addListener(listener)
        }
    }
}
