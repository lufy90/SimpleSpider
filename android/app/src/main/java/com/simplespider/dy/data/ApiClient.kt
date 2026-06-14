package com.simplespider.dy.data

import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.simplespider.dy.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    @Volatile
    private var overrideBaseUrl: String? = null

    @Volatile
    private var apiService: ApiService? = null

    @Volatile
    private var okHttp: OkHttpClient? = null

    private lateinit var appContext: Context

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private val authInterceptor = Interceptor { chain ->
        val token = AuthTokenHolder.getToken()
        val req = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(req)
    }

    private val unauthorizedInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 401) return@Interceptor response

        val path = request.url.encodedPath
        val isLoginRequest = request.method.equals("POST", ignoreCase = true) &&
            (path.endsWith("/token/") || path.endsWith("/token"))
        if (!isLoginRequest) {
            AuthTokenHolder.setToken(null)
            UnauthorizedSessionHandler.notifyUnauthorized()
        }
        response
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        synchronized(this) {
            okHttp = buildOkHttpClient()
            apiService = null
        }
        refreshImageLoader(appContext)
    }

    fun httpClient(): OkHttpClient = synchronized(this) {
        okHttp ?: buildOkHttpClient().also { okHttp = it }
    }

    @Synchronized
    fun applyBaseUrlOverride(apiBaseUrlOrNull: String?) {
        val normalized = apiBaseUrlOrNull?.ensureTrailingSlash()
        if (normalized == overrideBaseUrl) return
        overrideBaseUrl = normalized
        apiService = null
    }

    val api: ApiService
        get() = synchronized(this) {
            apiService ?: buildRetrofit().create(ApiService::class.java).also { apiService = it }
        }

    private fun buildOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(unauthorizedInterceptor)
            .addInterceptor(logging)
        if (::appContext.isInitialized) {
            TrustedSsl.applyTo(builder, appContext)
        }
        return builder.build()
    }

    private fun buildRetrofit(): Retrofit {
        val base = (overrideBaseUrl ?: BuildConfig.API_BASE_URL.ensureTrailingSlash())
            .ensureTrailingSlash()
        return Retrofit.Builder()
            .baseUrl(base)
            .client(httpClient())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"
}
