package com.smartsolar.mobile.ui.common;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.remote.dto.UserResponse;
import com.smartsolar.mobile.data.repository.AuthRepository;
import com.smartsolar.mobile.ui.auth.LoginActivity;
import com.smartsolar.mobile.ui.home.HomeActivity;
import com.smartsolar.mobile.ui.account.AccountActivity;
import com.smartsolar.mobile.ui.stations.StationDiscoveryActivity;
import com.smartsolar.mobile.ui.reservation.ReservationDetailsActivity;
import com.smartsolar.mobile.ui.reservations.*;
import com.smartsolar.mobile.util.MobileNavigation;
import com.smartsolar.mobile.util.MobileNavigation.Destination;
import com.smartsolar.mobile.util.SessionManager;
import java.util.function.Consumer;

/** Adds native chrome around existing Activities without owning transaction or session state. */
public final class WorkspaceChrome {
    private WorkspaceChrome() { }

    public static void attach(AppCompatActivity activity, String title, Destination screen) {
        attach(activity, title, screen, null);
    }

    public static void attach(AppCompatActivity activity, String title, Destination screen, Consumer<UserResponse> profile) {
        androidx.activity.EdgeToEdge.enable(activity);
        ViewGroup host = activity.findViewById(android.R.id.content);
        View original = host.getChildAt(0);
        if (original == null) return;
        host.removeView(original);
        // A single outer inset owner avoids double system-bar padding across legacy layouts.
        ViewCompat.setOnApplyWindowInsetsListener(original, null);
        View shell = activity.getLayoutInflater().inflate(R.layout.view_workspace_shell, host, false);
        ((FrameLayout) shell.findViewById(R.id.workspaceContent)).addView(original,
                new FrameLayout.LayoutParams(-1, -1));
        host.addView(shell);
        MaterialToolbar toolbar = shell.findViewById(R.id.workspaceToolbar);
        toolbar.setTitle(title);
        if (screen == null) {
            toolbar.setNavigationIcon(R.drawable.ic_nav_back);
            toolbar.setNavigationContentDescription(R.string.navigate_back);
            toolbar.setNavigationOnClickListener(v -> activity.getOnBackPressedDispatcher().onBackPressed());
        }
        View surface = shell.findViewById(R.id.workspaceNavSurface);
        ViewCompat.setOnApplyWindowInsetsListener(shell, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            // Keyboard owns the lower area while editing; the bar returns when it closes.
            if (surface.getTag() != null) surface.setVisibility(insets.isVisible(WindowInsetsCompat.Type.ime()) ? View.GONE : View.VISIBLE);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(shell);
        if (screen == null) return;
        BottomNavigationView navigation = shell.findViewById(R.id.workspaceBottomNav);
        navigation.setOnItemReselectedListener(item -> {
            Destination selected = Destination.values()[item.getItemId() - 1];
            if (selected != screen) navigate(activity, selected);
        });
        final AuthRepository auth;
        try {
            auth = new AuthRepository(RetrofitClient.create(activity, BuildConfig.API_BASE_URL, BuildConfig.DEBUG), new SessionManager(activity));
        } catch (IllegalArgumentException exception) { return; }
        activity.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            private int generation;
            private boolean boundProfile;
            @Override public void onResume(LifecycleOwner owner) {
                final int request = ++generation;
                surface.setTag(null); surface.setVisibility(View.GONE);
                auth.restore((user, expiry, error) -> {
                    if (activity.isFinishing() || activity.isDestroyed() || request != generation) return;
                    if (user == null) {
                        if (error == 0 || error == R.string.session_expired || error == R.string.mobile_role_not_supported) {
                            activity.startActivity(new Intent(activity, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                            activity.finish();
                        }
                        return;
                    }
                    String role = user.getRole();
                    if (MobileNavigation.selection(role, screen) == null) {
                        navigate(activity, Destination.HOME);
                        return;
                    }
                    if (profile != null && !boundProfile) { profile.accept(user); boundProfile = true; }
                    navigation.setOnItemSelectedListener(null);
                    navigation.getMenu().clear();
                    for (Destination destination : MobileNavigation.destinations(role)) {
                        android.view.MenuItem item = navigation.getMenu().add(0, destination.ordinal() + 1, destination.ordinal(), label(activity, destination));
                        item.setIcon(icon(destination));
                        item.setContentDescription(label(activity, destination));
                    }
                    navigation.setSelectedItemId(MobileNavigation.selection(role, screen).ordinal() + 1);
                    navigation.setOnItemSelectedListener(item -> {
                        Destination next = Destination.values()[item.getItemId() - 1];
                        if (next == MobileNavigation.selection(role, screen)) return true;
                        navigate(activity, next); return false;
                    });
                    surface.setTag(Boolean.TRUE); surface.setVisibility(View.VISIBLE);
                    ViewCompat.requestApplyInsets(shell);
                });
            }
            @Override public void onPause(LifecycleOwner owner) { generation++; }
            @Override public void onDestroy(LifecycleOwner owner) { auth.close(); }
        });
    }

    public static void navigate(AppCompatActivity activity, Destination destination) {
        Class<?> target;
        switch (destination) {
            case STATIONS: target = StationDiscoveryActivity.class; break;
            case RESERVATIONS: target = ReservationDetailsActivity.class; break;
            case HISTORY: target = BookingHistoryActivity.class; break;
            case ACCOUNT: target = AccountActivity.class; break;
            case SCAN: target = QrScannerActivity.class; break;
            case BOOKINGS: target = CurrentBookingsActivity.class; break;
            case SEARCH: target = SearchBookingsActivity.class; break;
            default: target = HomeActivity.class;
        }
        if (activity.getClass() == target) return;
        Intent intent = new Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (destination == Destination.HOME) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        // Keep Home as the stable Back anchor; camera and transactional screens retain their caller.
        if (!(activity instanceof HomeActivity) && destination != Destination.SCAN) activity.finish();
    }

    private static int icon(Destination d) {
        switch (d) {
            case HOME: return R.drawable.ic_nav_home;
            case STATIONS: return R.drawable.ic_nav_stations;
            case ACCOUNT: return R.drawable.ic_nav_account;
            case HISTORY: return R.drawable.ic_nav_history;
            case SCAN: return R.drawable.ic_nav_scan;
            case SEARCH: return R.drawable.ic_nav_search;
            default: return R.drawable.ic_nav_bookings;
        }
    }
    private static String label(AppCompatActivity a, Destination d) {
        switch (d) {
            case HOME: return a.getString(R.string.nav_home);
            case STATIONS: return a.getString(R.string.nav_stations);
            case RESERVATIONS: return a.getString(R.string.nav_reservations);
            case HISTORY: return a.getString(R.string.nav_history);
            case ACCOUNT: return a.getString(R.string.nav_account);
            case SCAN: return a.getString(R.string.nav_scan);
            case SEARCH: return a.getString(R.string.nav_search);
            default: return a.getString(R.string.nav_bookings);
        }
    }
}
