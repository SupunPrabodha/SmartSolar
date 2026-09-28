package com.smartsolar.mobile.data.remote.dto;

public final class RegisterProsumerRequest {
    private final String nic;
    private final String fullName;
    private final String email;
    private final String phoneNumber;
    private final String password;

    public RegisterProsumerRequest(String nic, String fullName, String email, String phoneNumber, String password) {
        this.nic = nic;
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.password = password;
    }
}
