package com.simplespider.dy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
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
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; error = null },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                scope.launch { doLogin(username, password, tokenStore, { loading = it }, { error = it }, onLoggedIn) }
            }),
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    doLogin(username, password, tokenStore, { loading = it }, { error = it }, onLoggedIn)
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
        onLoggedIn()
    } catch (e: Exception) {
        setError(e.message ?: "Login failed")
    } finally {
        setLoading(false)
    }
}
