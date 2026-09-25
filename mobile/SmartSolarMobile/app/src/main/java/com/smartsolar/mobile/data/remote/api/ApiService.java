package com.smartsolar.mobile.data.remote.api;

import com.smartsolar.mobile.data.remote.dto.AvailableSlotResponse;
import com.smartsolar.mobile.data.remote.dto.CompleteReservationTransferRequest;
import com.smartsolar.mobile.data.remote.dto.CreateReservationRequest;
import com.smartsolar.mobile.data.remote.dto.LoginRequest;
import com.smartsolar.mobile.data.remote.dto.LoginResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationCompletionResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationDashboardSummaryResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationPageResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationQrResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationVerificationResponse;
import com.smartsolar.mobile.data.remote.dto.UpdateReservationRequest;
import com.smartsolar.mobile.data.remote.dto.UserResponse;
import com.smartsolar.mobile.data.remote.dto.VerifyReservationQrRequest;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;

public interface ApiService {
    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @GET("users/me")
    Call<UserResponse> getCurrentUser();

    @POST("reservations")
    Call<ReservationResponse> createReservation(@Body CreateReservationRequest request);

    @GET("reservations/my")
    Call<List<ReservationResponse>> getMyReservations();

    @GET("reservations/slots")
    Call<List<AvailableSlotResponse>> getAvailableSlots();

    @GET("reservations/{reservationId}")
    Call<ReservationResponse> getReservation(
            @Path("reservationId") String reservationId
    );

    @PUT("reservations/{reservationId}")
    Call<ReservationResponse> updateReservation(
            @Path("reservationId") String reservationId,
            @Body UpdateReservationRequest request
    );

    @PATCH("reservations/{reservationId}/cancel")
    Call<ReservationResponse> cancelReservation(
            @Path("reservationId") String reservationId
    );

    @GET("reservations/dashboard-summary")
    Call<ReservationDashboardSummaryResponse> getDashboardSummary();

    @GET("reservations/current")
    Call<ReservationPageResponse> getCurrentBookings(
            @QueryMap Map<String, String> query
    );

    @GET("reservations/pending")
    Call<ReservationPageResponse> getPendingBookings(
            @QueryMap Map<String, String> query
    );

    @GET("reservations/history")
    Call<ReservationPageResponse> getBookingHistory(
            @QueryMap Map<String, String> query
    );

    @GET("reservations/search")
    Call<ReservationPageResponse> searchBookings(
            @QueryMap Map<String, String> query
    );

    @POST("reservations/{reservationId}/qr")
    Call<ReservationQrResponse> issueQr(
            @Path("reservationId") String reservationId
    );

    @POST("reservations/qr/verify")
    Call<ReservationVerificationResponse> verifyQr(
            @Body VerifyReservationQrRequest request
    );

    @POST("reservations/qr/complete")
    Call<ReservationCompletionResponse> completeTransfer(
            @Body CompleteReservationTransferRequest request
    );
}