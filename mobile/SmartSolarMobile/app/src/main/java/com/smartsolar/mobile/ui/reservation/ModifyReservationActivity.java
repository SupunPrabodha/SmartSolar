package com.smartsolar.mobile.ui.reservation;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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

public final class ModifyReservationActivity extends AppCompatActivity {
    public static final String EXTRA_RESERVATION_JSON = "com.smartsolar.mobile.RESERVATION_JSON";
    private static final Gson GSON = new Gson();

    private ReservationRepository repository;
    private ReservationResponse existing;

    private View layoutEditForm;
    private View layoutReview;
    private EditText editSlotId;
    private EditText editEnergyAmount;
    private TextView textError;
    private TextView textWaiting;
    private TextView textCurrentId;
    private TextView textCurrentSlot;
    private TextView textCurrentEnergy;
    private TextView textReviewSlotId;
    private TextView textReviewEnergy;
    private ProgressBar progress;
    private Button buttonReview;
    private Button buttonConfirm;
    private Button buttonBackToForm;
    private Button buttonCancelEdit;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_modify_reservation);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.modifyRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        layoutEditForm = findViewById(R.id.layoutEditForm);
        layoutReview = findViewById(R.id.layoutReview);
        editSlotId = findViewById(R.id.editSlotId);
        editEnergyAmount = findViewById(R.id.editEnergyAmount);
        textError = findViewById(R.id.textError);
        textWaiting = findViewById(R.id.textWaiting);
        textCurrentId = findViewById(R.id.textCurrentId);
        textCurrentSlot = findViewById(R.id.textCurrentSlot);
        textCurrentEnergy = findViewById(R.id.textCurrentEnergy);
        textReviewSlotId = findViewById(R.id.textReviewSlotId);
        textReviewEnergy = findViewById(R.id.textReviewEnergy);
        progress = findViewById(R.id.progress);
        buttonReview = findViewById(R.id.buttonReview);
        buttonConfirm = findViewById(R.id.buttonConfirm);
        buttonBackToForm = findViewById(R.id.buttonBackToForm);
        buttonCancelEdit = findViewById(R.id.buttonCancelEdit);

        String json = getIntent().getStringExtra(EXTRA_RESERVATION_JSON);
        if (json != null) {
            existing = GSON.fromJson(json, ReservationResponse.class);
        }

        if (existing == null) {
            finish();
            return;
        }

        try {
            ApiService api = RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
            repository = new ReservationRepository(api);
        } catch (IllegalArgumentException e) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        textCurrentId.setText(getString(R.string.label_reservation_id) + ": " + existing.getReservationId());
        textCurrentSlot.setText(getString(R.string.label_slot_id) + ": " + existing.getSlotId());
        textCurrentEnergy.setText(getString(R.string.label_energy_amount) + ": " + existing.getEnergyAmountKwh() + " kWh");

        editSlotId.setText(existing.getSlotId());
        editEnergyAmount.setText(String.valueOf(existing.getEnergyAmountKwh()));

        buttonReview.setOnClickListener(v -> onReviewClicked());
        buttonBackToForm.setOnClickListener(v -> onBackToFormClicked());
        buttonConfirm.setOnClickListener(v -> onConfirmClicked());
        buttonCancelEdit.setOnClickListener(v -> finish());
    }

    private void onReviewClicked() {
        textError.setVisibility(View.GONE);
        String slotId = editSlotId.getText() != null ? editSlotId.getText().toString().trim() : "";
        String energyStr = editEnergyAmount.getText() != null ? editEnergyAmount.getText().toString().trim() : "";

        if (!ReservationUiUtils.isValidGuid(slotId)) {
            showError(getString(R.string.error_slot_required));
            editSlotId.requestFocus();
            return;
        }
        if (!ReservationUiUtils.isValidEnergy(energyStr)) {
            showError(getString(R.string.error_energy_positive));
            editEnergyAmount.requestFocus();
            return;
        }

        textReviewSlotId.setText(slotId);
        textReviewEnergy.setText(energyStr + " kWh");
        layoutEditForm.setVisibility(View.GONE);
        layoutReview.setVisibility(View.VISIBLE);
    }

    private void onBackToFormClicked() {
        textError.setVisibility(View.GONE);
        layoutReview.setVisibility(View.GONE);
        layoutEditForm.setVisibility(View.VISIBLE);
    }

    private void onConfirmClicked() {
        if (busy || repository == null) return;
        setBusy(true);
        String slotId = editSlotId.getText().toString().trim();
        double energy = Double.parseDouble(editEnergyAmount.getText().toString().trim());

        repository.updateReservation(existing.getReservationId(), slotId, energy, new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                Intent intent = new Intent(ModifyReservationActivity.this, ReservationSummaryActivity.class);
                intent.putExtra(ReservationSummaryActivity.EXTRA_RESERVATION_JSON, GSON.toJson(result));
                intent.putExtra(ReservationSummaryActivity.EXTRA_MODE, ReservationSummaryActivity.MODE_UPDATED);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(ReservationError error) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                if (error.isSessionExpired()) {
                    startActivity(new Intent(ModifyReservationActivity.this, LoginActivity.class)
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
        textWaiting.setVisibility(value ? View.VISIBLE : View.GONE);
        buttonConfirm.setEnabled(!value);
        buttonBackToForm.setEnabled(!value);
        buttonReview.setEnabled(!value);
        if (value) textError.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        if (repository != null) repository.close();
        super.onDestroy();
    }
}
