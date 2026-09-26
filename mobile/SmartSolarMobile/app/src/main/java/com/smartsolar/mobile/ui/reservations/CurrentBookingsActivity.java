package com.smartsolar.mobile.ui.reservations;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.ReservationPageResponse;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import com.smartsolar.mobile.ui.auth.LoginActivity;
import java.util.HashMap;
import java.util.Map;

public final class CurrentBookingsActivity extends AppCompatActivity {
    private ReservationRepository repository;
    private ReservationAdapter adapter;
    private View progress;
    private View errorContainer;
    private TextView textError;
    private TextView textEmpty;
    private Button buttonRetry;
    private View paginationContainer;
    private Button buttonPrevPage;
    private Button buttonNextPage;
    private TextView textPageIndicator;

    private int currentPage = 1;
    private final int pageSize = 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_current_bookings);
        boolean pendingView = getIntent().getBooleanExtra("pendingView", false);
        ((TextView) findViewById(R.id.currentBookingsTitle)).setText(pendingView ? R.string.pending_bookings : R.string.current_bookings);
        ((TextView) findViewById(R.id.textEmpty)).setText(pendingView ? R.string.no_pending_bookings : R.string.no_current_bookings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.currentBookingsRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        progress = findViewById(R.id.progress);
        errorContainer = findViewById(R.id.errorContainer);
        textError = findViewById(R.id.textError);
        textEmpty = findViewById(R.id.textEmpty);
        buttonRetry = findViewById(R.id.buttonRetry);
        paginationContainer = findViewById(R.id.paginationContainer);
        buttonPrevPage = findViewById(R.id.buttonPrevPage);
        buttonNextPage = findViewById(R.id.buttonNextPage);
        textPageIndicator = findViewById(R.id.textPageIndicator);

        RecyclerView recyclerView = findViewById(R.id.recyclerBookings);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReservationAdapter(null);
        recyclerView.setAdapter(adapter);

        try {
            ApiService api = RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
            repository = new ReservationRepository(api);
        } catch (IllegalArgumentException e) {
            finish();
            return;
        }

        buttonRetry.setOnClickListener(v -> loadData(currentPage));
        buttonPrevPage.setOnClickListener(v -> {
            if (currentPage > 1) {
                loadData(currentPage - 1);
            }
        });
        buttonNextPage.setOnClickListener(v -> loadData(currentPage + 1));

        Button buttonRefreshDetails = findViewById(R.id.buttonRefreshDetails);
        if (buttonRefreshDetails != null) {
            buttonRefreshDetails.setOnClickListener(v -> loadData(currentPage));
        }

        Button buttonBackHome = findViewById(R.id.buttonBackHome);
        if (buttonBackHome != null) {
            buttonBackHome.setOnClickListener(v -> finish());
        }
        Button buttonPendingBookings = findViewById(R.id.buttonPendingBookings);
        if (buttonPendingBookings != null) buttonPendingBookings.setOnClickListener(v -> { if (!getIntent().getBooleanExtra("pendingView", false)) { startActivity(new Intent(this, CurrentBookingsActivity.class).putExtra("pendingView", true)); finish(); } });
        Button buttonSearchBookings = findViewById(R.id.buttonSearchBookings);
        if (buttonSearchBookings != null) buttonSearchBookings.setOnClickListener(v -> com.smartsolar.mobile.ui.common.WorkspaceChrome.navigate(this, com.smartsolar.mobile.util.MobileNavigation.Destination.SEARCH));

        findViewById(R.id.buttonCurrentView).setOnClickListener(v -> {
            if (getIntent().getBooleanExtra("pendingView", false)) { startActivity(new Intent(this, CurrentBookingsActivity.class)); finish(); }
        });
        findViewById(R.id.buttonHistoryView).setOnClickListener(v -> com.smartsolar.mobile.ui.common.WorkspaceChrome.navigate(this, com.smartsolar.mobile.util.MobileNavigation.Destination.HISTORY));
        loadData(1);
    }

    private void loadData(int page) {
        if (repository == null) return;
        setBusy(true);
        Map<String, String> query = new HashMap<>();
        query.put("page", String.valueOf(page));
        query.put("pageSize", String.valueOf(pageSize));

        boolean pendingView = getIntent().getBooleanExtra("pendingView", false);
        if (pendingView) repository.getPendingBookings(query, this::handleResult);
        else repository.getCurrentBookings(query, this::handleResult);
    }

    private void handleResult(ReservationPageResponse result, int errorResource, int httpStatusCode) {
            if (isFinishing() || isDestroyed()) return;
            setBusy(false);

            if (httpStatusCode == 401) {
                openLogin();
                return;
            }

            if (errorResource != 0 || result == null) {
                errorContainer.setVisibility(View.VISIBLE);
                textError.setText(errorResource != 0 ? errorResource : R.string.load_failed);
                adapter.setItems(null);
                paginationContainer.setVisibility(View.GONE);
                return;
            }

            currentPage = result.getPage();
            boolean hasMore = result.isHasMore();

            textPageIndicator.setText(getString(R.string.page_indicator, currentPage));
            buttonPrevPage.setEnabled(currentPage > 1);
            buttonNextPage.setEnabled(hasMore);
            paginationContainer.setVisibility(currentPage > 1 || hasMore ? View.VISIBLE : View.GONE);

            if (result.getItems() != null && !result.getItems().isEmpty()) {
                textEmpty.setVisibility(View.GONE);
                adapter.setItems(result.getItems());
            } else {
                adapter.setItems(null);
                textEmpty.setVisibility(View.VISIBLE);
            }
        }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            errorContainer.setVisibility(View.GONE);
            textEmpty.setVisibility(View.GONE);
        }
    }

    private void openLogin() {
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    @Override
    protected void onDestroy() {
        if (repository != null) repository.close();
        super.onDestroy();
    }
    @Override protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        com.smartsolar.mobile.ui.common.WorkspaceChrome.attach(this, getString(R.string.nav_bookings), com.smartsolar.mobile.util.MobileNavigation.Destination.BOOKINGS);
    }
}
