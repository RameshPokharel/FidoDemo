package com.entersekt.fido2

import android.content.Context
import com.google.android.gms.common.api.Api
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit


class RestApi {

    companion object {
       public fun getApi(): API {
            val cookieManager = CookieManager()
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
            val okHttpClientBuilder = OkHttpClient().newBuilder() //create OKHTTPClient
            okHttpClientBuilder.cookieJar(JavaNetCookieJar(cookieManager));

            val okHttpClient = okHttpClientBuilder
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()

            val retrofit =
                Retrofit.Builder().client(okHttpClient)
                    .baseUrl("https://devapi.singularkey.com/").build();

            return retrofit.create(API::class.java)
        }
    }

}

