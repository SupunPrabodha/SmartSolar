package com.smartsolar.mobile.data.remote.dto;
/** Distance is calculated by the API and is never saved to local persistence. */
public final class NearbyStationResponse {
    public StationResponse station;
    public double distanceKm;
}
