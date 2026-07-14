package com.simplespider.dy.data

import android.content.Context
import coil.Coil
import coil.ImageLoader
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object TrustedSsl {
    private const val RAW_CA_NAME = "api_trust_ca"

    fun hasBundledCa(context: Context): Boolean {
        val resId = context.resources.getIdentifier(RAW_CA_NAME, "raw", context.packageName)
        return resId != 0
    }

    fun applyTo(builder: OkHttpClient.Builder, context: Context) {
        val trustManager = trustManager(context)
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
    }

    fun trustManager(context: Context): X509TrustManager {
        val managers = mutableListOf<X509TrustManager>()
        managers += systemTrustManager()
        loadBundledCaTrustManager(context)?.let { managers += it }
        return when (managers.size) {
            1 -> managers.first()
            else -> compositeTrustManager(managers)
        }
    }

    private fun systemTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private fun loadBundledCaTrustManager(context: Context): X509TrustManager? {
        val resId = context.resources.getIdentifier(RAW_CA_NAME, "raw", context.packageName)
        if (resId == 0) return null
        return context.resources.openRawResource(resId).use { input ->
            val cf = CertificateFactory.getInstance("X.509")
            val certs = cf.generateCertificates(input)
            if (certs.isEmpty()) return@use null
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                certs.forEachIndexed { index, certificate ->
                    setCertificateEntry("bundled_ca_$index", certificate)
                }
            }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        }
    }

    private fun compositeTrustManager(managers: List<X509TrustManager>): X509TrustManager {
        val array = managers.toTypedArray()
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                verifyWithAnyManager(array) { it.checkClientTrusted(chain, authType) }
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                verifyWithAnyManager(array) { it.checkServerTrusted(chain, authType) }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> =
                array.flatMap { it.acceptedIssuers.asList() }.toTypedArray()
        }
    }

    private inline fun verifyWithAnyManager(
        managers: Array<X509TrustManager>,
        block: (X509TrustManager) -> Unit,
    ) {
        var lastError: CertificateException? = null
        for (manager in managers) {
            try {
                block(manager)
                return
            } catch (e: CertificateException) {
                lastError = e
            }
        }
        throw lastError ?: CertificateException("Untrusted certificate")
    }
}

fun ApiClient.refreshImageLoader(context: Context) {
    Coil.setImageLoader(
        ImageLoader.Builder(context.applicationContext)
            .okHttpClient(httpClient())
            .build(),
    )
}
