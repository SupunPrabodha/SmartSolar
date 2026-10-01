package com.smartsolar.mobile.util;

/** Tab changes are not invalidations. A failed attempt waits for explicit retry. */
public final class WorkspaceRefreshPolicy {
    private boolean attempted;
    private long revision = -1;
    public boolean needsLoad(long currentRevision) { return !attempted || revision != currentRevision; }
    public void attempted(long currentRevision) { attempted = true; revision = currentRevision; }
    public void interrupted() { attempted = false; }
}
