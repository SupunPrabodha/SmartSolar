package com.smartsolar.mobile.ui.reservations;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import java.util.HashMap;
import java.util.Map;

/** Searches authorized reservations through the server's exact filters. */
public final class SearchBookingsActivity extends AppCompatActivity {
    private ReservationRepository repository;
    private ReservationAdapter adapter;
    private EditText reservationId, nic, station;
    private Spinner status;
    private TextView message;
    private View progress;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_search_bookings);
        reservationId = findViewById(R.id.searchReservationId); nic = findViewById(R.id.searchProsumerNic); station = findViewById(R.id.searchStationId);
        status = findViewById(R.id.searchStatus); message = findViewById(R.id.searchMessage); progress = findViewById(R.id.searchProgress);
        status.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"All statuses", "Pending", "Approved", "Rejected", "Cancelled", "Completed"}));
        adapter = new ReservationAdapter(null); RecyclerView list = findViewById(R.id.searchResults); list.setLayoutManager(new LinearLayoutManager(this)); list.setAdapter(adapter);
        try { repository = new ReservationRepository(RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG)); } catch (IllegalArgumentException exception) { message.setText(R.string.api_not_configured); return; }
        findViewById(R.id.buttonSearch).setOnClickListener(v -> search());
        findViewById(R.id.buttonClear).setOnClickListener(v -> { reservationId.setText(""); nic.setText(""); station.setText(""); status.setSelection(0); search(); });
        search();
    }

    private void search() {
        Map<String, String> filters = new HashMap<>(); put(filters, "reservationId", reservationId); put(filters, "prosumerNic", nic); put(filters, "stationId", station);
        if (status.getSelectedItemPosition() > 0) filters.put("status", String.valueOf(status.getSelectedItem()));
        filters.put("page", "1"); filters.put("pageSize", "20"); progress.setVisibility(View.VISIBLE);
        repository.searchBookings(filters, (result, errorResource, httpStatusCode) -> { progress.setVisibility(View.GONE); if (result != null) { adapter.setItems(result.getItems()); if (result.getItems() == null || result.getItems().isEmpty()) message.setText(R.string.no_matching_bookings); else message.setText(""); } else message.setText(errorResource != 0 ? errorResource : R.string.load_failed); });
    }
    private static void put(Map<String, String> target, String key, EditText field) { String value = field.getText().toString().trim(); if (!value.isEmpty()) target.put(key, value); }
    @Override protected void onDestroy() { if (repository != null) repository.close(); super.onDestroy(); }
    @Override protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        com.smartsolar.mobile.ui.common.WorkspaceChrome.attach(this, getString(R.string.search_bookings), com.smartsolar.mobile.util.MobileNavigation.Destination.SEARCH, user -> findViewById(R.id.searchNicField).setVisibility("GridOperator".equals(user.getRole()) ? View.VISIBLE : View.GONE));
    }
}
