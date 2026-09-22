package com.smartsolar.mobile.util;

/** Minimal token boundary used by HTTP infrastructure and local JVM tests. */
public interface SessionStore {
    String getAccessToken();
    void clearIfMatches(String token);
}
