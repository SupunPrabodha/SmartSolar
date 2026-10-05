package com.smartsolar.mobile.ui.stations;
import com.smartsolar.mobile.util.DisplayReference;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
import com.smartsolar.mobile.ui.common.SurfaceUi;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.dto.StationResponse;

/** Reloads authoritative station details and read-only slot availability on every visit. */
public final class StationDetailActivity extends CatalogActivity {
    private LinearLayout content;
    private String stationId;
    private LinearLayout section;
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
            section=SurfaceUi.card(content);SurfaceUi.tint(section,R.color.solar_surface_strong);
            android.widget.ImageView badge = new android.widget.ImageView(this);
            badge.setImageResource(R.drawable.ic_nav_stations);
            badge.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.solar_primary)));
            badge.setBackgroundResource(R.drawable.bg_visual_brand);
            int inset = SurfaceUi.dp(this,10); badge.setPadding(inset,inset,inset,inset);
            badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            section.addView(badge,new LinearLayout.LayoutParams(SurfaceUi.dp(this,48),SurfaceUi.dp(this,48)));
            SurfaceUi.heading(section,station.name,0).setTextSize(24);
            add(DisplayReference.station(station.stationId), false);
            add(station.address, false);
            if (getIntent().hasExtra("distanceKm")) add(getString(R.string.station_distance_at_search, getIntent().getDoubleExtra("distanceKm", 0)), false);
            else add(getString(R.string.distance_unknown), false);
            metrics(station);
            section=SurfaceUi.card(content);
            SurfaceUi.heading(section,getString(R.string.detail_hours),R.drawable.ic_nav_history);
            add(getString(R.string.detail_week),false);
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
                            LinearLayout hours=section;
                            section=SurfaceUi.card(hours);SurfaceUi.tint(section,R.color.solar_surface);
                            add(com.smartsolar.mobile.util.ReservationUiUtils.schedule(start.toInstant(java.time.ZoneOffset.UTC).toString(), end.toInstant(java.time.ZoneOffset.UTC).toString()), false);
                            section=hours;
                            open = true;
                        } catch (RuntimeException invalidSchedule) { add(getString(R.string.time_unavailable), false); }
                    }
                }
                if (!open) add(getString(R.string.detail_no_hours), false);
            }
            section=SurfaceUi.card(content);
            SurfaceUi.heading(section,getString(R.string.slot_availability),R.drawable.ic_nav_bookings);
            add(getString(R.string.availability_note), false);
            request(api.stationSlots(stationId), slots -> {
                boolean hasActiveSlots = false;
                for (com.smartsolar.mobile.data.remote.dto.SlotResponse slot : slots) {
                    if (slot.isActive) {
                        hasActiveSlots=true;
                        LinearLayout availability=section;
                        section=SurfaceUi.card(availability);SurfaceUi.tint(section,R.color.solar_surface);
                        add(DisplayReference.slot(slot.slotId),false);
                        add(getString(R.string.detail_starts,time(slot.startAtUtc)),true);
                        add(getString(R.string.detail_ends,time(slot.endAtUtc)),false);
                        SurfaceUi.pill(section,getString(R.string.detail_available,slot.availableSlots,slot.totalSlots),R.color.solar_primary,R.color.solar_surface_selected);
                        section=availability;
                    }
                }
                if (!hasActiveSlots) add(getString(R.string.no_slots), false);
            });
        });
    }
    private void metrics(StationResponse station) {
        LinearLayout tiles=new LinearLayout(this);tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.setBaselineAligned(false);content.addView(tiles,new LinearLayout.LayoutParams(-1,-2));
        metric(tiles,getString(R.string.detail_capacity_value,station.capacityKwh),R.string.detail_capacity);
        metric(tiles,String.valueOf(station.totalBatterySlots),R.string.detail_battery);
    }
    private void metric(LinearLayout tiles,String value,int label) {
        LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams spacing=new LinearLayout.LayoutParams(0,-2,1);spacing.setMarginEnd(SurfaceUi.dp(this,6));
        tiles.addView(column,spacing);
        LinearLayout tile=SurfaceUi.card(column);
        SurfaceUi.heading(tile,value,0).setTextSize(22);
        TextView caption=new TextView(this);caption.setText(label);caption.setTextSize(14);
        caption.setTextColor(getColor(R.color.solar_secondary));tile.addView(caption);
    }
    @Override protected void busy(boolean value) {
        super.busy(value);
        com.smartsolar.mobile.ui.common.LoadingSurface skeleton=findViewById(R.id.catalogProgress);
        skeleton.setLabel(content!=null&&content.getChildCount()>0?R.string.detail_loading_slots:R.string.visual_loading_stations);
    }
    private String time(String value) {
        try { return com.smartsolar.mobile.util.ReservationUiUtils.formatUtc(value); }
        catch (RuntimeException exception) { return getString(R.string.time_unavailable); }
    }
    private void add(String text, boolean heading) {
        TextView view = (TextView) getLayoutInflater().inflate(R.layout.item_station_text, section, false);
        view.setText(text);
        if (heading) { view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge); androidx.core.view.ViewCompat.setAccessibilityHeading(view, true); }
        section.addView(view);
    }
    @Override protected void clearContent() { if (content != null) content.removeAllViews(); }
    @Override protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        com.smartsolar.mobile.ui.common.DeepScreenChrome.attach(this, getString(R.string.title_station_details));
        View refresh=findViewById(R.id.catalogRetry);
        ((ViewGroup)refresh.getParent()).removeView(refresh);
        androidx.appcompat.widget.Toolbar.LayoutParams position=new androidx.appcompat.widget.Toolbar.LayoutParams(-2,-2,android.view.Gravity.END|android.view.Gravity.CENTER_VERTICAL);
        ((com.google.android.material.appbar.MaterialToolbar)findViewById(R.id.deepToolbar)).addView(refresh,position);
    }
}
