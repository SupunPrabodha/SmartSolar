package com.smartsolar.mobile.data.remote.dto;

public class LoginRequest {
    private final String nic;
    private final String password;

    public LoginRequest(String nic, String password) {
        this.nic = nic;
        this.password = password;
    }

    public String getNic() { return nic; }
    public String getPassword() { return password; }
}
