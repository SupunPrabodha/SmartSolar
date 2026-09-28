package com.smartsolar.mobile.data.remote.dto;

public final class UpdateProfileRequest {
    private final String fullName;
    private final String email;
    private final String phoneNumber;
    public UpdateProfileRequest(String fullName, String email, String phoneNumber) { this.fullName = fullName; this.email = email; this.phoneNumber = phoneNumber; }
}
