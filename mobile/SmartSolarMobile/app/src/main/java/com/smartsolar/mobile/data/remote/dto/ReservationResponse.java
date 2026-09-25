package com.smartsolar.mobile.data.remote.dto;

public class ReservationResponse {
    private String reservationId;
    private String prosumerNic;
    private String stationId;
    private String slotId;
    private double energyAmountKwh;
    private String scheduledStartAtUtc;
    private String scheduledEndAtUtc;
    private String status;
    private String createdAtUtc;
    private String updatedAtUtc;
    private String rejectionRemark;

    public ReservationResponse() { }

    public ReservationResponse(String reservationId, String prosumerNic, String stationId,
                               String slotId, double energyAmountKwh, String scheduledStartAtUtc,
                               String scheduledEndAtUtc, String status, String createdAtUtc,
                               String updatedAtUtc) {
        this(reservationId, prosumerNic, stationId, slotId, energyAmountKwh, scheduledStartAtUtc,
             scheduledEndAtUtc, status, createdAtUtc, updatedAtUtc, null);
    }

    public ReservationResponse(String reservationId, String prosumerNic, String stationId,
                               String slotId, double energyAmountKwh, String scheduledStartAtUtc,
                               String scheduledEndAtUtc, String status, String createdAtUtc,
                               String updatedAtUtc, String rejectionRemark) {
        this.reservationId = reservationId;
        this.prosumerNic = prosumerNic;
        this.stationId = stationId;
        this.slotId = slotId;
        this.energyAmountKwh = energyAmountKwh;
        this.scheduledStartAtUtc = scheduledStartAtUtc;
        this.scheduledEndAtUtc = scheduledEndAtUtc;
        this.status = status;
        this.createdAtUtc = createdAtUtc;
        this.updatedAtUtc = updatedAtUtc;
        this.rejectionRemark = rejectionRemark;
    }

    public String getReservationId() { return reservationId; }
    public String getProsumerNic() { return prosumerNic; }
    public String getStationId() { return stationId; }
    public String getSlotId() { return slotId; }
    public double getEnergyAmountKwh() { return energyAmountKwh; }
    public String getScheduledStartAtUtc() { return scheduledStartAtUtc; }
    public String getScheduledEndAtUtc() { return scheduledEndAtUtc; }
    public String getStatus() { return status; }
    public String getCreatedAtUtc() { return createdAtUtc; }
    public String getUpdatedAtUtc() { return updatedAtUtc; }
    public String getRejectionRemark() { return rejectionRemark; }
    public void setRejectionRemark(String rejectionRemark) { this.rejectionRemark = rejectionRemark; }
}
