package com.smartsolar.mobile.ui.reservations;

import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.dto.ReservationPageResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import com.smartsolar.mobile.util.DisplayReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Resolves display references using existing role-scoped API reads, then submits real IDs. */
public final class ReferenceSearch {
    public interface PageLoader {
        void load(Map<String,String> query, ReservationRepository.DashboardCallback<ReservationPageResponse> callback);
    }
    private ReferenceSearch() { }
    public static void search(PageLoader loader, Map<String,String> filters,
            ReservationRepository.DashboardCallback<ReservationPageResponse> callback) {
        String reservation = filters.getOrDefault("reservationId", "").trim();
        String station = filters.getOrDefault("stationId", "").trim();
        if (reservation.isEmpty() && station.isEmpty()) { loader.load(filters, callback); return; }
        if ((!reservation.isEmpty() && !reservation.matches("(?i)REF-[0-9A-Z]{10}"))
                || (!station.isEmpty() && !station.matches("(?i)STN-[0-9A-Z]{10}"))) {
            callback.onComplete(null, R.string.reference_invalid, 0); return;
        }
        Map<String,String> scan = new HashMap<>(filters);
        scan.remove("reservationId"); scan.remove("stationId"); scan.put("pageSize", "100");
        scan(loader, filters, scan, 1, reservation, station, new HashSet<>(), new HashSet<>(), callback);
    }
    private static void scan(PageLoader loader, Map<String,String> original, Map<String,String> base,
            int page, String reservation, String station, Set<String> reservations, Set<String> stations,
            ReservationRepository.DashboardCallback<ReservationPageResponse> callback) {
        Map<String,String> query = new HashMap<>(base); query.put("page", String.valueOf(page));
        loader.load(query, (result, error, code) -> {
            if (result == null) { callback.onComplete(null, error, code); return; }
            for (ReservationResponse row : result.getItems()) {
                if (!reservation.isEmpty() && reservation.equalsIgnoreCase(DisplayReference.reservation(row.getReservationId())))
                    reservations.add(row.getReservationId());
                if (!station.isEmpty() && station.equalsIgnoreCase(DisplayReference.station(row.getStationId())))
                    stations.add(row.getStationId());
            }
            if (reservations.size() > 1 || stations.size() > 1) {
                callback.onComplete(null, R.string.reference_ambiguous, 0); return;
            }
            if (result.isHasMore()) {
                if (page >= 200) { callback.onComplete(null, R.string.reference_search_limit, 0); return; }
                scan(loader, original, base, page + 1, reservation, station, reservations, stations, callback);
                return;
            }
            if ((!reservation.isEmpty() && reservations.isEmpty()) || (!station.isEmpty() && stations.isEmpty())) {
                callback.onComplete(new ReservationPageResponse(java.util.Collections.emptyList(), 1, 20, false), 0, 200); return;
            }
            Map<String,String> resolved = new HashMap<>(original);
            if (!reservation.isEmpty()) resolved.put("reservationId", reservations.iterator().next());
            if (!station.isEmpty()) resolved.put("stationId", stations.iterator().next());
            loader.load(resolved, callback);
        });
    }
}
