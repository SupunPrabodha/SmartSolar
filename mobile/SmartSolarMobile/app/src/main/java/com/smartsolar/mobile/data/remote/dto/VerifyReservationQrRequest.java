package com.smartsolar.mobile.data.remote.dto;

public class VerifyReservationQrRequest {
    private String qrPayload;

    public VerifyReservationQrRequest() { }

    public VerifyReservationQrRequest(String qrPayload) {
        this.qrPayload = qrPayload;
    }

    public String getQrPayload() { return qrPayload; }
    public void setQrPayload(String qrPayload) { this.qrPayload = qrPayload; }
}
