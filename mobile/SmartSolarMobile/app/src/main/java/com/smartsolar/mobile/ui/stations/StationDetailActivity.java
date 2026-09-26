package com.smartsolar.mobile.ui.stations;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.dto.StationResponse;

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
            if (station.operatingSchedule == null || station.operatingSchedule.isEmpty()) add(getString(R.string.schedule_missing), false);
            else {
                boolean open = false;
                // Convert dated UTC recurrence intervals for the coming week; never alter stored schedule values.
                java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
                for (int offset = 0; offset < 7; offset++) {
                    java.time.LocalDate date = today.plusDays(offset);
                    for (StationResponse.OperatingDay day : station.operatingSchedule) {
                        if (day.day != date.getDayOfWeek().getValue() || day.isClosed) continue;
                        try {
                            java.time.LocalDateTime start = date.atTime(java.time.LocalTime.parse(day.opensAt));
                            java.time.LocalDateTime end = "24:00".equals(day.closesAt) ? date.plusDays(1).atStartOfDay() : date.atTime(java.time.LocalTime.parse(day.closesAt));
                            add(com.smartsolar.mobile.util.ReservationUiUtils.schedule(start.toInstant(java.time.ZoneOffset.UTC).toString(), end.toInstant(java.time.ZoneOffset.UTC).toString()), false);
                            open = true;
                        } catch (RuntimeException invalidSchedule) { add(getString(R.string.time_unavailable), false); }
                    }
                }
                if (!open) add(getString(R.string.closed_day), false);
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
        try { return com.smartsolar.mobile.util.ReservationUiUtils.formatUtc(value); }
        catch (RuntimeException exception) { return getString(R.string.time_unavailable); }
    }
    private void add(String text, boolean heading) {
        TextView view = (TextView) getLayoutInflater().inflate(R.layout.item_station_text, content, false);
        view.setText(text);
        if (heading) { view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge); androidx.core.view.ViewCompat.setAccessibilityHeading(view, true); }
        content.addView(view);
    }
    @Override protected void clearContent() { if (content != null) content.removeAllViews(); }
    @Override protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        com.smartsolar.mobile.ui.common.WorkspaceChrome.attach(this, getString(R.string.title_station_details), null);
    }
}
