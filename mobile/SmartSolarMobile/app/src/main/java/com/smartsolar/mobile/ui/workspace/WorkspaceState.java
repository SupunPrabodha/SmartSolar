package com.smartsolar.mobile.ui.workspace;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;
import androidx.lifecycle.ViewModel;
import com.smartsolar.mobile.data.remote.dto.UserResponse;
import com.smartsolar.mobile.util.WorkspaceRefreshPolicy;
import java.util.HashMap;
import java.util.Map;

/** Only in-memory presentation state. No token, QR payload or enterprise database cache. */
public final class WorkspaceState extends ViewModel {
    public UserResponse profile;
    public boolean verified;
    public long bookingRevision;
    public String owner;
    private final Map<String, Screen> screens = new HashMap<>();
    public Screen screen(String key) { return screens.computeIfAbsent(key, ignored -> new Screen()); }
    public void clear() { screens.clear(); profile = null; verified = false; owner = null; }
    public static final class Screen {
        public final Bundle values = new Bundle();
        public final WorkspaceRefreshPolicy refresh = new WorkspaceRefreshPolicy();
        public Object data;
        public SparseArray<Parcelable> hierarchy;
        public boolean loading;
    }
}
