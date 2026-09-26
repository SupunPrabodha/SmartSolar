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
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.UserResponse;
import com.smartsolar.mobile.data.repository.AuthRepository;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import com.smartsolar.mobile.ui.auth.LoginActivity;
import com.smartsolar.mobile.ui.common.WorkspaceChrome;
import com.smartsolar.mobile.ui.reservations.CurrentBookingsActivity;
import com.smartsolar.mobile.ui.reservations.QrScannerActivity;
import com.smartsolar.mobile.ui.reservation.CreateReservationActivity;
import com.smartsolar.mobile.util.MobileNavigation.Destination;
import com.smartsolar.mobile.util.ReservationUiUtils;
import com.smartsolar.mobile.util.SessionManager;

/** Compact, server-verified workspace with the existing session and live reservation metrics. */
public final class HomeActivity extends AppCompatActivity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable expiryCheck = this::signOut;
    private AuthRepository repository;
    private ReservationRepository reservationRepository;
    private TextView textError, textSession, textPendingCount, textApprovedFutureCount, textMetricsStatus;
    private View profileContent, progress;
    private Button buttonRefresh, buttonLogout;
    private boolean busy, visible;
    private long expiresAtMillis;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); EdgeToEdge.enable(this); setContentView(R.layout.activity_home);
        textError = findViewById(R.id.textError); textSession = findViewById(R.id.textSession);
        profileContent = findViewById(R.id.profileContent); progress = findViewById(R.id.progress);
        buttonRefresh = findViewById(R.id.buttonRefresh); buttonLogout = findViewById(R.id.buttonLogout);
        textPendingCount = findViewById(R.id.textPendingCount); textApprovedFutureCount = findViewById(R.id.textApprovedFutureCount);
        textMetricsStatus = findViewById(R.id.textMetricsStatus);
        SessionManager sessions = new SessionManager(this); expiresAtMillis = sessions.getExpiresAtMillis();
        try {
            ApiService api = RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
            repository = new AuthRepository(api, sessions); reservationRepository = new ReservationRepository(api);
        } catch (IllegalArgumentException exception) { openLogin(); return; }
        buttonRefresh.setOnClickListener(v -> restore()); buttonLogout.setOnClickListener(v -> signOut());
        findViewById(R.id.buttonCurrentBookings).setOnClickListener(v -> WorkspaceChrome.navigate(this, Destination.BOOKINGS));
        findViewById(R.id.buttonPendingBookings).setOnClickListener(v -> startActivity(new Intent(this, CurrentBookingsActivity.class).putExtra("pendingView", true)));
        findViewById(R.id.buttonScanTransaction).setOnClickListener(v -> startActivity(new Intent(this, QrScannerActivity.class)));
        findViewById(R.id.buttonModuleTwo).setOnClickListener(v -> startActivity(new Intent(this, CreateReservationActivity.class)));
    }
    @Override protected void onPostCreate(Bundle state) { super.onPostCreate(state); WorkspaceChrome.attach(this, getString(R.string.brand_name), Destination.HOME); }
    @Override protected void onStart() { super.onStart(); visible = true; scheduleExpiry(); restore(); }
    private void restore() {
        if (repository == null || busy) return;
        setBusy(true); profileContent.setVisibility(View.GONE); textSession.setText(R.string.checking_session);
        repository.restore(this::showResult);
    }
    private void showResult(UserResponse user, long expiry, int error) {
        if (isFinishing() || isDestroyed()) return;
        setBusy(false); textError.setVisibility(error == 0 ? View.GONE : View.VISIBLE);
        if (error != 0) textError.setText(error);
        if (user == null) {
            profileContent.setVisibility(View.GONE);
            if (error == 0 || error == R.string.session_expired || error == R.string.mobile_role_not_supported) openLogin();
            else { textSession.setText(R.string.profile_unavailable); scheduleExpiry(); }
            return;
        }
        expiresAtMillis = expiry; profileContent.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.textWelcome)).setText(ReservationUiUtils.greeting() + ", " + user.getFullName());
        ReservationUiUtils.formatStatusBadge(findViewById(R.id.textAccountStatus), user.getStatus());
        textSession.setText(getString(R.string.profile_verified, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date())));
        boolean operator = "GridOperator".equals(user.getRole());
        ((TextView) findViewById(R.id.homeDescription)).setText(operator ? R.string.operator_home_description : R.string.prosumer_home_description);
        findViewById(R.id.buttonScanTransaction).setVisibility(operator ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonModuleTwo).setVisibility(operator ? View.GONE : View.VISIBLE);
        loadDashboardSummary(); scheduleExpiry();
    }
    private void loadDashboardSummary() {
        if (reservationRepository == null) return;
        textPendingCount.setText("—"); textApprovedFutureCount.setText("—"); textMetricsStatus.setText(R.string.working);
        reservationRepository.getDashboardSummary((summary, error, status) -> {
            if (isFinishing() || isDestroyed()) return;
            if (summary != null) {
                textPendingCount.setText(String.valueOf(summary.getPendingReservations()));
                textApprovedFutureCount.setText(String.valueOf(summary.getApprovedFutureReservations()));
                textMetricsStatus.setText(getString(R.string.generated_at, ReservationUiUtils.formatTime(summary.getGeneratedAtUtc())));
            } else if (status == 401) signOut();
            else textMetricsStatus.setText(error != 0 ? error : R.string.load_failed);
        });
    }
    private void scheduleExpiry() {
        main.removeCallbacks(expiryCheck);
        if (visible && expiresAtMillis > 0) main.postDelayed(expiryCheck, Math.max(0, expiresAtMillis - System.currentTimeMillis()));
    }
    private void signOut() {
        if (repository == null) return;
        main.removeCallbacks(expiryCheck); setBusy(true);
        repository.logout((user, expiry, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (error == 0) openLogin();
            else { setBusy(false); profileContent.setVisibility(View.GONE); textSession.setText(R.string.profile_unavailable); textError.setText(error); textError.setVisibility(View.VISIBLE); }
        });
    }
    private void openLogin() {
        startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish();
    }
    private void setBusy(boolean value) {
        busy = value; progress.setVisibility(value ? View.VISIBLE : View.GONE);
        buttonRefresh.setEnabled(!value); buttonLogout.setEnabled(!value); if (value) textError.setVisibility(View.GONE);
    }
    @Override protected void onStop() { visible = false; main.removeCallbacks(expiryCheck); super.onStop(); }
    @Override protected void onDestroy() {
        main.removeCallbacks(expiryCheck); if (repository != null) repository.close();
        if (reservationRepository != null) reservationRepository.close(); super.onDestroy();
    }
}
