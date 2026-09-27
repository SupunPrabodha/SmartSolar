package com.smartsolar.mobile.ui.workspace;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.dto.ReservationDashboardSummaryResponse;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import com.smartsolar.mobile.ui.reservation.CreateReservationActivity;
import com.smartsolar.mobile.util.MobileNavigation.Section;
import com.smartsolar.mobile.util.ReservationUiUtils;

/** Shared role-aware Home content; switching tabs neither reloads profile nor counts. */
public final class HomeFragment extends WorkspaceFragment {
    private ReservationRepository repository;
    @Override protected int layout() { return R.layout.fragment_home; }
    @Override protected void bind(Bundle saved) {
        repository = new ReservationRepository(RetrofitClient.create(requireContext(), BuildConfig.API_BASE_URL, BuildConfig.DEBUG));
        findViewById(R.id.profileContent).setVisibility(View.VISIBLE);
        findViewById(R.id.buttonLogout).setOnClickListener(v -> workspace().logout());
        findViewById(R.id.buttonRefresh).setOnClickListener(v -> { retry(); workspace().verify(); });
        findViewById(R.id.buttonCurrentBookings).setOnClickListener(v -> workspace().openSection(Section.CURRENT));
        findViewById(R.id.buttonPendingBookings).setOnClickListener(v -> workspace().openSection(Section.PENDING));
        findViewById(R.id.buttonScanTransaction).setOnClickListener(v -> workspace().openScanner());
        findViewById(R.id.buttonModuleTwo).setOnClickListener(v -> {
            workspace().openSection(Section.MINE);
            startActivity(new Intent(requireContext(), CreateReservationActivity.class));
        });
        profile();
        if (memory.data != null) render((ReservationDashboardSummaryResponse) memory.data);
    }
    private void profile() {
        if (workspace().state().profile == null) return;
        com.smartsolar.mobile.data.remote.dto.UserResponse user = workspace().state().profile;
        ((TextView) findViewById(R.id.textWelcome)).setText(ReservationUiUtils.greeting() + ", " + user.getFullName());
        ReservationUiUtils.formatStatusBadge(findViewById(R.id.textAccountStatus), user.getStatus());
        boolean operator = "GridOperator".equals(user.getRole());
        ((TextView) findViewById(R.id.homeDescription)).setText(operator ? R.string.operator_home_description : R.string.prosumer_home_description);
        findViewById(R.id.buttonScanTransaction).setVisibility(operator ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonModuleTwo).setVisibility(operator ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.textSession)).setText(R.string.workspace_verified);
    }
    @Override protected void onWorkspaceReady() { profile(); }
    @Override protected void load() {
        memory.loading = true; findViewById(R.id.progress).setVisibility(View.VISIBLE);
        repository.getDashboardSummary((summary, error, code) -> {
            if (!alive()) return;
            memory.loading = false; findViewById(R.id.progress).setVisibility(View.GONE);
            if (code == 401) { workspace().openLogin(); return; }
            if (summary != null) { memory.data = summary; render(summary); }
            else ((TextView) findViewById(R.id.textMetricsStatus)).setText(error == 0 ? R.string.load_failed : error);
        });
    }
    private void render(ReservationDashboardSummaryResponse value) {
        ((TextView) findViewById(R.id.textPendingCount)).setText(String.valueOf(value.getPendingReservations()));
        ((TextView) findViewById(R.id.textApprovedFutureCount)).setText(String.valueOf(value.getApprovedFutureReservations()));
        ((TextView) findViewById(R.id.textMetricsStatus)).setText(getString(R.string.generated_at, ReservationUiUtils.formatTime(value.getGeneratedAtUtc())));
    }
    @Override public void onDestroyView() { if (repository != null) repository.close(); super.onDestroyView(); }
}
