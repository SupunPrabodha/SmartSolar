package com.smartsolar.mobile.data.remote.dto;

public class CompleteReservationTransferRequest {
    private String qrPayload;
    private String reservationId;

    public CompleteReservationTransferRequest() { }

    public CompleteReservationTransferRequest(String qrPayload) {
        this.qrPayload = qrPayload;
    }

    public CompleteReservationTransferRequest(String qrPayload, String reservationId) {
        this.qrPayload = qrPayload;
        this.reservationId = reservationId;
    }

    public String getQrPayload() { return qrPayload; }
    public void setQrPayload(String qrPayload) { this.qrPayload = qrPayload; }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
}
