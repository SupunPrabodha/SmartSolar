/*
 * SmartSolar Mobile - Prosumer & Operator Solar Energy Management Platform
 * StationNameResolver.java - In-memory cache and async resolver for charging station display names
 */

package com.smartsolar.mobile.util;

import android.content.Context;
import android.widget.TextView;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.StationResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Utility for resolving and caching human-readable station names from station identifiers,
 * ensuring cards and summaries display official station names instead of UUIDs.
 */
public final class StationNameResolver {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    public interface CallbackResolver<T> {
        void onResolved(T result);
    }

    private StationNameResolver() { }

    /**
     * Cache a known station ID to name mapping.
     */
    public static void put(String stationId, String stationName) {
        if (stationId != null && stationName != null && !stationName.trim().isEmpty()) {
            CACHE.put(stationId.trim(), stationName.trim());
        }
    }

    /**
     * Retrieve cached station name or null if not yet resolved.
     */
    public static String get(String stationId) {
        return stationId != null ? CACHE.get(stationId.trim()) : null;
    }

    /**
     * Bind station name directly to a TextView, displaying cached name immediately,
     * or a fallback identifier while asynchronously fetching and updating the view.
     */
    public static void bindStationName(TextView textView, String stationId) {
        if (textView == null) return;
        if (stationId == null || stationId.trim().isEmpty()) {
            textView.setText("—");
            textView.setTag(null);
            return;
        }

        final String cleanId = stationId.trim();
        String cached = CACHE.get(cleanId);
        if (cached != null && !cached.isEmpty()) {
            textView.setText(cached + "\n" + DisplayReference.station(cleanId));
            textView.setTag(cleanId);
            return;
        }

        // Set fallback text while resolving
        textView.setText(DisplayReference.station(cleanId));
        textView.setTag(cleanId);

        Context context = textView.getContext();
        ApiService api = RetrofitClient.create(context, BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
        api.getStation(cleanId).enqueue(new Callback<StationResponse>() {
            @Override
            public void onResponse(Call<StationResponse> call, Response<StationResponse> response) {
                StationResponse body = response.body();
                if (body != null && body.name != null && !body.name.trim().isEmpty()) {
                    String name = body.name.trim();
                    CACHE.put(cleanId, name);
                    if (cleanId.equals(textView.getTag())) {
                        textView.setText(name + "\n" + DisplayReference.station(cleanId));
                    }
                }
            }

            @Override
            public void onFailure(Call<StationResponse> call, Throwable t) {
                // Retains fallback text
            }
        });
    }

    /**
     * Fetch station name asynchronously with a callback.
     */
    public static void fetchStationName(Context context, String stationId, CallbackResolver<String> callback) {
        if (stationId == null || stationId.trim().isEmpty()) {
            if (callback != null) callback.onResolved(null);
            return;
        }

        final String cleanId = stationId.trim();
        String cached = CACHE.get(cleanId);
        if (cached != null && !cached.isEmpty()) {
            if (callback != null) callback.onResolved(cached);
            return;
        }

        ApiService api = RetrofitClient.create(context, BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
        api.getStation(cleanId).enqueue(new Callback<StationResponse>() {
            @Override
            public void onResponse(Call<StationResponse> call, Response<StationResponse> response) {
                StationResponse body = response.body();
                if (body != null && body.name != null && !body.name.trim().isEmpty()) {
                    String name = body.name.trim();
                    CACHE.put(cleanId, name);
                    if (callback != null) callback.onResolved(name);
                } else {
                    if (callback != null) callback.onResolved(DisplayReference.station(cleanId));
                }
            }

            @Override
            public void onFailure(Call<StationResponse> call, Throwable t) {
                if (callback != null) callback.onResolved(DisplayReference.station(cleanId));
            }
        });
    }
}
