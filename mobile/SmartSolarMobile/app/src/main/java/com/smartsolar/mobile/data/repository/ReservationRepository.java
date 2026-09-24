package com.smartsolar.mobile.data.repository;

import android.os.Handler;
import android.os.Looper;
import com.google.gson.Gson;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.CreateReservationRequest;
import com.smartsolar.mobile.data.remote.dto.ProblemDetailsResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.data.remote.dto.UpdateReservationRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Coordinates Member 3 reservation API calls for Android.
 * Authoritative business rules (7-day, 12-hour, overlap, capacity) reside strictly in the API.
 */
public final class ReservationRepository implements AutoCloseable {
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(ReservationError error);
    }

    private static final Gson GSON = new Gson();
    private final ApiService api;
    private final ExecutorService worker;
    private final Handler main;
    private volatile Call<?> currentCall;
    private volatile boolean closed;

    public ReservationRepository(ApiService api) {
        this(api, Executors.newSingleThreadExecutor(), getSafeMainHandler());
    }

    public ReservationRepository(ApiService api, ExecutorService worker, Handler main) {
        this.api = api;
        this.worker = worker;
        this.main = main;
    }

    private static Handler getSafeMainHandler() {
        try {
            Looper looper = Looper.getMainLooper();
            return looper != null ? new Handler(looper) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public void createReservation(String slotId, double energyAmountKwh, Callback<ReservationResponse> callback) {
        executeCall(() -> api.createReservation(new CreateReservationRequest(slotId, energyAmountKwh)), callback);
    }

    public void getMyReservations(Callback<List<ReservationResponse>> callback) {
        executeCall(api::getMyReservations, callback);
    }

    public void getAvailableSlots(Callback<List<com.smartsolar.mobile.data.remote.dto.AvailableSlotResponse>> callback) {
        executeCall(api::getAvailableSlots, callback);
    }

    public void getReservation(String reservationId, Callback<ReservationResponse> callback) {
        executeCall(() -> api.getReservation(reservationId), callback);
    }

    public void updateReservation(String reservationId, String slotId, double energyAmountKwh, Callback<ReservationResponse> callback) {
        executeCall(() -> api.updateReservation(reservationId, new UpdateReservationRequest(slotId, energyAmountKwh)), callback);
    }

    public void cancelReservation(String reservationId, Callback<ReservationResponse> callback) {
        executeCall(() -> api.cancelReservation(reservationId), callback);
    }

    private interface CallSupplier<T> {
        Call<T> get();
    }

    private <T> void executeCall(CallSupplier<T> supplier, Callback<T> callback) {
        worker.execute(() -> {
            try {
                Call<T> call = supplier.get();
                currentCall = call;
                Response<T> response = call.execute();
                if (response.isSuccessful() && response.body() != null) {
                    deliverSuccess(callback, response.body());
                } else {
                    ReservationError error = parseError(response);
                    deliverError(callback, error);
                }
            } catch (IOException exception) {
                deliverError(callback, new ReservationError(0, "Unable to reach the server. Check your connection.", null, null, false));
            } catch (RuntimeException exception) {
                deliverError(callback, new ReservationError(0, "An unexpected error occurred.", null, null, false));
            } finally {
                currentCall = null;
            }
        });
    }

    public static ReservationError parseError(Response<?> response) {
        int code = response.code();
        if (code == 401) {
            return new ReservationError(401, "Your session has expired. Please sign in again.", null, null, true);
        }
        String message = null;
        String traceId = null;
        Map<String, List<String>> validationErrors = null;
        try {
            if (response.errorBody() != null) {
                String errorJson = response.errorBody().string();
                ProblemDetailsResponse details = GSON.fromJson(errorJson, ProblemDetailsResponse.class);
                if (details != null) {
                    if (details.getDetail() != null && !details.getDetail().trim().isEmpty()) {
                        message = details.getDetail().trim();
                    } else if (details.getTitle() != null && !details.getTitle().trim().isEmpty()) {
                        message = details.getTitle().trim();
                    }
                    traceId = details.getTraceId();
                    validationErrors = details.getErrors();
                    if (message == null && validationErrors != null && !validationErrors.isEmpty()) {
                        for (List<String> errs : validationErrors.values()) {
                            if (errs != null && !errs.isEmpty()) {
                                message = errs.get(0);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to default status-based error message.
        }
        if (message == null || message.trim().isEmpty()) {
            switch (code) {
                case 400: message = "Invalid reservation request."; break;
                case 403: message = "You do not have permission to perform this action."; break;
                case 404: message = "The requested reservation was not found."; break;
                case 409: message = "Reservation conflict or cutoff window passed."; break;
                default: message = "Request failed with status " + code + "."; break;
            }
        }
        return new ReservationError(code, message, traceId, validationErrors, false);
    }

    private <T> void deliverSuccess(Callback<T> callback, T result) {
        if (main != null) {
            main.post(() -> { if (!closed) callback.onSuccess(result); });
        } else {
            if (!closed) callback.onSuccess(result);
        }
    }

    private <T> void deliverError(Callback<T> callback, ReservationError error) {
        if (main != null) {
            main.post(() -> { if (!closed) callback.onError(error); });
        } else {
            if (!closed) callback.onError(error);
        }
    }

    @Override
    public void close() {
        closed = true;
        Call<?> call = currentCall;
        if (call != null) call.cancel();
        worker.shutdownNow();
    }
}
