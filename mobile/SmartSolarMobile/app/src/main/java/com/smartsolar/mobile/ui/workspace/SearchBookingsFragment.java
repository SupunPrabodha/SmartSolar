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
    private int searchGeneration;
    private int page = 1;
    private Map<String,String> activeFilters;
    @Override protected int layout() { return R.layout.fragment_search_bookings; }
    @Override protected void bind(Bundle saved) {
        androidx.core.view.ViewCompat.setAccessibilityHeading(findViewById(R.id.polishFilterHeading),true);
        androidx.core.view.ViewCompat.setAccessibilityHeading(findViewById(R.id.polishResultHeading),true);
        reservationId = findViewById(R.id.searchReservationId); nic = findViewById(R.id.searchProsumerNic); station = findViewById(R.id.searchStationId);
        status = findViewById(R.id.searchStatus);
        status.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_filter_choice, new String[]{"All statuses", "Pending", "Approved", "Rejected", "Cancelled", "Completed"}));
        adapter = new ReservationAdapter(null); adapter.restoreExpanded(memory.values.getStringArrayList("expanded"));
        RecyclerView list = findViewById(R.id.searchResults); list.setLayoutManager(new LinearLayoutManager(requireContext())); list.setAdapter(adapter);
        repository = new ReservationRepository(RetrofitClient.create(requireContext(), BuildConfig.API_BASE_URL, BuildConfig.DEBUG));
        findViewById(R.id.searchNicField).setVisibility("GridOperator".equals(workspace().role()) ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonSearch).setOnClickListener(v -> { page = 1; activeFilters = null; retry(); });
        findViewById(R.id.buttonClear).setOnClickListener(v -> { reservationId.setText(""); nic.setText(""); station.setText(""); status.setSelection(0); page = 1; activeFilters = null; retry(); });
        page = memory.values.getInt("searchPage", 1);
        Bundle stored = memory.values.getBundle("searchFilters");
        if (stored != null) {
            activeFilters = new HashMap<>();
            for (String key : stored.keySet()) activeFilters.put(key, stored.getString(key, ""));
            reservationId.setText(activeFilters.getOrDefault("reservationId", ""));
            station.setText(activeFilters.getOrDefault("stationId", ""));
            nic.setText(activeFilters.getOrDefault("prosumerNic", ""));
            String[] states = {"", "Pending", "Approved", "Rejected", "Cancelled", "Completed"};
            for (int index = 0; index < states.length; index++)
                if (states[index].equals(activeFilters.getOrDefault("status", ""))) status.setSelection(index);
        }
        findViewById(R.id.searchPrevious).setOnClickListener(v -> { if (page > 1 && !memory.loading) { page--; retry(); } });
        findViewById(R.id.searchNext).setOnClickListener(v -> { if (!memory.loading && memory.data instanceof ReservationPageResponse
                && ((ReservationPageResponse) memory.data).isHasMore()) { page++; retry(); } });
        if (memory.data != null) render((ReservationPageResponse) memory.data);
    }
    @Override protected void onWorkspaceReady() { findViewById(R.id.searchNicField).setVisibility("GridOperator".equals(workspace().role()) ? View.VISIBLE : View.GONE); }
    @Override protected void load() {
        final int generation = ++searchGeneration;
        if (activeFilters == null) {
            activeFilters = new HashMap<>();
            put(activeFilters, "reservationId", reservationId); put(activeFilters, "stationId", station);
            if ("GridOperator".equals(workspace().role())) put(activeFilters, "prosumerNic", nic);
            if (status.getSelectedItemPosition() > 0) activeFilters.put("status", String.valueOf(status.getSelectedItem()));
        }
        Map<String,String> filters = new HashMap<>(activeFilters);
        if (!"GridOperator".equals(workspace().role())) filters.remove("prosumerNic");
        filters.put("page", String.valueOf(page)); filters.put("pageSize", "20");
        memory.data = null; adapter.setItems(java.util.Collections.emptyList());
        ((TextView) findViewById(R.id.searchMessage)).setText("");
        findViewById(R.id.searchPrevious).setEnabled(false); findViewById(R.id.searchNext).setEnabled(false);
        memory.loading = true; findViewById(R.id.searchProgress).setVisibility(View.VISIBLE);
        ((TextView)findViewById(R.id.buttonSearch)).setText(R.string.visual_loading_results);
        com.smartsolar.mobile.ui.reservations.ReferenceSearch.search((query, callback) -> {
            if (alive() && generation == searchGeneration) repository.searchBookings(query, callback);
        }, filters, (result, error, code) -> {
            if (!alive() || generation != searchGeneration) return;
            memory.loading = false; findViewById(R.id.searchProgress).setVisibility(View.GONE);
            ((TextView)findViewById(R.id.buttonSearch)).setText(R.string.search_button);
            if (code == 401) { workspace().openLogin(); return; }
            if (result != null) { memory.data = result; render(result); }
            else { ((TextView) findViewById(R.id.searchMessage)).setText(error == 0 ? R.string.load_failed : error);
                findViewById(R.id.searchPrevious).setEnabled(page > 1); }
        });
    }
    private void render(ReservationPageResponse result) {
        adapter.setItems(result.getItems());
        findViewById(R.id.searchPrevious).setEnabled(page > 1);
        findViewById(R.id.searchNext).setEnabled(result.isHasMore());
        ((TextView) findViewById(R.id.searchPageLabel)).setText(getString(R.string.search_page_label, page));
        ((TextView) findViewById(R.id.searchMessage)).setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_nav_search,0,0,0);
        ((TextView) findViewById(R.id.searchMessage)).setText(result.getItems() == null || result.getItems().isEmpty() ? getString(R.string.polish_search_empty) : getResources().getQuantityString(R.plurals.polish_results_count,result.getItems().size(),result.getItems().size()));
    }
    private static void put(Map<String,String> map, String key, EditText input) { String value = input.getText().toString().trim(); if (!value.isEmpty()) map.put(key,value); }
    @Override public void onDestroyView() {
        searchGeneration++;
        memory.values.putInt("searchPage", page);
        if (activeFilters != null) {
            Bundle stored = new Bundle();
            for (Map.Entry<String,String> entry : activeFilters.entrySet()) stored.putString(entry.getKey(), entry.getValue());
            memory.values.putBundle("searchFilters", stored);
        }
        memory.values.putStringArrayList("expanded", adapter.expanded());
        if (repository != null) repository.close(); super.onDestroyView();
    }
}
