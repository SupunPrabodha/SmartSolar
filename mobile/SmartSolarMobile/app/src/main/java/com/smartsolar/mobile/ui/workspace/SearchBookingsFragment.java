package com.smartsolar.mobile.ui.workspace;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.dto.ReservationPageResponse;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import com.smartsolar.mobile.ui.reservations.ReservationAdapter;
import java.util.HashMap;
import java.util.Map;

public final class SearchBookingsFragment extends WorkspaceFragment {
    private ReservationRepository repository;
    private ReservationAdapter adapter;
    private EditText reservationId, nic, station;
    private Spinner status;
    @Override protected int layout() { return R.layout.fragment_search_bookings; }
    @Override protected void bind(Bundle saved) {
        reservationId = findViewById(R.id.searchReservationId); nic = findViewById(R.id.searchProsumerNic); station = findViewById(R.id.searchStationId);
        status = findViewById(R.id.searchStatus);
        status.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, new String[]{"All statuses", "Pending", "Approved", "Rejected", "Cancelled", "Completed"}));
        adapter = new ReservationAdapter(null); adapter.restoreExpanded(memory.values.getStringArrayList("expanded"));
        RecyclerView list = findViewById(R.id.searchResults); list.setLayoutManager(new LinearLayoutManager(requireContext())); list.setAdapter(adapter);
        repository = new ReservationRepository(RetrofitClient.create(requireContext(), BuildConfig.API_BASE_URL, BuildConfig.DEBUG));
        findViewById(R.id.searchNicField).setVisibility("GridOperator".equals(workspace().role()) ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonSearch).setOnClickListener(v -> retry());
        findViewById(R.id.buttonClear).setOnClickListener(v -> { reservationId.setText(""); nic.setText(""); station.setText(""); status.setSelection(0); retry(); });
        if (memory.data != null) render((ReservationPageResponse) memory.data);
    }
    @Override protected void onWorkspaceReady() { findViewById(R.id.searchNicField).setVisibility("GridOperator".equals(workspace().role()) ? View.VISIBLE : View.GONE); }
    @Override protected void load() {
        Map<String,String> filters = new HashMap<>();
        put(filters, "reservationId", reservationId); put(filters, "stationId", station);
        if ("GridOperator".equals(workspace().role())) put(filters, "prosumerNic", nic);
        if (status.getSelectedItemPosition() > 0) filters.put("status", String.valueOf(status.getSelectedItem()));
        filters.put("page", "1"); filters.put("pageSize", "20");
        memory.loading = true; findViewById(R.id.searchProgress).setVisibility(View.VISIBLE);
        repository.searchBookings(filters, (result, error, code) -> {
            if (!alive()) return;
            memory.loading = false; findViewById(R.id.searchProgress).setVisibility(View.GONE);
            if (code == 401) { workspace().openLogin(); return; }
            if (result != null) { memory.data = result; render(result); }
            else ((TextView) findViewById(R.id.searchMessage)).setText(error == 0 ? R.string.load_failed : error);
        });
    }
    private void render(ReservationPageResponse result) {
        adapter.setItems(result.getItems());
        ((TextView) findViewById(R.id.searchMessage)).setText(result.getItems() == null || result.getItems().isEmpty() ? getString(R.string.no_matching_bookings) : "");
    }
    private static void put(Map<String,String> map, String key, EditText input) { String value = input.getText().toString().trim(); if (!value.isEmpty()) map.put(key,value); }
    @Override public void onDestroyView() {
        memory.values.putStringArrayList("expanded", adapter.expanded());
        if (repository != null) repository.close(); super.onDestroyView();
    }
}
