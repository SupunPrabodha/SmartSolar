package com.smartsolar.mobile.data.remote;

import android.content.Context;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.interceptor.AuthInterceptor;
import com.smartsolar.mobile.util.SessionManager;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {
    private RetrofitClient() { }

    public static ApiService create(Context context, String baseUrl, boolean debug) {
        HttpUrl url = HttpUrl.parse(baseUrl);
        if (url == null || !url.encodedPath().endsWith("/api/v1/") ||
                !url.username().isEmpty() || !url.password().isEmpty() ||
                url.query() != null || url.fragment() != null ||
                (!url.isHttps() && !(debug && url.host().equals("10.0.2.2")))) {
            throw new IllegalArgumentException("API URL is not configured correctly");
        }
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // BASIC never logs Authorization headers, credentials or response bodies.
        logging.setLevel(debug ? HttpLoggingInterceptor.Level.BASIC : HttpLoggingInterceptor.Level.NONE);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(new AuthInterceptor(new SessionManager(context)))
                .addInterceptor(logging)
                .build();
        return new Retrofit.Builder().baseUrl(url).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService.class);
    }
}
