package com.smartsolar.mobile.data.remote.dto;

public class ReservationQrResponse {
    private String reservationId;
    private String qrPayload;
    private String issuedAtUtc;

    public ReservationQrResponse() { }

    public ReservationQrResponse(String reservationId, String qrPayload, String issuedAtUtc) {
        this.reservationId = reservationId;
        this.qrPayload = qrPayload;
        this.issuedAtUtc = issuedAtUtc;
    }

    public String getReservationId() { return reservationId; }
    public String getQrPayload() { return qrPayload; }
    public String getIssuedAtUtc() { return issuedAtUtc; }
}
