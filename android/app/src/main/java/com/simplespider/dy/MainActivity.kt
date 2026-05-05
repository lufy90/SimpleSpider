package com.simplespider.dy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.simplespider.dy.data.TokenStore
import com.simplespider.dy.ui.AppNav
import com.simplespider.dy.ui.theme.SimpleSpiderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val tokenStore = TokenStore(applicationContext)
        setContent {
            SimpleSpiderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav(tokenStore = tokenStore)
                }
            }
        }
    }
}
