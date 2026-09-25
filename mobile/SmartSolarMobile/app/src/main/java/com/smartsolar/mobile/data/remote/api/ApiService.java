package com.smartsolar.mobile.data.remote.api;

import com.smartsolar.mobile.data.remote.dto.LoginRequest;
import com.smartsolar.mobile.data.remote.dto.LoginResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationDashboardSummaryResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationPageResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationQrResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationVerificationResponse;
import com.smartsolar.mobile.data.remote.dto.UserResponse;
import com.smartsolar.mobile.data.remote.dto.VerifyReservationQrRequest;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;

public interface ApiService {
    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @GET("users/me")
    Call<UserResponse> getCurrentUser();

    @GET("reservations/dashboard-summary")
    Call<ReservationDashboardSummaryResponse> getDashboardSummary();

    @GET("reservations/current")
    Call<ReservationPageResponse> getCurrentBookings(@QueryMap Map<String, String> query);

    @GET("reservations/pending")
    Call<ReservationPageResponse> getPendingBookings(@QueryMap Map<String, String> query);

    @GET("reservations/history")
    Call<ReservationPageResponse> getBookingHistory(@QueryMap Map<String, String> query);

    @GET("reservations/search")
    Call<ReservationPageResponse> searchBookings(@QueryMap Map<String, String> query);

    @POST("reservations/{reservationId}/qr")
    Call<ReservationQrResponse> issueQr(@Path("reservationId") String reservationId);

    @POST("reservations/qr/verify")
    Call<ReservationVerificationResponse> verifyQr(@Body VerifyReservationQrRequest request);

    @POST("reservations/qr/complete")
    Call<com.smartsolar.mobile.data.remote.dto.ReservationCompletionResponse> completeTransfer(@Body com.smartsolar.mobile.data.remote.dto.CompleteReservationTransferRequest request);
}

