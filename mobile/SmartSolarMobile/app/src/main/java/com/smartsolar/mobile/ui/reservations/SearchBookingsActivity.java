package com.smartsolar.mobile.ui.reservations;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
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
import com.smartsolar.mobile.data.repository.ReservationRepository;
import com.smartsolar.mobile.ui.auth.LoginActivity;
import java.util.HashMap;
import java.util.Map;

public final class SearchBookingsActivity extends AppCompatActivity {
    private ReservationRepository repository;
    private ReservationAdapter adapter;

    private EditText editReservationId;
    private EditText editProsumerNic;
    private EditText editStationId;
    private Spinner spinnerStatus;
    private Button buttonSearch;
    private Button buttonClear;

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

    private static final String[] STATUS_OPTIONS = {
            "All Statuses", "Pending", "Approved", "Rejected", "Cancelled", "Completed"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_search_bookings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.searchBookingsRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        editReservationId = findViewById(R.id.editReservationId);
        editProsumerNic = findViewById(R.id.editProsumerNic);
        editStationId = findViewById(R.id.editStationId);
        spinnerStatus = findViewById(R.id.spinnerStatus);
        buttonSearch = findViewById(R.id.buttonSearch);
        buttonClear = findViewById(R.id.buttonClear);

        progress = findViewById(R.id.progress);
        errorContainer = findViewById(R.id.errorContainer);
        textError = findViewById(R.id.textError);
        textEmpty = findViewById(R.id.textEmpty);
        buttonRetry = findViewById(R.id.buttonRetry);
        paginationContainer = findViewById(R.id.paginationContainer);
        buttonPrevPage = findViewById(R.id.buttonPrevPage);
        buttonNextPage = findViewById(R.id.buttonNextPage);
        textPageIndicator = findViewById(R.id.textPageIndicator);

        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, STATUS_OPTIONS);
        spinnerStatus.setAdapter(statusAdapter);

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

        buttonSearch.setOnClickListener(v -> performSearch(1));
        buttonClear.setOnClickListener(v -> clearFilters());
        buttonRetry.setOnClickListener(v -> performSearch(currentPage));
        buttonPrevPage.setOnClickListener(v -> {
            if (currentPage > 1) {
                performSearch(currentPage - 1);
            }
        });
        buttonNextPage.setOnClickListener(v -> performSearch(currentPage + 1));

        // Initial search to display existing records
        performSearch(1);
    }

    private void clearFilters() {
        editReservationId.setText("");
        editProsumerNic.setText("");
        editStationId.setText("");
        spinnerStatus.setSelection(0);
        performSearch(1);
    }

    private void performSearch(int page) {
        if (repository == null) return;
        setBusy(true);

        Map<String, String> query = new HashMap<>();
        String resId = editReservationId.getText() != null ? editReservationId.getText().toString().trim() : "";
        String nic = editProsumerNic.getText() != null ? editProsumerNic.getText().toString().trim() : "";
        String stationId = editStationId.getText() != null ? editStationId.getText().toString().trim() : "";
        String status = spinnerStatus.getSelectedItem() != null ? spinnerStatus.getSelectedItem().toString() : "";

        if (!resId.isEmpty()) query.put("reservationId", resId);
        if (!nic.isEmpty()) query.put("prosumerNic", nic);
        if (!stationId.isEmpty()) query.put("stationId", stationId);
        if (!status.isEmpty() && !"All Statuses".equals(status)) query.put("status", status);

        query.put("page", String.valueOf(page));
        query.put("pageSize", String.valueOf(pageSize));

        repository.searchBookings(query, (result, errorResource, httpStatusCode) -> {
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
        });
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
}
