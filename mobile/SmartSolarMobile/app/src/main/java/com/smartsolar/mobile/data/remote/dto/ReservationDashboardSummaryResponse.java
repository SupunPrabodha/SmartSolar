package com.smartsolar.mobile.data.remote.dto;

public class ReservationDashboardSummaryResponse {
    private long pendingReservations;
    private long approvedFutureReservations;
    private String generatedAtUtc;

    public ReservationDashboardSummaryResponse() { }

    public ReservationDashboardSummaryResponse(long pendingReservations, long approvedFutureReservations, String generatedAtUtc) {
        this.pendingReservations = pendingReservations;
        this.approvedFutureReservations = approvedFutureReservations;
        this.generatedAtUtc = generatedAtUtc;
    }

    public long getPendingReservations() { return pendingReservations; }
    public long getApprovedFutureReservations() { return approvedFutureReservations; }
    public String getGeneratedAtUtc() { return generatedAtUtc; }
}
