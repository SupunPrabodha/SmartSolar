package com.smartsolar.mobile.ui.reservations;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.ReservationCompletionResponse;
import com.smartsolar.mobile.data.repository.ReservationRepository;

/**
 * Step 9 & 10: Authoritative QR verification result screen and final transaction completion flow.
 * Displays trusted reservation information and enables Grid Operator to execute completion.
 */
public final class QrVerificationResultActivity extends AppCompatActivity {
    public static final String EXTRA_QR_PAYLOAD = "extra_verified_qr_payload";
    public static final String EXTRA_RESERVATION_ID = "extra_verified_reservation_id";
    public static final String EXTRA_PROSUMER_NIC = "extra_verified_prosumer_nic";
    public static final String EXTRA_STATION_ID = "extra_verified_station_id";
    public static final String EXTRA_SLOT_ID = "extra_verified_slot_id";
    public static final String EXTRA_ENERGY_AMOUNT = "extra_verified_energy_amount";
    public static final String EXTRA_SCHEDULE_START = "extra_verified_schedule_start";
    public static final String EXTRA_SCHEDULE_END = "extra_verified_schedule_end";
    public static final String EXTRA_STATUS = "extra_verified_status";
    public static final String EXTRA_QR_ISSUED_AT = "extra_verified_qr_issued_at";
    public static final String EXTRA_ELIGIBLE_FOR_COMPLETION = "extra_verified_eligible_for_completion";

    private ReservationRepository repository;
    private String qrPayload;
    private String reservationId;
    private double energyAmount;
    private String currentStatus;

    private TextView textStatus;
    private TextView textCompletionStatus;
    private TextView textCompletedAt;
    private TextView textCompletedBy;
    private MaterialButton buttonCompleteTransfer;
    private ProgressBar progressCompleteTransfer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_qr_verification_result);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.verificationRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        qrPayload = getIntent().getStringExtra(EXTRA_QR_PAYLOAD);
        reservationId = getIntent().getStringExtra(EXTRA_RESERVATION_ID);
        String prosumerNic = getIntent().getStringExtra(EXTRA_PROSUMER_NIC);
        String stationId = getIntent().getStringExtra(EXTRA_STATION_ID);
        String slotId = getIntent().getStringExtra(EXTRA_SLOT_ID);
        energyAmount = getIntent().getDoubleExtra(EXTRA_ENERGY_AMOUNT, 0.0);
        String scheduleStart = getIntent().getStringExtra(EXTRA_SCHEDULE_START);
        String scheduleEnd = getIntent().getStringExtra(EXTRA_SCHEDULE_END);
        currentStatus = getIntent().getStringExtra(EXTRA_STATUS);
        String qrIssuedAt = getIntent().getStringExtra(EXTRA_QR_ISSUED_AT);
        boolean eligibleForCompletion = getIntent().getBooleanExtra(EXTRA_ELIGIBLE_FOR_COMPLETION, false);

        TextView textReservationId = findViewById(R.id.textVerifiedReservationId);
        textStatus = findViewById(R.id.textVerifiedStatus);
        TextView textProsumer = findViewById(R.id.textVerifiedProsumer);
        TextView textStationSlot = findViewById(R.id.textVerifiedStationSlot);
        TextView textSchedule = findViewById(R.id.textVerifiedSchedule);
        TextView textEnergy = findViewById(R.id.textVerifiedEnergy);
        TextView textQrIssuedAt = findViewById(R.id.textVerifiedQrIssuedAt);

        textCompletionStatus = findViewById(R.id.textCompletionStatus);
        textCompletedAt = findViewById(R.id.textCompletedAt);
        textCompletedBy = findViewById(R.id.textCompletedBy);
        buttonCompleteTransfer = findViewById(R.id.buttonCompleteTransfer);
        progressCompleteTransfer = findViewById(R.id.progressCompleteTransfer);

        String idSnippet = reservationId != null && reservationId.length() > 8
                ? reservationId.substring(0, 8) + "…"
                : String.valueOf(reservationId);
        textReservationId.setText("Reservation: " + idSnippet);

        com.smartsolar.mobile.util.ReservationUiUtils.formatStatusBadge(textStatus, currentStatus);

        if (prosumerNic != null && !prosumerNic.trim().isEmpty()) {
            textProsumer.setText(getString(R.string.prosumer_nic_label, prosumerNic));
            textProsumer.setVisibility(View.VISIBLE);
        } else {
            textProsumer.setVisibility(View.GONE);
        }

        String station = stationId != null ? stationId : "—";
        textStationSlot.setText("Station: " + station);

        String startFormatted = com.smartsolar.mobile.util.ReservationUiUtils.formatUtc(scheduleStart);
        textSchedule.setText("Starts: " + startFormatted);

        textEnergy.setText(String.format(java.util.Locale.US, "%.1f kWh", energyAmount));

        if (qrIssuedAt != null && !qrIssuedAt.isEmpty()) {
            textQrIssuedAt.setText(getString(R.string.qr_issued_at_label, qrIssuedAt));
            textQrIssuedAt.setVisibility(View.VISIBLE);
        } else {
            textQrIssuedAt.setVisibility(View.GONE);
        }

        try {
            ApiService api = RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
            repository = new ReservationRepository(api);
        } catch (IllegalArgumentException e) {
            repository = null;
        }

        // Configure completion button visibility and eligibility
        boolean canComplete = "Approved".equalsIgnoreCase(currentStatus) && eligibleForCompletion;
        buttonCompleteTransfer.setEnabled(canComplete);
        buttonCompleteTransfer.setVisibility(canComplete ? View.VISIBLE : View.GONE);

        buttonCompleteTransfer.setOnClickListener(v -> showCompletionConfirmation());

        MaterialButton buttonScanAnother = findViewById(R.id.buttonScanAnother);
        MaterialButton buttonDone = findViewById(R.id.buttonDone);

        buttonScanAnother.setOnClickListener(v -> {
            startActivity(new Intent(this, QrScannerActivity.class));
            finish();
        });

        buttonDone.setOnClickListener(v -> finish());
    }

    private void showCompletionConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_completion_title)
                .setMessage(getString(R.string.confirm_completion_message, energyAmount, reservationId != null ? reservationId : ""))
                .setPositiveButton(R.string.confirm_action, (dialog, which) -> executeCompletion())
                .setNegativeButton(R.string.cancel_action, null)
                .show();
    }

    private void executeCompletion() {
        if (repository == null || qrPayload == null || qrPayload.isEmpty()) return;

        setBusy(true);

        repository.completeTransfer(qrPayload, reservationId, (result, errorRes, statusCode) -> {
            if (isFinishing() || isDestroyed()) return;
            setBusy(false);

            if (result != null) {
                onCompletionSuccess(result);
            } else {
                String message;
                if (statusCode == 409) {
                    message = "Reservation has already been completed or is no longer in an Approved state.";
                } else if (statusCode == 403) {
                    message = getString(R.string.access_denied);
                } else if (statusCode == 404) {
                    message = "QR reference is invalid or expired.";
                } else {
                    message = getString(errorRes != 0 ? errorRes : R.string.load_failed);
                }
                new AlertDialog.Builder(this)
                        .setTitle(R.string.error_title)
                        .setMessage(message)
                        .setPositiveButton(R.string.close, null)
                        .show();
            }
        });
    }

    private void onCompletionSuccess(ReservationCompletionResponse response) {
        currentStatus = response.getStatus();
        textStatus.setText(response.getStatus());
        textCompletionStatus.setText(R.string.completion_success_message);

        if (response.getCompletedAtUtc() != null) {
            textCompletedAt.setText(getString(R.string.completed_at_label, response.getCompletedAtUtc()));
            textCompletedAt.setVisibility(View.VISIBLE);
        }
        if (response.getCompletedByOperatorNic() != null) {
            textCompletedBy.setText(getString(R.string.completed_by_label, response.getCompletedByOperatorNic()));
            textCompletedBy.setVisibility(View.VISIBLE);
        }

        buttonCompleteTransfer.setVisibility(View.GONE);
        Toast.makeText(this, R.string.transaction_completed_subtitle, Toast.LENGTH_LONG).show();
    }

    private void setBusy(boolean busy) {
        progressCompleteTransfer.setVisibility(busy ? View.VISIBLE : View.GONE);
        buttonCompleteTransfer.setEnabled(!busy);
    }

    @Override
    protected void onDestroy() {
        if (repository != null) {
            repository.close();
        }
        super.onDestroy();
    }
}
