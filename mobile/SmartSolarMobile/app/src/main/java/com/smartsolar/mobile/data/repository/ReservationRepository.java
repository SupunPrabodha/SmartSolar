package com.smartsolar.mobile.data.repository;

import android.os.Handler;
import android.os.Looper;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.ReservationDashboardSummaryResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationPageResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Response;

public final class ReservationRepository implements AutoCloseable {
    public interface Callback<T> {
        void onComplete(T result, int errorResource, int httpStatusCode);
    }

    private final ApiService api;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile Call<?> currentCall;
    private volatile boolean closed;

    public ReservationRepository(ApiService api) {
        this.api = api;
    }

    public void getDashboardSummary(Callback<ReservationDashboardSummaryResponse> callback) {
        executeCall(() -> api.getDashboardSummary(), callback);
    }

    public void getCurrentBookings(Map<String, String> filters, Callback<ReservationPageResponse> callback) {
        Map<String, String> query = filters != null ? filters : Collections.emptyMap();
        executeCall(() -> api.getCurrentBookings(query), callback);
    }

    public void getPendingBookings(Map<String, String> filters, Callback<ReservationPageResponse> callback) {
        Map<String, String> query = filters != null ? filters : Collections.emptyMap();
        executeCall(() -> api.getPendingBookings(query), callback);
    }

    public void getBookingHistory(Map<String, String> filters, Callback<ReservationPageResponse> callback) {
        Map<String, String> query = filters != null ? filters : Collections.emptyMap();
        executeCall(() -> api.getBookingHistory(query), callback);
    }

    public void searchBookings(Map<String, String> filters, Callback<ReservationPageResponse> callback) {
        Map<String, String> query = filters != null ? filters : Collections.emptyMap();
        executeCall(() -> api.searchBookings(query), callback);
    }

    public void issueQr(String reservationId, Callback<com.smartsolar.mobile.data.remote.dto.ReservationQrResponse> callback) {
        executeCall(() -> api.issueQr(reservationId), callback);
    }

    public void verifyQr(String qrPayload, Callback<com.smartsolar.mobile.data.remote.dto.ReservationVerificationResponse> callback) {
        com.smartsolar.mobile.data.remote.dto.VerifyReservationQrRequest req =
                new com.smartsolar.mobile.data.remote.dto.VerifyReservationQrRequest(qrPayload);
        executeCall(() -> api.verifyQr(req), callback);
    }

    public void completeTransfer(String qrPayload, String reservationId, Callback<com.smartsolar.mobile.data.remote.dto.ReservationCompletionResponse> callback) {
        com.smartsolar.mobile.data.remote.dto.CompleteReservationTransferRequest req =
                new com.smartsolar.mobile.data.remote.dto.CompleteReservationTransferRequest(qrPayload, reservationId);
        executeCall(() -> api.completeTransfer(req), callback);
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
                if (!response.isSuccessful() || response.body() == null) {
                    if (response.errorBody() != null) response.errorBody().close();
                    int code = response.code();
                    int errorRes = code == 401 ? R.string.session_expired
                            : code == 403 ? R.string.access_denied
                            : R.string.load_failed;
                    deliver(callback, null, errorRes, code);
                    return;
                }
                deliver(callback, response.body(), 0, response.code());
            } catch (IOException e) {
                deliver(callback, null, R.string.connection_failed, 0);
            } catch (RuntimeException e) {
                deliver(callback, null, R.string.load_failed, 0);
            } finally {
                currentCall = null;
            }
        });
    }

    private <T> void deliver(Callback<T> callback, T result, int errorResource, int httpStatusCode) {
        main.post(() -> {
            if (!closed) {
                callback.onComplete(result, errorResource, httpStatusCode);
            }
        });
    }

    @Override
    public void close() {
        closed = true;
        Call<?> call = currentCall;
        if (call != null) call.cancel();
        worker.shutdownNow();
    }
}
