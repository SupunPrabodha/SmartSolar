package com.smartsolar.mobile.data.repository;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.AvailableSlotResponse;
import com.smartsolar.mobile.data.remote.dto.CompleteReservationTransferRequest;
import com.smartsolar.mobile.data.remote.dto.CreateReservationRequest;
import com.smartsolar.mobile.data.remote.dto.ProblemDetailsResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationCompletionResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationDashboardSummaryResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationPageResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationQrResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationVerificationResponse;
import com.smartsolar.mobile.data.remote.dto.UpdateReservationRequest;
import com.smartsolar.mobile.data.remote.dto.VerifyReservationQrRequest;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Response;

public final class ReservationRepository implements AutoCloseable {

    /** Callback for reservation create, read, update, and cancellation operations. */
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(ReservationError error);
    }

    /** Callback for dashboard, booking, and QR screens. */
    public interface DashboardCallback<T> {
        void onComplete(T result, int errorResource, int httpStatusCode);
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

    public void createReservation(
            String slotId,
            double energyAmountKwh,
            Callback<ReservationResponse> callback
    ) {
        executeReservationCall(
                () -> api.createReservation(
                        new CreateReservationRequest(slotId, energyAmountKwh)
                ),
                callback
        );
    }

    public void getMyReservations(Callback<List<ReservationResponse>> callback) {
        executeReservationCall(api::getMyReservations, callback);
    }

    public void getAvailableSlots(Callback<List<AvailableSlotResponse>> callback) {
        executeReservationCall(api::getAvailableSlots, callback);
    }

    public void getReservation(
            String reservationId,
            Callback<ReservationResponse> callback
    ) {
        executeReservationCall(() -> api.getReservation(reservationId), callback);
    }

    public void updateReservation(
            String reservationId,
            String slotId,
            double energyAmountKwh,
            Callback<ReservationResponse> callback
    ) {
        executeReservationCall(
                () -> api.updateReservation(
                        reservationId,
                        new UpdateReservationRequest(slotId, energyAmountKwh)
                ),
                callback
        );
    }

    public void cancelReservation(
            String reservationId,
            Callback<ReservationResponse> callback
    ) {
        executeReservationCall(() -> api.cancelReservation(reservationId), callback);
    }

    public void getDashboardSummary(
            DashboardCallback<ReservationDashboardSummaryResponse> callback
    ) {
        executeDashboardCall(api::getDashboardSummary, callback);
    }

    public void getCurrentBookings(
            Map<String, String> filters,
            DashboardCallback<ReservationPageResponse> callback
    ) {
        Map<String, String> query =
                filters != null ? filters : Collections.emptyMap();
        executeDashboardCall(() -> api.getCurrentBookings(query), callback);
    }

    public void getBookingHistory(
            Map<String, String> filters,
            DashboardCallback<ReservationPageResponse> callback
    ) {
        Map<String, String> query =
                filters != null ? filters : Collections.emptyMap();
        executeDashboardCall(() -> api.getBookingHistory(query), callback);
    }

    public void issueQr(
            String reservationId,
            DashboardCallback<ReservationQrResponse> callback
    ) {
        executeDashboardCall(() -> api.issueQr(reservationId), callback);
    }

    public void verifyQr(
            String qrPayload,
            DashboardCallback<ReservationVerificationResponse> callback
    ) {
        VerifyReservationQrRequest request =
                new VerifyReservationQrRequest(qrPayload);
        executeDashboardCall(() -> api.verifyQr(request), callback);
    }

    public void completeTransfer(
            String qrPayload,
            String reservationId,
            DashboardCallback<ReservationCompletionResponse> callback
    ) {
        CompleteReservationTransferRequest request =
                new CompleteReservationTransferRequest(qrPayload, reservationId);
        executeDashboardCall(() -> api.completeTransfer(request), callback);
    }

    private interface CallSupplier<T> {
        Call<T> get();
    }

    private <T> void executeReservationCall(
            CallSupplier<T> supplier,
            Callback<T> callback
    ) {
        worker.execute(() -> {
            try {
                Call<T> call = supplier.get();
                currentCall = call;
                Response<T> response = call.execute();

                if (response.isSuccessful() && response.body() != null) {
                    deliverSuccess(callback, response.body());
                } else {
                    deliverError(callback, parseError(response));
                }
            } catch (IOException exception) {
                deliverError(callback, new ReservationError(
                        0,
                        "Unable to reach the server. Check your connection.",
                        null,
                        null,
                        false
                ));
            } catch (RuntimeException exception) {
                deliverError(callback, new ReservationError(
                        0,
                        "An unexpected error occurred.",
                        null,
                        null,
                        false
                ));
            } finally {
                currentCall = null;
            }
        });
    }

    private <T> void executeDashboardCall(
            CallSupplier<T> supplier,
            DashboardCallback<T> callback
    ) {
        worker.execute(() -> {
            try {
                Call<T> call = supplier.get();
                currentCall = call;
                Response<T> response = call.execute();

                if (!response.isSuccessful() || response.body() == null) {
                    if (response.errorBody() != null) {
                        response.errorBody().close();
                    }

                    int code = response.code();
                    int errorResource = code == 401
                            ? R.string.session_expired
                            : code == 403
                                    ? R.string.access_denied
                                    : R.string.load_failed;

                    deliverComplete(callback, null, errorResource, code);
                    return;
                }

                deliverComplete(callback, response.body(), 0, response.code());
            } catch (IOException exception) {
                deliverComplete(callback, null, R.string.connection_failed, 0);
            } catch (RuntimeException exception) {
                deliverComplete(callback, null, R.string.load_failed, 0);
            } finally {
                currentCall = null;
            }
        });
    }

    public static ReservationError parseError(Response<?> response) {
        int code = response.code();

        if (code == 401) {
            return new ReservationError(
                    401,
                    "Your session has expired. Please sign in again.",
                    null,
                    null,
                    true
            );
        }

        String message = null;
        String traceId = null;
        Map<String, List<String>> validationErrors = null;

        try {
            if (response.errorBody() != null) {
                String errorJson = response.errorBody().string();
                ProblemDetailsResponse details =
                        GSON.fromJson(errorJson, ProblemDetailsResponse.class);

                if (details != null) {
                    if (details.getDetail() != null
                            && !details.getDetail().trim().isEmpty()) {
                        message = details.getDetail().trim();
                    } else if (details.getTitle() != null
                            && !details.getTitle().trim().isEmpty()) {
                        message = details.getTitle().trim();
                    }

                    traceId = details.getTraceId();
                    validationErrors = details.getErrors();

                    if (message == null
                            && validationErrors != null
                            && !validationErrors.isEmpty()) {
                        for (List<String> errors : validationErrors.values()) {
                            if (errors != null && !errors.isEmpty()) {
                                message = errors.get(0);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Use the status-based message if the response body cannot be parsed.
        }

        if (message == null || message.trim().isEmpty()) {
            switch (code) {
                case 400:
                    message = "Invalid reservation request.";
                    break;
                case 403:
                    message = "You do not have permission to perform this action.";
                    break;
                case 404:
                    message = "The requested reservation was not found.";
                    break;
                case 409:
                    message = "Reservation conflict or cutoff window passed.";
                    break;
                default:
                    message = "Request failed with status " + code + ".";
                    break;
            }
        }

        return new ReservationError(
                code,
                message,
                traceId,
                validationErrors,
                false
        );
    }

    private <T> void deliverSuccess(Callback<T> callback, T result) {
        deliverOnMain(() -> {
            if (!closed) {
                callback.onSuccess(result);
            }
        });
    }

    private <T> void deliverError(Callback<T> callback, ReservationError error) {
        deliverOnMain(() -> {
            if (!closed) {
                callback.onError(error);
            }
        });
    }

    private <T> void deliverComplete(
            DashboardCallback<T> callback,
            T result,
            int errorResource,
            int httpStatusCode
    ) {
        deliverOnMain(() -> {
            if (!closed) {
                callback.onComplete(result, errorResource, httpStatusCode);
            }
        });
    }

    private void deliverOnMain(Runnable action) {
        if (main != null) {
            main.post(action);
        } else {
            action.run();
        }
    }

    @Override
    public void close() {
        closed = true;

        Call<?> call = currentCall;
        if (call != null) {
            call.cancel();
        }

        worker.shutdownNow();
    }
}