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

public final class CreateReservationActivity extends AppCompatActivity {
    private static final Gson GSON = new Gson();

    private ReservationRepository repository;
    private View layoutForm;
    private View layoutReview;
    private EditText editSlotId;
    private EditText editEnergyAmount;
    private TextView textError;
    private TextView textWaiting;
    private TextView textReviewSlotId;
    private TextView textReviewEnergy;
    private ProgressBar progress;
    private Button buttonReview;
    private Button buttonConfirm;
    private Button buttonBackToForm;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_reservation);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.createRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        layoutForm = findViewById(R.id.layoutForm);
        layoutReview = findViewById(R.id.layoutReview);
        editSlotId = findViewById(R.id.editSlotId);
        editEnergyAmount = findViewById(R.id.editEnergyAmount);
        textError = findViewById(R.id.textError);
        textWaiting = findViewById(R.id.textWaiting);
        textReviewSlotId = findViewById(R.id.textReviewSlotId);
        textReviewEnergy = findViewById(R.id.textReviewEnergy);
        progress = findViewById(R.id.progress);
        buttonReview = findViewById(R.id.buttonReview);
        buttonConfirm = findViewById(R.id.buttonConfirm);
        buttonBackToForm = findViewById(R.id.buttonBackToForm);

        try {
            ApiService api = RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
            repository = new ReservationRepository(api);
        } catch (IllegalArgumentException e) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        buttonReview.setOnClickListener(v -> onReviewClicked());
        buttonBackToForm.setOnClickListener(v -> onBackToFormClicked());
        buttonConfirm.setOnClickListener(v -> onConfirmClicked());
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
        layoutForm.setVisibility(View.GONE);
        layoutReview.setVisibility(View.VISIBLE);
    }

    private void onBackToFormClicked() {
        textError.setVisibility(View.GONE);
        layoutReview.setVisibility(View.GONE);
        layoutForm.setVisibility(View.VISIBLE);
    }

    private void onConfirmClicked() {
        if (busy || repository == null) return;
        setBusy(true);
        String slotId = editSlotId.getText().toString().trim();
        double energy = Double.parseDouble(editEnergyAmount.getText().toString().trim());

        repository.createReservation(slotId, energy, new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                Intent intent = new Intent(CreateReservationActivity.this, ReservationSummaryActivity.class);
                intent.putExtra(ReservationSummaryActivity.EXTRA_RESERVATION_JSON, GSON.toJson(result));
                intent.putExtra(ReservationSummaryActivity.EXTRA_MODE, ReservationSummaryActivity.MODE_CREATED);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(ReservationError error) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                if (error.isSessionExpired()) {
                    startActivity(new Intent(CreateReservationActivity.this, LoginActivity.class)
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
