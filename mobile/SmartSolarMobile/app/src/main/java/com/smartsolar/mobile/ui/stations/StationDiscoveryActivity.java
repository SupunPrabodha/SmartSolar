package com.smartsolar.mobile.ui.stations;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.dto.StationResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Displays API station records on Google Maps; coarse location is requested only on user action. */
public final class StationDiscoveryActivity extends CatalogActivity {
    private LinearLayout list;
    private TextView locationStatus;
    private GoogleMap map;
    private CancellationTokenSource locationToken;
    private final List<StationResponse> stations = new ArrayList<>();
    private final Map<String, Double> distances = new HashMap<>();
    private Double latitude, longitude;
    private int locationGeneration;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> {
            if (!visible) return;
            if (granted) locate();
            else { locationStatus.setText(R.string.location_denied); latitude = longitude = null; load(); }
        });
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved); setup(R.layout.activity_station_discovery);
        androidx.core.view.ViewCompat.setAccessibilityHeading(findViewById(R.id.discoveryTitle), true);
        list = findViewById(R.id.stationList); locationStatus = findViewById(R.id.locationStatus);
        if (saved != null && saved.containsKey("latitude")) { latitude = saved.getDouble("latitude"); longitude = saved.getDouble("longitude"); }
        findViewById(R.id.findNearby).setOnClickListener(v -> {
            if (!authorized) { verify(); return; }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate();
            else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
        });
        findViewById(R.id.showAllStations).setOnClickListener(v -> {
            cancelLocation(); latitude = longitude = null; locationStatus.setText(R.string.all_stations); load();
        });
        if (BuildConfig.MAPS_CONFIGURED) {
            SupportMapFragment fragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.stationMap);
            if (fragment == null) {
                fragment = SupportMapFragment.newInstance();
                getSupportFragmentManager().beginTransaction().replace(R.id.stationMap, fragment).commitNow();
            }
            fragment.getMapAsync(ready -> {
                map = ready; map.getUiSettings().setZoomControlsEnabled(true);
                map.setOnMarkerClickListener(marker -> { if (marker.getTag() instanceof String) openStation((String) marker.getTag()); return true; });
                renderMap();
            });
        } else {
            findViewById(R.id.stationMap).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.mapStatus)).setText(R.string.map_unavailable);
        }
    }
    @Override protected void onVerified() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            latitude = longitude = null;
        load();
    }
    private void load() {
        if (!authorized || list == null) return;
        resetRequests(); clearContent(); message.setText("");
        if (latitude != null && longitude != null) {
            locationStatus.setText(R.string.nearby_radius);
            request(api.nearbyStations(latitude, longitude, 25), rows -> {
                for (com.smartsolar.mobile.data.remote.dto.NearbyStationResponse row : rows) {
                    if (row.station != null && row.station.isActive) { stations.add(row.station); distances.put(row.station.stationId, row.distanceKm); }
                }
                render();
            });
        } else request(api.listStations(), rows -> { for (StationResponse row : rows) if (row.isActive) stations.add(row); render(); });
    }
    private void locate() {
        if (!authorized) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        cancelLocation();
        final int current = locationGeneration;
        locationToken = new CancellationTokenSource();
        locationStatus.setText(R.string.locating);
        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY).setMaxUpdateAgeMillis(30000).setDurationMillis(15000).build();
        LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(request, locationToken.getToken())
            .addOnSuccessListener(this, location -> {
                if (!visible || !authorized || current != locationGeneration) return;
                if (location == null) { latitude = longitude = null; locationStatus.setText(R.string.location_unavailable); }
                else { latitude = location.getLatitude(); longitude = location.getLongitude(); }
                load();
            }).addOnFailureListener(this, error -> {
                if (!visible || !authorized || current != locationGeneration) return;
                latitude = longitude = null; locationStatus.setText(R.string.location_unavailable); load();
            });
    }
    private void render() {
        list.removeAllViews();
        if (stations.isEmpty()) message.setText(R.string.no_stations);
        for (StationResponse station : stations) {
            View row = getLayoutInflater().inflate(R.layout.item_station, list, false);
            ((TextView) row.findViewById(R.id.stationName)).setText(station.name);
            androidx.core.view.ViewCompat.setAccessibilityHeading(row.findViewById(R.id.stationName), true);
            ((TextView) row.findViewById(R.id.stationSummary)).setText(getString(R.string.station_summary, station.address, station.capacityKwh, station.totalBatterySlots));
            TextView distance = row.findViewById(R.id.stationDistance);
            Double km = distances.get(station.stationId);
            distance.setText(km == null ? getString(R.string.distance_unknown) : getString(R.string.station_distance, km));
            row.findViewById(R.id.stationOpen).setOnClickListener(v -> openStation(station.stationId));
            list.addView(row);
        }
        renderMap();
    }
    private void renderMap() {
        if (map == null) return;
        map.clear();
        if (!authorized || stations.isEmpty()) return;
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (StationResponse station : stations) {
            LatLng point = new LatLng(station.latitude, station.longitude); bounds.include(point);
            com.google.android.gms.maps.model.Marker marker = map.addMarker(new MarkerOptions().position(point).title(station.name).snippet(station.address));
            if (marker != null) marker.setTag(station.stationId);
        }
        findViewById(R.id.stationMap).post(() -> {
            if (!visible || stations.isEmpty() || map == null) return;
            if (stations.size() == 1) map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(stations.get(0).latitude, stations.get(0).longitude), 13));
            else map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 60));
        });
    }
    private void openStation(String id) {
        if (!authorized) return;
        Intent intent = new Intent(this, StationDetailActivity.class).putExtra("stationId", id);
        Double km = distances.get(id); if (km != null) intent.putExtra("distanceKm", km);
        startActivity(intent);
    }
    @Override protected void clearContent() {
        stations.clear(); distances.clear();
        if (list != null) list.removeAllViews();
        if (map != null) map.clear();
    }
    private void cancelLocation() {
        locationGeneration++;
        if (locationToken != null) { locationToken.cancel(); locationToken = null; }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (latitude != null) { out.putDouble("latitude", latitude); out.putDouble("longitude", longitude); }
        super.onSaveInstanceState(out);
    }
    @Override protected void onStop() { cancelLocation(); super.onStop(); }
    @Override protected void onPostCreate(Bundle state) {
        super.onPostCreate(state);
        com.smartsolar.mobile.ui.common.WorkspaceChrome.attach(this, getString(R.string.find_stations), com.smartsolar.mobile.util.MobileNavigation.Destination.STATIONS);
    }
}
