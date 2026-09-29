package com.smartsolar.mobile.util;

import android.view.View;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import retrofit2.Response;

/** Single visible Snackbar, stable deduplication and accessible dismissal for immediate feedback. */
public final class EnterpriseFeedback {
    public enum Kind { SUCCESS, INFO, WARNING, ERROR }
    private Snackbar current;
    private String key;
    public void show(View view, String message, boolean error) { show(view, message, error ? Kind.ERROR : Kind.SUCCESS); }
    public void show(View view, String message, Kind kind) {
        if (current != null && current.isShown() && message.equals(key)) return;
        if (current != null) current.dismiss();
        key = message;
        current = Snackbar.make(view, message, kind == Kind.ERROR || kind == Kind.WARNING ? Snackbar.LENGTH_INDEFINITE : Snackbar.LENGTH_LONG);
        current.setAction("Dismiss", v -> current.dismiss());
        current.setTextMaxLines(5);
        current.setBackgroundTint(android.graphics.Color.parseColor(kind == Kind.ERROR ? "#842D24" : kind == Kind.WARNING ? "#715100" : kind == Kind.INFO ? "#20516D" : "#164F3B"));
        current.setTextColor(android.graphics.Color.WHITE); current.setActionTextColor(android.graphics.Color.WHITE);
        current.show();
    }
    public void dismiss() { if (current != null) current.dismiss(); current = null; key = null; }
    public static String problem(Response<?> response) {
        String message = response.code() >= 500 ? "Service unavailable. Please retry." :
            response.code() == 403 ? "This action is not available for your account." :
            response.code() == 401 ? "Your session ended. Sign in again." : "The request could not be completed.";
        try {
            if (response.errorBody() != null) {
                String body = response.errorBody().string();
                if (body.length() < 16000 && response.code() < 500) {
                    JsonObject problem = JsonParser.parseString(body).getAsJsonObject();
                    if (problem.has("detail") && problem.get("detail").isJsonPrimitive()) message = problem.get("detail").getAsString();
                    else if (problem.has("title")) message = problem.get("title").getAsString();
                }
            }
        } catch (Exception ignored) { }
        String reference = response.headers().get("X-Correlation-ID");
        if (reference != null && reference.matches("[a-fA-F0-9]{32}")) message += " Reference: " + reference;
        return message;
    }
}
