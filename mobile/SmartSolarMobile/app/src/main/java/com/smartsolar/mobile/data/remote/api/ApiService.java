package com.smartsolar.mobile.data.remote.api;

import com.smartsolar.mobile.data.remote.dto.LoginRequest;
import com.smartsolar.mobile.data.remote.dto.LoginResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import com.smartsolar.mobile.data.remote.dto.UserResponse;

public interface ApiService {
    @GET("stations")
    Call<java.util.List<com.smartsolar.mobile.data.remote.dto.StationResponse>> listStations();
    @GET("stations/nearby")
    Call<java.util.List<com.smartsolar.mobile.data.remote.dto.NearbyStationResponse>> nearbyStations(
        @retrofit2.http.Query("latitude") double latitude, @retrofit2.http.Query("longitude") double longitude,
        @retrofit2.http.Query("radiusKm") double radiusKm);
    @GET("stations/{id}")
    Call<com.smartsolar.mobile.data.remote.dto.StationResponse> getStation(@retrofit2.http.Path("id") String id);
    @GET("stations/{id}/slots")
    Call<java.util.List<com.smartsolar.mobile.data.remote.dto.SlotResponse>> stationSlots(@retrofit2.http.Path("id") String id);

    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @GET("users/me")
    Call<UserResponse> getCurrentUser();

    @PUT("users/me") Call<UserResponse> updateMyProfile(@Body com.smartsolar.mobile.data.remote.dto.UpdateProfileRequest request);
    @POST("users/me/deactivation-request") Call<Void> requestDeactivation();
}
