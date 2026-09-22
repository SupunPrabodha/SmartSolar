package com.smartsolar.mobile.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.smartsolar.mobile.data.local.AppDatabaseHelper;
import com.smartsolar.mobile.data.remote.dto.UserResponse;

/** App-private session state. Call from a worker thread because cache operations use SQLite. */
public final class SessionManager implements SessionStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences preferences;
    private final Context context;

    public SessionManager(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences("smart_solar_session", Context.MODE_PRIVATE);
    }

    public void saveSession(String token, String expiresAtUtc, UserResponse user) {
        long expiry = SessionExpiry.parse(expiresAtUtc);
        if (!SessionExpiry.isValid(token, expiry, System.currentTimeMillis()) || user == null) {
            throw new IllegalArgumentException("Invalid session response");
        }
        synchronized (LOCK) {
            try (AppDatabaseHelper database = new AppDatabaseHelper(context)) { database.cacheUser(user); }
            preferences.edit().putString("access_token", token).putLong("expires_at", expiry).apply();
        }
    }

    @Override
    public String getAccessToken() {
        synchronized (LOCK) {
            String token = preferences.getString("access_token", null);
            if (!SessionExpiry.isValid(token, preferences.getLong("expires_at", 0), System.currentTimeMillis())) {
                clear();
                return null;
            }
            return token;
        }
    }

    public long getExpiresAtMillis() {
        return preferences.getLong("expires_at", 0);
    }

    public void cacheProfile(UserResponse user) {
        synchronized (LOCK) {
            if (getAccessToken() == null) throw new IllegalStateException("Session expired");
            try (AppDatabaseHelper database = new AppDatabaseHelper(context)) { database.cacheUser(user); }
        }
    }

    @Override
    public void clearIfMatches(String token) {
        synchronized (LOCK) {
            // A delayed 401 from an older request must not erase a newer login.
            if (token != null && token.equals(preferences.getString("access_token", null))) clear();
        }
    }

    public void clear() {
        synchronized (LOCK) {
            preferences.edit().clear().apply();
            try (AppDatabaseHelper database = new AppDatabaseHelper(context)) { database.clearUser(); }
        }
    }
}
