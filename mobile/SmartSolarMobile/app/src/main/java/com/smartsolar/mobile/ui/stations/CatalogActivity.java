package com.smartsolar.mobile.ui.stations;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.repository.AuthRepository;
import com.smartsolar.mobile.ui.auth.LoginActivity;
import com.smartsolar.mobile.util.SessionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Shares the existing verified session and cancels catalog requests when a screen stops. */
public abstract class CatalogActivity extends AppCompatActivity {
    protected ApiService api;
    protected TextView message;
    protected boolean visible;
    protected boolean authorized;
    private View progress;
    private AuthRepository auth;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Call<?>> calls = new ArrayList<>();
    private int generation;
    private int sessionGeneration;
    private final Runnable expire = () -> {
        authorized = false; resetRequests(); clearContent();
        auth.logout((user, expiry, error) -> openLogin());
    };
    protected void setup(int layout) {
        EdgeToEdge.enable(this);
        setContentView(layout);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.catalogRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        message = findViewById(R.id.catalogMessage); progress = findViewById(R.id.catalogProgress);
        findViewById(R.id.catalogBack).setOnClickListener(v -> finish());
        findViewById(R.id.catalogRetry).setOnClickListener(v -> verify());
        try {
            api = RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
            auth = new AuthRepository(api, new SessionManager(this));
        } catch (IllegalArgumentException exception) { openLogin(); }
    }
    @Override protected void onStart() {
        super.onStart(); visible = true; verify();
    }
    protected void verify() {
        if (auth == null) return;
        authorized = false; resetRequests(); clearContent(); busy(true);
        message.setText(R.string.checking_session);
        final int current = ++sessionGeneration;
        // Expiry is enforced even if /users/me cannot be reached.
        handler.removeCallbacks(expire);
        long expiryAt = new SessionManager(this).getExpiresAtMillis();
        handler.postDelayed(expire, Math.max(0, expiryAt - System.currentTimeMillis()));
        auth.restore((user, expiry, error) -> {
            if (!visible || current != sessionGeneration) return;
            busy(false);
            if (user == null) {
                if (error == 0 || error == R.string.session_expired || error == R.string.mobile_role_not_supported) openLogin();
                else message.setText(error);
                return;
            }
            authorized = true; message.setText("");
            onVerified();
        });
    }
    protected abstract void onVerified();
    protected abstract void clearContent();
    protected void busy(boolean value) { progress.setVisibility(value ? View.VISIBLE : View.GONE); }
    protected void resetRequests() {
        generation++;
        for (Call<?> call : calls) call.cancel();
        calls.clear();
    }
    protected <T> void request(Call<T> call, Consumer<T> success) {
        if (!authorized) return;
        final int current = generation;
        calls.add(call); busy(true);
        call.enqueue(new Callback<T>() {
            @Override public void onResponse(Call<T> request, Response<T> response) {
                calls.remove(request);
                if (!visible || current != generation) { if (response.errorBody() != null) response.errorBody().close(); return; }
                busy(!calls.isEmpty());
                if (response.code() == 401) {
                    if (response.errorBody() != null) response.errorBody().close();
                    openLogin(); return;
                }
                if (response.isSuccessful() && response.body() != null) success.accept(response.body());
                else {
                    if (response.errorBody() != null) response.errorBody().close();
                    message.setText(response.code() == 404 ? R.string.station_unavailable : R.string.catalog_request_failed);
                }
            }
            @Override public void onFailure(Call<T> request, Throwable error) {
                calls.remove(request);
                if (!visible || current != generation || request.isCanceled()) return;
                busy(!calls.isEmpty()); message.setText(R.string.connection_failed);
            }
        });
    }
    private void openLogin() {
        if (isFinishing() || isDestroyed()) return;
        startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
    @Override protected void onStop() {
        visible = false; authorized = false; sessionGeneration++; resetRequests();
        handler.removeCallbacks(expire); clearContent(); super.onStop();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (auth != null) auth.close();
        super.onDestroy();
    }
}
