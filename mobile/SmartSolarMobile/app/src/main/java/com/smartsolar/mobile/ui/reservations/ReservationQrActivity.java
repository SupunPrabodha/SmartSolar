package com.smartsolar.mobile.ui.reservations;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.repository.ReservationRepository;

/**
 * Step 7: Secure transaction QR display screen for Approved reservations.
 * Requests a high-entropy opaque reference from the API and renders it locally as a QR bitmap.
 */
public final class ReservationQrActivity extends AppCompatActivity {
    public static final String EXTRA_RESERVATION_ID = "extra_reservation_id";
    public static final String EXTRA_PROSUMER_NIC = "extra_prosumer_nic";
    public static final String EXTRA_STATION_ID = "extra_station_id";
    public static final String EXTRA_SLOT_ID = "extra_slot_id";
    public static final String EXTRA_ENERGY_AMOUNT = "extra_energy_amount";
    public static final String EXTRA_SCHEDULE_START = "extra_schedule_start";
    public static final String EXTRA_SCHEDULE_END = "extra_schedule_end";
    public static final String EXTRA_STATUS = "extra_status";

    private ReservationRepository repository;
    private String reservationId;

    private TextView textReservationId;
    private TextView textStatus;
    private TextView textProsumer;
    private TextView textStationSlot;
    private TextView textSchedule;
    private TextView textEnergy;

    private LinearLayout layoutLoading;
    private LinearLayout layoutError;
    private TextView textError;
    private MaterialButton buttonRetry;

    private MaterialCardView cardQrDisplay;
    private ImageView imageQrCode;
    private TextView textQrIssuedAt;
    private MaterialButton buttonClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reservation_qr);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.qrRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        reservationId = getIntent().getStringExtra(EXTRA_RESERVATION_ID);
        String prosumerNic = getIntent().getStringExtra(EXTRA_PROSUMER_NIC);
        String stationId = getIntent().getStringExtra(EXTRA_STATION_ID);
        String slotId = getIntent().getStringExtra(EXTRA_SLOT_ID);
        double energyAmount = getIntent().getDoubleExtra(EXTRA_ENERGY_AMOUNT, 0.0);
        String scheduleStart = getIntent().getStringExtra(EXTRA_SCHEDULE_START);
        String scheduleEnd = getIntent().getStringExtra(EXTRA_SCHEDULE_END);
        String status = getIntent().getStringExtra(EXTRA_STATUS);

        textReservationId = findViewById(R.id.textQrReservationId);
        textStatus = findViewById(R.id.textQrStatus);
        textProsumer = findViewById(R.id.textQrProsumer);
        textStationSlot = findViewById(R.id.textQrStationSlot);
        textSchedule = findViewById(R.id.textQrSchedule);
        textEnergy = findViewById(R.id.textQrEnergy);

        layoutLoading = findViewById(R.id.layoutQrLoading);
        layoutError = findViewById(R.id.layoutQrError);
        textError = findViewById(R.id.textQrError);
        buttonRetry = findViewById(R.id.buttonQrRetry);

        cardQrDisplay = findViewById(R.id.cardQrDisplay);
        imageQrCode = findViewById(R.id.imageQrCode);
        textQrIssuedAt = findViewById(R.id.textQrIssuedAt);
        buttonClose = findViewById(R.id.buttonCloseQr);

        String idSnippet = reservationId != null && reservationId.length() > 8
                ? reservationId.substring(0, 8) + "…"
                : String.valueOf(reservationId);
        textReservationId.setText("Reservation: " + idSnippet);

        com.smartsolar.mobile.util.ReservationUiUtils.formatStatusBadge(textStatus, status);

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

        try {
            ApiService api = RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
            repository = new ReservationRepository(api);
        } catch (IllegalArgumentException e) {
            finish();
            return;
        }

        buttonRetry.setOnClickListener(v -> loadQr());
        buttonClose.setOnClickListener(v -> finish());

        View buttonBackHomeQr = findViewById(R.id.buttonBackHomeQr);
        if (buttonBackHomeQr != null) {
            buttonBackHomeQr.setOnClickListener(v -> finish());
        }

        loadQr();
    }

    private void loadQr() {
        if (reservationId == null || repository == null) return;
        setLoading(true);
        repository.issueQr(reservationId, (result, errorRes, statusCode) -> {
            if (isFinishing() || isDestroyed()) return;
            setLoading(false);
            if (result != null && result.getQrPayload() != null) {
                renderQr(result.getQrPayload(), result.getIssuedAtUtc());
            } else {
                showError(statusCode == 409 ? R.string.qr_ineligible_error
                        : statusCode == 403 ? R.string.access_denied
                        : errorRes != 0 ? errorRes : R.string.load_failed);
            }
        });
    }

    private void renderQr(String payload, String issuedAtUtc) {
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bitmap = encoder.encodeBitmap(payload, BarcodeFormat.QR_CODE, 600, 600);
            imageQrCode.setImageBitmap(bitmap);
            cardQrDisplay.setVisibility(View.VISIBLE);
            layoutError.setVisibility(View.GONE);
            if (issuedAtUtc != null) {
                textQrIssuedAt.setText(getString(R.string.qr_issued_at_label, issuedAtUtc));
                textQrIssuedAt.setVisibility(View.VISIBLE);
            } else {
                textQrIssuedAt.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            showError(R.string.load_failed);
        }
    }

    private void setLoading(boolean loading) {
        layoutLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            cardQrDisplay.setVisibility(View.GONE);
            layoutError.setVisibility(View.GONE);
        }
    }

    private void showError(int messageRes) {
        cardQrDisplay.setVisibility(View.GONE);
        layoutError.setVisibility(View.VISIBLE);
        textError.setText(messageRes);
    }

    @Override
    protected void onDestroy() {
        if (repository != null) {
            repository.close();
        }
        super.onDestroy();
    }
}
