package com.smartsolar.mobile.ui.reservation;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.gson.Gson;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.ui.home.HomeActivity;
import com.smartsolar.mobile.util.ReservationUiUtils;

public final class ReservationSummaryActivity extends AppCompatActivity {
    public static final String EXTRA_RESERVATION_JSON = "com.smartsolar.mobile.RESERVATION_JSON";
    public static final String EXTRA_MODE = "com.smartsolar.mobile.SUMMARY_MODE";
    public static final String MODE_CREATED = "CREATED";
    public static final String MODE_UPDATED = "UPDATED";
    public static final String MODE_CANCELLED = "CANCELLED";

    private static final Gson GSON = new Gson();
    private ReservationResponse reservation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reservation_summary);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.summaryRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        String json = getIntent().getStringExtra(EXTRA_RESERVATION_JSON);
        if (json != null) {
            reservation = GSON.fromJson(json, ReservationResponse.class);
        }

        if (reservation == null) {
            finish();
            return;
        }

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        TextView textBanner = findViewById(R.id.textSummaryBanner);
        if (MODE_UPDATED.equals(mode)) {
            textBanner.setText(R.string.reservation_updated_banner);
        } else if (MODE_CANCELLED.equals(mode)) {
            textBanner.setText(R.string.reservation_cancelled_banner);
        } else {
            textBanner.setText(R.string.reservation_created_banner);
        }

        TextView textReservationId = findViewById(R.id.textSummaryReservationId);
        TextView textStatus = findViewById(R.id.textSummaryStatus);
        TextView textNic = findViewById(R.id.textSummaryNic);
        TextView textStationId = findViewById(R.id.textSummaryStationId);
        TextView textSlotId = findViewById(R.id.textSummarySlotId);
        TextView textEnergy = findViewById(R.id.textSummaryEnergy);
        TextView textStart = findViewById(R.id.textSummaryStart);
        TextView textEnd = findViewById(R.id.textSummaryEnd);
        TextView textCutoff = findViewById(R.id.textSummaryCutoff);

        textReservationId.setText(reservation.getReservationId());
        textStatus.setText(reservation.getStatus());
        textNic.setText(reservation.getProsumerNic());
        textStationId.setText(reservation.getStationId());
        textSlotId.setText(reservation.getSlotId());
        textEnergy.setText(String.format("%s kWh", reservation.getEnergyAmountKwh()));
        textStart.setText(ReservationUiUtils.formatUtc(reservation.getScheduledStartAtUtc()));
        textEnd.setText(ReservationUiUtils.formatUtc(reservation.getScheduledEndAtUtc()));
        textCutoff.setText(ReservationUiUtils.formatCutoffUtc(reservation.getScheduledStartAtUtc()));

        Button buttonViewDetails = findViewById(R.id.buttonViewDetails);
        Button buttonBackHome = findViewById(R.id.buttonBackHome);

        buttonViewDetails.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReservationDetailsActivity.class);
            intent.putExtra(ReservationDetailsActivity.EXTRA_RESERVATION_ID, reservation.getReservationId());
            startActivity(intent);
            finish();
        });

        buttonBackHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }
}
