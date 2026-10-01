package com.smartsolar.mobile.data.remote.dto;

public class ReservationCompletionResponse {
    private String reservationId;
    private String prosumerNic;
    private String stationId;
    private String slotId;
    private double energyAmountKwh;
    private String scheduledStartAtUtc;
    private String scheduledEndAtUtc;
    private String status;
    private String completedAtUtc;
    private String completedByOperatorNic;
    private String updatedAtUtc;

    public ReservationCompletionResponse() { }

    public ReservationCompletionResponse(String reservationId, String prosumerNic, String stationId,
                                         String slotId, double energyAmountKwh, String scheduledStartAtUtc,
                                         String scheduledEndAtUtc, String status, String completedAtUtc,
                                         String completedByOperatorNic, String updatedAtUtc) {
        this.reservationId = reservationId;
        this.prosumerNic = prosumerNic;
        this.stationId = stationId;
        this.slotId = slotId;
        this.energyAmountKwh = energyAmountKwh;
        this.scheduledStartAtUtc = scheduledStartAtUtc;
        this.scheduledEndAtUtc = scheduledEndAtUtc;
        this.status = status;
        this.completedAtUtc = completedAtUtc;
        this.completedByOperatorNic = completedByOperatorNic;
        this.updatedAtUtc = updatedAtUtc;
    }

    public String getReservationId() { return reservationId; }
    public String getProsumerNic() { return prosumerNic; }
    public String getStationId() { return stationId; }
    public String getSlotId() { return slotId; }
    public double getEnergyAmountKwh() { return energyAmountKwh; }
    public String getScheduledStartAtUtc() { return scheduledStartAtUtc; }
    public String getScheduledEndAtUtc() { return scheduledEndAtUtc; }
    public String getStatus() { return status; }
    public String getCompletedAtUtc() { return completedAtUtc; }
    public String getCompletedByOperatorNic() { return completedByOperatorNic; }
    public String getUpdatedAtUtc() { return updatedAtUtc; }
}
