package com.smartsolar.mobile.ui.stations;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.dto.StationResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Reloads authoritative station details and read-only slot availability on every visit. */
public final class StationDetailActivity extends CatalogActivity {
    private LinearLayout content;
    private String stationId;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved); setup(R.layout.activity_station_detail);
        content = findViewById(R.id.stationDetails);
        stationId = getIntent().getStringExtra("stationId");
    }
    @Override protected void onVerified() {
        clearContent();
        if (stationId == null || stationId.isEmpty()) { message.setText(R.string.station_unavailable); return; }
        request(api.getStation(stationId), station -> {
            if (!station.isActive) { message.setText(R.string.station_unavailable); return; }
            add(station.name, true); add(station.address, false);
            add(getString(R.string.station_capacity, station.capacityKwh, station.totalBatterySlots), false);
            if (getIntent().hasExtra("distanceKm")) add(getString(R.string.station_distance_at_search, getIntent().getDoubleExtra("distanceKm", 0)), false);
            else add(getString(R.string.distance_unknown), false);
            add(getString(R.string.schedule_utc), true);
            String[] days = getResources().getStringArray(R.array.weekdays);
            if (station.operatingSchedule == null || station.operatingSchedule.isEmpty()) add(getString(R.string.schedule_missing), false);
            else for (StationResponse.OperatingDay day : station.operatingSchedule) {
                if (day.day >= 1 && day.day <= 7)
                    add(getString(R.string.schedule_day, days[day.day - 1], day.isClosed ? getString(R.string.closed_day) : day.opensAt + " - " + day.closesAt), false);
            }
            add(getString(R.string.slot_availability), true);
            add(getString(R.string.availability_note), false);
            request(api.stationSlots(stationId), slots -> {
                if (slots.isEmpty()) add(getString(R.string.no_slots), false);
                for (com.smartsolar.mobile.data.remote.dto.SlotResponse slot : slots) {
                    if (slot.isActive) add(getString(R.string.slot_summary, time(slot.startAtUtc), time(slot.endAtUtc), slot.availableSlots, slot.totalSlots), false);
                }
            });
        });
    }
    private String time(String value) {
        try { return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.getDefault()).withZone(ZoneOffset.UTC).format(Instant.parse(value)); }
        catch (RuntimeException exception) { return getString(R.string.time_unavailable); }
    }
    private void add(String text, boolean heading) {
        TextView view = (TextView) getLayoutInflater().inflate(R.layout.item_station_text, content, false);
        view.setText(text);
        if (heading) { view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge); androidx.core.view.ViewCompat.setAccessibilityHeading(view, true); }
        content.addView(view);
    }
    @Override protected void clearContent() { if (content != null) content.removeAllViews(); }
}
