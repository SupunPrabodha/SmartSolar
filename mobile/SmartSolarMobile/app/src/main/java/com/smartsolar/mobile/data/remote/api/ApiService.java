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
    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @GET("users/me")
    Call<UserResponse> getCurrentUser();

    @PUT("users/me") Call<UserResponse> updateMyProfile(@Body com.smartsolar.mobile.data.remote.dto.UpdateProfileRequest request);
    @POST("users/me/deactivation-request") Call<Void> requestDeactivation();
}
