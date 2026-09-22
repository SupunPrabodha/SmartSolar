package com.smartsolar.mobile.data.repository;

import android.os.Handler;
import android.os.Looper;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.LoginRequest;
import com.smartsolar.mobile.data.remote.dto.LoginResponse;
import com.smartsolar.mobile.data.remote.dto.UserResponse;
import com.smartsolar.mobile.util.SessionManager;
import com.smartsolar.mobile.util.MobileAccess;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Response;

/** Coordinates REST/session/cache work. Enterprise validation remains in the API. */
public final class AuthRepository implements AutoCloseable {
    public interface Callback {
        void complete(UserResponse user, long expiresAtMillis, int errorResource);
    }

    private final ApiService api;
    private final SessionManager sessions;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile Call<?> currentCall;
    private volatile boolean closed;

    public AuthRepository(ApiService api, SessionManager sessions) {
        this.api = api;
        this.sessions = sessions;
    }

    public void login(String nic, String password, Callback callback) {
        worker.execute(() -> {
            try {
                sessions.clear();
                Call<LoginResponse> call = api.login(new LoginRequest(nic, password));
                currentCall = call;
                Response<LoginResponse> response = call.execute();
                if (!response.isSuccessful() || response.body() == null) {
                    if (response.errorBody() != null) response.errorBody().close();
                    deliver(callback, null, R.string.login_failed);
                    return;
                }
                LoginResponse body = response.body();
                if (body.getUser() == null || !MobileAccess.canEnter(body.getUser().getRole(), body.getUser().getStatus())) {
                    sessions.clear();
                    deliver(callback, null, R.string.mobile_role_not_supported);
                    return;
                }
                sessions.saveSession(body.getAccessToken(), body.getExpiresAtUtc(), body.getUser());
                deliver(callback, body.getUser(), 0);
            } catch (IOException exception) {
                deliver(callback, null, R.string.connection_failed);
            } catch (RuntimeException exception) {
                deliver(callback, null, R.string.session_failed);
            } finally { currentCall = null; }
        });
    }

    public void restore(Callback callback) {
        worker.execute(() -> {
            try {
                if (sessions.getAccessToken() == null) {
                    deliver(callback, null, 0);
                    return;
                }
                Call<UserResponse> call = api.getCurrentUser();
                currentCall = call;
                Response<UserResponse> response = call.execute();
                if (!response.isSuccessful() || response.body() == null) {
                    if (response.errorBody() != null) response.errorBody().close();
                    deliver(callback, null, response.code() == 401
                            ? R.string.session_expired : R.string.connection_failed);
                    return;
                }
                if (!MobileAccess.canEnter(response.body().getRole(), response.body().getStatus())) {
                    sessions.clear();
                    deliver(callback, null, R.string.mobile_role_not_supported);
                    return;
                }
                sessions.cacheProfile(response.body());
                deliver(callback, response.body(), 0);
            } catch (IOException exception) {
                // A transient outage does not grant offline authorization or erase a valid token.
                deliver(callback, null, R.string.connection_failed);
            } catch (RuntimeException exception) {
                deliver(callback, null, R.string.session_failed);
            } finally { currentCall = null; }
        });
    }

    public void logout(Callback callback) {
        worker.execute(() -> {
            try {
                sessions.clear();
                deliver(callback, null, 0);
            } catch (RuntimeException exception) {
                deliver(callback, null, R.string.session_failed);
            }
        });
    }

    private void deliver(Callback callback, UserResponse user, int error) {
        long expiry = user == null ? 0 : sessions.getExpiresAtMillis();
        main.post(() -> { if (!closed) callback.complete(user, expiry, error); });
    }

    @Override
    public void close() {
        closed = true;
        Call<?> call = currentCall;
        if (call != null) call.cancel();
        worker.shutdownNow();
    }
}
