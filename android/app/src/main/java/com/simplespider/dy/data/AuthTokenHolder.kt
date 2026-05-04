package com.simplespider.dy.data

import java.util.concurrent.atomic.AtomicReference

object AuthTokenHolder {
    private val tokenRef = AtomicReference<String?>(null)

    fun setToken(token: String?) {
        tokenRef.set(token)
    }

    fun getToken(): String? = tokenRef.get()
}
