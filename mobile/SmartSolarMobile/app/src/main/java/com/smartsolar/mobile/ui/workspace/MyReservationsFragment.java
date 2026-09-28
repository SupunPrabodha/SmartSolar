package com.smartsolar.mobile.ui.workspace;
import com.smartsolar.mobile.ui.reservation.CreateReservationActivity;
import com.smartsolar.mobile.ui.reservation.ModifyReservationActivity;
import com.smartsolar.mobile.ui.reservation.ReservationSummaryActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.data.repository.ReservationError;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import com.smartsolar.mobile.util.ReservationUiUtils;
import java.util.List;
import java.util.Locale;

public final class MyReservationsFragment extends WorkspaceFragment {
    private static final Gson GSON = new Gson();

    private ReservationRepository repository;
    private Button buttonHeaderNewReservation;
    private Button buttonRefreshDetails;
    private LinearLayout layoutReservationsList;
    private TextView textEmptyState;
    private TextView textError;
    private TextView textSuccess;
    private ProgressBar progress;
    private boolean busy;

    private final java.util.Set<String> expanded = new java.util.HashSet<>();
    @Override protected int layout() { return R.layout.fragment_my_reservations; }
    @Override protected void bind(Bundle saved) {
        buttonHeaderNewReservation = findViewById(R.id.buttonHeaderNewReservation);
        buttonRefreshDetails = findViewById(R.id.buttonRefreshDetails);
        layoutReservationsList = findViewById(R.id.layoutReservationsList);
        textEmptyState = findViewById(R.id.textEmptyState); textError = findViewById(R.id.textError);
        textSuccess = findViewById(R.id.textSuccess); progress = findViewById(R.id.progress);
        repository = new ReservationRepository(RetrofitClient.create(requireContext(), BuildConfig.API_BASE_URL, BuildConfig.DEBUG));
        buttonHeaderNewReservation.setOnClickListener(v -> startActivity(new Intent(requireContext(), CreateReservationActivity.class)));
        buttonRefreshDetails.setOnClickListener(v -> retry());
        java.util.ArrayList<String> ids = memory.values.getStringArrayList("expanded");
        if (ids != null) expanded.addAll(ids);
        if (memory.data != null) displayReservations(java.util.Arrays.asList((ReservationResponse[]) memory.data));
    }
    @Override protected void onWorkspaceReady() {
        // Re-evaluate display-only cutoff controls without fetching or rebuilding retained cards.
        if (!(memory.data instanceof ReservationResponse[])) return;
        ReservationResponse[] rows = (ReservationResponse[]) memory.data;
        for (int i=0; i<Math.min(rows.length, layoutReservationsList.getChildCount()); i++) {
            boolean terminal = ReservationUiUtils.isTerminalStatus(rows[i].getStatus());
            boolean cutoff = ReservationUiUtils.isCutoffPassed(rows[i].getScheduledStartAtUtc(), System.currentTimeMillis());
            View card = layoutReservationsList.getChildAt(i);
            card.findViewById(R.id.buttonCardModify).setEnabled(!terminal && !cutoff);
            card.findViewById(R.id.buttonCardCancel).setEnabled(!terminal && !cutoff);
            TextView notice = card.findViewById(R.id.textExpandedRestriction);
            notice.setVisibility(terminal || cutoff ? View.VISIBLE : View.GONE);
            if (terminal || cutoff) notice.setText(terminal ? R.string.terminal_status_notice : R.string.cutoff_passed_notice);
        }
    }
    @Override protected void load() { loadReservations(); }
    private void loadReservations() {
        if (repository == null || busy) return;
        setBusy(true);
        textError.setVisibility(View.GONE);
        textSuccess.setVisibility(View.GONE);

        repository.getMyReservations(new ReservationRepository.Callback<List<ReservationResponse>>() {
            @Override
            public void onSuccess(List<ReservationResponse> reservations) {
                if (!alive()) return;
                setBusy(false);
                memory.data = reservations.toArray(new ReservationResponse[0]); displayReservations(reservations);
            }

            @Override
            public void onError(ReservationError error) {
                if (!alive()) return;
                setBusy(false);
                if (error.isSessionExpired()) {
                    workspace().openLogin();
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
        LayoutInflater inflater = LayoutInflater.from(requireContext());
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
                Intent intent = new Intent(requireContext(), com.smartsolar.mobile.ui.reservations.ReservationQrActivity.class);
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
                if (isExpanded) expanded.remove(res.getReservationId()); else expanded.add(res.getReservationId());
                layoutExpandedDetails.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
                textChevron.setText(isExpanded ? "⌄" : "⌃");
            };
            layoutExpandedDetails.setVisibility(expanded.contains(res.getReservationId()) ? View.VISIBLE : View.GONE);
            textChevron.setText(expanded.contains(res.getReservationId()) ? "⌃" : "⌄");
            androidx.core.view.ViewCompat.setStateDescription(cardHeader, getString(expanded.contains(res.getReservationId()) ? R.string.details_expanded : R.string.details_collapsed));
            cardHeader.setOnClickListener(v -> { toggleListener.onClick(v); androidx.core.view.ViewCompat.setStateDescription(cardHeader, getString(layoutExpandedDetails.getVisibility() == View.VISIBLE ? R.string.details_expanded : R.string.details_collapsed)); });

            // Action Buttons
            buttonCardModify.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), ModifyReservationActivity.class);
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
        new MaterialAlertDialogBuilder(requireContext())
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
                if (!alive()) return;
                setBusy(false);
                workspace().reservationsChanged();
                Intent intent = new Intent(requireContext(), ReservationSummaryActivity.class);
                intent.putExtra(ReservationSummaryActivity.EXTRA_RESERVATION_JSON, GSON.toJson(result));
                intent.putExtra(ReservationSummaryActivity.EXTRA_MODE, ReservationSummaryActivity.MODE_CANCELLED);
                startActivity(intent);
            }

            @Override
            public void onError(ReservationError error) {
                if (!alive()) return;
                setBusy(false);
                if (error.isSessionExpired()) {
                    workspace().openLogin();
                    return;
                }
                textError.setText(error.getMessage());
                textError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value; memory.loading = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        buttonRefreshDetails.setEnabled(!value);
        buttonHeaderNewReservation.setEnabled(!value);
    }

    @Override public void onDestroyView() {
        memory.values.putStringArrayList("expanded", new java.util.ArrayList<>(expanded));
        if (repository != null) repository.close(); busy = false; super.onDestroyView();
    }
}
