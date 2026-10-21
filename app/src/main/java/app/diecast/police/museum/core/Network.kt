package app.diecast.police.museum.core

import app.diecast.police.museum.BuildConfig

object Network {
    val client: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(
                okhttp3.logging.HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
                    } else {
                        okhttp3.logging.HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .build()
    }
}
