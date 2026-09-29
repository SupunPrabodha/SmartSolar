/*
 * SmartSolar Mobile - Prosumer & Operator Solar Energy Management Platform
 * ReservationSummaryAnalyticsFragment.java - Summary analytics screen for energy provision & status metrics
 */

package com.smartsolar.mobile.ui.workspace;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.chip.ChipGroup;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.data.remote.dto.StationResponse;
import com.smartsolar.mobile.data.repository.ReservationError;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import com.smartsolar.mobile.ui.common.SolarDonutChartView;
import com.smartsolar.mobile.ui.common.SolarDonutChartView.DonutEntry;
import com.smartsolar.mobile.util.ReservationUiUtils;
import com.smartsolar.mobile.util.StationNameResolver;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Summary analytics fragment displaying energy provided to the grid, pending energy,
 * an interactive pie/donut chart, carbon offset calculator, and top trading station.
 */
public final class ReservationSummaryAnalyticsFragment extends WorkspaceFragment {

    private enum PeriodFilter {
        ALL_TIME, THIS_MONTH, THIS_WEEK, LAST_30_DAYS, THIS_YEAR
    }

    private static final double CO2_KG_PER_KWH = 0.85;
    private static final double TREE_ANNUAL_CO2_KG = 21.77;

    private ReservationRepository repository;
    private SolarDonutChartView chartEnergyDonut;
    private ChipGroup chipGroupPeriods;
    private ProgressBar progressSummary;
    private LinearLayout errorContainerSummary;
    private TextView textErrorSummary;
    private Button buttonRetrySummary;
    private Button buttonRefreshSummary;

    private TextView textKpiCompletedKwh;
    private TextView textKpiCompletedSub;
    private TextView textKpiPendingKwh;
    private TextView textKpiPendingSub;
    private TextView textKpiApprovedKwh;
    private TextView textKpiApprovedSub;
    private TextView textKpiTotalKwh;
    private TextView textKpiTotalSub;

    private TextView textCarbonOffsetKg;
    private TextView textCarbonTreesEquivalent;
    private TextView textTopStationName;
    private LinearLayout layoutTopStationDetails;
    private TextView textTopStationEnergy;
    private TextView textTopStationTransfers;
    private TextView textTopStationShare;

    private final List<ReservationResponse> allReservations = new ArrayList<>();
    private PeriodFilter currentPeriod = PeriodFilter.ALL_TIME;
    private boolean busy;

    @Override
    protected int layout() {
        return R.layout.fragment_reservation_summary_analytics;
    }

    @Override
    protected void bind(Bundle saved) {
        // Initialize view references and listeners
        repository = new ReservationRepository(RetrofitClient.create(requireContext(), BuildConfig.API_BASE_URL, BuildConfig.DEBUG));

        chartEnergyDonut = findViewById(R.id.chartEnergyDonut);
        chipGroupPeriods = findViewById(R.id.chipGroupPeriods);
        progressSummary = findViewById(R.id.progressSummary);
        errorContainerSummary = findViewById(R.id.errorContainerSummary);
        textErrorSummary = findViewById(R.id.textErrorSummary);
        buttonRetrySummary = findViewById(R.id.buttonRetrySummary);
        buttonRefreshSummary = findViewById(R.id.buttonRefreshSummary);

        textKpiCompletedKwh = findViewById(R.id.textKpiCompletedKwh);
        textKpiCompletedSub = findViewById(R.id.textKpiCompletedSub);
        textKpiPendingKwh = findViewById(R.id.textKpiPendingKwh);
        textKpiPendingSub = findViewById(R.id.textKpiPendingSub);
        textKpiApprovedKwh = findViewById(R.id.textKpiApprovedKwh);
        textKpiApprovedSub = findViewById(R.id.textKpiApprovedSub);
        textKpiTotalKwh = findViewById(R.id.textKpiTotalKwh);
        textKpiTotalSub = findViewById(R.id.textKpiTotalSub);

        textCarbonOffsetKg = findViewById(R.id.textCarbonOffsetKg);
        textCarbonTreesEquivalent = findViewById(R.id.textCarbonTreesEquivalent);
        textTopStationName = findViewById(R.id.textTopStationName);
        layoutTopStationDetails = findViewById(R.id.layoutTopStationDetails);
        textTopStationEnergy = findViewById(R.id.textTopStationEnergy);
        textTopStationTransfers = findViewById(R.id.textTopStationTransfers);
        textTopStationShare = findViewById(R.id.textTopStationShare);

        buttonRefreshSummary.setOnClickListener(v -> retry());
        buttonRetrySummary.setOnClickListener(v -> retry());

        setupPeriodChips();

        String savedPeriod = memory.values.getString("summary_period");
        if (savedPeriod != null) {
            try {
                currentPeriod = PeriodFilter.valueOf(savedPeriod);
                applyPeriodChipSelection(currentPeriod);
            } catch (IllegalArgumentException ignored) { }
        }

        if (memory.data instanceof ReservationResponse[]) {
            allReservations.clear();
            allReservations.addAll(Arrays.asList((ReservationResponse[]) memory.data));
            calculateAndRenderMetrics(false);
        }
    }

    private void setupPeriodChips() {
        // Setup listener for period filtering chips
        chipGroupPeriods.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chipAllTime) currentPeriod = PeriodFilter.ALL_TIME;
            else if (checkedId == R.id.chipThisMonth) currentPeriod = PeriodFilter.THIS_MONTH;
            else if (checkedId == R.id.chipThisWeek) currentPeriod = PeriodFilter.THIS_WEEK;
            else if (checkedId == R.id.chipLast30Days) currentPeriod = PeriodFilter.LAST_30_DAYS;
            else if (checkedId == R.id.chipThisYear) currentPeriod = PeriodFilter.THIS_YEAR;

            memory.values.putString("summary_period", currentPeriod.name());
            calculateAndRenderMetrics(true);
        });
    }

    private void applyPeriodChipSelection(PeriodFilter period) {
        // Synchronize chip check state with restored period
        int chipId = R.id.chipAllTime;
        if (period == PeriodFilter.THIS_MONTH) chipId = R.id.chipThisMonth;
        else if (period == PeriodFilter.THIS_WEEK) chipId = R.id.chipThisWeek;
        else if (period == PeriodFilter.LAST_30_DAYS) chipId = R.id.chipLast30Days;
        else if (period == PeriodFilter.THIS_YEAR) chipId = R.id.chipThisYear;
        chipGroupPeriods.check(chipId);
    }

    @Override
    protected void load() {
        // Load prosumer reservations for analytics calculation
        fetchReservations();
    }

    private void fetchReservations() {
        // Fetch all reservations from repository
        if (repository == null || busy) return;
        setBusy(true);
        errorContainerSummary.setVisibility(View.GONE);

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
                calculateAndRenderMetrics(true);
            }

            @Override
            public void onError(ReservationError error) {
                if (!alive()) return;
                setBusy(false);
                if (error.isSessionExpired()) {
                    workspace().openLogin();
                    return;
                }
                errorContainerSummary.setVisibility(View.VISIBLE);
                textErrorSummary.setText(error.getMessage());
            }
        });
    }

    private void calculateAndRenderMetrics(boolean animateChart) {
        // Aggregate energy metrics and render chart, KPI cards, carbon impact, and top station
        List<ReservationResponse> filtered = filterByPeriod(allReservations, currentPeriod);

        double completedKwh = 0, pendingKwh = 0, approvedKwh = 0, cancelledKwh = 0, rejectedKwh = 0;
        int completedCount = 0, pendingCount = 0, approvedCount = 0, cancelledCount = 0, rejectedCount = 0;

        Map<String, double[]> stationAggregates = new HashMap<>(); // stationId -> [completedKwh, completedCount]

        for (ReservationResponse res : filtered) {
            String status = res.getStatus() != null ? res.getStatus() : "";
            double energy = res.getEnergyAmountKwh();
            String stationId = res.getStationId() != null ? res.getStationId() : "Unknown";

            if ("Completed".equalsIgnoreCase(status)) {
                completedKwh += energy;
                completedCount++;

                double[] stat = stationAggregates.computeIfAbsent(stationId, k -> new double[]{0, 0});
                stat[0] += energy;
                stat[1] += 1;
            } else if ("Pending".equalsIgnoreCase(status)) {
                pendingKwh += energy;
                pendingCount++;
            } else if ("Approved".equalsIgnoreCase(status)) {
                approvedKwh += energy;
                approvedCount++;
            } else if ("Cancelled".equalsIgnoreCase(status)) {
                cancelledKwh += energy;
                cancelledCount++;
            } else if ("Rejected".equalsIgnoreCase(status)) {
                rejectedKwh += energy;
                rejectedCount++;
            }
        }

        double totalKwh = completedKwh + pendingKwh + approvedKwh + cancelledKwh + rejectedKwh;
        int totalCount = filtered.size();

        // Update KPI Cards
        textKpiCompletedKwh.setText(String.format(Locale.US, "%.1f kWh", completedKwh));
        textKpiCompletedSub.setText(formatCountWithPct(completedCount, completedKwh, totalKwh));

        textKpiPendingKwh.setText(String.format(Locale.US, "%.1f kWh", pendingKwh));
        textKpiPendingSub.setText(formatCountWithPct(pendingCount, pendingKwh, totalKwh));

        textKpiApprovedKwh.setText(String.format(Locale.US, "%.1f kWh", approvedKwh));
        textKpiApprovedSub.setText(formatCountWithPct(approvedCount, approvedKwh, totalKwh));

        textKpiTotalKwh.setText(String.format(Locale.US, "%.1f kWh", totalKwh));
        textKpiTotalSub.setText(totalCount == 1 ? getString(R.string.summary_booking_count_single) : getString(R.string.summary_bookings_count, totalCount));

        // Prepare Donut Chart Entries
        List<DonutEntry> chartEntries = new ArrayList<>();
        int completedColor = ContextCompat.getColor(requireContext(), R.color.solar_status_completed);
        int approvedColor = ContextCompat.getColor(requireContext(), R.color.solar_status_approved);
        int pendingColor = ContextCompat.getColor(requireContext(), R.color.solar_status_pending);
        int cancelledColor = ContextCompat.getColor(requireContext(), R.color.solar_status_cancelled);
        int rejectedColor = ContextCompat.getColor(requireContext(), R.color.solar_status_rejected);

        if (completedKwh > 0) chartEntries.add(new DonutEntry(getString(R.string.summary_provided_to_grid), completedKwh, completedColor, completedCount));
        if (approvedKwh > 0) chartEntries.add(new DonutEntry(getString(R.string.summary_approved_scheduled), approvedKwh, approvedColor, approvedCount));
        if (pendingKwh > 0) chartEntries.add(new DonutEntry(getString(R.string.summary_pending_approval), pendingKwh, pendingColor, pendingCount));
        if (cancelledKwh > 0) chartEntries.add(new DonutEntry("Cancelled", cancelledKwh, cancelledColor, cancelledCount));
        if (rejectedKwh > 0) chartEntries.add(new DonutEntry("Rejected", rejectedKwh, rejectedColor, rejectedCount));

        chartEnergyDonut.setData(chartEntries, animateChart);

        // Render Carbon Offset & Green Impact
        renderCarbonOffset(completedKwh);

        // Render Top Trading Station
        renderTopTradingStation(stationAggregates, completedKwh);
    }

    private void renderCarbonOffset(double completedKwh) {
        // Calculate CO2 reduction and equivalent tree absorption
        double carbonOffsetKg = completedKwh * CO2_KG_PER_KWH;
        double treeYears = carbonOffsetKg / TREE_ANNUAL_CO2_KG;

        textCarbonOffsetKg.setText(String.format(Locale.US, "%.1f kg CO₂", carbonOffsetKg));
        textCarbonTreesEquivalent.setText(getString(R.string.summary_trees_equivalent, treeYears));
    }

    private void renderTopTradingStation(Map<String, double[]> stationAggregates, double totalCompletedKwh) {
        // Find top trading station by delivered energy
        String topStationId = null;
        double topKwh = 0;
        int topTransfers = 0;

        for (Map.Entry<String, double[]> entry : stationAggregates.entrySet()) {
            double kwh = entry.getValue()[0];
            int count = (int) entry.getValue()[1];
            if (kwh > topKwh || (kwh == topKwh && count > topTransfers)) {
                topStationId = entry.getKey();
                topKwh = kwh;
                topTransfers = count;
            }
        }

        if (topStationId != null && topKwh > 0) {
            StationNameResolver.bindStationName(textTopStationName, topStationId);
            layoutTopStationDetails.setVisibility(View.VISIBLE);
            textTopStationEnergy.setText(String.format(Locale.US, "%.1f kWh delivered", topKwh));
            textTopStationTransfers.setText(topTransfers == 1 ? getString(R.string.summary_top_station_transfers_single) : getString(R.string.summary_top_station_transfers, topTransfers));
            double share = totalCompletedKwh > 0 ? (topKwh / totalCompletedKwh) * 100.0 : 0.0;
            textTopStationShare.setText(getString(R.string.summary_top_station_share, share));
        } else {
            textTopStationName.setText(R.string.summary_top_station_empty);
            layoutTopStationDetails.setVisibility(View.GONE);
        }
    }

    private String formatCountWithPct(int count, double kwh, double totalKwh) {
        // Format subtitle text with count and overall percentage
        double pct = totalKwh > 0 ? (kwh / totalKwh) * 100.0 : 0.0;
        String countStr = count == 1 ? getString(R.string.summary_booking_count_single) : getString(R.string.summary_bookings_count, count);
        return String.format(Locale.US, "%s (%.1f%%)", countStr, pct);
    }

    private List<ReservationResponse> filterByPeriod(List<ReservationResponse> input, PeriodFilter period) {
        // Filter reservations according to selected time window
        if (input == null || input.isEmpty() || period == PeriodFilter.ALL_TIME) {
            return input != null ? input : new ArrayList<>();
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate;

        switch (period) {
            case THIS_WEEK:
                startDate = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                break;
            case THIS_MONTH:
                startDate = today.with(TemporalAdjusters.firstDayOfMonth());
                break;
            case LAST_30_DAYS:
                startDate = today.minusDays(30);
                break;
            case THIS_YEAR:
                startDate = today.with(TemporalAdjusters.firstDayOfYear());
                break;
            case ALL_TIME:
            default:
                return input;
        }

        long startEpochMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        List<ReservationResponse> result = new ArrayList<>();

        for (ReservationResponse res : input) {
            long resTimeMillis = parseReservationTime(res.getScheduledStartAtUtc());
            if (resTimeMillis >= startEpochMillis) {
                result.add(res);
            }
        }
        return result;
    }

    private long parseReservationTime(String timeIso) {
        // Parse ISO 8601 date string to epoch millis with fallback
        if (timeIso == null || timeIso.trim().isEmpty()) return 0;
        try {
            return Instant.parse(timeIso).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    private void setBusy(boolean value) {
        // Toggle loading progress indicator and refresh button state
        busy = value;
        progressSummary.setVisibility(value ? View.VISIBLE : View.GONE);
        buttonRefreshSummary.setEnabled(!value);
    }

    @Override
    public void onDestroyView() {
        // Clean up repository on view destruction
        if (repository != null) {
            repository.close();
        }
        busy = false;
        super.onDestroyView();
    }
}
