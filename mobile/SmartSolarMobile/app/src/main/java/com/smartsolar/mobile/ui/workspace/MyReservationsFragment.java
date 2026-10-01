/*
 * SmartSolar Mobile - Prosumer & Operator Solar Energy Management Platform
 * MyReservationsFragment.java - Fragment managing prosumer's reservation list with status filtering
 */

package com.smartsolar.mobile.ui.workspace;

import com.smartsolar.mobile.ui.reservation.CreateReservationActivity;
import com.smartsolar.mobile.ui.reservation.ModifyReservationActivity;
import com.smartsolar.mobile.ui.reservation.ReservationSummaryActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ImageView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.data.remote.dto.StationResponse;
import com.smartsolar.mobile.data.repository.ReservationError;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import com.smartsolar.mobile.util.ReservationUiUtils;
import com.smartsolar.mobile.util.StationNameResolver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fragment rendering the prosumer's own reservations with status filtering,
 * card expansion, QR code viewing, modification, and cancellation actions.
 */
public final class MyReservationsFragment extends WorkspaceFragment {
    private static final Gson GSON = new Gson();
    private static final String[] STATUS_OPTIONS = new String[]{
            "All statuses", "Pending", "Approved", "Rejected", "Cancelled", "Completed"
    };

    private ReservationRepository repository;
    private Button buttonHeaderNewReservation;
    private Button buttonRefreshDetails;
    private Spinner spinnerMyStatusFilter;
    private LinearLayout layoutReservationsList;
    private TextView textEmptyState;
    private TextView textError;
    private TextView textSuccess;
    private ProgressBar progress;
    private boolean busy;

    private final List<ReservationResponse> allReservations = new ArrayList<>();
    private String currentFilter = "All statuses";
    private final Set<String> expanded = new HashSet<>();

    @Override
    protected int layout() {
        // Return layout resource for my reservations fragment
        return R.layout.fragment_my_reservations;
    }

    @Override
    protected void bind(Bundle saved) {
        // Bind UI components, setup status filter spinner adapter, and restore state
        buttonHeaderNewReservation = findViewById(R.id.buttonHeaderNewReservation);
        buttonRefreshDetails = findViewById(R.id.buttonRefreshDetails);
        spinnerMyStatusFilter = findViewById(R.id.spinnerMyStatusFilter);
        layoutReservationsList = findViewById(R.id.layoutReservationsList);
        textEmptyState = findViewById(R.id.textEmptyState);
        textError = findViewById(R.id.textError);
        textSuccess = findViewById(R.id.textSuccess);
        progress = findViewById(R.id.progress);

        repository = new ReservationRepository(RetrofitClient.create(requireContext(), BuildConfig.API_BASE_URL, BuildConfig.DEBUG));

        // Setup status filter spinner
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                STATUS_OPTIONS
        );
        spinnerMyStatusFilter.setAdapter(statusAdapter);

        String savedFilter = memory.values.getString("filter_status", "All statuses");
        if (savedFilter != null) {
            currentFilter = savedFilter;
            int position = Arrays.asList(STATUS_OPTIONS).indexOf(savedFilter);
            if (position >= 0) {
                spinnerMyStatusFilter.setSelection(position);
            }
        }

        spinnerMyStatusFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFilter = STATUS_OPTIONS[position];
                memory.values.putString("filter_status", currentFilter);
                applyFilterAndDisplay();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No action needed when nothing is selected
            }
        });

        buttonHeaderNewReservation.setOnClickListener(v -> startActivity(new Intent(requireContext(), CreateReservationActivity.class)));
        buttonRefreshDetails.setOnClickListener(v -> retry());

        ArrayList<String> ids = memory.values.getStringArrayList("expanded");
        if (ids != null) {
            expanded.addAll(ids);
        }

        if (memory.data != null) {
            allReservations.clear();
            allReservations.addAll(Arrays.asList((ReservationResponse[]) memory.data));
            applyFilterAndDisplay();
        }
    }

    @Override
    protected void onWorkspaceReady() {
        // Re-evaluate display-only cutoff controls without fetching or rebuilding retained cards
        if (!(memory.data instanceof ReservationResponse[])) return;
        ReservationResponse[] rows = (ReservationResponse[]) memory.data;
        for (int i = 0; i < Math.min(rows.length, layoutReservationsList.getChildCount()); i++) {
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

    @Override
    protected void load() {
        // Execute data loading for prosumer reservations
        loadReservations();
    }

    private void loadReservations() {
        // Fetch current prosumer reservations from backend repository
        if (repository == null || busy) return;
        setBusy(true);
        progress.setVisibility(View.GONE);
        findViewById(R.id.reservationsSkeleton).setVisibility(View.VISIBLE);
        textError.setVisibility(View.GONE);
        textSuccess.setVisibility(View.GONE);

        repository.getMyReservations(new ReservationRepository.Callback<List<ReservationResponse>>() {
            @Override
            public void onSuccess(List<ReservationResponse> reservations) {
                if (!alive()) return;
                setBusy(false);
                allReservations.clear();
                if (reservations != null) {
                    allReservations.addAll(reservations);
                }
                memory.data = allReservations.toArray(new ReservationResponse[0]);
                applyFilterAndDisplay();
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

    private void applyFilterAndDisplay() {
        // Filter cached reservations by current selected status and render
        List<ReservationResponse> filtered = new ArrayList<>();
        boolean isAll = "All statuses".equalsIgnoreCase(currentFilter);
        for (ReservationResponse res : allReservations) {
            if (isAll || currentFilter.equalsIgnoreCase(res.getStatus())) {
                filtered.add(res);
            }
        }
        displayReservations(filtered);
    }

    private void displayReservations(List<ReservationResponse> reservations) {
        // Inflate and populate card views for each reservation in the filtered list
        layoutReservationsList.removeAllViews();

        if (reservations == null || reservations.isEmpty()) {
            if ("All statuses".equalsIgnoreCase(currentFilter) || allReservations.isEmpty()) {
                textEmptyState.setText(R.string.no_reservations_found);
            } else {
                textEmptyState.setText("No " + currentFilter + " reservations found.");
            }
            textEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        textEmptyState.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        long nowMillis = System.currentTimeMillis();

        for (ReservationResponse res : reservations) {
            View card = inflater.inflate(R.layout.item_reservation_card, layoutReservationsList, false);
            View viewStatusAccent = card.findViewById(R.id.viewStatusAccent);
            TextView textCardStatus = card.findViewById(R.id.textCardStatus);
            TextView textCardStation = card.findViewById(R.id.textCardStation);
            TextView textCardEnergy = card.findViewById(R.id.textCardEnergy);
            ImageView textChevron = card.findViewById(R.id.textChevron);
            View cardHeader = card.findViewById(R.id.cardHeader);
            View layoutExpandedDetails = card.findViewById(R.id.layoutExpandedDetails);

            TextView textExpandedStart = card.findViewById(R.id.textExpandedStart);
            TextView textExpandedEnd = card.findViewById(R.id.textExpandedEnd);
            TextView textExpandedCutoff = card.findViewById(R.id.textExpandedCutoff);
            TextView textExpandedRestriction = card.findViewById(R.id.textExpandedRestriction);
            View layoutRejectionNotice = card.findViewById(R.id.layoutRejectionNotice);
            TextView textRejectionRemark = card.findViewById(R.id.textRejectionRemark);
            Button buttonCardModify = card.findViewById(R.id.buttonCardModify);
            Button buttonCardCancel = card.findViewById(R.id.buttonCardCancel);
            View layoutCardApprovedActions = card.findViewById(R.id.layoutCardApprovedActions);
            Button buttonCardViewQr = card.findViewById(R.id.buttonCardViewQr);
            Button buttonCardGetDirections = card.findViewById(R.id.buttonCardGetDirections);

            // Bind Essential Preview Info
            textCardStatus.setText(res.getStatus());
            formatStatusBadge(textCardStatus, res.getStatus());
            ReservationUiUtils.formatStatusAccent(viewStatusAccent, res.getStatus());

            StationNameResolver.bindStationName(textCardStation, res.getStationId());
            textCardEnergy.setText(String.format(Locale.US, "%.1f kWh", res.getEnergyAmountKwh()));

            // Bind Expanded Details
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
            if (layoutCardApprovedActions != null) {
                layoutCardApprovedActions.setVisibility(approved ? View.VISIBLE : View.GONE);
            }
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

            buttonCardGetDirections.setVisibility(approved ? View.VISIBLE : View.GONE);
            buttonCardGetDirections.setOnClickListener(v -> openDirectionsForStation(res.getStationId()));

            // Expand/Collapse Chevron interaction
            View.OnClickListener toggleListener = v -> {
                boolean isExpanded = layoutExpandedDetails.getVisibility() == View.VISIBLE;
                if (isExpanded) expanded.remove(res.getReservationId()); else expanded.add(res.getReservationId());
                layoutExpandedDetails.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
                textChevron.setRotation(isExpanded ? 0f : 180f);
            };
            layoutExpandedDetails.setVisibility(expanded.contains(res.getReservationId()) ? View.VISIBLE : View.GONE);
            textChevron.setRotation(expanded.contains(res.getReservationId()) ? 180f : 0f);
            androidx.core.view.ViewCompat.setStateDescription(cardHeader, getString(expanded.contains(res.getReservationId()) ? R.string.details_expanded : R.string.details_collapsed));
            cardHeader.setOnClickListener(v -> {
                toggleListener.onClick(v);
                androidx.core.view.ViewCompat.setStateDescription(cardHeader, getString(layoutExpandedDetails.getVisibility() == View.VISIBLE ? R.string.details_expanded : R.string.details_collapsed));
            });

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
        // Delegate status badge color/pill formatting to utility
        ReservationUiUtils.formatStatusBadge(view, status);
    }

    private void showCancelConfirmDialog(ReservationResponse reservation) {
        // Display confirmation dialog before executing reservation cancellation
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
        // Perform asynchronous reservation cancellation request to backend API
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

    private void openDirectionsForStation(String stationId) {
        // Query station coordinates and launch Google Maps / Navigation intent
        if (stationId == null || stationId.trim().isEmpty()) {
            return;
        }
        com.smartsolar.mobile.data.remote.api.ApiService api = RetrofitClient.create(requireContext(), BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
        api.getStation(stationId).enqueue(new retrofit2.Callback<StationResponse>() {
            @Override
            public void onResponse(retrofit2.Call<StationResponse> call, retrofit2.Response<StationResponse> response) {
                if (!alive()) return;
                StationResponse station = response.body();
                android.net.Uri uri;
                if (station != null && (station.latitude != 0 || station.longitude != 0)) {
                    String label = station.name != null && !station.name.isEmpty() ? station.name : "Solar Station " + stationId;
                    uri = android.net.Uri.parse("geo:" + station.latitude + "," + station.longitude + "?q=" + station.latitude + "," + station.longitude + "(" + android.net.Uri.encode(label) + ")");
                } else if (station != null && station.address != null && !station.address.isEmpty()) {
                    uri = android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(station.address));
                } else {
                    uri = android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode("Solar Station " + stationId));
                }
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, uri);
                try {
                    startActivity(mapIntent);
                } catch (android.content.ActivityNotFoundException e) {
                    String webUrl = "https://www.google.com/maps/search/?api=1&query=" + (station != null && (station.latitude != 0 || station.longitude != 0) ? (station.latitude + "," + station.longitude) : android.net.Uri.encode(station != null && station.address != null && !station.address.isEmpty() ? station.address : "Solar Station " + stationId));
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(webUrl)));
                }
            }

            @Override
            public void onFailure(retrofit2.Call<StationResponse> call, Throwable t) {
                if (!alive()) return;
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode("Solar Station " + stationId)));
                try {
                    startActivity(mapIntent);
                } catch (android.content.ActivityNotFoundException e) {
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=" + android.net.Uri.encode("Solar Station " + stationId))));
                }
            }
        });
    }

    private void setBusy(boolean value) {
        // Update loading busy state and disable interactive buttons
        busy = value;
        if (!value) findViewById(R.id.reservationsSkeleton).setVisibility(View.GONE);
        memory.loading = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        buttonRefreshDetails.setEnabled(!value);
        buttonHeaderNewReservation.setEnabled(!value);
        if (spinnerMyStatusFilter != null) {
            spinnerMyStatusFilter.setEnabled(!value);
        }
    }

    @Override
    public void onDestroyView() {
        // Persist expanded item states and clean up repository resources
        memory.values.putStringArrayList("expanded", new ArrayList<>(expanded));
        if (repository != null) repository.close();
        busy = false;
        super.onDestroyView();
    }
}
