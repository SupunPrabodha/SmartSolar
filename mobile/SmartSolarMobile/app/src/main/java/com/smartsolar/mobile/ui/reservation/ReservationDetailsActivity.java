package com.smartsolar.mobile.ui.reservation;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.data.repository.ReservationError;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import com.smartsolar.mobile.ui.auth.LoginActivity;
import com.smartsolar.mobile.util.ReservationUiUtils;

public final class ReservationDetailsActivity extends AppCompatActivity {
    public static final String EXTRA_RESERVATION_ID = "com.smartsolar.mobile.RESERVATION_ID";
    private static final Gson GSON = new Gson();

    private ReservationRepository repository;
    private ReservationResponse currentReservation;
    private String reservationId;

    private View layoutLookup;
    private View cardDetails;
    private View layoutActions;
    private EditText editLookupReservationId;
    private Button buttonLookup;
    private TextView textError;
    private TextView textSuccess;
    private ProgressBar progress;

    private TextView textDetailsReservationId;
    private TextView textDetailsStatus;
    private TextView textDetailsStationId;
    private TextView textDetailsSlotId;
    private TextView textDetailsEnergy;
    private TextView textDetailsStart;
    private TextView textDetailsEnd;
    private TextView textDetailsCutoff;
    private TextView textDetailsRestriction;

    private Button buttonModify;
    private Button buttonCancel;
    private Button buttonRefreshDetails;
    private Button buttonBackHome;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reservation_details);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailsRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        layoutLookup = findViewById(R.id.layoutLookup);
        cardDetails = findViewById(R.id.cardDetails);
        layoutActions = findViewById(R.id.layoutActions);
        editLookupReservationId = findViewById(R.id.editLookupReservationId);
        buttonLookup = findViewById(R.id.buttonLookup);
        textError = findViewById(R.id.textError);
        textSuccess = findViewById(R.id.textSuccess);
        progress = findViewById(R.id.progress);

        textDetailsReservationId = findViewById(R.id.textDetailsReservationId);
        textDetailsStatus = findViewById(R.id.textDetailsStatus);
        textDetailsStationId = findViewById(R.id.textDetailsStationId);
        textDetailsSlotId = findViewById(R.id.textDetailsSlotId);
        textDetailsEnergy = findViewById(R.id.textDetailsEnergy);
        textDetailsStart = findViewById(R.id.textDetailsStart);
        textDetailsEnd = findViewById(R.id.textDetailsEnd);
        textDetailsCutoff = findViewById(R.id.textDetailsCutoff);
        textDetailsRestriction = findViewById(R.id.textDetailsRestriction);

        buttonModify = findViewById(R.id.buttonModify);
        buttonCancel = findViewById(R.id.buttonCancel);
        buttonRefreshDetails = findViewById(R.id.buttonRefreshDetails);
        buttonBackHome = findViewById(R.id.buttonBackHome);

        try {
            ApiService api = RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
            repository = new ReservationRepository(api);
        } catch (IllegalArgumentException e) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        reservationId = getIntent().getStringExtra(EXTRA_RESERVATION_ID);
        if (reservationId != null && !reservationId.trim().isEmpty()) {
            layoutLookup.setVisibility(View.GONE);
            fetchReservation(reservationId.trim());
        } else {
            layoutLookup.setVisibility(View.VISIBLE);
        }

        buttonLookup.setOnClickListener(v -> {
            String input = editLookupReservationId.getText() != null ? editLookupReservationId.getText().toString().trim() : "";
            if (input.isEmpty()) {
                showError(getString(R.string.error_res_id_required));
                return;
            }
            reservationId = input;
            fetchReservation(reservationId);
        });

        buttonModify.setOnClickListener(v -> {
            if (currentReservation == null) return;
            Intent intent = new Intent(this, ModifyReservationActivity.class);
            intent.putExtra(ModifyReservationActivity.EXTRA_RESERVATION_JSON, GSON.toJson(currentReservation));
            startActivity(intent);
        });

        buttonCancel.setOnClickListener(v -> showCancelConfirmationDialog());
        buttonRefreshDetails.setOnClickListener(v -> {
            if (reservationId != null) fetchReservation(reservationId);
        });
        buttonBackHome.setOnClickListener(v -> finish());
    }

    private void fetchReservation(String id) {
        if (busy || repository == null) return;
        setBusy(true);
        textSuccess.setVisibility(View.GONE);

        repository.getReservation(id, new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                displayReservation(result);
            }

            @Override
            public void onError(ReservationError error) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                if (error.isSessionExpired()) {
                    startActivity(new Intent(ReservationDetailsActivity.this, LoginActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                    return;
                }
                showError(error.getMessage());
                cardDetails.setVisibility(View.GONE);
                layoutActions.setVisibility(View.GONE);
                if (reservationId == null || reservationId.isEmpty()) {
                    layoutLookup.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void displayReservation(ReservationResponse res) {
        currentReservation = res;
        textError.setVisibility(View.GONE);
        cardDetails.setVisibility(View.VISIBLE);
        layoutActions.setVisibility(View.VISIBLE);

        textDetailsReservationId.setText(res.getReservationId());
        textDetailsStatus.setText(res.getStatus());
        textDetailsStationId.setText(res.getStationId());
        textDetailsSlotId.setText(res.getSlotId());
        textDetailsEnergy.setText(String.format("%s kWh", res.getEnergyAmountKwh()));
        textDetailsStart.setText(ReservationUiUtils.formatUtc(res.getScheduledStartAtUtc()));
        textDetailsEnd.setText(ReservationUiUtils.formatUtc(res.getScheduledEndAtUtc()));
        textDetailsCutoff.setText(ReservationUiUtils.formatCutoffUtc(res.getScheduledStartAtUtc()));

        boolean terminal = ReservationUiUtils.isTerminalStatus(res.getStatus());
        boolean cutoffPassed = ReservationUiUtils.isCutoffPassed(res.getScheduledStartAtUtc(), System.currentTimeMillis());

        if (terminal) {
            textDetailsRestriction.setText(getString(R.string.terminal_status_notice));
            textDetailsRestriction.setVisibility(View.VISIBLE);
            buttonModify.setEnabled(false);
            buttonCancel.setEnabled(false);
        } else if (cutoffPassed) {
            textDetailsRestriction.setText(getString(R.string.cutoff_passed_notice));
            textDetailsRestriction.setVisibility(View.VISIBLE);
            buttonModify.setEnabled(false);
            buttonCancel.setEnabled(false);
        } else {
            textDetailsRestriction.setVisibility(View.GONE);
            buttonModify.setEnabled(true);
            buttonCancel.setEnabled(true);
        }
    }

    private void showCancelConfirmationDialog() {
        if (currentReservation == null) return;
        String msg = getString(R.string.dialog_cancel_message,
                currentReservation.getReservationId(),
                String.valueOf(currentReservation.getEnergyAmountKwh()));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_cancel_title)
                .setMessage(msg)
                .setPositiveButton(R.string.dialog_confirm_cancellation, (dialog, which) -> executeCancel())
                .setNegativeButton(R.string.dialog_keep_reservation, null)
                .show();
    }

    private void executeCancel() {
        if (currentReservation == null || busy || repository == null) return;
        setBusy(true);

        repository.cancelReservation(currentReservation.getReservationId(), new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                displayReservation(result);
                textSuccess.setText(R.string.reservation_cancelled_banner);
                textSuccess.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(ReservationError error) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                if (error.isSessionExpired()) {
                    startActivity(new Intent(ReservationDetailsActivity.this, LoginActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                    return;
                }
                showError(error.getMessage());
            }
        });
    }

    private void showError(String message) {
        textError.setText(message);
        textError.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        buttonLookup.setEnabled(!value);
        buttonModify.setEnabled(!value && currentReservation != null && !ReservationUiUtils.isTerminalStatus(currentReservation.getStatus()));
        buttonCancel.setEnabled(!value && currentReservation != null && !ReservationUiUtils.isTerminalStatus(currentReservation.getStatus()));
        buttonRefreshDetails.setEnabled(!value);
        if (value) textError.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (reservationId != null && !reservationId.isEmpty()) {
            fetchReservation(reservationId);
        }
    }

    @Override
    protected void onDestroy() {
        if (repository != null) repository.close();
        super.onDestroy();
    }
}
