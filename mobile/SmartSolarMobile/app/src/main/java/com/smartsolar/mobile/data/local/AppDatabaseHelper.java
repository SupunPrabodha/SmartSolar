package com.smartsolar.mobile.data.local;

import android.content.Context;
import android.content.ContentValues;
import com.smartsolar.mobile.data.remote.dto.UserResponse;
import java.time.Instant;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AppDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "smart_solar_local.db";
    private static final int DATABASE_VERSION = 1;

    public AppDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void cacheUser(UserResponse user) {
        // Cache only the API profile for this signed-in user; never passwords or authoritative state.
        ContentValues values = new ContentValues();
        values.put("nic", user.getNic());
        values.put("full_name", user.getFullName());
        values.put("email", user.getEmail());
        values.put("phone_number", user.getPhoneNumber());
        values.put("role", user.getRole());
        values.put("status", user.getStatus());
        values.put("updated_at_utc", Instant.now().toString());
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("local_user", null, null);
            db.insertOrThrow("local_user", null, values);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public void clearUser() {
        // Remove the previous user's local profile on logout/session expiry.
        getWritableDatabase().delete("local_user", null, null);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE local_user (" +
                        "nic TEXT PRIMARY KEY," +
                        "full_name TEXT NOT NULL," +
                        "email TEXT," +
                        "phone_number TEXT," +
                        "role TEXT NOT NULL," +
                        "status TEXT NOT NULL," +
                        "updated_at_utc TEXT NOT NULL" +
                        ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future schema migrations must be added here rather than dropping user data silently.
    }
}
