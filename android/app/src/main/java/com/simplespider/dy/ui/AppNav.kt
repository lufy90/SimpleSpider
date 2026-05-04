package com.simplespider.dy.ui

import android.util.Base64
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.simplespider.dy.data.AuthTokenHolder
import com.simplespider.dy.data.TokenStore
import com.simplespider.dy.ui.screens.AuthorDetailScreen
import com.simplespider.dy.ui.screens.AuthorsScreen
import com.simplespider.dy.ui.screens.LoginScreen
import com.simplespider.dy.ui.screens.VideoPlayerScreen
import com.simplespider.dy.ui.screens.VideosScreen
import kotlinx.coroutines.flow.first

private const val ROUTE_LOGIN = "login"
private const val ROUTE_MAIN = "main"
private const val ROUTE_AUTHOR = "author/{authorId}"
private const val ROUTE_PLAYER = "player/{urlB64}"

@Composable
fun AppNav(
    tokenStore: TokenStore,
) {
    val navController = rememberNavController()
    var startDest by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val token = tokenStore.tokenFlow.first()
        AuthTokenHolder.setToken(token)
        startDest = if (token.isNullOrBlank()) ROUTE_LOGIN else ROUTE_MAIN
    }

    val dest = startDest
    if (dest == null) return

    NavHost(navController = navController, startDestination = dest) {
        composable(ROUTE_LOGIN) {
            LoginScreen(
                tokenStore = tokenStore,
                onLoggedIn = {
                    navController.navigate(ROUTE_MAIN) {
                        popUpTo(ROUTE_LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(ROUTE_MAIN) {
            MainTabs(
                navController = navController,
            )
        }
        composable(ROUTE_AUTHOR) { entry ->
            val id = entry.arguments?.getString("authorId")?.toIntOrNull() ?: return@composable
            AuthorDetailScreen(
                authorId = id,
                onBack = { navController.popBackStack() },
                onVideoClick = click@{ video ->
                    val url = video.playSrc ?: return@click
                    val enc = Base64.encodeToString(
                        url.toByteArray(Charsets.UTF_8),
                        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                    )
                    navController.navigate("player/$enc")
                },
            )
        }
        composable(ROUTE_PLAYER) { entry ->
            val b64 = entry.arguments?.getString("urlB64") ?: return@composable
            val url = decodeB64(b64)
            VideoPlayerScreen(
                playUrl = url,
                title = "Video",
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun decodeB64(b64: String): String {
    val bytes = Base64.decode(b64, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    return String(bytes, Charsets.UTF_8)
}

@Composable
private fun MainTabs(navController: NavHostController) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Person, null) },
                    label = { Text("Authors") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                    label = { Text("Videos") },
                )
            }
        },
    ) { padding ->
        when (tab) {
            0 -> AuthorsScreen(
                modifier = Modifier.padding(padding),
                onAuthorClick = { id -> navController.navigate("author/$id") },
            )
            1 -> VideosScreen(
                modifier = Modifier.padding(padding),
                authorId = null,
                onVideoClick = tabVideo@{ video ->
                    val url = video.playSrc ?: return@tabVideo
                    val enc = Base64.encodeToString(
                        url.toByteArray(Charsets.UTF_8),
                        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                    )
                    navController.navigate("player/$enc")
                },
            )
        }
    }
}
