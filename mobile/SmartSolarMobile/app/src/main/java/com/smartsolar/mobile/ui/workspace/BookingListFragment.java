package com.smartsolar.mobile.ui.workspace;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.dto.ReservationPageResponse;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import com.smartsolar.mobile.ui.reservations.ReservationAdapter;
import com.smartsolar.mobile.util.MobileNavigation.Section;
import java.util.HashMap;
import java.util.Map;

/** Current, Pending and History share one paged presentation and independent retained state. */
public final class BookingListFragment extends WorkspaceFragment {
    private ReservationRepository repository;
    private ReservationAdapter adapter;
    private Section section;
    private int page;
    public static BookingListFragment create(Section section) {
        BookingListFragment f = new BookingListFragment(); Bundle args = new Bundle();
        args.putString("section", section.name()); f.setArguments(args); return f;
    }
    @Override protected int layout() { return R.layout.fragment_booking_list; }
    @Override protected void bind(Bundle saved) {
        section = Section.valueOf(requireArguments().getString("section"));
        page = memory.values.getInt("page", 1);
        repository = new ReservationRepository(RetrofitClient.create(requireContext(), BuildConfig.API_BASE_URL, BuildConfig.DEBUG));
        RecyclerView list = findViewById(R.id.recyclerBookings);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ReservationAdapter(null); adapter.restoreExpanded(memory.values.getStringArrayList("expanded")); list.setAdapter(adapter);
        findViewById(R.id.buttonRefreshDetails).setOnClickListener(v -> retry());
        findViewById(R.id.buttonRetry).setOnClickListener(v -> retry());
        findViewById(R.id.buttonPrevPage).setOnClickListener(v -> { if (page > 1) { page--; retry(); } });
        findViewById(R.id.buttonNextPage).setOnClickListener(v -> { page++; retry(); });
        ((TextView) findViewById(R.id.textEmpty)).setText(section == Section.CURRENT ? R.string.no_current_bookings : section == Section.PENDING ? R.string.no_pending_bookings : R.string.no_booking_history);
        if (memory.data != null) render((ReservationPageResponse) memory.data);
    }
    @Override protected void load() {
        memory.loading = true; findViewById(R.id.progress).setVisibility(View.VISIBLE);
        findViewById(R.id.errorContainer).setVisibility(View.GONE);
        Map<String,String> query = new HashMap<>(); query.put("page", String.valueOf(page)); query.put("pageSize", "20");
        ReservationRepository.DashboardCallback<ReservationPageResponse> callback = (result, error, code) -> {
            if (!alive()) return;
            memory.loading = false; findViewById(R.id.progress).setVisibility(View.GONE);
            if (code == 401) { workspace().openLogin(); return; }
            if (result != null) { memory.data = result; render(result); }
            else {
                findViewById(R.id.errorContainer).setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.textError)).setText(error == 0 ? R.string.load_failed : error);
            }
        };
        if (section == Section.PENDING) repository.getPendingBookings(query, callback);
        else if (section == Section.HISTORY) repository.getBookingHistory(query, callback);
        else repository.getCurrentBookings(query, callback);
    }
    private void render(ReservationPageResponse result) {
        page = result.getPage(); memory.values.putInt("page", page); adapter.setItems(result.getItems());
        findViewById(R.id.textEmpty).setVisibility(result.getItems() == null || result.getItems().isEmpty() ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.textPageIndicator)).setText(getString(R.string.page_indicator, page));
        findViewById(R.id.paginationContainer).setVisibility(page > 1 || result.isHasMore() ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonPrevPage).setEnabled(page > 1);
        findViewById(R.id.buttonNextPage).setEnabled(result.isHasMore());
    }
    @Override public void onDestroyView() {
        memory.values.putStringArrayList("expanded", adapter.expanded());
        if (repository != null) repository.close(); super.onDestroyView();
    }
}
