package com.smartsolar.mobile.data.remote.api;

import com.smartsolar.mobile.data.remote.dto.CreateReservationRequest;
import com.smartsolar.mobile.data.remote.dto.LoginRequest;
import com.smartsolar.mobile.data.remote.dto.LoginResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.data.remote.dto.UpdateReservationRequest;
import com.smartsolar.mobile.data.remote.dto.UserResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface ApiService {
    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @GET("users/me")
    Call<UserResponse> getCurrentUser();

    @POST("reservations")
    Call<ReservationResponse> createReservation(@Body CreateReservationRequest request);

    @GET("reservations/my")
    Call<java.util.List<ReservationResponse>> getMyReservations();

    @GET("reservations/slots")
    Call<java.util.List<com.smartsolar.mobile.data.remote.dto.AvailableSlotResponse>> getAvailableSlots();

    @GET("reservations/{reservationId}")
    Call<ReservationResponse> getReservation(@Path("reservationId") String reservationId);

    @PUT("reservations/{reservationId}")
    Call<ReservationResponse> updateReservation(
            @Path("reservationId") String reservationId,
            @Body UpdateReservationRequest request
    );

    @PATCH("reservations/{reservationId}/cancel")
    Call<ReservationResponse> cancelReservation(@Path("reservationId") String reservationId);
}

