package com.simplespider.dy.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@UnstableApi
object VideoPlaybackCache {
    const val PREFETCH_AHEAD_COUNT = 2

    private const val CACHE_DIR_NAME = "video_playback_cache"
    private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L
    private const val MIN_BUFFER_AHEAD_MS = 4_000L
    private const val USER_AGENT = "SimpleSpider/1.0"

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null

    private val activeWriter = AtomicReference<CacheWriter?>(null)
    private val fullyCachedUrls = mutableSetOf<String>()

    fun createPlayer(context: Context): ExoPlayer {
        val appContext = context.applicationContext
        val dataSourceFactory = cacheDataSourceFactory(appContext)
        return ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(appContext)
                    .setDataSourceFactory(dataSourceFactory),
            )
            .build()
    }

    fun cancelPrefetch() {
        activeWriter.getAndSet(null)?.cancel()
    }

    suspend fun prefetchVideos(
        context: Context,
        player: ExoPlayer,
        urls: List<String>,
    ) {
        val appContext = context.applicationContext
        for (url in urls.distinct()) {
            if (!coroutineContext.isActive) return
            if (isFullyCached(appContext, url)) continue

            while (coroutineContext.isActive &&
                (!isNetworkFine(appContext) || !bufferHealthy(player))
            ) {
                delay(500L)
            }
            if (!coroutineContext.isActive) return

            try {
                prefetchUrl(appContext, url)
                synchronized(fullyCachedUrls) {
                    fullyCachedUrls.add(url)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
            }
        }
    }

    fun bufferHealthy(player: ExoPlayer): Boolean {
        return when (player.playbackState) {
            Player.STATE_BUFFERING -> false
            Player.STATE_IDLE, Player.STATE_ENDED -> true
            Player.STATE_READY -> {
                val buffered = player.bufferedPosition
                val current = player.currentPosition
                val duration = player.duration
                if (duration != C.TIME_UNSET && duration > 0L) {
                    val aheadMs = buffered - current
                    aheadMs >= MIN_BUFFER_AHEAD_MS || buffered >= duration - 500L
                } else {
                    buffered - current >= MIN_BUFFER_AHEAD_MS
                }
            }
            else -> false
        }
    }

    fun isNetworkFine(context: Context): Boolean {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager
                ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isFullyCached(context: Context, url: String): Boolean {
        synchronized(fullyCachedUrls) {
            if (fullyCachedUrls.contains(url)) return true
        }
        val cache = simpleCache(context)
        val dataSpec = DataSpec.Builder().setUri(Uri.parse(url)).build()
        val cacheKey = cacheDataSourceFactory(context).cacheKeyFactory.buildCacheKey(dataSpec)
        return cache.isCached(cacheKey, 0L, Long.MAX_VALUE)
    }

    private suspend fun prefetchUrl(context: Context, url: String) {
        withContext(Dispatchers.IO) {
            val dataSource = cacheDataSourceFactory(context).createDataSource()
            val dataSpec = DataSpec.Builder().setUri(Uri.parse(url)).build()
            val writer = CacheWriter(dataSource, dataSpec, null, null)
            activeWriter.set(writer)
            try {
                writer.cache()
            } finally {
                activeWriter.compareAndSet(writer, null)
            }
        }
    }

    private fun simpleCache(context: Context): SimpleCache {
        simpleCache?.let { return it }
        synchronized(this) {
            simpleCache?.let { return it }
            val appContext = context.applicationContext
            val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME)
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            val databaseProvider = StandaloneDatabaseProvider(appContext)
            return SimpleCache(cacheDir, evictor, databaseProvider).also { simpleCache = it }
        }
    }

    private fun cacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        cacheDataSourceFactory?.let { return it }
        synchronized(this) {
            cacheDataSourceFactory?.let { return it }
            val appContext = context.applicationContext
            val httpClient = buildVideoHttpClient(appContext)
            val upstreamFactory = OkHttpDataSource.Factory(httpClient)
                .setUserAgent(USER_AGENT)
            return CacheDataSource.Factory()
                .setCache(simpleCache(appContext))
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .also { cacheDataSourceFactory = it }
        }
    }

    private fun buildVideoHttpClient(context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
        TrustedSsl.applyTo(builder, context.applicationContext)
        return builder.build()
    }
}
