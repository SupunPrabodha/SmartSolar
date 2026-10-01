package com.smartsolar.mobile;

import com.smartsolar.mobile.util.WorkspaceRefreshPolicy;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorkspaceRefreshPolicyTest {
    @Test public void firstLoadThenRepeatedSelectionsDoNotReload() {
        WorkspaceRefreshPolicy policy = new WorkspaceRefreshPolicy();
        assertTrue(policy.needsLoad(0)); policy.attempted(0);
        for (int i=0; i<20; i++) assertFalse(policy.needsLoad(0));
    }
    @Test public void mutationInvalidatesOnce() {
        WorkspaceRefreshPolicy policy = new WorkspaceRefreshPolicy(); policy.attempted(4);
        assertTrue(policy.needsLoad(5)); policy.attempted(5); assertFalse(policy.needsLoad(5));
    }
    @Test public void interruptedLoadOrExplicitRetryCanLoadAgain() {
        WorkspaceRefreshPolicy policy = new WorkspaceRefreshPolicy(); policy.attempted(0);
        assertFalse(policy.needsLoad(0)); policy.interrupted(); assertTrue(policy.needsLoad(0));
    }
}
