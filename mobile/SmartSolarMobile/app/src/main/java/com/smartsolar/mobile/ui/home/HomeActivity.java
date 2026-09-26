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
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.UserResponse;
import com.smartsolar.mobile.data.repository.AuthRepository;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import com.smartsolar.mobile.ui.account.AccountActivity;
import com.smartsolar.mobile.ui.auth.LoginActivity;
import com.smartsolar.mobile.ui.reservations.BookingHistoryActivity;
import com.smartsolar.mobile.ui.reservations.CurrentBookingsActivity;
import com.smartsolar.mobile.ui.reservations.QrScannerActivity;
import com.smartsolar.mobile.ui.stations.StationDiscoveryActivity;
import com.smartsolar.mobile.util.MobileAccess;
import com.smartsolar.mobile.util.SessionManager;

import java.text.DateFormat;
import java.util.Date;

/**
 * Common authenticated home for station discovery, account access,
 * reservation dashboards, booking navigation and GridOperator QR operations.
 */
public final class HomeActivity extends AppCompatActivity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable expiryCheck = this::signOut;

    private AuthRepository repository;
    private ReservationRepository reservationRepository;

    private TextView textError;
    private TextView textSession;
    private View profileContent;
    private View progress;
    private Button buttonRefresh;
    private Button buttonLogout;

    private TextView textPendingCount;
    private TextView textApprovedFutureCount;
    private TextView textMetricsStatus;

    private Button buttonCurrentBookings;
    private Button buttonBookingHistory;
    private Button buttonScanTransaction;

    private View cardScanTransaction;
    private View cardModuleTwo;

    private boolean busy;
    private boolean visible;
    private long expiresAtMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.homeRoot),
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    view.setPadding(
                            bars.left,
                            bars.top,
                            bars.right,
                            bars.bottom
                    );

                    return insets;
                }
        );

        textError = findViewById(R.id.textError);
        textSession = findViewById(R.id.textSession);
        profileContent = findViewById(R.id.profileContent);
        progress = findViewById(R.id.progress);
        buttonRefresh = findViewById(R.id.buttonRefresh);
        buttonLogout = findViewById(R.id.buttonLogout);

        textPendingCount = findViewById(R.id.textPendingCount);
        textApprovedFutureCount = findViewById(R.id.textApprovedFutureCount);
        textMetricsStatus = findViewById(R.id.textMetricsStatus);

        buttonCurrentBookings = findViewById(R.id.buttonCurrentBookings);
        buttonBookingHistory = findViewById(R.id.buttonBookingHistory);
        buttonScanTransaction = findViewById(R.id.buttonScanTransaction);

        cardScanTransaction = findViewById(R.id.cardScanTransaction);
        cardModuleTwo = findViewById(R.id.cardModuleTwo);

        if (cardScanTransaction != null) {
            cardScanTransaction.setVisibility(View.GONE);
        }

        ((TextView) findViewById(R.id.textEnvironment)).setText(
                BuildConfig.DEBUG
                        ? R.string.environment_development
                        : R.string.environment_release
        );

        SessionManager sessions = new SessionManager(this);
        expiresAtMillis = sessions.getExpiresAtMillis();

        try {
            ApiService api = RetrofitClient.create(
                    this,
                    BuildConfig.API_BASE_URL,
                    BuildConfig.DEBUG
            );

            repository = new AuthRepository(api, sessions);
            reservationRepository = new ReservationRepository(api);

        } catch (IllegalArgumentException exception) {
            openLogin();
            return;
        }

        buttonRefresh.setOnClickListener(view -> restore());
        buttonLogout.setOnClickListener(view -> signOut());

        /*
         * Member 1:
         * Station discovery / Google Maps entry point.
         */
        View buttonFindStations = findViewById(R.id.buttonFindStations);

        if (buttonFindStations != null) {
            buttonFindStations.setOnClickListener(view ->
                    startActivity(
                            new Intent(
                                    this,
                                    StationDiscoveryActivity.class
                            )
                    )
            );
        }

        /*
         * Member 4:
         * Current and historical booking views.
         */
        if (buttonCurrentBookings != null) {
            buttonCurrentBookings.setOnClickListener(view ->
                    startActivity(
                            new Intent(
                                    this,
                                    CurrentBookingsActivity.class
                            )
                    )
            );
        }

        if (buttonBookingHistory != null) {
            buttonBookingHistory.setOnClickListener(view ->
                    startActivity(
                            new Intent(
                                    this,
                                    BookingHistoryActivity.class
                            )
                    )
            );
        }

        /*
         * Member 4:
         * GridOperator QR scanner.
         * Role-based visibility is applied after the profile is restored.
         */
        if (buttonScanTransaction != null) {
            buttonScanTransaction.setOnClickListener(view ->
                    startActivity(
                            new Intent(
                                    this,
                                    QrScannerActivity.class
                            )
                    )
            );
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        visible = true;
        scheduleExpiry();
        restore();
    }

    /**
     * Restore and verify the current authenticated profile.
     */
    private void restore() {
        if (repository == null || busy) {
            return;
        }

        setBusy(true);
        profileContent.setVisibility(View.GONE);
        textSession.setText(R.string.checking_session);

        repository.restore(this::showResult);
    }

    /**
     * Update the authenticated workspace using the latest verified user.
     */
    private void showResult(
            UserResponse user,
            long expiry,
            int error
    ) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        setBusy(false);

        textError.setVisibility(
                error == 0 ? View.GONE : View.VISIBLE
        );

        if (error != 0) {
            textError.setText(error);
        }

        if (user == null) {
            profileContent.setVisibility(View.GONE);

            if (error == 0
                    || error == R.string.session_expired
                    || error == R.string.mobile_role_not_supported) {

                openLogin();

            } else {
                /*
                 * A cached profile never authorizes offline access.
                 */
                textSession.setText(R.string.profile_unavailable);
                scheduleExpiry();
            }

            return;
        }

        expiresAtMillis = expiry;

        profileContent.setVisibility(View.VISIBLE);

        ((TextView) findViewById(R.id.textWelcome)).setText(
                getString(
                        R.string.welcome_name,
                        user.getFullName()
                )
        );

        ((TextView) findViewById(R.id.textRole)).setText(
                getString(
                        R.string.role_value,
                        user.getRole()
                )
        );

        ((TextView) findViewById(R.id.textAccountStatus)).setText(
                getString(
                        R.string.status_value,
                        user.getStatus()
                )
        );

        ((TextView) findViewById(R.id.textExpiry)).setText(
                getString(
                        R.string.expiry_value,
                        DateFormat.getDateTimeInstance(
                                DateFormat.SHORT,
                                DateFormat.SHORT
                        ).format(new Date(expiry))
                )
        );

        textSession.setText(
                getString(
                        R.string.profile_verified,
                        DateFormat.getTimeInstance(
                                DateFormat.SHORT
                        ).format(new Date())
                )
        );

        boolean operator = "GridOperator".equals(user.getRole());

        /*
         * Member 1:
         * Station discovery remains available to supported mobile roles.
         *
         * Do not use the old R.string.operations value here. The merged
         * baseline uses Find Stations for the station-discovery entry point.
         */
        TextView moduleOne = findViewById(R.id.moduleOne);

        if (moduleOne != null) {
            moduleOne.setText(R.string.find_stations);
        }

        /*
         * Member 2:
         * Prosumer account/profile management.
         * GridOperators do not receive the Prosumer account-management entry.
         */
        View account = findViewById(R.id.buttonAccount);

        if (account != null) {
            account.setVisibility(
                    operator ? View.GONE : View.VISIBLE
            );

            if (!operator) {
                account.setOnClickListener(view ->
                        startActivity(
                                new Intent(
                                        this,
                                        AccountActivity.class
                                )
                                        .putExtra(
                                                "name",
                                                user.getFullName()
                                        )
                                        .putExtra(
                                                "email",
                                                user.getEmail()
                                        )
                                        .putExtra(
                                                "phone",
                                                user.getPhoneNumber()
                                        )
                        )
                );
            } else {
                account.setOnClickListener(null);
            }
        }

        /*
         * Member 3 / Member 4:
         * Role-specific module labels.
         */
        TextView moduleTwo = findViewById(R.id.moduleTwo);
        TextView moduleThree = findViewById(R.id.moduleThree);

        if (moduleTwo != null) {
            moduleTwo.setText(
                    operator
                            ? R.string.scan_transaction
                            : R.string.my_reservations
            );
        }

        if (moduleThree != null) {
            moduleThree.setText(
                    operator
                            ? R.string.transaction_history
                            : R.string.booking_history
            );
        }

        /*
         * Member 3:
         * Prosumer reservation-management entry.
         *
         * GridOperator uses the dedicated QR / operational controls instead.
         */
        Button buttonModuleTwo = findViewById(R.id.buttonModuleTwo);

        if (!operator) {
            if (cardModuleTwo != null) {
                cardModuleTwo.setVisibility(View.VISIBLE);
            }

            if (buttonModuleTwo != null) {
                buttonModuleTwo.setEnabled(true);
                buttonModuleTwo.setText(
                        R.string.manage_reservations
                );

                buttonModuleTwo.setOnClickListener(view ->
                        startActivity(
                                new Intent(
                                        this,
                                        com.smartsolar.mobile.ui.reservation
                                                .ReservationDetailsActivity.class
                                )
                        )
                );
            }

            if (moduleTwo != null) {
                moduleTwo.setOnClickListener(view ->
                        startActivity(
                                new Intent(
                                        this,
                                        com.smartsolar.mobile.ui.reservation
                                                .ReservationDetailsActivity.class
                                )
                        )
                );
            }

        } else {
            if (cardModuleTwo != null) {
                cardModuleTwo.setVisibility(View.GONE);
            }

            if (buttonModuleTwo != null) {
                buttonModuleTwo.setEnabled(false);
                buttonModuleTwo.setText(
                        R.string.coming_next
                );
                buttonModuleTwo.setOnClickListener(null);
            }

            if (moduleTwo != null) {
                moduleTwo.setOnClickListener(null);
            }
        }

        /*
         * Member 4:
         * QR scanning is restricted using the existing role helper.
         */
        boolean canScanTransactionQr =
                MobileAccess.canScanTransactionQr(
                        user.getRole()
                );

        if (cardScanTransaction != null) {
            cardScanTransaction.setVisibility(
                    canScanTransactionQr
                            ? View.VISIBLE
                            : View.GONE
            );
        }

        if (buttonScanTransaction != null) {
            buttonScanTransaction.setEnabled(
                    canScanTransactionQr
            );
        }

        /*
         * Member 4 dashboard counters.
         */
        loadDashboardSummary();

        scheduleExpiry();
    }

    /**
     * Load live reservation dashboard counts from the API.
     */
    private void loadDashboardSummary() {
        if (reservationRepository == null) {
            return;
        }

        reservationRepository.getDashboardSummary(
                (summary, errorRes, statusCode) -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (summary != null) {
                        if (textPendingCount != null) {
                            textPendingCount.setText(
                                    getString(
                                            R.string.pending_reservations_label
                                    )
                                            + ": "
                                            + summary.getPendingReservations()
                            );
                        }

                        if (textApprovedFutureCount != null) {
                            textApprovedFutureCount.setText(
                                    getString(
                                            R.string.approved_future_reservations_label
                                    )
                                            + ": "
                                            + summary.getApprovedFutureReservations()
                            );
                        }

                        if (textMetricsStatus != null
                                && summary.getGeneratedAtUtc() != null) {

                            textMetricsStatus.setText(
                                    getString(
                                            R.string.generated_at,
                                            summary.getGeneratedAtUtc()
                                    )
                            );
                        }

                    } else if (statusCode == 401) {
                        signOut();
                    }
                }
        );
    }

    /**
     * Schedule automatic logout when the current JWT expires.
     */
    private void scheduleExpiry() {
        main.removeCallbacks(expiryCheck);

        if (visible && expiresAtMillis > 0) {
            main.postDelayed(
                    expiryCheck,
                    Math.max(
                            0,
                            expiresAtMillis
                                    - System.currentTimeMillis()
                    )
            );
        }
    }

    /**
     * Clear the authenticated session and return to Login.
     */
    private void signOut() {
        if (repository == null) {
            return;
        }

        main.removeCallbacks(expiryCheck);
        setBusy(true);

        repository.logout((user, expiry, error) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (error == 0) {
                openLogin();

            } else {
                setBusy(false);
                profileContent.setVisibility(View.GONE);
                textSession.setText(R.string.profile_unavailable);
                textError.setText(error);
                textError.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * Return to the login activity and clear the authenticated task stack.
     */
    private void openLogin() {
        startActivity(
                new Intent(
                        this,
                        LoginActivity.class
                ).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
        );

        finish();
    }

    /**
     * Apply common loading-state behaviour.
     */
    private void setBusy(boolean value) {
        busy = value;

        progress.setVisibility(
                value ? View.VISIBLE : View.GONE
        );

        buttonRefresh.setEnabled(!value);
        buttonLogout.setEnabled(!value);

        if (value) {
            textError.setVisibility(View.GONE);
        }
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

        if (repository != null) {
            repository.close();
        }

        if (reservationRepository != null) {
            reservationRepository.close();
        }

        super.onDestroy();
    }
}