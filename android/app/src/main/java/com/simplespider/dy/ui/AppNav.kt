package com.simplespider.dy.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.simplespider.dy.data.ApiClient
import com.simplespider.dy.data.AuthTokenHolder
import com.simplespider.dy.data.UnauthorizedSessionHandler
import com.simplespider.dy.data.hostPortInputToApiBaseUrl
import com.simplespider.dy.data.PlayerPlaylistHolder
import com.simplespider.dy.data.TokenStore
import com.simplespider.dy.ui.screens.AuthorDetailScreen
import com.simplespider.dy.ui.screens.AuthorsScreen
import com.simplespider.dy.ui.screens.LoginScreen
import com.simplespider.dy.ui.screens.rememberAuthorsListHoistedState
import com.simplespider.dy.ui.screens.SettingsScreen
import com.simplespider.dy.ui.screens.VideoPlayerScreen
import com.simplespider.dy.ui.screens.VideosScreen
import kotlinx.coroutines.flow.first

private const val ROUTE_LOGIN = "login"
private const val ROUTE_MAIN = "main"
private const val ROUTE_AUTHORS_LIST = "authors_list"
private const val ROUTE_AUTHORS_DETAIL = "authors_detail/{authorId}"
private const val ROUTE_PLAYER = "player"
private const val ROUTE_AUTHOR_FROM_PLAYER = "author_from_player/{authorId}"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun AppNav(
    tokenStore: TokenStore,
) {
    val navController = rememberNavController()
    var startDest by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val savedHost = tokenStore.apiServerFlow.first()
        ApiClient.applyBaseUrlOverride(hostPortInputToApiBaseUrl(savedHost.orEmpty()))
        val token = tokenStore.tokenFlow.first()
        AuthTokenHolder.setToken(token)
        startDest = if (token.isNullOrBlank()) ROUTE_LOGIN else ROUTE_MAIN
    }

    val dest = startDest
    if (dest == null) return

    LaunchedEffect(navController, tokenStore) {
        UnauthorizedSessionHandler.events.collect {
            tokenStore.clearToken()
            AuthTokenHolder.setToken(null)
            navController.navigate(ROUTE_LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val mainVideosFeedHoist = remember { VideosFeedHoist() }

    NavHost(navController = navController, startDestination = dest) {
        composable(ROUTE_LOGIN) {
            LoginScreen(
                tokenStore = tokenStore,
                onLoggedIn = {
                    navController.navigate(ROUTE_MAIN) {
                        popUpTo(ROUTE_LOGIN) { inclusive = true }
                    }
                },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
            )
        }
        composable(ROUTE_MAIN) {
            MainTabs(
                navController = navController,
                tokenStore = tokenStore,
                mainVideosFeedHoist = mainVideosFeedHoist,
                onLoggedOut = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                modifier = Modifier.safeDrawingPadding(),
                tokenStore = tokenStore,
                onLoggedOut = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(ROUTE_PLAYER) { entry ->
            val snapshot = remember(entry) { PlayerPlaylistHolder.takeSnapshotAndClear() }
            if (snapshot == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val playlist = snapshot.entries
            val start = snapshot.startIndex
            if (playlist.isEmpty()) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            VideoPlayerScreen(
                initialEntries = playlist,
                initialIndex = start,
                tokenStore = tokenStore,
                openedFromAuthorId = snapshot.openedFromAuthorId,
                pagination = snapshot.pagination,
                onNavigateToAuthor = { authorNavId ->
                    navController.navigate("author_from_player/$authorNavId") {
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = ROUTE_AUTHOR_FROM_PLAYER,
            arguments = listOf(
                navArgument("authorId") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val authorId = backStackEntry.arguments?.getInt("authorId") ?: return@composable
            AuthorDetailScreen(
                modifier = Modifier.safeDrawingPadding(),
                authorId = authorId,
                tokenStore = tokenStore,
                onBack = { navController.popBackStack() },
                onVideoClick = { _, playlist, index, pagination ->
                    PlayerPlaylistHolder.setFromVideos(
                        playlist,
                        index,
                        openedFromAuthorContextId = authorId,
                        pagination = pagination,
                    )
                    navController.navigate(ROUTE_PLAYER)
                },
            )
        }
    }
}

@Composable
private fun MainTabs(
    navController: NavHostController,
    tokenStore: TokenStore,
    mainVideosFeedHoist: VideosFeedHoist,
    onLoggedOut: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(72.dp),
            ) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "Authors",
                            modifier = Modifier.size(26.dp),
                        )
                    },
                    label = { },
                    alwaysShowLabel = false,
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Videos",
                            modifier = Modifier.size(26.dp),
                        )
                    },
                    label = { },
                    alwaysShowLabel = false,
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(26.dp),
                        )
                    },
                    label = { },
                    alwaysShowLabel = false,
                )
            }
        },
    ) { padding ->
        when (tab) {
            0 -> AuthorsTabWithNestedNav(
                modifier = Modifier.padding(padding),
                tokenStore = tokenStore,
                rootNavController = navController,
            )
            1 -> VideosScreen(
                modifier = Modifier.padding(padding),
                tokenStore = tokenStore,
                authorId = null,
                mainTabFeed = mainVideosFeedHoist,
                onVideoClick = { _, playlist, index, pagination ->
                    PlayerPlaylistHolder.setFromVideos(playlist, index, pagination = pagination)
                    navController.navigate(ROUTE_PLAYER)
                },
            )
            2 -> SettingsScreen(
                modifier = Modifier.padding(padding),
                tokenStore = tokenStore,
                onLoggedOut = onLoggedOut,
            )
        }
    }
}

@Composable
private fun AuthorsTabWithNestedNav(
    modifier: Modifier = Modifier,
    tokenStore: TokenStore,
    rootNavController: NavHostController,
) {
    val authorsNavController = rememberNavController()
    val hoisted = rememberAuthorsListHoistedState()
    NavHost(
        navController = authorsNavController,
        startDestination = ROUTE_AUTHORS_LIST,
        modifier = modifier,
    ) {
        composable(ROUTE_AUTHORS_LIST) {
            AuthorsScreen(
                hoistedState = hoisted,
                onAuthorClick = { id ->
                    authorsNavController.navigate("authors_detail/$id")
                },
            )
        }
        composable(
            route = ROUTE_AUTHORS_DETAIL,
            arguments = listOf(
                navArgument("authorId") { type = NavType.IntType },
            ),
        ) { entry ->
            val authorId = entry.arguments?.getInt("authorId") ?: return@composable
            AuthorDetailScreen(
                authorId = authorId,
                tokenStore = tokenStore,
                onBack = { authorsNavController.popBackStack() },
                onVideoClick = { _, playlist, index, pagination ->
                    PlayerPlaylistHolder.setFromVideos(
                        playlist,
                        index,
                        openedFromAuthorContextId = authorId,
                        pagination = pagination,
                    )
                    rootNavController.navigate(ROUTE_PLAYER)
                },
            )
        }
    }
}
