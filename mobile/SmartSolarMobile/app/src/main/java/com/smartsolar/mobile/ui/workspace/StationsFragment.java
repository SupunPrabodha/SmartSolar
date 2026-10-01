package com.smartsolar.mobile.ui.workspace;
import com.smartsolar.mobile.ui.stations.StationDetailActivity;
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
public final class StationsFragment extends WorkspaceFragment {
    private LinearLayout list;
    private TextView locationStatus;
    private GoogleMap map;
    private boolean mapRendered;
    private CancellationTokenSource locationToken;
    private final List<StationResponse> stations = new ArrayList<>();
    private final Map<String, Double> distances = new HashMap<>();
    private Double latitude, longitude;
    private int locationGeneration;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(), granted -> {
            if (!alive() || !isResumed()) return;
            if (granted) locate();
            else { locationStatus.setText(R.string.location_denied); latitude = longitude = null; load(); }
        });
    private com.smartsolar.mobile.data.remote.api.ApiService api;
    private TextView message;
    private final List<retrofit2.Call<?>> calls = new ArrayList<>();
    private int requestGeneration;
    private boolean authorized() { return alive() && workspace().isVerified(); }
    @Override protected boolean bookingData() { return false; }
    @Override protected int layout() { return R.layout.fragment_stations; }
    @Override protected void bind(Bundle saved) {
        if (saved != null) memory.values.putBoolean("mapHidden", saved.getBoolean("mapHidden", false));
        findViewById(R.id.toggleStationMap).setOnClickListener(v -> {
            memory.values.putBoolean("mapHidden", !memory.values.getBoolean("mapHidden", false));
            updateMapVisibility();
        });
        updateMapVisibility();
        api = com.smartsolar.mobile.data.remote.RetrofitClient.create(requireContext(), BuildConfig.API_BASE_URL, BuildConfig.DEBUG);
        message = findViewById(R.id.catalogMessage);
        findViewById(R.id.catalogRetry).setOnClickListener(v -> retry());
        androidx.core.view.ViewCompat.setAccessibilityHeading(findViewById(R.id.discoveryTitle), true);
        list = findViewById(R.id.stationList); locationStatus = findViewById(R.id.locationStatus);
        Bundle coords = saved == null ? memory.values : saved;
        if (coords.containsKey("latitude")) { latitude = coords.getDouble("latitude"); longitude = coords.getDouble("longitude"); }
        if (memory.data instanceof Snapshot) {
            Snapshot snapshot = (Snapshot) memory.data;
            stations.addAll(snapshot.stations); distances.putAll(snapshot.distances);
        }
        findViewById(R.id.findNearby).setOnClickListener(v -> {
            showDiscoverySelection();
            if (!authorized()) { workspace().verify(); return; }
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate();
            else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
        });
        findViewById(R.id.showAllStations).setOnClickListener(v -> {
            cancelLocation(); latitude = longitude = null; locationStatus.setText(R.string.all_stations); load();
        });
        if (BuildConfig.MAPS_CONFIGURED) {
            SupportMapFragment fragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.stationMap);
            if (fragment == null) {
                fragment = SupportMapFragment.newInstance(new com.google.android.gms.maps.GoogleMapOptions().useViewLifecycleInFragment(true));
                getChildFragmentManager().beginTransaction().replace(R.id.stationMap, fragment).commitNow();
            }
            final int viewRequest = viewGeneration;
            fragment.getMapAsync(ready -> {
                if (!alive() || viewRequest != viewGeneration) return;
                map = ready; map.getUiSettings().setZoomControlsEnabled(true);
                map.setOnMarkerClickListener(marker -> { if (marker.getTag() instanceof String) openStation((String) marker.getTag()); return true; });
                renderMap();
            });
        } else {
            findViewById(R.id.stationMap).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.mapStatus)).setText(R.string.map_unavailable);
        }
        showDiscoverySelection();
        if (memory.data != null) render();
    }
    @Override protected void onWorkspaceReady() {
        if (map != null && !mapRendered) renderMap();
    }
    @Override protected void load() {
        if (!authorized() || list == null) return;
        resetRequests(); clearContent(); message.setText("");
        memory.loading = true; memory.refresh.attempted(0);
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) latitude = longitude = null;
        showDiscoverySelection();
        if (latitude != null && longitude != null) {
            locationStatus.setText(R.string.nearby_radius);
            request(api.nearbyStations(latitude, longitude, 25), rows -> {
                for (com.smartsolar.mobile.data.remote.dto.NearbyStationResponse row : rows) {
                    if (row.station != null && row.station.isActive) {
                        stations.add(row.station);
                        distances.put(row.station.stationId, row.distanceKm);
                        com.smartsolar.mobile.util.StationNameResolver.put(row.station.stationId, row.station.name);
                    }
                }
                render();
            });
        } else request(api.listStations(), rows -> {
            for (StationResponse row : rows) {
                if (row.isActive) {
                    stations.add(row);
                    com.smartsolar.mobile.util.StationNameResolver.put(row.stationId, row.name);
                }
            }
            render();
        });
    }
    private void locate() {
        if (!authorized()) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        cancelLocation();
        final int current = locationGeneration;
        locationToken = new CancellationTokenSource();
        locationStatus.setText(R.string.locating);
        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY).setMaxUpdateAgeMillis(30000).setDurationMillis(15000).build();
        LocationServices.getFusedLocationProviderClient(requireContext()).getCurrentLocation(request, locationToken.getToken())
            .addOnSuccessListener(location -> {
                if (!alive() || !isResumed() || !authorized() || current != locationGeneration) return;
                locationToken = null;
                if (location == null) { latitude = longitude = null; locationStatus.setText(R.string.location_unavailable); }
                else { latitude = location.getLatitude(); longitude = location.getLongitude(); }
                load();
            }).addOnFailureListener(error -> {
                if (!alive() || !isResumed() || !authorized() || current != locationGeneration) return;
                locationToken = null;
                latitude = longitude = null; locationStatus.setText(R.string.location_unavailable); load();
            });
    }
    private void showDiscoverySelection() {
        boolean nearby = latitude != null && longitude != null;
        ((com.google.android.material.button.MaterialButton)findViewById(R.id.findNearby)).setChecked(nearby);
        ((com.google.android.material.button.MaterialButton)findViewById(R.id.showAllStations)).setChecked(!nearby);
    }
    private void render() {
        showDiscoverySelection();
        list.removeAllViews();
        if (stations.isEmpty()) message.setText(R.string.no_stations);
        for (StationResponse station : stations) {
            View row = getLayoutInflater().inflate(R.layout.item_station, list, false);
            ((TextView) row.findViewById(R.id.stationName)).setText(station.name);
            androidx.core.view.ViewCompat.setAccessibilityHeading(row.findViewById(R.id.stationName), true);
            ((TextView) row.findViewById(R.id.stationSummary)).setText(station.address);
            ((TextView) row.findViewById(R.id.stationCapacity)).setText(getString(R.string.visual_station_capacity, station.capacityKwh));
            ((TextView) row.findViewById(R.id.stationSlots)).setText(getString(R.string.visual_station_slots, station.totalBatterySlots));
            TextView distance = row.findViewById(R.id.stationDistance);
            Double km = distances.get(station.stationId);
            distance.setText(km == null ? getString(R.string.distance_unknown) : getString(R.string.station_distance, km));
            row.findViewById(R.id.stationOpen).setOnClickListener(v -> openStation(station.stationId));
            list.addView(row);
        }
        memory.data = new Snapshot(stations, distances);
        renderMap();
    }
    private void updateMapVisibility() {
        boolean hidden = memory.values.getBoolean("mapHidden", false);
        findViewById(R.id.stationMapSurface).setVisibility(hidden ? View.GONE : View.VISIBLE);
        if (!hidden && !mapRendered) findViewById(R.id.stationMap).post(() -> { if (alive() && map != null) renderMap(); });
        ((com.google.android.material.button.MaterialButton)findViewById(R.id.toggleStationMap))
            .setText(hidden ? R.string.show_station_map : R.string.hide_station_map);
    }
    private void renderMap() {
        if (memory.values.getBoolean("mapHidden", false)) { mapRendered = false; return; }
        if (map == null) return;
        map.clear();
        if (!authorized()) return;
        mapRendered = true;
        if (stations.isEmpty()) return;
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (StationResponse station : stations) {
            LatLng point = new LatLng(station.latitude, station.longitude); bounds.include(point);
            com.google.android.gms.maps.model.Marker marker = map.addMarker(new MarkerOptions().position(point).title(station.name).snippet(station.address));
            if (marker != null) marker.setTag(station.stationId);
        }
        findViewById(R.id.stationMap).post(() -> {
            if (!alive() || stations.isEmpty() || map == null) return;
            if (memory.values.getBoolean("mapHidden", false)) { mapRendered = false; return; }
            com.google.android.gms.maps.model.CameraPosition camera = memory.values.getParcelable("camera");
            if (camera != null) { map.moveCamera(CameraUpdateFactory.newCameraPosition(camera)); memory.values.remove("camera"); }
            else if (stations.size() == 1) map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(stations.get(0).latitude, stations.get(0).longitude), 13));
            else map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 60));
        });
    }
    private void openStation(String id) {
        if (!authorized()) return;
        Intent intent = new Intent(requireContext(), StationDetailActivity.class).putExtra("stationId", id);
        Double km = distances.get(id); if (km != null) intent.putExtra("distanceKm", km);
        startActivity(intent);
    }
    private void clearContent() {
        stations.clear(); distances.clear();
        if (list != null) list.removeAllViews();
        if (map != null) map.clear();
    }
    private void cancelLocation() {
        locationGeneration++;
        if (locationToken != null) { locationToken.cancel(); locationToken = null; }
    }
    @Override public void onSaveInstanceState(Bundle out) {
        if (latitude != null) { out.putDouble("latitude", latitude); out.putDouble("longitude", longitude); }
        out.putBoolean("mapHidden", memory.values.getBoolean("mapHidden", false));
        super.onSaveInstanceState(out);
    }
    private void resetRequests() {
        requestGeneration++; for (retrofit2.Call<?> call : calls) call.cancel(); calls.clear();
    }
    private <T> void request(retrofit2.Call<T> call, java.util.function.Consumer<T> success) {
        final int request = requestGeneration;
        calls.add(call); findViewById(R.id.catalogProgress).setVisibility(View.VISIBLE);
        call.enqueue(new retrofit2.Callback<T>() {
            @Override public void onResponse(retrofit2.Call<T> c, retrofit2.Response<T> response) {
                calls.remove(c);
                if (!alive() || request != requestGeneration) { if (response.errorBody() != null) response.errorBody().close(); return; }
                memory.loading = false; findViewById(R.id.catalogProgress).setVisibility(View.GONE);
                if (response.code() == 401) workspace().openLogin();
                else if (response.isSuccessful() && response.body() != null) success.accept(response.body());
                else message.setText(R.string.catalog_request_failed);
                if (response.errorBody() != null) response.errorBody().close();
            }
            @Override public void onFailure(retrofit2.Call<T> c, Throwable error) {
                calls.remove(c);
                if (!alive() || request != requestGeneration || c.isCanceled()) return;
                memory.loading = false; findViewById(R.id.catalogProgress).setVisibility(View.GONE); message.setText(R.string.connection_failed);
            }
        });
    }
    @Override public void onPause() {
        if (locationToken != null && locationStatus != null) locationStatus.setText(R.string.location_explanation);
        cancelLocation(); super.onPause();
    }
    @Override public void onDestroyView() {
        cancelLocation(); resetRequests();
        if (map != null) memory.values.putParcelable("camera", map.getCameraPosition());
        if (latitude != null) { memory.values.putDouble("latitude", latitude); memory.values.putDouble("longitude", longitude); }
        else { memory.values.remove("latitude"); memory.values.remove("longitude"); }
        map = null; mapRendered = false; stations.clear(); distances.clear(); list = null;
        super.onDestroyView();
    }
    private static final class Snapshot {
        final List<StationResponse> stations;
        final Map<String,Double> distances;
        Snapshot(List<StationResponse> rows, Map<String,Double> km) { stations = new ArrayList<>(rows); distances = new HashMap<>(km); }
    }
}
