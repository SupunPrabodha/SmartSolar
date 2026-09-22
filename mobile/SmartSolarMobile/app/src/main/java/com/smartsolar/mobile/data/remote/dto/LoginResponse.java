package com.smartsolar.mobile.data.remote.dto;

public class LoginResponse {
    private String accessToken;
    private String expiresAtUtc;
    private UserResponse user;

    public String getAccessToken() { return accessToken; }
    public String getExpiresAtUtc() { return expiresAtUtc; }
    public UserResponse getUser() { return user; }
}
