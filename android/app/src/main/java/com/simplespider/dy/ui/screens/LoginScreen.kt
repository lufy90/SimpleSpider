package com.simplespider.dy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.simplespider.dy.data.ApiClient
import com.simplespider.dy.data.AuthTokenHolder
import com.simplespider.dy.data.LoginRequest
import com.simplespider.dy.data.TokenStore
import com.simplespider.dy.ui.theme.DyTextSecondary
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    tokenStore: TokenStore,
    onLoggedIn: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberLogin by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tokenStore) {
        val saved = tokenStore.readSavedLoginCredentials()
        if (saved.remember) {
            rememberLogin = true
            username = saved.username
            password = saved.password
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SimpleSpider", color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Sign in to continue", color = DyTextSecondary)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenSettings) {
            Text("Settings (API server)")
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; error = null },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                scope.launch {
                    doLogin(
                        username = username,
                        password = password,
                        rememberLogin = rememberLogin,
                        tokenStore = tokenStore,
                        setLoading = { loading = it },
                        setError = { error = it },
                        onLoggedIn = onLoggedIn,
                    )
                }
            }),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { rememberLogin = !rememberLogin },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = rememberLogin,
                onCheckedChange = null,
            )
            Text(text = "Remember me")
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    doLogin(
                        username = username,
                        password = password,
                        rememberLogin = rememberLogin,
                        tokenStore = tokenStore,
                        setLoading = { loading = it },
                        setError = { error = it },
                        onLoggedIn = onLoggedIn,
                    )
                }
            },
            enabled = !loading && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Login")
            }
        }
    }
}

private suspend fun doLogin(
    username: String,
    password: String,
    rememberLogin: Boolean,
    tokenStore: TokenStore,
    setLoading: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    onLoggedIn: () -> Unit,
) {
    setLoading(true)
    setError(null)
    try {
        val res = ApiClient.api.login(LoginRequest(username.trim(), password))
        tokenStore.saveToken(res.accessToken)
        AuthTokenHolder.setToken(res.accessToken)
        if (rememberLogin) {
            tokenStore.saveLoginCredentials(username, password)
        } else {
            tokenStore.clearSavedLoginCredentials()
        }
        onLoggedIn()
    } catch (e: Exception) {
        setError(e.message ?: "Login failed")
    } finally {
        setLoading(false)
    }
}
