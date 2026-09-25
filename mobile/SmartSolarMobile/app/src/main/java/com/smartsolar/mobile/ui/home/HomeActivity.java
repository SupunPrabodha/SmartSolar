package com.smartsolar.mobile.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.dto.UserResponse;
import com.smartsolar.mobile.data.repository.AuthRepository;
import com.smartsolar.mobile.ui.auth.LoginActivity;
import com.smartsolar.mobile.util.SessionManager;
import java.text.DateFormat;
import java.util.Date;

/** Authenticated home with station discovery; other member modules remain disabled. */
public final class HomeActivity extends AppCompatActivity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable expiryCheck = this::signOut;
    private AuthRepository repository;
    private TextView textError;
    private TextView textSession;
    private View profileContent;
    private View progress;
    private Button buttonRefresh;
    private Button buttonLogout;
    private boolean busy;
    private boolean visible;
    private long expiresAtMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homeRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        textError = findViewById(R.id.textError);
        textSession = findViewById(R.id.textSession);
        profileContent = findViewById(R.id.profileContent);
        progress = findViewById(R.id.progress);
        buttonRefresh = findViewById(R.id.buttonRefresh);
        buttonLogout = findViewById(R.id.buttonLogout);
        ((TextView) findViewById(R.id.textEnvironment)).setText(
                BuildConfig.DEBUG ? R.string.environment_development : R.string.environment_release);
        SessionManager sessions = new SessionManager(this);
        expiresAtMillis = sessions.getExpiresAtMillis();
        try {
            repository = new AuthRepository(
                    RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG), sessions);
        } catch (IllegalArgumentException exception) {
            openLogin();
            return;
        }
        buttonRefresh.setOnClickListener(view -> restore());
        buttonLogout.setOnClickListener(view -> signOut());
        findViewById(R.id.buttonFindStations).setOnClickListener(view ->
            startActivity(new Intent(this, com.smartsolar.mobile.ui.stations.StationDiscoveryActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        scheduleExpiry();
        restore();
    }

    private void restore() {
        if (repository == null || busy) return;
        setBusy(true);
        profileContent.setVisibility(View.GONE);
        textSession.setText(R.string.checking_session);
        repository.restore(this::showResult);
    }

    private void showResult(UserResponse user, long expiry, int error) {
        if (isFinishing() || isDestroyed()) return;
        setBusy(false);
        textError.setVisibility(error == 0 ? View.GONE : View.VISIBLE);
        if (error != 0) textError.setText(error);
        if (user == null) {
            profileContent.setVisibility(View.GONE);
            if (error == 0 || error == R.string.session_expired || error == R.string.mobile_role_not_supported) {
                openLogin();
            } else {
                // A cached profile never authorizes offline access.
                textSession.setText(R.string.profile_unavailable);
                scheduleExpiry();
            }
            return;
        }
        expiresAtMillis = expiry;
        profileContent.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.textWelcome)).setText(getString(R.string.welcome_name, user.getFullName()));
        ((TextView) findViewById(R.id.textRole)).setText(getString(R.string.role_value, user.getRole()));
        ((TextView) findViewById(R.id.textAccountStatus)).setText(getString(R.string.status_value, user.getStatus()));
        ((TextView) findViewById(R.id.textExpiry)).setText(getString(R.string.expiry_value,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(expiry))));
        textSession.setText(getString(R.string.profile_verified,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date())));
        boolean operator = "GridOperator".equals(user.getRole());
        ((TextView) findViewById(R.id.moduleOne)).setText(R.string.find_stations);
        ((TextView) findViewById(R.id.moduleTwo)).setText(operator ? R.string.scan_transaction : R.string.my_reservations);
        ((TextView) findViewById(R.id.moduleThree)).setText(operator ? R.string.transaction_history : R.string.booking_history);
        scheduleExpiry();
    }

    private void scheduleExpiry() {
        main.removeCallbacks(expiryCheck);
        if (visible && expiresAtMillis > 0) {
            main.postDelayed(expiryCheck, Math.max(0, expiresAtMillis - System.currentTimeMillis()));
        }
    }

    private void signOut() {
        if (repository == null) return;
        main.removeCallbacks(expiryCheck);
        setBusy(true);
        repository.logout((user, expiry, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (error == 0) openLogin();
            else {
                setBusy(false);
                profileContent.setVisibility(View.GONE);
                textSession.setText(R.string.profile_unavailable);
                textError.setText(error);
                textError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void openLogin() {
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        buttonRefresh.setEnabled(!value);
        buttonLogout.setEnabled(!value);
        if (value) textError.setVisibility(View.GONE);
    }

    @Override
    protected void onStop() {
        visible = false;
        main.removeCallbacks(expiryCheck);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(expiryCheck);
        if (repository != null) repository.close();
        super.onDestroy();
    }
}
