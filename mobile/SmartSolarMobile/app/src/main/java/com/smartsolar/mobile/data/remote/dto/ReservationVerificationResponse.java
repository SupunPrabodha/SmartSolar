package com.smartsolar.mobile.data.remote.dto;

public class ReservationVerificationResponse {
    private String reservationId;
    private String prosumerNic;
    private String stationId;
    private String slotId;
    private double energyAmountKwh;
    private String scheduledStartAtUtc;
    private String scheduledEndAtUtc;
    private String status;
    private String qrIssuedAtUtc;
    private boolean eligibleForCompletion;

    public ReservationVerificationResponse() { }

    public ReservationVerificationResponse(String reservationId, String prosumerNic, String stationId,
                                           String slotId, double energyAmountKwh, String scheduledStartAtUtc,
                                           String scheduledEndAtUtc, String status, String qrIssuedAtUtc,
                                           boolean eligibleForCompletion) {
        this.reservationId = reservationId;
        this.prosumerNic = prosumerNic;
        this.stationId = stationId;
        this.slotId = slotId;
        this.energyAmountKwh = energyAmountKwh;
        this.scheduledStartAtUtc = scheduledStartAtUtc;
        this.scheduledEndAtUtc = scheduledEndAtUtc;
        this.status = status;
        this.qrIssuedAtUtc = qrIssuedAtUtc;
        this.eligibleForCompletion = eligibleForCompletion;
    }

    public String getReservationId() { return reservationId; }
    public String getProsumerNic() { return prosumerNic; }
    public String getStationId() { return stationId; }
    public String getSlotId() { return slotId; }
    public double getEnergyAmountKwh() { return energyAmountKwh; }
    public String getScheduledStartAtUtc() { return scheduledStartAtUtc; }
    public String getScheduledEndAtUtc() { return scheduledEndAtUtc; }
    public String getStatus() { return status; }
    public String getQrIssuedAtUtc() { return qrIssuedAtUtc; }
    public boolean isEligibleForCompletion() { return eligibleForCompletion; }
}
