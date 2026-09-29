package com.smartsolar.mobile.ui.workspace;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.appbar.MaterialToolbar;
import com.smartsolar.mobile.BuildConfig;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.data.remote.RetrofitClient;
import com.smartsolar.mobile.data.repository.AuthRepository;
import com.smartsolar.mobile.ui.auth.LoginActivity;
import com.smartsolar.mobile.ui.reservations.QrScannerActivity;
import com.smartsolar.mobile.util.MobileNavigation;
import com.smartsolar.mobile.util.MobileNavigation.Destination;
import com.smartsolar.mobile.util.MobileNavigation.Section;
import com.smartsolar.mobile.util.SessionManager;

/** One authenticated Activity; add/show/hide retains destinations without Activity history. */
public final class WorkspaceActivity extends AppCompatActivity {
    public com.smartsolar.mobile.data.remote.dto.NotificationInbox inbox;
    private retrofit2.Call<com.smartsolar.mobile.data.remote.dto.NotificationInbox> inboxCall;
    private WorkspaceState state;
    private AuthRepository auth;
    private BottomNavigationView navigation;
    private MaterialToolbar toolbar;
    private View content, navSurface, gate;
    private Destination selected = Destination.HOME;
    private SharedPreferences preferences;
    private String sessionToken;
    private boolean verifying, foreground, openingLogin, openingScanner, needsVerification = true;
    private int generation;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable expire = this::logout;
    private final SharedPreferences.OnSharedPreferenceChangeListener sessionChanged = (prefs, key) -> {
        if (key == null || "access_token".equals(key)) {
            String current = prefs.getString("access_token", null);
            if (sessionToken == null || !sessionToken.equals(current)) openLogin();
        }
    };
    public static void returnToWorkspace(Activity source) {
        source.startActivity(new Intent(source, WorkspaceActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        source.finish();
    }
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        androidx.activity.EdgeToEdge.enable(this);
        state = new ViewModelProvider(this).get(WorkspaceState.class);
        setContentView(R.layout.activity_workspace);
        if (saved != null) {
            if (state.owner == null) state.owner = saved.getString("owner");
            try { selected = Destination.valueOf(saved.getString("destination", "HOME")); }
            catch (IllegalArgumentException ignored) { selected = Destination.HOME; }
        }
        toolbar = findViewById(R.id.workspaceToolbar);
        toolbar.getMenu().add(0, 1001, 0, "My Profile").setIcon(R.drawable.ic_nav_account).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
        android.view.MenuItem notifications=toolbar.getMenu().add(0, 1002, 1, "Notifications").setIcon(R.drawable.ic_ui_bell);
        notifications.setActionView(R.layout.view_notification_action);notifications.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        notifications.getActionView().setOnClickListener(v->{if(isVerified())com.smartsolar.mobile.ui.account.AccountExperienceActivity.open(this,"inbox");});
        toolbar.setOnMenuItemClickListener(item -> {
            if (!isVerified()) return true;
            com.smartsolar.mobile.ui.account.AccountExperienceActivity.open(this, item.getItemId() == 1001 ? "profile" : "inbox");
            return true;
        });
        navigation = findViewById(R.id.workspaceBottomNav);
        content = findViewById(R.id.workspaceContent);
        navSurface = findViewById(R.id.workspaceNavSurface);
        gate = findViewById(R.id.workspaceGate);
        preferences = getSharedPreferences("smart_solar_session", MODE_PRIVATE);
        sessionToken = preferences.getString("access_token", null);
        preferences.registerOnSharedPreferenceChangeListener(sessionChanged);
        try { auth = new AuthRepository(RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG), new SessionManager(this)); }
        catch (IllegalArgumentException error) { openLogin(); return; }
        findViewById(R.id.workspaceRetry).setOnClickListener(v -> verify());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.workspaceRoot), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            navSurface.setVisibility(isVerified() && !insets.isVisible(WindowInsetsCompat.Type.ime()) ? View.VISIBLE : View.GONE);
            return WindowInsetsCompat.CONSUMED;
        });
        navigation.setOnItemReselectedListener(item -> { /* A reselect is intentionally a no-op. */ });
        navigation.setOnItemSelectedListener(item -> {
            Destination next = Destination.values()[item.getItemId() - 1];
            if (!isVerified()) return false;
            if (next == Destination.SCAN) { openScanner(); return false; }
            if (MobileNavigation.shouldSwitch(role(), selected, next)) select(next);
            return true;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (!isVerified()) { moveTaskToBack(true); return; }
                Destination back = MobileNavigation.back(selected);
                if (back != null) select(back); else moveTaskToBack(true);
            }
        });
        // A retained profile is valid only for this same in-memory session on configuration recreation.
        if (saved != null && state.verified && sessionToken != null && preferences.getLong("expires_at", 0) > System.currentTimeMillis()) { needsVerification = false; showWorkspace(); }
    }
    public WorkspaceState state() { return state; }
    public boolean isVerified() { return state != null && state.verified && !openingLogin; }
    public String role() { return state.profile == null ? "" : state.profile.getRole(); }
    @Override protected void onStart() {
        super.onStart(); foreground = true; state.bookingRevision = WorkspaceChanges.bookings();
        if (needsVerification) verify(); else scheduleExpiry();
    }
    public void verify() {
        if (auth == null || verifying || openingLogin) return;
        verifying = true; state.verified = false; gate.setVisibility(View.VISIBLE);
        content.setVisibility(View.INVISIBLE); navSurface.setVisibility(View.GONE);
        ((TextView) findViewById(R.id.workspaceMessage)).setText(R.string.checking_session);
        findViewById(R.id.workspaceRetry).setEnabled(false);
        final int request = ++generation;
        auth.restore((user, expiry, error) -> {
            if (isFinishing() || isDestroyed() || request != generation) return;
            verifying = false; findViewById(R.id.workspaceRetry).setEnabled(true);
            if (!foreground) { needsVerification = true; state.verified = false; return; }
            if (user == null) {
                if (error == 0 || error == R.string.session_expired || error == R.string.mobile_role_not_supported) { openLogin(); return; }
                ((TextView) findViewById(R.id.workspaceMessage)).setText(error);
                scheduleExpiry(); return;
            }
            String owner = user.getNic() + "/" + user.getRole();
            if (state.owner != null && !owner.equals(state.owner)) {
                state.clear(); startActivity(new Intent(this, WorkspaceActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish(); return;
            }
            state.owner = owner; state.profile = user; state.verified = true;
            needsVerification = false; showWorkspace(); scheduleExpiry();
        });
    }
    private void showWorkspace() {
        gate.setVisibility(View.GONE); content.setVisibility(View.VISIBLE);
        navigation.getMenu().clear();
        for (Destination d : MobileNavigation.destinations(role())) navigation.getMenu().add(0, d.ordinal()+1, d.ordinal(), d == Destination.HOME ? R.string.nav_home : title(d)).setIcon(icon(d));
        if (!MobileNavigation.destinations(role()).contains(selected) || selected == Destination.SCAN) selected = Destination.HOME;
        show(selected); ViewCompat.requestApplyInsets(findViewById(R.id.workspaceRoot));
        notifyReady(getSupportFragmentManager());
        loadInbox();
        if (selected == Destination.HOME && !state.profile.isProfileComplete() && !preferences.getBoolean("profile_prompt:" + state.profile.getNic(), false)) {
            preferences.edit().putBoolean("profile_prompt:" + state.profile.getNic(), true).apply();
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Make this workspace yours")
                .setMessage("Add your photo and confirm your contact details. You can continue using the workspace now.")
                .setNegativeButton("Skip for this session", null)
                .setPositiveButton("Complete Profile", (dialog, which) -> com.smartsolar.mobile.ui.account.AccountExperienceActivity.open(this, "profile")).show();
        }
    }
    private void loadInbox() {
        if (inboxCall != null) inboxCall.cancel();
        inboxCall = RetrofitClient.create(this, BuildConfig.API_BASE_URL, BuildConfig.DEBUG).notifications();
        final int request = generation;
        inboxCall.enqueue(new retrofit2.Callback<com.smartsolar.mobile.data.remote.dto.NotificationInbox>() {
            @Override public void onResponse(retrofit2.Call<com.smartsolar.mobile.data.remote.dto.NotificationInbox> call, retrofit2.Response<com.smartsolar.mobile.data.remote.dto.NotificationInbox> response) {
                if (response.errorBody() != null) response.errorBody().close();
                if (!foreground || request != generation || !isVerified()) return;
                if (response.isSuccessful() && response.body() != null) {
                    inbox = response.body();
                    toolbar.getMenu().findItem(1002).setTitle("Notifications" + (inbox.unreadCount == 0 ? "" : " (" + (inbox.unreadCount > 99 ? "99+" : inbox.unreadCount) + ")"));
                    View bell=toolbar.getMenu().findItem(1002).getActionView();
                    bell.setContentDescription(getResources().getQuantityString(R.plurals.polish_notification_description,inbox.unreadCount,inbox.unreadCount));
                    TextView badge=bell.findViewById(R.id.toolbarUnreadBadge);
                    badge.setText(inbox.unreadCount>99?"99+":String.valueOf(inbox.unreadCount));badge.setVisibility(inbox.unreadCount>0?View.VISIBLE:View.GONE);
                    notifyReady(getSupportFragmentManager());
                }
            }
            @Override public void onFailure(retrofit2.Call<com.smartsolar.mobile.data.remote.dto.NotificationInbox> call, Throwable error) { }
        });
    }
    private void notifyReady(androidx.fragment.app.FragmentManager manager) {
        for (Fragment f : manager.getFragments()) {
            if (f instanceof WorkspaceFragment) ((WorkspaceFragment) f).ensureLoaded();
            notifyReady(f.getChildFragmentManager());
        }
    }
    public void select(Destination destination) {
        if (!isVerified() || !MobileNavigation.destinations(role()).contains(destination) || destination == Destination.SCAN) return;
        selected = destination; show(destination);
    }
    private void show(Destination destination) {
        if (getSupportFragmentManager().isStateSaved()) return;
        String tag = "workspace:" + destination.name();
        Fragment next = getSupportFragmentManager().findFragmentByTag(tag);
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction().setReorderingAllowed(true);
        for (Fragment old : getSupportFragmentManager().getFragments()) {
            if (old.getTag() != null && old.getTag().startsWith("workspace:") && old != next) tx.hide(old).setMaxLifecycle(old, Lifecycle.State.STARTED);
        }
        if (next == null) {
            next = create(destination); tx.add(R.id.workspaceContent, next, tag);
        } else tx.show(next);
        tx.setMaxLifecycle(next, Lifecycle.State.RESUMED).setPrimaryNavigationFragment(next).commitNow();
        toolbar.setTitle(title(destination));
        if (navigation.getSelectedItemId() != destination.ordinal()+1) navigation.setSelectedItemId(destination.ordinal()+1);
    }
    private Fragment create(Destination d) {
        switch (d) {
            case STATIONS: return new StationsFragment();
            case ACCOUNT: return new AccountFragment();
            case RESERVATIONS: return BookingWorkspaceFragment.create(true);
            case BOOKINGS: return BookingWorkspaceFragment.create(false);
            case HISTORY: return BookingListFragment.create(Section.HISTORY);
            case SEARCH: return new SearchBookingsFragment();
            default: return new HomeFragment();
        }
    }
    public void openSection(Section section) {
        Destination parent = MobileNavigation.parent(role(), section);
        if (parent == null) return;
        select(parent);
        Fragment f = getSupportFragmentManager().findFragmentByTag("workspace:" + parent.name());
        if (f instanceof BookingWorkspaceFragment) ((BookingWorkspaceFragment) f).selectSection(section);
    }
    public void openScanner() {
        if (!openingScanner && "GridOperator".equals(role()) && isVerified()) { openingScanner = true; startActivity(new Intent(this, QrScannerActivity.class)); }
    }
    @Override protected void onResume() { super.onResume(); openingScanner = false; }
    public void reservationsChanged() {
        WorkspaceChanges.reservationsChanged(); state.bookingRevision = WorkspaceChanges.bookings();
        notifyReady(getSupportFragmentManager());
    }
    public void logout() {
        if (auth == null || openingLogin) return;
        state.verified = false; content.setVisibility(View.INVISIBLE); navSurface.setVisibility(View.GONE);
        auth.logout((user, expiry, error) -> {
            if (error == 0) openLogin();
            else { gate.setVisibility(View.VISIBLE); ((TextView) findViewById(R.id.workspaceMessage)).setText(error); }
        });
    }
    public void openLogin() {
        if (openingLogin || isFinishing()) return;
        openingLogin = true;
        if (state != null) state.clear();
        startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish();
    }
    private void scheduleExpiry() {
        main.removeCallbacks(expire);
        long expiry = preferences.getLong("expires_at", 0);
        if (foreground) main.postDelayed(expire, Math.max(0, expiry - System.currentTimeMillis()));
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); }
    @Override protected void onSaveInstanceState(Bundle out) { out.putString("destination", selected.name()); out.putString("owner", state.owner); super.onSaveInstanceState(out); }
    @Override protected void onStop() {
        foreground = false; if (inboxCall != null) inboxCall.cancel(); main.removeCallbacks(expire);
        if (!isChangingConfigurations()) { needsVerification = true; state.verified = false; }
        super.onStop();
    }
    @Override protected void onDestroy() {
        generation++; main.removeCallbacksAndMessages(null);
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(sessionChanged);
        if (auth != null) auth.close(); super.onDestroy();
    }
    private int title(Destination d) {
        switch (d) {
            case HOME: return R.string.brand_name;
            case STATIONS: return R.string.nav_stations;
            case RESERVATIONS: return R.string.nav_reservations;
            case HISTORY: return R.string.nav_history;
            case ACCOUNT: return R.string.nav_account;
            case SCAN: return R.string.nav_scan;
            case BOOKINGS: return R.string.nav_bookings;
            default: return R.string.nav_search;
        }
    }
    private int icon(Destination d) {
        switch (d) {
            case HOME: return R.drawable.ic_nav_home;
            case STATIONS: return R.drawable.ic_nav_stations;
            case HISTORY: return R.drawable.ic_nav_history;
            case ACCOUNT: return R.drawable.ic_nav_account;
            case SCAN: return R.drawable.ic_nav_scan;
            case SEARCH: return R.drawable.ic_nav_search;
            default: return R.drawable.ic_nav_bookings;
        }
    }
}
