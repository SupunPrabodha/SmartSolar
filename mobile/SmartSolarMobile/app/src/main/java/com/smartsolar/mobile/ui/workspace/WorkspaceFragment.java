package com.smartsolar.mobile.ui.workspace;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;

/** A destination owns content only; the Activity owns session, chrome and tab selection. */
public abstract class WorkspaceFragment extends Fragment {
    protected View root;
    protected int viewGeneration;
    protected WorkspaceState.Screen memory;
    protected abstract int layout();
    protected abstract void bind(Bundle saved);
    protected abstract void load();
    protected void onWorkspaceReady() { }
    protected boolean bookingData() { return true; }
    protected WorkspaceActivity workspace() { return (WorkspaceActivity) requireActivity(); }
    protected <T extends View> T findViewById(int id) { return root.findViewById(id); }
    protected boolean alive() { return isAdded() && root != null; }
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saved) {
        viewGeneration++;
        root = inflater.inflate(layout(), container, false);
        String parent = getParentFragment() == null ? "" : getParentFragment().getTag() + "/";
        memory = workspace().state().screen(parent + getTag());
        return root;
    }
    @Override public void onViewCreated(View view, Bundle saved) { bind(saved); }
    @Override public void onViewStateRestored(Bundle saved) {
        super.onViewStateRestored(saved);
        if (memory.hierarchy != null) root.restoreHierarchyState(memory.hierarchy);
    }
    @Override public void onResume() { super.onResume(); ensureLoaded(); }
    public final void ensureLoaded() {
        if (!alive() || !isResumed() || !workspace().isVerified()) return;
        onWorkspaceReady();
        long revision = bookingData() ? workspace().state().bookingRevision : 0;
        if (!memory.loading && memory.refresh.needsLoad(revision)) {
            memory.refresh.attempted(revision); load();
        }
    }
    protected void retry() {
        if (!workspace().isVerified()) { workspace().verify(); return; }
        memory.refresh.interrupted(); ensureLoaded();
    }
    @Override public void onDestroyView() {
        if (memory != null && root != null) {
            memory.hierarchy = new android.util.SparseArray<>();
            root.saveHierarchyState(memory.hierarchy);
            if (memory.loading) { memory.loading = false; memory.refresh.interrupted(); }
        }
        viewGeneration++; root = null;
        super.onDestroyView();
    }
}
