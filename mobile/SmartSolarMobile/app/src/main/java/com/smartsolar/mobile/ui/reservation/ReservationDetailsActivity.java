package com.smartsolar.mobile.ui.reservation;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
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
import java.util.List;
import java.util.Locale;

public final class ReservationDetailsActivity extends AppCompatActivity {
    public static final String EXTRA_RESERVATION_ID = "com.smartsolar.mobile.RESERVATION_ID";
    private static final Gson GSON = new Gson();

    private ReservationRepository repository;
    private Button buttonHeaderNewReservation;
    private Button buttonRefreshDetails;
    private Button buttonBackHome;
    private LinearLayout layoutReservationsList;
    private TextView textEmptyState;
    private TextView textError;
    private TextView textSuccess;
    private ProgressBar progress;
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

        buttonHeaderNewReservation = findViewById(R.id.buttonHeaderNewReservation);
        buttonRefreshDetails = findViewById(R.id.buttonRefreshDetails);
        buttonBackHome = findViewById(R.id.buttonBackHome);
        layoutReservationsList = findViewById(R.id.layoutReservationsList);
        textEmptyState = findViewById(R.id.textEmptyState);
        textError = findViewById(R.id.textError);
        textSuccess = findViewById(R.id.textSuccess);
        progress = findViewById(R.id.progress);

        try {
            ApiService api = RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
            repository = new ReservationRepository(api);
        } catch (IllegalArgumentException e) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        buttonHeaderNewReservation.setOnClickListener(v ->
                startActivity(new Intent(this, CreateReservationActivity.class)));

        buttonRefreshDetails.setOnClickListener(v -> loadReservations());
        buttonBackHome.setOnClickListener(v -> finish());
        findViewById(R.id.buttonCurrentView).setOnClickListener(v -> com.smartsolar.mobile.ui.common.WorkspaceChrome.navigate(this, com.smartsolar.mobile.util.MobileNavigation.Destination.BOOKINGS));
        findViewById(R.id.buttonPendingView).setOnClickListener(v -> { startActivity(new Intent(this, com.smartsolar.mobile.ui.reservations.CurrentBookingsActivity.class).putExtra("pendingView", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); });
        findViewById(R.id.buttonSearchView).setOnClickListener(v -> com.smartsolar.mobile.ui.common.WorkspaceChrome.navigate(this, com.smartsolar.mobile.util.MobileNavigation.Destination.SEARCH));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReservations();
    }

    private void loadReservations() {
        if (repository == null || busy) return;
        setBusy(true);
        textError.setVisibility(View.GONE);
        textSuccess.setVisibility(View.GONE);

        repository.getMyReservations(new ReservationRepository.Callback<List<ReservationResponse>>() {
            @Override
            public void onSuccess(List<ReservationResponse> reservations) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                displayReservations(reservations);
            }

            @Override
            public void onError(ReservationError error) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                if (error.isSessionExpired()) {
                    startActivity(new Intent(ReservationDetailsActivity.this, LoginActivity.class));
                    finish();
                    return;
                }
                textError.setText(error.getMessage());
                textError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void displayReservations(List<ReservationResponse> reservations) {
        layoutReservationsList.removeAllViews();

        if (reservations == null || reservations.isEmpty()) {
            textEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        textEmptyState.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        long nowMillis = System.currentTimeMillis();

        for (ReservationResponse res : reservations) {
            View card = inflater.inflate(R.layout.item_reservation_card, layoutReservationsList, false);

            TextView textCardIdSnippet = card.findViewById(R.id.textCardIdSnippet);
            TextView textCardStatus = card.findViewById(R.id.textCardStatus);
            TextView textCardStation = card.findViewById(R.id.textCardStation);
            TextView textCardEnergy = card.findViewById(R.id.textCardEnergy);
            TextView textCardStartPreview = card.findViewById(R.id.textCardStartPreview);
            TextView textChevron = card.findViewById(R.id.textChevron);
            View cardHeader = card.findViewById(R.id.cardHeader);
            View layoutExpandedDetails = card.findViewById(R.id.layoutExpandedDetails);

            TextView textExpandedReservationId = card.findViewById(R.id.textExpandedReservationId);
            TextView textExpandedSlotId = card.findViewById(R.id.textExpandedSlotId);
            TextView textExpandedStart = card.findViewById(R.id.textExpandedStart);
            TextView textExpandedEnd = card.findViewById(R.id.textExpandedEnd);
            TextView textExpandedCutoff = card.findViewById(R.id.textExpandedCutoff);
            TextView textExpandedRestriction = card.findViewById(R.id.textExpandedRestriction);
            View layoutRejectionNotice = card.findViewById(R.id.layoutRejectionNotice);
            TextView textRejectionRemark = card.findViewById(R.id.textRejectionRemark);
            Button buttonCardModify = card.findViewById(R.id.buttonCardModify);
            Button buttonCardCancel = card.findViewById(R.id.buttonCardCancel);
            Button buttonCardViewQr = card.findViewById(R.id.buttonCardViewQr);

            // Bind Essential Preview Info
            String idSnippet = res.getReservationId() != null && res.getReservationId().length() > 8
                    ? res.getReservationId().substring(0, 8) + "…"
                    : String.valueOf(res.getReservationId());
            textCardIdSnippet.setText("Reservation #" + ReservationUiUtils.shortReference(res.getReservationId()));

            textCardStatus.setText(res.getStatus());
            formatStatusBadge(textCardStatus, res.getStatus());

            textCardStation.setText("Station " + ReservationUiUtils.shortReference(res.getStationId()));
            textCardEnergy.setText(String.format(Locale.US, "%.1f kWh", res.getEnergyAmountKwh()));
            textCardStartPreview.setText(ReservationUiUtils.schedule(res.getScheduledStartAtUtc(), res.getScheduledEndAtUtc()));

            // Bind Expanded Details
            textExpandedReservationId.setText(res.getReservationId());
            textExpandedSlotId.setText(res.getSlotId() != null ? res.getSlotId() : "—");
            textExpandedStart.setText(ReservationUiUtils.formatUtc(res.getScheduledStartAtUtc()));
            textExpandedEnd.setText(ReservationUiUtils.formatUtc(res.getScheduledEndAtUtc()));
            textExpandedCutoff.setText(ReservationUiUtils.formatCutoffUtc(res.getScheduledStartAtUtc()));

            // Rejection remark notice
            if ("Rejected".equalsIgnoreCase(res.getStatus()) && res.getRejectionRemark() != null && !res.getRejectionRemark().trim().isEmpty()) {
                textRejectionRemark.setText(res.getRejectionRemark());
                layoutRejectionNotice.setVisibility(View.VISIBLE);
            } else {
                layoutRejectionNotice.setVisibility(View.GONE);
            }

            // Restrictions
            boolean terminal = ReservationUiUtils.isTerminalStatus(res.getStatus());
            boolean cutoffPassed = ReservationUiUtils.isCutoffPassed(res.getScheduledStartAtUtc(), nowMillis);

            if (terminal) {
                textExpandedRestriction.setText(R.string.terminal_status_notice);
                textExpandedRestriction.setVisibility(View.VISIBLE);
                buttonCardModify.setEnabled(false);
                buttonCardCancel.setEnabled(false);
            } else if (cutoffPassed) {
                textExpandedRestriction.setText(R.string.cutoff_passed_notice);
                textExpandedRestriction.setVisibility(View.VISIBLE);
                buttonCardModify.setEnabled(false);
                buttonCardCancel.setEnabled(false);
            } else {
                textExpandedRestriction.setVisibility(View.GONE);
                buttonCardModify.setEnabled(true);
                buttonCardCancel.setEnabled(true);
            }

            boolean approved = "Approved".equalsIgnoreCase(res.getStatus());
            buttonCardViewQr.setVisibility(approved ? View.VISIBLE : View.GONE);
            buttonCardViewQr.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.smartsolar.mobile.ui.reservations.ReservationQrActivity.class);
                intent.putExtra(com.smartsolar.mobile.ui.reservations.ReservationQrActivity.EXTRA_RESERVATION_ID, res.getReservationId());
                intent.putExtra(com.smartsolar.mobile.ui.reservations.ReservationQrActivity.EXTRA_PROSUMER_NIC, res.getProsumerNic());
                intent.putExtra(com.smartsolar.mobile.ui.reservations.ReservationQrActivity.EXTRA_STATION_ID, res.getStationId());
                intent.putExtra(com.smartsolar.mobile.ui.reservations.ReservationQrActivity.EXTRA_SLOT_ID, res.getSlotId());
                intent.putExtra(com.smartsolar.mobile.ui.reservations.ReservationQrActivity.EXTRA_ENERGY_AMOUNT, res.getEnergyAmountKwh());
                intent.putExtra(com.smartsolar.mobile.ui.reservations.ReservationQrActivity.EXTRA_SCHEDULE_START, res.getScheduledStartAtUtc());
                intent.putExtra(com.smartsolar.mobile.ui.reservations.ReservationQrActivity.EXTRA_SCHEDULE_END, res.getScheduledEndAtUtc());
                intent.putExtra(com.smartsolar.mobile.ui.reservations.ReservationQrActivity.EXTRA_STATUS, res.getStatus());
                startActivity(intent);
            });

            // Expand/Collapse Chevron interaction
            View.OnClickListener toggleListener = v -> {
                boolean isExpanded = layoutExpandedDetails.getVisibility() == View.VISIBLE;
                layoutExpandedDetails.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
                textChevron.setText(isExpanded ? "⌄" : "⌃");
            };
            androidx.core.view.ViewCompat.setStateDescription(cardHeader, getString(R.string.details_collapsed));
            cardHeader.setOnClickListener(v -> { toggleListener.onClick(v); androidx.core.view.ViewCompat.setStateDescription(cardHeader, getString(layoutExpandedDetails.getVisibility() == View.VISIBLE ? R.string.details_expanded : R.string.details_collapsed)); });

            // Action Buttons
            buttonCardModify.setOnClickListener(v -> {
                Intent intent = new Intent(this, ModifyReservationActivity.class);
                intent.putExtra(ModifyReservationActivity.EXTRA_RESERVATION_JSON, GSON.toJson(res));
                startActivity(intent);
            });

            buttonCardCancel.setOnClickListener(v -> showCancelConfirmDialog(res));

            layoutReservationsList.addView(card);
        }
    }

    private void formatStatusBadge(TextView view, String status) {
        ReservationUiUtils.formatStatusBadge(view, status);
    }

    private void showCancelConfirmDialog(ReservationResponse reservation) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_cancel_title)
                .setMessage(getString(R.string.dialog_cancel_message,
                        reservation.getReservationId(),
                        String.format(Locale.US, "%.1f", reservation.getEnergyAmountKwh())))
                .setNegativeButton(R.string.dialog_keep_reservation, null)
                .setPositiveButton(R.string.dialog_confirm_cancellation, (dialog, which) -> executeCancel(reservation))
                .show();
    }

    private void executeCancel(ReservationResponse reservation) {
        setBusy(true);
        textError.setVisibility(View.GONE);
        textSuccess.setVisibility(View.GONE);

        repository.cancelReservation(reservation.getReservationId(), new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                Intent intent = new Intent(ReservationDetailsActivity.this, ReservationSummaryActivity.class);
                intent.putExtra(ReservationSummaryActivity.EXTRA_RESERVATION_JSON, GSON.toJson(result));
                intent.putExtra(ReservationSummaryActivity.EXTRA_MODE, ReservationSummaryActivity.MODE_CANCELLED);
                startActivity(intent);
            }

            @Override
            public void onError(ReservationError error) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                if (error.isSessionExpired()) {
                    startActivity(new Intent(ReservationDetailsActivity.this, LoginActivity.class));
                    finish();
                    return;
                }
                textError.setText(error.getMessage());
                textError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        buttonRefreshDetails.setEnabled(!value);
        buttonHeaderNewReservation.setEnabled(!value);
    }

    @Override
    protected void onDestroy() {
        if (repository != null) repository.close();
        super.onDestroy();
    }
    @Override protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        com.smartsolar.mobile.ui.common.WorkspaceChrome.attach(this, getString(R.string.title_my_reservations), com.smartsolar.mobile.util.MobileNavigation.Destination.RESERVATIONS);
    }
}
