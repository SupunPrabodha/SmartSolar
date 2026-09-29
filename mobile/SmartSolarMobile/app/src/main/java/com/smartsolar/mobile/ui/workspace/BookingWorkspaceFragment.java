package com.smartsolar.mobile.ui.workspace;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import com.google.android.material.tabs.TabLayout;
import com.smartsolar.mobile.R;
import com.smartsolar.mobile.util.MobileNavigation.Section;

/** Retained child fragments provide one cohesive Reservations/Bookings feature area. */
public final class BookingWorkspaceFragment extends WorkspaceFragment {
    private TabLayout tabs;
    private Section section;
    private boolean selecting;
    public static BookingWorkspaceFragment create(boolean prosumer) {
        BookingWorkspaceFragment f = new BookingWorkspaceFragment();
        Bundle args = new Bundle(); args.putBoolean("prosumer", prosumer); f.setArguments(args); return f;
    }
    @Override protected int layout() { return R.layout.fragment_booking_workspace; }
    @Override protected void bind(Bundle saved) {
        boolean prosumer = requireArguments().getBoolean("prosumer");
        tabs = findViewById(R.id.bookingTabs);
        Section[] sections = prosumer ? new Section[]{Section.MINE, Section.CURRENT, Section.PENDING, Section.SUMMARY, Section.SEARCH} : new Section[]{Section.CURRENT, Section.PENDING, Section.HISTORY};
        for (Section value : sections) tabs.addTab(tabs.newTab().setText(label(value)).setTag(value));
        String selected = saved == null ? memory.values.getString("section") : saved.getString("section");
        section = selected == null ? sections[0] : Section.valueOf(selected);
        if (!java.util.Arrays.asList(sections).contains(section)) section = sections[0];
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { if (!selecting) selectSection((Section) tab.getTag()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        selectSection(section);
    }
    public void selectSection(Section nextSection) {
        if (tabs == null || getChildFragmentManager().isStateSaved()) return;
        int position = -1;
        for (int i=0; i<tabs.getTabCount(); i++) if (tabs.getTabAt(i).getTag() == nextSection) position = i;
        if (position < 0) return;
        section = nextSection; memory.values.putString("section", section.name());
        selecting = true; tabs.selectTab(tabs.getTabAt(position)); selecting = false;
        String tag = "section:" + section.name();
        Fragment next = getChildFragmentManager().findFragmentByTag(tag);
        FragmentTransaction tx = getChildFragmentManager().beginTransaction().setReorderingAllowed(true);
        for (Fragment old : getChildFragmentManager().getFragments()) if (old != next) tx.hide(old).setMaxLifecycle(old, Lifecycle.State.STARTED);
        if (next == null) {
            next = section == Section.MINE ? new MyReservationsFragment() : section == Section.SUMMARY ? new ReservationSummaryAnalyticsFragment() : section == Section.SEARCH ? new SearchBookingsFragment() : BookingListFragment.create(section);
            tx.add(R.id.bookingSectionContent, next, tag);
        } else tx.show(next);
        tx.setMaxLifecycle(next, Lifecycle.State.RESUMED).setPrimaryNavigationFragment(next).commitNow();
    }
    @Override protected void load() { /* Child destinations own their first load and invalidation. */ }
    @Override public void onSaveInstanceState(Bundle out) { out.putString("section", section.name()); super.onSaveInstanceState(out); }
    private int label(Section value) {
        switch (value) {
            case MINE: return R.string.my_bookings_tab;
            case CURRENT: return R.string.nav_current;
            case PENDING: return R.string.nav_pending;
            case SUMMARY: return R.string.nav_summary;
            case HISTORY: return R.string.nav_history;
            default: return R.string.nav_search;
        }
    }
}
