package com.smartsolar.mobile.ui.reservations;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.ReservationVerificationResponse;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import java.util.Collections;

/**
 * Step 8: Native Android Grid Operator QR scanner screen.
 * Scans the opaque transaction QR reference and forwards it to the API for authoritative verification.
 */
public final class QrScannerActivity extends AppCompatActivity {
    private static final String PAYLOAD_PREFIX = "SMG1.";

    private DecoratedBarcodeView barcodeScanner;
    private LinearLayout layoutPermissionDenied;
    private LinearLayout layoutVerifying;
    private LinearLayout layoutScanError;
    private TextView textScanErrorMessage;
    private MaterialButton buttonGrantPermission;
    private MaterialButton buttonScanAgain;
    private ImageButton buttonBack;

    private ReservationRepository repository;
    private boolean isScanning;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    layoutPermissionDenied.setVisibility(View.GONE);
                    startScanner();
                } else {
                    layoutPermissionDenied.setVisibility(View.VISIBLE);
                }
            });

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result == null || result.getText() == null || !isScanning) return;
            handleScannedPayload(result.getText());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);

        barcodeScanner = findViewById(R.id.barcodeScanner);
        layoutPermissionDenied = findViewById(R.id.layoutPermissionDenied);
        layoutVerifying = findViewById(R.id.layoutVerifying);
        layoutScanError = findViewById(R.id.layoutScanError);
        textScanErrorMessage = findViewById(R.id.textScanErrorMessage);
        buttonGrantPermission = findViewById(R.id.buttonGrantPermission);
        buttonScanAgain = findViewById(R.id.buttonScanAgain);
        buttonBack = findViewById(R.id.buttonBack);

        barcodeScanner.getBarcodeView().setDecoderFactory(
                new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));

        try {
            ApiService api = RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
            repository = new ReservationRepository(api);
        } catch (IllegalArgumentException e) {
            finish();
            return;
        }

        buttonBack.setOnClickListener(v -> finish());
        buttonGrantPermission.setOnClickListener(v -> checkAndRequestPermission());
        buttonScanAgain.setOnClickListener(v -> resumeScanning());

        checkAndRequestPermission();
    }

    private void checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            layoutPermissionDenied.setVisibility(View.GONE);
            startScanner();
        } else {
            layoutPermissionDenied.setVisibility(View.VISIBLE);
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startScanner() {
        isScanning = true;
        barcodeScanner.decodeSingle(callback);
        barcodeScanner.resume();
    }

    private void resumeScanning() {
        layoutScanError.setVisibility(View.GONE);
        layoutVerifying.setVisibility(View.GONE);
        startScanner();
    }

    private void handleScannedPayload(String scannedText) {
        isScanning = false;
        barcodeScanner.pause();

        // Lightweight format validation before dispatching to API
        String payload = scannedText != null ? scannedText.trim() : "";
        if (payload.isEmpty() || !payload.startsWith(PAYLOAD_PREFIX) || payload.length() > 256) {
            showScanError(getString(R.string.invalid_qr_format));
            return;
        }

        layoutVerifying.setVisibility(View.VISIBLE);

        repository.verifyQr(payload, (result, errorRes, statusCode) -> {
            if (isFinishing() || isDestroyed()) return;
            layoutVerifying.setVisibility(View.GONE);

            if (result != null) {
                openVerificationResult(result, payload);
            } else {
                String message;
                if (statusCode == 404) {
                    message = "QR code reference is invalid, expired, or was revoked.";
                } else if (statusCode == 409) {
                    message = getString(errorRes != 0 ? errorRes : R.string.qr_changed);
                } else if (statusCode == 403) {
                    message = getString(R.string.access_denied);
                } else if (statusCode == 400) {
                    message = getString(R.string.invalid_qr_format);
                } else {
                    message = getString(errorRes != 0 ? errorRes : R.string.qr_verification_failed);
                }
                showScanError(message);
            }
        });
    }

    private void showScanError(String message) {
        textScanErrorMessage.setText(message);
        layoutScanError.setVisibility(View.VISIBLE);
    }

    private void openVerificationResult(ReservationVerificationResponse response, String qrPayload) {
        Intent intent = new Intent(this, QrVerificationResultActivity.class);
        intent.putExtra(QrVerificationResultActivity.EXTRA_QR_PAYLOAD, qrPayload);
        intent.putExtra(QrVerificationResultActivity.EXTRA_RESERVATION_ID, response.getReservationId());
        intent.putExtra(QrVerificationResultActivity.EXTRA_PROSUMER_NIC, response.getProsumerNic());
        intent.putExtra(QrVerificationResultActivity.EXTRA_STATION_ID, response.getStationId());
        intent.putExtra(QrVerificationResultActivity.EXTRA_SLOT_ID, response.getSlotId());
        intent.putExtra(QrVerificationResultActivity.EXTRA_ENERGY_AMOUNT, response.getEnergyAmountKwh());
        intent.putExtra(QrVerificationResultActivity.EXTRA_SCHEDULE_START, response.getScheduledStartAtUtc());
        intent.putExtra(QrVerificationResultActivity.EXTRA_SCHEDULE_END, response.getScheduledEndAtUtc());
        intent.putExtra(QrVerificationResultActivity.EXTRA_STATUS, response.getStatus());
        intent.putExtra(QrVerificationResultActivity.EXTRA_QR_ISSUED_AT, response.getQrIssuedAtUtc());
        intent.putExtra(QrVerificationResultActivity.EXTRA_ELIGIBLE_FOR_COMPLETION, response.isEligibleForCompletion());
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            barcodeScanner.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeScanner.pause();
    }

    @Override
    protected void onDestroy() {
        if (repository != null) {
            repository.close();
        }
        super.onDestroy();
    }
    @Override protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        com.smartsolar.mobile.ui.common.WorkspaceChrome.attach(this, getString(R.string.scan_transaction_qr), null);
    }
}
