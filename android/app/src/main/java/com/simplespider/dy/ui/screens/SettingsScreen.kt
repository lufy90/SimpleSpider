package com.simplespider.dy.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.simplespider.dy.data.ApiClient
import com.simplespider.dy.data.AuthTokenHolder
import com.simplespider.dy.data.TokenStore
import com.simplespider.dy.data.VideoEndAction
import com.simplespider.dy.data.VideoListQueryParams
import com.simplespider.dy.data.hostPortInputToApiBaseUrl
import com.simplespider.dy.ui.theme.DyTextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    tokenStore: TokenStore,
    onLoggedOut: () -> Unit = {},
) {
    var apiHostDraft by remember { mutableStateOf("") }
    var randomList by remember { mutableStateOf(false) }
    var endAction by remember { mutableStateOf(VideoEndAction.PLAY_NEXT) }
    var gridColumns by remember { mutableIntStateOf(2) }
    var loggedIn by remember { mutableStateOf(false) }
    var apiSavedHint by remember { mutableStateOf<String?>(null) }
    var videoQuery by remember { mutableStateOf(VideoListQueryParams()) }
    var minRateDraft by remember { mutableStateOf("") }
    var maxRateDraft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        apiHostDraft = tokenStore.apiServerFlow.first().orEmpty()
        randomList = tokenStore.videoListRandomFlow.first()
        endAction = tokenStore.videoEndActionFlow.first()
        gridColumns = tokenStore.videoGridColumnsFlow.first()
    }

    LaunchedEffect(tokenStore) {
        tokenStore.tokenFlow.collect { loggedIn = !it.isNullOrBlank() }
    }

    LaunchedEffect(tokenStore) {
        tokenStore.videoListQueryParamsFlow.collect { videoQuery = it }
    }

    LaunchedEffect(videoQuery.minRate, videoQuery.maxRate) {
        minRateDraft = videoQuery.minRate?.toString().orEmpty()
        maxRateDraft = videoQuery.maxRate?.toString().orEmpty()
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
            Text("API server", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "HTTPS by default. Host or host:port (default port 8000 if omitted). Prefix http:// for plain HTTP.",
                style = MaterialTheme.typography.bodySmall,
                color = DyTextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiHostDraft,
                onValueChange = { apiHostDraft = it; apiSavedHint = null },
                label = { Text("Server IP (optional port)") },
                placeholder = { Text("10.0.2.2:8000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        tokenStore.saveApiServerHostPort(apiHostDraft)
                        ApiClient.applyBaseUrlOverride(hostPortInputToApiBaseUrl(apiHostDraft))
                        apiSavedHint = "Saved"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply API address")
            }
            if (apiSavedHint != null) {
                Spacer(Modifier.height(4.dp))
                Text(apiSavedHint!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(28.dp))
            Text("Video list", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Random order", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Sends random=true to the video list API.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DyTextSecondary,
                    )
                }
                Switch(
                    checked = randomList,
                    onCheckedChange = { v ->
                        randomList = v
                        scope.launch { tokenStore.saveVideoListRandom(v) }
                    },
                )
            }

            Spacer(Modifier.height(28.dp))
            Text("Video list API filters", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Optional query params for GET dy/video/ only.",
                style = MaterialTheme.typography.bodySmall,
                color = DyTextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text("Exact rate", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            val rateChoices = listOf<Pair<String, Int?>>(
                "Any" to null,
                "0" to 0,
                "1" to 1,
                "2" to 2,
                "3" to 3,
                "4" to 4,
                "5" to 5,
            )
            rateChoices.forEach { (label, rateValue) ->
                val selected = when {
                    rateValue == null -> videoQuery.rate == null
                    else -> videoQuery.rate == rateValue
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            onClick = {
                                scope.launch { tokenStore.saveVideoQueryRate(rateValue) }
                            },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Min / max rate", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Inclusive range on the video rate field (0–10). Leave blank to ignore.",
                style = MaterialTheme.typography.bodySmall,
                color = DyTextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = minRateDraft,
                    onValueChange = { minRateDraft = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Min") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = maxRateDraft,
                    onValueChange = { maxRateDraft = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Max") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        var lo = minRateDraft.toIntOrNull()
                        var hi = maxRateDraft.toIntOrNull()
                        if (lo != null && hi != null && lo > hi) {
                            val t = lo
                            lo = hi
                            hi = t
                        }
                        tokenStore.saveVideoQueryMinRate(lo)
                        tokenStore.saveVideoQueryMaxRate(hi)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply rate range")
            }

            Spacer(Modifier.height(16.dp))
            Text("Liked", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            TriBoolFilterColumn(
                current = videoQuery.isLike,
                onPick = { v -> scope.launch { tokenStore.saveVideoQueryIsLike(v) } },
            )

            Spacer(Modifier.height(16.dp))
            Text("Favorited", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            TriBoolFilterColumn(
                current = videoQuery.isFavor,
                onPick = { v -> scope.launch { tokenStore.saveVideoQueryIsFavor(v) } },
            )

            Spacer(Modifier.height(16.dp))
            Text("Status", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            val statusChoices = listOf(
                "Any" to null,
                "Ready" to "ready",
                "Waiting" to "waiting",
                "Running" to "running",
                "Error" to "error",
            )
            statusChoices.forEach { (label, statusValue) ->
                val selected = when {
                    statusValue == null -> videoQuery.status == null
                    else -> videoQuery.status == statusValue
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            onClick = {
                                scope.launch { tokenStore.saveVideoQueryStatus(statusValue) }
                            },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("When a video finishes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            VideoEndAction.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = endAction == option,
                            onClick = {
                                endAction = option
                                scope.launch { tokenStore.saveVideoEndAction(option) }
                            },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = endAction == option,
                        onClick = null,
                    )
                    Text(
                        when (option) {
                            VideoEndAction.PLAY_NEXT -> "Play next"
                            VideoEndAction.REPLAY_CURRENT -> "Replay current"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Video grid", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Videos per row in the video list.",
                style = MaterialTheme.typography.bodySmall,
                color = DyTextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            listOf(2, 3, 4).forEach { n ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = gridColumns == n,
                            onClick = {
                                gridColumns = n
                                scope.launch { tokenStore.saveVideoGridColumns(n) }
                            },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = gridColumns == n,
                        onClick = null,
                    )
                    Text(
                        "$n per row",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            if (loggedIn) {
                Button(
                    onClick = {
                        scope.launch {
                            tokenStore.clearToken()
                            AuthTokenHolder.setToken(null)
                            onLoggedOut()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text("Log out")
                }
            }
        }
}

@Composable
private fun TriBoolFilterColumn(
    current: Boolean?,
    onPick: (Boolean?) -> Unit,
) {
    val choices = listOf(
        "Any" to null,
        "Yes" to true,
        "No" to false,
    )
    choices.forEach { (label, value) ->
        val selected = when {
            value == null -> current == null
            else -> current == value
        }
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = { onPick(value) },
                    role = Role.RadioButton,
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
